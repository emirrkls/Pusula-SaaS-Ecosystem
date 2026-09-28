import React, { useCallback, useEffect, useRef, useState } from 'react';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://api.pusulaiklimlendirme.com';
const META_GRAPH_VERSION = 'v26.0';
const META_ID_PATTERN = /^[0-9]{5,32}$/;
const STATE_PATTERN = /^[A-Za-z0-9_-]{40,80}$/;

function parseOnboardingContext() {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
    const state = params.get('state') || '';
    const appId = params.get('app_id') || '';
    const configurationId = params.get('configuration_id') || '';
    const expiresAt = params.get('expires_at') || '';
    const expiresAtMs = Date.parse(expiresAt);

    if (!STATE_PATTERN.test(state)) {
        throw new Error('Bağlantı geçersiz. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
    }
    if (!META_ID_PATTERN.test(appId) || !META_ID_PATTERN.test(configurationId)) {
        throw new Error('Meta bağlantı ayarları eksik. Pusula Ayarlar ekranından bağlantıyı yeniden başlatın.');
    }
    if (!Number.isFinite(expiresAtMs)) {
        throw new Error('Bağlantı süresi doğrulanamadı. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
    }
    if (expiresAtMs <= Date.now()) {
        throw new Error('Bu bağlantının süresi dolmuş. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
    }

    return { state, appId, configurationId, expiresAtMs };
}

function completionError(response, payload) {
    const serverMessage = payload.message || payload.error;
    if (typeof serverMessage === 'string' && serverMessage.trim()) {
        return serverMessage;
    }
    if (response.status === 400 || response.status === 410) {
        return 'Bağlantı oturumunun süresi dolmuş veya daha önce kullanılmış. Pusula Ayarlar ekranından yeniden başlatın.';
    }
    if (response.status === 409) {
        return 'Bu WhatsApp numarası başka bir işletmeye bağlı. Lütfen bağlantı bilgilerinizi kontrol edin.';
    }
    return 'Bağlantı tamamlanamadı. Lütfen Pusula Ayarlar ekranından yeniden deneyin.';
}

function WhatsAppConnect() {
    const [stateToken, setStateToken] = useState('');
    const [metaConfig, setMetaConfig] = useState(null);
    const [sdkReady, setSdkReady] = useState(false);
    const [phase, setPhase] = useState('loading');
    const [message, setMessage] = useState('Güvenli bağlantı hazırlanıyor…');
    const authCode = useRef('');
    const accountData = useRef(null);
    const completing = useRef(false);

    const complete = useCallback(async () => {
        if (completing.current || !authCode.current || !accountData.current || !stateToken) return;
        completing.current = true;
        setPhase('working');
        setMessage('WhatsApp Business hesabınız doğrulanıyor…');
        try {
            const response = await fetch(`${API_BASE_URL}/api/public/whatsapp/onboarding/complete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    state: stateToken,
                    code: authCode.current,
                    wabaId: accountData.current.waba_id,
                    phoneNumberId: accountData.current.phone_number_id,
                }),
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(completionError(response, payload));
            setPhase('success');
            setMessage(`${payload.displayPhoneNumber || 'WhatsApp numaranız'} başarıyla Pusula'ya bağlandı.`);
        } catch (error) {
            completing.current = false;
            const errorMessage = error.message || 'Bağlantı sırasında beklenmeyen bir hata oluştu.';
            const requiresNewSession = /süresi dolmuş|daha önce kullanılmış/i.test(errorMessage);
            setPhase(requiresNewSession ? 'expired' : 'error');
            setMessage(errorMessage);
        }
    }, [stateToken]);

    useEffect(() => {
        let context;
        try {
            context = parseOnboardingContext();
        } catch (error) {
            setPhase(/süresi dolmuş/i.test(error.message) ? 'expired' : 'error');
            setMessage(error.message);
            return undefined;
        }

        setStateToken(context.state);
        setMetaConfig(context);

        const expiresInMs = context.expiresAtMs - Date.now();
        const expiryTimer = window.setTimeout(() => {
            setSdkReady(false);
            setPhase('expired');
            setMessage('Bu bağlantının süresi doldu. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
        }, expiresInMs);

        const messageListener = (event) => {
            if (!['https://www.facebook.com', 'https://web.facebook.com'].includes(event.origin)) return;
            let data = event.data;
            try { if (typeof data === 'string') data = JSON.parse(data); } catch { return; }
            if (data?.type !== 'WA_EMBEDDED_SIGNUP') return;
            if (data.event === 'FINISH' && data.data?.waba_id && data.data?.phone_number_id) {
                accountData.current = data.data;
                complete();
            } else if (data.event === 'CANCEL') {
                setPhase('idle');
                setMessage('Bağlantı işlemi tamamlanmadı. Hazır olduğunuzda yeniden deneyebilirsiniz.');
            } else if (data.event === 'ERROR') {
                setPhase('error');
                const detail = typeof data.data?.error_message === 'string'
                    ? ` Meta bildirimi: ${data.data.error_message}`
                    : '';
                setMessage(`Meta bağlantı işlemi tamamlanamadı.${detail} Pusula Ayarlar ekranından yeni bir bağlantı oluşturup tekrar deneyin.`);
            }
        };
        window.addEventListener('message', messageListener);

        window.fbAsyncInit = () => {
            // Meta's FedCM flow only forwards the requested scope and drops the
            // Login for Business configuration id. Embedded Signup must use the
            // classic OAuth popup so Meta can resolve the backend-provided configuration id.
            window.FB.init({
                appId: context.appId,
                cookie: true,
                xfbml: true,
                version: META_GRAPH_VERSION,
                fedCM: false,
            });
            setSdkReady(true);
            setPhase('idle');
            setMessage('Mevcut WhatsApp Business uygulamanızı koruyarak bağlantıyı başlatabilirsiniz.');
        };
        const existing = document.getElementById('facebook-jssdk');
        if (!existing) {
            const script = document.createElement('script');
            script.id = 'facebook-jssdk';
            script.async = true;
            script.defer = true;
            script.crossOrigin = 'anonymous';
            script.referrerPolicy = 'origin';
            script.src = 'https://connect.facebook.net/tr_TR/sdk.js';
            script.onerror = () => {
                setPhase('error');
                setMessage('Meta bağlantı bileşeni yüklenemedi. İnternet bağlantınızı kontrol edip sayfayı yenileyin.');
            };
            document.body.appendChild(script);
        } else if (window.FB) {
            window.fbAsyncInit();
        }
        return () => {
            window.clearTimeout(expiryTimer);
            window.removeEventListener('message', messageListener);
        };
    }, [complete]);

    useEffect(() => { complete(); }, [complete]);

    const launch = () => {
        if (!sdkReady || !window.FB || !stateToken || !metaConfig) return;
        if (metaConfig.expiresAtMs <= Date.now()) {
            setSdkReady(false);
            setPhase('expired');
            setMessage('Bu bağlantının süresi doldu. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
            return;
        }
        setPhase('working');
        setMessage('Meta bağlantı penceresi açılıyor…');
        window.FB.login((response) => {
            if (response.authResponse?.code) {
                authCode.current = response.authResponse.code;
                complete();
            } else {
                setPhase('idle');
                setMessage('Meta yetkilendirmesi tamamlanmadı. Hazır olduğunuzda tekrar deneyin.');
            }
        }, {
            config_id: metaConfig.configurationId,
            auth_type: 'rerequest',
            response_type: 'code',
            override_default_response_type: true,
            extras: {
                setup: {},
                featureType: 'whatsapp_business_app_onboarding',
            },
        });
    };

    return (
        <main className="min-h-screen bg-slate-950 text-white grid place-items-center px-5 py-10">
            <section className="w-full max-w-xl rounded-3xl border border-white/10 bg-white/[0.06] p-7 md:p-10 shadow-2xl shadow-cyan-950/30">
                <div className="mb-8 flex items-center gap-3">
                    <div className="grid h-12 w-12 place-items-center rounded-2xl bg-emerald-500/15 text-2xl">✓</div>
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.22em] text-cyan-300">Pusula</p>
                        <h1 className="text-2xl font-semibold">WhatsApp Business bağlantısı</h1>
                    </div>
                </div>
                <p className="mb-7 leading-7 text-slate-300">Servis iş emri oluşturulduğunda ve tamamlandığında müşterilerinize onaylı şablonlarla otomatik bilgi verin. Bu işlem mevcut WhatsApp Business uygulamanızı korur.</p>
                <div className={`mb-7 rounded-2xl border p-4 text-sm leading-6 ${phase === 'success' ? 'border-emerald-400/30 bg-emerald-400/10 text-emerald-100' : phase === 'error' || phase === 'expired' ? 'border-rose-400/30 bg-rose-400/10 text-rose-100' : 'border-white/10 bg-black/20 text-slate-200'}`}>
                    {message}
                </div>
                {!['success', 'error', 'expired'].includes(phase) && (
                    <button type="button" onClick={launch} disabled={!sdkReady || phase === 'working' || !stateToken}
                        className="w-full rounded-xl bg-cyan-500 px-5 py-3.5 font-semibold text-slate-950 transition hover:bg-cyan-300 disabled:cursor-wait disabled:opacity-50">
                        {phase === 'working' ? 'Bağlanıyor…' : 'Meta ile güvenli bağlan'}
                    </button>
                )}
                <p className="mt-5 text-center text-xs leading-5 text-slate-500">Erişim jetonu tarayıcıda gösterilmez; Pusula sunucusunda şifreli olarak saklanır.</p>
            </section>
        </main>
    );
}

export default WhatsAppConnect;
