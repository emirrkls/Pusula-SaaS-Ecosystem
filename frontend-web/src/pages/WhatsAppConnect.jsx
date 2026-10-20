import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createSignupCoordinator, embeddedSignupOptions, initialOnboardingState, parseEmbeddedSignupEvent } from '../lib/whatsappOnboarding';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://api.pusulaiklimlendirme.com';
const META_GRAPH_VERSION = 'v26.0';

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
    const [onboarding] = useState(() => initialOnboardingState(
        typeof window === 'undefined' ? '' : window.location.hash,
        Date.now(),
    ));
    const metaConfig = onboarding.context;
    const stateToken = metaConfig?.state || '';
    const [sdkReady, setSdkReady] = useState(false);
    const [phase, setPhase] = useState(onboarding.phase);
    const [message, setMessage] = useState(onboarding.message);
    const authorized = useRef(false);
    const mounted = useRef(true);
    const coordinatorRef = useRef(null);

    const showProgress = useCallback((payload) => {
        if (!mounted.current) return;
        if (!payload.connected) {
            setPhase('error');
            setMessage('WhatsApp bağlantısı aktif değil. Pusula Ayarlar ekranından durumu kontrol edin.');
        } else if (payload.sync?.status === 'ACTION_REQUIRED') {
            setPhase('error');
            setMessage('Bağlantı yetkilendirildi ancak eşitleme için kontrol gerekiyor. Pusula Ayarlar ekranından durumu kontrol edin; bağlantıyı silmeyin.');
        } else if (payload.sync && payload.sync.status !== 'READY') {
            setPhase('syncing');
            setMessage('WhatsApp hesabınız bağlandı; kişi ve sohbet eşitlemesi devam ediyor. WhatsApp Business uygulamasını açık tutun. Geçmiş paylaşım tercihiniz korunur.');
        } else {
            setPhase('success');
            setMessage(payload.sync?.historyStatus === 'DECLINED'
                ? 'WhatsApp bağlantınız hazır. Sohbet geçmişini paylaşmama tercihiniz korundu.'
                : 'WhatsApp bağlantınız hazır. Pusula Ayarlar ekranından bağlantı durumunu yenileyebilirsiniz.');
        }
    }, []);

    const complete = useCallback(async (signals) => {
        if (!stateToken || !mounted.current) return;
        setPhase('working');
        setMessage('WhatsApp Business hesabınız doğrulanıyor…');
        try {
            const response = await fetch(`${API_BASE_URL}/api/public/whatsapp/onboarding/complete`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    state: stateToken,
                    code: signals.code,
                    wabaId: signals.wabaId,
                    phoneNumberId: signals.phoneNumberId,
                    coexistence: signals.coexistence,
                }),
            });
            const payload = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(completionError(response, payload));
            authorized.current = true;
            showProgress(payload);
        } catch {
            // A lost HTTP response must not cause a second OAuth code exchange.
            try {
                const progress = await fetch(`${API_BASE_URL}/api/public/whatsapp/onboarding/progress`, {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ state: stateToken }),
                });
                if (!progress.ok) throw new Error('unavailable');
                const payload = await progress.json();
                authorized.current = true;
                showProgress(payload);
            } catch {
                if (mounted.current) {
                    setPhase('error');
                    setMessage('Bağlantı sonucu doğrulanamadı. Pusula Ayarlar ekranından durumu yenileyin. Tekrar yetkilendirmeden önce mevcut bağlantıyı kontrol edin.');
                }
            }
        }
    }, [showProgress, stateToken]);
    useEffect(() => {
        const coordinator = createSignupCoordinator(complete);
        coordinatorRef.current = coordinator;
        mounted.current = true;
        return () => { mounted.current = false; coordinator.cancel(); };
    }, [complete]);

    useEffect(() => {
        if (phase !== 'syncing') return undefined;
        const controller = new AbortController();
        let timer;
        let polls = 0;
        const poll = async () => {
            try {
                const response = await fetch(`${API_BASE_URL}/api/public/whatsapp/onboarding/progress`, {
                    method: 'POST', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ state: stateToken }), signal: controller.signal,
                });
                if (!response.ok) throw new Error('unavailable');
                showProgress(await response.json());
            } catch {
                if (!controller.signal.aborted && mounted.current) {
                    setMessage('Eşitleme durumu şu anda alınamıyor. İşlem sunucuda sürer; Pusula Ayarlar ekranından durumu yenileyebilirsiniz.');
                }
            }
            if (!controller.signal.aborted && ++polls < 60) timer = window.setTimeout(poll, 5000);
            else if (!controller.signal.aborted && mounted.current)
                setMessage('Eşitleme sunucuda devam ediyor. Bu pencereyi kapatabilir, Pusula Ayarlar ekranından durumu yenileyebilirsiniz.');
        };
        timer = window.setTimeout(poll, 1500);
        return () => { controller.abort(); window.clearTimeout(timer); };
    }, [phase, showProgress, stateToken]);

    useEffect(() => {
        const context = metaConfig;
        const coordinator = coordinatorRef.current;
        if (!context || !coordinator) return undefined;

        const expiresInMs = context.expiresAtMs - Date.now();
        const expiryTimer = window.setTimeout(() => {
            if (authorized.current) return;
            coordinator.cancel();
            setSdkReady(false);
            setPhase('expired');
            setMessage('Bu bağlantının süresi doldu. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
        }, expiresInMs);

        const messageListener = (event) => {
            if (!coordinator.isActive()) return;
            const data = parseEmbeddedSignupEvent(event.origin, event.data);
            if (!data) return;
            if (data.event === 'FINISH') {
                coordinator.event(data);
            } else if (data.event === 'CANCEL') {
                coordinator.cancel();
                setPhase('idle');
                setMessage('Bağlantı işlemi tamamlanmadı. Hazır olduğunuzda yeniden deneyebilirsiniz.');
            } else if (data.event === 'ERROR') {
                coordinator.cancel();
                setPhase('error');
                setMessage('Meta bağlantı işlemi tamamlanamadı. Pusula Ayarlar ekranından durumu kontrol edin.');
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
    }, [complete, metaConfig]);

    const launch = () => {
        const coordinator = coordinatorRef.current;
        if (!coordinator) return;
        if (!sdkReady || !window.FB || !stateToken || !metaConfig) return;
        if (metaConfig.expiresAtMs <= Date.now()) {
            setSdkReady(false);
            setPhase('expired');
            setMessage('Bu bağlantının süresi doldu. Pusula Ayarlar ekranından yeni bir bağlantı oluşturun.');
            return;
        }
        setPhase('working');
        const currentAttempt = coordinator.start();
        setMessage('Meta bağlantı penceresi açılıyor…');
        window.FB.login((response) => {
            if (response.authResponse?.code) {
                coordinator.code(currentAttempt, response.authResponse.code);
            } else {
                if (!coordinator.cancel(currentAttempt)) return;
                setPhase('idle');
                setMessage('Meta yetkilendirmesi tamamlanmadı. Hazır olduğunuzda tekrar deneyin.');
            }
        }, embeddedSignupOptions(metaConfig.configurationId));
    };

    return (
        <main className="min-h-screen bg-slate-950 font-sans antialiased text-white grid place-items-center px-5 py-10">
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
                {!['success', 'syncing', 'error', 'expired'].includes(phase) && (
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
