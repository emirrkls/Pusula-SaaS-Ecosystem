import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Plus, Minus, CheckCircle } from 'lucide-react';

const Services = () => {
    useEffect(() => {
        document.title = 'Hizmetlerimiz - Klima, VRF, Isı Pompası, Güneş Enerjisi | Pusula İklimlendirme Didim';
    }, []);
    /**
     * SERVICE DETAIL IMAGES - Place your images in: public/assets/img/
     * These are the same as Home page services:
     *   - service-1.jpg (Split Klima)
     *   - service-2.jpg (VRF Systems)
     *   - service-3.jpg (Kombi/Boiler)
     *   - service-4.jpg (Montaj/Installation)
     */
    const servicesList = [
        {
            id: "split-klima",
            title: "Split Klima Sistemleri",
            desc: "Ev ve küçük ofisler için ideal iklimlendirme çözümü. Enerji tasarruflu, sessiz ve yüksek performanslı split klimaların satışı, montajı ve bakımı konusunda uzmanız.",
            features: ["Yüksek Enerji Verimliliği (A++)", "Sessiz Çalışma Modu", "Hızlı Soğutma/Isıtma", "5 Yıl Garanti"],
            image: "/assets/img/service-1.jpg"
        },
        {
            id: "vrf",
            title: "VRF Sistemleri",
            desc: "Oteller, plazalar ve büyük binalar için merkezi iklimlendirme. Tek bir dış ünite ile birden çok iç üniteyi kontrol edin. Maksimum enerji tasarrufu ve konfor sağlar.",
            features: ["Merkezi Kontrol", "Bölgesel İklimlendirme", "Düşük İşletme Maliyeti", "Uzun Ömürlü Sistemler"],
            image: "/assets/img/service-2.jpg"
        },
        {
            id: "isi-pompasi",
            title: "Isı Pompası Sistemleri",
            desc: "Yenilenebilir enerji ile ısıtma ve soğutma. Isı pompası sistemleri ile hem çevre dostu hem de ekonomik iklimlendirme çözümleri sunuyoruz.",
            features: ["Yüksek Enerji Verimliliği", "Düşük Karbon Ayak İzi", "4 Mevsim Kullanım", "Devlet Teşvikleri"],
            image: "/assets/img/service-3.jpg"
        },
        {
            id: "montaj",
            title: "Profesyonel Montaj & Keşif",
            desc: "Klimanızın performansını doğrudan etkileyen en önemli faktör doğru montajdır. Uzman ekiplerimizle estetik ve güvenli montaj hizmeti sunuyoruz.",
            features: ["Ücretsiz Keşif", "Vakumla Montaj", "Estetik Borulama", "Temiz İşçilik"],
            image: "/assets/img/service-4.jpg",
            objectPosition: "center 30%"
        },
        {
            id: "gunes-enerjisi",
            title: "Güneş Enerjisi Sistemleri",
            desc: "Kopp marka fotovoltaik panel sistemleri ile elektrik faturalarınızı minimize edin. Profesyonel kurulum ve devlet teşviklerinden yararlanma desteği sunuyoruz.",
            features: ["Yüksek Verimli Paneller", "25 Yıl Panel Garantisi", "Devlet Teşvikleri", "Hızlı Amortisman"],
            image: "/assets/img/service-5.jpg",
            objectPosition: "top"
        },
        {
            id: "soguk-hava",
            title: "Soğuk Hava Deposu",
            desc: "Ticari ve endüstriyel soğuk hava depoları için komple çözümler. Bakım, onarım, montaj ve yeni sistem kurulumu hizmetleri sunuyoruz.",
            features: ["Ticari Soğutma", "Endüstriyel Sistemler", "Periyodik Bakım", "7/24 Acil Servis"],
            image: "/assets/img/service-6.jpg",
            objectPosition: "top"
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
                        <div className="w-full md:w-1/2 overflow-hidden rounded-2xl shadow-xl h-[400px]">
                            <img
                                src={service.image}
                                alt={service.title}
                                className="w-full h-[120%] object-cover"
                                style={{ objectPosition: service.objectPosition || 'top' }}
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
