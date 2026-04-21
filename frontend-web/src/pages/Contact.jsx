import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { MapPin, Phone, Mail, Send, CheckCircle, AlertCircle, Loader2, XCircle } from 'lucide-react';

// ===== i18n — Türkçe UI Metinleri =====
const i18n = {
    pageTitle: 'İletişim - Pusula İklimlendirme Didim | Klima Servisi Randevu',
    heading: 'İletişim & Randevu',
    subheading: 'Sorularınız için bize ulaşın veya hemen servis talebi oluşturun.',
    contactInfo: 'İletişim Bilgileri',
    address: 'Adres',
    addressValue: 'Çamlık Mah. Ege Cad. No76/B\nDidim/AYDIN',
    phone: 'Telefon',
    phoneValue: '+90 540 025 09 25',
    phoneSupport: '7/24 Acil Destek Hattı',
    email: 'E-posta',
    emailValue: 'pusulaiklimlendirme.didim@gmail.com',
    formTitle: 'Servis Talebi Oluştur',
    formSubtitle: 'Formu doldurun, en kısa sürede size dönüş yapalım.',
    labelName: 'Ad Soyad',
    labelPhone: 'Telefon',
    labelDeviceType: 'Cihaz Tipi',
    labelAddress: 'Adres',
    labelNote: 'Notunuz (Opsiyonel)',
    placeholderName: 'Adınız Soyadınız',
    placeholderPhone: '0 (555) 555 55 55',
    placeholderAddress: 'Açık adresiniz...',
    placeholderNote: 'Arıza hakkında kısa bilgi...',
    deviceOptions: {
        Klima: 'Split Klima',
        VRF: 'VRF Sistem',
        Kombi: 'Kombi',
        Diger: 'Diğer',
    },
    submitButton: 'Talebi Gönder',
    submittingButton: 'Gönderiliyor...',
    successTitle: 'Talebiniz Alındı!',
    successMessage: 'Uzman ekibimiz en kısa sürede sizinle iletişime geçecektir.',
    errorTitle: 'Bir Hata Oluştu',
    errorGeneric: 'Talebiniz gönderilemedi. Lütfen telefonla bize ulaşın.',
    errorRateLimit: 'Çok fazla istek gönderdiniz. Lütfen bir dakika sonra tekrar deneyin.',
    errorValidation: 'Lütfen formdaki hataları düzeltin.',
    errorNetwork: 'Sunucuya bağlanılamadı. İnternet bağlantınızı kontrol edin.',
    errorPhoneInvalid: 'Lütfen geçerli bir telefon numarası giriniz (10 haneli).',
    mapLabel: 'Çamlık Mah. Ege Cad. No76/B, Didim/AYDIN',
};

// ===== API Yapılandırması =====
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const COMPANY_ID = import.meta.env.VITE_COMPANY_ID || '1';

// ===== Telefon Numarası Yardımcı Fonksiyonları =====

/**
 * Kullanıcının girdiği ham telefon string'inden saf rakamları çıkarır
 * ve 10 haneli standart formata normalize eder.
 * 
 * Örnekler:
 *   '0 (553) 863 85 66' → '5538638566'
 *   '+90 553 863 85 66' → '5538638566'
 *   '05538638566'        → '5538638566'
 *   '553 863 85 66'      → '5538638566'
 * 
 * @param {string} raw - Kullanıcının girdiği ham numara
 * @returns {string|null} 10 haneli saf numara veya geçersizse null
 */
function normalizePhone(raw) {
    // 1. Rakam olmayan her şeyi temizle (boşluk, tire, parantez, + vb.)
    const digitsOnly = raw.replace(/\D/g, '');

    let normalized = digitsOnly;

    // 2. Başında '90' varsa ve toplam 12 hane ise → ülke kodu kırp
    if (normalized.startsWith('90') && normalized.length === 12) {
        normalized = normalized.substring(2);
    }

    // 3. Başında '0' varsa ve toplam 11 hane ise → baştaki 0'ı kırp
    if (normalized.startsWith('0') && normalized.length === 11) {
        normalized = normalized.substring(1);
    }

    // 4. Tam 10 hane değilse geçersiz
    if (normalized.length !== 10) {
        return null;
    }

    return normalized;
}

/**
 * Telefon numarasını kullanıcı dostu formatta gösterir.
 * Kullanıcı yazarken anlık maskeleme sağlar.
 * 
 * Rakam sayısına göre kademeli format:
 *   1-1  hane  → '0'
 *   2-4  hane  → '0 (5XX'
 *   5-7  hane  → '0 (553) XXX'
 *   8-9  hane  → '0 (553) 863 XX'
 *   10-11 hane → '0 (553) 863 85 66'
 * 
 * @param {string} raw - Kullanıcının girdiği ham string
 * @returns {string} Formatlanmış görüntüleme string'i
 */
function formatPhoneDisplay(raw) {
    // Sadece rakamları al
    let digits = raw.replace(/\D/g, '');

    // Başındaki ülke kodunu (90) temizle — kullanıcı +90 yazmış olabilir
    if (digits.startsWith('90') && digits.length > 11) {
        digits = digits.substring(2);
    }

    // Maksimum 11 hane (0 + 10 hane) ile sınırla
    if (digits.length > 11) {
        digits = digits.substring(0, 11);
    }

    // Kademeli maskeleme: 0 (5XX) XXX XX XX
    const len = digits.length;
    if (len === 0) return '';
    if (len <= 1) return digits; // '0'
    if (len <= 4) return `${digits[0]} (${digits.substring(1)}`; // '0 (55'
    if (len <= 7) return `${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4)}`; // '0 (553) 863'
    if (len <= 9) return `${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)} ${digits.substring(7)}`; // '0 (553) 863 85'
    return `${digits[0]} (${digits.substring(1, 4)}) ${digits.substring(4, 7)} ${digits.substring(7, 9)} ${digits.substring(9, 11)}`; // '0 (553) 863 85 66'
}

/**
 * Servis talebini backend API'sine gönderir.
 * 
 * @param {Object} formData - Form verileri
 * @returns {Promise<Object>} API yanıtı
 * @throws {Object} Hata durumunda { status, data } fırlatır
 */
async function submitServiceRequest(formData) {
    const payload = {
        companyId: parseInt(COMPANY_ID, 10),
        customerName: formData.name.trim(),
        customerPhone: formData.phone.trim(),
        customerAddress: formData.address.trim(),
        deviceType: formData.deviceType,
        description: formData.note?.trim() || null,
        // Honeypot alanı — gerçek kullanıcılar bunu görmez
        website: formData.website || '',
    };

    const response = await fetch(`${API_BASE_URL}/api/public/service-request`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
        },
        body: JSON.stringify(payload),
    });

    const data = await response.json();

    if (!response.ok) {
        // API'den gelen hata yanıtını fırlat
        throw { status: response.status, data };
    }

    return data;
}

// ===== Toast Notification Bileşeni =====
const Toast = ({ type, title, message, onClose }) => {
    const config = {
        success: {
            icon: <CheckCircle className="w-6 h-6" />,
            bgClass: 'bg-green-50 border-green-200',
            iconClass: 'text-green-500',
            titleClass: 'text-green-800',
            messageClass: 'text-green-700',
        },
        error: {
            icon: <XCircle className="w-6 h-6" />,
            bgClass: 'bg-red-50 border-red-200',
            iconClass: 'text-red-500',
            titleClass: 'text-red-800',
            messageClass: 'text-red-700',
        },
        warning: {
            icon: <AlertCircle className="w-6 h-6" />,
            bgClass: 'bg-yellow-50 border-yellow-200',
            iconClass: 'text-yellow-500',
            titleClass: 'text-yellow-800',
            messageClass: 'text-yellow-700',
        },
    };

    const c = config[type] || config.error;

    return (
        <motion.div
            initial={{ opacity: 0, y: -20, scale: 0.95 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.95 }}
            transition={{ duration: 0.3 }}
            className={`fixed top-24 right-4 z-50 max-w-sm w-full border rounded-xl p-4 shadow-xl ${c.bgClass}`}
        >
            <div className="flex items-start gap-3">
                <div className={c.iconClass}>{c.icon}</div>
                <div className="flex-1">
                    <h4 className={`font-semibold ${c.titleClass}`}>{title}</h4>
                    <p className={`text-sm mt-1 ${c.messageClass}`}>{message}</p>
                </div>
                <button
                    onClick={onClose}
                    className="text-gray-400 hover:text-gray-600 transition-colors"
                    aria-label="Bildirimi kapat"
                >
                    ✕
                </button>
            </div>
        </motion.div>
    );
};

// ===== Ana Contact Bileşeni =====
const Contact = () => {
    useEffect(() => {
        document.title = i18n.pageTitle;
    }, []);

    // Form durumu
    const [formData, setFormData] = useState({
        name: '',
        phone: '',
        deviceType: 'Klima',
        address: '',
        note: '',
        website: '', // Honeypot — CSS ile gizli
    });

    // UI durumları
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [toast, setToast] = useState(null); // { type, title, message }
    const [fieldErrors, setFieldErrors] = useState({}); // Backend validation hataları

    const handleChange = (e) => {
        const { name, value } = e.target;

        if (name === 'phone') {
            // Telefon alanı — anlık maskeleme uygula
            // Kullanıcı ne yazarsa yazsın, güzel formatlanmış hali gösterilir
            setFormData({ ...formData, phone: formatPhoneDisplay(value) });
        } else {
            setFormData({ ...formData, [name]: value });
        }

        // Kullanıcı düzeltme yaparken ilgili alan hatasını temizle
        if (fieldErrors[name]) {
            setFieldErrors((prev) => {
                const next = { ...prev };
                delete next[name];
                return next;
            });
        }
    };

    /**
     * Form gönderim handler'ı.
     * Backend API'sine asenkron POST isteği gönderir.
     * Başarı/hata durumlarını UI'da Toast ile gösterir.
     */
    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        setFieldErrors({});
        setToast(null);

        // --- Telefon Numarası Ön Temizleme ---
        // Kullanıcının girdiği formatı (boşluk, tire, parantez) temizleyip
        // saf 10 haneli numaraya dönüştür
        const cleanedPhone = normalizePhone(formData.phone);

        if (!cleanedPhone) {
            // Geçersiz numara — kullanıcıya bildir ve işlemi durdur
            setFieldErrors({ phone: i18n.errorPhoneInvalid });
            setToast({
                type: 'error',
                title: i18n.errorTitle,
                message: i18n.errorPhoneInvalid,
            });
            setIsSubmitting(false);
            setTimeout(() => setToast(null), 6000);
            return;
        }

        // Temizlenmiş telefon numarasıyla form verisini hazırla
        const submissionData = {
            ...formData,
            phone: cleanedPhone, // '5538638566' formatında backend'e gider
        };

        try {
            await submitServiceRequest(submissionData);

            // ✅ Başarılı — formu sıfırla ve başarı mesajı göster
            setToast({
                type: 'success',
                title: i18n.successTitle,
                message: i18n.successMessage,
            });

            setFormData({
                name: '',
                phone: '',
                deviceType: 'Klima',
                address: '',
                note: '',
                website: '',
            });

            // Toast'u 6 saniye sonra otomatik kapat
            setTimeout(() => setToast(null), 6000);

        } catch (error) {
            if (error?.status === 429) {
                // ⚠️ Rate limit aşıldı
                setToast({
                    type: 'warning',
                    title: i18n.errorTitle,
                    message: i18n.errorRateLimit,
                });
            } else if (error?.status === 400 && error?.data?.fields) {
                // ❌ Validation hataları — alan bazlı göster
                setFieldErrors(mapBackendFieldErrors(error.data.fields));
                setToast({
                    type: 'error',
                    title: i18n.errorTitle,
                    message: i18n.errorValidation,
                });
            } else if (error instanceof TypeError) {
                // 🌐 Network hatası (sunucuya ulaşılamadı)
                setToast({
                    type: 'error',
                    title: i18n.errorTitle,
                    message: i18n.errorNetwork,
                });
            } else {
                // 💥 Genel hata
                setToast({
                    type: 'error',
                    title: i18n.errorTitle,
                    message: error?.data?.error || i18n.errorGeneric,
                });
            }

            setTimeout(() => setToast(null), 8000);
        } finally {
            setIsSubmitting(false);
        }
    };

    /**
     * Backend alan adlarını frontend form alanlarına eşler.
     * Backend: customerName → Frontend: name
     */
    function mapBackendFieldErrors(backendFields) {
        const mapping = {
            customerName: 'name',
            customerPhone: 'phone',
            customerAddress: 'address',
            description: 'note',
            deviceType: 'deviceType',
        };

        const mapped = {};
        for (const [backendKey, message] of Object.entries(backendFields)) {
            const frontendKey = mapping[backendKey] || backendKey;
            mapped[frontendKey] = message;
        }
        return mapped;
    }

    return (
        <div className="pt-20 bg-gray-50 min-h-screen">
            {/* Toast Notification */}
            <AnimatePresence>
                {toast && (
                    <Toast
                        type={toast.type}
                        title={toast.title}
                        message={toast.message}
                        onClose={() => setToast(null)}
                    />
                )}
            </AnimatePresence>

            <div className="bg-brand-dark text-white py-16 text-center">
                <h1 className="text-4xl font-bold mb-4">{i18n.heading}</h1>
                <p className="text-gray-300">{i18n.subheading}</p>
            </div>

            <div className="container mx-auto px-4 py-16">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">

                    {/* Contact Info */}
                    <div className="space-y-8">
                        <div className="bg-white p-8 rounded-2xl shadow-lg">
                            <h2 className="text-2xl font-bold text-brand-dark mb-6">{i18n.contactInfo}</h2>
                            <div className="space-y-6">
                                <div className="flex items-start gap-4">
                                    <div className="bg-brand-cyan/10 p-3 rounded-full">
                                        <MapPin className="w-6 h-6 text-brand-cyan" />
                                    </div>
                                    <div>
                                        <h3 className="font-semibold text-gray-900">{i18n.address}</h3>
                                        <p className="text-gray-600">Çamlık Mah. Ege Cad. No76/B<br />Didim/AYDIN</p>
                                    </div>
                                </div>
                                <div className="flex items-start gap-4">
                                    <div className="bg-brand-cyan/10 p-3 rounded-full">
                                        <Phone className="w-6 h-6 text-brand-cyan" />
                                    </div>
                                    <div>
                                        <h3 className="font-semibold text-gray-900">{i18n.phone}</h3>
                                        <a href="tel:+905400250925" className="text-gray-600 hover:text-brand-cyan transition-colors">{i18n.phoneValue}</a>
                                        <p className="text-gray-500 text-sm">{i18n.phoneSupport}</p>
                                    </div>
                                </div>
                                <div className="flex items-start gap-4">
                                    <div className="bg-brand-cyan/10 p-3 rounded-full">
                                        <Mail className="w-6 h-6 text-brand-cyan" />
                                    </div>
                                    <div>
                                        <h3 className="font-semibold text-gray-900">{i18n.email}</h3>
                                        <a href="mailto:pusulaiklimlendirme.didim@gmail.com" className="text-gray-600 hover:text-brand-cyan transition-colors">{i18n.emailValue}</a>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Google Maps */}
                        <div className="rounded-2xl overflow-hidden shadow-lg h-72 w-full relative group">
                            <iframe
                                title="Pusula İklimlendirme Konum"
                                src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d198.20523582074577!2d27.26841455155094!3d37.35946178726738!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x14be87002fa82177%3A0xeb8373b878688da7!2sPusula%20%C4%B0klimlendirme!5e0!3m2!1str!2str!4v1776796038740!5m2!1str!2str"
                                width="100%"
                                height="100%"
                                style={{ border: 0 }}
                                allowFullScreen=""
                                loading="lazy"
                                referrerPolicy="no-referrer-when-downgrade"
                                className="absolute inset-0"
                            ></iframe>
                            {/* Map overlay on hover */}
                            <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-brand-dark/80 to-transparent p-4 translate-y-full group-hover:translate-y-0 transition-transform duration-300">
                                <div className="flex items-center gap-2 text-white text-sm">
                                    <MapPin className="w-4 h-4 text-brand-cyan" />
                                    <span>{i18n.mapLabel}</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Service Request Form */}
                    <div className="bg-white p-8 rounded-2xl shadow-xl border-t-4 border-brand-cyan">
                        <h2 className="text-2xl font-bold text-brand-dark mb-2">{i18n.formTitle}</h2>
                        <p className="text-gray-600 mb-8">{i18n.formSubtitle}</p>

                        <form onSubmit={handleSubmit} className="space-y-6" id="service-request-form">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                {/* Ad Soyad */}
                                <div>
                                    <label htmlFor="contact-name" className="block text-sm font-medium text-gray-700 mb-2">
                                        {i18n.labelName}
                                    </label>
                                    <input
                                        id="contact-name"
                                        type="text"
                                        name="name"
                                        required
                                        value={formData.name}
                                        onChange={handleChange}
                                        disabled={isSubmitting}
                                        className={`w-full px-4 py-3 rounded-lg border ${
                                            fieldErrors.name ? 'border-red-400 ring-2 ring-red-200' : 'border-gray-300'
                                        } focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all disabled:opacity-60 disabled:cursor-not-allowed`}
                                        placeholder={i18n.placeholderName}
                                    />
                                    {fieldErrors.name && (
                                        <p className="text-red-500 text-xs mt-1">{fieldErrors.name}</p>
                                    )}
                                </div>

                                {/* Telefon */}
                                <div>
                                    <label htmlFor="contact-phone" className="block text-sm font-medium text-gray-700 mb-2">
                                        {i18n.labelPhone}
                                    </label>
                                    <input
                                        id="contact-phone"
                                        type="text"
                                        inputMode="tel"
                                        autoComplete="tel"
                                        name="phone"
                                        required
                                        value={formData.phone}
                                        onChange={handleChange}
                                        disabled={isSubmitting}
                                        className={`w-full px-4 py-3 rounded-lg border ${
                                            fieldErrors.phone ? 'border-red-400 ring-2 ring-red-200' : 'border-gray-300'
                                        } focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all disabled:opacity-60 disabled:cursor-not-allowed`}
                                        placeholder={i18n.placeholderPhone}
                                    />
                                    {fieldErrors.phone && (
                                        <p className="text-red-500 text-xs mt-1">{fieldErrors.phone}</p>
                                    )}
                                </div>
                            </div>

                            {/* Cihaz Tipi */}
                            <div>
                                <label htmlFor="contact-device-type" className="block text-sm font-medium text-gray-700 mb-2">
                                    {i18n.labelDeviceType}
                                </label>
                                <select
                                    id="contact-device-type"
                                    name="deviceType"
                                    value={formData.deviceType}
                                    onChange={handleChange}
                                    disabled={isSubmitting}
                                    className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                                >
                                    {Object.entries(i18n.deviceOptions).map(([value, label]) => (
                                        <option key={value} value={value}>{label}</option>
                                    ))}
                                </select>
                            </div>

                            {/* Adres */}
                            <div>
                                <label htmlFor="contact-address" className="block text-sm font-medium text-gray-700 mb-2">
                                    {i18n.labelAddress}
                                </label>
                                <textarea
                                    id="contact-address"
                                    name="address"
                                    required
                                    value={formData.address}
                                    onChange={handleChange}
                                    disabled={isSubmitting}
                                    rows="3"
                                    className={`w-full px-4 py-3 rounded-lg border ${
                                        fieldErrors.address ? 'border-red-400 ring-2 ring-red-200' : 'border-gray-300'
                                    } focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all disabled:opacity-60 disabled:cursor-not-allowed`}
                                    placeholder={i18n.placeholderAddress}
                                ></textarea>
                                {fieldErrors.address && (
                                    <p className="text-red-500 text-xs mt-1">{fieldErrors.address}</p>
                                )}
                            </div>

                            {/* Not */}
                            <div>
                                <label htmlFor="contact-note" className="block text-sm font-medium text-gray-700 mb-2">
                                    {i18n.labelNote}
                                </label>
                                <textarea
                                    id="contact-note"
                                    name="note"
                                    value={formData.note}
                                    onChange={handleChange}
                                    disabled={isSubmitting}
                                    rows="2"
                                    className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                                    placeholder={i18n.placeholderNote}
                                ></textarea>
                            </div>

                            {/* Honeypot — Bot Tuzağı (CSS ile gizli) */}
                            <div
                                style={{ position: 'absolute', left: '-9999px', opacity: 0, height: 0, overflow: 'hidden' }}
                                aria-hidden="true"
                                tabIndex={-1}
                            >
                                <label htmlFor="contact-website">Website</label>
                                <input
                                    id="contact-website"
                                    type="text"
                                    name="website"
                                    value={formData.website}
                                    onChange={handleChange}
                                    autoComplete="off"
                                    tabIndex={-1}
                                />
                            </div>

                            {/* Gönder Butonu */}
                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className="w-full bg-brand-cyan hover:bg-cyan-400 text-white font-bold py-4 rounded-lg shadow-lg hover:shadow-cyan-500/30 transition-all flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed disabled:hover:bg-brand-cyan"
                            >
                                {isSubmitting ? (
                                    <>
                                        <Loader2 className="w-5 h-5 animate-spin" />
                                        {i18n.submittingButton}
                                    </>
                                ) : (
                                    <>
                                        <Send className="w-5 h-5" />
                                        {i18n.submitButton}
                                    </>
                                )}
                            </button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Contact;
