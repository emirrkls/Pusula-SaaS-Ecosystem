export const SERVICES_PATH = '/hizmetler';

export const allServicesLink = { name: 'Tüm Hizmetler', path: SERVICES_PATH };

export const klimaLandingLinks = [
    { name: 'Didim Klima Tamiri', path: '/didim-klima-tamiri' },
    { name: 'Didim Klima Bakımı', path: '/didim-klima-bakimi' },
    { name: 'Didim Klima Montajı', path: '/didim-klima-montaji' },
    { name: 'Klima Gaz Dolumu', path: '/didim-klima-gaz-dolumu' },
    { name: 'Hisense Klima Servisi', path: '/didim-hisense-klima-servisi' },
];

export const serviceSectionLinks = [
    { name: 'Split Klima', path: '/didim-split-klima-servisi' },
    { name: 'Ticari Klima', path: '/didim-ticari-klima-servisi' },
    { name: 'VRF Sistemleri', path: '/didim-vrf-servisi' },
    { name: 'Isı Pompası', path: '/didim-isi-pompasi-servisi' },
    { name: 'Havuz Isı Pompası', path: '/didim-havuz-isi-pompasi' },
    { name: 'Montaj & Keşif', path: '/didim-klima-montaji' },
    { name: 'Güneş Enerjisi', path: '/didim-gunes-enerjisi-sistemleri' },
    { name: 'Soğuk Hava Deposu', path: '/didim-soguk-hava-deposu-servisi' },
];

/** Header Hizmetler dropdown grupları */
export const serviceMenuGroups = [
    { links: [allServicesLink] },
    { label: 'Didim Klima Servisi', links: klimaLandingLinks },
    {
        label: 'Isı Pompaları',
        links: serviceSectionLinks.filter(({ path }) =>
            ['/didim-isi-pompasi-servisi', '/didim-havuz-isi-pompasi'].includes(path)
        ),
    },
    {
        label: 'Sistemler & Enerji',
        links: serviceSectionLinks.filter(({ path }) =>
            [
                '/didim-split-klima-servisi',
                '/didim-ticari-klima-servisi',
                '/didim-vrf-servisi',
                '/didim-gunes-enerjisi-sistemleri',
                '/didim-soguk-hava-deposu-servisi',
            ].includes(path)
        ),
    },
];

export const mainNavLinks = [
    { name: 'Ana Sayfa', path: '/' },
    { name: 'Hakkımızda', path: '/hakkimizda' },
    { name: 'Referanslarımız', path: '/referanslarimiz' },
    { name: 'İletişim', path: '/iletisim' },
];

export const footerQuickLinks = [
    { name: 'Ana Sayfa', path: '/' },
    { name: 'Hakkımızda', path: '/hakkimizda' },
    { name: 'Referanslarımız', path: '/referanslarimiz' },
    allServicesLink,
    { name: 'İletişim', path: '/iletisim' },
    { name: 'Destek', path: '/destek' },
];

export const serviceAreaLinks = [
    { name: 'Altınkum Klima Servisi', path: '/altinkum-klima-servisi' },
    { name: 'Akbük Klima Servisi', path: '/akbuk-klima-servisi' },
    { name: 'Bozbük Klima Servisi', path: '/bozbuk-klima-servisi' },
];
