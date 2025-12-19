import React, { useState, useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Phone, Menu, X, Snowflake } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

const Navbar = () => {
    const [isScrolled, setIsScrolled] = useState(false);
    const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
    const location = useLocation();

    useEffect(() => {
        const handleScroll = () => {
            setIsScrolled(window.scrollY > 20);
        };
        window.addEventListener('scroll', handleScroll);
        return () => window.removeEventListener('scroll', handleScroll);
    }, []);

    const navLinks = [
        { name: 'Ana Sayfa', path: '/' },
        { name: 'Hizmetler', path: '/hizmetler' },
        { name: 'Hakkımızda', path: '/hakkimizda' },
        { name: 'İletişim', path: '/iletisim' },
    ];

    const isHome = location.pathname === '/';

    return (
        <nav
            className={`fixed top-0 left-0 w-full z-50 transition-all duration-300 ${isScrolled || !isHome ? 'bg-brand-dark/95 backdrop-blur-md shadow-lg py-3' : 'bg-transparent py-5'
                }`}
        >
            <div className="container mx-auto px-4 md:px-8 flex justify-between items-center">
                {/* Logo */}
                <Link to="/" className="flex items-center gap-2 group">
                    <div className="bg-brand-cyan p-2 rounded-lg group-hover:rotate-12 transition-transform">
                        <Snowflake className="text-white w-6 h-6" />
                    </div>
                    <span className="text-2xl font-bold text-white tracking-tight">
                        Pusula<span className="text-brand-cyan">.</span>
                    </span>
                </Link>

                {/* Desktop Nav */}
                <div className="hidden md:flex items-center gap-8">
                    {navLinks.map((link) => (
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

                {/* Mobile Toggle */}
                <button
                    className="md:hidden text-white"
                    onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
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
                        <div className="flex flex-col p-4 gap-4">
                            {navLinks.map((link) => (
                                <Link
                                    key={link.name}
                                    to={link.path}
                                    onClick={() => setIsMobileMenuOpen(false)}
                                    className="text-white/80 hover:text-brand-cyan py-2 border-b border-white/5"
                                >
                                    {link.name}
                                </Link>
                            ))}
                            <Link
                                to="/iletisim"
                                onClick={() => setIsMobileMenuOpen(false)}
                                className="bg-brand-cyan text-white py-3 rounded-lg text-center font-bold mt-2"
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
