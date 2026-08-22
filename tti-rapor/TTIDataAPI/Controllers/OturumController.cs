using System.Security.Claims;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Dtos;
using TTIDataAPI.Models;

namespace TTIDataAPI.Controllers
{
    [Route("api/oturum")]
    [ApiController]
    public class OturumController : ControllerBase
    {
        private readonly AppDbContext _context;
        private readonly ILogger<OturumController> _gunluk;

        public OturumController(AppDbContext context, ILogger<OturumController> gunluk)
        {
            _context = context;
            _gunluk = gunluk;
        }

        [HttpPost("giris")]
        [AllowAnonymous]
        public async Task<ActionResult<OturumYaniti>> Giris(
            [FromBody] GirisIstegi istek, CancellationToken iptal)
        {
            var kullanici = await _context.Kullanicilar
                .FirstOrDefaultAsync(x => x.KullaniciAdi == istek.KullaniciAdi && x.Aktif, iptal);

            // Kullanıcı yok ile parola yanlış AYNI yanıtı döner: farklı yanıt vermek, hangi
            // kullanıcı adlarının var olduğunu deneme yanılmayla saydırırdı.
            if (kullanici is null || !BCrypt.Net.BCrypt.Verify(istek.Parola, kullanici.ParolaOzeti))
            {
                _gunluk.LogWarning("Başarısız giriş denemesi: {Kullanici}", istek.KullaniciAdi);
                return Unauthorized(new { hata = "Kullanıcı adı veya parola hatalı." });
            }

            await OturumAcAsync(kullanici);
            return Ok(Donustur(kullanici));
        }

        [HttpPost("cikis")]
        [Authorize]
        public async Task<IActionResult> Cikis()
        {
            await HttpContext.SignOutAsync(CookieAuthenticationDefaults.AuthenticationScheme);
            return NoContent();
        }

        /// <summary>Sayfa yenilendiğinde arayüzün oturumu geri kurması için.</summary>
        [HttpGet("ben")]
        [Authorize]
        public async Task<ActionResult<OturumYaniti>> Ben(CancellationToken iptal)
        {
            var ad = User.Identity?.Name ?? string.Empty;
            var kullanici = await _context.Kullanicilar.AsNoTracking()
                .FirstOrDefaultAsync(x => x.KullaniciAdi == ad && x.Aktif, iptal);

            if (kullanici is null) return Unauthorized(new { hata = "Oturum geçersiz." });
            return Ok(Donustur(kullanici));
        }

        [HttpPost("parola")]
        [Authorize]
        public async Task<ActionResult<OturumYaniti>> ParolaDegistir(
            [FromBody] ParolaDegistirIstegi istek, CancellationToken iptal)
        {
            var ad = User.Identity?.Name ?? string.Empty;
            var kullanici = await _context.Kullanicilar
                .FirstOrDefaultAsync(x => x.KullaniciAdi == ad && x.Aktif, iptal);

            if (kullanici is null) return Unauthorized(new { hata = "Oturum geçersiz." });

            if (!BCrypt.Net.BCrypt.Verify(istek.MevcutParola, kullanici.ParolaOzeti))
            {
                return BadRequest(new { hata = "Mevcut parola hatalı." });
            }

            if (istek.MevcutParola == istek.YeniParola)
            {
                return BadRequest(new { hata = "Yeni parola eskisiyle aynı olamaz." });
            }

            kullanici.ParolaOzeti = BCrypt.Net.BCrypt.HashPassword(istek.YeniParola);
            kullanici.IlkGirisMi = false;
            await _context.SaveChangesAsync(iptal);

            // IlkGirisMi bir claim olarak çerezde duruyor; oturum tazelenmezse arayüz parola
            // değiştirme ekranından çıkamaz.
            await OturumAcAsync(kullanici);

            return Ok(Donustur(kullanici));
        }

        private async Task OturumAcAsync(Kullanici kullanici)
        {
            var kimlik = new ClaimsIdentity(
                new[]
                {
                    new Claim(ClaimTypes.Name, kullanici.KullaniciAdi),
                    new Claim(ClaimTypes.Role, kullanici.Rol),
                    new Claim("adSoyad", kullanici.AdSoyad),
                    new Claim(
                        Services.ParolaDegistirmeFiltresi.IlkGirisClaim,
                        kullanici.IlkGirisMi ? "true" : "false"),
                    // Kullanıcı yönetimi yetkisi çerezde taşınır; uçlar her istekte veritabanına
                    // sormak yerine bunu okur (rol claim'iyle aynı desen).
                    new Claim(SistemYoneticisiClaim, kullanici.SistemYoneticisiMi ? "true" : "false"),
                },
                CookieAuthenticationDefaults.AuthenticationScheme);

            await HttpContext.SignInAsync(
                CookieAuthenticationDefaults.AuthenticationScheme,
                new ClaimsPrincipal(kimlik),
                new AuthenticationProperties { IsPersistent = false });
        }

        private static OturumYaniti Donustur(Kullanici k) => new()
        {
            KullaniciAdi = k.KullaniciAdi,
            AdSoyad = k.AdSoyad,
            Rol = k.Rol,
            IlkGirisMi = k.IlkGirisMi,
            SistemYoneticisiMi = k.SistemYoneticisiMi,
        };

        /// <summary>Kullanıcı yönetimi yetkisini taşıyan claim; <see cref="KullaniciController"/> okur.</summary>
        internal const string SistemYoneticisiClaim = "sistemYoneticisi";
    }
}
