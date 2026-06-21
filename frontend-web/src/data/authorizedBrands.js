/** Marka logoları (SVG, WebP veya PNG): frontend-web/public/assets/img/brands/ */
export const AUTHORIZED_BRAND_LOGOS = '/assets/img/brands';

export const AUTHORIZED_BRANDS = [
    {
        name: 'Hisense',
        logo: `${AUTHORIZED_BRAND_LOGOS}/hisense.svg`,
        category: 'Klima',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        name: 'Üntes',
        logo: `${AUTHORIZED_BRAND_LOGOS}/untes.svg`,
        category: 'Klima',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        name: 'Nibe',
        logo: `${AUTHORIZED_BRAND_LOGOS}/nibe.svg`,
        category: 'Isı pompası',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        name: 'LG Monoblok',
        logo: `${AUTHORIZED_BRAND_LOGOS}/lg-monoblok.svg`,
        category: 'Isı pompası',
        detail: 'LG Grubu monoblok ısı pompaları',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        name: 'Solimpeks',
        logo: `${AUTHORIZED_BRAND_LOGOS}/solimpeks.webp`,
        category: 'Isı pompası',
        detail: 'Solimpeks Grubu ısı pompaları',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
];

export const AUTHORIZED_BRANDS_SUMMARY =
    'Hisense ve Üntes klima; Nibe, LG monoblok ve Solimpeks ısı pompalarında yetkili bayi ve servis.';

export const AUTHORIZED_BRANDS_FAQ_ANSWER =
    'Hisense klima ve Üntes\'te yetkili bayi ve servisimiz. Nibe, LG monoblok (LG Grubu) ve Solimpeks Grubu ısı pompalarında yetkili bayi ve servis hizmeti sunuyoruz. Diğer marka klimalara da genel teknik servis veriyoruz.';
