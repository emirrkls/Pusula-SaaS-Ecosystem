import { BUSINESS_PARTNERS } from '../data/authorizedBrands';

export function BusinessPartnersSection({ variant = 'light' }) {
    const isDark = variant === 'dark';

    return (
        <section
            aria-labelledby="business-partners-heading"
            className={`mt-16 border-t pt-12 ${isDark ? 'border-white/10' : 'border-gray-200'}`}
        >
            <div className="mx-auto mb-8 max-w-2xl text-center">
                <p className="mb-3 text-xs font-semibold uppercase tracking-widest text-brand-cyan">İş ortaklarımız</p>
                <h2 id="business-partners-heading" className={`mb-4 text-2xl font-semibold md:text-3xl ${isDark ? 'text-white' : 'text-brand-dark'}`}>
                    Güçlü markalarla, doğru çözümler.
                </h2>
                <p className={`text-sm leading-relaxed ${isDark ? 'text-gray-300' : 'text-gray-600'}`}>
                    Klima, merkezi iklimlendirme ve ısıtma projelerimizde birlikte çalıştığımız markalar.
                </p>
            </div>
            <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-label="İş ortağı markalar">
                {BUSINESS_PARTNERS.map((partner) => (
                    <li
                        key={partner.id}
                        data-partner-id={partner.id}
                        className={`flex flex-col items-center rounded-xl border px-5 py-6 text-center ${
                            isDark ? 'border-white/10 bg-white/5' : 'border-gray-200 bg-white'
                        }`}
                    >
                        <div className="mb-5 flex h-20 w-full items-center justify-center rounded-lg bg-white px-3">
                            <img src={partner.logo} alt="" width="180" height="64" loading="lazy" decoding="async" className="max-h-16 max-w-full object-contain" />
                        </div>
                        <h3 className={`text-base font-semibold ${isDark ? 'text-white' : 'text-brand-dark'}`}>{partner.name}</h3>
                        <p className={`mt-2 text-xs ${isDark ? 'text-gray-400' : 'text-gray-500'}`}>{partner.category}</p>
                    </li>
                ))}
            </ul>
        </section>
    );
}
