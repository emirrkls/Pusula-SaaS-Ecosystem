# Pusula Masaüstü Uygulaması Otomatik Güncelleme (Auto-Update) Analizi

Bu raporda, dağıtılmış masaüstü (Java/JavaFX) uygulamalarınızı nasıl otomatik olarak güncelleyebileceğinizi ve profesyonel "Yeni Güncelleme Mevcut" sistemlerinin arka planda nasıl çalıştığını detaylandırıyoruz.

## 1. Profesyonel Güncelleme Sistemleri Nasıl Çalışır?

Profesyonel masaüstü uygulamalarındaki (Spotify, Discord, VS Code vb.) güncelleme mekanizması aslında oldukça basit bir **İstemci-Sunucu (Client-Server)** sorgusuna dayanır:

1. **Sürüm Kontrolü (Version Check):** Uygulama her açıldığında (veya arka planda belirli aralıklarla) sunucudaki sabit bir adrese istek atar. Örneğin uygulamanız `GET https://api.pusula.com/v1/version` adresine sorgu yapar.
2. **Kıyaslama:** Sunucu şu formatta bir JSON döner:
   ```json
   {
     "latestVersion": "1.0.5",
     "mandatory": false,
     "releaseNotes": "Arayüz renkleri iyileştirildi, finans butonu düzeltildi.",
     "downloadUrl": "https://pusula.com/downloads/pusula-update-v1.0.5.zip"
   }
   ```
   Uygulama içine gömülü (hardcoded) olan kendi sürümüne bakar (örneğin `"1.0.2"`). Eğer `"1.0.5" > "1.0.2"` ise, arayüzde **"Bekleyen 1 adet güncellemeniz bulunmaktadır"** uyarısı gösterilir.
3. **İndirme ve Değiştirme (Download & Replace):** 
   Kullanıcı "Güncelle" butonuna bastığında:
   - Yeni sürüm dosyaları geçici bir klasöre (`AppData/Temp` vb.) indirilir.
   - Mevcut çalışan uygulama dosyaları, uygulama açıkken silinemez (İşletim sistemi buna izin vermez). 
   - Bu yüzden çalışan program kapanır, arkadan ufak boyutlu bir **"Updater (Güncelleyici)"** programı başlar. Eski dosyaları silip yenilerini kopyalar ve uygulamayı yeni haliyle tekrar başlatır.

> [!WARNING]
> **Şu anki Dağıtılmış Sürümler Hakkında Önemli Not:** 
> İnsanlara _önceden_ verdiğiniz ve şuan bilgisayarlarında çalışan sürümlerin içinde bir "Sürüm Kontrolü" ve "Güncelleyici" mekanizması kodlanmadığı için, bu kullanıcıların **bir sonraki güncellemeyi manuel (elle) indirmeleri gerekecektir**. Ancak onlara vereceğimiz bu _yeni_ (otomatik güncelleyicili) versiyondan sonraki tüm güncellemeler otomatik olacaktır.

---

## 2. Pusula JavaFX Projesi İçin Seçenekler

Java dünyasında masaüstü uygulamalarını auto-update yapmak için birkaç güvenilir yöntem vardır. Seçenekler karmaşıklıktan kolaylığa doğru sıralanmıştır:

### Seçenek A: Update4j veya FXLauncher (Önerilen Modern Yöntem)
Bunlar Java uygulamaları için yazılmış açık kaynaklı güncelleme kütüphaneleridir.
* **Nasıl Çalışır:** Uygulama yerine çok küçük bir `Başlatıcı (Launcher) jar` dosyası dağıtırsınız. Kullanıcı uygulamaya tıkladığında önce Launcher açılır. Web sunucunuzdaki (örn: S3 veya kendi sunucunuz) güncel dosyaları tarar, sadece değişenleri saniyeler içinde indirir ve asıl uygulamayı açar.
* **Avantajı:** Boyutu büyüktür diye tüm programı baştan indirmez, sadece `dashboard.fxml` veya `styles.css` gibi değişen küçük dosyaları indirebilir (Delta update). Arka planda sessiz çalışır.

### Seçenek B: Uygulama İçi Özel (Custom) Rest API Kontrolü ve Yönlendirme (En Basit)
Altyapınızda halihazırda Spring Boot sunucunuz var. Bu sunucuya `/api/desktop/version` diye bir alan ekleriz.
* **Nasıl Çalışır:** Masaüstü uygulamasının `DashboardController`'ı açılır açılmaz asenkron olarak bu API'ye sorar. Yeni sürüm varsa ekranda bir JavaFX Uyarı Kutusu (Alert) veya Bildirim çıkarır: *"Versiyon 1.1 çıktı! Lütfen sitemizden güncelleyin."* Kullanıcı "İndir"e bastığında varsayılan web tarayıcısı (Chrome) açılır ve doğrudan son versiyonun `.exe` (veya zıp) dosyası iner.
* **Avantajı:** Kurulumu ve programlaması en kolay yoldur. Karmaşık dosya değiştirme kilitlenmeleriyle uğraştırmaz.
* **Dezavantajı:** Güncellemeyi arka planda sihirli bir şekilde kurmaz, kullanıcıya yeni setup dosyasını indirtip kurdurtur (Tıpkı Zoom veya Postman'in manuel güncellemeleri gibi).

### Seçenek C: JPackage + Üçüncü Parti Yükleyiciler (Advanced Installer vb.)
Kullanıcılara `.jar` veya bat dosyası vermek yerine, Java 14+ ile gelen `jpackage` kullanarak uygulamayı saf bir Windows `.msi` veya `.exe` kurulum dosyası haline getiririz.
* **Nasıl Çalışır:** JPackage içine bir otomatik güncelleyici gömülebilir veya MSI dosyasına "Update" flag'leri verilebilir.
* **Avantajı:** Son kullanıcı deneyimi tıpki kurumsal uygulamalar gibidir (Denetim Masasına kurulur vs.).

---

## 3. İzlememiz Gereken Yol (Tavsiye Edilen Strateji)

Bu aşamada zaman maliyetini ve bakım kolaylığını düşündüğümüzde **adım adım** ilerlemek en sağlıklısıdır:

**Faz 1 (Hemen Yapılabilecek): Custom API Sürüm Uyarısı**
1. Backend sunucunuza basit bir `version.json` veya endpoint koyalım. (Örn: `{"latest": "1.0.1", "url": "..."}`)
2. Masaüstü uygulamasının içine bir "Sürüm Kontrol Yöneticisi" sınıfı ekleyelim. Açılışta API'nizi kontrol etsin.
3. Yeni sürüm varsa, dashboard'un tepesinde kırmızı bir uyarı (`HBox` banner'ı) çıksın: **"🎉 Bekleyen 1 adet güncellemeniz var. İndirmek için tıklayın."**
4. Kullanıcı tıklayınca linkten son versiyon iner.

**Faz 2 (Gelişmiş Aşamada): Sessiz Otomatik Kurulum (Update4j)**
Uygulama iyice yerleştiğinde Update4j kurarız. Kullanıcı hiç tarayıcı açmadan, program başlarken "Güncelleniyor... %40" barı görür ve 5 saniyede uygulama kendi kendini yenilemiş şekilde açılır (Tıpkı Discord'un açılıştaki siyah ekran yüklemesi gibi).

Eğer onaylarsanız, **Faz 1 (Custom API Kontrolü ve JavaFX Bildirimi)** sistemini hızlıca hemen `Launcher.java` veya `DashboardController` içerisine tasarlayıp size sunabilirim!
