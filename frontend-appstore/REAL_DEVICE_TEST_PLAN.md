# Pusula Servis iOS Gercek Cihaz Test Plani

Bu plan, App Store Connect ayarlari tamamlandiktan sonra iPhone ve iPad uzerinde uygulanacak yayin oncesi kabul testlerini tanimlar.

## Test Ortami

- Backend: production veya izole staging VPS
- Uygulama: Release'e yakin Debug/TestFlight build
- Roller: `COMPANY_ADMIN`, `TECHNICIAN`
- Tenantlar: birbirinden bagimsiz A ve B sirketi
- Apple: Sandbox tester hesabi
- Urunler: `com.pusula.usta`, `com.pusula.patron`
- Donanim: kamerali bir iPhone ve destek devam edecekse bir iPad

Gercek musteri verisi kullanmayin. Test kayitlarini `IOS-QA-` on ekiyle olusturun ve test sonunda yalniz bu kayitlari temizleyin.

## Cihaz Matrisi

| Cihaz | Surum | Yon | Tema | Sonuc |
|---|---|---|---|---|
| Guncel iPhone | Guncel iOS | Dikey | Acik | Bekliyor |
| Guncel iPhone | Guncel iOS | Dikey | Koyu | Bekliyor |
| Daha kucuk iPhone | iOS 17+ | Dikey | Acik | Bekliyor |
| iPad | iPadOS 17+ | Dikey | Acik | Bekliyor |
| iPad | iPadOS 17+ | Yatay | Koyu | Bekliyor |

## 1. Kurulum ve Oturum

### IOS-AUTH-01 Temiz kurulum

1. Uygulamayi cihazdan silin ve Xcode/TestFlight ile yeniden kurun.
2. Uygulamayi acin.

Beklenen:

- Pusula Servis ikonu ana ekranda dogru gorunur.
- Uygulama login ekraninda acilir.
- Ilk acilista gereksiz kamera veya fotograf izni istenmez.
- Uygulama acilisinda bos, siyah veya takili ekran olmaz.

### IOS-AUTH-02 Bireysel kayit

1. Yeni ve benzersiz bir e-posta ile hesap olusturun.
2. Eksik alan, gecersiz e-posta ve 6 karakterden kisa sifre deneyin.
3. Gizlilik Politikasi ve Kullanim Kosullari baglantilarini acin.

Beklenen:

- Gecersiz form gonderilemez veya anlasilir hata gosterir.
- Basarili kayit sonrasinda oturum acilir ve CIRAK plani gorunur.
- Legal baglantilar HTTPS uzerinden dogru sayfalari acar.

### IOS-AUTH-03 Oturum geri yukleme

1. Giris yapin.
2. Uygulamayi uygulama degistiriciden tamamen kapatin.
3. Yeniden acin.

Beklenen:

- Token Keychain'den guvenli bicimde geri yuklenir.
- Profil ve abonelik baglami backend'den dogrulanmadan yanlis rol ekrani gosterilmez.
- Gecersiz veya suresi dolmus token login ekranina dondurur.

### IOS-AUTH-04 Kurumsal ve hatali giris

1. Kurumsal sekmede kurum kodu, kullanici adi ve sifreyle girin.
2. Hatali sifre ve hatali kurum kodu deneyin.

Beklenen:

- Dogru hesap dogru tenant ve rolle acilir.
- Hatali bilgiler hassas sunucu ayrintisi gostermeyen anlasilir hata verir.

## 2. Rol ve Tenant Izolasyonu

### IOS-ROLE-01 Teknisyen sinirlari

1. Teknisyen hesabi ile girin.
2. Profil, is listesi ve barkod akisini gezin.

Beklenen:

- Paket satin alma/yukseltme butonu gorunmez.
- Admin finans, ayarlar ve katalog degistirme ekranlari gorunmez.
- Stok okuma ve barkod arama calisir; alis fiyati gorunmez.

### IOS-ROLE-02 Admin sinirlari

1. Sirket yoneticisi ile girin.
2. Musteri, arac, stok, finans ve ayarlar ekranlarini acin.

Beklenen:

- Yetkili islemler erisilebilir.
- B tenantina ait bilinen bir arac veya stok ID'si uygulama/API araciligiyla degistirilemez.
- Yabanci kayit 404 gibi davranir ve B tenant verisi degismez.

### IOS-ROLE-03 Read-only abonelik

1. Backend'de test sirketini read-only duruma alin.
2. Uygulamayi yeniden acin veya abonelik baglamini yenileyin.

Beklenen:

- Read-only banner gorunur.
- Yazma islemleri devre disidir.
- Listeleme ve mevcut kayitlari goruntuleme calisir.

## 3. Musteri ve Arac

### IOS-CUSTOMER-01 Musteri yasam dongusu

1. `IOS-QA-Musteri` adinda musteri olusturun.
2. Telefon, adres ve diger alanlari guncelleyin.
3. Musteriye arac ekleyin, guncelleyin ve silin.
4. Aktif servis fisi bulunan musteri veya araci silmeyi deneyin.

Beklenen:

- Basarili islemler listeye hemen yansir.
- Validation ve conflict hatalari anlasilir mesaj gosterir.
- Request body icindeki sahte ID/companyId tenant sahipligini degistiremez.

## 4. Stok ve Barkod

### IOS-INV-01 Admin CRUD

1. `IOS-QA-Parca` adinda stok olusturun.
2. Adet, kritik seviye, alis/satis fiyati, marka, kategori ve barkod girin.
3. Kaydi duzenleyin ve silin.

Beklenen:

- POST/PUT sonucu listeye dogru yansir; DELETE sonrasi kayit kaybolur.
- Bos parca adi ve negatif sayisal alanlar kaydedilemez.
- Kritik stok rengi ve marj hesabi dogrudur.

### IOS-INV-02 Kamera barkodu

1. Barkod tara butonuna basin.
2. Ilk izin ekraninda reddedin ve yeniden deneyin.
3. Ayarlardan kamera izni verip bilinen barkodu okutun.
4. Bilinmeyen barkod okutun.

Beklenen:

- Red durumunda uygulama cokmez ve izin yonlendirmesi/anlasilir durum gosterir.
- Bilinen barkod dogru stogu bulur.
- Bilinmeyen barkod hata/yeniden tara akisi gosterir.
- Ayni kod art arda algilanarak birden fazla islem olusturmaz.

## 5. Servis Fisi Uctan Uca

### IOS-TICKET-01 Olusturma ve atama

1. Admin olarak test musterisine servis fisi olusturun.
2. Teknisyen atayin ve gerekirse toplu atamayi deneyin.
3. Teknisyen hesabinda listeyi yenileyin.

Beklenen:

- Fis dogru filtrede gorunur.
- Teknisyen yalniz kendi atamalarini gorur.
- Timeline olusturma ve atama olaylarini gosterir.

### IOS-TICKET-02 Saha islemleri

1. Teknisyen olarak durumu devam ediyor yapin.
2. Barkodla ve manuel olarak parca ekleyin.
3. Servis oncesi/sonrasi fotograf yukleyin ve birini silin.
4. Musteri imzasi alin.
5. Tahsilat girin ve isi tamamlayin.

Beklenen:

- Stok miktari dogru azalir ve toplam tutar dogru hesaplanir.
- Fotograf yukleme sirasinda ilerleme/hata durumu gorunur.
- Imza fisle iliskilendirilir.
- Tamamlanan fis duzenlenemez; PDF dogru verileri ve imzayi icerir.

### IOS-TICKET-03 Iptal ve takip

1. Test fisine parca ekleyin ve fisi iptal edin.
2. Tamamlanmis bir fisten takip/garanti kaydi olusturun.

Beklenen:

- Iptalde kullanilan stok geri gelir.
- Takip kaydi dogru musteri ve onceki fis baglantisiyla olusur.
- Timeline iki islemi de gosterir.

### IOS-TICKET-04 Sistem entegrasyonlari

1. Musteri telefonunu arama aksiyonunu acin.
2. Adresi Haritalar'da acin.
3. Servis PDF'ini Paylas menusuyle test hedefe gonderin.

Beklenen:

- Telefon ve Haritalar dogru sistem uygulamasini acar.
- PDF gecici dosyasi paylasilir ve uygulamaya donuste ekran bozulmaz.

## 6. Finans, Teklif ve Ayarlar

### IOS-BUSINESS-01 Finans

1. Gider ekleyin, gunluk ozeti yenileyin ve gunu kapatin.
2. Analiz, cari hesap ve aylik rapor sekmelerini acin.
3. Baglantiyi kesip her sekmeyi yeniden yukleyin.

Beklenen:

- Toplamlar ve grafikler backend ile aynidir.
- Basarisiz istek bos veri gibi gorunmez; hata ve tekrar dene aksiyonu cikar.
- Aylik PDF indirilebilir/paylasilabilir.

### IOS-BUSINESS-02 Teklif

1. Teklif olusturun, guncelleyin ve durumunu degistirin.
2. PDF olusturup paylasin.

Beklenen:

- Tutarlar, musteri ve satirlar dogrudur.
- Feature gate ve read-only kurallari uygulanir.

### IOS-BUSINESS-03 Ayarlar

1. Kullanici olusturun, rol/aktiflik guncelleyin ve sifre sifirlayin.
2. Teknisyen imzasi ve sirket logosu yukleyin.
3. Aktif isi olan kullaniciyi silip yeniden atama akisini deneyin.

Beklenen:

- Fotograf secici ek izin istemeden acilir.
- JPEG donusumu ve upload calisir.
- Conflict durumunda yeniden atama secenekleri gorunur; veri sessizce kaybolmaz.

## 7. Harita ve Servis Kalitesi

### IOS-MAP-01 Saha Radari

1. Konumlu ve konumsuz teknisyen kayitlariyla haritayi acin.
2. Haritayi kaydirin/yakinlastirin ve yenileyin.
3. Baglantiyi kesip tekrar deneyin.

Beklenen:

- Pinler dogru koordinat ve durum rengiyle gorunur.
- Konumsuz kayit uygulamayi cokertmez.
- Hata durumunda tekrar dene mesaji gorunur.

### IOS-QUALITY-01 Servis fotograflari

1. Tarih, fis numarasi ve once/sonra filtrelerini deneyin.
2. Bos sonuc ve ag hatasi olusturun.

Beklenen:

- Filtreler dogru sonucu verir.
- Bos sonuc ile yukleme hatasi birbirinden ayirt edilir.

## 8. StoreKit Sandbox

Bu bolum App Store Connect urunleri ve Sandbox tester hazir olduktan sonra uygulanir.

### IOS-IAP-01 Urun yukleme ve iptal

1. Admin olarak Paketler ekranini acin.
2. USTA ve PATRON fiyat/donemlerini kontrol edin.
3. Satin alma ekranini acip islemi iptal edin.

Beklenen:

- Fiyat ve para birimi yalniz StoreKit'ten gelir; sabit/fallback fiyat yoktur.
- Uygun kullanicida Apple'in introductory offer metni gorunur.
- Iptal hata gibi plan degisikligi yaratmaz.

### IOS-IAP-02 USTA satin alma

1. CIRAK hesabi ile USTA satin alin.
2. Backend `apple-verify` istegini ve sirket planini kontrol edin.

Beklenen:

- JWS backend tarafinda dogrulanmadan transaction finish edilmez.
- Basarida plan/feature context yenilenir ve USTA gorunur.
- Ayni transaction tekrar geldiginde idempotent davranir.

### IOS-IAP-03 Upgrade ve downgrade

1. USTA'dan PATRON'a yukseltin.
2. PATRON'dan USTA'ya dusurme istegi verin.

Beklenen:

- Yukseltme/dusurme metinleri dogrudur.
- Apple subscription group seviyeleri nedeniyle ayni anda iki abonelik olusmaz.
- Etkinlesme zamani ve backend plani Apple kurallariyla uyumludur.

### IOS-IAP-04 Restore ve yeniden kurulum

1. Uygulamayi silip tekrar kurun.
2. Ayni uygulama hesabi ve Apple Sandbox hesabi ile girin.
3. Satin Alimlari Geri Yukle'yi calistirin.

Beklenen:

- Aktif entitlement backend ile yeniden eslenir.
- Restore tekrarlansa da duplicate payment event olusmaz.

### IOS-IAP-05 Backend hatasi ve pending

1. Apple satin alma sirasinda backend dogrulamasini gecici olarak erisilemez yapin.
2. Ask to Buy/pending senaryosunu Sandbox'ta tetikleyin.

Beklenen:

- Backend hatasinda transaction finish edilmez ve kullaniciya tekrar denenecegi soylenir.
- Pending islem plan yetkisini erken acmaz.
- Transaction update geldiginde uygulama acikken yeniden dogrulama yapilir.

### IOS-IAP-06 Iptal, iade ve hesap silme

1. Aboneligi Apple yonetim ekranindan iptal edin.
2. Sandbox refund/revoke senaryosu uygulayin.
3. Aktif abonelikle Hesabimi Sil'e basin.

Beklenen:

- Hesap silme uyarisi Apple aboneliginin ayrica iptal edilmesi gerektigini soyler.
- Server Notifications V2 sonrasinda renewal/refund/revoke backend'e yansir.
- Hesap silme tum uygulama verisini siler ve cihaz oturumunu kapatir.

## 9. Dayaniklilik ve Erisilebilirlik

### IOS-NET-01 Ag kesintileri

Her ana ekranda Wi-Fi/mobil veriyi kapatip yukleme ve yazma islemi deneyin.

Beklenen:

- Uygulama cokmez, sonsuz spinner'da kalmaz ve bos veriyi basari gibi gostermez.
- Yeniden baglaninca tekrar dene veya pull-to-refresh calisir.

### IOS-UI-01 Ekran ve metin

1. Acik/koyu tema, buyuk Dynamic Type ve yatay iPad kullanin.
2. Klavye acikken tum formlari tamamlayin.

Beklenen:

- Metinler kesilmez, butonlar ust uste binmez ve kritik aksiyonlar erisilebilir kalir.
- Liste, sheet, alert ve tab gecislerinde layout sicramasi olmaz.

### IOS-LIFE-01 Yasam dongusu

Upload, PDF uretimi ve satin alma sirasinda uygulamayi arka plana alip geri donun.

Beklenen:

- Islem ya guvenle tamamlanir ya da anlasilir sekilde tekrar denenebilir.
- Duplicate servis fisi, odeme veya stok hareketi olusmaz.

## Cikis Kriterleri

- Tum P0/P1 senaryolari basarili.
- Crash, veri sizintisi, tenant ihlali veya duplicate odeme yok.
- StoreKit purchase/restore/upgrade gercek Sandbox JWS ile backend'de dogrulandi.
- iPhone ve destekleniyorsa iPad ekran goruntuleri alindi.
- Her hata icin cihaz, build, hesap rolu, adimlar ve ekran goruntusu kaydedildi.

## Sonuc Kaydi

| Test ID | Build | Cihaz | Sonuc | Kanit/Not |
|---|---|---|---|---|
| IOS-AUTH-01 |  |  | Bekliyor |  |
| IOS-ROLE-01 |  |  | Bekliyor |  |
| IOS-INV-02 |  |  | Bekliyor |  |
| IOS-TICKET-02 |  |  | Bekliyor |  |
| IOS-IAP-02 |  |  | Bekliyor |  |
| IOS-IAP-04 |  |  | Bekliyor |  |
| IOS-UI-01 |  |  | Bekliyor |  |
