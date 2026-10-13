# Pusula Servis iOS Gercek Cihaz Test Plani

Bu plan, App Store Connect ayarlari tamamlandiktan sonra iPhone ve iPad uzerinde uygulanacak yayin oncesi kabul testlerini tanimlar.

## Test Ortami

- Backend: production veya izole staging VPS
- Uygulama: Release'e yakin Debug/TestFlight build
- Roller: `COMPANY_ADMIN`, `TECHNICIAN`
- Tenantlar: birbirinden bagimsiz A ve B sirketi
- Apple: Sandbox tester hesabi
- Urunler: `com.pusula.usta`, `com.pusula.usta.yearly`, `com.pusula.patron`, `com.pusula.patron.yearly`
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

Ek regresyon (12 October 2026): agi kapatip gecerli kayitli oturumla soguk acilis yapin; 429/5xx gecici hatalarini izole test ortaminda deneyin. Keychain bilgileri silinmemeli, onbellekten yetkili ekran acilmamali; **Tekrar Dene / Baska hesapla giris** secenekleri gorunmelidir. Ag duzeldikten sonra tekrar deneme dogru tenant/rolu acmali ve bekleyen bildirim ancak ekran hazir olunca yonlendirmelidir. 401/403 ve desteklenmeyen rol ise guvenle oturumu kapatmalidir.

### IOS-AUTH-04 Kurumsal ve hatali giris

1. Kurumsal sekmede kurum kodu, kullanici adi ve sifreyle girin.
2. Hatali sifre ve hatali kurum kodu deneyin.

Beklenen:

- Dogru hesap dogru tenant ve rolle acilir.
- Hatali bilgiler hassas sunucu ayrintisi gostermeyen anlasilir hata verir.

### IOS-AUTH-05 Apple/Google ile sifresiz kayit ve giris (11 October 2026)

1. iPhone ve iPad'de hem bireysel giris hem hesap olusturma ekranindan Apple ile devam edin.
2. Yeni test hesabinda E-postami Gizle'yi, mevcut hesapta tekrar girisi deneyin; Apple ikinci giriste ad/e-posta vermeyebilir.
3. Apple penceresini iptal edin; sonra tekrar deneyin. Google akisinda da basari/iptal senaryolarini kontrol edin.
4. Giristen sonra uygulamayi tamamen kapatip tekrar acin.
5. Hesap bolumunden **Masaustu sifresi belirle (istege bagli)** islemini kendiniz acin; once vazgecin veya sheet'i kapatin, sonra yeniden acip guclu bir sifre kaydedin.
6. Yalniz kayitli sifreyi kullanarak masaustu girisini ve mevcut finansal onaylari kontrol edin. Eski sifreli hesaplarin sifresinin degismedigini teyit edin.

Beklenen:

- Apple/Google kimligi sunucuda dogrulandiktan sonra mobil oturum acilir; zorunlu sifre formu yoktur. Backend'in eski `requiresPasswordSetup: true` alani bu akisi engellemez.
- E-postami Gizle ve sonraki giris ayni hesap/tenant ile devam eder; yeni sirket olusturulmaz.
- Opsiyonel masaustu formundan vazgecmek cikis yaptirmaz veya mobil fonksiyonlari kisitlamaz. Klavye acikken iPad dahil kaydet/vazgec butonlari erisilebilirdir.
- Sifre kaydedilince ilk sifre belirleme secenegi kaybolur; mevcut sifre bu yolla uzerine yazilamaz. Yeniden acilista sunucudan dogru durum gelir.
- Yetkisiz rol, baska tenant ve gecersiz parola denemeleri reddedilir; bos parola veya istemci bayragi finansal yetki acmaz.

Otomatik Mac kapilari: `swift test --package-path frontend-appstore` ve `SocialSignInFlowTests` (iPhone + iPad), `AuthPresentationTests`, `StartupSmokeTests`, `TicketListPerformanceTests`. DEBUG-only offline fixture gercek Apple/Google girisinin veya cihaz testinin yerine gecmez.

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

Ek regresyon: tarayiciyi 10 kez hizla acip kapatin; kamera izin penceresi acikken tarayicidan ayrilin ve izni daha sonra verin. Arka plana alip geri donun. Kapanmis ekran kamerayi yeniden baslatmamali; kamera kapanisinda ana arayuz takilmamali; Thread Performance Checker ana thread'de `startRunning`/`stopRunning` uyarmamalidir.

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

#### Product availability regressions (2026-10-04)

- Run `swift test --package-path frontend-appstore`: routing/session policies plus six real product-catalog policy tests.
- Run `AuthPresentationTests` and `StartupSmokeTests` on a clean simulator, then a Release simulator build. Confirm the bundled font resolves as `GoogleSans-TextMedium` and both provider buttons have the same width/height. The Turkish system Apple button must localize without replacing its official artwork.
- Real device/TestFlight: after confirming Active agreements/bank/tax and the four exact product IDs in App Store Connect, open Packages. Prices must come from StoreKit, not a screenshot fixture or hardcoded fallback.
- Empty Apple response: all three plan cards and the server's limits remain visible. Paid CTAs are disabled, the free plan reads perpetual/free, and the notice must not blame Wi-Fi.
- Partial response: available paid options work; missing monthly/yearly options remain disabled. Switch billing periods to verify both states.
- Offline/failed refresh: preserve already received Apple prices; show a separate connection/request notice. No product-loading failure may open a purchase-error alert.
- Use the notice's Technical Information to inspect bundle/version/build, actual storefront, requested/returned count, missing IDs and error domain/code. It contains no identity, token, receipt or signed transaction.
- Repeat Retry while loading: only one product request may run. Close and reopen Packages during a request; the loading flag must recover. Also run the account-binding and refresh-failure regressions below; purchase verification changed in the 12 October preflight release.
- StoreKit/simulator UI validation still requires a Mac. These source changes do not repair an Apple-side catalog problem by themselves and do not establish that product loading has been verified on a real device.

### IOS-IAP-02 USTA satin alma

1. CIRAK hesabi ile USTA satin alin.
2. Backend `apple-verify` istegini ve sirket planini kontrol edin.

Beklenen:

- JWS backend tarafinda dogrulanmadan transaction finish edilmez.
- Basarida plan/feature context yenilenir ve USTA gorunur.
- Ayni transaction tekrar geldiginde idempotent davranir.
- Satin alma Apple'a sunucunun isletme UUID'siyle (`appAccountToken`) gider; ayni transaction'in purchase sonucu ve updates akisinda ayni anda gelmesi duplicate dogrulama/odeme yaratmaz.

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
- Uygulama kapaliyken/auth hazir degilken gelen unfinished transaction, dogru isletme girisinden sonra yeniden denenir. Baska isletmeye ait transaction finish edilmez veya o isletmenin paketini degistirmez.

### IOS-IAP-06 Iptal, iade ve hesap silme

1. Aboneligi Apple yonetim ekranindan iptal edin.
2. Sandbox refund/revoke senaryosu uygulayin.
3. Aktif abonelikle Hesabimi Sil'e basin.

Beklenen:

- Hesap silme uyarisi Apple aboneliginin ayrica iptal edilmesi gerektigini soyler.
- Server Notifications V2 sonrasinda renewal/refund/revoke backend'e yansir.
- Hesap silme giris kimliklerini/sosyal baglantilari, imzayi ve cihaz bildirim kaydini kaldirir; profil anonimlestirilir ve cihaz oturumu kapanir. Ortak isletme/servis/finans kayitlari hukuki ve operasyonel gerekcelerle korunabilir. Uyarisinda tum verilerin silinecegi iddia edilmez.
- Apple abonelik yonetimi baglantisi paket ve rol degisse bile Hesap ekraninda erisilebilir kalir; hesap silme aboneligi kendiliginden iptal etmez.

### IOS-IAP-07 Satin alma sirasinda hesap degistirme

1. A isletmesinin admin hesabinda Sandbox satin almasini acin; islem/pending tamamlanmadan cikis yapip B isletmesine girin.
2. Apple islem sonucunu veya bekleyen transaction update'ini tamamlayin; restore'u B ile de deneyin.
3. A isletmesine geri girip unfinished/restore akisini tekrar deneyin.

Beklenen:

- Istek ilk yakalanan JWT/oturum revizyonuna sabitlenir; A islemi B'nin oturumuyla gonderilmez.
- Imzali UUID farkliysa backend yazma yapmadan reddeder; B paketi degismez, eski sonuc B ekraninda basari gostermez.
- A'ya donuste dogrulama/idempotent finish calisir. Ayni isletmenin farkli yetkili yoneticileri ayni kalici UUID'yi kullanir; UUID kisi bilgisi icermez.

### IOS-IAP-08 Eski satin alma sahipligi

1. Yeni UUID'yi icermeyen, daha once A'ya baglanmis eski bir Sandbox subscription chain'ini A ile restore edin.
2. Ayni chain'i B ile ve A farkli odeme saglayicisina gectikten sonra B ile deneyin.
3. Henuz hicbir isletmeye kaydedilmemis eski tokensiz transaction'i restore etmeyi deneyin.

Beklenen:

- Kayitli A sahipligi korunur; B'ye aktarim ledger ile de reddedilir.
- Eski eslesmemis transaction 409 ile guvenle reddedilir; basari/finish/plan degisikligi yoktur. Kullaniciya yeniden odeme yapmamasi ve destekten sahiplik cozumlemesi gerektigi soylenir.
- Eski build ile yeni tokensiz ilk satin almalarin da bu kurala tabi oldugu rollout planinda acikca belirtilir; guvenlik kontrolu bypass edilmez.

### IOS-IAP-09 Dogrulama sonrasi yenileme ve dil

1. Apple dogrulamasi backend'de basarili olduktan sonra feature-context yenilemesini gecici olarak basarisiz yapin.
2. Restore icin de tekrarlayin. Ag duzeldiginde paketi yenileyin.
3. Uygulamayi Turkce ve Ingilizce acip paket donemi, limitler, iptal/pending/restore ve hesap silme metinlerini okuyun.

Beklenen:

- Sunucu dogrulamasi tamamlanmadan finish yoktur. Dogrulama basarili, context yenileme basarisizsa "paket guncellendi" denmez; yenileme beklendigi ve yeniden odeme yapilmamasi aciklanir.
- Sunucudaki paket korunur; sonraki basarili yenileme dogru yetkileri getirir.
- Kaynak anahtarlar, yanlis ceviri veya bozuk format argumanlari gorunmez. Fiyat yalniz gercek StoreKit urununden, paket limitleri backend'den gelir; plan icerigi alinamazsa satin alma CTA'si kapali kalir.

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

### IOS-READ-01 Kesilen baglanti ve bolum bazli tekrar deneme

Yeni build ile demo is emrini acin. Parca, teknisyen notu veya islem gecmisi
okumasi basarisizken o bolumde yukleme hatasi ve Tekrar Dene gorunmeli;
"henuz kayit yok" yalnizca basarili bos sunucu yanitindan sonra gorunmeli.
Baglanti geri geldiginde sadece basarisiz bolumu tekrar deneyin. Diger bolumler
ve daha once alinmis kayitlar korunmali. Sertifika/guven denetimi atlanmamali.
Eksik ozet alanlari olan eski kapali fiste parca okuma hatasi varsa dogrulanmamis
malzeme/fis toplami sifir tutar gibi gosterilmemeli.

### IOS-READ-02 Musteri ve diger listelerde yenileme iptali

Musteri listesini asagi cekerek yenileyin, hizla baska sekmeye gecip geri donun.
Teklif, parca secimi, katalog, bildirim, finans, borc/cari gecmisi, servis agi,
gorsel arsivi ve ayarlar okumalarinda da tekrarlayin. Mevcut liste yenilemede
kaybolmamali; normal istek iptali "Hata / Vazgecildi" uyarisina donusmemeli.
Gercek baglanti hatasi kayit yok dememeli ve tekrar denenebilmeli. Basarili bos
okuma -> hata -> iptal edilen tekrar deneme, eski hatayi yanlis bos sonuca
donusturmemeli. Oturum degisiminden sonra eski istegin sonucu gorunmemeli.

13 Ekim 2026 tanisi: #985 icin 13:36 TR zamaninda not/gecmis GET'leri 200,
parca istegi HTTP erisim kaydinda yok; TLS 1.2/1.3 zincir/hostname denetimi
basarili. 13:46 TR musteri GET'i once 200 sonra istemci iptali 499/Broken pipe;
bu pencereye ait TLS/upstream hatasi veya servis restart'i yok. Kalici TLS
arizasi kanitlanmadi. Kaynak duzeltmesi bu gecici hatayi maskeliyor degil,
bos/hatali/yuklenen/iptal durumlarini ayiriyor. Cihazdaki kesin TLS tetikleyicisi
halen dogrulanmadi; otomatik/sentetik test gercek cihaz ag denemesinin yerine gecmez.

### IOS-NET-01 Baglanti tanilama ve manuel rapor paylasimi

Yeni build'de giris ekranindaki veya Hesap/Ayarlar altindaki Baglanti Tanilama
bolumunu acin. API okuma, PDF indirme ve gorsel yukleme; Wi-Fi/mobil veri ve
arka plandan donus senaryolarini ayni cihazda deneyin. Hata aninin TR saatini
kaydedip raporu yenileyin, inceleyin ve sistem paylasim sayfasindan paylasin.
Bu rapor eski build 54'te mevcut degildir; tek basina TLS kok nedenini cozmez.

Beklenen:

- API, indirme, yukleme ve galeriden dosya alma/hazirlama ayri operation alanlarinda gorunur.
- Domain/kod ve izin verilen underlying/CFStream kodlari tutulur; HTTP hata ve normal iptal ayridir.
- URLSession olcumleri mevcutsa DNS/TCP/TLS/istek/yanit zamanlari, yeniden kullanilan baglanti ve protokol gorunur. Bos olcum belirli bir hata asamasinin kaniti degildir.
- En fazla 100 kayit ve 24 saatlik pencere; zamanlar UTC/ISO8601'dir. Arayuz yolu baglanti baslangicindaki NWPath tahminidir; task'in cellular olcumu ayrica tutulur.
- Her kaydin sourceAppVersion/sourceBuild/sourceOSVersion alanlari olay anindaki surumu korur; rapor metadata'si raporu olusturan guncel surumu anlatir.
- Ham URL/query, kimlikler, Authorization/cookie, istek/yanit govdesi, foto/mesaj icerigi, IP/SSID/cihaz adi raporda yoktur. Hata mesajlari ve serbest userInfo metni disari cikmaz.
- Kayitlar cihazda korumali/backup disi saklanir; rapor otomatik yuklenmez. Silme ve cikis/hesap degisimi yerel raporu temizler; zaten paylasilmis kopyalari silemez.
- Sertifika dogrulamasi/URLSession guven politikasi degismez. POST/yazma islemleri otomatik tekrarlanmaz. Dosya hatasi asıl islemin sonucunu degistirmez.
- Rapor tum OS/StoreKit/sosyal giris SDK/AsyncImage trafigini kapsamaz. Tanilama UUID'si istek bazlidir; mevcut Nginx combined log'u bu header'i kaydetmez. Sunucu eslestirmesi simdilik zaman/metot/route ile yapilir.

Gerekirse Mac Console veya kontrollu Apple CFNetwork/sysdiagnose kaydi ile
gercek TLS tetikleyicisi arastirilir. Ham Apple gunlukleri hassas veri
icerebilir; genel kullanicilarda detayli CFNETWORK_DIAGNOSTICS acilmaz.

Yeni build'in dagitimi oncesinde manuel destek raporlarinin alinmasi/saklanmasi
is akisina gore App Store gizlilik beyanlari ve gizlilik metni tekrar
degerlendirilmelidir (Other Diagnostic Data/Performance Data veya destek
istisnasinin tum kosullari). Cihazda tutma, otomatik sunucuya gonderme degildir;
manuel paylasim da kosulsuz bir beyan muafiyeti kabul edilmemelidir. Bu calisma
App Store Connect'teki mevcut beyanlari veya incelemedeki build 54'u degistirmez.

## Cikis Kriterleri

- Backend V46, yeni iOS dagitimindan once yedek alinip PostgreSQL 13+ uzerinde uygulanmis; `flyway_schema_history` ve feature context UUID'si dogrulanmis olmali. Yerel Java/mock testleri SQL migrasyonunu dogrulamaz.
- Kaynak kontrolleri (`node --test scripts/test-ios-auth-contract.cjs scripts/test-ios-release-contract.cjs`), Mac `swift test`, iPhone/iPad sosyal giris UI testleri, acilis/performans testleri ve temiz Release derlemesi gecmis olmali. Windows'taki kaynak testi native derlemenin yerine gecmez.
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
