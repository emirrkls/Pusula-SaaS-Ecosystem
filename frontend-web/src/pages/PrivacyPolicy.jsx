import React from 'react';

const PrivacyPolicy = () => {
  return (
    <section className="pt-36 pb-20 bg-gray-50 min-h-screen">
      <div className="container mx-auto px-4 md:px-8 max-w-4xl">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 md:p-10">
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-4">Gizlilik Politikası</h1>
          <p className="text-sm text-gray-500 mb-8">Son güncelleme: 30 Nisan 2026</p>

          <div className="space-y-6 text-gray-700 leading-relaxed">
            <p>
              Pusula İklimlendirme olarak, kişisel verilerinizin güvenliğini önemsiyoruz. Bu politika;
              web sitemiz ile Android ve iOS mobil uygulamalarımız üzerinden toplanan verilerin
              hangi amaçlarla kullanıldığını, nasıl saklandığını ve haklarınızı açıklar.
            </p>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">1. Toplanan Veriler</h2>
              <p>
                Hizmet kalitesini sağlamak için ad-soyad, iletişim bilgileri, işlem kayıtları,
                teknik servis formları, cihaz ve uygulama kullanım bilgileri ile ödeme ve abonelikle
                ilgili sınırlı verileri işleyebiliriz.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">2. İşleme Amacı</h2>
              <p>
                Veriler; servis operasyonunun yürütülmesi, kullanıcı hesap yönetimi, faturalama,
                güvenlik, yasal yükümlülüklerin yerine getirilmesi ve ürün geliştirme amaçlarıyla işlenir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">3. Uygulama İzinleri (Android ve iOS)</h2>
              <p>
                Mobil uygulamalarımızda aşağıdaki izinler kullanılabilir:
                <br />
                <strong>Kamera İzni:</strong> Barkod/QR kod tarama işlemleri için kullanılır.
                Kamera, yalnızca kullanıcı ilgili özelliği kullandığında etkinleşir.
                <br />
                <strong>İnternet İzni:</strong> Sunucu iletişimi, giriş işlemleri ve servis verilerinin
                senkronizasyonu için kullanılır.
                <br />
                <strong>Titreşim İzni:</strong> Barkod okuma gibi durumlarda kullanıcıya geri bildirim
                vermek için kullanılır.
                <br />
                Uygulama izinleri; özelliğin çalışması için gerekli olduğu ölçüde ve sınırlı amaçlarla kullanılır.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">4. Veri Paylaşımı</h2>
              <p>
                Verileriniz, yasal zorunluluklar dışında üçüncü kişilerle satılmaz. Altyapı,
                ödeme ve iletişim hizmetleri için yalnızca gerekli olduğu kapsamda yetkili
                hizmet sağlayıcılarla paylaşılabilir.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">5. Saklama ve Güvenlik</h2>
              <p>
                Veriler teknik ve idari tedbirlerle korunur. Yetkisiz erişim, değiştirme veya
                kayıplara karşı güvenlik kontrolleri uygulanır.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">6. Haklarınız</h2>
              <p>
                KVKK kapsamında verilerinize erişme, düzeltme, silme ve işlemeye itiraz haklarına
                sahipsiniz. Talepleriniz için aşağıdaki iletişim kanallarını kullanabilirsiniz.
              </p>
            </div>

            <div>
              <h2 className="text-xl font-semibold text-brand-dark mb-2">7. İletişim</h2>
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
