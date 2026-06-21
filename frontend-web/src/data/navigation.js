export const SERVICES_PATH = '/hizmetler';

export const allServicesLink = { name: 'Tüm Hizmetler', path: SERVICES_PATH };

export const klimaLandingLinks = [
    { name: 'Didim Klima Tamiri', path: '/didim-klima-tamiri' },
    { name: 'Didim Klima Bakımı', path: '/didim-klima-bakimi' },
    { name: 'Didim Klima Montajı', path: '/didim-klima-montaji' },
];

export const serviceSectionLinks = [
    { name: 'Split Klima', path: `${SERVICES_PATH}#split-klima` },
    { name: 'VRF Sistemleri', path: `${SERVICES_PATH}#vrf` },
    { name: 'Isı Pompası', path: `${SERVICES_PATH}#isi-pompasi` },
    { name: 'Montaj & Keşif', path: `${SERVICES_PATH}#montaj` },
    { name: 'Güneş Enerjisi', path: `${SERVICES_PATH}#gunes-enerjisi` },
    { name: 'Soğuk Hava Deposu', path: `${SERVICES_PATH}#soguk-hava` },
];

/** Header Hizmetler dropdown grupları */
export const serviceMenuGroups = [
    { links: [allServicesLink] },
    { label: 'Didim Klima Servisi', links: klimaLandingLinks },
    {
        label: 'Sistemler & Enerji',
        links: serviceSectionLinks.filter(({ path }) =>
            ['#vrf', '#isi-pompasi', '#gunes-enerjisi'].some((hash) => path.endsWith(hash))
        ),
    },
];

export const mainNavLinks = [
    { name: 'Ana Sayfa', path: '/' },
    { name: 'Hakkımızda', path: '/hakkimizda' },
    { name: 'İletişim', path: '/iletisim' },
];

export const footerQuickLinks = [
    { name: 'Ana Sayfa', path: '/' },
    { name: 'Hakkımızda', path: '/hakkimizda' },
    allServicesLink,
    { name: 'İletişim', path: '/iletisim' },
    { name: 'Destek', path: '/destek' },
];

export const serviceAreaLinks = [
    { name: 'Altınkum Klima Servisi', path: '/altinkum-klima-servisi' },
    { name: 'Akbük Klima Servisi', path: '/akbuk-klima-servisi' },
    { name: 'Bozbük Klima Servisi', path: '/bozbuk-klima-servisi' },
];
