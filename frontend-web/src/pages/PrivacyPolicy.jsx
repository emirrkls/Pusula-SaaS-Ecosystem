import React from 'react';
import { PageSeo } from '../seo/PageSeo';

const PrivacyPolicy = () => (
  <section className="pt-36 pb-20 bg-gray-50 min-h-screen">
    <PageSeo title="Gizlilik Politikası | Pusula Servis Yönetimi"
      description="Pusula Servis Yönetimi kişisel veri kullanımı, saklama, hesap silme ve gizlilik hakları."
      path="/privacy" />
    <div className="container mx-auto px-4 md:px-8 max-w-4xl">
      <article className="bg-white rounded-2xl shadow-xs border border-gray-100 p-6 md:p-10">
        <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-4">Gizlilik Politikası</h1>
        <p className="text-sm text-gray-500 mb-8">Son güncelleme: 7 Ekim 2026</p>
        <div className="space-y-8 text-gray-700 leading-relaxed">
          <p>
            Bu politika, Pusula İklimlendirme web sitesi ve Pusula Servis Yönetimi masaüstü,
            Android ve iOS uygulamaları için geçerlidir. Hizmeti sunan Emircan Keleş / Pusula
            İklimlendirme, hesap ve platform işlemlerindeki kişisel verilerinizi burada açıklanan
            amaçlarla işler. İşletmelerin sisteme eklediği müşteri ve servis verileri kendi
            faaliyetleriyle ilgilidir; bu verilerin hukuka uygun toplanması, yetkili kişilerle
            paylaşılması ve saklanması ilgili işletmenin sorumluluğundadır.
          </p>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">1. Hangi verileri, neden işleriz?</h2>
            <ul className="list-disc pl-6 space-y-3">
              <li><strong>Hesap ve iletişim:</strong> Ad-soyad, kullanıcı adı, e-posta, işletme ve rol
                bilgileri; hesap açma, giriş, yetkilendirme ve destek için kullanılır. Parolalar
                açık metin olarak değil, tek yönlü parola özeti olarak saklanır.</li>
              <li><strong>Müşteri ve servis:</strong> Müşteri adı, telefon, e-posta, adres, servis
                konumu, iş emri, teklif, malzeme, teknisyen notu, fotoğraf, imza ve diğer kullanıcı
                içerikleri; servis operasyonu ve raporlar için işlenir. İşletmenin yetkilendirdiği
                roller ve tanımlı servis ağı erişimleri kapsamında gösterilir.</li>
              <li><strong>Finans ve abonelik:</strong> Cari hareketler, borç/alacak, tahsilat ve gider
                kayıtları işletmenin finans takibi için kullanılır. Ürün/paket, abonelik tarihleri
                ve doğrulanmış işlem bilgileri paket yetkileri, yenileme, iade ve geri yükleme için
                kullanılır. Apple işlem kimlikleri sunucuda eşleştirme için özetlenerek saklanır.
                App Store kart bilgilerini Apple işler; kart numarası veya güvenlik kodu
                sunucumuza gönderilmez.</li>
              <li><strong>Güvenlik ve bildirim:</strong> IP adresi, zaman damgası, hata/işlem kayıtları
                ve cihaz bildirim belirteci; güvenlik, teknik destek ve iş emri bildirimleri için
                kullanılır. Bildirim belirteçleri sunucuda şifreli saklanır.</li>
            </ul>
            <p className="mt-4">Veriler hizmetin ve sözleşmenin yürütülmesi, yasal yükümlülükler
              ve ölçülü güvenlik ihtiyaçları kapsamında; izin veya açık rıza gereken özelliklerde
              ilgili izninizle işlenir. Reklam hedefleme amacıyla kullanılmaz ve satılmaz.</p>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">2. Google ve Apple ile giriş</h2>
            <p>Bu seçeneklerde sağlayıcının doğruladığı hesap kimliği, ad ve e-posta bilgisi
              giriş/kayıt için işlenir. Apple ile e-postanızı gizlerseniz Apple’ın iletim adresi
              alınır. Google veya Apple parolanızı almayız. Apple giriş bağlantısını kaldırmak
              için gereken yenileme belirteci sunucuda şifreli saklanır; hesap silinirken Apple
              belirteci iptal edilir ve sosyal giriş bağlantıları kaldırılır. Sosyal giriş
              isteğe bağlıdır; kullanıcı adı/e-posta ve parola akışı da kullanılabilir.</p>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">3. Uygulama izinleri</h2>
            <p>Kamera, başlattığınız barkod/QR okuma veya servis fotoğrafı çekme işlemlerinde
              kullanılır. Fotoğraf seçicide yalnızca seçtiğiniz görsel alınır; arşivinizin
              tamamı sunucuya aktarılmaz. Servis adresleri ve kaydedilen konum bilgileri harita
              ve iş takibinde gösterilebilir; mevcut iOS/Android uygulamalarında cihazınızın
              konumu sürekli veya arka planda takip edilmez. Bildirim izni iş emri ve işlem
              bildirimleri; internet bağlantısı senkronizasyon; Android titreşim izni barkod
              okuma gibi geri bildirimler için kullanılır.</p>
            <p className="mt-3">İzinleri cihaz ayarlarından değiştirebilirsiniz. İzni kapatmak
              hesabınızı silmez; ilgili özelliği sınırlar. Önceden yüklenen görseli silmek veya
              iletişim tercihini değiştirmek için ilgili uygulama işlemi ya da destek kanalı
              kullanılabilir.</p>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">4. Kimlerle paylaşılır?</h2>
            <p>Veriler, yalnızca hizmetin gerektirdiği ölçüde barındırma, depolama, ödeme, giriş
              ve iletişim sağlayıcılarıyla işlenebilir. Apple; App Store, Apple ile giriş ve
              cihaz bildirimleri için, Google; Google ile giriş ve ilgili Android mağaza
              işlemleri için kullanılır. İşletmenin etkinleştirdiği WhatsApp entegrasyonunda
              alıcı telefon numarası ve gönderilen mesaj içeriği Meta/WhatsApp’a iletilir.
              Harita bağlantısını açtığınızda servis adresi/konumu harita sağlayıcısına
              aktarılabilir.</p>
            <p className="mt-3">Sağlayıcılar yurt dışında veri işleyebilir; kendi gizlilik
              koşulları da geçerlidir. Erişim gerekli kapsamla sınırlandırılır, veri güvenliği
              ve uygulanabilir veri aktarım yükümlülükleri gözetilir. Kamu kurumlarına ancak
              geçerli yasal talepler kapsamında bilgi verilir.</p>
            <p className="mt-3">Sağlayıcı politikaları:{' '}
              <a className="text-brand-cyan hover:underline" href="https://www.apple.com/legal/privacy/" target="_blank" rel="noopener noreferrer">Apple</a>,{' '}
              <a className="text-brand-cyan hover:underline" href="https://policies.google.com/privacy" target="_blank" rel="noopener noreferrer">Google</a>,{' '}
              <a className="text-brand-cyan hover:underline" href="https://www.whatsapp.com/legal/privacy-policy" target="_blank" rel="noopener noreferrer">WhatsApp</a>.
            </p>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">5. Saklama ve güvenlik</h2>
            <p>Hesap verileri hesap ve hizmet ilişkisi devam ederken gerekli amaçlarla saklanır.
              İşletme kayıtlarının süresi; işlem amacı, işletmenin talimatı ve varsa mali,
              hukuki veya garanti yükümlülüğüne göre belirlenir. Silme talebinde yasal olarak
              saklanması gereken kayıtlar ayrıca değerlendirilir; gerekçe ve uygulanacak süre
              talep sahibine açıklanır. Saklama amacı veya yükümlülüğü kalmayan veri silinir
              ya da anonimleştirilir.</p>
            <p className="mt-3">HTTPS, işletme/rol bazlı erişim, parola özetleme ve gerekli
              belirteçler için şifreleme kullanılır. Felaket kurtarma yedekleri canlı kullanımdan
              ayrı tutulur; geri yüklemede silme işlemlerinin korunması gerekir. Hesap silme,
              eski yedeklerin anında fiziksel imhası anlamına gelmez. Yedek saklama süresi ve
              talebinize uygulanacak imha kapsamı hakkında destekten bilgi alabilirsiniz.</p>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">6. Hesabımı nasıl silerim?</h2>
            <p>Mobil uygulamada <strong>Hesap / Profil veya Ayarlar → Hesabı Sil</strong> seçeneğiyle
              işlemi başlatıp onaylayabilirsiniz. Başarılı işlemde giriş erişimi kaldırılır;
              kullanıcı adı, parola özeti ve profil adı kişisel olmayan değerlerle değiştirilir,
              sosyal giriş bağlantıları ve cihaz bildirim kayıtları silinir. Profil imzası
              kaldırılır; fiziksel dosya silme işlemi kalıcı bir silme kuyruğuyla tamamlanır
              ve geçici hatada yeniden denenir. Kişisel giriş geçmişi ve kullanıcı profilinin
              denetim kopyaları da temizlenir. Başarısız silme, başarılıymış gibi gösterilmez;
              tekrar deneyebilir veya destek alabilirsiniz.</p>
            <p className="mt-3">Hesabı silmek, işletmenin veya diğer kullanıcıların ortak cari,
              servis, teklif ve mali kayıtlarını topluca silmez. İşlem ilişkileri için kişisel
              giriş bilgisi içermeyen bir kullanıcı işaretçisi kalabilir. Ortak kayıtlar ve
              yasal saklama kapsamındaki veriler için ayrıca silme/erişim talebinde
              bulunabilirsiniz. Silinmiş hesap yeniden girişle etkinleştirilmez; yeniden
              kayıt yeni hesap oluşturur.</p>
            <p className="mt-3"><strong>Hesabı silmek Apple aboneliğini otomatik iptal etmez.</strong>{' '}
              Yenilemeyi durdurmak için aboneliğinizi{' '}
              <a className="text-brand-cyan hover:underline" href="https://apps.apple.com/account/subscriptions/">Apple abonelik ayarlarından</a>{' '}
              ayrıca iptal edin. İade ilgili mağaza kurallarına tabidir. Ücretsiz Çırak paketi
              süresizdir ve otomatik ücretlendirme oluşturmaz.</p>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-brand-dark mb-3">7. Haklarınız ve iletişim</h2>
            <p>6698 sayılı Kanun kapsamında verilerinizin işlenip işlenmediğini öğrenme,
              bilgi isteme, amacını ve paylaşılan tarafları öğrenme, yanlış verileri
              düzelttirme, koşulları oluştuğunda silme/yok etme ve kanunda belirtilen diğer
              itiraz ve talep haklarına sahipsiniz. Kimliğinizi doğrulamaya yetecek bilgiyle
              aşağıdaki kanala başvurabilirsiniz; parola veya gereksiz hassas bilgi göndermeyin.
              Başvurular uygulanabilir usule göre en kısa sürede, en geç 30 gün içinde yanıtlanır.</p>
            <p className="mt-3">Veri sorumlusu / hizmet sağlayıcı: Emircan Keleş — Pusula İklimlendirme<br />
              Adres: Çamlık Mah. Ege Caddesi No:76/B, Didim/Aydın 09270<br />
              E-posta: <a className="text-brand-cyan hover:underline" href="mailto:pusulaiklimlendirme.didim@gmail.com">pusulaiklimlendirme.didim@gmail.com</a><br />
              Telefon: <a className="text-brand-cyan hover:underline" href="tel:+905400250925">+90 540 025 09 25</a>
            </p>
          </section>
          <p className="text-sm text-gray-500">Veri işleme değiştiğinde bu sayfa güncellenir.
            Yeni izin gerektiren özellikler, bilgilendirme ve izin tamamlanmadan mevcut
            izniniz varmış gibi kullanılmaz.</p>
        </div>
      </article>
    </div>
  </section>
);

export default PrivacyPolicy;
