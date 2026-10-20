const META_ID_PATTERN = /^[0-9]{5,32}$/;
const STATE_PATTERN = /^[A-Za-z0-9_-]{40,80}$/;

function parseOnboardingExpiry(value) {
    const iso = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?)(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})?$/.exec(value);
    if (!iso) return NaN;
    const [, dateTime, fraction, offset] = iso;
    // Legacy LocalDateTime values are UTC, not the browser's local timezone.
    return Date.parse(`${dateTime}${fraction ? `.${fraction.slice(0, 3)}` : ''}${offset || 'Z'}`);
}

export function readOnboardingContext(fragment, nowMs) {
    const params = new URLSearchParams(fragment.replace(/^#/, ''));
    const state = params.get('state') || '';
    const appId = params.get('app_id') || '';
    const configurationId = params.get('configuration_id') || '';
    const expiresAtMs = parseOnboardingExpiry(params.get('expires_at') || '');

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

export function embeddedSignupOptions(configurationId) {
    return {
        config_id: configurationId,
        auth_type: 'rerequest',
        response_type: 'code',
        override_default_response_type: true,
        extras: {
            setup: {},
            featureType: 'whatsapp_business_app_onboarding',
            // Session payload version is independent of the v4 signup experience.
            sessionInfoVersion: '3',
        },
    };
}

// OAuth's code callback and postMessage asset event may arrive in either order.
// Neither is sufficient on its own; cancellation invalidates late callbacks.
export function createSignupCoordinator(onComplete) {
    let epoch = 0;
    let active = false;
    let code = '';
    let account = null;
    const completeIfReady = () => {
        if (!active || !code || !account) return;
        active = false;
        const payload = { code, ...account };
        code = '';
        account = null;
        onComplete(payload);
    };
    return {
        start() { epoch += 1; active = true; code = ''; account = null; return epoch; },
        cancel(expectedEpoch = epoch) {
            if (!active || expectedEpoch !== epoch) return false;
            active = false; epoch += 1; code = ''; account = null; return true;
        },
        isActive() { return active; },
        code(expectedEpoch, value) {
            if (!active || epoch !== expectedEpoch || typeof value !== 'string' || !value || value.length > 4096) return false;
            code = value; completeIfReady(); return true;
        },
        event(value) {
            if (!active || value?.event !== 'FINISH') return false;
            account = { wabaId: value.wabaId, phoneNumberId: value.phoneNumberId, coexistence: value.coexistence };
            completeIfReady(); return true;
        },
    };
}

export function parseEmbeddedSignupEvent(origin, raw) {
    if (!['https://www.facebook.com', 'https://web.facebook.com'].includes(origin)) return null;
    let payload;
    try { payload = typeof raw === 'string' ? JSON.parse(raw) : raw; } catch { return null; }
    if (!payload || payload.type !== 'WA_EMBEDDED_SIGNUP') return null;
    if (['CANCEL', 'ERROR'].includes(payload.event)) return { event: payload.event };
    const coexistence = payload.event === 'FINISH_WHATSAPP_BUSINESS_APP_ONBOARDING';
    if (!coexistence && payload.event !== 'FINISH') return null;
    const data = payload.data;
    if (!data || !META_ID_PATTERN.test(String(data.waba_id || ''))) return null;
    const phone = data.phone_number_id;
    if (phone != null && !META_ID_PATTERN.test(String(phone))) return null;
    if (!coexistence && !phone) return null;
    return {
        event: 'FINISH',
        wabaId: String(data.waba_id),
        phoneNumberId: phone ? String(phone) : null,
        coexistence,
    };
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
