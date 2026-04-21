import React from 'react';
import { Link } from 'react-router-dom';
import { Facebook, Instagram, Twitter, MapPin, Phone, Mail } from 'lucide-react';

const Footer = () => {
    return (
        <footer className="bg-brand-dark text-white pt-16 pb-8">
            <div className="container mx-auto px-4 md:px-8">
                <div className="grid grid-cols-1 md:grid-cols-4 gap-12 mb-12">
                    {/* Brand Info */}
                    <div className="space-y-4">
                        <div className="flex items-center">
                            <img
                                src="/assets/img/logo.svg"
                                alt="Pusula İklimlendirme"
                                className="h-16 w-auto drop-shadow-[0_0_10px_rgba(255,255,255,0.3)]"
                            />
                        </div>
                        <p className="text-gray-400 text-sm leading-relaxed">
                            Konforunuz için profesyonel iklimlendirme çözümleri.
                            Yılların deneyimi ve uzman kadromuzla hizmetinizdeyiz.
                        </p>
                        <div className="flex gap-4 pt-2">
                            <a href="#" className="bg-white/5 p-2 rounded-full hover:bg-brand-cyan transition-colors">
                                <Facebook className="w-5 h-5" />
                            </a>
                            <a href="#" className="bg-white/5 p-2 rounded-full hover:bg-brand-cyan transition-colors">
                                <Instagram className="w-5 h-5" />
                            </a>
                            <a href="#" className="bg-white/5 p-2 rounded-full hover:bg-brand-cyan transition-colors">
                                <Twitter className="w-5 h-5" />
                            </a>
                        </div>
                    </div>

                    {/* Quick Links */}
                    <div>
                        <h3 className="text-lg font-semibold mb-6 border-l-4 border-brand-cyan pl-3">Hızlı Erişim</h3>
                        <ul className="space-y-3 text-gray-400">
                            <li><Link to="/" className="hover:text-brand-cyan transition-colors">Ana Sayfa</Link></li>
                            <li><Link to="/hakkimizda" className="hover:text-brand-cyan transition-colors">Hakkımızda</Link></li>
                            <li><Link to="/hizmetler" className="hover:text-brand-cyan transition-colors">Hizmetlerimiz</Link></li>
                            <li><Link to="/iletisim" className="hover:text-brand-cyan transition-colors">İletişim</Link></li>
                        </ul>
                    </div>

                    {/* Services */}
                    <div>
                        <h3 className="text-lg font-semibold mb-6 border-l-4 border-brand-cyan pl-3">Hizmetler</h3>
                        <ul className="space-y-3 text-gray-400">
                            <li><Link to="/hizmetler" className="hover:text-brand-cyan transition-colors">Klima Bakım & Onarım</Link></li>
                            <li><Link to="/hizmetler" className="hover:text-brand-cyan transition-colors">VRF Sistemleri</Link></li>
                            <li><Link to="/hizmetler" className="hover:text-brand-cyan transition-colors">Isı Pompası</Link></li>
                            <li><Link to="/hizmetler" className="hover:text-brand-cyan transition-colors">Güneş Enerjisi</Link></li>
                        </ul>
                    </div>

                    {/* Contact */}
                    <div>
                        <h3 className="text-lg font-semibold mb-6 border-l-4 border-brand-cyan pl-3">İletişim</h3>
                        <ul className="space-y-4 text-gray-400">
                            <li className="flex items-start gap-3">
                                <MapPin className="w-5 h-5 text-brand-cyan shrink-0 mt-0.5" />
                                <span className="text-sm">Çamlık Mah. Ege Cad. No76/B<br />Didim/AYDIN</span>
                            </li>
                            <li className="flex items-center gap-3">
                                <Phone className="w-5 h-5 text-brand-cyan shrink-0" />
                                <a href="tel:+905400250925" className="text-sm hover:text-brand-cyan transition-colors">+90 540 025 09 25</a>
                            </li>
                            <li className="flex items-center gap-3">
                                <Mail className="w-5 h-5 text-brand-cyan shrink-0" />
                                <a href="mailto:pusulaiklimlendirme.didim@gmail.com" className="text-sm hover:text-brand-cyan transition-colors">pusulaiklimlendirme.didim@gmail.com</a>
                            </li>
                        </ul>
                    </div>
                </div>

                <div className="border-t border-white/10 pt-8 text-center text-gray-500 text-sm">
                    <p>&copy; {new Date().getFullYear()} Pusula İklimlendirme. Tüm hakları saklıdır.</p>
                </div>
            </div>
        </footer>
    );
};

export default Footer;
