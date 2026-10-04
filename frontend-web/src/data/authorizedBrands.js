/** Marka logoları (SVG, WebP veya PNG): frontend-web/public/assets/img/brands/ */
export const AUTHORIZED_BRAND_LOGOS = '/assets/img/brands';

// Dealer/service or partnership status and product scope are confirmed by the business
// owner. The source URLs identify the original logos, not the authorization.
export const AUTHORIZED_BRANDS = [
    {
        id: 'hisense',
        name: 'Hisense',
        logo: `${AUTHORIZED_BRAND_LOGOS}/hisense.svg`,
        category: 'Klima sistemleri',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        id: 'untes',
        name: 'Üntes',
        logo: `${AUTHORIZED_BRAND_LOGOS}/untes.svg`,
        category: 'VRF ve ısı pompası sistemleri',
        detail: 'Üntes Grubu VRF ve ısı pompası sistemleri',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        id: 'nibe',
        name: 'Nibe',
        logo: `${AUTHORIZED_BRAND_LOGOS}/nibe.svg`,
        category: 'Isı pompası sistemleri',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        id: 'lg-monoblok',
        name: 'LG Monoblok',
        logo: `${AUTHORIZED_BRAND_LOGOS}/lg-monoblok.svg`,
        category: 'Monoblok ısı pompası sistemleri',
        detail: 'LG Grubu monoblok ısı pompaları',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        id: 'solimpeks',
        name: 'Solimpeks',
        logo: `${AUTHORIZED_BRAND_LOGOS}/solimpeks.webp`,
        category: 'Isı pompası ve güneş enerjisi',
        detail: 'Solimpeks Grubu ısı pompası ve fotovoltaik panel',
        roles: ['Yetkili bayi', 'Yetkili servis'],
    },
    {
        id: 'midea-vrf',
        name: 'Midea',
        logo: `${AUTHORIZED_BRAND_LOGOS}/midea.webp`,
        category: 'VRF sistemleri',
        roles: ['Yetkili bayi', 'Yetkili servis'],
        source: 'https://www.midea.com/global',
    },
    {
        id: 'daikin-vrf',
        name: 'Daikin',
        logo: `${AUTHORIZED_BRAND_LOGOS}/daikin.svg`,
        category: 'VRV / VRF sistemleri',
        roles: ['Proje odaklı iş ortaklığı'],
        source: 'https://www.daikin.com.tr/vrv-sistem-klimalar',
    },
    {
        id: 'quatech',
        name: 'Quatech',
        logo: `${AUTHORIZED_BRAND_LOGOS}/quatech.png`,
        category: 'Klima sistemleri',
        roles: ['Yetkili bayi', 'Yetkili servis'],
        source: 'https://quatech.com.tr/',
    },
    {
        id: 'termodinamik',
        name: 'Termodinamik',
        logo: `${AUTHORIZED_BRAND_LOGOS}/termodinamik.svg`,
        category: 'Isıtma sistemleri',
        roles: ['Yetkili bayi', 'Yetkili servis'],
        source: 'https://tdheating.com.tr/',
    },
    {
        id: 'baymak',
        name: 'Baymak',
        logo: `${AUTHORIZED_BRAND_LOGOS}/baymak.svg`,
        category: 'Split klima, ısı pompası ve havuz ısı pompası',
        roles: ['Çalıştığımız marka'],
        source: 'https://www.baymak.com.tr/',
        logoSource: 'https://cdn.baymak.com.tr/assets/svg/baymak--logo.svg?v=1.5',
    },
    {
        id: 'varmeks',
        name: 'Varmeks',
        logo: `${AUTHORIZED_BRAND_LOGOS}/varmeks.png`,
        category: 'Havuz ısı pompası sistemleri',
        roles: ['Çalıştığımız marka'],
        source: 'https://www.varmeks.com.tr/',
        logoSource: 'https://cdn.b12.io/client_media/louJKCFb/722b9b12-d02f-11f0-9327-0242ac110002-png-regular_image.png',
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
    'Hisense ve Quatech klima; Üntes ve Midea VRF; Üntes, Nibe, LG monoblok ve Solimpeks ısı pompaları; Termodinamik ısıtma sistemlerinde yetkili bayi ve servis hizmeti sunuyoruz. Daikin VRV / VRF sistemlerinde ise proje odaklı iş ortaklığı yürütüyoruz. Split klima ve ısı pompasında Baymak; havuz ısı pompalarında Baymak ve Varmeks markalarıyla çalışıyoruz.';

export const POOL_HEAT_PUMP_SUMMARY =
    'Villa, otel ve işletme havuzları için Baymak ve Varmeks havuz ısı pompası çözümleri. Havuz hacmi, kullanım dönemi ve tesisata göre keşif, cihaz seçimi, kurulum ve bakım planlıyoruz.';

export const BRAND_NETWORK_TITLE = 'Yetkili Bayiliklerimiz, Servisliklerimiz ve İş Ortaklarımız';
export const BRAND_NETWORK_LIST_LABEL = 'Yetkili markalarımız ve iş ortaklarımız';

export const OTHER_BRANDS_SERVICE_NOTE =
    'Didim, Akbük, Altınkum ve çevresinde diğer marka klimalarda da bakım, montaj ve arıza desteği sunuyoruz.';

export const AUTHORIZED_BRANDS_FAQ_ANSWER =
    `${AUTHORIZED_BRANDS_SUMMARY} Midea yetki kapsamımız VRF sistemleriyle sınırlıdır. Güneş enerjisi sistemlerinde Solimpeks ve Panasonic Grubu fotovoltaik panel, Kopp Grubu inverter ve batarya kullanıyoruz. ${OTHER_BRANDS_SERVICE_NOTE}`;
