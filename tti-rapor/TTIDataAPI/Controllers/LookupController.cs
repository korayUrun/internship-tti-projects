using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Dtos;
using TTIDataAPI.Models;

namespace TTIDataAPI.Controllers
{
    /// <summary>
    /// Arayüzün açılır listelerini besleyen sabitler ve kablo kataloğu.
    /// </summary>
    /// <remarks>
    /// Listeler arayüzde de sunucuda da elle yazıldığında birbirinden kayıyor ve hiçbir hata
    /// çıkmıyordu (arıza riski etiketi üç yerde üç farklıydı). Tek kaynak Sabitler sınıfı.
    /// </remarks>
    [Route("api/lookup")]
    [ApiController]
    [Authorize]
    public class LookupController : ControllerBase
    {
        private readonly AppDbContext _context;

        public LookupController(AppDbContext context) => _context = context;

        [HttpGet("riskler")]
        public ActionResult<IEnumerable<string>> Riskler() => Ok(Sabitler.RiskSeviyeleri);

        [HttpGet("statuler")]
        public ActionResult<IEnumerable<string>> Statuler() => Ok(Sabitler.Statuler);

        /// <summary>
        /// Proje kapsamları. Sabitler'den DEĞİL, tablodan okunur — yönetici listeye ekleme yapabiliyor.
        /// </summary>
        [HttpGet("kapsamlar")]
        public async Task<ActionResult<IEnumerable<string>>> Kapsamlar(CancellationToken iptal)
        {
            var kapsamlar = await _context.ProjeKapsamlari.AsNoTracking()
                .Where(x => x.Aktif)
                .OrderBy(x => x.Sira).ThenBy(x => x.Ad)
                .Select(x => x.Ad)
                .ToListAsync(iptal);

            return Ok(kapsamlar);
        }

        /// <summary>
        /// Açılır listeye yeni proje kapsamı ekler. Yalnızca yönetici.
        /// </summary>
        /// <remarks>
        /// Sahada listede olmayan bir kapsamla karşılaşıldığında eskiden kayıt açmanın yolu yoktu:
        /// liste koddaydı, yeni madde kod değişikliği ve yeni dağıtım gerektiriyordu.
        ///
        /// Ekleme ucu var ama SİLME ucu YOK: eski taleplerin `ProjeGuzergah` alanı bu adı düz metin
        /// olarak taşıyor, satırı silmek o kayıtları doğrulanamaz hâle getirirdi.
        /// </remarks>
        [HttpPost("kapsamlar")]
        [Authorize(Roles = Sabitler.RolYonetici)]
        public async Task<ActionResult<ProjeKapsami>> KapsamEkle(
            [FromBody] KapsamEkleIstegi istek, CancellationToken iptal)
        {
            var ad = (istek.Ad ?? string.Empty).Trim();
            if (string.IsNullOrWhiteSpace(ad))
            {
                return BadRequest(new { hata = "Kapsam adı boş olamaz." });
            }

            // Karşılaştırma büyük/küçük harf duyarsız: "Havai Güzergah" ile "havai güzergah" iki
            // ayrı seçenek olarak listede durursa kullanıcı hangisini seçtiğini bilemez ve
            // raporlar ikiye bölünür. Veritabanı locale=C olduğu için ILIKE kullanılıyor.
            var varOlan = await _context.ProjeKapsamlari.AsNoTracking()
                .FirstOrDefaultAsync(x => EF.Functions.ILike(x.Ad, ad), iptal);

            if (varOlan is not null)
            {
                // Pasife alınmış bir kapsam yeniden eklenmek isteniyorsa onu geri açmak, ikinci bir
                // satır yaratmaktan doğrudur.
                if (!varOlan.Aktif)
                {
                    var geriAcilan = await _context.ProjeKapsamlari
                        .FirstAsync(x => x.Id == varOlan.Id, iptal);
                    geriAcilan.Aktif = true;
                    await _context.SaveChangesAsync(iptal);
                    return Ok(geriAcilan);
                }

                return Conflict(new { hata = $"Bu kapsam zaten listede: {varOlan.Ad}" });
            }

            var sira = await _context.ProjeKapsamlari.AnyAsync(iptal)
                ? await _context.ProjeKapsamlari.MaxAsync(x => x.Sira, iptal) + 1
                : 1;

            var kapsam = new ProjeKapsami { Ad = ad, Sira = sira, Aktif = true };
            _context.ProjeKapsamlari.Add(kapsam);

            try
            {
                await _context.SaveChangesAsync(iptal);
            }
            catch (DbUpdateException)
            {
                // Benzersizlik kısıtı: iki yönetici aynı adı aynı anda ekledi. Yukarıdaki kontrol
                // ikisinde de geçmiş olabilir; çakışmayı asıl durduran veritabanı.
                return Conflict(new { hata = $"Bu kapsam zaten listede: {ad}" });
            }

            return Ok(kapsam);
        }

        /// <summary>NOSID araması ve otomatik doldurma için kablo kataloğu.</summary>
        [HttpGet("katalog")]
        public async Task<ActionResult<IEnumerable<KabloKatalog>>> Katalog(
            [FromQuery] string? ara, CancellationToken iptal)
        {
            var sorgu = _context.KabloKataloglari.AsNoTracking();

            if (!string.IsNullOrWhiteSpace(ara))
            {
                // ILIKE: locale=C ile açılmış veritabanında büyük/küçük harf duyarsız arama.
                var kalip = $"%{ara.Trim()}%";
                sorgu = sorgu.Where(x => EF.Functions.ILike(x.Nosid, kalip));
            }

            var sonuc = await sorgu
                .OrderBy(x => x.Nosid)
                .Take(200)   // arayüz otomatik tamamlama listesi; tüm envanteri göndermenin anlamı yok
                .ToListAsync(iptal);

            return Ok(sonuc);
        }
    }
}
