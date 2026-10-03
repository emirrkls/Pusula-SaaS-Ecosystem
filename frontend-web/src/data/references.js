// Reference relationships are supplied by Pusula. Sources below verify names/logos,
// not the scope of a project or an endorsement by the entire brand network.
const logoRoot = '/assets/img/references';

export const REFERENCE_CLIENTS = [
    {
        id: 'madame-coco',
        name: 'Madame Coco',
        logo: `${logoRoot}/madame-coco.svg`,
        source: 'https://www.madamecoco.com/',
    },
    {
        id: 'espressolab',
        name: 'Espressolab',
        logo: `${logoRoot}/espressolab.svg`,
        source: 'https://espressolab.com/',
    },
    {
        id: 'tryp-by-wyndham-didim',
        name: 'TRYP by Wyndham Didim',
        logo: `${logoRoot}/tryp-by-wyndham-didim.png`,
        source: 'https://trypbywyndhamdidim.com/',
    },
    {
        id: 'akbuk-palace',
        name: 'Akbük Palace Hotel & Residence',
        logo: `${logoRoot}/akbuk-palace.jpg`,
        source: 'https://www.instagram.com/akbukpalaceresidence/',
    },
    {
        id: 'egeli-endustriyel-mutfak',
        name: 'Egeli Endüstriyel Mutfak',
        logo: `${logoRoot}/egeli-endustriyel-mutfak.jpg`,
        source: 'https://www.instagram.com/egeliendustriyelmutfakcom/',
    },
    {
        id: 'sec-market',
        name: 'Seç Market',
        logo: `${logoRoot}/sec-market.png`,
        source: 'https://www.secmarket.com.tr/',
    },
    {
        id: 'necdet-yapi',
        name: 'Necdet Yapı',
        logo: `${logoRoot}/necdet-yapi.png`,
        source: 'https://necdetyapi.com.tr/',
    },
    {
        id: 'gur-life-yapi',
        name: 'Gür Life Yapı İnşaat',
        logo: `${logoRoot}/gur-life-yapi.jpg`,
        source: 'https://www.instagram.com/gurlifeyapi/',
    },
    {
        id: 'subasi-insaat',
        name: 'Subaşı İnşaat',
        logo: null,
        source: null,
        verificationNote: 'Several Didim businesses share this name; awaiting owner confirmation.',
    },
    {
        id: 'sapphire-mimarlik',
        name: 'Sapphire Mimarlık',
        logo: `${logoRoot}/sapphire-mimarlik.jpg`,
        source: 'https://www.instagram.com/sapphiremimarlik/',
        verificationNote: 'The official profile is named Safir Mimarlık; its logo reads SAPPHIRE.',
    },
    {
        id: 'gucuyener-insaat',
        name: 'Gücüyener İnşaat',
        logo: `${logoRoot}/gucuyener-insaat.jpg`,
        source: 'https://www.instagram.com/gucuyener_insaat_/',
    },
    {
        id: 'can-serhat-yapi',
        name: 'Can Serhat Yapı',
        logo: null,
        source: null,
        verificationNote: 'Could not unambiguously match the supplied name; awaiting owner confirmation.',
    },
    {
        id: 'reklam-deposu',
        name: 'Reklam Deposu',
        logo: `${logoRoot}/reklam-deposu.jpg`,
        source: 'https://www.instagram.com/didimreklamdeposu/',
    },
    {
        id: 'ozsoy-yapi-muhendislik',
        name: 'Özsoy Yapı Mühendislik',
        logo: `${logoRoot}/ozsoy-yapi-muhendislik.jpg`,
        source: 'https://www.instagram.com/ozsoyyapimuh/',
    },
    {
        id: 'cs-can-yapi',
        name: 'CS Can Yapı',
        logo: null,
        source: null,
        verificationNote: 'The exact company name appears in Didim business listings, but no official logo source was verified.',
    },
    {
        id: 'pilot-garage',
        name: 'Pilot Garage Didim',
        logo: `${logoRoot}/pilot-garage.svg`,
        source: 'https://pilotgarage.com/tr/bayiler/aydin-didim',
    },
    {
        id: 'emin-oto',
        name: 'Emin Oto',
        logo: `${logoRoot}/emin-oto.png`,
        source: 'https://www.eminotoservis.com/',
    },
    {
        id: 'eg-garaj',
        name: 'EG Garaj',
        logo: `${logoRoot}/eg-garaj.jpg`,
        source: 'https://www.instagram.com/eggaraj09/',
    },
    {
        id: 'dfit-didim',
        name: 'D’Fit Didim',
        logo: `${logoRoot}/dfit-didim.jpg`,
        source: 'https://www.instagram.com/dfitdidim/',
        verificationNote: 'The official Didim profile is named D’Fit Fitness & Fight Club.',
    },
];

// Keep each orbit spacious. Any future additions beyond the showcase are still
// available in the full list/mobile layout, without packing extra logos into it.
export const REFERENCE_ORBIT_CAPACITY = 19;

export const REFERENCE_ORBITS = [
    { id: 'outer', references: REFERENCE_CLIENTS.slice(0, 11), offset: -90 },
    { id: 'inner', references: REFERENCE_CLIENTS.slice(11, REFERENCE_ORBIT_CAPACITY), offset: -60 },
];
