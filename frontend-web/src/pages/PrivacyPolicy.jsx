import React from 'react';

const PrivacyPolicy = () => {
  return (
    <section className="pt-36 pb-20 bg-gray-50 min-h-screen">
      <div className="container mx-auto px-4 md:px-8 max-w-4xl">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 md:p-10">
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-4">Gizlilik Politikasi</h1>
          <p className="text-sm text-gray-500 mb-8">Son guncelleme: 27 Nisan 2026</p>

          <div className="space-y-6 text-gray-700 leading-relaxed">
            <p>
              Pusula Service olarak, kisisel verilerinizin guvenligini onemsiyoruz. Bu politika,
              uygulama ve web platformu uzerinden toplanan verilerin hangi amaclarla kullanildigini,
              nasil saklandigini ve haklarinizi aciklar.
            </p>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">1. Toplanan Veriler</h2>
              <p>
                Hizmet kalitesini saglamak icin ad-soyad, iletisim bilgileri, islem kayitlari,
                teknik servis formlari, odeme ve abonelikle ilgili sinirli bilgileri isleyebiliriz.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">2. Isleme Amaci</h2>
              <p>
                Veriler, servis operasyonunun yurutilmesi, kullanici hesap yonetimi, faturalama,
                guvenlik, yasal yukumlulukler ve urun gelistirme amaclariyla islenir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">3. Veri Paylasimi</h2>
              <p>
                Verileriniz yasal zorunluluklar disinda ucuncu kisilerle satilmaz. Altyapi,
                odeme ve iletisim hizmetleri icin sadece gerekli oldugu kapsamda yetkili
                hizmet saglayicilarla paylasilabilir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">4. Saklama ve Guvenlik</h2>
              <p>
                Veriler teknik ve idari tedbirlerle korunur. Yetkisiz erisim, degistirme veya
                kayiplara karsi guvenlik kontrolleri uygulanir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">5. Haklariniz</h2>
              <p>
                KVKK kapsaminda verilerinize erisme, duzeltme, silme ve isleme itiraz haklarina
                sahipsiniz. Talepleriniz icin asagidaki iletisim kanallarini kullanabilirsiniz.
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

export default PrivacyPolicy;
