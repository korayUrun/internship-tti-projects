using System.ComponentModel.DataAnnotations;

namespace TTIDataAPI.Dtos
{
    public class GirisIstegi
    {
        [Required(ErrorMessage = "Kullanıcı adı zorunludur.")]
        [MaxLength(64)]
        public string KullaniciAdi { get; set; } = string.Empty;

        [Required(ErrorMessage = "Parola zorunludur.")]
        public string Parola { get; set; } = string.Empty;
    }

    public class ParolaDegistirIstegi
    {
        [Required(ErrorMessage = "Mevcut parola zorunludur.")]
        public string MevcutParola { get; set; } = string.Empty;

        [Required(ErrorMessage = "Yeni parola zorunludur.")]
        [MinLength(6, ErrorMessage = "Yeni parola en az 6 karakter olmalıdır.")]
        public string YeniParola { get; set; } = string.Empty;
    }

    public class OturumYaniti
    {
        public string KullaniciAdi { get; set; } = string.Empty;
        public string AdSoyad { get; set; } = string.Empty;
        public string Rol { get; set; } = string.Empty;
        public bool IlkGirisMi { get; set; }

        /// <summary>Kullanıcı yönetimi ekranını yalnızca bu bayrağı taşıyan hesap görür.</summary>
        public bool SistemYoneticisiMi { get; set; }
    }

    /// <summary>Sistem yöneticisinin başka bir kullanıcıyı güncelleme isteği.</summary>
    /// <remarks>
    /// İki alan da opsiyonel: yalnızca parola sıfırlamak ya da yalnızca giriş adını düzeltmek
    /// olağan durumlar. İkisi de boş gelirse istek anlamsızdır ve 400 döner.
    /// </remarks>
    public class KullaniciGuncelleIstegi
    {
        [MaxLength(64)]
        public string? YeniKullaniciAdi { get; set; }

        [MinLength(6, ErrorMessage = "Yeni parola en az 6 karakter olmalıdır.")]
        public string? YeniParola { get; set; }
    }

    public class KullaniciYaniti
    {
        public string KullaniciAdi { get; set; } = string.Empty;
        public string AdSoyad { get; set; } = string.Empty;
        public string Rol { get; set; } = string.Empty;
        public bool Aktif { get; set; }
        public bool IlkGirisMi { get; set; }
        public bool SistemYoneticisiMi { get; set; }
    }
}
