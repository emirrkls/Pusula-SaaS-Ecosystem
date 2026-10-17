# Pusula destek merkezi

Bu modül masaüstü, iOS ve Android'den gelen soruları, hata bildirimlerini ve geliştirme önerilerini ortak backend üzerinden Süper Admin paneline taşır. Özellik varsayılan olarak kapalıdır; backend, masaüstü, Android ve panel geliştirmeleri yereldedir. 17 Ekim doğrulamasında yalnızca iOS destek dosyaları ayrı `codex/support-center-validation` dalına gönderildi; `main` ve incelemedeki build 54 değiştirilmedi. GitHub Actions kullanılmadan ayrı, dağıtımsız Xcode Cloud test akışı kuruldu. Üretim uygulaması yeniden yayımlanmadı/başlatılmadı; kullanıcı onayıyla e-posta rumuzu ve bir sonraki yayın için korumalı VPS SMTP ayarları hazırlandı.

## Kullanıcı ve yönetici akışı

- Üç uygulamada **Yardım ve Destek** girişi bulunur. Giriş ekranından mevcut destek e-postasına ulaşılabilir; oturumsuz talep API'si yoktur.
- Giriş yapan şirket yöneticisi ve teknisyen kendi taleplerini açar, önceki mesajları okur, ek indirir ve yanıtlayabilir. Aynı işletmedeki başka bir kullanıcının talebi gösterilmez.
- Türler soru, hata ve öneridir. Durumlar yeni, inceleniyor, kullanıcıdan bilgi bekleniyor ve çözüldü; öneriler için ayrıca değerlendiriliyor ve planlandı vardır.
- Kullanıcının yanıtı çözülen talebi yeniden açar. Önceliği ve yönetim durumunu yalnızca destek yetkilisi değiştirir.
- Süper Admin talep listesini tür, durum, işletme ID ve konu ile filtreler; kullanıcıya yanıt veya yalnızca ekibe görünen dahili not yazar. Dahili not ile durum/öncelik değişimi tek gönderimde birleştirilmez.
- `SUPER_ADMIN` ve `SUPER_ADMIN_OPS` okur/yazar; `SUPER_ADMIN_READONLY` yalnızca okur. Temsili işletme oturumları destek verilerine erişemez.
- Uygulama etkin ekrandayken okunmamış yanıt sayısı yaklaşık 60 saniyede yenilenir. Bu sürüm arka planda APNs/FCM destek bildirimi göndermez.

## Bileşenler

| Alan | Kaynak |
|---|---|
| Backend | `backend/src/main/java/com/pusula/backend/support/` |
| Veritabanı | `V49__support_center.sql` |
| Windows | `frontend-desktop/.../support/` ve `controller/SupportCenterView.java` |
| iOS | `Core/Support/`, `Services/SupportService.swift`, `Views/Shared/SupportCenterView.swift` |
| Android | `ui/support/` |
| Süper Admin | Ayrı `Pusula-Super-Admin-Panel` deposundaki `support-center.js` ve `support-center.css` |

Süper Admin klasörü ana deponun `.gitignore` kapsamındadır. İleride yayın onayı verildiğinde iki deponun değişiklikleri ayrı kontrol edilmelidir; ana depo push'u panel dosyalarını içermez.

## API ve kayıt modeli

- `GET /api/public/support-contact`: etkinlik ve iletişim bilgileri; kullanıcı verisi döndürmez.
- `/api/support/tickets`: kullanıcının kendi talep listesi ve yeni talep gönderimi.
- `/api/support/tickets/{id}`: konuşma; `before` ile önceki mesajlar.
- `/api/support/tickets/{id}/messages`: yanıt; `/read`: okundu işareti.
- `/api/support/attachments/{id}`: yetkili özel indirme; `/api/support/unread-count`: sayaç.
- `/api/superadmin/support/...`: aynı kaynakların destek yetkisine bağlı yönetim sürümü.

Beş yeni tablo iş emri tablolarından ayrıdır: `support_tickets`, `support_messages`, `support_attachments`, `support_events`, `support_mail_outbox`. Liste sayfaları 30, konuşma sayfaları 100 mesajla sınırlandırılır. Tarihler UTC olarak saklanır.

Yeni talep ve mesaj, istemcinin koruduğu UUID `clientKey` ile gönderilir. Kullanıcı satırı ve ilgili talep kilitlenir; mesaj, ekler ve e-posta kuyruğu aynı transaction içinde kaydedilir. `(author_id, client_key)` benzersizdir. Aynı anahtar/içerik tekrarında önceki sonuç döner; değişmiş içerikte 409 alınır. Backend belirsiz arızaları 503 olarak ve içerik sızdırmadan bildirir. İstemci belirsiz sonuçta yeni anahtar üretmez ve otomatik arka plan gönderimi yapmaz.

Aboneliği salt okunur hale gelen kullanıcı desteğe yazabilir; istisna yalnızca kendi destek oluşturma/yanıt/okundu yollarına uygulanır. Finans ve diğer işlem kısıtları değişmez.

## Taslaklar ve bağlantı kesintileri

- Windows taslağı oturum hesabına ayrılmış, Windows kullanıcısına bağlı DPAPI şifrelemesiyle atomik dosyada saklanır.
- Android taslağı hesap bazlı `EncryptedSharedPreferences` içinde saklanır; ilgili dosyalar bulut yedeği ve cihaz aktarımından hariçtir.
- iOS taslağı hesap bazlı, korumalı ve yedek dışı Application Support dosyasıdır.
- Gönderimden önce içerik ve anahtar birlikte saklanır. Yanıt alınana kadar girişler dondurulur; **aynı gönderimi tekrar dene** aynı içeriği/anahtarı kullanır. Kesin form reddinde düzenleme tekrar açılır.
- Hesap değiştirilince eski yanıtlar ekrana uygulanmaz. Hesap silmede yalnızca silinen hesabın yerel taslağı temizlenir. Normal çıkışta taslak başka hesaba gösterilmeden korunur.
- Süper Admin taslakları yalnızca açık sekme belleğindedir; tarayıcı kalıcı depolamasına mesaj yazılmaz. Aynı sekmede belirsiz gönderim anahtarı korunur, yenileme/kapatma öncesi uyarı gösterilir. Tarayıcı tamamen kapanır veya yönetici çıkış yaparsa taslak geri yüklenmez; belirsiz gönderimden sonra konuşmayı kontrol etmeden yeni yanıt oluşturulmamalıdır.

## Ekler ve gizlilik

- Bir mesajda en fazla 3 ek; JPEG/PNG için giriş sınırı 2 MiB, JSON bağlantı raporu için 256 KiB. HTTP gövdesi 8.500.000 baytla sınırlıdır.
- Sunucu görsel türünü ve boyutlarını doğrular, en fazla 16 megapiksel kabul eder, 1600 piksele küçültüp JPEG olarak yeniden kodlar. EXIF ve istemci dosya adı saklanmaz. Görselin görünen içeriği otomatik anonimleştirilmez; kullanıcı göndermeden önce hassas alanları kapatmalıdır.
- JSON raporu yalnızca `schemaVersion: 1` ve `records` yapısından yeniden oluşturulan sınırlı teknik alanları içerir. Rastgele JSON, ham header/body/token, IP, tam URL veya serbest hata metni saklanmaz. Rotaların bilinmeyen bölümleri maskelenir. En fazla 100 kayıt ve kayıt başına 5 hata kodu tutulur.
- Ekler PostgreSQL `BYTEA` alanındadır; herkese açık `/uploads` yoluna konmaz. İndirme sahiplik/yetki kontrolüyle, `no-store`, `nosniff` ve güvenli dosya adıyla yapılır. Dahili not eki kullanıcı tarafından ID bilinse bile indirilemez.
- Tutulan ekler kullanıcı başına 20 MiB, destek yetkilisi başına 100 MiB ile sınırlıdır. Sınır dolunca yalnızca yeni ek reddedilir; metin gönderimi kullanılabilir. Bu kota işletmenin servis fotoğrafı kotasından ayrıdır.
- Kullanıcı başına günde 10 yeni talep, saatte 60 mesaj sınırı vardır; mevcut gönderimin tekrarı yeniden sayılmaz.
- Ekler 30 gün, çözülen talepler kapanıştan 180 gün sonra günlük temizlik işiyle silinir; kapanmış konuşmalar bir çalışmada en fazla 100 adet temizlenir. Yedeklerin saklama süresi ayrıca yönetilmelidir.
- Hesap silme kullanıcının destek konuşmalarını, dosyalarını ve bekleyen e-posta kayıtlarını kaldırır. Başka kullanıcıların konuşmalarındaki silinen destek çalışanı kimliği anonimleştirilir.
- Gizlilik metni ve mağaza beyanları yayından önce destek mesajı ve isteğe bağlı görsel/teknik rapor işlemesini kapsayacak şekilde gözden geçirilmelidir. Parola, kart bilgisi veya müşteri/finans dökümü istenmez.

## Alan adları ve e-posta

Hedef kurulum `pusulaiklimlendirme.store` üzerinde ürün/pazarlama/destek, `admin.pusulaiklimlendirme.cloud` üzerinde yönetim panelidir. Mevcut `.com` sitesi ve `https://api.pusulaiklimlendirme.com` API adresi değiştirilmez.

17 Ekim 2026'da kullanıcı onayıyla `destek@pusulaiklimlendirme.store`, aktif `bilgi@pusulaiklimlendirme.store` kutusuna rumuz olarak bağlandı; ikinci kutu alınmadı. `QA-20261017-A7C2` yapay test mesajı bu rumuz üzerinden bilgi kutusuna ulaştı. Destek rumuzundan gönderilen yanıt Gmail gelen kutusuna ulaştı; alıcı sunucu SPF, DKIM ve DMARC için `pass` bildirdi. DMARC politikası `p=none` olduğundan bu yalnızca izleme politikasıdır.

Hostinger planı **16 Kasım 2026'ya kadar deneme** durumundadır. Ödeme/yenileme değiştirilmedi; kesintisiz kullanım için kullanıcı yenilemeyi ayrıca yönetmelidir. Yerel backend iletişim varsayılanı doğrulanmış destek adresidir. API erişilemezse eski istemci sabiti hâlâ çalışan kurumsal Gmail adresidir; backend yayımlanınca alınan iletişim yanıtı destek adresini gösterecektir. Canlı backend ayarına bu oturumda müdahale edilmedi.

`admin.pusulaiklimlendirme.cloud` henüz DNS'te tanımlı değildir. Yönetim panelinin yeni alan adına yayını, TLS/CORS ve yönetici erişimi ayrı yayın onayı gerektirir.

Kullanıcı mevcut posta parolasını Hostinger VPS terminalindeki gizli `getpass` alanına kendisi girdi; parola sohbetten alınmadı, repoya/loga yazılmadı. VPS'ten zorunlu STARTTLS ve sertifika doğrulamasıyla SMTP kimlik doğrulaması geçti; `QA-20261017-SMTP1` yapay mesajı Gmail gelen kutusuna ulaştı. Ayarlar `/etc/pusula-support-smtp.env` dosyasında `0600 root`, systemd bağlantısı `40-support-smtp.conf` içinde hazırlandı. Ana `/etc/pusula-backend.env` dosyası değiştirilmedi. Yalnızca `daemon-reload` yapıldı; mevcut backend süreci yeniden başlatılmadı. Ayarlar bir sonraki onaylı yeniden başlatma/yayında yüklenir ve `APP_SUPPORT_ENABLED=false` kalır. Bu SMTP testi, henüz yayımlanmayan destek outbox akışının uçtan uca testi değildir.

Admin uyarıları dayanıklı `support_mail_outbox` kuyruğudur. SMTP ve alıcı yapılandırılmadığında gönderim yapılmaz. Mesaj yalnızca talep referansı içerir; kullanıcının metni veya ekleri e-postaya taşınmaz. Sekiz deneme ve sınırlı artan bekleme uygulanır. Teslimat en az bir kez ilkesindedir; SMTP kabulü ile DB kaydı arasındaki kesintide uyarı e-postası yinelenebilir, destek mesajı yinelenmez. Kullanıcının doğrulanmamış işletme e-postasına otomatik yanıt gönderilmez.

Parolasız bağlantı tanılaması için `scripts/test-support-smtp-transport.ps1` kullanılabilir: yalnızca EHLO/STARTTLS ve platformun sertifika/hostname doğrulamasını yapar; AUTH veya e-posta göndermez. 17 Ekim testi TLS 1.3 ile geçti. Bu kontrol, parola doğrulamasının veya gerçek outbox teslimatının yerine geçmez.

## Yerel doğrulama

Ana depoda:

```powershell
cd backend
mvn test
cd ../frontend-desktop
mvn test
cd ../frontend-playstore/PusulaService
./gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --offline --no-daemon
cd ../..
node --test scripts/test-support-contract.cjs scripts/test-ios-auth-contract.cjs scripts/test-ios-release-contract.cjs scripts/test-xcode-cloud-contract.cjs scripts/test-ios-testflight.cjs
```

Ayrı panelde `npm run build` çalıştırılır. Panelin mevcut Vite geliştirme proxy'si üretim API'sine gidebilir; destek arayüz testi için onu kullanmayın. Bunun yerine panel build'inden sonra ana depoda `node scripts/preview-support-local.cjs` çalıştırın ve `http://127.0.0.1:8574/` açın. Bu sunucu yalnızca loopback'e bağlanır, API çağrılarını sahte yerel verilerle cevaplar ve dış API'ye iletmez. İstenirse `local-preview@example.invalid` / `offline-fixture` gibi tamamen uydurma bilgilerle giriş yapılabilir; gerçek parola kullanılmaz. Bu fixture backend güvenlik testi değildir.

Mac'te `cd frontend-appstore && swift test` ile beş destek modeli/taslak testi dahil paket testleri çalıştırılmalıdır. Ardından gerçek iPhone/iPad Simulator hedefinde uygulama derlemesi ve aşağıdaki senaryolar doğrulanmalıdır. Windows kaynak sözleşmesi testlerinin geçmesi iOS'un derlendiğini kanıtlamaz.

17 Ekim ek doğrulamaları:

- Yerel PostgreSQL 17.7 üzerinde yalnızca loopback'e bağlı, boş ve atılabilir veritabanında **10/10** destek testi geçti. Gerçek V49 migration/partial index, erişim/retention ve sekiz paralel aynı-anahtar gönderiminin tek talep/mesaj/outbox üretmesi doğrulandı. `SupportPostgresIntegrationTest`, `SUPPORT_TEST_JDBC_URL` ile yalnızca localhost `support_validation_*` veritabanlarına izin verir; canlı veritabanına karşı çalıştırılmaz. Mevcut dolu staging yedeğine geçiş provası ayrıca yayın kapısıdır.
- Android debug derlemesi ve **14 birim testi**, yeni API 35 emülatöründe gerçek Compose ekranı/ViewModel/şifreli depolama kullanan **3 arayüz testi** geçti. Kayıp yanıtta aynı anahtar, yalnızca konu içeren taslak ve hesap değişiminde eski yanıtın atılması doğrulandı. Test taşıyıcısı sadece `androidTest` içindedir; gerçek API/hesap kullanılmaz.
- Masaüstü JavaFX ekranında **3 arayüz testi** yerel yapay HTTP sunucusu ve gerçek Windows DPAPI taslağıyla geçti. Belirsiz gönderim tekrarı, yenileme hatasında listenin korunması ve oturumsuz e-posta yolu doğrulandı. Taslak okunmadan yeni form açılmasını engelleyen zamanlama koruması eklendi. Tam masaüstü paketi: 55 test, 52 geçen, 3 ilgisiz koşullu test atlandı.
- Xcode Cloud `Support Validation` akışı yalnızca `codex/support-center-validation` dalında manuel Test çalıştırır; iPhone 17 ve iPad Air 11-inch (M3), zorunlu testler, temiz derleme. Archive, TestFlight ve başka post-action yoktur. Build 60 ortak iptal kontrolünün yanlış tür adını yakaladı; 817d7ac ile düzeltildi. **Build 61 Succeeded:** 106 Swift model/politika testi ve üç saat diliminin her birinde 17 tarih regresyonu geçti. 39 arayüz senaryosu her iki zorunlu hedefte geçti (78 cihaz çalıştırması, 0 başarısız/atlanan); dört yeni destek senaryosu buna dahildir. İki destek senaryosunda klavye açılışıyla birlikte `Invalid frame dimension (negative or non-finite)` uyarısı üretildi; assertion'lar geçti, gerçek cihaz pilotunda görsel kontrol edilmelidir. Kesin sonuç ve kanıtlar `outputs/support-validation.json` içindedir. Arayüz fixture'ları yalnızca DEBUG + Simulator + açık ekran görüntüsü bayrağıyla çalışır, gerçek oturum açmaz.

```powershell
# Masaüstü gerçek JavaFX testleri (yalnızca yapay yerel veri)
$env:SUPPORT_UI_TEST='true'
cd frontend-desktop
mvn test

# Hazır, atılabilir Android emülatöründe sadece destek testleri
cd ../frontend-playstore/PusulaService
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.pusula.service.ui.support.SupportFlowInstrumentedTest' --no-daemon
```

## Yayın öncesi kontrol listesi

1. İncelemedeki build 54'ü ve mağazada seçili build'i değiştirmeyin. Yeni istemciler ayrı yayın onayıyla hazırlanmalıdır.
2. Yerel gerçek PostgreSQL testleri tamamlandı; ayrıca mevcut dolu staging yedeğine V49 migration provası ve yedekten dönüş kontrolü yapın. Boş PostgreSQL testi mevcut üretim şemasının/yedeğinin birebir provası değildir.
3. iOS Mac derlemesini ve üç platformda gerçek arayüz testlerini tamamlayın: konu yazıp kapat/aç, ek önizle/çıkar, bağlantı kaybı ve aynı gönderim, hesap değişimi, uzun konuşma, erişim reddi, klavye ve büyük metin.
4. A kullanıcısı/B kullanıcısı/B işletmesi, salt okunur abonelik, salt okunur destek yetkilisi, temsili oturum ve dahili ek erişim matrisini staging'de kontrol edin.
5. Alan adları, TLS, CORS izinleri, yönetici erişimi ve doğrulanmış posta kutusunu kurun. `APP_SUPPORT_ENABLED=false` durumunu pilot başlayana kadar koruyun.
6. SMTP ayarları ve root korumalı parola hazırlandı; sonraki onaylı backend yayını/yeniden başlatmasından sonra kontrollü sentetik outbox testiyle teslimatı doğrulayın. `APP_SUPPORT_ENABLED=false`, `APP_SUPPORT_EMAIL=destek@pusulaiklimlendirme.store`, `APP_SUPPORT_WEBSITE=https://www.pusulaiklimlendirme.com/destek`, `APP_SUPPORT_NOTIFY_EMAIL=bilgi@pusulaiklimlendirme.store` ve `APP_SUPPORT_MAIL_FROM=destek@pusulaiklimlendirme.store` korunur. SMTP sunucusu `smtp.hostinger.com`, STARTTLS portu 587, oturum kullanıcısı `bilgi@pusulaiklimlendirme.store` olmalıdır; rumuz ayrı posta kutusu/parola değildir. SMTP parolası sohbet, repo veya loga yazılmaz. Varsayılan bağlantı zaman aşımı 5 saniye, okuma/yazma 10 saniyedir; STARTTLS zorunlu ve sunucu kimliği doğrulaması açıktır. `scripts/setup-support-smtp.py` yalnızca etkileşimli root terminalinde çalışır, mevcut özel ayarları üzerine yazmaz, önce SMTP doğrulamasını yapar ve uygulamayı yeniden başlatmaz; yeniden kurulum için eski dosyaları düşünmeden silmeyin.
7. Gizlilik metinlerini ve mağaza beyanlarını doğrulayın; önce tek demo işletmesinde pilot yapın, sonra özellik bayrağını açın. İşlem fişleri, finans kayıtları ve mevcut APNs/FCM iş emri bildirimleri etkilenmemelidir.

SMTP ayarları için resmî kaynak: [Hostinger Email yapılandırması](https://www.hostinger.com/support/1575756-how-to-get-email-account-configuration-details-for-hostinger-email/). Yeni e-posta sağlayıcısının veri işleme koşulları da gizlilik kontrolüne dahil edilir; Almanya'daki VPS konumu e-posta yolunun yalnızca Almanya'da işlendiğini kanıtlamaz.

## Sonraki aşamalar

E-posta/WhatsApp'tan talep alma, arka plan push yanıtları, temsilci ataması, SLA/iş saati otomasyonu, bilgi bankası, memnuniyet ve gelişmiş raporlar bu ilk sürüme dahil değildir. `.store` pazarlama sitesi ayrı kurulum işidir. Destek rumuzuna gelen e-posta bilgi kutusundan elle takip edilir; otomatik talep kaydına dönüşmez. Hazır olmayan kanallar canlı gösterilmez.
