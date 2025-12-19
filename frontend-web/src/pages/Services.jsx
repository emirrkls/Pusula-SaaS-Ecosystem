import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Minus, CheckCircle } from 'lucide-react';

const Services = () => {
    const servicesList = [
        {
            id: "split-klima",
            title: "Split Klima Sistemleri",
            desc: "Ev ve küçük ofisler için ideal iklimlendirme çözümü. Enerji tasarruflu, sessiz ve yüksek performanslı split klimaların satışı, montajı ve bakımı konusunda uzmanız.",
            features: ["Yüksek Enerji Verimliliği (A++)", "Sessiz Çalışma Modu", "Hızlı Soğutma/Isıtma", "5 Yıl Garanti"],
            image: "https://images.unsplash.com/photo-1614634351680-31362e51927c?q=80&w=800&auto=format&fit=crop"
        },
        {
            id: "vrf",
            title: "VRF Sistemleri",
            desc: "Oteller, plazalar ve büyük binalar için merkezi iklimlendirme. Tek bir dış ünite ile birden çok iç üniteyi kontrol edin. Maksimum enerji tasarrufu ve konfor sağlar.",
            features: ["Merkezi Kontrol", "Bölgesel İklimlendirme", "Düşük İşletme Maliyeti", "Uzun Ömürlü Sistemler"],
            image: "https://images.unsplash.com/photo-1581094288338-2314dddb7ece?q=80&w=800&auto=format&fit=crop"
        },
        {
            id: "kombi",
            title: "Kombi Bakım & Onarım",
            desc: "Kombinizin verimli çalışması ve güvenliğiniz için periyodik bakım şarttır. Petek temizliği ve kombi arıza onarımlarında 7/24 hizmetinizdeyiz.",
            features: ["Petek Temizliği", "Gaz Kaçak Kontrolü", "Verimlilik Testi", "Orijinal Yedek Parça"],
            image: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=800&auto=format&fit=crop"
        },
        {
            id: "montaj",
            title: "Profesyonel Montaj",
            desc: "Klimanızın performansını doğrudan etkileyen en önemli faktör doğru montajdır. Uzman ekiplerimizle estetik ve güvenli montaj hizmeti sunuyoruz.",
            features: ["Ücretsiz Keşif", "Vakumla Montaj", "Estetik Borulama", "Temiz İşçilik"],
            image: "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?q=80&w=800&auto=format&fit=crop"
        }
    ];

    const faqs = [
        {
            q: "Klima bakımı ne sıklıkla yapılmalı?",
            a: "Klimanızın verimli çalışması ve sağlığınız için yılda en az 2 kez (Yaz ve Kış sezonu öncesi) bakım yaptırmanızı öneriyoruz."
        },
        {
            q: "Servis ücreti ne kadar?",
            a: "Servis ücretlerimiz yapılacak işleme ve cihaz tipine göre değişmektedir. Güncel fiyat bilgisi için lütfen bizimle iletişime geçin."
        },
        {
            q: "Garanti süresi nedir?",
            a: "Yaptığımız tüm parça değişimleri ve işçilik hizmetleri 1 yıl firmamız garantisi altındadır."
        },
        {
            q: "Hangi markalara hizmet veriyorsunuz?",
            a: "Daikin, Mitsubishi, Arçelik, Vestel, Bosch başta olmak üzere piyasadaki tüm marka ve modellere teknik servis hizmeti vermekteyiz."
        }
    ];

    const [activeAccordion, setActiveAccordion] = useState(null);

    return (
        <div className="pt-20 bg-gray-50 min-h-screen">
            {/* Header */}
            <div className="bg-brand-dark text-white py-16 text-center">
                <div className="container mx-auto px-4">
                    <h1 className="text-4xl font-bold mb-4">Hizmetlerimiz</h1>
                    <p className="text-gray-300 max-w-2xl mx-auto">
                        İhtiyacınıza uygun, profesyonel ve garantili iklimlendirme çözümleri.
                    </p>
                </div>
            </div>

            {/* Services List */}
            <div className="container mx-auto px-4 py-16 space-y-20">
                {servicesList.map((service, index) => (
                    <motion.div
                        key={service.id}
                        initial={{ opacity: 0, y: 30 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ duration: 0.5 }}
                        className={`flex flex-col ${index % 2 === 1 ? 'md:flex-row-reverse' : 'md:flex-row'} gap-8 items-center`}
                    >
                        <div className="w-full md:w-1/2">
                            <img
                                src={service.image}
                                alt={service.title}
                                className="rounded-2xl shadow-xl w-full h-[400px] object-cover"
                            />
                        </div>
                        <div className="w-full md:w-1/2 space-y-6">
                            <h2 className="text-3xl font-bold text-brand-dark">{service.title}</h2>
                            <p className="text-gray-600 text-lg leading-relaxed">{service.desc}</p>
                            <ul className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                {service.features.map((feature, idx) => (
                                    <li key={idx} className="flex items-center gap-2 text-gray-700 font-medium">
                                        <CheckCircle className="w-5 h-5 text-brand-cyan" />
                                        {feature}
                                    </li>
                                ))}
                            </ul>
                        </div>
                    </motion.div>
                ))}
            </div>

            {/* FAQ Section */}
            <div className="bg-white py-20">
                <div className="container mx-auto px-4 max-w-3xl">
                    <div className="text-center mb-12">
                        <h2 className="text-3xl font-bold text-brand-dark">Sıkça Sorulan Sorular</h2>
                    </div>
                    <div className="space-y-4">
                        {faqs.map((faq, index) => (
                            <div key={index} className="border border-gray-200 rounded-lg overflow-hidden">
                                <button
                                    onClick={() => setActiveAccordion(activeAccordion === index ? null : index)}
                                    className="w-full flex items-center justify-between p-6 bg-white hover:bg-gray-50 transition-colors text-left"
                                >
                                    <span className="font-semibold text-brand-dark text-lg">{faq.q}</span>
                                    {activeAccordion === index ? (
                                        <Minus className="text-brand-cyan" />
                                    ) : (
                                        <Plus className="text-brand-cyan" />
                                    )}
                                </button>
                                <AnimatePresence>
                                    {activeAccordion === index && (
                                        <motion.div
                                            initial={{ height: 0, opacity: 0 }}
                                            animate={{ height: 'auto', opacity: 1 }}
                                            exit={{ height: 0, opacity: 0 }}
                                            className="overflow-hidden"
                                        >
                                            <div className="p-6 pt-0 text-gray-600 bg-gray-50 border-t border-gray-100">
                                                {faq.a}
                                            </div>
                                        </motion.div>
                                    )}
                                </AnimatePresence>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
};

export default Services;
