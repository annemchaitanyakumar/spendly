Add-Type -AssemblyName System.Drawing
$srcPath = "C:\Users\chait\.gemini\antigravity-ide\brain\fe0ae3f4-882b-40f2-addc-c657cb8e8fc6\spendly_app_icon_1792650320895.jpg"
$src = [System.Drawing.Image]::FromFile($srcPath)

$targets = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

$resBase = "C:\Users\chait\.gemini\antigravity-ide\scratch\spendly\app\src\main\res"

foreach ($pair in $targets.GetEnumerator()) {
    $folder = $pair.Key
    $size = $pair.Value
    $destDir = Join-Path $resBase $folder
    if (-not (Test-Path $destDir)) { New-Item -ItemType Directory -Path $destDir -Force }
    
    $bmp = New-Object System.Drawing.Bitmap $size, $size
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($src, 0, 0, $size, $size)
    $g.Dispose()

    # Save as PNG
    $destPng = Join-Path $destDir "ic_launcher.png"
    $bmp.Save($destPng, [System.Drawing.Imaging.ImageFormat]::Png)

    $destRoundPng = Join-Path $destDir "ic_launcher_round.png"
    $bmp.Save($destRoundPng, [System.Drawing.Imaging.ImageFormat]::Png)

    $bmp.Dispose()
    Write-Host "Generated $size x $size icons in $folder"
}

# Also save a 512x512 master in drawable
$drawableDir = Join-Path $resBase "drawable"
$bmp512 = New-Object System.Drawing.Bitmap 512, 512
$g512 = [System.Drawing.Graphics]::FromImage($bmp512)
$g512.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g512.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g512.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g512.DrawImage($src, 0, 0, 512, 512)
$g512.Dispose()
$bmp512.Save((Join-Path $drawableDir "ic_app_icon.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp512.Dispose()

$src.Dispose()
Write-Host "App icon assets generation complete!"
