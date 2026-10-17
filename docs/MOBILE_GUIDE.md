# Mobil uygulama rehberi

iOS ve Android kapsamındadır; masaüstü turu değiştirilmez. İncelemedeki build 54 ve mağaza başvurusu bu çalışma tarafından değiştirilmez.

## İçerik ve kullanım

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
