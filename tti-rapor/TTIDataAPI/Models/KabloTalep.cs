namespace TTIDataAPI.Models
{
    /// <summary>
    /// Sahadan gelen kablo/altyapı talebi. `rapor` şemasındaki ana kayıt.
    /// </summary>
    /// <remarks>
    /// Tarih alanları UTC tutulur. Npgsql `timestamptz` sütununa Kind=Local bir DateTime yazmayı
    /// reddeder (çalışma zamanı hatası); SQLite'ta bu hata hiç çıkmadığı için kod baştan
    /// DateTime.Now kullanıyordu. Yerel saate çevirme arayüzün işidir.
    /// </remarks>
    public class KabloTalep
    {
        public int Id { get; set; }

        /// <summary>Kaydı açan kullanıcının kullanıcı adı (Kullanici.KullaniciAdi).</summary>
        public string Olusturan { get; set; } = string.Empty;

        public string Nosid { get; set; } = string.Empty;
        public string? LocationA { get; set; }
        public string? LocationB { get; set; }
        public string? ProjeGuzergah { get; set; }
        public string? SahaSehir { get; set; }
        public string? EkAraligi { get; set; }

        /// <summary>Güzergâh uzunluğu, metre. Arayüz "sadece sayısal değer" istiyor.</summary>
        public int? GuzergahUzunlugu { get; set; }

        public string ArizaRiski { get; set; } = Sabitler.RiskSeviyeleri[0];

        /// <summary>
        /// Güzergâh dosyasının (.kml/.kmz) YUKLEME_KOK altındaki göreli yolu
        /// (`guzergah/{yil}/{talepId}/{guid}.kml`). Dosya yoksa null — yükleme ZORUNLU DEĞİL.
        /// </summary>
        /// <remarks>
        /// Bu alan, iki uç için ayrı ayrı toplanan GPS enlem/boylamının (KonumA*/KonumB*) yerine
        /// geldi. O alanlar sahada dolmuyordu: masaüstü tarayıcıda ve kurumsal VPN arkasında konum
        /// izni çoğunlukla reddediliyor, dolayısıyla iki uçlu güzergâh neredeyse hiç oluşmuyordu.
        /// Kullanıcının zaten elinde olan .kml/.kmz dosyası hem gerçek güzergâhın tamamını taşır
        /// hem de tek noktaya indirgenmez.
        /// </remarks>
        public string? GuzergahDosyaYolu { get; set; }

        /// <summary>Kullanıcının yüklediği özgün dosya adı; indirmede bu adla sunulur.</summary>
        public string? GuzergahDosyaAdi { get; set; }

        /// <summary>
        /// Fotoğrafın YUKLEME_KOK altındaki göreli yolu (`{yil}/{talepId}/{guid}.jpg`).
        /// Görüntünün kendisi diskte durur: base64 olarak satırda tutulduğunda her listeleme
        /// sorgusu tüm fotoğrafları da çekiyordu.
        /// </summary>
        public string? FotografYolu { get; set; }

        public string? IlkAciklama { get; set; }
        public string Statu { get; set; } = Sabitler.StatuAcik;
        public string? SonAciklama { get; set; }
        public string? SonDegistiren { get; set; }

        public DateTime OlusturulmaTarihi { get; set; } = DateTime.UtcNow;
        public DateTime SonDegistirilmeTarihi { get; set; } = DateTime.UtcNow;

        public List<GuncellemeGecmisi> Gecmis { get; set; } = new List<GuncellemeGecmisi>();
    }

    /// <summary>Statü değişikliklerinin denetim izi. Kayıt silinince birlikte silinir.</summary>
    public class GuncellemeGecmisi
    {
        public int Id { get; set; }
        public int KabloTalepId { get; set; }
        public KabloTalep? KabloTalep { get; set; }

        public string Degistiren { get; set; } = string.Empty;
        public string EskiStatu { get; set; } = string.Empty;
        public string YeniStatu { get; set; } = string.Empty;
        public string? Aciklama { get; set; }
        public DateTime Tarih { get; set; } = DateTime.UtcNow;
    }
}
