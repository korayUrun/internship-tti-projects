# tti-projeler

Bu depo, birden fazla alt projeyi içeren **sanitize edilmiş** bir çalışma alanıdır.
Üretim ortamlarına ait gerçek kimlik bilgileri, müşteri/çalışan verileri ve operasyonel altyapı detayları bilinçli olarak depoya dahil edilmemiştir.

## Alt projeler

- `tti-hakedis`: Kotlin/Android istemci + Spring Boot sunucu + web arayüzü
- `tti-rapor`: ASP.NET Core tabanlı raporlama API ve arayüz bileşenleri
- `tti-data`: FastAPI backend + yönetim paneli
- `tti-sunucu`: Ortak dağıtım/provizyon şablonları

## Güvenlik ve yapılandırma

- Tüm gizli değerleri ortam değişkenleri veya gizli yönetim sistemleri ile sağlayın.
- Örnek dosyalardaki `CHANGE_ME` / `example.invalid` değerlerini üretimde birebir kullanmayın.
- Ayrıntılar için [SECURITY.md](SECURITY.md) dosyasına bakın.

## Public release notları

Bu pull request, depoyu public görünürlüğe hazırlamak için gizli/sahaya özel içerik temizliği yapar.
Tarihsel commit temizliği ve dış sistemlerde credential rotation adımları için [PUBLIC_RELEASE_CHECKLIST.md](PUBLIC_RELEASE_CHECKLIST.md) dosyasını izleyin.
