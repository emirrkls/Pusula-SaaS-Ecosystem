# Servis Ağı — yayın öncesi inceleme

Dal: `codex/subdealer-service-network`. Başlangıç: `88e603f`.

Bu geliştirme üretimden ayrıdır. Main birleştirmesi, üretim veritabanı migration'ı, masaüstü dağıtımı ve TestFlight yüklemesi kullanıcı onayı olmadan yapılmaz.

## Bu sürümün kapsamı

Ana firma, ayrı işletmelerden oluşan tek kademeli bir servis ağı yönetebilir. Yeni alt servis ve yöneticisini açabilir veya mevcut işletmeye işletme koduyla davet gönderebilir. Mevcut işletmenin kendi yöneticisi kabul etmeden bağlantı etkinleşmez.

Ana firma, aktif alt servise müşteri adı/telefonu/adresi, iş başlığı, randevu başlangıç-bitişi ve özel iş talimatı gönderir. Alt servis işi reddedebilir veya kendi müşterilerinden birine bağlayarak kabul edebilir. Müşteri seçilmezse yalnızca bu işin paylaşılan bilgilerinden alt serviste yeni müşteri oluşturulur. Teknisyen kabul sırasında veya daha sonra atanabilir.

Kabul, **alt servisin kendi işletmesinde normal servis fişi** oluşturur. Mevcut parça, dış gider, teknisyen notu, görsel, randevu değişikliği ve kapama akışları bu yerel fiş üzerinde çalışır. Ağ talimatı müşteri PDF'ine girmeyen teknisyene özel alana aktarılır.

Ana firma; ilk/güncel randevuyu, operasyon durumunu, tamamlanma tarihini ve iki firmanın açıkça paylaştığı notları görür. Durum/randevu değişiklikleri iş geçmişine ve ana firma yönetici bildirimlerine yansır. Finansal değerler, özel teknisyen notları, stok ve tüm müşteri listesi ana firmaya açılmaz. Görseller bu fazda firmalar arasında otomatik paylaşılmaz.

Kabul bekleyen iş gönderici tarafından gerekçeyle geri çekilebilir. Kabul edilmiş işi gönderen firma doğrudan iptal edemez veya değiştiremez; alt servis normal fiş yetkileriyle yönetir. Bekleyen/açık işler bitmeden bağlantı kapatılamaz. Bağlantı kapanınca geçmiş ağ işleri iki eski tarafın erişiminde kalır; başka bir ana firmaya taşınmaz. Eski iş sonradan yeniden açılırsa eski işin tarafları aynı kalır.

## Yetki ve veri sınırları

| İşlem / kayıt | Ana firma yöneticisi | İlgili alt servis yöneticisi | Teknisyen / başka firma |
| --- | --- | --- | --- |
| Ağ politikası ve limit tanımlama | Hayır; yalnızca SUPER_ADMIN | Hayır | Hayır |
| Alt servis oluşturma/davet | Ağ yetkisi ve kapasite varsa | Kendi ikinci kademe ağını açamaz | Hayır |
| Mevcut işletme davetini kabul | Hayır | Evet | Hayır |
| İş gönderme / bekleyen işi geri çekme | Evet | Hayır | Hayır |
| İşi kabul/ret, müşteri/teknisyen seçimi | Hayır | Evet | Hayır |
| Ortak iş geçmişi ve not | Evet | Evet | Hayır |
| Kabul edilen yerel fiş | Yalnızca sınırlı operasyon özeti | Mevcut işletme yetkileri | Sadece atanmış teknisyenin mevcut fiş yetkileri |
| Alt servisin özel finansı/stoğu/müşterileri | Hayır | Mevcut yetkiler | Mevcut tenant/teknisyen sınırları |

SUPER_ADMIN genel yönetici rolü, ağ işlerinde sınırsız tenant dolaşımı sağlamaz. Politikalar dışında operasyon erişimi kaydın tarafı olmayı gerektirir. Salt okunur/askıya alınmış/süresi dolmuş işletmelerin ağ yazma işlemleri engellenir; geçmişi okumak mümkündür. Mevcut salt okunur impersonation koruması korunur.

## Finans, plan ve limit kararı

- Ağ gönderimi bir satış, tahsilat, gider veya ana firmada ikinci servis fişi oluşturmaz. Böylece kabul tek başına mükerrer gelir doğurmaz.
- Alt servis kendi fişini ücretli kapatırsa **kendi finansına** yansır. Bu sürüm şirketler arası hakediş, komisyon, ana firma faturası veya merkezi tahsilat hesaplamaz. Garanti işlerinde mevcut garantili/0 TL kapama kullanılabilir.
- Var olan ana firma fişini alt servise devretme veya iki tarafta finansal fiş eşleme bu sürümde yoktur; ağ ekranından yeni operasyon gönderilir.
- Usta/Patron/Çırak limitleri ve fiyatları değiştirilmez. `service_network_policies` tek merkezi kaynak olur: `enabled`, `maxMembers`, `maxMonthlyOrders`. Varsayılan herkes için kapalıdır. Ağ yetkisini SUPER_ADMIN masaüstü ekranından veya yetkili API'den tanımlar.
- Bekleyen davetler alt servis limitine dahildir. Reddedilen/kapanan bağlantılar kapasiteden çıkar. Gönderilen işler, reddedilse/geri çekilse de gönderildiği ayın kotasında kalır; tekrar denemeler ikinci kez sayılmaz. Kota ayı Türkiye saatine göre hesaplanır; geçmiş/gelecek randevu tarihi kotayı başka aya taşımaz.
- Yeni oluşturulan alt servis bağımsız Çırak/14 günlük deneme hesabıdır. Ana firmanın Patron paketi alt servise otomatik kopyalanmaz. 400 servislik ticari sözleşmede deneme sonrası lisanslama/merkezi ödeme kararı verilmelidir.

## Masaüstü ve iOS

- Servis Ağı: gelen işler, gönderilen işler, alt servisler/davetler.
- İşlerde isim, başlık, firma ve iş numarası araması; durum ve ilk randevu tarihi filtresi; 50 kayıtlık sayfalama.
- Alt servis oluşturma/davet, iş gönderme, kabul/ret, paylaşılan not, geçmiş ve yerel fişe geçiş.
- Yönetici bildirimi, kayıt türü ve numarasıyla doğrudan ilgili ağ işine veya davete gider. Ana firmanın gönderdiği işten geri dönüldüğünde gönderilen işler açılır; davet bildirimi genel gelen işler listesine düşmez. Kayıt API'leri iki taraf dışındaki işletmelerin erişimini engeller. Push iletim altyapısı mevcut APNs yapılandırmasını kullanır; çevrimiçi iletim ayrı cihaz kabul testi gerektirir.
- Masaüstünde sabit bir küçük modal yerine kaydırılabilen sayfa formları, üstte kalan aksiyonlar ve satıra geçen araç çubukları. iOS'ta Form/List ve sistem gezinme çubuğu aksiyonları.
- Android ve web için yeni ağ yönetim ekranı bu faza dahil değildir; kabul edilen normal fişlerin mevcut API akışı korunur.

## Güvenilirlik ve 400 servis ölçeği

Gönderimde firma kapsamlı benzersiz `requestKey`, kabulde benzersiz yerel fiş bağlantısı ve transaction kullanılır. Firma kilitleri ID sırasıyla alınır. Kabul sırasında kilit sonrası entity yenilenir; aynı anda gelen iki istek tek müşteri/fiş oluşturur. Çocuk tenant'ın müşteri, teknisyen ve aylık fiş kotaları doğrulanır. Yerel fiş oluşturulamazsa müşteri, kabul ve geçmiş işlemi geri alınır.

Alt servis hesabı oluşturma da firma kapsamlı kalıcı `requestKey` ile korunur. Aynı formdaki ağ hatası sonrası tekrar ve eşzamanlı istekler aynı işletme/kullanıcı bilgilerini döndürür, ikinci hesap veya kota tüketimi oluşturmaz. Tekrar denemesi yönetici şifresini değiştirmez; şifre veya şifre parmak izi işlem makbuzuna kaydedilmez. Aynı anahtarla farklı hesap bilgisi gönderilmesi reddedilir. Form kapatılıp yeni form açıldığında yeni anahtar üretilir; uygulama yeniden başlatıldıysa yeniden oluşturmadan önce alt servis listesi kontrol edilmelidir.

V35; 4 yeni tablo, ilişki/tekillik/durum/tarih kontrolleri ve firma-tarih/durum/geçmiş indeksleri ekler. Aktif/bekleyen bir alt servisin ikinci ana firmaya bağlanmasını PostgreSQL partial unique index de engeller. Liste 50 kayıtla sınırlıdır; fiş durumları sayfa başına toplu okunur. Her ana firma altındaki mutasyonlar kota tutarlılığı için kısa süreli sıraya girer; firmalar birbirini global kilitlemez.

V36, hesap oluşturma makbuzunun anahtar/yönetici adı/kullanıcı adı alanlarını ve firma-anahtar tekilliğini ekler. V35 değiştirilmez; önceden oluşmuş davetler ve bağlantılar nullable alanlarla uyumlu kalır.

400 servislik sentetik dizin testi yapıldı. Bu, 400 teknisyenin aynı anda yoğun trafik/görsel yüklemesi için yük testi veya SLA garantisi **değildir**. Büyük ağ açılışından önce staging ortamında gerçekçi eşzamanlı iş kabul/kapama, bildirim fan-out, DB bağlantı havuzu ve disk/medya yük testleri gerekir. Başlangıç önerisi: 3–5 servislik pilot, ölçüm sonrası 25–50, sonrasında 400.

## Doğrulama

- Backend tam `mvn verify`: 219 test, 0 hata, 1 atlama. Atlanan PostgreSQL migration testi ayrı PostgreSQL koşusunda çalıştırıldı.
- Servis Ağı entegrasyonları: 25 test, H2 ve PostgreSQL 17'de başarılı. Tenant izolasyonu, rol, davet onayı, döngü/ikinci kademe engeli, şifre hash'i, limitler, tarih/arama, transaction geri alma, özel veri sızıntısı, 400 kayıt, eşzamanlı kabul, hesap oluşturma tekrarı ve kota yarışı dahil.
- Ek 2 gerçek yaşam döngüsü testi, `ServiceTicketService` oluşturma/kapama metodunu taklit etmeden kabul → teknisyen güncellemesi → kısmi tahsilat/cari → ağ geçmişi/bildirim akışını ve kapama hatasında geri almayı H2/PostgreSQL üzerinde sınar. Ana firmada finansal kayıt oluşmadığı, tekrar kapamanın ikinci cari yaratmadığı doğrulanır. Dış mesajlar ve günlük finans özeti uzlaştırma çağrısı bu testte taklit edilen sınırlardır; cari bakiye/hareketleri gerçek servislerle yazılır.
- Gerçek V35 + V36 SQL migration: PostgreSQL 17'de tekillik, partial index, kabul-fiş bağlantısı ve randevu kontrolleri başarılı (toplam 28 PostgreSQL testi). İkinci ana firma kontrolünde genel SQL hatası yerine `23505` ve özellikle `uq_network_child_live` doğrulanır; test verisi ID sequence'ini atlamaz.
- Masaüstü tam `mvn verify` (`NETWORK_UI_TEST=true`): 30 test başarılı. Üç JavaFX testi localhost sentetik verilerle 900×600 liste/form ve görünür aksiyonları, iş/davet bildiriminin tam kayda gidişini ve doğru listeye dönüşü sınar; canlı API kullanmaz. Liste/form render çıktıları görsel olarak da incelendi.
- iOS imzasız simulator derlemesi için ayrı GitHub Actions doğrulaması eklendi. Archive, mağaza yüklemesi veya TestFlight dağıtım adımı içermez. Son CI sonucu yayın öncesi ayrıca kontrol edilmelidir.
- Üretim verisi testlere alınmadı. Yerel test DB/log/görselleri gitignore altında; kimlik bilgileri commit'e dahil değil.

## Sonraki geliştirme alanları

1. Merkezi lisanslama ve ağ içi hakediş/komisyon/faturalama modelini ticari sözleşmeye göre ayrı tanımlamak.
2. Mevcut ana firma servis fişinden devir, alt servis değişikliği ve çift tarafta mutabakatlı iptal/geri çağırma.
3. İzinli görsel/belge paylaşımı, ana firma kalite onayı, SLA süreleri ve gecikme uyarıları.
4. Bölge/uzmanlık/yetkinlik filtreleri, kapasiteye göre dağıtım, toplu gönderim ve performans panosu.
5. Çok kademeli organizasyon ve bir servisin birden fazla marka ağına katılması; mevcut tek-parent sınırını bilinçli olarak yeniden modellemek.
6. Çok büyük müşteri listelerinde mevcut müşteri seçim API'lerini sunucu tarafı aramalı/sayfalı hale getirmek; mevcut ekranlar sonuçları ilk 40 eşleşmeyle sınırlar ama müşteri API'si tüm kendi tenant listesini getirir.
7. Hesap oluşturma sonrası güvenli tek kullanımlık davet/şifre belirleme bağlantısı; bugün yönetici ilk şifreyi belirleyip güvenli kanaldan paylaşır. Aynı formdan tekrar korumalıdır; uygulama kapanması sonrasında makbuz kurtarma/davet akışı ileride geliştirilebilir.
8. Gerçek iPhone/iPad cihazında kabul/gezinme/push ve erişilebilirlik kabul testi; 400 servis seviyesinde staging yük testi.

## Onay sonrası kontrollü yayın planı

1. Branch/CI ve uygulama kabul sonuçlarını kontrol et; ana dal ilerlediyse çakışmaları tekrar test et.
2. Üretim PostgreSQL'in tam yedeğini al ve ayrı restore ile doğrula. Önce backend V35 + V36 migration'larını ve uygulamayı yayınla; hiçbir işletmenin ağ yetkisini topluca açma.
3. Bir pilot ana firma için SUPER_ADMIN üzerinden kapasite tanımla. 3–5 test/izinli alt servisle tenant izolasyonu, davet, kabul, fiş kapama, finansın iki kez yazılmadığı ve bildirimleri kontrol et.
4. Masaüstü sürüm/installer metadatasını yükseltip dağıt; onaylı iOS release hattını ayrı tetikle. Geliştirme dalının CI hattı TestFlight dağıtmaz.
5. Hata halinde önce pilot ağ politikasını devre dışı bırak (yeni ağ oluşturma/gönderimini durdurur; kabul edilmiş işlerin takibi sürer). Gerekirse önceki backend/client sürümüne dön; ek tabloları koru. Veri oluştuktan sonra tabloları silmek veya otomatik down-migration yapmak güvenli rollback değildir. Tam DB restore yalnızca sonradan oluşan gerçek işlemler değerlendirilerek bakım penceresinde uygulanmalıdır.

**Üretime geçiş için kullanıcı onayı beklenir.**
