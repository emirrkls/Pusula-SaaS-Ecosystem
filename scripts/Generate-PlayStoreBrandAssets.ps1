$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$assetRoot = Join-Path $root "frontend-playstore\PusulaService\play-store-assets"
$desktopLogo = Join-Path $root "frontend-desktop\src\main\resources\app.png"
$phoneShot = Join-Path $assetRoot "real-emulator\phone\phone-02-operation.png"
$tabletShot = Join-Path $assetRoot "real-emulator\tablet-10\tablet-10-02-operation.png"

$outIcon = Join-Path $assetRoot "icon\pusula-servis-yonetimi-icon-512.png"
$outIconLegacy = Join-Path $assetRoot "icon\pusula-servis-icon-512.png"
$outFeature = Join-Path $assetRoot "feature-graphic\pusula-servis-yonetimi-feature-1024x500.png"
$outFeatureLegacy = Join-Path $assetRoot "feature-graphic\pusula-servis-feature-1024x500.png"

function EnsureDir($path) {
    if (-not (Test-Path $path)) { New-Item -ItemType Directory -Path $path | Out-Null }
}

function Color($hex) {
    $h = $hex.TrimStart("#")
    [System.Drawing.Color]::FromArgb(
        [Convert]::ToInt32($h.Substring(0, 2), 16),
        [Convert]::ToInt32($h.Substring(2, 2), 16),
        [Convert]::ToInt32($h.Substring(4, 2), 16)
    )
}

$C = @{
    Navy = Color "#1C3461"
    Deep = Color "#10264D"
    Cyan = Color "#00B6EB"
    CyanSoft = Color "#D9F6FF"
    White = Color "#FFFFFF"
    Ink = Color "#0F2147"
    Gray = Color "#5B6472"
    Border = Color "#DDE6EF"
    Surface = Color "#F4F8FC"
    Green = Color "#13A878"
    Amber = Color "#F0A11A"
}

$TR = @{
    Yonetimi = ("Y" + [char]0x00F6 + "netimi")
    IsEmri = ([char]0x0130 + [char]0x015F + " emri")
    Musteri = ("m" + [char]0x00FC + [char]0x015F + "teri")
    Akisini = ("ak" + [char]0x0131 + [char]0x015F + [char]0x0131 + "n" + [char]0x0131)
    Yonetin = ("y" + [char]0x00F6 + "netin")
    IsEmirleri = ([char]0x0130 + [char]0x015F + " emirleri")
    EkipTakibi = "Ekip takibi"
    Acik = ("A" + [char]0x00E7 + [char]0x0131 + "k")
    SahaKontrol = ("Canl" + [char]0x0131 + " saha kontrol" + [char]0x00FC)
}

function Brush($color) { New-Object System.Drawing.SolidBrush($color) }
function Pen($color, $width = 1) { New-Object System.Drawing.Pen($color, $width) }
function Font($size, $style = [System.Drawing.FontStyle]::Regular) {
    New-Object System.Drawing.Font("Segoe UI", $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

function RoundedPath($x, $y, $w, $h, $r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $path
}

function FillRound($g, $color, $x, $y, $w, $h, $r) {
    $path = RoundedPath $x $y $w $h $r
    $g.FillPath((Brush $color), $path)
    $path.Dispose()
}

function StrokeRound($g, $color, $x, $y, $w, $h, $r, $line = 1) {
    $path = RoundedPath $x $y $w $h $r
    $g.DrawPath((Pen $color $line), $path)
    $path.Dispose()
}

function DrawText($g, $text, $x, $y, $w, $h, $size, $color, $style = [System.Drawing.FontStyle]::Regular, $align = "Near") {
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = [System.Drawing.StringAlignment]::$align
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Near
    $fmt.Trimming = [System.Drawing.StringTrimming]::EllipsisWord
    $g.DrawString($text, (Font $size $style), (Brush $color), (New-Object System.Drawing.RectangleF($x, $y, $w, $h)), $fmt)
    $fmt.Dispose()
}

function NewBitmap($w, $h, $alpha = $false) {
    $fmt = if ($alpha) { [System.Drawing.Imaging.PixelFormat]::Format32bppArgb } else { [System.Drawing.Imaging.PixelFormat]::Format24bppRgb }
    $bmp = New-Object System.Drawing.Bitmap($w, $h, $fmt)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    @($bmp, $g)
}

function GetCompassSymbol() {
    if (-not (Test-Path $desktopLogo)) {
        throw "Desktop logo not found: $desktopLogo"
    }

    $src = [System.Drawing.Bitmap]::FromFile($desktopLogo)
    $crop = New-Object System.Drawing.Rectangle(230, 55, 560, 640)
    $symbol = New-Object System.Drawing.Bitmap($crop.Width, $crop.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($symbol)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $crop.Width, $crop.Height)), $crop, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $src.Dispose()

    for ($x = 0; $x -lt $symbol.Width; $x++) {
        for ($y = 0; $y -lt $symbol.Height; $y++) {
            $p = $symbol.GetPixel($x, $y)
            if ($p.R -gt 242 -and $p.G -gt 242 -and $p.B -gt 242) {
                $symbol.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
            }
        }
    }
    $symbol
}

function DrawCompass($g, $x, $y, $w, $h) {
    $symbol = GetCompassSymbol
    $g.DrawImage($symbol, (New-Object System.Drawing.Rectangle($x, $y, $w, $h)))
    $symbol.Dispose()
}

function DrawTinyGlyph($g, $kind, $x, $y, $size, $color) {
    $pen = Pen $color ([Math]::Max(2, [int]($size / 12)))
    $brush = Brush $color
    if ($kind -eq "jobs") {
        $g.DrawRectangle($pen, $x + $size * .22, $y + $size * .16, $size * .56, $size * .68)
        $g.DrawLine($pen, $x + $size * .34, $y + $size * .34, $x + $size * .66, $y + $size * .34)
        $g.DrawLine($pen, $x + $size * .34, $y + $size * .50, $x + $size * .66, $y + $size * .50)
        $g.DrawLine($pen, $x + $size * .34, $y + $size * .66, $x + $size * .56, $y + $size * .66)
    } elseif ($kind -eq "team") {
        $g.FillEllipse($brush, $x + $size * .38, $y + $size * .18, $size * .24, $size * .24)
        $g.DrawArc($pen, $x + $size * .22, $y + $size * .42, $size * .56, $size * .42, 200, 140)
        $g.FillEllipse($brush, $x + $size * .12, $y + $size * .32, $size * .18, $size * .18)
        $g.FillEllipse($brush, $x + $size * .70, $y + $size * .32, $size * .18, $size * .18)
    } elseif ($kind -eq "stock") {
        $g.DrawRectangle($pen, $x + $size * .18, $y + $size * .28, $size * .64, $size * .48)
        $g.DrawLine($pen, $x + $size * .18, $y + $size * .42, $x + $size * .82, $y + $size * .42)
    } else {
        $g.DrawLine($pen, $x + $size * .22, $y + $size * .72, $x + $size * .78, $y + $size * .72)
        $g.FillRectangle($brush, $x + $size * .28, $y + $size * .50, $size * .10, $size * .20)
        $g.FillRectangle($brush, $x + $size * .46, $y + $size * .34, $size * .10, $size * .36)
        $g.FillRectangle($brush, $x + $size * .64, $y + $size * .20, $size * .10, $size * .50)
    }
    $pen.Dispose()
    $brush.Dispose()
}

function DrawMetric($g, $x, $y, $label, $value, $kind, $accent) {
    FillRound $g $C.White $x $y 152 118 18
    StrokeRound $g $C.Border $x $y 152 118 18 2
    FillRound $g ([System.Drawing.Color]::FromArgb(32, $accent)) ($x + 18) ($y + 18) 42 42 12
    DrawTinyGlyph $g $kind ($x + 22) ($y + 22) 34 $accent
    DrawText $g $label ($x + 74) ($y + 24) 66 24 16 $C.Gray
    DrawText $g $value ($x + 74) ($y + 50) 66 42 28 $C.Ink ([System.Drawing.FontStyle]::Bold)
}

function DrawPhoneMock($g, $path, $x, $y, $w, $h) {
    $x = [int]$x
    $y = [int]$y
    $w = [int]$w
    $h = [int]$h
    FillRound $g ([System.Drawing.Color]::FromArgb(235, 255, 255, 255)) $x $y $w $h 42
    StrokeRound $g ([System.Drawing.Color]::FromArgb(90, 255, 255, 255)) $x $y $w $h 42 2
    if (Test-Path $path) {
        $img = [System.Drawing.Bitmap]::FromFile($path)
        $inner = New-Object System.Drawing.Rectangle -ArgumentList ($x + 10), ($y + 10), ($w - 20), ($h - 20)
        $clip = RoundedPath $inner.X $inner.Y $inner.Width $inner.Height 34
        $oldClip = $g.Clip
        $g.SetClip($clip)
        $srcRatio = $img.Width / $img.Height
        $dstRatio = $inner.Width / $inner.Height
        if ($srcRatio -gt $dstRatio) {
            $srcH = $img.Height
            $srcW = [int]($srcH * $dstRatio)
            $srcX = [int](($img.Width - $srcW) / 2)
            $srcRect = New-Object System.Drawing.Rectangle($srcX, 0, $srcW, $srcH)
        } else {
            $srcW = $img.Width
            $srcH = [int]($srcW / $dstRatio)
            $srcY = [int](($img.Height - $srcH) / 2)
            $srcRect = New-Object System.Drawing.Rectangle(0, $srcY, $srcW, $srcH)
        }
        $g.DrawImage($img, $inner, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
        $g.Clip = $oldClip
        $clip.Dispose()
        $img.Dispose()
    }
}

function GenerateIcon($file) {
    $parts = NewBitmap 512 512 $true
    $bmp = $parts[0]; $g = $parts[1]
    $g.Clear([System.Drawing.Color]::Transparent)

    $rect = New-Object System.Drawing.Rectangle(0, 0, 512, 512)
    $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $C.Deep, $C.Navy, 45)
    $g.FillPath($lg, (RoundedPath 0 0 512 512 108))
    $lg.Dispose()

    $cyanPen = Pen ([System.Drawing.Color]::FromArgb(70, $C.Cyan)) 12
    $g.DrawEllipse($cyanPen, 60, 58, 392, 392)
    $cyanPen.Dispose()
    FillRound $g ([System.Drawing.Color]::FromArgb(245, 255, 255, 255)) 96 96 320 320 80
    DrawCompass $g 132 80 248 300
    FillRound $g $C.Cyan 312 332 88 88 24
    DrawTinyGlyph $g "jobs" 330 350 52 $C.White

    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

function GenerateFeature($file) {
    $parts = NewBitmap 1024 500
    $bmp = $parts[0]; $g = $parts[1]
    $bg = New-Object System.Drawing.Rectangle(0, 0, 1024, 500)
    $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($bg, $C.Deep, $C.Navy, 0)
    $g.FillRectangle($lg, $bg)
    $lg.Dispose()

    FillRound $g ([System.Drawing.Color]::FromArgb(255, 0, 150, 196)) 800 -126 340 340 170
    FillRound $g ([System.Drawing.Color]::FromArgb(255, 8, 118, 150)) -140 330 320 320 160
    $ringPen = Pen ([System.Drawing.Color]::FromArgb(42, $C.White)) 3
    $g.DrawEllipse($ringPen, 690, 42, 450, 450)
    $ringPen.Dispose()

    FillRound $g ([System.Drawing.Color]::FromArgb(246, 255, 255, 255)) 64 58 82 82 22
    DrawCompass $g 78 56 54 66
    DrawText $g "Pusula Servis" 64 164 410 52 42 $C.White ([System.Drawing.FontStyle]::Bold)
    DrawText $g $TR.Yonetimi 64 214 350 48 40 $C.Cyan ([System.Drawing.FontStyle]::Bold)
    $featureLine = $TR.IsEmri + ", ekip, " + $TR.Musteri + ", stok ve finans " + $TR.Akisini + " tek ekranda " + $TR.Yonetin + "."
    DrawText $g $featureLine 66 282 438 76 25 ([System.Drawing.Color]::FromArgb(236, 255, 255, 255))

    $pillData = @(
        @($TR.IsEmirleri, "jobs", $C.Cyan),
        @($TR.EkipTakibi, "team", $C.Green),
        @("Stok", "stock", $C.Amber),
        @("Finans", "finance", $C.Cyan)
    )
    for ($i = 0; $i -lt $pillData.Count; $i++) {
        $pill = $pillData[$i]
        $col = $i % 2
        $row = [Math]::Floor($i / 2)
        $px = 66 + ($col * 178)
        $py = 376 + ($row * 50)
        FillRound $g ([System.Drawing.Color]::FromArgb(28, $C.White)) $px $py 164 40 18
        DrawTinyGlyph $g $pill[1] ($px + 12) ($py + 8) 24 $pill[2]
        DrawText $g $pill[0] ($px + 42) ($py + 10) 108 22 15 $C.White ([System.Drawing.FontStyle]::Bold)
    }

    DrawPhoneMock $g $tabletShot 512 58 420 268
    DrawPhoneMock $g $phoneShot 692 124 174 324

    FillRound $g ([System.Drawing.Color]::FromArgb(245, 255, 255, 255)) 498 344 430 96 28
    DrawMetric $g 524 358 $TR.Acik "6" "jobs" $C.Cyan
    DrawMetric $g 692 358 "Ekip" "3" "team" $C.Green
    FillRound $g ([System.Drawing.Color]::FromArgb(26, $C.Cyan)) 858 368 44 44 14
    DrawTinyGlyph $g "finance" 867 377 26 $C.Cyan

    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

EnsureDir (Split-Path -Parent $outIcon)
EnsureDir (Split-Path -Parent $outFeature)

GenerateIcon $outIcon
GenerateFeature $outFeature
Copy-Item -LiteralPath $outIcon -Destination $outIconLegacy -Force
Copy-Item -LiteralPath $outFeature -Destination $outFeatureLegacy -Force

Write-Host "Generated:"
Write-Host $outIcon
Write-Host $outFeature
