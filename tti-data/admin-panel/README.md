# Admin Panel — Merkezi Veri Yönetim Sistemi

React + TypeScript + Vite. Sadece şirket iç ağında (LAN/VPN) çalışacak şekilde tasarlanmıştır — hiçbir
zaman public internete açılmamalıdır.

## Geliştirme

```bash
npm install
cp .env.example .env   # VITE_API_BASE_URL'i backend adresine göre ayarlayın
npm run dev
```

## Build (üretim)

```bash
npm run build
```

`dist/` klasörünü iç ağdaki bir web sunucusundan (nginx, IIS vb.) serve edin. Backend'in
`CORS_ALLOWED_ORIGINS` ayarına bu panelin adresini eklemeyi unutmayın.

## Ekranlar

- **Giriş** — admin kullanıcı adı/şifre, JWT `localStorage`'da tutulur.
- **Dataset listesi** — tüm dataset'ler, son güncelleme bilgisi, "Geçmiş" ve "Güncelle" linkleri.
- **Güncelleme** — sürükle-bırak Excel yükleme, validasyon hatalarını/uyarılarını gösterir.
- **Geçmiş** — yükleme geçmişi ve "Bu versiyona dön" (rollback) aksiyonu.
