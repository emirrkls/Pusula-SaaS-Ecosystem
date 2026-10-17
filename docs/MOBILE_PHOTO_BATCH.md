# İş emrine toplu görsel yükleme

17 Ekim 2026 sürüm adayı; iOS ve Android. Masaüstü ve App Review'daki build 54 değiştirilmez.

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
