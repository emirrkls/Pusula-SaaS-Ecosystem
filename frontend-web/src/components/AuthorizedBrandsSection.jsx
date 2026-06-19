import React from 'react';
import { Award } from 'lucide-react';
import { AUTHORIZED_BRANDS, AUTHORIZED_BRANDS_SUMMARY } from '../data/authorizedBrands';

export function AuthorizedBrandsSection({ variant = 'light' }) {
    const isDark = variant === 'dark';

    return (
        <section className={isDark ? 'bg-brand-dark text-white py-20' : 'bg-gray-50 py-20'}>
            <div className="container mx-auto px-4">
                <div className="text-center mb-12 max-w-3xl mx-auto">
                    <div className="inline-flex items-center gap-2 text-brand-cyan font-bold tracking-wider uppercase text-sm mb-3">
                        <Award className="w-4 h-4" />
                        Yetkili Bayi & Servis
                    </div>
                    <h2 className={`text-3xl md:text-4xl font-bold mb-4 ${isDark ? 'text-white' : 'text-brand-dark'}`}>
                        Marka Yetkili Satış ve Servis
                    </h2>
                    <p className={isDark ? 'text-gray-300' : 'text-gray-600'}>
                        {AUTHORIZED_BRANDS_SUMMARY} Satış, montaj, periyodik bakım ve arıza onarımında
                        üretici garantisi ve orijinal yedek parça desteği sunuyoruz.
                    </p>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
                    {AUTHORIZED_BRANDS.map((brand) => (
                        <div
                            key={brand.name}
                            className={`rounded-xl border p-5 ${
                                isDark
                                    ? 'border-white/10 bg-white/5'
                                    : 'border-gray-200 bg-white shadow-sm'
                            }`}
                        >
                            <p className={`text-xs font-semibold uppercase tracking-wide mb-2 ${isDark ? 'text-brand-cyan' : 'text-brand-cyan'}`}>
                                {brand.category}
                            </p>
                            <h3 className={`text-xl font-bold mb-1 ${isDark ? 'text-white' : 'text-brand-dark'}`}>
                                {brand.name}
                            </h3>
                            {brand.detail && (
                                <p className={`text-sm mb-3 ${isDark ? 'text-gray-400' : 'text-gray-500'}`}>
                                    {brand.detail}
                                </p>
                            )}
                            <div className="flex flex-wrap gap-2">
                                {brand.roles.map((role) => (
                                    <span
                                        key={role}
                                        className={`text-xs font-medium px-2.5 py-1 rounded-full ${
                                            isDark
                                                ? 'bg-brand-cyan/20 text-brand-cyan'
                                                : 'bg-brand-cyan/10 text-brand-dark'
                                        }`}
                                    >
                                        {role}
                                    </span>
                                ))}
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </section>
    );
}
