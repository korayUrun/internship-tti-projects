# Security Policy

## Secret management

- API anahtarları, parola/JWT imza anahtarı ve benzeri tüm sırlar depoya commit edilmemelidir.
- Sırlar yalnızca environment variables veya kurumsal secret manager üzerinden verilmelidir.
- `.env.example` dosyaları yalnızca örnek/placeholder değerler içermelidir (`CHANGE_ME`, `example.invalid`).

## Vulnerability reporting

Bir güvenlik açığı bulursanız lütfen public issue açmadan önce repository sahibine özel kanaldan bildirin.
Raporunuzda etkilenen dosya/akış, yeniden üretim adımı ve beklenen etkiyi paylaşın.

## Historical exposure warning

Bu depoda geçmişte credential bulunmuş olabileceği varsayımıyla hareket edilmelidir.
Public açılmadan önce:

1. Tüm ilgili credential ve anahtarlar rotate edilmelidir.
2. Gerekirse git geçmişi secret temizliği için yeniden yazılmalıdır.
3. Rotate sonrası sistem erişimleri doğrulanmalıdır.
