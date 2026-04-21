import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { MapPin, Phone, Mail, Send, CheckCircle } from 'lucide-react';

const Contact = () => {
    useEffect(() => {
        document.title = 'İletişim - Pusula İklimlendirme Didim | Klima Servisi Randevu';
    }, []);
    const [formData, setFormData] = useState({
        name: '',
        phone: '',
        deviceType: 'Klima',
        address: '',
        note: ''
    });
    const [isSubmitted, setIsSubmitted] = useState(false);

    const handleChange = (e) => {
        setFormData({ ...formData, [e.target.name]: e.target.value });
    };

    const handleSubmit = (e) => {
        e.preventDefault();
        // Simulate API call
        console.log('Form Submitted:', formData);

        // Show success state
        setIsSubmitted(true);

        // Reset after 3 seconds
        setTimeout(() => {
            setIsSubmitted(false);
            setFormData({
                name: '',
                phone: '',
                deviceType: 'Klima',
                address: '',
                note: ''
            });
        }, 3000);
    };

    return (
        <div className="pt-20 bg-gray-50 min-h-screen">
            <div className="bg-brand-dark text-white py-16 text-center">
                <h1 className="text-4xl font-bold mb-4">İletişim & Randevu</h1>
                <p className="text-gray-300">Sorularınız için bize ulaşın veya hemen servis talebi oluşturun.</p>
            </div>

            <div className="container mx-auto px-4 py-16">
                <div className="grid grid-cols-1 lg:grid-cols-2 gap-12">

                    {/* Contact Info */}
                    <div className="space-y-8">
                        <div className="bg-white p-8 rounded-2xl shadow-lg">
                            <h2 className="text-2xl font-bold text-brand-dark mb-6">İletişim Bilgileri</h2>
                            <div className="space-y-6">
                                <div className="flex items-start gap-4">
                                    <div className="bg-brand-cyan/10 p-3 rounded-full">
                                        <MapPin className="w-6 h-6 text-brand-cyan" />
                                    </div>
                                    <div>
                                        <h3 className="font-semibold text-gray-900">Adres</h3>
                                        <p className="text-gray-600">Çamlık Mah. Ege Cad. No76/B<br />Didim/AYDIN</p>
                                    </div>
                                </div>
                                <div className="flex items-start gap-4">
                                    <div className="bg-brand-cyan/10 p-3 rounded-full">
                                        <Phone className="w-6 h-6 text-brand-cyan" />
                                    </div>
                                    <div>
                                        <h3 className="font-semibold text-gray-900">Telefon</h3>
                                        <a href="tel:+905400250925" className="text-gray-600 hover:text-brand-cyan transition-colors">+90 540 025 09 25</a>
                                        <p className="text-gray-500 text-sm">7/24 Acil Destek Hattı</p>
                                    </div>
                                </div>
                                <div className="flex items-start gap-4">
                                    <div className="bg-brand-cyan/10 p-3 rounded-full">
                                        <Mail className="w-6 h-6 text-brand-cyan" />
                                    </div>
                                    <div>
                                        <h3 className="font-semibold text-gray-900">E-posta</h3>
                                        <a href="mailto:pusulaiklimlendirme.didim@gmail.com" className="text-gray-600 hover:text-brand-cyan transition-colors">pusulaiklimlendirme.didim@gmail.com</a>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Google Maps */}
                        <div className="rounded-2xl overflow-hidden shadow-lg h-72 w-full relative group">
                            <iframe
                                title="Pusula İklimlendirme Konum"
                                src="https://maps.google.com/maps?q=37.3579,27.2685&t=&z=16&ie=UTF8&iwloc=&output=embed"
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
                                    <span>Çamlık Mah. Ege Cad. No76/B, Didim/AYDIN</span>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Service Request Form */}
                    <div className="bg-white p-8 rounded-2xl shadow-xl border-t-4 border-brand-cyan">
                        <h2 className="text-2xl font-bold text-brand-dark mb-2">Servis Talebi Oluştur</h2>
                        <p className="text-gray-600 mb-8">Formu doldurun, en kısa sürede size dönüş yapalım.</p>

                        {isSubmitted ? (
                            <motion.div
                                initial={{ opacity: 0, scale: 0.9 }}
                                animate={{ opacity: 1, scale: 1 }}
                                className="bg-green-50 border border-green-200 rounded-xl p-8 text-center"
                            >
                                <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
                                <h3 className="text-xl font-bold text-green-800 mb-2">Talebiniz Alındı!</h3>
                                <p className="text-green-700">Uzman ekibimiz en kısa sürede sizinle iletişime geçecektir.</p>
                            </motion.div>
                        ) : (
                            <form onSubmit={handleSubmit} className="space-y-6">
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Ad Soyad</label>
                                        <input
                                            type="text"
                                            name="name"
                                            required
                                            value={formData.name}
                                            onChange={handleChange}
                                            className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all"
                                            placeholder="Adınız Soyadınız"
                                        />
                                    </div>
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2">Telefon</label>
                                        <input
                                            type="tel"
                                            name="phone"
                                            required
                                            value={formData.phone}
                                            onChange={handleChange}
                                            className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all"
                                            placeholder="0555 555 55 55"
                                        />
                                    </div>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Cihaz Tipi</label>
                                    <select
                                        name="deviceType"
                                        value={formData.deviceType}
                                        onChange={handleChange}
                                        className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all"
                                    >
                                        <option value="Klima">Split Klima</option>
                                        <option value="VRF">VRF Sistem</option>
                                        <option value="Kombi">Kombi</option>
                                        <option value="Diger">Diğer</option>
                                    </select>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Adres</label>
                                    <textarea
                                        name="address"
                                        required
                                        value={formData.address}
                                        onChange={handleChange}
                                        rows="3"
                                        className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all"
                                        placeholder="Açık adresiniz..."
                                    ></textarea>
                                </div>

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">Notunuz (Opsiyonel)</label>
                                    <textarea
                                        name="note"
                                        value={formData.note}
                                        onChange={handleChange}
                                        rows="2"
                                        className="w-full px-4 py-3 rounded-lg border border-gray-300 focus:ring-2 focus:ring-brand-cyan focus:border-transparent outline-none transition-all"
                                        placeholder="Arıza hakkında kısa bilgi..."
                                    ></textarea>
                                </div>

                                <button
                                    type="submit"
                                    className="w-full bg-brand-cyan hover:bg-cyan-400 text-white font-bold py-4 rounded-lg shadow-lg hover:shadow-cyan-500/30 transition-all flex items-center justify-center gap-2"
                                >
                                    <Send className="w-5 h-5" />
                                    Talebi Gönder
                                </button>
                            </form>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Contact;
