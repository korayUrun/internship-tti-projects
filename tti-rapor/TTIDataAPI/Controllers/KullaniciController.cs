using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Dtos;
using TTIDataAPI.Models;

namespace TTIDataAPI.Controllers
{
    /// <summary>
    /// Kullanıcı yönetimi — **yalnızca `admin` hesabı** (18 Ağustos 2026, kullanıcı isteği).
    /// </summary>
    /// <remarks>
    /// Yetki role değil <see cref="Kullanici.SistemYoneticisiMi"/> bayrağına bağlı: mevcut dört
    /// yönetici (kkestane, scorbaci, ofirat, spamuk) birbirinin hesabına erişemez, yalnızca `admin`
    /// erişir. Gerekçe <see cref="Kullanici.SistemYoneticisiMi"/> üzerinde.
    ///
    /// Kapsam bilinçli olarak **dar**: giriş adı ve parola. Kullanıcı ekleme/silme, rol ve aktiflik
    /// değişimi yok — istenmedi ve her biri ayrı bir yetki kararı. Hesap listesi kodda
    /// (<see cref="Services.BaslangicVerisi"/>) duruyor.
    /// </remarks>
    [Route("api/kullanicilar")]
    [ApiController]
    [Authorize]
    [SistemYoneticisiGerekir]
    public class KullaniciController : ControllerBase
    {
        private readonly AppDbContext _context;
        private readonly ILogger<KullaniciController> _gunluk;

        public KullaniciController(AppDbContext context, ILogger<KullaniciController> gunluk)
        {
            _context = context;
            _gunluk = gunluk;
        }

        [HttpGet]
        public async Task<ActionResult<IEnumerable<KullaniciYaniti>>> Listele(CancellationToken iptal)
        {
            var kullanicilar = await _context.Kullanicilar.AsNoTracking()
                .OrderByDescending(x => x.SistemYoneticisiMi)
                .ThenBy(x => x.Rol)
                .ThenBy(x => x.AdSoyad)
                .Select(x => new KullaniciYaniti
                {
                    KullaniciAdi = x.KullaniciAdi,
                    AdSoyad = x.AdSoyad,
                    Rol = x.Rol,
                    Aktif = x.Aktif,
                    IlkGirisMi = x.IlkGirisMi,
                    SistemYoneticisiMi = x.SistemYoneticisiMi,
                })
                .ToListAsync(iptal);

            return Ok(kullanicilar);
        }

        /// <summary>
        /// Bir kullanıcının giriş adını ve/veya parolasını değiştirir.
        /// </summary>
        /// <remarks>
        /// ⚠️ **Giriş adı değişince eski kayıtların sahipliği de taşınır.** `kablo_talep.olusturan`,
        /// `kablo_talep.son_degistiren` ve `guncelleme_gecmisi.degistiren` kullanıcı adını DÜZ METİN
        /// tutuyor (yabancı anahtar yok). Yalnızca `kullanici` satırı güncellenseydi, kişi kendi
        /// açtığı talepleri listede göremez hâle gelirdi — saha kullanıcısının süzgeci tam olarak bu
        /// alanı karşılaştırıyor (`KabloController.YetkiliSorgu`) — ve hiçbir hata çıkmazdı.
        /// Bu yüzden ad değişimi ile kayıt güncellemesi **tek transaction**.
        ///
        /// Parola sıfırlandığında <see cref="Kullanici.IlkGirisMi"/> yeniden `true` olur: yöneticinin
        /// bildiği bir parolayla çalışmaya devam etmek yerine kullanıcı kendi parolasını belirler.
        /// </remarks>
        [HttpPut("{kullaniciAdi}")]
        public async Task<ActionResult<KullaniciYaniti>> Guncelle(
            string kullaniciAdi, [FromBody] KullaniciGuncelleIstegi istek, CancellationToken iptal)
        {
            var yeniAd = istek.YeniKullaniciAdi?.Trim();
            var yeniParola = istek.YeniParola;
            var adDegisiyor = !string.IsNullOrWhiteSpace(yeniAd) && yeniAd != kullaniciAdi;
            var parolaDegisiyor = !string.IsNullOrWhiteSpace(yeniParola);

            if (!adDegisiyor && !parolaDegisiyor)
            {
                return BadRequest(new { hata = "Değiştirilecek bir şey yok: yeni giriş adı ya da yeni parola verin." });
            }

            var kullanici = await _context.Kullanicilar
                .FirstOrDefaultAsync(x => x.KullaniciAdi == kullaniciAdi, iptal);
            if (kullanici is null) return NotFound(new { hata = $"Kullanıcı bulunamadı: {kullaniciAdi}" });

            var oturumSahibi = User.Identity?.Name ?? string.Empty;

            if (adDegisiyor)
            {
                // Kendi giriş adını değiştirmek oturumu geçersiz kılar: çerezdeki kimlik eski adı
                // taşıyor ve bir sonraki istekte 401 düşer. Kullanıcı sebebini anlamadan sistemden
                // atılmış olur; bu yüzden kapalı.
                if (kullanici.KullaniciAdi == oturumSahibi)
                {
                    return BadRequest(new
                    {
                        hata = "Kendi giriş adınızı buradan değiştiremezsiniz; oturumunuz geçersiz kalırdı.",
                    });
                }

                if (!KullaniciAdiKalibi.IsMatch(yeniAd!))
                {
                    return BadRequest(new
                    {
                        hata = "Giriş adı yalnızca küçük harf, rakam, nokta, tire ve alt çizgi içerebilir (ör. kkestane).",
                    });
                }

                var adAlinmis = await _context.Kullanicilar
                    .AnyAsync(x => x.KullaniciAdi == yeniAd, iptal);
                if (adAlinmis) return Conflict(new { hata = $"Bu giriş adı zaten kullanılıyor: {yeniAd}" });
            }

            var eskiAd = kullanici.KullaniciAdi;

            // Ad ve kayıt sahipliği birlikte değişmeli; ikisinin arasında düşen bir hata kayıtları
            // sahipsiz bırakırdı.
            await using var islem = await _context.Database.BeginTransactionAsync(iptal);

            if (adDegisiyor)
            {
                kullanici.KullaniciAdi = yeniAd!;

                var talepSahipligi = await _context.KabloTalepleri
                    .Where(x => x.Olusturan == eskiAd)
                    .ExecuteUpdateAsync(k => k.SetProperty(x => x.Olusturan, yeniAd), iptal);

                var talepDegistiren = await _context.KabloTalepleri
                    .Where(x => x.SonDegistiren == eskiAd)
                    .ExecuteUpdateAsync(k => k.SetProperty(x => x.SonDegistiren, yeniAd), iptal);

                var gecmisDegistiren = await _context.GuncellemeGecmisleri
                    .Where(x => x.Degistiren == eskiAd)
                    .ExecuteUpdateAsync(k => k.SetProperty(x => x.Degistiren, yeniAd), iptal);

                _gunluk.LogInformation(
                    "Giriş adı değişti: {Eski} -> {Yeni}. Taşınan kayıt: {Olusturan} oluşturan, " +
                    "{SonDegistiren} son değiştiren, {Gecmis} geçmiş satırı.",
                    eskiAd, yeniAd, talepSahipligi, talepDegistiren, gecmisDegistiren);
            }

            if (parolaDegisiyor)
            {
                kullanici.ParolaOzeti = BCrypt.Net.BCrypt.HashPassword(yeniParola);
                // Yöneticinin bildiği parolayla devam edilmesin; kullanıcı kendi parolasını belirler.
                kullanici.IlkGirisMi = true;
                _gunluk.LogInformation("{Kullanici} parolası sistem yöneticisi tarafından sıfırlandı.", kullanici.KullaniciAdi);
            }

            await _context.SaveChangesAsync(iptal);
            await islem.CommitAsync(iptal);

            return Ok(new KullaniciYaniti
            {
                KullaniciAdi = kullanici.KullaniciAdi,
                AdSoyad = kullanici.AdSoyad,
                Rol = kullanici.Rol,
                Aktif = kullanici.Aktif,
                IlkGirisMi = kullanici.IlkGirisMi,
                SistemYoneticisiMi = kullanici.SistemYoneticisiMi,
            });
        }

        /// <summary>
        /// Giriş adı alfabesi. `kablo_talep.olusturan` ve URL yolu bu değeri taşıyor; boşluk ve özel
        /// karakterler ikisini de bozardı.
        /// </summary>
        private static readonly System.Text.RegularExpressions.Regex KullaniciAdiKalibi =
            new("^[a-z0-9._-]{3,64}$", System.Text.RegularExpressions.RegexOptions.Compiled);
    }

    /// <summary>
    /// Kullanıcı yönetimi uçlarını `SistemYoneticisiMi` bayrağı olmayan herkese kapatır.
    /// </summary>
    /// <remarks>
    /// Rol tabanlı `[Authorize(Roles = ...)]` kullanılamıyor: yetki rolde değil ayrı bir bayrakta
    /// (bkz. <see cref="Kullanici.SistemYoneticisiMi"/>). Kural denetleyicinin tamamına bir kez
    /// uygulanıyor — uç uç tekrarlansaydı yeni bir uç eklenirken unutulabilirdi ve hata sessiz olurdu.
    /// </remarks>
    public class SistemYoneticisiGerekirAttribute : ActionFilterAttribute
    {
        public override void OnActionExecuting(ActionExecutingContext baglam)
        {
            var yetkili = baglam.HttpContext.User
                .FindFirst(OturumController.SistemYoneticisiClaim)?.Value == "true";

            if (!yetkili)
            {
                baglam.Result = new ObjectResult(new { hata = "Bu işlem için sistem yöneticisi yetkisi gerekir." })
                {
                    StatusCode = StatusCodes.Status403Forbidden,
                };
            }
        }
    }
}
