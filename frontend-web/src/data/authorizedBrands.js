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
        category: 'VRF & Isı pompası',
        detail: 'Üntes Grubu VRF ve ısı pompası sistemleri',
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
        category: 'Isı pompası & Güneş enerjisi',
        detail: 'Solimpeks Grubu ısı pompası ve fotovoltaik panel',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
];

export const SOLAR_ENERGY_BRANDS = {
    panels: ['Solimpeks Grubu', 'Panasonic Grubu'],
    inverters: ['Kopp Grubu'],
    batteries: ['Kopp Grubu'],
};

export const SOLAR_ENERGY_SUMMARY =
    'Solimpeks ve Panasonic Grubu fotovoltaik panel; Kopp Grubu inverter ve batarya sistemleri.';

export const AUTHORIZED_BRANDS_SUMMARY =
    'Hisense klima; Üntes VRF ve ısı pompası; Nibe, LG monoblok ve Solimpeks ısı pompalarında yetkili bayi ve servis.';

export const OTHER_BRANDS_SERVICE_NOTE =
    'Yetkili olmadığımız Daikin, Mitsubishi, Airfel, Bosch ve diğer tüm marka klimalarda da arıza, bakım ve montaj için profesyonel teknik servis sunuyoruz.';

export const AUTHORIZED_BRANDS_FAQ_ANSWER =
    `Hisense klima yetkili bayi ve servisimiz. Üntes Grubu VRF ve ısı pompalarında; Nibe, LG monoblok (LG Grubu) ve Solimpeks Grubu ısı pompalarında yetkili bayi ve servis hizmeti sunuyoruz. Güneş enerjisi sistemlerinde Solimpeks ve Panasonic Grubu fotovoltaik panel, Kopp Grubu inverter ve batarya kullanıyoruz. ${OTHER_BRANDS_SERVICE_NOTE}`;
