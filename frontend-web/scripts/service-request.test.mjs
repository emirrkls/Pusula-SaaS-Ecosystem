import assert from 'node:assert/strict';
import test from 'node:test';
import { MARINE_DEVICE_TYPE, initialDeviceType, createServiceRequestForm, buildServiceRequestPayload } from '../src/lib/serviceRequest.js';

const form = {
    ...createServiceRequestForm(MARINE_DEVICE_TYPE),
    name: ' Test Müşterisi ', phone: '5400250925', address: ' Didim marina, A iskelesi 12 ',
    boatName: ' Test Teknesi ', deviceModel: ' Test model ', note: ' Soğutmuyor. ',
};

test('marine request carries boat and device details through the existing public API contract', () => {
    assert.deepEqual(buildServiceRequestPayload(form, '1'), {
        companyId: 1, customerName: 'Test Müşterisi', customerPhone: '5400250925',
        customerAddress: 'Didim marina, A iskelesi 12', deviceType: MARINE_DEVICE_TYPE,
        description: 'Tekne adı: Test Teknesi\nCihaz marka / model: Test model\nSoğutmuyor.', website: '',
    });
});

test('switching to another service does not send hidden boat fields', () => {
    const payload = buildServiceRequestPayload({ ...form, deviceType: 'Klima' }, '1');
    assert.equal(payload.description, 'Soğutmuyor.');
    assert.equal(payload.deviceType, 'Klima');
});

test('optional marine details can be omitted and form reset preserves the selected service', () => {
    const reset = createServiceRequestForm(MARINE_DEVICE_TYPE);
    assert.equal(reset.deviceType, MARINE_DEVICE_TYPE);
    assert.equal(buildServiceRequestPayload(reset, '1').description, null);
    assert.equal(buildServiceRequestPayload({ ...form, boatName: ' ', deviceModel: '', note: ' Bakım ' }, '1').description, 'Bakım');
    assert.equal(buildServiceRequestPayload({ ...form, website: 'bot-content' }, '1').website, 'bot-content');
});

test('only the supported marine query preselects the marine service', () => {
    assert.equal(initialDeviceType('yat-tekne'), MARINE_DEVICE_TYPE);
    for (const value of [null, '', 'unknown', '<script>']) assert.equal(initialDeviceType(value), 'Klima');
    const maximumDetails = buildServiceRequestPayload({ ...form, boatName: 'a'.repeat(80), deviceModel: 'b'.repeat(80), note: 'c'.repeat(700) }, '1');
    assert.ok(maximumDetails.description.length <= 1000, 'marine field limits respect backend description limit');
});
