import React from 'react';
import { motion } from 'framer-motion';
import { Users, Target, Award } from 'lucide-react';
import { PageSeo } from '../seo/PageSeo';
import { AuthorizedBrandsSection } from '../components/AuthorizedBrandsSection';
import { AUTHORIZED_BRANDS_SUMMARY } from '../data/authorizedBrands';

const About = () => {
    return (
        <>
            <PageSeo
                title="Hakkımızda - Didim'in Güvenilir İklimlendirme Firması | Pusula İklimlendirme"
                description="2010'dan beri Didim ve Aydın'da iklimlendirme hizmeti. Hisense, Üntes yetkili bayi; Nibe, LG monoblok ve Solimpeks ısı pompası yetkili servis."
                path="/hakkimizda"
                breadcrumbs={[
                    { name: 'Ana Sayfa', path: '/' },
                    { name: 'Hakkımızda', path: '/hakkimizda' },
                ]}
            />
        <div className="pt-20 bg-white min-h-screen">
            {/* Hero */}
            {/* ABOUT PAGE HERO BACKGROUND - Place image at: public/assets/img/about-hero-bg.jpg */}
            <div className="relative bg-brand-dark text-white py-24 overflow-hidden">
                <div className="absolute inset-0 opacity-10">
                    <img
                        src="/assets/img/about-hero-bg.jpg" // TODO: Office/company photo for hero
                        alt="Pusula İklimlendirme Didim ofisi — klima servisi merkezi"
                        className="w-full h-full object-cover"
                    />
                </div>
                <div className="container mx-auto px-4 relative z-10 text-center">
                    <motion.h1
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        className="text-4xl md:text-6xl font-bold mb-6"
                    >
                        Hakkımızda
                    </motion.h1>
                    <p className="text-xl text-gray-300 max-w-2xl mx-auto">
                        2010'dan beri iklimlendirme sektöründe güven ve kalitenin adresi.
                    </p>
                </div>
            </div>

            {/* Content */}
            <div className="container mx-auto px-4 py-20">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-16 items-center mb-20">
                    <div>
                        <h2 className="text-3xl font-bold text-brand-dark mb-6">Hikayemiz</h2>
                        <p className="text-gray-600 leading-relaxed mb-4">
                            Pusula İklimlendirme olarak, 10 yılı aşkın süredir Didim, Aydın ve çevre ilçelerde kurumsal ve bireysel müşterilerimize hizmet vermekteyiz. Küçük bir teknik servis olarak başladığımız bu yolculukta, bugün geniş araç filomuz ve uzman kadromuzla bölgenin güvenilir iklimlendirme firmalarından biri haline geldik.
                        </p>
                        <p className="text-gray-600 leading-relaxed mb-4">
                            Amacımız sadece arızaları gidermek değil, yaşam alanlarınızın konforunu artıracak kalıcı çözümler üretmektir. Teknolojiyi yakından takip ediyor, ekibimizi sürekli eğitiyoruz.
                        </p>
                        <p className="text-gray-600 leading-relaxed">
                            {AUTHORIZED_BRANDS_SUMMARY} Yetkili bayi ve servis statümüz sayesinde satış, montaj
                            ve arıza müdahalelerinde orijinal parça ve üretici standartlarında hizmet sunuyoruz.
                        </p>
                    </div>
                    {/* public/assets/img/about-us-1.jpeg · about-us-2.jpeg */}
                    <div className="grid grid-cols-2 gap-4">
                        <img
                            src="/assets/img/about-us-1.jpeg"
                            alt="Didim'de klima montajı yapan Pusula İklimlendirme servis ekibi"
                            className="rounded-xl shadow-lg w-full h-64 object-cover mt-8"
                        />
                        <img
                            src="/assets/img/about-us-2.jpeg"
                            alt="Pusula İklimlendirme Didim — servis aracı ve iklimlendirme merkezi"
                            className="rounded-xl shadow-lg w-full h-64 object-cover"
                        />
                    </div>
                </div>

                {/* Mission/Vision Cards */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-8 mb-20">
                    <div className="bg-gray-50 p-8 rounded-xl border border-gray-100 text-center">
                        <div className="bg-brand-cyan/10 w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6">
                            <Target className="w-8 h-8 text-brand-cyan" />
                        </div>
                        <h3 className="text-xl font-bold text-brand-dark mb-3">Misyonumuz</h3>
                        <p className="text-gray-600">
                            Müşterilerimize en hızlı, ekonomik ve kaliteli iklimlendirme çözümlerini sunarak yaşam kalitelerini artırmak.
                        </p>
                    </div>
                    <div className="bg-gray-50 p-8 rounded-xl border border-gray-100 text-center">
                        <div className="bg-brand-cyan/10 w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6">
                            <Users className="w-8 h-8 text-brand-cyan" />
                        </div>
                        <h3 className="text-xl font-bold text-brand-dark mb-3">Vizyonumuz</h3>
                        <p className="text-gray-600">
                            Türkiye'nin en güvenilir ve tercih edilen iklimlendirme servisi olmak ve sürdürülebilir teknolojilere öncülük etmek.
                        </p>
                    </div>
                    <div className="bg-gray-50 p-8 rounded-xl border border-gray-100 text-center">
                        <div className="bg-brand-cyan/10 w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-6">
                            <Award className="w-8 h-8 text-brand-cyan" />
                        </div>
                        <h3 className="text-xl font-bold text-brand-dark mb-3">Değerlerimiz</h3>
                        <p className="text-gray-600">
                            Dürüstlük, şeffaflık, müşteri memnuniyeti ve sürekli gelişim temel değerlerimizdir.
                        </p>
                    </div>
                </div>

                <AuthorizedBrandsSection variant="dark" />
            </div>
        </div>
        </>
    );
};

export default About;
