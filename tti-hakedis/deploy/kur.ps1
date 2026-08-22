# Kurulum hazırlığı — Windows (PowerShell).
#
#   powershell -ExecutionPolicy Bypass -File deploy\kur.ps1
#
# Ne yapar:
#   1. Belge arşivi ve yükleme klasörlerini kullanıcı profili altında oluşturur
#   2. `deploy\.env` yoksa `.env.example`'dan üretir
#   3. `.env` içindeki BOŞ ya da EKSİK anahtarları doldurur
#
# UYARI: Var olan bir `.env`'i EZMEZ. Dolu bir değeri değiştirmek, çalışan bir kurulumun arşivini
#        sessizce başka bir klasöre taşımak demek olurdu; eski dosyalar orada kalır ve kimse fark
#        etmez. Değeri değiştirmek isteyen `.env`'i elle düzenler.

$ErrorActionPreference = 'Stop'
$kaynak = Split-Path -Parent $MyInvocation.MyCommand.Path
$envDosyasi = Join-Path $kaynak '.env'

$cati = Join-Path $env:USERPROFILE 'TTI-Hakedis'
$arsiv = Join-Path $cati 'arsiv'
$yukleme = Join-Path $cati 'yukleme'

New-Item -ItemType Directory -Force -Path $arsiv, $yukleme | Out-Null

# Docker Compose bind mount'ta ileri egik cizgi ister; ters cizgi Windows yolunu bozuyor.
$arsivCompose = $arsiv -replace '\\', '/'
$yuklemeCompose = $yukleme -replace '\\', '/'

Write-Host "OK Klasorler hazir:"
Write-Host "    arsiv   : $arsiv"
Write-Host "    yukleme : $yukleme"

if (-not (Test-Path $envDosyasi)) {
    Copy-Item (Join-Path $kaynak '.env.example') $envDosyasi
    Write-Host "OK .env olusturuldu (.env.example'dan)."
}

function Set-Anahtar([string]$anahtar, [string]$deger) {
    $satirlar = Get-Content $envDosyasi
    $mevcut = $satirlar | Where-Object { $_ -match "^$anahtar=" } | Select-Object -Last 1

    if ($mevcut -and $mevcut.Substring($anahtar.Length + 1).Trim()) {
        $deger0 = $mevcut.Substring($anahtar.Length + 1)
        Write-Host "-  $anahtar zaten dolu, DOKUNULMADI: $deger0"
        # Goreli yol compose dosyasinin bulundugu klasore (deploy) gore cozulur; .env.example'dan
        # gelen './arsiv' bu yuzden deploy/arsiv'a duser ve kullanici belgeleri profil klasorunde
        # arayip bulamaz.
        if ($deger0.StartsWith('.')) {
            $goreli = $deger0.TrimStart([char]'.', [char]'/', [char]'\')
            Write-Host "   UYARI Goreli yol: gercek konum $kaynak/$goreli"
        }
        Write-Host "   Degistirmek icin: deploy/.env duzenleyin, ESKI klasorun icerigini tasiyin,"
        Write-Host "   sonra 'docker compose up -d server' ile konteyneri yeniden olusturun."
        return
    }

    if ($mevcut) {
        $satirlar = $satirlar | ForEach-Object { if ($_ -match "^$anahtar=") { "$anahtar=$deger" } else { $_ } }
        Set-Content -Path $envDosyasi -Value $satirlar -Encoding UTF8
    }
    else {
        Add-Content -Path $envDosyasi -Value "$anahtar=$deger" -Encoding UTF8
    }
    Write-Host "OK $anahtar=$deger"
}

Set-Anahtar 'ARSIV_KOK' $arsivCompose
Set-Anahtar 'YUKLEME_KOK' $yuklemeCompose

Write-Host ""
Write-Host "Siradaki adim:"
Write-Host "  1. deploy\.env icindeki DB_PAROLA'yi degistirin (varsayilan birakilmamali)"
Write-Host "  2. .\gradlew :server:bootJar"
Write-Host "  3. cd deploy; docker compose up -d --build"
Write-Host ""
Write-Host "Belgeler suraya yazilacak: $arsiv\YedekTTI\{BOLGE}\{On_Fizibilite|1_Kesif|Hakedis}\"
