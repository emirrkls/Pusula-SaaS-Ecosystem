import React, { useState, useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Phone, Menu, X, ChevronDown } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { mainNavLinks, serviceMenuGroups } from '../data/navigation';

const Navbar = () => {
    const [isScrolled, setIsScrolled] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const [isServicesOpen, setIsServicesOpen] = useState(false);
    const [isMobileServicesOpen, setIsMobileServicesOpen] = useState(false);
    const servicesRef = useRef(null);
    const location = useLocation();

    useEffect(() => {
        const handleScroll = () => setIsScrolled(window.scrollY > 20);
        window.addEventListener('scroll', handleScroll);
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

    useEffect(() => {
        setIsServicesOpen(false);
        setIsMobileMenuOpen(false);
        setIsMobileServicesOpen(false);
    }, [location.pathname, location.hash]);

    useEffect(() => {
        const handleClickOutside = (e) => {
            if (servicesRef.current && !servicesRef.current.contains(e.target)) {
                setIsServicesOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const isHome = location.pathname === '/';
    const isServicesActive =
        location.pathname === '/hizmetler' ||
        location.pathname.startsWith('/didim-klima-');

    const closeMobile = () => {
        setIsMobileMenuOpen(false);
        setIsMobileServicesOpen(false);
    };

    return (
        <nav
            className={`fixed top-0 left-0 w-full z-50 transition-all duration-300 ${
                isScrolled || !isHome ? 'bg-brand-dark/95 backdrop-blur-md shadow-lg py-3' : 'bg-transparent py-5'
            }`}
        >
            <div className="container mx-auto px-4 md:px-8 flex justify-between items-center">
                <Link to="/" className="flex items-center group">
                    <img
                        src="/assets/img/logo.svg"
                        alt="Pusula İklimlendirme"
                        className="h-16 w-auto group-hover:scale-105 transition-transform drop-shadow-[0_0_10px_rgba(255,255,255,0.3)]"
                    />
                </Link>

                {/* Desktop Nav */}
                <div className="hidden md:flex items-center gap-8">
                    <Link
                        to="/"
                        className="text-white/90 hover:text-brand-cyan font-medium transition-colors text-sm uppercase tracking-wide"
                    >
                        Ana Sayfa
                    </Link>

                    <div className="relative" ref={servicesRef}>
                        <button
                            type="button"
                            onClick={() => setIsServicesOpen((open) => !open)}
                            className={`flex items-center gap-1 font-medium transition-colors text-sm uppercase tracking-wide ${
                                isServicesActive ? 'text-brand-cyan' : 'text-white/90 hover:text-brand-cyan'
                            }`}
                            aria-expanded={isServicesOpen}
                            aria-haspopup="true"
                        >
                            Hizmetler
                            <ChevronDown
                                className={`w-4 h-4 transition-transform ${isServicesOpen ? 'rotate-180' : ''}`}
                            />
                        </button>

                        <AnimatePresence>
                            {isServicesOpen && (
                                <motion.div
                                    initial={{ opacity: 0, y: 8 }}
                                    animate={{ opacity: 1, y: 0 }}
                                    exit={{ opacity: 0, y: 8 }}
                                    transition={{ duration: 0.15 }}
                                    className="absolute top-full left-0 mt-3 w-64 bg-brand-dark border border-white/10 rounded-xl shadow-xl overflow-hidden"
                                >
                                    {serviceMenuGroups.map((group, groupIndex) => (
                                        <div
                                            key={group.label ?? 'overview'}
                                            className={groupIndex > 0 ? 'border-t border-white/10' : ''}
                                        >
                                            {group.label && (
                                                <p className="px-4 pt-3 pb-1 text-[10px] font-bold uppercase tracking-wider text-brand-cyan">
                                                    {group.label}
                                                </p>
                                            )}
                                            <ul className="py-2">
                                                {group.links.map((link) => (
                                                    <li key={link.path}>
                                                        <Link
                                                            to={link.path}
                                                            onClick={() => setIsServicesOpen(false)}
                                                            className="block px-4 py-2.5 text-sm text-white/85 hover:text-brand-cyan hover:bg-white/5 transition-colors"
                                                        >
                                                            {link.name}
                                                        </Link>
                                                    </li>
                                                ))}
                                            </ul>
                                        </div>
                                    ))}
                                </motion.div>
                            )}
                        </AnimatePresence>
                    </div>

                    {mainNavLinks.slice(1).map((link) => (
                        <Link
                            key={link.name}
                            to={link.path}
                            className="text-white/90 hover:text-brand-cyan font-medium transition-colors text-sm uppercase tracking-wide"
                        >
                            {link.name}
                        </Link>
                    ))}

                    <Link
                        to="/iletisim"
                        className="bg-brand-cyan hover:bg-cyan-400 text-white px-6 py-2.5 rounded-full font-semibold transition-all shadow-lg hover:shadow-cyan-500/30 flex items-center gap-2"
                    >
                        <Phone className="w-4 h-4" />
                        Servis Talebi
                    </Link>
                </div>

                <button
                    type="button"
                    className="md:hidden text-white"
                    onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
                    aria-label={isMobileMenuOpen ? 'Menüyü kapat' : 'Menüyü aç'}
                >
                    {isMobileMenuOpen ? <X /> : <Menu />}
                </button>
            </div>

            {/* Mobile Menu */}
            <AnimatePresence>
                {isMobileMenuOpen && (
                    <motion.div
                        initial={{ opacity: 0, height: 0 }}
                        animate={{ opacity: 1, height: 'auto' }}
                        exit={{ opacity: 0, height: 0 }}
                        className="md:hidden bg-brand-dark border-t border-white/10 overflow-hidden"
                    >
                        <div className="flex flex-col p-4 gap-1">
                            <Link
                                to="/"
                                onClick={closeMobile}
                                className="text-white/80 hover:text-brand-cyan py-2 border-b border-white/5"
                            >
                                Ana Sayfa
                            </Link>

                            <button
                                type="button"
                                onClick={() => setIsMobileServicesOpen((open) => !open)}
                                className="flex items-center justify-between text-white/80 hover:text-brand-cyan py-2 border-b border-white/5"
                            >
                                <span>Hizmetler</span>
                                <ChevronDown
                                    className={`w-4 h-4 transition-transform ${isMobileServicesOpen ? 'rotate-180' : ''}`}
                                />
                            </button>

                            <AnimatePresence>
                                {isMobileServicesOpen && (
                                    <motion.div
                                        initial={{ opacity: 0, height: 0 }}
                                        animate={{ opacity: 1, height: 'auto' }}
                                        exit={{ opacity: 0, height: 0 }}
                                        className="overflow-hidden pl-3 pb-2"
                                    >
                                        {serviceMenuGroups.map((group) => (
                                            <div key={group.label ?? 'overview'} className="mb-2">
                                                {group.label && (
                                                    <p className="text-[10px] font-bold uppercase tracking-wider text-brand-cyan py-2">
                                                        {group.label}
                                                    </p>
                                                )}
                                                {group.links.map((link) => (
                                                    <Link
                                                        key={link.path}
                                                        to={link.path}
                                                        onClick={closeMobile}
                                                        className="block text-white/70 hover:text-brand-cyan py-1.5 text-sm"
                                                    >
                                                        {link.name}
                                                    </Link>
                                                ))}
                                            </div>
                                        ))}
                                    </motion.div>
                                )}
                            </AnimatePresence>

                            {mainNavLinks.slice(1).map((link) => (
                                <Link
                                    key={link.name}
                                    to={link.path}
                                    onClick={closeMobile}
                                    className="text-white/80 hover:text-brand-cyan py-2 border-b border-white/5"
                                >
                                    {link.name}
                                </Link>
                            ))}

                            <Link
                                to="/iletisim"
                                onClick={closeMobile}
                                className="bg-brand-cyan text-white py-3 rounded-lg text-center font-bold mt-3"
                            >
                                Hemen Servis Çağır
                            </Link>
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
        </nav>
    );
};

export default Navbar;
