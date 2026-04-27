import React from 'react';

const TermsOfUse = () => {
  return (
    <section className="pt-36 pb-20 bg-gray-50 min-h-screen">
      <div className="container mx-auto px-4 md:px-8 max-w-4xl">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 md:p-10">
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-4">Kullanim Sartlari</h1>
          <p className="text-sm text-gray-500 mb-8">Son guncelleme: 27 Nisan 2026</p>

          <div className="space-y-6 text-gray-700 leading-relaxed">
            <p>
              Bu sartlar, Pusula Service uygulamasi ve web platformunun kullanim kosullarini belirler.
              Uygulamayi kullanarak asagidaki kosullari kabul etmis sayilirsiniz.
            </p>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">1. Hizmetin Kapsami</h2>
              <p>
                Platform, teknik servis operasyonlari, is emri yonetimi, tahsilat kaydi ve raporlama
                amaciyla sunulur. Sunulan ozellikler abonelik paketine gore degisebilir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">2. Hesap Guvenligi</h2>
              <p>
                Hesap bilgilerinizin gizliliginden siz sorumlusunuz. Yetkisiz kullanim suphelerinde
                derhal tarafimiza bilgi vermelisiniz.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">3. Kabul Edilmeyen Kullanim</h2>
              <p>
                Hukuka aykiri islem yapmak, sistemi manipule etmek, hizmete zarar vermek veya
                baska kullanicilarin haklarini ihlal etmek yasaktir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">4. Abonelik ve Odeme</h2>
              <p>
                Ucretli paketler App Store ve ilgili odeme sistemlerinin kosullarina tabidir.
                Fiyatlandirma ve yenileme detaylari satin alma aninda gosterilir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">5. Sorumlulugun Sinirlandirilmasi</h2>
              <p>
                Hizmet, teknik kesinti veya ucuncu taraf altyapi sorunlari nedeniyle gecici olarak
                erisilemeyebilir. Mevzuatin izin verdigi olcude sorumluluk sinirlandirilir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">6. Iletisim</h2>
              <p>
                E-posta: <a className="text-brand-cyan hover:underline" href="mailto:pusulaiklimlendirme.didim@gmail.com">pusulaiklimlendirme.didim@gmail.com</a><br />
                Telefon: <a className="text-brand-cyan hover:underline" href="tel:+905400250925">+90 540 025 09 25</a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
};

export default TermsOfUse;
