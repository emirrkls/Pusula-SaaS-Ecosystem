import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { CheckCircle, Plus, Minus, Phone, ArrowRight } from 'lucide-react';
import { landingPages } from './landingPages';
import { PageSeo } from '../../seo/PageSeo';
import { SITE_URL } from '../../seo/constants';

const ServiceLandingPage = ({ pageKey }) => {
    const page = landingPages[pageKey];
    const [activeAccordion, setActiveAccordion] = useState(null);

    if (!page) {
        return null;
    }

    const serviceSchema = page.serviceName
        ? {
            '@context': 'https://schema.org',
            '@type': 'Service',
            name: page.serviceName,
            description: page.description,
            provider: {
                '@type': 'HVACBusiness',
                name: 'Pusula İklimlendirme',
                telephone: '+905400250925',
                url: SITE_URL,
                address: {
                    '@type': 'PostalAddress',
                    streetAddress: 'Çamlık Mah. Ege Cad. No76/B',
                    addressLocality: 'Didim',
                    addressRegion: 'Aydın',
                    postalCode: '09270',
                    addressCountry: 'TR',
                },
            },
            areaServed: [
                { '@type': 'City', name: 'Didim' },
                { '@type': 'AdministrativeArea', name: 'Aydın' },
                { '@type': 'Place', name: 'Altınkum' },
                { '@type': 'Place', name: 'Akbük' },
                { '@type': 'Place', name: 'Bozbük' },
            ],
            serviceType: page.serviceName,
            url: `${SITE_URL}/${page.slug}`,
        }
        : null;

    return (
        <>
            <PageSeo
                title={page.title}
                description={page.description}
                path={`/${page.slug}`}
                faqs={page.faqs}
                breadcrumbs={[
                    { name: 'Ana Sayfa', path: '/' },
                    { name: page.h1, path: `/${page.slug}` },
                ]}
                structuredData={serviceSchema}
            />
        <div className="pt-20 bg-gray-50 min-h-screen">
            {/* Hero */}
            <div className="bg-brand-dark text-white py-16 md:py-20">
                <div className="container mx-auto px-4 text-center max-w-3xl">
                    <motion.h1
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="text-4xl md:text-5xl font-bold mb-4"
                    >
                        {page.h1}
                    </motion.h1>
                    <motion.p
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: 0.1 }}
                        className="text-lg md:text-xl text-gray-300"
                    >
                        {page.subtitle}
                    </motion.p>
                    <motion.div
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: 0.2 }}
                        className="flex flex-col sm:flex-row gap-4 justify-center mt-8"
                    >
                        <Link
                            to="/iletisim"
                            className="bg-brand-cyan hover:bg-cyan-400 text-white px-8 py-3 rounded-full font-bold transition-all inline-flex items-center justify-center gap-2"
                        >
                            Servis Talebi Oluştur
                            <ArrowRight className="w-5 h-5" />
                        </Link>
                        <a
                            href="tel:+905400250925"
                            className="border border-white/30 hover:border-brand-cyan text-white px-8 py-3 rounded-full font-bold transition-all inline-flex items-center justify-center gap-2"
                        >
                            <Phone className="w-5 h-5" />
                            0540 025 09 25
                        </a>
                    </motion.div>
                </div>
            </div>

            {/* Intro */}
            <div className="container mx-auto px-4 py-16 max-w-4xl">
                {page.answerBox && (
                    <div className="mb-8 rounded-xl border-l-4 border-brand-cyan bg-white p-6 shadow-sm">
                        <p className="text-lg font-semibold leading-relaxed text-brand-dark">
                            {page.answerBox}
                        </p>
                    </div>
                )}
                <div className="space-y-4 text-gray-600 text-lg leading-relaxed">
                    {page.intro.map((paragraph, index) => (
                        <p key={index}>{paragraph}</p>
                    ))}
                </div>
            </div>

            {/* Features */}
            <div className="bg-white py-16">
                <div className="container mx-auto px-4 max-w-4xl">
                    <h2 className="text-3xl font-bold text-brand-dark mb-8 text-center">
                        Hizmet Kapsamımız
                    </h2>
                    <ul className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        {page.features.map((feature, index) => (
                            <li key={index} className="flex items-start gap-3 text-gray-700">
                                <CheckCircle className="w-5 h-5 text-brand-cyan shrink-0 mt-0.5" />
                                {feature}
                            </li>
                        ))}
                    </ul>
                </div>
            </div>

            {/* Process */}
            <div className="container mx-auto px-4 py-16 max-w-4xl">
                <h2 className="text-3xl font-bold text-brand-dark mb-10 text-center">
                    Nasıl Çalışıyoruz?
                </h2>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    {page.steps.map((step, index) => (
                        <div key={index} className="bg-white p-6 rounded-xl shadow-md border-l-4 border-brand-cyan">
                            <span className="text-brand-cyan font-bold text-sm">Adım {index + 1}</span>
                            <h3 className="text-xl font-bold text-brand-dark mt-1 mb-2">{step.title}</h3>
                            <p className="text-gray-600">{step.desc}</p>
                        </div>
                    ))}
                </div>
            </div>

            {/* FAQ */}
            <div className="bg-white py-16">
                <div className="container mx-auto px-4 max-w-3xl">
                    <h2 className="text-3xl font-bold text-brand-dark mb-8 text-center">
                        Sıkça Sorulan Sorular
                    </h2>
                    <div className="space-y-4">
                        {page.faqs.map((faq, index) => (
                            <div key={index} className="border border-gray-200 rounded-lg overflow-hidden">
                                <button
                                    onClick={() => setActiveAccordion(activeAccordion === index ? null : index)}
                                    className="w-full flex items-center justify-between p-6 bg-white hover:bg-gray-50 transition-colors text-left"
                                >
                                    <span className="font-semibold text-brand-dark">{faq.q}</span>
                                    {activeAccordion === index ? (
                                        <Minus className="text-brand-cyan shrink-0" />
                                    ) : (
                                        <Plus className="text-brand-cyan shrink-0" />
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

            {/* Related + CTA */}
            <div className="bg-brand-cyan py-16">
                <div className="container mx-auto px-4 text-center">
                    <h2 className="text-3xl font-bold text-white mb-4">
                        {page.ctaHeading || "Didim'de Klimanız İçin Yanınızdayız"}
                    </h2>
                    <p className="text-white/90 mb-6 max-w-xl mx-auto">
                        Diğer hizmetlerimizi de inceleyin veya hemen randevu oluşturun.
                    </p>
                    <div className="flex flex-wrap justify-center gap-4 mb-8">
                        {page.related.map((key) => {
                            const related = landingPages[key];
                            return (
                                <Link
                                    key={key}
                                    to={`/${related.slug}`}
                                    className="bg-white/20 hover:bg-white/30 text-white px-5 py-2 rounded-full text-sm font-medium transition-colors"
                                >
                                    {related.h1}
                                </Link>
                            );
                        })}
                        <Link
                            to="/hizmetler"
                            className="bg-white/20 hover:bg-white/30 text-white px-5 py-2 rounded-full text-sm font-medium transition-colors"
                        >
                            Tüm Hizmetler
                        </Link>
                    </div>
                    <Link
                        to="/iletisim"
                        className="bg-white text-brand-cyan px-8 py-4 rounded-full font-bold text-lg hover:bg-gray-100 transition-colors inline-flex items-center gap-2"
                    >
                        Hemen Randevu Al
                        <ArrowRight className="w-5 h-5" />
                    </Link>
                </div>
            </div>
        </div>
        </>
    );
};

export default ServiceLandingPage;
