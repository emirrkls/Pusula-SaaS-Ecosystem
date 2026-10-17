# Mobil uygulama rehberi

iOS ve Android kapsamındadır; masaüstü turu değiştirilmez. Mağazada seçili build 54 ve manuel yayın ayarı bu çalışma tarafından değiştirilmez.

## İçerik ve kullanım

### Gerçek ekran turu revizyonu — 17 Ekim

**Son dağıtım — 18 Ekim 2026:** Xcode Cloud build **1.0 (73)**, kaynak `982eae8`, Test / Archive / TestFlight Internal Testing adımlarında başarılıdır. 139 Swift testi ve üç iş saat diliminde ayrı ayrı 17 tarih/liste testi geçti. 51 arayüz senaryosunun tamamı hem iPhone 17 hem iPad Air 11-inch (M3) üzerinde geçti (102 çalışma, 0 başarısız / 0 atlanan). Büyük yazıda arama → açık kayıt seçimi → Devam senaryosunun üç bağımsız açılışı da iki hedefte başarılıdır. Apple processing tamamlandı; mevcut beş kişilik Pusula Internal Testers grubuna dağıtım ve Türkçe test notları App Store Connect'te doğrulandı. Süre 31 dakika, Cloud hesaplama kullanımı 63 dakikadır. Build 54 ve manuel yayın ayarı değiştirilmedi; App Store genel yayını başlatılmadı. Aşağıdaki başarısız denemeler geçmiş doğrulama kayıtlarıdır.

Build 67'deki dersler metin sayfasıydı; arkada uygulama ekranı çizilmediği için koyu temada büyük boş alan oluşuyordu. Yeni revizyon, üretimde kullanılan SwiftUI/Compose ekranlarını salt okunur bir tur katmanında açar. iOS'ta hesap sekmesinin üzerinde tam ekran sunulur; Devam/Geri ayrı alt alandadır. Kayıt gerektiren adımda mevcut müşteri veya servis fişini kullanıcı seçer; ilk kayıt kendiliğinden seçilmez, tur için kayıt oluşturulmaz. İlgili bölüm gerçek yerleşimden vurgulanır/kaydırılır. İskonto/kurum/garanti açıklamalarında yalnız geçici form görünümü açılır; tutar uydurulmaz ve form gönderilmez.

Tur açıkken gerçek ekranın dokunma ve erişilebilirlik eylemleri kapalıdır. Ortak ağ katmanında GET/HEAD dışındaki istekler reddedilir; ilk fiziksel gönderim ve tekrar denemeler de kontrol edilir. Android'in ayrı destek istemcisi aynı kapıyı kullanır. Bağımsız yaşam döngüsü anahtarları eski bir kapatma olayının başka turu açığa çıkarmasını engeller. Destek turu gerçek taslağı geri yüklemez veya üzerine yazmaz. iOS bildirim yönlendirmesi tur bitene kadar bekler. Bu koruma sunucu yetkilerinin yerine geçmez ve paket/yetki kazandırmaz.

`tour.screen` / `tour.target` tüm 156 adımda kanonik katalogdan gelir. `node scripts/sync-mobile-tour.cjs` ve `node scripts/sync-mobile-guide.cjs --check` eşleşmeyi denetler. Android'de henüz gerçek hedefi bulunmayan yeniden planlama, tedarikçi borçları ve işletme mülkiyeti konuları yalnız iOS'ta gösterilir; ilgisiz bir finans sekmesine yönlendirilmez. Android müşteri geçmişi turu gerçek iş emri listesini seçilen müşteri kimliğine göre salt okunur süzer.

Konu listesi çevrimdışı çalışır; gerçek kayıt ekranlarının canlı okuma için bağlantıya ihtiyacı vardır. Okuma hatasında sahte kayıt veya başarılı işlem gösterilmez. Testler çevrimdışı sentetik içerik/taşıma sınırları kullanır; gerçek sunucuya veya gerçek kayıtlara yazmaz.

Kaynak `014a3b6` ve iOS düzeltmesi `4bf7b52`, `main` dalına normal push ile gönderildi; GitHub Actions çalışması oluşmadığı ayrıca kontrol edildi. Xcode Cloud build 69 (`4bf7b52`) için 139 Swift testi ve üç iş saat dilimi kapısı geçti; test paketi, Archive ve Apple processing tamamlandı. Arayüz sonucu iPhone 48/51, iPad 49/51 oldu; iç dağıtım çalışmadı. Rehberde iPhone büyük-yazı testinin 144. satırı, kayıt seçiminden sonra Devam'a basıp ilerlemeyi anında okuma sırasında başarısız oldu; başlık ve alt alan yerleşim kontrolleri geçmişti. Yeni test seçilen kaydı görünür kılar, kayıt seçimi ekranının kapanmasını ve Devam'ın etkinleşmesini bekler, ardından gerçek ilerleme değişimini doğrular. Hiçbir senaryo veya görünürlük assertion'ı kaldırılmaz. Sonraki Cloud kapısı ayrıca geçmelidir. Build 67'nin aşağıdaki geçmiş sonuçları bu revizyona aktarılmaz. Backend zaten uyumlu olduğundan gereksiz deploy yapılmadı; build 54 değiştirilmedi.

Birleşik kaynak doğrulaması: 84 yapısal kontrolde 83 geçti, ayrı yönetim paneli çalışma kopyasına bağlı 1 kontrol koşullu atlandı; hata yok. Android 1.3.8 (32): 25 birim testi, 12 arayüz testi (API 36 / 16 KB, 4 GiB test belleği), imzalı Release APK/AAB ve soğuk açılış geçti. Rehber çerçevesinin çevrimdışı testine ek olarak gerçek Hilt/üretim ekranı entegrasyonu çalıştırıldı; bu test başlamadan tüm uygulama HTTP'si engellenir ve gerçek oturum kurulmaz. Eksik kayıt bağlamında Devam kapalıdır; kayıt otomatik seçilmez.

### Büyük yazı yerleşimi ve kayıt hazırlığı

Turun gerçek ekranı/kayıt seçicisi ile açıklama paneli ayrı fiziksel alanlara yerleşir; açıklama bir safe-area örtüsü olarak kayıtların üzerine uzanmaz. Açıklama metni kendi alanında kaydırılır, Devam/Geri düğmeleri sabittir. Kayıt seçilirken açıklama paneli daha kısadır; kayıt hazır olunca ders alanı genişler. Erişilebilirlik testinde seçilecek kaydın tamamının gerçek seçici alanı içinde kaldığı ve dokunulduktan sonra bağlamın hazır olduğu doğrulanır. Kayıt ortaya alınırken sayfa boyu ileri/geri savrulan hareket yerine ölçülen mesafede kenar kaydırması kullanılır; görünürlük şartları gevşetilmez. Seçilen kayıt veya ekran değişince okuma görevi yeni anahtarla başlar; eski/iptal edilmiş okumalar yeni bağlamın yüklenme durumunu değiştiremez.

Build 70'te iPad'in 51 senaryosunun tamamı geçti; iPhone'da büyük-yazı rehber ve destek senaryoları başarısız oldu. Build 71'de iPhone destek merkezinin sekiz senaryosu geçti; rehber kayıt seçimi hâlâ hazır duruma ulaşmadı. Build 72 (`735c5f0`) ayrık tur yerleşiminde aynı iPhone senaryosunu geçemedi. Ayrıntılı hata ağacında erişilebilir XXXL yazıda kayıt kartı 292 pt, seçici alanı 242,7 pt ölçüldü; tam görünürlük geometrik olarak mümkün değildi. Kayıt seçimi için kısalan panel ve ölçülü kaydırma bu kanıta dayanır. Bu build'lerin dağıtım kapısı atlanmadı. Düzeltme, build 73'ün tam iki-cihaz seçkisinde geçti.

Build 68'de iki hedefte de 12 arayüz senaryosu başarısız oldu; iç dağıtım çalışmadı. Ayrı durumlarla açılan koşullu tam ekranın boş kalması, üst erişilebilirlik kimliklerinin alt düğmeleri örtmesi ve görselden ayrılma diyaloğunda iptal eyleminin erişilebilirlik ağacına alınmaması düzeltildi. Testler kaldırılmadı; sunumun hazır olması beklenir ve fotoğraflardaki açık ayrılma uyarısı iPhone/iPad'de aynı davranır. İki tanılama paketinde PusulaService uygulama çökmesi yoktu; Apple simülatör servis kayıtları uygulama çökmesi olarak sayılmadı.

Başarısız denemeler gizlenmedi: eski API 35 emülatöründe süreç kapanışı/SIGSEGV; temiz API 36'da yetersiz test belleği; gerçek müşteri ekranı testinde doğrudan `layoutInfo` gözleminin oluşturduğu yeniden çizim döngüsü görüldü. Döngü `derivedStateOf` ile yalnız kalem sayısını gözleyerek düzeltildi ve aynı gerçek ekran testi geçti. Büyük yazı testinde ayrıca platform klavye animasyonu sırasında farklı anlardan ölçüm alma yarışı ayrıştırıldı; klavyenin kapalı ve iki çerçevenin kararlı olması beklenir, görünürlük sınırları gevşetilmez. Son dokuz senaryonun tamamı geçti. Bunlar gerçek cihaz/paket satın alma/ağ güzergâhı testlerinin yerine geçmez.

Hesap ekranındaki **Uygulama Rehberi**; 10 kategori, 56 konu ve 156 kısa adımdan oluşur. Yönetici / teknisyen ve iOS / Android filtreleri gerçek yeteneklere göre uygulanır. Arama Türkçe karakter ve büyük-küçük harf farklarını tolere eder. Hızlı Başlangıç, konu seçimi, tamamlanmayanlar ve kaldığı yerden devam desteklenir. Paket gerektiren özellikler bilgilendirme olarak görülebilir; rehber yetki veya satın alma engelini kaldırmaz.

Rehber kayıt oluşturmaz, ödeme almaz, mesaj göndermez veya rastgele bir müşteri fişini açmaz. Henüz sunulmayan özellikler (örneğin bakım planlama) mevcut özellikmiş gibi anlatılmaz.

Tek kaynak: `guides/mobile-guide.json`. Değişiklik sonrası:

```sh
node scripts/sync-mobile-guide.cjs --write
node --test scripts/test-mobile-guide.cjs
```

Yerel kopyalar iOS bundle ve Android assets içine dahil edilir. Kapsama testi yeni ekranların eşleştirilmesini ister. Konu içeriği anlamlı biçimde değişirse o konunun revision değeri artırılır; eski tamamlanma yeni sürümü tamamlamış sayılmaz.

## İlerleme ve gizlilik

İlerleme yalnızca cihazda saklanır. Anahtar JWT subject + şirketin tek yönlü özeti ve rolden oluşur; JWT, ad, müşteri bilgisi veya bağlantı raporu kaydedilmez. Token yenileme ilerlemeyi kaybettirmez; farklı hesap, şirket ve roller ayrıdır. Hesap silme başarılı olduktan sonra ilgili ilerleme temizlenir. Android rehber tercihleri bulut yedekleme ve cihaz aktarımından hariçtir.

## Eski turun düzeltmeleri

iOS ilk tanıtımdaki Devam / Başla eylemleri kaydırılan içerikten ayrı sabit bir alt alandadır. İleri ve geri indeksleri sınırlandırılmıştır. Eski bağlamsal tur kartında tahmini sabit kart yüksekliğiyle konumlandırma kaldırılmış, metin kaydırılabilir tutulmuş ve eylemler ayrı alana alınmıştır. Aktif adımlar tur başında sabitlenir; finans yetkisi yoksa finans adımı gösterilmez.

iOS arayüzündeki genel yön oku ve Android'in eski kilit simgesi yerine mevcut gerçek Pusula uygulama simgesinin aynı baytları kullanılır.

Android rehberi sistem çubuklarını hesaba katan tam açılmış yerel alt sayfa olarak sunulur. İçerik kaydırılır; adım düğmeleri ayrı alt alandadır. Arama klavyesi kapatılarak derse geçilir. Arayüz testi Devam düğmesinin görünür rehber sınırlarının tamamı içinde kaldığını da doğrular.

## Doğrulama kapıları

- Node: katalog senkronizasyonu, kapsam, rol / platform tanımları, gerçek menü bağlantıları, simge eşitliği, salt okunur akış ve fixture güvenliği.
- Swift paket: katalog doğrulama, Türkçe arama, filtreler, indeks / son adım, devam / yeniden oynatma ve bozuk içerik.
- iPhone + iPad: gerçek yönetici Hesap ve teknisyen Profil menüleri, rehberde ilerleme / geri dönüş / devam / bitirme / hesap ayrımı, arama ve erişilebilir büyük yazı, eski ilk tanıtım ve bağlamsal tur.
- Android: aynı politika birim testleri ve gerçek Compose ekranında giriş, adımlar, arama, ilerleme / rol ayrımı ve temizleme. İmzalı Release APK / AAB, 16 KB uyumu ayrıca doğrulanır.

GitHub Actions kullanılmaz. iOS dağıtımı yalnızca gerekli Xcode Cloud testleri başarılı olduğunda ayrı bir TestFlight build'i olarak yapılır. Mağazadaki build seçimi / App Review başvurusu otomatik değiştirilmez.

## 17 Ekim 2026 doğrulaması

- Android 1.3.7 (31): 18 birim testi, 9 emülatör arayüz testi ve ayrıca sistem yazı boyutu 2× iken bir erişilebilirlik testi geçti. İmzalı APK / AAB, 16 KB hizalama, sekiz 64-bit yerel kitaplık ve soğuk açılış doğrulandı. Kullanıcının seçimi APK paylaşımıdır; Google Play'e yükleme yapılmaz.
- iOS rehber build 64 (`11fd695`): Xcode Cloud Test / Archive / TestFlight iç dağıtımı başarılı. 121 Swift testi ve 48 arayüz senaryosunun her iki zorunlu hedefte çalıştırılması geçti (iPhone 17 + iPad Air 11-inch M3, 96 cihaz çalıştırması). Rehberin altı senaryosu buna dahildir. Önceki denemedeki tembel menü satırı ve açık arama klavyesi sorunları düzeltilmiştir; başarısız build 63 test grubuna dağıtılmadı.
- Kullanıcının ek isteğiyle teknisyen notu güvenli tekrar denemesi `b4bc03f`, rehber dalına `45a9fae` ile birleştirildi. Canlı backend bu sürümle eşleşir; yeniden backend yayını gerekmemiştir. Birleşik iOS kaynağının ayrıca yeni Cloud build'inde doğrulanması gerekir. Android kodu bu birleştirmede değişmedi; mevcut APK aynı Android kaynağını içerir.
- Bu kontroller gerçek cihazdaki giriş, bildirim, StoreKit veya aralıklı operatör bağlantı hatalarının uçtan uca doğrulanması değildir. Rehber/test fixture'ları gerçek müşteri veya ödeme verisiyle işlem yapmaz.

Birleşik build 65'in iPhone büyük-yazı/arama senaryosu `guide.next` erişim kontrolünde başarısız oldu; aynı iPad senaryosu geçti. Bu build dağıtım kapısını geçemez. Sonraki kaynakta iOS dersleri arama üzerindeki sheet yerine mevcut NavigationStack içinde açılır, seçimde klavye odağı bırakılır ve kartın tamamı açık dokunma alanıdır. Testler sabit alt düğmeyi beklerken ekrana kaydırma hareketi göndermez; üç bağımsız soğuk açılışta arama → ders → Devam, kapalı klavye ve gerçek ekran sınırları denetlenir. Başarısızlıkta ekran görüntüsü ve erişilebilirlik ağacı fixture verisiyle saklanır. Zorunlu iPhone/iPad kapıları aynen korunur.

Build 66'da iPhone'un aynı büyük-yazı/arama senaryosu bu kez konu kartı bulunamadığı için başarısız oldu; diğer beş rehber senaryosu geçti. Sonraki kaynak aramayı üstte sabit, FocusState ile yönetilen yerel bir metin alanında sunar. Arama sırasında uzun tanıtım/ilerleme/kategori alanı sonuçların önünde yer kaplamaz; aktif filtreler varsa açık temizleme eylemi korunur. Doğrudan başlık eşleşmeleri diğer ders metinlerindeki eşleşmelerin önüne alınır. Test gerçek katalog kaydırıcısını kullanır, üç soğuk açılışı ve klavye açık senaryoyu korur; ayrıca aramayı temizleyip Hızlı Başlangıç'a dönüşü denetler. Sonraki native Cloud doğrulaması tamamlanmadan bu kaynak başarılı veya dağıtılmış sayılmaz.

**Sonuç — build 67 (`4d7783a`, 17 Ekim 2026):** Xcode Cloud Test / Archive / TestFlight iç dağıtımı başarılı. 129 Swift testi ve üç iş saat dilimi kapısı geçti. 48 arayüz senaryosunun tümü iPhone 17 ve iPad Air 11-inch (M3) üzerinde geçti; başarısız veya atlanan arayüz senaryosu yok. Önceki denemelerde başarısız olan büyük yazı/arama testinin her iki cihazdaki Passed sonucu ayrıca doğrulandı. TestFlight işleme tamamlandı; build 67, mevcut Pusula Internal Testers grubuna (5 kişi) dağıtıldı ve Türkçe test notları kaydedildi. Süre 30 dakika, Cloud hesaplama kullanımı 59 dakikadır. Build 65 ve 66'nın iç dağıtımı çalışmadı; build 54 / App Review değiştirilmedi.

Cloud günlüklerinde engelleyici olmayan SwiftUI `Invalid frame dimension (negative or non-finite)` uyarıları görüldü. Büyük yazı testindeki görünürlük, dokunulabilirlik, pozitif yükseklik ve ekran sınırı kontrolleri üç bağımsız açılışta geçmiştir; uyarı bir test başarısızlığı olarak gizlenmemiş veya kapı atlanmamıştır. Gerçek cihaz pilotu yine yapılmalıdır.

## Gerçek cihaz pilotu

1. Yeni test sürümünü yükleyin; yönetici hesabında Hesap → Uygulama Rehberi'ni açın. Teknisyen hesabında aynı giriş Profil/Hesap menüsündedir.
2. Hızlı Başlangıç'ta Devam ve Geri'yi deneyin. Dersi kapatıp yeniden açınca kaldığınız adım korunmalı; Bitir sonrası tamamlandı işareti görünmelidir.
3. `iskonto` arayın. Klavye açıkken konuya dokunun; ders açıldığında klavye kapanmalı, Devam düğmesi görünür ve kullanılabilir olmalıdır. Büyük sistem yazı boyutuyla tekrarlayın; aramayı temizleyince kategoriler geri gelmelidir.
4. Yönetici ve teknisyen hesaplarında role uygun konuları karşılaştırın. Başka hesaba geçmek önceki hesabın ilerlemesini o hesaba taşımamalıdır.
5. Rehberi internet kapalıyken deneyin. Rehberde gezinmek gerçek kayıt, ödeme veya mesaj oluşturmamalıdır.
6. Ayrı olarak yalnızca demo iş emrinde teknisyen notu ekleyin. Belirsiz bağlantı sonucunda aynı notu değiştirmeden yeniden deneyin; tek onaylı not görünmelidir. Bu, operatör kaynaklı aralıklı bağlantı sorununun kökten çözüldüğü anlamına gelmez.
