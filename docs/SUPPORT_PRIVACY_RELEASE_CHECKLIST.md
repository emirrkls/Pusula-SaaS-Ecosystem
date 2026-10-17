# Destek merkezi gizlilik kontrolü

Bu kontrol listesi yeni destek istemci sürümünün yayın hazırlığıdır. İncelemedeki build 54'ün başvurusu veya yayımlanmış App Store beyanı bu çalışma sırasında değiştirilmedi. Kullanıcı onayıyla 17 Ekim 2026'da web gizlilik sayfası destek mesajı, isteğe bağlı ekler, korumalı taslak, saklama süresi ve hesap silmeyi açıklayacak şekilde yayımlandı; backend ve yönetim paneli canlıdır. Bu, aşağıdaki mobil mağaza beyanı kontrolünün yerine geçmez.

## Yeni özelliğin veri eşlemesi

| İşlenen veri | Apple kategorisi | Amaç ve ilişki |
|---|---|---|
| Talep konusu, mesajlar ve destek yanıtları | Customer Support | App Functionality, hesaba bağlı, takip amacı yok |
| Kullanıcının seçerek gönderdiği ekran görüntüsü | Photos or Videos | App Functionality, hesaba bağlı, takip amacı yok |
| İsteğe bağlı maskelenmiş bağlantı raporları | Other Diagnostic Data; süre ölçümleri için Performance Data kapsamını da karşılaştırın | App Functionality, hesaba bağlı, takip amacı yok |
| Talep sahibi kullanıcı kimliği ve işletme ilişkisi | User ID | Mevcut User ID beyanıyla eşleştirin; yeni reklam kimliği üretilmez |

Kaynak: [Apple App Privacy Details](https://developer.apple.com/app-store/app-privacy-details/), 17 Ekim 2026. Apple'ın isteğe bağlı geri bildirim istisnası tüm koşullar sağlandığında uygulanır. Bu sürümde istisnaya dayanmak yerine destek içeriğini açıkça beyan etmek tercih edilir. Bu tablo yalnızca destek modülüdür; uygulamanın önceki tüm veri kategorilerini değiştirmez veya kaldırmaz.

## Yayın kapıları

- Mevcut App Privacy yanıtlarını okuyup yukarıdaki kategorilerle karşılaştırın. Aynı tür zaten beyanlıysa aynı kategoriyi tekrar oluşturmayın.
- Nihai beyanı ürün sahibine gösterip onay almadan App Store'da yayımlamayın; build 54'ün mevcut başvurusuna müdahale etmeyin.
- Ekran görüntülerinin otomatik anonimleştirilmediği uyarısı istemcide görünür olmalıdır. Ekler isteğe bağlı olmalıdır.
- Windows taslağı DPAPI, Android taslağı şifreli tercihler, iOS taslağı korumalı/yedek dışı dosyalarda tutulmalıdır; hesaplar birbirine erişememelidir.
- Kullanıcı silmede destek konuşmaları, ekler ve bekleyen e-posta kayıtları temizlenmelidir. PostgreSQL testi fiziksel satır temizliğini doğrular; yedek saklama ayrıca değerlendirilir.
- Destek dışı işletme/finans kayıtları için mevcut hesap silme açıklamasını değiştirmeyin.
- SMTP e-postası yalnızca talep referansı taşımalıdır; mesaj gövdesi ve ekler e-posta kuyruğuna kopyalanmamalıdır.
- Yeni kurumsal adresin gelen ve giden teslimat testleri bitmeden mevcut doğrulanmış Gmail yedeğini kaldırmayın.
