$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root "frontend-playstore\PusulaService\play-store-assets"

$C = @{
    Navy = [System.Drawing.Color]::FromArgb(28, 52, 97)
    Cyan = [System.Drawing.Color]::FromArgb(0, 182, 235)
    White = [System.Drawing.Color]::FromArgb(255, 255, 255)
    Muted = [System.Drawing.Color]::FromArgb(245, 248, 251)
    Subtle = [System.Drawing.Color]::FromArgb(238, 244, 248)
    Border = [System.Drawing.Color]::FromArgb(226, 232, 240)
    Gray = [System.Drawing.Color]::FromArgb(85, 85, 85)
    Light = [System.Drawing.Color]::FromArgb(100, 116, 139)
    Green = [System.Drawing.Color]::FromArgb(16, 185, 129)
    Orange = [System.Drawing.Color]::FromArgb(245, 158, 11)
    Red = [System.Drawing.Color]::FromArgb(185, 28, 28)
}

function EnsureDir($p) { if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p | Out-Null } }
function B($color) { New-Object System.Drawing.SolidBrush($color) }
function P($color, $w = 1) { New-Object System.Drawing.Pen($color, $w) }
function F($size, $style = [System.Drawing.FontStyle]::Regular) { New-Object System.Drawing.Font("Segoe UI", $size, $style, [System.Drawing.GraphicsUnit]::Pixel) }

function NewCanvas($w, $h, $alpha = $false) {
    $fmt = if ($alpha) { [System.Drawing.Imaging.PixelFormat]::Format32bppArgb } else { [System.Drawing.Imaging.PixelFormat]::Format24bppRgb }
    $bmp = New-Object System.Drawing.Bitmap($w, $h, $fmt)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    @($bmp, $g)
}

function PathRound($x, $y, $w, $h, $r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    $p
}

function FillRound($g, $color, $x, $y, $w, $h, $r) { $g.FillPath((B $color), (PathRound $x $y $w $h $r)) }
function StrokeRound($g, $color, $x, $y, $w, $h, $r, $line = 1) { $g.DrawPath((P $color $line), (PathRound $x $y $w $h $r)) }

function Text($g, $text, $x, $y, $w, $h, $size, $color, $style = [System.Drawing.FontStyle]::Regular, $align = "Near") {
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::$align
    $sf.LineAlignment = [System.Drawing.StringAlignment]::Near
    $sf.Trimming = [System.Drawing.StringTrimming]::EllipsisWord
    $g.DrawString($text, (F $size $style), (B $color), (New-Object System.Drawing.RectangleF($x, $y, $w, $h)), $sf)
}

function Logo($g, $x, $y, $s) {
    FillRound $g $C.Cyan $x $y $s $s 16
    $pen = P $C.White ([Math]::Max(3, $s / 14))
    $g.DrawEllipse($pen, $x + $s * 0.2, $y + $s * 0.2, $s * 0.6, $s * 0.6)
    $cx = $x + $s / 2; $cy = $y + $s / 2
    $pts = @(
        [System.Drawing.PointF]::new([single]$cx, [single]($y + $s * 0.14)),
        [System.Drawing.PointF]::new([single]($x + $s * 0.58), [single]$cy),
        [System.Drawing.PointF]::new([single]$cx, [single]($y + $s * 0.86)),
        [System.Drawing.PointF]::new([single]($x + $s * 0.42), [single]$cy)
    )
    $g.FillPolygon((B $C.Navy), $pts)
}

function Topbar($g, $w, $title, $subtitle) {
    FillRound $g $C.White 44 44 ($w - 88) 120 28
    StrokeRound $g $C.Border 44 44 ($w - 88) 120 28 2
    Logo $g 72 70 58
    Text $g $title 148 68 ($w - 300) 36 30 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Text $g $subtitle 148 105 ($w - 300) 26 20 $C.Light
    FillRound $g $C.Subtle ($w - 140) 74 64 42 18
    Text $g "TR" ($w - 129) 83 42 22 18 $C.Navy ([System.Drawing.FontStyle]::Bold) "Center"
}

function Hero($g, $x, $y, $w, $title, $sub, $badge) {
    FillRound $g $C.White $x $y $w 196 26
    StrokeRound $g $C.Border $x $y $w 196 26 2
    FillRound $g $C.Cyan $x ($y + 36) 8 94 4
    Text $g $badge ($x + 34) ($y + 30) 260 28 22 $C.Light
    Text $g $title ($x + 34) ($y + 66) ($w - 68) 52 38 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Text $g $sub ($x + 34) ($y + 122) ($w - 68) 48 22 $C.Gray
}

function Metric($g, $x, $y, $w, $label, $value, $accent) {
    FillRound $g $C.White $x $y $w 132 22
    StrokeRound $g $C.Border $x $y $w 132 22 2
    FillRound $g ([System.Drawing.Color]::FromArgb(35, $accent)) ($x + 22) ($y + 20) 46 46 14
    Text $g $label ($x + 22) ($y + 76) ($w - 44) 25 20 $C.Light
    Text $g $value ($x + 22) ($y + 101) ($w - 44) 28 25 $C.Navy ([System.Drawing.FontStyle]::Bold)
}

function Card($g, $x, $y, $w, $h, $title, $body, $status, $accent) {
    FillRound $g $C.White $x $y $w $h 22
    StrokeRound $g $C.Border $x $y $w $h 22 2
    FillRound $g ([System.Drawing.Color]::FromArgb(35, $accent)) ($x + 22) ($y + 24) 52 52 16
    Text $g $title ($x + 92) ($y + 21) ($w - 118) 36 26 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Text $g $body ($x + 92) ($y + 58) ($w - 118) 54 20 $C.Gray
    FillRound $g ([System.Drawing.Color]::FromArgb(30, $accent)) ($x + 22) ($y + $h - 48) 150 30 15
    Text $g $status ($x + 34) ($y + $h - 43) 126 22 17 $accent ([System.Drawing.FontStyle]::Bold) "Center"
}

function Phone($file, $s) {
    $parts = NewCanvas 1080 1920
    $bmp = $parts[0]; $g = $parts[1]
    $g.Clear($C.Muted)
    Topbar $g 1080 "Pusula Servis" $s.Subtitle
    Hero $g 64 204 952 $s.Hero $s.HeroSub $s.Badge
    $x = 64
    foreach ($m in $s.Metrics) { Metric $g $x 440 292 $m[0] $m[1] $m[2]; $x += 330 }
    Text $g $s.Section 64 620 800 36 30 $C.Navy ([System.Drawing.FontStyle]::Bold)
    $y = 674
    foreach ($card in $s.Cards) { Card $g 64 $y 952 176 $card[0] $card[1] $card[2] $card[3]; $y += 204 }
    FillRound $g $C.White 64 1668 952 116 28
    StrokeRound $g $C.Border 64 1668 952 116 28 2
    $tabs = @("Isler", "Musteri", "Finans", "Ayarlar")
    for ($i = 0; $i -lt 4; $i++) {
        $tx = 94 + ($i * 232)
        if ($i -eq $s.Active) {
            FillRound $g ([System.Drawing.Color]::FromArgb(28, $C.Cyan)) $tx 1692 178 66 24
            Text $g $tabs[$i] $tx 1712 178 26 20 $C.Navy ([System.Drawing.FontStyle]::Bold) "Center"
        } else {
            Text $g $tabs[$i] $tx 1712 178 26 20 $C.Light ([System.Drawing.FontStyle]::Regular) "Center"
        }
    }
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

function Tablet($file, $title, $section, $variant) {
    $parts = NewCanvas 1920 1080
    $bmp = $parts[0]; $g = $parts[1]
    $g.Clear($C.Muted)
    FillRound $g $C.White 42 42 280 996 26
    StrokeRound $g $C.Border 42 42 280 996 26 2
    Logo $g 82 82 78
    Text $g "Pusula" 82 178 180 38 34 $C.Navy ([System.Drawing.FontStyle]::Bold)
    $nav = @("Dashboard", "Is Emirleri", "Musteriler", "Finans", "Ayarlar")
    for ($i = 0; $i -lt $nav.Count; $i++) {
        $ny = 260 + $i * 74
        if ($i -eq $variant) { FillRound $g ([System.Drawing.Color]::FromArgb(30, $C.Cyan)) 72 $ny 220 52 18 }
        Text $g $nav[$i] 96 ($ny + 13) 170 24 20 ($(if ($i -eq $variant) { $C.Navy } else { $C.Light })) ($(if ($i -eq $variant) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }))
    }
    Text $g $title 366 72 740 52 42 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Text $g $section 366 126 760 28 22 $C.Light
    Hero $g 366 190 620 "Operasyon gorunumu" "Servis fisleri, ekipler ve tahsilat tek ekranda" "PATRON paketi"
    Metric $g 1024 190 250 "Bugunku isler" "18" $C.Cyan
    Metric $g 1300 190 250 "Tahsilat" "24.850 TL" $C.Green
    Metric $g 1576 190 250 "Bekleyen" "7" $C.Orange
    Text $g "Aktif servis akisi" 366 440 520 38 30 $C.Navy ([System.Drawing.FontStyle]::Bold)
    $cards = @(
        @("Altunkum Otel", "Salon tipi klima bakimi, teknisyen: Mehmet Y.", "Sahada", $C.Cyan),
        @("Mavisehir Villa", "Ariza tespiti tamamlandi, parca bekleniyor.", "Bekliyor", $C.Orange),
        @("Akbuk Market", "Tahsilat alindi, rapor PDF hazir.", "Kapandi", $C.Green)
    )
    $y = 500
    foreach ($c0 in $cards) { Card $g 366 $y 690 148 $c0[0] $c0[1] $c0[2] $c0[3]; $y += 170 }
    FillRound $g $C.White 1114 440 710 456 24
    StrokeRound $g $C.Border 1114 440 710 456 24 2
    Text $g "Finans ve kalite ozeti" 1152 474 560 38 30 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Text $g "Gelir, gider, musteri bakiyesi ve servis kalite gorselleri yonetici ekraninda birlesir." 1152 526 600 58 22 $C.Gray
    Metric $g 1152 620 190 "Gelir" "38K TL" $C.Green
    Metric $g 1368 620 190 "Gider" "9K TL" $C.Red
    Metric $g 1584 620 190 "Kar" "29K TL" $C.Cyan
    FillRound $g ([System.Drawing.Color]::FromArgb(28, $C.Navy)) 1152 808 622 54 20
    Text $g "Raporlar ve servis PDF ciktilari hazir" 1178 823 560 24 20 $C.Navy ([System.Drawing.FontStyle]::Bold) "Center"
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

function Icon($file) {
    $parts = NewCanvas 512 512 $true
    $bmp = $parts[0]; $g = $parts[1]
    $g.Clear([System.Drawing.Color]::Transparent)
    FillRound $g $C.Navy 0 0 512 512 96
    FillRound $g ([System.Drawing.Color]::FromArgb(38, $C.White)) 58 58 396 396 74
    Logo $g 132 118 248
    Text $g "P" 188 174 136 126 118 $C.White ([System.Drawing.FontStyle]::Bold) "Center"
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

function Feature($file) {
    $parts = NewCanvas 1024 500
    $bmp = $parts[0]; $g = $parts[1]
    $g.Clear($C.Navy)
    FillRound $g ([System.Drawing.Color]::FromArgb(255, 0, 151, 196)) 720 -80 380 380 190
    FillRound $g ([System.Drawing.Color]::FromArgb(255, 9, 112, 128)) -120 270 340 340 170
    Logo $g 74 74 82
    Text $g "Pusula Servis" 74 176 430 58 48 $C.White ([System.Drawing.FontStyle]::Bold)
    Text $g "Mobil is emri, musteri ve tahsilat yonetimi" 76 250 440 80 30 ([System.Drawing.Color]::FromArgb(235, 255, 255, 255))
    FillRound $g ([System.Drawing.Color]::FromArgb(245, 255, 255, 255)) 570 58 360 384 38
    StrokeRound $g ([System.Drawing.Color]::FromArgb(80, 255, 255, 255)) 570 58 360 384 38 2
    Text $g "Bugunku Isler" 612 98 250 36 30 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Metric $g 612 160 130 "Acik" "18" $C.Cyan
    Metric $g 766 160 130 "Kapali" "11" $C.Green
    FillRound $g $C.White 612 324 284 78 24
    StrokeRound $g $C.Border 612 324 284 78 24 2
    Text $g "Akbuk Market" 642 344 210 26 24 $C.Navy ([System.Drawing.FontStyle]::Bold)
    Text $g "PDF rapor hazir" 642 372 210 24 19 $C.Gray
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

EnsureDir $out
foreach ($d in @("icon", "feature-graphic", "phone-screenshots", "tablet-7-screenshots", "tablet-10-screenshots")) { EnsureDir (Join-Path $out $d) }

Icon (Join-Path $out "icon\pusula-servis-icon-512.png")
Feature (Join-Path $out "feature-graphic\pusula-servis-feature-1024x500.png")

$screens = @(
    @{ Subtitle="Yonetici paneli"; Hero="Servis ekibiniz tek ekranda"; HeroSub="Is emri, tahsilat ve musteri akisini canli izleyin"; Badge="Dashboard"; Section="Ozet metrikler"; Active=0; Metrics=@(@("Bugunku","18",$C.Cyan),@("Tahsilat","24K TL",$C.Green),@("Bekleyen","7",$C.Orange)); Cards=@(@("Altunkum Otel","Bakim randevusu ve teknisyen atamasi yapildi.","Sahada",$C.Cyan),@("Mavisehir Villa","Ariza tespiti tamamlandi, parca bekleniyor.","Bekliyor",$C.Orange),@("Akbuk Market","Servis kapandi, PDF rapor hazir.","Kapandi",$C.Green)) },
    @{ Subtitle="Teknisyen ekrani"; Hero="Is emirleri cebinizde"; HeroSub="Musteri bilgisi, konum, parca ve imza akisi"; Badge="Operasyon"; Section="Aktif fisler"; Active=0; Metrics=@(@("Acik","12",$C.Cyan),@("Atandi","9",$C.Navy),@("Acil","3",$C.Red)); Cards=@(@("Servis fisi #1042","Klima gaz kontrolu, musteri: Deniz Apart.","Yolda",$C.Cyan),@("Servis fisi #1041","Kompresor kontrolu ve bakim formu.","Islemde",$C.Orange),@("Servis fisi #1038","Musteri imzasi alindi.","Tamam",$C.Green)) },
    @{ Subtitle="Musteri yonetimi"; Hero="Musteri gecmisi duzenli"; HeroSub="Servis kayitlari, teklif ve cari hareketler bir arada"; Badge="CRM"; Section="Musteri kartlari"; Active=1; Metrics=@(@("Musteri","428",$C.Cyan),@("Teklif","36",$C.Navy),@("Cari","82K TL",$C.Orange)); Cards=@(@("Didim Plaza","4 aktif cihaz, son bakim 18 Haziran.","Kurumsal",$C.Navy),@("Ege Market","2 acik servis fisi, bakiye takibi aktif.","Cari",$C.Orange),@("Altinkum Evleri","Periyodik bakim hatirlatmasi olusturuldu.","Planli",$C.Green)) },
    @{ Subtitle="Finans ve tahsilat"; Hero="Tahsilati servisle kapatin"; HeroSub="Nakit, kart, havale ve cari hesap kayitlari"; Badge="Finans"; Section="Gunluk finans"; Active=2; Metrics=@(@("Gelir","38K TL",$C.Green),@("Gider","9K TL",$C.Red),@("Kar","29K TL",$C.Cyan)); Cards=@(@("Tahsilat alindi","Servis fisi #1042 icin kart odeme kaydi.","Kart",$C.Green),@("Parca gideri","Fan motoru ve sarf malzeme gideri islendi.","Gider",$C.Red),@("Cari hareket","Musteri bakiyesi guncellendi.","Cari",$C.Orange)) }
)

for ($i = 0; $i -lt $screens.Count; $i++) { Phone (Join-Path $out ("phone-screenshots\phone-{0:00}-1080x1920.png" -f ($i + 1))) $screens[$i] }

$tabletTitles = @(
    @("Yonetici Dashboard", "Gunluk servis performansi ve finans ozeti"),
    @("Is Emirleri", "Teknisyen atama ve saha durum takibi"),
    @("Musteri ve Cari", "Musteri gecmisi, teklif ve bakiye yonetimi"),
    @("Raporlama", "PDF ciktilar, kalite gorselleri ve operasyon kayitlari")
)
for ($i = 0; $i -lt $tabletTitles.Count; $i++) {
    Tablet (Join-Path $out ("tablet-7-screenshots\tablet-7-{0:00}-1920x1080.png" -f ($i + 1))) $tabletTitles[$i][0] $tabletTitles[$i][1] $i
    Tablet (Join-Path $out ("tablet-10-screenshots\tablet-10-{0:00}-1920x1080.png" -f ($i + 1))) $tabletTitles[$i][0] $tabletTitles[$i][1] $i
}

$notes = @"
# Pusula Servis - Google Play store assets

Prepared for Play Console > Grow users > Store presence > Main store listing > Graphics.

## Files
- icon/pusula-servis-icon-512.png: 512x512 PNG, alpha
- feature-graphic/pusula-servis-feature-1024x500.png: 1024x500 PNG
- phone-screenshots/*.png: 4 phone screenshots, 1080x1920
- tablet-7-screenshots/*.png: 4 7-inch tablet screenshots, 1920x1080
- tablet-10-screenshots/*.png: 4 10-inch tablet screenshots, 1920x1080

## Short description
Teknik servis ekipleri icin is emri, musteri, stok ve tahsilat yonetimi

## Full description draft
Pusula Servis, teknik servis ekiplerinin gunluk operasyonlarini mobilde yonetmesi icin gelistirilmis bir is takip uygulamasidir. Is emirlerini goruntuleyebilir, teknisyen atamasi yapabilir, musteri bilgilerine erisebilir, servis surecinde fotograf ve imza kayitlarini takip edebilir, tahsilat ve cari hareketleri duzenli sekilde yonetebilirsiniz.

One cikan ozellikler:
- Is emri ve teknisyen takibi
- Musteri ve cihaz gecmisi
- Stok ve parca kullanimi
- Tahsilat, cari hesap ve finans ozeti
- Servis raporu ve PDF ciktilari
- Paket bazli kota ve ekip yonetimi

## Alt text suggestions
- Phone 1: Yonetici panelinde gunluk servis, tahsilat ve bekleyen isler ozeti
- Phone 2: Teknisyen ekraninda aktif servis fisleri ve saha durumu
- Phone 3: Musteri yonetimi ekraninda musteri kartlari ve cari bilgiler
- Phone 4: Finans ekraninda gelir, gider ve tahsilat kayitlari
- Tablet: Genis ekranda servis operasyonu, finans ve kalite ozeti
"@
Set-Content -Path (Join-Path $out "store-listing-notes-tr.md") -Value $notes -Encoding UTF8
Write-Host "Generated Play Store assets at $out"
