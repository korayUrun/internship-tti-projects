using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Models;
using TTIDataAPI.Services;

namespace TTIDataAPI.Controllers
{
    /// <summary>
    /// Konteyner sağlık kontrolünün çağırdığı uç. TTI'nin /api/saglik ucuyla simetrik.
    /// </summary>
    [Route("api/saglik")]
    [ApiController]
    [AllowAnonymous]
    public class SaglikController : ControllerBase
    {
        private readonly AppDbContext _context;
        private readonly FotografDeposu _fotograflar;

        public SaglikController(AppDbContext context, FotografDeposu fotograflar)
        {
            _context = context;
            _fotograflar = fotograflar;
        }

        [HttpGet]
        public async Task<IActionResult> Getir(CancellationToken iptal)
        {
            var veritabani = await _context.Database.CanConnectAsync(iptal);
            var yuklemeYazilabilir = _fotograflar.YazilabilirMi();

            var yanit = new
            {
                durum = veritabani ? "ayakta" : "bozuk",
                // Çalışan sürüm dışarıdan görünmeli: imaj etiketi değişmezdir, ama hangi etiketin
                // ayakta olduğunu sormanın tek güvenilir yolu uygulamanın kendisine sormaktır.
                // Konteynerde SURUM ortam değişkeni compose'daki imaj etiketiyle aynı değeri taşır.
                surum = Environment.GetEnvironmentVariable("SURUM") ?? "bilinmiyor",
                veritabani = veritabani ? "ayakta" : "erisilemiyor",
                veritabaniAdi = _context.Database.GetDbConnection().Database,
                sema = AppDbContext.Sema,
                yuklemeYazilabilir,
            };

            // Sağlıksızken 200 dönmek healthcheck'i anlamsız kılar: konteyner "healthy" görünür
            // ve arıza ancak ilk istekte fark edilir.
            return veritabani ? Ok(yanit) : StatusCode(StatusCodes.Status503ServiceUnavailable, yanit);
        }
    }
}
