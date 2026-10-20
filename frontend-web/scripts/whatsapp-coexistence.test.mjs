import assert from 'node:assert/strict';
import test from 'node:test';
import { spawnSync } from 'node:child_process';
import { createSignupCoordinator, embeddedSignupOptions, parseEmbeddedSignupEvent, readOnboardingContext } from '../src/lib/whatsappOnboarding.js';

const origin = 'https://www.facebook.com';
const event = (event, data = {}) => ({ type: 'WA_EMBEDDED_SIGNUP', event, data });

test('Coexistence requests session logging v3 without changing signup configuration', () => {
    const options = embeddedSignupOptions('2136126430333068');
    assert.equal(options.config_id, '2136126430333068');
    assert.equal(options.extras.sessionInfoVersion, '3');
    assert.equal(options.extras.featureType, 'whatsapp_business_app_onboarding');
    assert.equal(options.response_type, 'code');
});
test('Coexistence FINISH may contain only waba_id; backend resolves the authorized phone', () => {
    assert.deepEqual(parseEmbeddedSignupEvent(origin, JSON.stringify(event(
        'FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING', { waba_id: '12345' }))), {
        event: 'FINISH', wabaId: '12345', phoneNumberId: null, coexistence: true,
    });
});
test('legacy FINISH still requires and preserves both asset ids', () => {
    assert.equal(parseEmbeddedSignupEvent(origin, event('FINISH', { waba_id: '12345' })), null);
    assert.deepEqual(parseEmbeddedSignupEvent(origin, event('FINISH', {
        waba_id: '12345', phone_number_id: '67890',
    })), { event: 'FINISH', wabaId: '12345', phoneNumberId: '67890', coexistence: false });
});
test('untrusted origins, malformed payloads and invalid ids cannot complete a session', () => {
    for (const source of ['https://evil.example', 'https://facebook.com.evil.example', 'null']) {
        assert.equal(parseEmbeddedSignupEvent(source, event('FINISH', { waba_id: '12345', phone_number_id: '67890' })), null);
    }
    for (const raw of ['{', null, {}, event('OTHER'), event('FINISH', { waba_id: 'bad', phone_number_id: '67890' }),
        event('FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING', { waba_id: '12345', phone_number_id: 'bad' })]) {
        assert.equal(parseEmbeddedSignupEvent(origin, raw), null);
    }
});
test('cancel/error never forwards raw Meta payload or credentials', () => {
    assert.deepEqual(parseEmbeddedSignupEvent(origin, event('ERROR', { error_message: 'secret' })), { event: 'ERROR' });
    assert.deepEqual(parseEmbeddedSignupEvent(origin, event('CANCEL')), { event: 'CANCEL' });
});
test('legacy timezone-less expiry remains UTC, including nanoseconds', () => {
    const params = new URLSearchParams({ state: 'a'.repeat(43), app_id: '12345', configuration_id: '67890',
        expires_at: '2026-10-20T09:00:00.123456789' });
    assert.equal(readOnboardingContext('#' + params, 0).expiresAtMs, Date.parse('2026-10-20T09:00:00.123Z'));
});

for (const order of ['code-first', 'finish-first']) {
    test(`callback race ${order} completes exactly once, never with half the authorization`, () => {
        const requests = [];
        const coordinator = createSignupCoordinator(payload => requests.push(payload));
        const epoch = coordinator.start();
        const finish = parseEmbeddedSignupEvent(origin, event('FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING', { waba_id: '12345' }));
        if (order === 'code-first') coordinator.code(epoch, 'test-code');
        else coordinator.event(finish);
        assert.equal(requests.length, 0);
        if (order === 'code-first') coordinator.event(finish);
        else coordinator.code(epoch, 'test-code');
        coordinator.event(finish); coordinator.code(epoch, 'test-code');
        assert.equal(requests.length, 1);
        assert.deepEqual(requests[0], { code: 'test-code', wabaId: '12345', phoneNumberId: null, coexistence: true });
    });
}
test('cancel discards old account/code and late callbacks cannot finish a newer attempt', () => {
    const requests = [];
    const coordinator = createSignupCoordinator(payload => requests.push(payload));
    const old = coordinator.start();
    coordinator.code(old, 'old-code'); coordinator.cancel(old);
    assert.equal(coordinator.event({event:'FINISH',wabaId:'12345'}), false);
    const current = coordinator.start();
    coordinator.event({event:'FINISH',wabaId:'12345',phoneNumberId:null,coexistence:true});
    assert.equal(coordinator.code(old, 'old-code'), false);
    assert.equal(requests.length, 0);
    coordinator.code(current, 'new-code');
    assert.equal(requests.length, 1); assert.equal(requests[0].code, 'new-code');
});

for (const zone of ['UTC', 'Europe/Istanbul', 'Europe/Berlin', 'America/Los_Angeles', 'Asia/Kolkata', 'Pacific/Auckland']) {
    test(`onboarding expiry is the same instant in ${zone}`, () => {
        const url = new URL('../src/lib/whatsappOnboarding.js', import.meta.url).href;
        const params = new URLSearchParams({ state: 'a'.repeat(43), app_id: '12345', configuration_id: '67890',
            expires_at: '2026-10-20T09:00:00.123456789' });
        const script = `import {readOnboardingContext} from ${JSON.stringify(url)};
            process.stdout.write(String(readOnboardingContext(${JSON.stringify('#' + params)}, 0).expiresAtMs));`;
        const result = spawnSync(process.execPath, ['--input-type=module', '-e', script], {
            env: {...process.env, TZ: zone}, encoding:'utf8',
        });
        assert.equal(result.status, 0, result.stderr);
        assert.equal(Number(result.stdout), Date.parse('2026-10-20T09:00:00.123Z'));
    });
}
