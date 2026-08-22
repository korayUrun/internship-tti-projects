namespace TTIDataAPI.Models
{
    /// <summary>
    /// NOSID araması ve otomatik doldurma için kablo envanteri kopyası.
    /// </summary>
    /// <remarks>
    /// Aynı envanterin tamamı TTI'nin `hakedis.kable` tablosunda duruyor, ama şemalar arası okuma
    /// yapılmıyor: her proje yalnızca kendi şemasında işlem yapar. Bunun bilinçli bedeli envanterin
    /// iki yerde tutulması; kayması hâlinde otorite NOS+ dosyasıdır.
    ///
    /// Önceki hâl: `wwwroot/cable_catalog.json` hiçbir kodda okunmuyordu (ölü dosya) ve
    /// index.html içindeki gömülü liste "943 verinin tamamı" diyordu ama 10 kayıt içeriyordu.
    /// </remarks>
    public class KabloKatalog
    {
        public int Id { get; set; }

        public string Nosid { get; set; } = string.Empty;
        public string? SahaSehir { get; set; }
        public int? Uzunluk { get; set; }
        public string? LocationA { get; set; }
        public string? LocationB { get; set; }
    }
}
