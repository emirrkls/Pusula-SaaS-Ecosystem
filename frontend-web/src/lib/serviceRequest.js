export const MARINE_DEVICE_TYPE = 'Yat / Tekne Klima ve Buzdolabı';

export function initialDeviceType(service) {
    return service === 'yat-tekne' ? MARINE_DEVICE_TYPE : 'Klima';
}

export function createServiceRequestForm(deviceType = 'Klima') {
    return { name: '', phone: '', deviceType, address: '', note: '', boatName: '', deviceModel: '', website: '' };
}

export function serviceRequestDescription(formData) {
    const details = formData.deviceType === MARINE_DEVICE_TYPE
        ? [
            formData.boatName?.trim() && `Tekne adı: ${formData.boatName.trim()}`,
            formData.deviceModel?.trim() && `Cihaz marka / model: ${formData.deviceModel.trim()}`,
        ].filter(Boolean)
        : [];
    return [...details, formData.note?.trim()].filter(Boolean).join('\n') || null;
}

export function buildServiceRequestPayload(formData, companyId) {
    return {
        companyId: Number.parseInt(companyId, 10),
        customerName: formData.name.trim(),
        customerPhone: formData.phone.trim(),
        customerAddress: formData.address.trim(),
        deviceType: formData.deviceType,
        description: serviceRequestDescription(formData),
        website: formData.website || '',
    };
}
