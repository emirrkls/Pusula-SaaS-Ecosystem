import { useState, useSyncExternalStore } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Building2, Circle, Grid2X2, Pause, Play } from 'lucide-react';
import { PageSeo } from '../seo/PageSeo';
import { REFERENCE_CLIENTS, REFERENCE_ORBITS, REFERENCE_ORBIT_CAPACITY } from '../data/references';
import './References.css';

const motionQuery = '(prefers-reduced-motion: reduce)';

function subscribeToMotionPreference(onChange) {
    const query = window.matchMedia(motionQuery);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
}

function ReferenceCard({ reference }) {
    const [imageFailed, setImageFailed] = useState(false);

    return (
        <figure className="reference-card" data-reference-id={reference.id}>
            <div className="reference-card-logo">
                {reference.logo && !imageFailed ? (
                    <img
                        src={reference.logo}
                        alt=""
                        width="120"
                        height="76"
                        decoding="async"
                        onError={() => setImageFailed(true)}
                    />
                ) : (
                    <Building2 className="reference-card-placeholder" aria-hidden="true" />
                )}
            </div>
            <figcaption>{reference.name}</figcaption>
        </figure>
    );
}

export default function References() {
    const [paused, setPaused] = useState(false);
    const [listView, setListView] = useState(false);
    const reducedMotion = useSyncExternalStore(
        subscribeToMotionPreference,
        () => window.matchMedia(motionQuery).matches,
        () => false,
    );

    return (
        <>
            <PageSeo
                title="Referanslarımız | Pusula İklimlendirme – Didim"
                description="Pusula İklimlendirme'nin hizmet verdiği markalar ve işletmelerden bir seçki. Didim ve çevresinde güvenilir iklimlendirme çözümleri."
                path="/referanslarimiz"
                breadcrumbs={[
                    { name: 'Ana Sayfa', path: '/' },
                    { name: 'Referanslarımız', path: '/referanslarimiz' },
                ]}
            />
            <div className="references-page">
                <div className="references-container">
                    <header className="references-intro">
                        <p className="references-eyebrow">Referanslarımız</p>
                        <h1>İyi hizmet.<br /><span>Güçlü referanslar.</span></h1>
                        <p className="references-description">
                            Her işletmenin ihtiyacı farklı, yaklaşımımız aynı: özenli çalışma ve
                            güvenilir teknik destek. İklimlendirme çözümlerimizle hizmet verdiğimiz
                            markalardan ve kuruluşlardan bazıları.
                        </p>
                    </header>

                    <section className="references-showcase" aria-label="Hizmet verdiğimiz işletmeler">
                        <div className="references-toolbar">
                            <p>Hizmet verdiğimiz işletmelerden bir seçki</p>
                            <div className="references-controls">
                                {!listView && (
                                    <button
                                        type="button"
                                        onClick={() => setPaused((value) => !value)}
                                        aria-pressed={paused}
                                        disabled={reducedMotion}
                                    >
                                        {paused || reducedMotion ? <Play aria-hidden="true" /> : <Pause aria-hidden="true" />}
                                        {reducedMotion ? 'Hareket kapalı' : paused ? 'Hareketi başlat' : 'Hareketi durdur'}
                                    </button>
                                )}
                                <button
                                    type="button"
                                    onClick={() => setListView((value) => !value)}
                                    aria-pressed={listView}
                                >
                                    {listView ? <Circle aria-hidden="true" /> : <Grid2X2 aria-hidden="true" />}
                                    {listView ? 'Çember görünümü' : 'Liste görünümü'}
                                </button>
                            </div>
                        </div>

                        {!listView && (
                            <div className="reference-orbit" data-paused={paused || reducedMotion}>
                                <div className="reference-orbit-track reference-orbit-track--outer" aria-hidden="true" />
                                <div className="reference-orbit-track reference-orbit-track--inner" aria-hidden="true" />
                                <div className="reference-orbit-center" aria-hidden="true">
                                    <img src="/assets/img/logo.svg" alt="" width="128" height="58" />
                                    <span>Konforun merkezinde.</span>
                                </div>
                                {REFERENCE_ORBITS.map((orbit) => (
                                    <ul
                                        key={orbit.id}
                                        className={`reference-orbit-ring reference-orbit-ring--${orbit.id}`}
                                        aria-label="Referans işletmeler"
                                    >
                                        {orbit.references.map((reference, index) => (
                                            <li
                                                key={reference.id}
                                                className="reference-orbit-position"
                                                style={{ '--reference-angle': `${orbit.offset + index * 360 / orbit.references.length}deg` }}
                                            >
                                                <div className="reference-orbit-align">
                                                    <div className="reference-orbit-counter">
                                                        <ReferenceCard reference={reference} />
                                                    </div>
                                                </div>
                                            </li>
                                        ))}
                                    </ul>
                                ))}
                            </div>
                        )}

                        <ul className={`references-grid${listView ? ' references-grid--selected' : ''}`} aria-label="Tüm referans işletmeler">
                            {REFERENCE_CLIENTS.map((reference) => (
                                <li key={reference.id}><ReferenceCard reference={reference} /></li>
                            ))}
                        </ul>
                        {!listView && REFERENCE_CLIENTS.length > REFERENCE_ORBIT_CAPACITY && (
                            <button className="references-see-all" type="button" onClick={() => setListView(true)}>
                                Tüm referansları gör <ArrowRight aria-hidden="true" />
                            </button>
                        )}
                    </section>

                    <div className="references-bottom">
                        <p>İşletmeniz için doğru çözümü birlikte planlayalım.</p>
                        <Link to="/iletisim">Bizimle iletişime geçin <ArrowRight aria-hidden="true" /></Link>
                    </div>
                    <p className="references-brand-note">Logolar ve marka adları ilgili kuruluşlara aittir.</p>
                </div>
            </div>
        </>
    );
}
