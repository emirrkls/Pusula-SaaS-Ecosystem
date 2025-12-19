import React from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { ArrowRight, ShieldCheck, Clock, Wallet, ChevronRight } from 'lucide-react';

const Home = () => {
    const features = [
        {
            icon: <Clock className="w-8 h-8 text-brand-cyan" />,
            title: "Hızlı Servis",
            desc: "Acil durumlarda 7/24 teknik destek ve hızlı müdahale garantisi."
        },
        {
            icon: <ShieldCheck className="w-8 h-8 text-brand-cyan" />,
            title: "Garantili İşçilik",
            desc: "Tüm bakım ve onarım işlemlerimiz 1 yıl parça ve işçilik garantilidir."
        },
        {
            icon: <Wallet className="w-8 h-8 text-brand-cyan" />,
            title: "Uygun Fiyat",
            desc: "Kaliteli hizmeti en rekabetçi fiyatlarla sunuyoruz. Sürpriz maliyet yok."
        }
    ];

    const services = [
        {
            title: "Split Klima",
            image: "https://images.unsplash.com/photo-1614634351680-31362e51927c?q=80&w=800&auto=format&fit=crop",
            desc: "Ev ve ofisleriniz için en verimli split klima çözümleri."
        },
        {
            title: "VRF Sistemleri",
            image: "https://images.unsplash.com/photo-1581094288338-2314dddb7ece?q=80&w=800&auto=format&fit=crop",
            desc: "Büyük ölçekli binalar için merkezi iklimlendirme sistemleri."
        },
        {
            title: "Kombi Bakımı",
            image: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=800&auto=format&fit=crop",
            desc: "Kışa hazırlık ve periyodik kombi bakım hizmetleri."
        },
        {
            title: "Montaj & Keşif",
            image: "https://images.unsplash.com/photo-1621905251189-08b45d6a269e?q=80&w=800&auto=format&fit=crop",
            desc: "Profesyonel ekiplerimizle ücretsiz keşif ve güvenli montaj."
        }
    ];

    return (
        <div className="bg-gray-50">
            {/* Hero Section */}
            <section className="relative h-screen flex items-center justify-center overflow-hidden">
                <div className="absolute inset-0 z-0">
                    <img
                        src="https://images.unsplash.com/photo-1516937941348-c09645f3a26d?q=80&w=2070&auto=format&fit=crop"
                        alt="Hero Background"
                        className="w-full h-full object-cover"
                    />
                    <div className="absolute inset-0 bg-brand-dark/70"></div>
                </div>

                <div className="relative z-10 container mx-auto px-4 text-center text-white">
                    <motion.h1
                        initial={{ opacity: 0, y: 30 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.8 }}
                        className="text-5xl md:text-7xl font-bold mb-6 tracking-tight"
                    >
                        Konforunuzun Yönü <span className="text-brand-cyan">Pusula</span>
                    </motion.h1>
                    <motion.p
                        initial={{ opacity: 0, y: 30 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.8, delay: 0.2 }}
                        className="text-xl md:text-2xl text-gray-200 mb-10 max-w-2xl mx-auto font-light"
                    >
                        İklimlendirme çözümlerinde uzman kadro, son teknoloji ve güvenilir hizmet.
                    </motion.p>
                    <motion.div
                        initial={{ opacity: 0, y: 30 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ duration: 0.8, delay: 0.4 }}
                    >
                        <Link
                            to="/iletisim"
                            className="bg-brand-cyan hover:bg-cyan-400 text-white px-8 py-4 rounded-full font-bold text-lg transition-all shadow-lg hover:shadow-cyan-500/40 inline-flex items-center gap-2"
                        >
                            Hemen Servis Çağır
                            <ArrowRight className="w-5 h-5" />
                        </Link>
                    </motion.div>
                </div>
            </section>

            {/* Features Grid */}
            <section className="py-20 container mx-auto px-4 -mt-20 relative z-20">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                    {features.map((feature, index) => (
                        <motion.div
                            key={index}
                            initial={{ opacity: 0, y: 20 }}
                            whileInView={{ opacity: 1, y: 0 }}
                            viewport={{ once: true }}
                            transition={{ delay: index * 0.2 }}
                            className="bg-white p-8 rounded-2xl shadow-xl hover:shadow-2xl transition-shadow border-b-4 border-brand-cyan group"
                        >
                            <div className="bg-brand-cyan/10 w-16 h-16 rounded-full flex items-center justify-center mb-6 group-hover:bg-brand-cyan/20 transition-colors">
                                {feature.icon}
                            </div>
                            <h3 className="text-xl font-bold text-brand-dark mb-3">{feature.title}</h3>
                            <p className="text-gray-600 leading-relaxed">{feature.desc}</p>
                        </motion.div>
                    ))}
                </div>
            </section>

            {/* Services Preview */}
            <section className="py-20 bg-white">
                <div className="container mx-auto px-4">
                    <div className="text-center mb-16">
                        <span className="text-brand-cyan font-bold tracking-wider uppercase text-sm">Hizmetlerimiz</span>
                        <h2 className="text-4xl font-bold text-brand-dark mt-2">Neler Yapıyoruz?</h2>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                        {services.map((service, index) => (
                            <motion.div
                                key={index}
                                initial={{ opacity: 0, scale: 0.95 }}
                                whileInView={{ opacity: 1, scale: 1 }}
                                viewport={{ once: true }}
                                className="group relative overflow-hidden rounded-xl shadow-lg aspect-[3/4] cursor-pointer"
                            >
                                <img
                                    src={service.image}
                                    alt={service.title}
                                    className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500"
                                />
                                <div className="absolute inset-0 bg-gradient-to-t from-brand-dark/90 via-brand-dark/20 to-transparent flex flex-col justify-end p-6">
                                    <h3 className="text-xl font-bold text-white mb-2">{service.title}</h3>
                                    <p className="text-gray-300 text-sm mb-4 opacity-0 group-hover:opacity-100 transition-opacity duration-300 transform translate-y-4 group-hover:translate-y-0">
                                        {service.desc}
                                    </p>
                                    <Link to="/hizmetler" className="text-brand-cyan font-semibold flex items-center gap-1 hover:gap-2 transition-all">
                                        Detaylı Bilgi <ChevronRight className="w-4 h-4" />
                                    </Link>
                                </div>
                            </motion.div>
                        ))}
                    </div>
                </div>
            </section>

            {/* CTA Strip */}
            <section className="py-16 bg-brand-cyan relative overflow-hidden">
                <div className="absolute inset-0 opacity-10 pattern-dots"></div>
                <div className="container mx-auto px-4 relative z-10 flex flex-col md:flex-row items-center justify-between gap-8 text-center md:text-left">
                    <div>
                        <h2 className="text-3xl md:text-4xl font-bold text-white mb-2">Kış gelmeden kombi bakımınızı yaptırın!</h2>
                        <p className="text-white/90 text-lg">Erken rezervasyon fırsatlarından yararlanın, kışı sıcak geçirin.</p>
                    </div>
                    <Link
                        to="/iletisim"
                        className="bg-white text-brand-cyan px-8 py-4 rounded-full font-bold text-lg hover:bg-gray-100 transition-colors shadow-lg whitespace-nowrap"
                    >
                        Randevu Al
                    </Link>
                </div>
            </section>
        </div>
    );
};

export default Home;
