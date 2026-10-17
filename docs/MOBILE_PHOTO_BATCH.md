# İş emrine toplu görsel yükleme

17 Ekim 2026 sürüm adayı; iOS ve Android. Masaüstü, mağazada seçili build 54 ve manuel yayın ayarı değiştirilmez.

- Galeriden en fazla 10 görsel seçilir; kamera tek fotoğraf akışını korur.
- Seçim anındaki kategori/not tüm gruba sabitlenir. Yeni seçim, bekleyen bir grubu değiştiremez.
- Görseller sırayla hazırlanır ve yüklenir; büyük iOS görselleri tam boyut açılmadan 2048 piksele küçültülür. Android mevcut sınırlı JPEG hazırlayıcısını IO iş parçacığında kullanır.
- Her görselin ayrı UUID anahtarı vardır. Her iki istemci de mevcut `/api/reliable-mutations/tickets/{id}/photos` yoluna gider. Sunucu aynı anahtarın fiş, kategori, not ve dosya özetiyle eşleşmesini denetler.
- Başarılı görseller tekrar gönderilmez. Yanıtı alınamayan görselin anahtarı ve hazırlanmış baytları tekrar denemeye kadar korunur. Okunamayan bir kaynak diğer görselleri durdurmaz.
- Yükleme sırasında ayrılma/yeniden gönderme kapalıdır. Bekleyen grup varken ayrılmak açık onay gerektirir; kullanıcıya yanıtı gelmeyen kaydın sunucuda bulunabileceği hatırlatılır.
- Bekleyen grup ekran/ViewModel ömründe tutulur; uygulama kapatılırsa arka planda veya kalıcı kuyrukta kendiliğinden gönderilmez. Böyle bir durumda yeniden seçimden önce liste kontrol edilmelidir.
- Hesap değişikliği kuyruğu durdurur; eski yanıtlar uygulanmaz. Token isteğe sabitlenir ve başka hesabın token'ıyla değiştirilemez. Rehberin salt okunur taşıma kapısı korunur.

## Doğrulama

Swift paketinde üç toplu yükleme durum testi; iPhone/iPad için gerçek görsel ekranında sentetik kayıp yanıt, kısmi başarı, büyük yazı ve ayrılma onayı testleri eklendi. Bu çevrimdışı testler gerçek galeriyi/Apple Photos iznini sınamaz ve Release'e alınmaz.

Android birim testleri sınırlandırma, sabit anahtar/bayt ve başarısız kaynak davranışını denetler. Cihaz testleri gerçek ViewModel ve Repository üzerinden sentetik API sınırında kısmi başarı, kayıp yanıt, okunamayan kaynak ve hesap değişimini çalıştırır. Üretim API'sine test kaydı yazılmaz.

Kaynak sözleşmeleri: `node --test scripts/test-photo-batch-contract.cjs`. Son dağıtım sonucu, test sayıları ve kaynak SHA'sı yerel sürüm raporunda ayrıca kaydedilir; önceki build 67'nin sonuçları yeni build için kanıt değildir.

Backend kodu bu istemci değişikliğinde değişmez. Canlı JAR'ın önceki doğrulanmış güvenilir yükleme sürümüyle SHA-256 eşleşmesi ve destek iletişim uç noktası salt okunur doğrulanır; gereksiz yeniden başlatma/deploy yapılmaz.

## Son doğrulama / dağıtım durumu

- Android 1.3.8 (32): 25 birim + 12 cihaz testi, imzalı APK/AAB, 16 KB ZIP/ELF/RELRO kontrolleri ve soğuk açılış başarılı. Kullanıcının seçimi APK paylaşımıdır; Google Play'e yayın yapılmaz.
- Backend: 23 denetleyici + 20 izole H2 entegrasyon testi, 0 hata / 0 atlanan. Dört yeni multipart denetleyici testi; içerik/fiş/kategori/not özeti, tekrar denemede eski fotoğrafın dönmesi, yetki iptali ve zorunlu anahtarı sınar. Bu testler canlı veriye erişmez; üretim sunucu kodu değişmez.
- Tam backend seçkisi de yeniden çalıştırıldı: 450 keşfedilen, 439 geçen, 0 hata, 11 koşullu atlanan (ayrı PostgreSQL test veritabanları yapılandırılmadığı için). Bu çalıştırma PostgreSQL'e özgü migration veya kilitleme davranışının yeni doğrulaması değildir.
- Xcode Cloud build 69, kaynak `4bf7b52`: 139 Swift testi, test derlemesi, Archive ve processing başarılı. Çoklu görselin iki native senaryosu da iki hedefte geçti. Tam arayüz kapısı diğer rehber/destek senaryolarından dolayı iPhone 48/51 ve iPad 49/51 ile başarısız oldu; iç dağıtım çalışmadı. Build 68'de kısmi yükleme senaryosu iki hedefte geçti, çıkış uyarısı testi başarısız oldu; kapı atlanmadı ve gruba dağıtılmadı. Build 69 kaynağı çıkış uyarısını açık iptal düğmeli ortak alert olarak düzeltir. Sonraki Cloud kapısı ayrıca geçmelidir.
- Gerçek cihazda Photos izinleri, 1–10 seçim, kamera, yönlendirme ve Wi-Fi/mobil veri geçişi ayrıca pilotta denenmelidir. Simülatör başarıları aralıklı operatör/TLS sorununun giderildiği anlamına gelmez.

## Gerçek cihaz pilotu

Demo hesabında mevcut bir servis fişinin Görseller ekranını kullanın. Önce tek görsel, sonra bir grupta 2–10 görsel seçin; her birinin bir kez listelendiğini ve ortak kategori/notu taşıdığını kontrol edin. Kamera ile tek fotoğraf yolunu ayrıca deneyin. İzin reddedildiğinde uygulama çökmemeli veya sahte başarı göstermemelidir.

Bekleyen grup varken yeniden seçim yapılmamalı; ayrılma uyarısında **Burada Kal** kuyruğu korumalıdır. Gerçek bir kısmi hata olursa başarılı fotoğrafları not edin ve **bekleyenleri tekrar dene** eylemini kullanın; tamamlananlar yeniden yüklenmemelidir. Bağlantı sonucunun belirsiz olduğu anda ekranı kapatırsanız yeniden seçimden önce sunucu listesini kontrol edin. Ekran ömrü dışındaki kalıcı/arka plan kuyruğu bu sürümün kapsamı değildir.

Yükleme sırasında hesap değişirse eski kuyruğun yeni hesaba taşınmadığını doğrulayın. Ağ güzergâhı testlerini aynı telefon/aynı demo fişiyle Wi-Fi ve mobil veri üzerinde ayrı yapın; raporları saat ve ağ türüyle etiketleyin. Bu pilot için incelemedeki/mağazada seçili build 54 değiştirilmez.
