namespace TTIDataAPI.Models
{
    /// <summary>
    /// Arayüzün ve sunucunun paylaştığı sabit listeler — tek doğruluk kaynağı.
    /// </summary>
    /// <remarks>
    /// Bu sınıf, arıza riski etiketinin üç yerde üç farklı yazıldığı durumu kapatmak için var:
    /// model varsayılanı "1.0 - Cok Dusuk", SQLite verisi "7.0 - Yüksek", arayüz "3 - Yüksek".
    /// Üçü de birbirini tutmadığı için hiçbir sorgu ve hiçbir rapor güvenilir değildi ve
    /// **hata da çıkmıyordu**. Arayüz artık seçenekleri /api/lookup uçlarından üretir.
    /// </remarks>
    public static class Sabitler
    {
        /// <summary>Yeni açılan talebin başlangıç statüsü. Türkçe iş akışı: onay bekliyor.</summary>
        public const string StatuAcik = "Onay Bekliyor";

        /// <summary>
        /// Talep statüleri (Türkçe iş akışı). Sıra, sahadaki yaşam döngüsünü izler:
        /// Onay Bekliyor → İşleniyor → Tamamlandı (ya da her aşamada İptal Edildi).
        /// Tek doğruluk kaynağı burasıdır; arayüz seçenekleri /api/lookup/statuler'den üretir.
        /// </summary>
        public static readonly string[] Statuler =
        {
            "Onay Bekliyor",
            "İşleniyor",
            "Tamamlandı",
            "İptal Edildi",
        };

        public static readonly string[] RiskSeviyeleri =
        {
            "1 - Düşük",
            "2 - Orta",
            "3 - Yüksek",
            "4 - Kritik",
        };

        /// <summary>
        /// Proje kapsamlarının İLK TOHUMU — çalışma zamanındaki doğruluk kaynağı DEĞİL.
        /// </summary>
        /// <remarks>
        /// Liste `rapor.proje_kapsami` tablosuna taşındı; yönetici arayüzden yeni kapsam
        /// ekleyebiliyor. Buradaki dizi yalnızca <see cref="Services.BaslangicVerisi"/> tarafından,
        /// tabloda eksik olan maddeleri tamamlamak için okunur. Buraya madde eklemek çalışan bir
        /// kurulumu değiştirmez (tabloda zaten varsa atlanır); yeni kapsam arayüzden eklenir.
        /// </remarks>
        public static readonly string[] ProjeKapsamlari =
        {
            "1-Açıkta Serili Kablolar",
            "2-Servis Yolu Olmayan Güzergahlar",
            "3-Galvaniz Boru Geçişleri",
            "4-Arıza Sonrası Menholhüz Gömülü Ekler",
            "5-Havai Güzergah",
            "6-Şahıs Arazi Geçişleri",
            "7-Altyapısız Güzergah",
        };

        public const string RolYonetici = "admin";
        public const string RolKullanici = "user";
    }
}
