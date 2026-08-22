package tr.com.tti.hakedis.server.web

/**
 * İş ve belge iptali için ortak istek gövdesi.
 *
 * [gerekce] zorunludur ve boş olamaz: iptal, "bölgede sırayla devam" kuralının tek istisnasıdır
 * (NEWPHASES.md D5). Neden kullanıldığı yazılmazsa, aylar sonra kuyruğun neden atlandığı
 * anlaşılamaz — veritabanı kısıtı da (`*_iptal_izi_tutarli`) gerekçesiz iptali kabul etmez.
 */
data class IptalIstegi(val gerekce: String? = null)
