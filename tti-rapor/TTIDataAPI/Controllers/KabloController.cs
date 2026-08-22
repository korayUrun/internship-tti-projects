using ClosedXML.Excel;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Dtos;
using TTIDataAPI.Models;
using TTIDataAPI.Services;

namespace TTIDataAPI.Controllers
{
    [Route("api/kablo")]
    [ApiController]
    [Authorize]
    public class KabloController : ControllerBase
    {
        private readonly AppDbContext _context;
        private readonly FotografDeposu _fotograflar;
        private readonly GuzergahDeposu _guzergahlar;

        public KabloController(
            AppDbContext context, FotografDeposu fotograflar, GuzergahDeposu guzergahlar)
        {
            _context = context;
            _fotograflar = fotograflar;
            _guzergahlar = guzergahlar;
        }

        /// <summary>
        /// Yetki süzgeci. Yönetici her kaydı görür; saha kullanıcısı yalnızca kendi açtığı ya da
        /// son güncellediği kayıtları.
        /// </summary>
        /// <remarks>
        /// Süzgeç SORGUDA uygulanır, listeyi çekip bellekte filtrelemek yasak: süzgeci her uçta
        /// tekrarlamak gerekir, biri unutulduğunda hata sessizdir ve başkasının verisi sızar.
        /// Önceki hâlde süzgeç yalnızca tarayıcıdaydı — API'ye doğrudan istek atan herkes tüm
        /// kayıtları alabiliyordu.
        /// </remarks>
        private IQueryable<KabloTalep> YetkiliSorgu()
        {
            var sorgu = _context.KabloTalepleri.AsNoTracking();
            if (User.IsInRole(Sabitler.RolYonetici)) return sorgu;

            var kullanici = User.Identity?.Name ?? string.Empty;
            return sorgu.Where(x => x.Olusturan == kullanici || x.SonDegistiren == kullanici);
        }

        [HttpGet]
        public async Task<ActionResult<IEnumerable<TalepYaniti>>> Listele(CancellationToken iptal)
        {
            // xmin (Surum) shadow bir sütun; AsNoTracking sorgusunda ancak PROJEKSİYONLA okunabilir.
            // Bu yüzden Include yerine Gecmis'i de projeksiyonda topluyoruz.
            var satirlar = await YetkiliSorgu()
                .OrderByDescending(x => x.OlusturulmaTarihi)
                .Select(x => new { Talep = x, Surum = EF.Property<uint>(x, "xmin"), Gecmis = x.Gecmis.ToList() })
                .ToListAsync(iptal);

            return Ok(satirlar.Select(s => Donustur(s.Talep, s.Surum, s.Gecmis)).ToList());
        }

        [HttpGet("{id:int}")]
        public async Task<ActionResult<TalepYaniti>> Getir(int id, CancellationToken iptal)
        {
            var satir = await YetkiliSorgu()
                .Where(x => x.Id == id)
                .Select(x => new { Talep = x, Surum = EF.Property<uint>(x, "xmin"), Gecmis = x.Gecmis.ToList() })
                .FirstOrDefaultAsync(iptal);

            if (satir is null) return NotFound(new { hata = "Talep bulunamadı." });
            return Ok(Donustur(satir.Talep, satir.Surum, satir.Gecmis));
        }

        [HttpPost]
        public async Task<ActionResult<TalepYaniti>> Olustur(
            [FromBody] TalepOlusturIstegi istek, CancellationToken iptal)
        {
            if (!Sabitler.RiskSeviyeleri.Contains(istek.ArizaRiski))
            {
                return BadRequest(new { hata = $"Geçersiz arıza riski: {istek.ArizaRiski}" });
            }

            // Proje kapsamı doludur ama listede yoksa reddet — risk/statü ile aynı katılık.
            // Liste artık tabloda (yönetici ekleyebiliyor), bu yüzden kontrol de oradan yapılır.
            if (!string.IsNullOrWhiteSpace(istek.ProjeGuzergah))
            {
                var kapsamVar = await _context.ProjeKapsamlari
                    .AnyAsync(x => x.Ad == istek.ProjeGuzergah && x.Aktif, iptal);
                if (!kapsamVar)
                {
                    return BadRequest(new { hata = $"Geçersiz proje kapsamı: {istek.ProjeGuzergah}" });
                }
            }

            // NOSID kataloğa karşı doğrulanır: katalog envanterin otoritesidir, serbest metin değil.
            // Katalogda olmayan yeni kablolar önce yönetici Excel yükleme ucundan eklenmelidir.
            var nosidVar = await _context.KabloKataloglari
                .AnyAsync(x => x.Nosid == istek.Nosid, iptal);
            if (!nosidVar)
            {
                return BadRequest(new
                {
                    hata = $"NOSID kataloğda bulunamadı: {istek.Nosid}. " +
                           "Önce kablo kataloğunu güncelleyin.",
                });
            }

            var kullanici = User.Identity?.Name ?? string.Empty;
            var simdi = DateTime.UtcNow;

            var talep = new KabloTalep
            {
                Olusturan = kullanici,
                Nosid = istek.Nosid,
                LocationA = istek.LocationA,
                LocationB = istek.LocationB,
                ProjeGuzergah = istek.ProjeGuzergah,
                SahaSehir = istek.SahaSehir,
                EkAraligi = istek.EkAraligi,
                GuzergahUzunlugu = istek.GuzergahUzunlugu,
                ArizaRiski = istek.ArizaRiski,
                IlkAciklama = istek.IlkAciklama,
                Statu = Sabitler.StatuAcik,
                SonAciklama = istek.IlkAciklama,
                SonDegistiren = kullanici,
                OlusturulmaTarihi = simdi,
                SonDegistirilmeTarihi = simdi,
            };

            _context.KabloTalepleri.Add(talep);
            await _context.SaveChangesAsync(iptal);

            return CreatedAtAction(
                nameof(Getir), new { id = talep.Id }, Donustur(talep, Surum(talep), talep.Gecmis));
        }

        [HttpPut("{id:int}")]
        public async Task<ActionResult<TalepYaniti>> Guncelle(
            int id, [FromBody] TalepGuncelleIstegi istek, CancellationToken iptal)
        {
            if (!Sabitler.Statuler.Contains(istek.Statu))
            {
                return BadRequest(new { hata = $"Geçersiz statü: {istek.Statu}" });
            }

            // Yetki süzgeci burada da geçerli: başkasının kaydını güncelleyemez.
            var talep = await YetkiliSorgu().AsTracking()
                .Include(x => x.Gecmis)
                .FirstOrDefaultAsync(x => x.Id == id, iptal);

            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            var kullanici = User.Identity?.Name ?? "Sistem";
            var eskiStatu = talep.Statu;

            talep.Statu = istek.Statu;
            talep.SonAciklama = istek.SonAciklama;
            talep.SonDegistiren = kullanici;
            talep.SonDegistirilmeTarihi = DateTime.UtcNow;

            talep.Gecmis.Add(new GuncellemeGecmisi
            {
                KabloTalepId = id,
                Degistiren = kullanici,
                EskiStatu = eskiStatu,
                YeniStatu = istek.Statu,
                Aciklama = istek.SonAciklama,
                Tarih = DateTime.UtcNow,
            });

            // Kayıp güncellemeyi asıl yakalayan satır: WHERE xmin = istemcinin gördüğü sürüm.
            // İstemci düzenlemeye başladıktan sonra kaydı başkası değiştirdiyse xmin artık farklıdır,
            // UPDATE 0 satır etkiler ve DbUpdateConcurrencyException düşer. Surum=0 ise (eski/eksik
            // istemci) eski davranışa düşülür ve eşzamanlılık kontrolü uygulanmaz.
            if (istek.Surum != 0)
            {
                _context.Entry(talep).Property("xmin").OriginalValue = istek.Surum;
            }

            try
            {
                await _context.SaveChangesAsync(iptal);
            }
            catch (DbUpdateConcurrencyException)
            {
                // xmin eşleşmedi: başka biri aynı kaydı bu arada değiştirdi. Sessizce ezmek yerine
                // istemciyi yeniden yüklemeye zorluyoruz.
                return Conflict(new
                {
                    hata = "Bu kayıt siz düzenlerken başka bir kullanıcı tarafından değiştirildi. " +
                           "Listeyi yenileyip tekrar deneyin.",
                });
            }

            return Ok(Donustur(talep, Surum(talep), talep.Gecmis));
        }

        [HttpDelete("{id:int}")]
        public async Task<IActionResult> Sil(int id, CancellationToken iptal)
        {
            var talep = await YetkiliSorgu().AsTracking()
                .FirstOrDefaultAsync(x => x.Id == id, iptal);

            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            var fotograf = talep.FotografYolu;
            var guzergah = talep.GuzergahDosyaYolu;

            _context.KabloTalepleri.Remove(talep);   // geçmiş satırları cascade ile gider
            await _context.SaveChangesAsync(iptal);

            // Dosyalar satır gittikten SONRA silinir: silme başarısız olsaydı, kaydı duran ama
            // dosyası olmayan bir talep kalırdı. Ters sıra veri kaybı, bu sıra artık dosya üretir.
            _fotograflar.Sil(fotograf);
            _guzergahlar.Sil(guzergah);
            return NoContent();
        }

        [HttpPost("{id:int}/fotograf")]
        [RequestSizeLimit(10 * 1024 * 1024)]
        public async Task<ActionResult<TalepYaniti>> FotografYukle(
            int id, IFormFile dosya, CancellationToken iptal)
        {
            if (dosya is null || dosya.Length == 0)
            {
                return BadRequest(new { hata = "Dosya boş." });
            }

            var talep = await YetkiliSorgu().AsTracking()
                .FirstOrDefaultAsync(x => x.Id == id, iptal);

            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            string yeniYol;
            try
            {
                yeniYol = await _fotograflar.KaydetAsync(id, dosya, iptal);
            }
            catch (ArgumentException hata)
            {
                return BadRequest(new { hata = hata.Message });
            }

            var eskiYol = talep.FotografYolu;
            talep.FotografYolu = yeniYol;
            talep.SonDegistirilmeTarihi = DateTime.UtcNow;
            await _context.SaveChangesAsync(iptal);

            // Yeni dosya kayda bağlandıktan SONRA eskisi silinir; sıra ters olsaydı kayıt
            // başarısız olduğunda hem eski hem yeni fotoğraf kaybolurdu.
            if (!string.IsNullOrEmpty(eskiYol) && eskiYol != yeniYol) _fotograflar.Sil(eskiYol);

            return Ok(Donustur(talep, Surum(talep), talep.Gecmis));
        }

        [HttpGet("{id:int}/fotograf")]
        public async Task<IActionResult> FotografIndir(int id, CancellationToken iptal)
        {
            var talep = await YetkiliSorgu().FirstOrDefaultAsync(x => x.Id == id, iptal);
            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            var tamYol = _fotograflar.TamYol(talep.FotografYolu);
            if (tamYol is null) return NotFound(new { hata = "Bu talebe ait fotoğraf yok." });

            var tur = Path.GetExtension(tamYol).ToLowerInvariant() switch
            {
                ".png" => "image/png",
                ".webp" => "image/webp",
                _ => "image/jpeg",
            };

            return PhysicalFile(tamYol, tur);
        }

        [HttpDelete("{id:int}/fotograf")]
        public async Task<IActionResult> FotografSil(int id, CancellationToken iptal)
        {
            var talep = await YetkiliSorgu().AsTracking()
                .FirstOrDefaultAsync(x => x.Id == id, iptal);

            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            var yol = talep.FotografYolu;
            talep.FotografYolu = null;
            await _context.SaveChangesAsync(iptal);

            _fotograflar.Sil(yol);
            return NoContent();
        }

        /// <summary>
        /// Talebin güzergâh dosyasını (.kml/.kmz) yükler; varsa öncekinin yerine geçer.
        /// </summary>
        /// <remarks>
        /// Zorunlu DEĞİL: talep dosyasız da açılabilir ve dosya sonradan da eklenebilir. Kaydın
        /// kimlik alanları gönderimden sonra dondurulur ama bu uç — fotoğraf ucu gibi — açık kalır;
        /// sahadan dönen kullanıcının unuttuğu dosyayı ekleyebilmesi gerekiyor.
        /// </remarks>
        [HttpPost("{id:int}/guzergah")]
        [RequestSizeLimit(10 * 1024 * 1024)]
        public async Task<ActionResult<TalepYaniti>> GuzergahYukle(
            int id, IFormFile dosya, CancellationToken iptal)
        {
            if (dosya is null || dosya.Length == 0)
            {
                return BadRequest(new { hata = "Dosya boş." });
            }

            var talep = await YetkiliSorgu().AsTracking()
                .FirstOrDefaultAsync(x => x.Id == id, iptal);

            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            string yeniYol;
            try
            {
                yeniYol = await _guzergahlar.KaydetAsync(id, dosya, iptal);
            }
            catch (ArgumentException hata)
            {
                return BadRequest(new { hata = hata.Message });
            }

            var eskiYol = talep.GuzergahDosyaYolu;
            talep.GuzergahDosyaYolu = yeniYol;
            // Özgün ad yalnızca gösterim/indirme içindir; diskteki ad GUID. Yol ayracı içeren bir
            // ad gelirse (bazı tarayıcılar tam yol gönderir) yalnızca son parçası saklanır.
            talep.GuzergahDosyaAdi = Path.GetFileName(dosya.FileName);
            talep.SonDegistirilmeTarihi = DateTime.UtcNow;
            await _context.SaveChangesAsync(iptal);

            // Yeni dosya kayda bağlandıktan SONRA eskisi silinir; sıra ters olsaydı kayıt
            // başarısız olduğunda hem eski hem yeni dosya kaybolurdu.
            if (!string.IsNullOrEmpty(eskiYol) && eskiYol != yeniYol) _guzergahlar.Sil(eskiYol);

            return Ok(Donustur(talep, Surum(talep), talep.Gecmis));
        }

        [HttpGet("{id:int}/guzergah")]
        public async Task<IActionResult> GuzergahIndir(int id, CancellationToken iptal)
        {
            var talep = await YetkiliSorgu().FirstOrDefaultAsync(x => x.Id == id, iptal);
            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            var tamYol = _guzergahlar.TamYol(talep.GuzergahDosyaYolu);
            if (tamYol is null) return NotFound(new { hata = "Bu talebe ait güzergah dosyası yok." });

            // Kullanıcı dosyayı yüklediği adla geri alır; diskteki GUID adı dışarı sızmaz.
            var indirmeAdi = string.IsNullOrWhiteSpace(talep.GuzergahDosyaAdi)
                ? $"Guzergah_{talep.Id}{Path.GetExtension(tamYol)}"
                : talep.GuzergahDosyaAdi;

            return PhysicalFile(tamYol, GuzergahDeposu.IcerikTuru(tamYol), indirmeAdi);
        }

        [HttpDelete("{id:int}/guzergah")]
        public async Task<IActionResult> GuzergahSil(int id, CancellationToken iptal)
        {
            var talep = await YetkiliSorgu().AsTracking()
                .FirstOrDefaultAsync(x => x.Id == id, iptal);

            if (talep is null) return NotFound(new { hata = "Talep bulunamadı." });

            var yol = talep.GuzergahDosyaYolu;
            talep.GuzergahDosyaYolu = null;
            talep.GuzergahDosyaAdi = null;
            await _context.SaveChangesAsync(iptal);

            _guzergahlar.Sil(yol);
            return NoContent();
        }

        [HttpGet("excel-indir")]
        public async Task<IActionResult> ExcelIndir(CancellationToken iptal)
        {
            var talepler = await YetkiliSorgu()
                .OrderByDescending(x => x.OlusturulmaTarihi)
                .ToListAsync(iptal);

            using var workbook = new XLWorkbook();
            var sayfa = workbook.Worksheets.Add("Saha Talepleri");

            string[] basliklar =
            {
                "ID", "Oluşturan", "NOSID", "Saha / Şehir", "Location A", "Location B",
                "Proje Güzergah", "Ek Aralığı", "Uzunluk (m)", "Arıza Riski", "Statü",
                "Güzergah Dosyası", "İlk Açıklama", "Son Açıklama",
                "Oluşturulma", "Son Değiştirilme", "Son Değiştiren",
            };

            for (var s = 0; s < basliklar.Length; s++)
            {
                sayfa.Cell(1, s + 1).Value = basliklar[s];
            }
            sayfa.Row(1).Style.Font.Bold = true;

            for (var i = 0; i < talepler.Count; i++)
            {
                var t = talepler[i];
                var satir = i + 2;

                sayfa.Cell(satir, 1).Value = t.Id;
                sayfa.Cell(satir, 2).Value = t.Olusturan;
                sayfa.Cell(satir, 3).Value = t.Nosid;
                sayfa.Cell(satir, 4).Value = t.SahaSehir;
                sayfa.Cell(satir, 5).Value = t.LocationA;
                sayfa.Cell(satir, 6).Value = t.LocationB;
                sayfa.Cell(satir, 7).Value = t.ProjeGuzergah;
                sayfa.Cell(satir, 8).Value = t.EkAraligi;
                // Sayı hücresi gerçek sayı olarak yazılır, metin olarak değil.
                if (t.GuzergahUzunlugu.HasValue) sayfa.Cell(satir, 9).Value = t.GuzergahUzunlugu.Value;
                sayfa.Cell(satir, 10).Value = t.ArizaRiski;
                sayfa.Cell(satir, 11).Value = t.Statu;
                sayfa.Cell(satir, 12).Value = t.GuzergahDosyaAdi;
                sayfa.Cell(satir, 13).Value = t.IlkAciklama;
                sayfa.Cell(satir, 14).Value = t.SonAciklama;
                // UTC saklanır, çıktıda yerel saat gösterilir — dosyayı okuyan kişi saha saatini bekler.
                sayfa.Cell(satir, 15).Value = Yerel(t.OlusturulmaTarihi);
                sayfa.Cell(satir, 16).Value = Yerel(t.SonDegistirilmeTarihi);
                sayfa.Cell(satir, 17).Value = t.SonDegistiren;
            }

            sayfa.Column(15).Style.DateFormat.Format = "dd.MM.yyyy HH:mm";
            sayfa.Column(16).Style.DateFormat.Format = "dd.MM.yyyy HH:mm";
            sayfa.Columns().AdjustToContents();

            using var akis = new MemoryStream();
            workbook.SaveAs(akis);

            return File(
                akis.ToArray(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "Saha_Talepleri.xlsx");
        }

        /// <summary>
        /// Kablo kataloğunu Excel'den günceller. Yalnızca yönetici. NOS+ envanterinin dışa
        /// aktarımıyla (cable_data_*.xlsx) aynı biçimi bekler.
        /// </summary>
        /// <remarks>
        /// Insert-only: yalnızca kataloğda henüz OLMAYAN NOSID'ler eklenir; var olan kayıtlar
        /// güncellenmez ve HİÇBİR kayıt silinmez (kullanıcı kararı). Ayrıştırma hatasında (eksik
        /// sayfa/sütun, boş/tekrar eden NOSID) hiçbir şey yazılmaz.
        /// </remarks>
        [HttpPost("katalog-yukle")]
        [Authorize(Roles = Sabitler.RolYonetici)]
        [RequestSizeLimit(10 * 1024 * 1024)]
        public async Task<IActionResult> KatalogYukle(IFormFile dosya, CancellationToken iptal)
        {
            if (dosya is null || dosya.Length == 0)
            {
                return BadRequest(new { hata = "Dosya boş." });
            }

            List<KabloKatalog> okunan;
            try
            {
                // ClosedXML akışta konumlanabilir bir kaynak ister; multipart akışı önce belleğe alınır.
                using var bellek = new MemoryStream();
                await dosya.CopyToAsync(bellek, iptal);
                bellek.Position = 0;
                okunan = KatalogXlsxOkuyucu.Oku(bellek);
            }
            catch (KatalogAyristirmaHatasi hata)
            {
                return BadRequest(new { hata = hata.Message });
            }

            var mevcut = await _context.KabloKataloglari
                .Select(x => x.Nosid)
                .ToListAsync(iptal);
            var mevcutSet = new HashSet<string>(mevcut, StringComparer.Ordinal);

            var eklenecek = okunan.Where(k => !mevcutSet.Contains(k.Nosid)).ToList();
            if (eklenecek.Count > 0)
            {
                _context.KabloKataloglari.AddRange(eklenecek);
                await _context.SaveChangesAsync(iptal);
            }

            return Ok(new
            {
                toplam = okunan.Count,
                eklenen = eklenecek.Count,
                atlanan = okunan.Count - eklenecek.Count,
            });
        }

        private static DateTime Yerel(DateTime utc) =>
            TimeZoneInfo.ConvertTimeFromUtc(
                DateTime.SpecifyKind(utc, DateTimeKind.Utc),
                Zaman.Istanbul);

        /// <summary>Takip edilen bir kaydın anlık sürümünü (xmin) okur. Kayıt izleniyor olmalı.</summary>
        private uint Surum(KabloTalep t) => (uint)_context.Entry(t).Property("xmin").CurrentValue!;

        private static TalepYaniti Donustur(
            KabloTalep t, uint surum, IEnumerable<GuncellemeGecmisi> gecmis) => new()
        {
            Id = t.Id,
            Olusturan = t.Olusturan,
            Nosid = t.Nosid,
            LocationA = t.LocationA,
            LocationB = t.LocationB,
            ProjeGuzergah = t.ProjeGuzergah,
            SahaSehir = t.SahaSehir,
            EkAraligi = t.EkAraligi,
            GuzergahUzunlugu = t.GuzergahUzunlugu,
            ArizaRiski = t.ArizaRiski,
            FotografUrl = string.IsNullOrEmpty(t.FotografYolu) ? null : $"/api/kablo/{t.Id}/fotograf",
            GuzergahUrl = string.IsNullOrEmpty(t.GuzergahDosyaYolu)
                ? null
                : $"/api/kablo/{t.Id}/guzergah",
            GuzergahDosyaAdi = t.GuzergahDosyaAdi,
            IlkAciklama = t.IlkAciklama,
            Statu = t.Statu,
            SonAciklama = t.SonAciklama,
            SonDegistiren = t.SonDegistiren,
            OlusturulmaTarihi = t.OlusturulmaTarihi,
            SonDegistirilmeTarihi = t.SonDegistirilmeTarihi,
            Surum = surum,
            Gecmis = gecmis
                .OrderByDescending(g => g.Tarih)
                .Select(g => new GecmisYaniti
                {
                    Id = g.Id,
                    Degistiren = g.Degistiren,
                    EskiStatu = g.EskiStatu,
                    YeniStatu = g.YeniStatu,
                    Aciklama = g.Aciklama,
                    Tarih = g.Tarih,
                })
                .ToList(),
        };
    }

    /// <summary>Saat dilimi kimliği işletim sistemine göre değişir; tek yerde çözülür.</summary>
    internal static class Zaman
    {
        internal static readonly TimeZoneInfo Istanbul = Bul();

        private static TimeZoneInfo Bul()
        {
            // Linux/macOS "Europe/Istanbul", Windows "Turkey Standard Time" bekler. .NET 8'de
            // ikisi de çoğu platformda çözülür ama garanti değil; UTC'ye düşmek çökmekten iyidir.
            foreach (var kimlik in new[] { "Europe/Istanbul", "Turkey Standard Time" })
            {
                try { return TimeZoneInfo.FindSystemTimeZoneById(kimlik); }
                catch (TimeZoneNotFoundException) { }
                catch (InvalidTimeZoneException) { }
            }
            return TimeZoneInfo.Utc;
        }
    }
}
