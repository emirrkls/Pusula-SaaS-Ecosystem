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

## Doğrulama kapıları

- Node: katalog senkronizasyonu, kapsam, rol / platform tanımları, gerçek menü bağlantıları, simge eşitliği, salt okunur akış ve fixture güvenliği.
- Swift paket: katalog doğrulama, Türkçe arama, filtreler, indeks / son adım, devam / yeniden oynatma ve bozuk içerik.
- iPhone + iPad: gerçek yönetici Hesap ve teknisyen Profil menüleri, rehberde ilerleme / geri dönüş / devam / bitirme / hesap ayrımı, arama ve erişilebilir büyük yazı, eski ilk tanıtım ve bağlamsal tur.
- Android: aynı politika birim testleri ve gerçek Compose ekranında giriş, adımlar, arama, ilerleme / rol ayrımı ve temizleme. İmzalı Release APK / AAB, 16 KB uyumu ayrıca doğrulanır.

GitHub Actions kullanılmaz. iOS dağıtımı yalnızca gerekli Xcode Cloud testleri başarılı olduğunda ayrı bir TestFlight build'i olarak yapılır. Mağazadaki build seçimi / App Review başvurusu otomatik değiştirilmez.
