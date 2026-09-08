import React, { useCallback, useEffect, useRef, useState } from 'react';

const APP_ID = '4494017667582443';
const CONFIGURATION_ID = '2136126430333068';
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://api.pusulaiklimlendirme.com';

function WhatsAppConnect() {
    const [stateToken, setStateToken] = useState('');
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
            if (!response.ok) throw new Error(payload.message || payload.error || 'Bağlantı tamamlanamadı.');
            setPhase('success');
            setMessage(`${payload.displayPhoneNumber || 'WhatsApp numaranız'} başarıyla Pusula'ya bağlandı.`);
        } catch (error) {
            completing.current = false;
            setPhase('error');
            setMessage(error.message || 'Bağlantı sırasında beklenmeyen bir hata oluştu.');
        }
    }, [stateToken]);

    useEffect(() => {
        const state = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('state') || '';
        if (!/^[A-Za-z0-9_-]{40,80}$/.test(state)) {
            setPhase('error');
            setMessage('Bağlantı geçersiz veya süresi dolmuş. Pusula Ayarlar ekranından yeni bağlantı oluşturun.');
            return undefined;
        }
        setStateToken(state);

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
                setMessage('Meta bağlantı işlemi hata verdi. Yeni bir bağlantı oturumu oluşturup tekrar deneyin.');
            }
        };
        window.addEventListener('message', messageListener);

        window.fbAsyncInit = () => {
            window.FB.init({ appId: APP_ID, cookie: true, xfbml: false, version: 'v26.0' });
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
            document.body.appendChild(script);
        } else if (window.FB) {
            window.fbAsyncInit();
        }
        return () => window.removeEventListener('message', messageListener);
    }, [complete]);

    useEffect(() => { complete(); }, [complete]);

    const launch = () => {
        if (!sdkReady || !window.FB || !stateToken) return;
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
            config_id: CONFIGURATION_ID,
            response_type: 'code',
            override_default_response_type: true,
            extras: {
                setup: {},
                featureType: 'whatsapp_business_app_onboarding',
                sessionInfoVersion: '3',
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
                <div className={`mb-7 rounded-2xl border p-4 text-sm leading-6 ${phase === 'success' ? 'border-emerald-400/30 bg-emerald-400/10 text-emerald-100' : phase === 'error' ? 'border-rose-400/30 bg-rose-400/10 text-rose-100' : 'border-white/10 bg-black/20 text-slate-200'}`}>
                    {message}
                </div>
                {phase !== 'success' && (
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
