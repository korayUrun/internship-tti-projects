using System.ComponentModel.DataAnnotations;

namespace TTIDataAPI.Dtos
{
    /// <summary>
    /// Talep oluşturma isteği.
    /// </summary>
    /// <remarks>
    /// Denetleyici doğrudan KabloTalep entity'sini bind ediyordu; istemci `Id`, `Statu`, `Gecmis`
    /// ve `Olusturan` gönderip başkasının adına kayıt açabiliyordu (over-posting). Bu üç alan
    /// artık istekte yok: `Olusturan` oturumdan, `Statu` sabitten, `Id` veritabanından gelir.
    /// </remarks>
    public class TalepOlusturIstegi
    {
        [Required(ErrorMessage = "NOSID zorunludur.")]
        [MaxLength(128)]
        public string Nosid { get; set; } = string.Empty;

        [MaxLength(1024)] public string? LocationA { get; set; }
        [MaxLength(1024)] public string? LocationB { get; set; }
        [MaxLength(128)] public string? ProjeGuzergah { get; set; }
        [MaxLength(128)] public string? SahaSehir { get; set; }
        [MaxLength(128)] public string? EkAraligi { get; set; }

        /// <summary>
        /// Güzergâh uzunluğu, METRE. Zorunlu.
        /// </summary>
        /// <remarks>
        /// Tip `int?` (int değil): `[Required]` yalnızca null'ı yakalayabilsin diye. `int` olsaydı
        /// alan gönderilmediğinde 0'a düşer, `[Required]` bunu "dolu" sayar ve kayıt sessizce
        /// sıfır metreyle açılırdı. Alt sınır 1: sıfır uzunluklu güzergâh diye bir şey yok.
        ///
        /// Alan arayüzde katalogdan otomatik DOLDURULMAZ. Önceden NOSID seçilince kataloğun
        /// uzunluğu forma yazılıyordu; o değer envanterin kablo uzunluğu, sahada ölçülen güzergâh
        /// uzunluğu değil — kullanıcı onaylamadan gerçek veri gibi kaydediliyordu.
        /// </remarks>
        [Required(ErrorMessage = "Güzergâh uzunluğu (metre) zorunludur.")]
        [Range(1, 10_000_000, ErrorMessage = "Güzergâh uzunluğu metre cinsinden pozitif olmalıdır.")]
        public int? GuzergahUzunlugu { get; set; }

        [Required(ErrorMessage = "Arıza riski zorunludur.")]
        [MaxLength(32)]
        public string ArizaRiski { get; set; } = string.Empty;

        [MaxLength(4000)] public string? IlkAciklama { get; set; }
    }

    /// <summary>Statü/açıklama güncelleme isteği. `SonDegistiren` oturumdan gelir.</summary>
    public class TalepGuncelleIstegi
    {
        [Required(ErrorMessage = "Statü zorunludur.")]
        [MaxLength(32)]
        public string Statu { get; set; } = string.Empty;

        [MaxLength(4000)] public string? SonAciklama { get; set; }

        /// <summary>
        /// İstemcinin düzenlemeye başladığı kaydın sürümü (PostgreSQL xmin). Sunucu bunu, kaydın
        /// arada başkası tarafından değiştirilip değiştirilmediğini anlamak için kullanır; eşleşmezse
        /// 409 döner. Yanıttaki <see cref="TalepYaniti.Surum"/> alanından gelir.
        /// </summary>
        public uint Surum { get; set; }
    }

    public class TalepYaniti
    {
        public int Id { get; set; }
        public string Olusturan { get; set; } = string.Empty;
        public string Nosid { get; set; } = string.Empty;
        public string? LocationA { get; set; }
        public string? LocationB { get; set; }
        public string? ProjeGuzergah { get; set; }
        public string? SahaSehir { get; set; }
        public string? EkAraligi { get; set; }
        public int? GuzergahUzunlugu { get; set; }
        public string ArizaRiski { get; set; } = string.Empty;

        /// <summary>Fotoğrafın indirileceği uç; fotoğraf yoksa null. Base64 gövde DÖNMEZ.</summary>
        public string? FotografUrl { get; set; }

        /// <summary>Güzergâh dosyasının indirileceği uç; dosya yoksa null.</summary>
        public string? GuzergahUrl { get; set; }

        /// <summary>Güzergâh dosyasının özgün adı — arayüz "hangi dosya yüklü" diye bunu gösterir.</summary>
        public string? GuzergahDosyaAdi { get; set; }

        public string? IlkAciklama { get; set; }
        public string Statu { get; set; } = string.Empty;
        public string? SonAciklama { get; set; }
        public string? SonDegistiren { get; set; }
        public DateTime OlusturulmaTarihi { get; set; }
        public DateTime SonDegistirilmeTarihi { get; set; }

        /// <summary>
        /// Kaydın sürümü (PostgreSQL xmin). İstemci düzenlemeye başladığında saklar ve
        /// güncellemede <see cref="TalepGuncelleIstegi.Surum"/> ile geri gönderir; eşzamanlı
        /// "kayıp güncelleme"yi yakalamanın anahtarıdır.
        /// </summary>
        public uint Surum { get; set; }

        public List<GecmisYaniti> Gecmis { get; set; } = new();
    }

    public class GecmisYaniti
    {
        public int Id { get; set; }
        public string Degistiren { get; set; } = string.Empty;
        public string EskiStatu { get; set; } = string.Empty;
        public string YeniStatu { get; set; } = string.Empty;
        public string? Aciklama { get; set; }
        public DateTime Tarih { get; set; }
    }
}
