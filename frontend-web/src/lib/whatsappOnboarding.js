const META_ID_PATTERN = /^[0-9]{5,32}$/;
const STATE_PATTERN = /^[A-Za-z0-9_-]{40,80}$/;

export function readOnboardingContext(fragment, nowMs) {
    const params = new URLSearchParams(fragment.replace(/^#/, ''));
    const state = params.get('state') || '';
    const appId = params.get('app_id') || '';
    const configurationId = params.get('configuration_id') || '';
    const expiresAtMs = Date.parse(params.get('expires_at') || '');

    if (!STATE_PATTERN.test(state)) {
        throw new Error('Bağlantı geçersiz. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
    }
    if (!META_ID_PATTERN.test(appId) || !META_ID_PATTERN.test(configurationId)) {
        throw new Error('Meta bağlantı ayarları eksik. Pusula Ayarlar ekranından bağlantıyı yeniden başlatın.');
    }
    if (!Number.isFinite(expiresAtMs)) {
        throw new Error('Bağlantı süresi doğrulanamadı. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
    }
    if (expiresAtMs <= nowMs) {
        throw new Error('Bu bağlantının süresi dolmuş. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
    }

    return { state, appId, configurationId, expiresAtMs };
}

export function initialOnboardingState(fragment, nowMs) {
    try {
        return {
            context: readOnboardingContext(fragment, nowMs),
            phase: 'loading',
            message: 'Güvenli bağlantı hazırlanıyor…',
        };
    } catch (error) {
        return {
            context: null,
            phase: /süresi dolmuş/i.test(error.message) ? 'expired' : 'error',
            message: error.message,
        };
    }
}
