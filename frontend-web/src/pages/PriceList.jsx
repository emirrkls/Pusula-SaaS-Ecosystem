import React from 'react';
import { BadgePercent, CalendarDays, CreditCard, Snowflake, Sparkles } from 'lucide-react';
import { PageSeo } from '../seo/PageSeo';

const priceGroups = [
    {
        brand: 'Toshiba',
        note: 'Bonus karta 6 taksit',
        accent: 'from-sky-500 to-cyan-400',
        products: [
            { capacity: '10000 BTU', price: '49.000 TL' },
            { capacity: '13000 BTU', price: '54.000 TL' },
            { capacity: '18000 BTU', price: '71.000 TL' },
            { capacity: '24000 BTU', price: '89.000 TL' },
        ],
    },
    {
        brand: 'Hisense',
        note: 'Peşin fiyatına 8 taksit imkanı',
        accent: 'from-indigo-500 to-blue-400',
        image: '/assets/img/prices/hisense-klima.jpg',
        products: [
            { capacity: '9000 BTU', price: '32.000 TL' },
            { capacity: '12000 BTU', price: '35.500 TL' },
            { capacity: '18000 BTU', price: '51.000 TL' },
            { capacity: '24000 BTU', price: '61.000 TL' },
        ],
    },
    {
        brand: 'Rota',
        note: '6 taksit imkanı',
        accent: 'from-emerald-500 to-teal-400',
        image: '/assets/img/prices/rota-klima.jpeg',
        products: [
            { capacity: '9000 BTU', price: '35.900 TL' },
            { capacity: '12000 BTU', price: '38.100 TL' },
            { capacity: '18000 BTU', price: '56.600 TL' },
            { capacity: '24000 BTU', price: '67.800 TL' },
            { capacity: '24-26 BTU Silindir', price: '75.000 TL' },
        ],
    },
];

const PriceList = () => {
    return (
        <>
            <PageSeo
                title="Klima Fiyat Listesi | Pusula Iklimlendirme"
                description="Pusula İklimlendirme klima fiyat listesi."
                path="/fiyat-listesi"
                noindex
            />

            <div className="min-h-screen bg-slate-50 pt-28 pb-16">
                <section className="relative overflow-hidden bg-brand-dark text-white">
                    <div className="absolute inset-0">
                        <img
                            src="/assets/img/hero-bg.jpg"
                            alt=""
                            className="h-full w-full object-cover opacity-25"
                        />
                        <div className="absolute inset-0 bg-gradient-to-r from-brand-dark via-brand-dark/90 to-brand-dark/70" />
                    </div>

                    <div className="container relative mx-auto px-4 py-12 md:px-8 md:py-16">
                        <div className="max-w-4xl">
                            <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-4 py-2 text-sm font-semibold text-white/90 backdrop-blur">
                                <Snowflake className="h-4 w-4 text-brand-cyan" />
                                Klima katalog fiyat listesi
                            </div>
                            <h1 className="text-4xl font-bold tracking-tight md:text-6xl">
                                Pusula İklimlendirme Fiyat Listesi
                            </h1>
                            <p className="mt-5 max-w-2xl text-base leading-7 text-white/75 md:text-lg">
                                Toshiba, Hisense ve Rota klima modelleri için güncel satış fiyatları ve taksit seçenekleri.
                            </p>
                            <div className="mt-8 flex flex-wrap gap-3 text-sm text-white/80">
                                <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-2">
                                    <CalendarDays className="h-4 w-4 text-brand-cyan" />
                                    Güncelleme: 30 Haziran 2026
                                </span>
                                <span className="inline-flex items-center gap-2 rounded-full bg-white/10 px-4 py-2">
                                    <CreditCard className="h-4 w-4 text-brand-cyan" />
                                    Taksit seçenekleri marka bazlıdır
                                </span>
                            </div>
                        </div>
                    </div>
                </section>

                <section className="container mx-auto px-4 py-10 md:px-8">
                    <div className="grid gap-6 lg:grid-cols-3">
                        {priceGroups.map((group) => (
                            <article
                                key={group.brand}
                                className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm"
                            >
                                <div className={`h-2 bg-gradient-to-r ${group.accent}`} />
                                {group.image && (
                                    <div className="aspect-[4/3] overflow-hidden bg-slate-100">
                                        <img
                                            src={group.image}
                                            alt={`${group.brand} klima`}
                                            className="h-full w-full object-contain p-4"
                                            loading="lazy"
                                        />
                                    </div>
                                )}
                                <div className="p-6">
                                    <div className="flex items-start justify-between gap-4">
                                        <div>
                                            <h2 className="text-2xl font-bold text-brand-dark">{group.brand}</h2>
                                            <p className="mt-2 inline-flex items-center gap-2 text-sm font-semibold text-slate-600">
                                                <BadgePercent className="h-4 w-4 text-brand-cyan" />
                                                {group.note}
                                            </p>
                                        </div>
                                        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-slate-100">
                                            <Sparkles className="h-5 w-5 text-brand-cyan" />
                                        </div>
                                    </div>

                                    <div className="mt-6 divide-y divide-slate-100">
                                        {group.products.map((product) => (
                                            <div
                                                key={`${group.brand}-${product.capacity}`}
                                                className="grid grid-cols-[1fr_auto] items-center gap-4 py-4"
                                            >
                                                <span className="font-semibold text-slate-700">{product.capacity}</span>
                                                <span className="text-right text-xl font-bold text-brand-dark">
                                                    {product.price}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </article>
                        ))}
                    </div>

                    <div className="mt-8 rounded-lg border border-slate-200 bg-white p-5 text-sm leading-6 text-slate-600">
                        Fiyat klima satış bedelidir, fiyatlamaya 4 metre borulama ve montaj ücreti dahildir. Stok
                        durumu ve ücretsiz keşif imkanından faydalanmak için Pusula İklimlendirme ile iletişime
                        geçebilirsiniz.
                    </div>
                </section>
            </div>
        </>
    );
};

export default PriceList;
