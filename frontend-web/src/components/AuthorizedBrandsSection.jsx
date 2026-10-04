import { AUTHORIZED_BRANDS, BRAND_NETWORK_TITLE, BRAND_NETWORK_LIST_LABEL, OTHER_BRANDS_SERVICE_NOTE } from '../data/authorizedBrands';
import './AuthorizedBrandsSection.css';

export function AuthorizedBrandsSection({ variant = 'light' }) {
    const isDark = variant === 'dark';

    return (
        <section
            aria-labelledby="authorized-brands-heading"
            className={isDark ? 'bg-brand-dark text-white py-16 md:py-20' : 'bg-gray-50 py-16 md:py-20'}
        >
            <div className="container mx-auto px-4">
                <div className="mx-auto mb-10 max-w-3xl text-center">
                    <p className="mb-3 text-xs font-semibold uppercase tracking-widest text-brand-cyan">
                        Güçlü markalarla, doğru çözümler.
                    </p>
                    <h2 id="authorized-brands-heading" className={`mb-4 text-2xl font-semibold md:text-3xl ${isDark ? 'text-white' : 'text-brand-dark'}`}>
                        {BRAND_NETWORK_TITLE}
                    </h2>
                    <p className={`text-sm leading-relaxed ${isDark ? 'text-gray-300' : 'text-gray-600'}`}>
                        Didim, Akbük ve Altınkum’da klima, VRF, ısı pompası, havuz ısıtma ve ısıtma çözümleri sunuyoruz.
                        Yetkili bayilik ve servisliklerimizi, çalıştığımız markalar ve proje odaklı iş ortaklarımızla birlikte
                        aşağıda görebilirsiniz.
                    </p>
                </div>

                <ul className="mx-auto grid max-w-6xl grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-label={BRAND_NETWORK_LIST_LABEL}>
                    {AUTHORIZED_BRANDS.map((brand) => (
                        <li
                            key={brand.id}
                            data-brand-id={brand.id}
                            className={`authorized-brand-card flex min-w-0 flex-col items-center rounded-xl border px-5 py-6 text-center ${
                                isDark ? 'authorized-brand-card--dark border-white/10 bg-white/5' : 'border-gray-200 bg-white'
                            }`}
                        >
                            <div className="mb-5 flex h-20 w-full items-center justify-center rounded-lg bg-white px-3">
                                <img
                                    src={brand.logo}
                                    alt=""
                                    width="180"
                                    height="64"
                                    loading="lazy"
                                    decoding="async"
                                    className="authorized-brand-logo h-16 w-[180px] max-w-full object-contain"
                                />
                            </div>
                            <h3 className={`text-base font-semibold ${isDark ? 'text-white' : 'text-brand-dark'}`}>
                                {brand.name}
                            </h3>
                            <p className={`mt-2 text-xs leading-relaxed ${isDark ? 'text-gray-400' : 'text-gray-500'}`}>{brand.category}</p>
                        </li>
                    ))}
                </ul>

                <p className={`mt-10 max-w-3xl mx-auto text-center text-sm leading-relaxed ${
                    isDark ? 'text-gray-300' : 'text-gray-600'
                }`}>
                    {OTHER_BRANDS_SERVICE_NOTE}
                </p>
            </div>
        </section>
    );
}
