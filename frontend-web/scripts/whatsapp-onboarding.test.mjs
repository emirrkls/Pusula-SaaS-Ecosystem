import assert from 'node:assert/strict';
import test from 'node:test';
import { initialOnboardingState, readOnboardingContext } from '../src/lib/whatsappOnboarding.js';

const NOW = Date.parse('2026-10-03T23:35:30Z');
const params = {
    state: 'a'.repeat(43),
    app_id: '123456789012345',
    configuration_id: '987654321098765',
    expires_at: '2026-10-03T23:45:30Z',
};
const fragment = (overrides = {}) => `#${new URLSearchParams({ ...params, ...overrides })}`;

test('valid onboarding has the state and configuration ready on the first render', () => {
    const initial = initialOnboardingState(fragment(), NOW);
    assert.equal(initial.phase, 'loading');
    assert.deepEqual(initial.context, {
        state: params.state,
        appId: params.app_id,
        configurationId: params.configuration_id,
        expiresAtMs: Date.parse(params.expires_at),
    });
    assert.equal(initial.message.includes(params.state), false);
});

for (const [name, overrides, expectedMessage] of [
    ['invalid state', { state: 'short' }, /Bağlantı geçersiz/],
    ['invalid app id', { app_id: 'not-a-meta-id' }, /ayarları eksik/],
    ['missing configuration', { configuration_id: '' }, /ayarları eksik/],
    ['missing expiry', { expires_at: '' }, /süresi doğrulanamadı/],
    ['invalid expiry', { expires_at: 'invalid' }, /süresi doğrulanamadı/],
]) {
    test(`${name} blocks SDK initialization`, () => {
        const initial = initialOnboardingState(fragment(overrides), NOW);
        assert.equal(initial.context, null);
        assert.equal(initial.phase, 'error');
        assert.match(initial.message, expectedMessage);
        assert.throws(() => readOnboardingContext(fragment(overrides), NOW), expectedMessage);
    });
}

for (const expiry of ['2026-10-03T23:35:29Z', '2026-10-03T23:35:30Z']) {
    test(`expiry at ${expiry} cannot be used at the boundary`, () => {
        const initial = initialOnboardingState(fragment({ expires_at: expiry }), NOW);
        assert.equal(initial.context, null);
        assert.equal(initial.phase, 'expired');
    });
}

test('opening the public route without an onboarding session shows a safe error', () => {
    const initial = initialOnboardingState('', NOW);
    assert.equal(initial.context, null);
    assert.equal(initial.phase, 'error');
    assert.match(initial.message, /yeni bir bağlantı oluşturun/);
});
