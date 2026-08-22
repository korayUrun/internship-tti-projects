using System.Globalization;
using ClosedXML.Excel;
using TTIDataAPI.Models;

namespace TTIDataAPI.Services
{
    /// <summary>
    /// Kablo envanteri (.xlsx) → <see cref="KabloKatalog"/> okuyucusu.
    /// </summary>
    /// <remarks>
    /// Kaynak, TTI Saha Hakediş tarafının kullandığı NOS+ dışa aktarımıyla (cable_data_*.xlsx) AYNI
    /// biçimdedir; `cable` sayfasında 80'den fazla sütunun yalnızca birkaçı okunur. LocationA/B'nin
    /// nasıl kurulduğu (segment sırası, ayraçlar, "-"→boş sadeleştirmesi) hakediş `KonumYolu` kuralıyla
    /// BİREBİR aynıdır; iki taraf aynı envanteri farklı gösterirse eşleştirme/karşılaştırma bozulur.
    ///
    /// Fail-loud: eksik sayfa/sütun, boş NOSID veya dosya içinde tekrar eden NOSID durumunda hiç
    /// yazmadan <see cref="KatalogAyristirmaHatasi"/> fırlatır. Sessizce atlanan satır, sessizce eksik
    /// katalog demektir.
    /// </remarks>
    public static class KatalogXlsxOkuyucu
    {
        private const string SayfaKablo = "cable";

        private const string SutunNosid = "nosid";
        private const string SutunSehirA = "cityA";
        private const string SutunUzunluk = "length";

        /// <summary>Konum yolu segmentleri, envanterin sütun sırasında. Sütun adı = segment + "A"/"B".</summary>
        private static readonly string[] KonumAlanlari =
            { "country", "city", "address", "building", "floor", "room", "cage", "footprint", "rackspace" };

        private const string Ayrac = " >> ";

        /// <summary>Bina ve kat tek segmentte birleşir: "AND Plaza > Ground Floor".</summary>
        private const string BinaKatAyraci = " > ";

        public static List<KabloKatalog> Oku(Stream akis)
        {
            XLWorkbook workbook;
            try
            {
                workbook = new XLWorkbook(akis);
            }
            catch (Exception ex) when (ex is not KatalogAyristirmaHatasi)
            {
                // Geçersiz/bozuk dosyada ClosedXML kendi istisnasını fırlatır; kullanıcıya 500 değil
                // 400 dönebilmek için anlamlı bir ayrıştırma hatasına çeviriyoruz.
                throw new KatalogAyristirmaHatasi("Dosya geçerli bir Excel (.xlsx) değil ya da bozuk.");
            }

            using var _kapat = workbook;

            var sayfa = workbook.Worksheets
                .FirstOrDefault(w => string.Equals(w.Name, SayfaKablo, StringComparison.OrdinalIgnoreCase))
                ?? throw new KatalogAyristirmaHatasi($"'{SayfaKablo}' sayfası yok.");

            var baslikSatiri = sayfa.FirstRowUsed()
                ?? throw new KatalogAyristirmaHatasi($"'{SayfaKablo}' sayfasında başlık satırı yok.");

            var sutunlar = SutunIndeksleri(baslikSatiri);

            var kayitlar = new List<KabloKatalog>();
            var gorulen = new HashSet<string>(StringComparer.Ordinal);

            var sonSatir = sayfa.LastRowUsed()!.RowNumber();
            for (var satirNo = baslikSatiri.RowNumber() + 1; satirNo <= sonSatir; satirNo++)
            {
                var satir = sayfa.Row(satirNo);

                var nosid = Deger(satir, sutunlar, SutunNosid);
                if (nosid is null)
                {
                    if (SatirBos(satir, sutunlar)) continue;
                    throw new KatalogAyristirmaHatasi($"Satır {satirNo}: '{SutunNosid}' boş.");
                }

                if (!gorulen.Add(nosid))
                {
                    throw new KatalogAyristirmaHatasi(
                        $"Satır {satirNo}: '{nosid}' envanterde birden fazla kez geçiyor.");
                }

                kayitlar.Add(new KabloKatalog
                {
                    Nosid = nosid,
                    SahaSehir = Deger(satir, sutunlar, SutunSehirA),
                    Uzunluk = Uzunluk(satir, sutunlar),
                    LocationA = KonumYolu(satir, sutunlar, "A"),
                    LocationB = KonumYolu(satir, sutunlar, "B"),
                });
            }

            if (kayitlar.Count == 0)
            {
                throw new KatalogAyristirmaHatasi($"'{SayfaKablo}' sayfasında veri satırı yok.");
            }

            return kayitlar;
        }

        /// <summary>Beklenen başlıkları büyük/küçük harf duyarsız bul; eksik varsa fail-loud.</summary>
        private static Dictionary<string, int> SutunIndeksleri(IXLRow baslikSatiri)
        {
            var beklenen = new List<string> { SutunNosid, SutunUzunluk };
            foreach (var yon in new[] { "A", "B" })
            {
                foreach (var alan in KonumAlanlari) beklenen.Add(alan + yon);
            }
            beklenen = beklenen.Distinct(StringComparer.OrdinalIgnoreCase).ToList();

            var bulunan = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
            var sonSutun = baslikSatiri.LastCellUsed()?.Address.ColumnNumber ?? 0;
            for (var sutunNo = 1; sutunNo <= sonSutun; sutunNo++)
            {
                var baslik = Sadelestir(baslikSatiri.Cell(sutunNo).GetString());
                if (baslik is null) continue;
                var eslesen = beklenen.FirstOrDefault(b => b.Equals(baslik, StringComparison.OrdinalIgnoreCase));
                if (eslesen is not null && !bulunan.ContainsKey(eslesen)) bulunan[eslesen] = sutunNo;
            }

            var eksik = beklenen.Where(b => !bulunan.ContainsKey(b)).ToList();
            if (eksik.Count > 0)
            {
                throw new KatalogAyristirmaHatasi(
                    $"'{SayfaKablo}' sayfasında eksik sütun: {string.Join(", ", eksik)}");
            }

            return bulunan;
        }

        /// <summary>Bir ucun konum yolu; hiçbir segment yoksa null (B ucu boş olabilir).</summary>
        private static string? KonumYolu(IXLRow satir, Dictionary<string, int> sutunlar, string yon)
        {
            string? Segment(string alan) => Deger(satir, sutunlar, alan + yon);

            var binaKat = new[] { Segment("building"), Segment("floor") }
                .Where(s => s is not null)
                .ToList();
            var binaKatMetni = binaKat.Count > 0 ? string.Join(BinaKatAyraci, binaKat) : null;

            var segmentler = new[]
            {
                Segment("country"),
                Segment("city"),
                Segment("address"),
                binaKatMetni,
                Segment("room"),
                Segment("cage"),
                Segment("footprint"),
                Segment("rackspace"),
            }.Where(s => s is not null).ToList();

            return segmentler.Count > 0 ? string.Join(Ayrac, segmentler) : null;
        }

        private static int? Uzunluk(IXLRow satir, Dictionary<string, int> sutunlar)
        {
            var ham = Deger(satir, sutunlar, SutunUzunluk);
            if (ham is null) return null;
            // Hücre sayısal geldiğinde "150" ya da "150.0" olabilir; ikisini de tam sayıya indir.
            if (double.TryParse(ham, NumberStyles.Any, CultureInfo.InvariantCulture, out var sayi))
            {
                return (int)Math.Round(sayi);
            }
            return null;
        }

        private static string? Deger(IXLRow satir, Dictionary<string, int> sutunlar, string sutunAdi) =>
            Sadelestir(satir.Cell(sutunlar[sutunAdi]).GetString());

        private static bool SatirBos(IXLRow satir, Dictionary<string, int> sutunlar) =>
            sutunlar.Values.All(c => Sadelestir(satir.Cell(c).GetString()) is null);

        /// <summary>
        /// Envanterde boş yerine "-" kullanıldığı için o da boş sayılır; iç boşluklar teke iner.
        /// Büyük/küçük harf dönüşümü YAPILMAZ ("İzmir".ToLower() birleşik karakter üretip eşleşmeyi bozar).
        /// </summary>
        private static string? Sadelestir(string? ham)
        {
            if (ham is null) return null;
            var sade = System.Text.RegularExpressions.Regex.Replace(ham.Trim(), "\\s+", " ");
            return sade.Length > 0 && sade != "-" ? sade : null;
        }
    }

    /// <summary>Excel ayrıştırma başarısız oldu; çağıran 400 döndürür ve hiçbir şey yazmaz.</summary>
    public class KatalogAyristirmaHatasi : Exception
    {
        public KatalogAyristirmaHatasi(string mesaj) : base(mesaj) { }
    }
}
