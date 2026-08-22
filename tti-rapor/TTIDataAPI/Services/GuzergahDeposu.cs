namespace TTIDataAPI.Services
{
    /// <summary>
    /// Talebin güzergâh dosyasını (.kml / .kmz) diske yazar.
    /// </summary>
    /// <remarks>
    /// Bu depo, formdan kaldırılan iki uçlu GPS alanlarının yerine geldi. Kullanıcı Google Earth /
    /// NetGIS çıktısını olduğu gibi yükler; sunucu dosyayı YORUMLAMAZ, sakladığı gibi geri verir.
    /// Ayrıştırmaya kalkmak, biçim uyuşmazlığında sahanın elindeki geçerli dosyayı reddetmek
    /// demek olurdu.
    ///
    /// Fotoğraflardan AYRI bir `guzergah/` alt klasörü kullanır: aynı talebin iki dosyası aynı
    /// klasöre düşseydi, yedekten elle dosya ayıklarken hangisinin ne olduğu yalnızca uzantıdan
    /// anlaşılırdı. Yükleme kökünün tamamı yedeklenir (bkz. tti-sunucu/yedek.sh).
    ///
    /// Yükleme ZORUNLU DEĞİL — tti-hakedis'teki EK1 alanından bilinçli olarak farklı.
    /// </remarks>
    public class GuzergahDeposu : DosyaDeposu
    {
        public GuzergahDeposu(IConfiguration yapilandirma, ILogger<GuzergahDeposu> gunluk)
            : base(yapilandirma, gunluk, new[] { ".kml", ".kmz" }, altKlasor: "guzergah")
        {
        }

        /// <summary>İndirmede kullanılacak içerik türü. Bilinmeyen uzantı ikili akışa düşer.</summary>
        public static string IcerikTuru(string yol) =>
            Path.GetExtension(yol).ToLowerInvariant() switch
            {
                ".kml" => "application/vnd.google-earth.kml+xml",
                ".kmz" => "application/vnd.google-earth.kmz",
                _ => "application/octet-stream",
            };
    }
}
