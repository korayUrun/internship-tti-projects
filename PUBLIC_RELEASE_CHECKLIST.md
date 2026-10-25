# Public Release Checklist

Bu kontrol listesi, depoyu public yapmadan önce tamamlanmalıdır.

- [ ] Tüm canlı parola/API key/JWT/sertifika anahtarları dış sistemlerde rotate edildi.
- [ ] Public repoda yalnızca placeholder (`CHANGE_ME`, `example.invalid`) değerler kaldı.
- [ ] Müşteri/çalışan/operasyonel envanter verileri depodan kaldırıldı veya sentetik hale getirildi.
- [ ] Secret scan (CI + local) temiz sonuç verdi.
- [ ] Güvenlik bildirimi ve kullanım notları (`SECURITY.md`, `README.md`) güncel.

## Önemli sınırlama

Bu PR, tek başına aşağıdakileri **yapmaz**:

1. Dış sistemlerde credential rotation
2. Mevcut git geçmişindeki secret'ların otomatik yok edilmesi

Bu iki adım ayrıca yönetici tarafından uygulanmalıdır.

## Güvenli geçmiş temizleme örnekleri (yönetici)

> Aşağıdaki komutlar yalnızca örnektir; gerçek secret değeri yazmayın.

### git-filter-repo

```bash
git clone --mirror <repo-url> repo-clean.git
cd repo-clean.git

# Örnek: hassas dosyaları geçmişten çıkar
git filter-repo \
  --path KULLANICILAR-VE-PAROLALAR.md \
  --path tti-sunucu/kurulum-2026-08-19 \
  --invert-paths

# Örnek: metin tabanlı replace kurallarıyla secret pattern temizle
cat > /tmp/replacements.txt <<'EOF'
regex:AKIA[0-9A-Z]{16}==>REDACTED_AWS_KEY
regex:[A-Za-z0-9_\-]{24,}==>REDACTED_TOKEN
EOF

git filter-repo --replace-text /tmp/replacements.txt

# Son doğrulama ve force push (koordinasyon gerekli)
git push --force --all
git push --force --tags
```

### BFG Repo-Cleaner (alternatif)

```bash
java -jar bfg.jar --delete-files KULLANICILAR-VE-PAROLALAR.md <repo>.git
java -jar bfg.jar --delete-folders tti-sunucu/kurulum-2026-08-19 --no-blob-protection <repo>.git
```

## Rotation örnek başlıkları

- Veritabanı kullanıcı parolaları
- JWT imzalama anahtarları
- Yönetici/parola tabanlı yerel hesaplar
- Uygulama API anahtarları
- Sertifika private key materyali
