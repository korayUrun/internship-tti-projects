# PDF başlığındaki marka logosu

**Dosya yerinde** (4 Ağustos 2026): `tti-logo.png` — 720×243, saydam zeminli. Kaynağı
`app/src/main/res/drawable-nodpi/tti_logo.png`; iki kopya bilinçli, çünkü APK ve jar ayrı paketler.

Bu klasöre **`tti-logo.png`** adıyla TTI logosunu koyun; sunucu yeniden derlendiğinde (`:server:bootJar`)
PDF'in sağ üst köşesinde otomatik olarak görünür. Kod değişikliği gerekmez.

⚠️ **İki ayrıntı sessizce metne düşürür** — ikisi de log'a bir satır bırakır, hata vermez:
1. **Ad tam olarak `tti-logo.png` olmalı.** `tti-logo.jpeg`, `logo.png` gibi bir ad aranmaz; dosya
   "yok" sayılır.
2. **İçerik gerçekten PNG olmalı.** Uzantıyı `.png` yapmak yetmez: `PngDogrulayici` dosyanın ilk
   sekiz baytına bakar, JPEG'i reddeder.

- **Biçim:** PNG (içeriğinden doğrulanır — adı `.png` olan başka bir dosya kabul edilmez).
- **Arka plan:** saydam tercih edilir; PDF'te beyaz zemine basılır.
- **Boyut:** en az ~600 px genişlik yeterli. Şablon 14 mm yüksekliğe ölçekler, en/boy oranı korunur.
- **Dosya yoksa** belge üretimi aksamaz: başlıkta marka adı **metin olarak** basılır ve log'a bir
  `INFO` satırı düşer.

İlgili kod: `server/.../cikti/MarkaLogosu.kt` · şablon: `core/domain/.../document/PdfTemplate.kt`
