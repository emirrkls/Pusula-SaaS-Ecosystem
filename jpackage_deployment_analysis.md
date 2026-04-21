# Pusula Desktop - .EXE Dağıtımı ve Logo (İkon) Problemi Analizi

Bu raporda, kullanıcıların bilgisayarlarına hiçbir Java kurmadan uygulamayı tek tıkla yükleyebileceği yapıyı (Native Installer) ve görev çubuğundaki (Taskbar) logonun neden Java kahve fincanına dönüştüğünün teknik nedenlerini inceliyoruz.

## 1. Logo Neden Java'nın Kendi Logosuna Dönüşüyor?

Siz uygulamanın içinde `stage.getIcons().add(new Image("pusula_logo.png"))` diyerek sol üstteki pencere ikonunu değiştirmiş durumdasınız. İlk açılışta Windows bu ikonu görev çubuğuna da yansıtır. Ancak bir süre sonra Windows arka planda çalışan gerçek sürece (Process) bakar. 

Uygulamayı bir `.jar` dosyası olarak veya kısıtlı bir başlatıcı (batch script) ile çalıştırdığınızda, Windows Görev Yöneticisinde çalışan asıl .exe dosyası `javaw.exe`'nin ta kendisidir. Windows, uygulamanın kendine ait bir `.exe`'si olmadığını fark ettiğinde (örneğin görev çubuğuna sabitlemeye kalktığınızda veya Windows Explorer arayüzü güncellediğinde) gerçek çalıştırıcının ikonunu, yani meşhur **Java Kahve Fincanını** geri getirir.

Bunu çözmenin **tek net yolu**, JavaFX uygulamasını tıpkı C++ veya C# uygulamaları gibi gerçek bir `.exe` (PE executable) dosyası içine gömmek ve bu `.exe` dosyasına kendi ikonunuzu (.ico) çakmaktır.

---

## 2. Kullanıcıların Java Kurmadan Uygulamayı Çalıştırması (.EXE Dağıtım Seçenekleri)

Bugünlerde hiçbir modern profesyonel Java masaüstü masaüstü uygulaması müşteriden "Lütfen bilgisayarınıza Java 21 yükleyin" diye talepte bulunmaz. Müşteri sadece bir `PusulaSetup.exe` (veya `.msi`) indirip Next -> Next diyerek kurulumu yapar.

Bunu yapmak için piyasadaki **En İyi ve Ücretsiz** yöntem JDK'nin kalbinde yer alan **`jpackage`** aracıdır.

### JPackage (%100 Native ve Gömülü Java) Nasıl Çalışır?
`jpackage`, uygulamanızın `.jar` dosyalarını ve ihtiyaç duyduğu tüm kütüphaneleri alır. Daha da önemlisi, bilgisayarınızdaki (geliştirici bilgisayarı) Java 21'in sadece sizin programınızın ihtiyaç duyduğu çekirdek parçalarını "budayıp" keserek uygulamanın içine gizlice gömer (Buna *Custom JRE* denir).

**Sonuç:** Ortaya örneğin 80 MB büyüklüğünde bir `PusulaServis-1.0.msi` kurulum dosyası çıkar. Kullanıcı bunu çift tıklayıp kurduğunda Program Files'a `.exe` olarak kurulur ve başlat menüsüne kendi logonuzla eklenir.

### Kurulumu ve Kullanımı İçin Tam Yol Haritası:

`pom.xml` dosyamıza baktığımızda zaten projenizin dışa bağımlılıkları `copy-dependencies` ile çıkarıp bir `.jar` oluşturulmaya mükemmel şekilde hazırlandığını görüyorum.

Şu adımları takip ederek projeden EXE inşa edeceğiz:
1. **İkon Dosyası Hazırlama:** Elinizdeki `.png` logoyu, Windows'un okuyabilmesi için bir `.ico` dosyasına çevireceğiz (örn: `pusula.ico`).
2. **Maven ile Derleme:** `mvn clean package` komutuyla her zamanki gibi `target/` klasöründe dosyalarımızı tazeleyeceğiz.
3. **Jpackage Komutunu Çalıştırma:** Sizin komut satırınızdan şu kod çalıştırılacak:
   ```bash
   jpackage --type msi --name "Pusula Servis Yönetimi" --app-version 1.0.1 --vendor "Pusula" --icon pusula.ico --dest target/installer --input target/lib --main-jar backend-0.0.1-SNAPSHOT.jar --main-class com.pusula.desktop.Launcher --win-shortcut --win-menu
   ```
4. **Çıktı:** `target/installer/` klasörünün içinde nur topu gibi bir `Pusula Servis Yönetimi-1.0.1.msi` (Kurulum sihirbazı dosyası) oluşacak.

Müşteriler bu kurulum dosyasını indirdiğinde kurulum yapacaklar ve başlat menülerine `Pusula Servis Yönetimi` eklenecek. Uygulamanın kendi gömülü `.exe`'si olduğu için kahve fincanı bir daha asla görünmeyecek.

Eğer istersen proje klasörüne bu `jpackage` işlemlerini otomatikleştirecek pratik bir `build_exe.bat` scripti yazabilir veya maven pluginini buna göre güncelleyebilirim?
