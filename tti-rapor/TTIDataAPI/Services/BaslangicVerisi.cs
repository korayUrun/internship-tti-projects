using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Models;

namespace TTIDataAPI.Services
{
    /// <summary>
    /// Açılışta eksik başlangıç kayıtlarını tamamlar. Migration'dan SONRA çalışır.
    /// </summary>
    /// <remarks>
    /// Neden migration içinde SQL değil: parola özetleri depoya gömülmesin ve varsayılan parola
    /// ortamdan verilebilsin diye. İşlem fikir birliğine dayanır (idempotent) — var olan kaydı
    /// ezmez, yalnızca eksikleri ekler; her açılışta güvenle koşar.
    /// </remarks>
    public class BaslangicVerisi
    {
        private readonly AppDbContext _context;
        private readonly IConfiguration _yapilandirma;
        private readonly ILogger<BaslangicVerisi> _gunluk;

        /// <summary>
        /// Kullanıcılar bununla açılır ve İLK GİRİŞTE değiştirmek zorundadır
        /// (IlkGirisMi = true; ParolaDegistirmeFiltresi parola değişene kadar veri uçlarını kapatır).
        /// RAPOR_VARSAYILAN_PAROLA ortam değişkeniyle ezilebilir.
        /// </summary>
        private const string VarsayilanParola = "CHANGE_ME_PASSWORD";

        /// <summary>
        /// Sistem yöneticisi hesabı — başkalarının giriş adını ve parolasını değiştirebilen tek hesap
        /// (kullanıcı isteği, 18 Ağustos 2026).
        /// </summary>
        /// <remarks>
        /// Parolası diğerlerinden AYRI ve `IlkGirisMi = false`: kullanıcı bu parolayı bilerek belirledi,
        /// ilk girişte değiştirmeye zorlamak isteği boşa çıkarırdı. Bu hesabın varlık nedeni de zaten
        /// parolasını unutanları kurtarmak — kendisi kilitlenirse kurtaracak kimse kalmaz.
        /// </remarks>
        private const string SistemYoneticisiAdi = "admin";

        /// <summary>
        /// `admin` parolası. Hakediş'teki `admin` hesabıyla **aynı** (kullanıcı isteği, 19 Ağustos
        /// 2026): iki uygulamada tek bir yönetici parolası akılda tutuluyor.
        /// </summary>
        /// <remarks>
        /// ⚠️ Bu sabit yalnızca hesap **ilk kez** oluşturulurken okunur; var olan kaydın parolası
        /// değişmez (<see cref="SistemYoneticisiniEkleAsync"/> ezmiyor). Kurulu bir sistemde parolayı
        /// değiştirmenin yolu `admin` ekranı ya da `RAPOR_ADMIN_PAROLA` ile yeniden tohumlamadır.
        /// </remarks>
        private const string SistemYoneticisiParolasi = "CHANGE_ME_PASSWORD";

        private static readonly (string Ad, string AdSoyad, string Rol)[] Kullanicilar =
        {
            ("yonetici-1", "Yonetici Bir", Sabitler.RolYonetici),
            ("yonetici-2", "Yonetici Iki", Sabitler.RolYonetici),
            ("yonetici-3", "Yonetici Uc", Sabitler.RolYonetici),
            ("yonetici-4", "Yonetici Dort", Sabitler.RolYonetici),
            ("saha-1", "Saha Kullanici Bir", Sabitler.RolKullanici),
            ("saha-2", "Saha Kullanici Iki", Sabitler.RolKullanici),
            ("saha-3", "Saha Kullanici Uc", Sabitler.RolKullanici),
            ("saha-4", "Saha Kullanici Dort", Sabitler.RolKullanici),
            ("saha-5", "Saha Kullanici Bes", Sabitler.RolKullanici),
            ("saha-6", "Saha Kullanici Alti", Sabitler.RolKullanici),
            ("saha-7", "Saha Kullanici Yedi", Sabitler.RolKullanici),
        };

        public BaslangicVerisi(
            AppDbContext context, IConfiguration yapilandirma, ILogger<BaslangicVerisi> gunluk)
        {
            _context = context;
            _yapilandirma = yapilandirma;
            _gunluk = gunluk;
        }

        public async Task CalistirAsync(CancellationToken iptal = default)
        {
            await KullanicilariEkleAsync(iptal);
            await SistemYoneticisiniEkleAsync(iptal);
            await KapsamlariEkleAsync(iptal);
        }

        /// <summary>
        /// `admin` hesabını eksikse ekler. Var olan kaydı EZMEZ — parolası değiştirilmişse öyle kalır.
        /// </summary>
        private async Task SistemYoneticisiniEkleAsync(CancellationToken iptal)
        {
            var varMi = await _context.Kullanicilar
                .AnyAsync(x => x.KullaniciAdi == SistemYoneticisiAdi, iptal);
            if (varMi) return;

            var parola = Environment.GetEnvironmentVariable("RAPOR_ADMIN_PAROLA");
            if (string.IsNullOrWhiteSpace(parola)) parola = SistemYoneticisiParolasi;

            _context.Kullanicilar.Add(new Kullanici
            {
                KullaniciAdi = SistemYoneticisiAdi,
                AdSoyad = "Sistem Yöneticisi",
                ParolaOzeti = BCrypt.Net.BCrypt.HashPassword(parola),
                Rol = Sabitler.RolYonetici,
                // Parolayı kullanıcı belirledi; ilk girişte değiştirmeye zorlamak isteği boşa çıkarırdı.
                IlkGirisMi = false,
                Aktif = true,
                SistemYoneticisiMi = true,
            });

            await _context.SaveChangesAsync(iptal);
            _gunluk.LogInformation("Sistem yöneticisi hesabı ({Ad}) eklendi.", SistemYoneticisiAdi);
        }

        /// <summary>
        /// Proje kapsamı listesini tabloda EKSİK olan maddelerle tamamlar.
        /// </summary>
        /// <remarks>
        /// Kullanıcı tohumlamasıyla aynı kural: var olan satıra dokunulmaz. Yöneticinin arayüzden
        /// eklediği kapsamlar ile koddaki tohum listesi zamanla ayrışacaktır ve bu beklenen
        /// durumdur — otorite tablodur. "Eksikleri yaz" yerine "listeyi eşitle" deseydik, her
        /// açılış yöneticinin eklediklerini sessizce silerdi.
        /// </remarks>
        private async Task KapsamlariEkleAsync(CancellationToken iptal)
        {
            var mevcut = await _context.ProjeKapsamlari
                .Select(x => x.Ad)
                .ToListAsync(iptal);

            var eksikler = Sabitler.ProjeKapsamlari.Where(a => !mevcut.Contains(a)).ToList();
            if (eksikler.Count == 0) return;

            // Sıra numarası kaldığı yerden devam eder; tohum listesinin kendi sırası korunur.
            var sonraki = mevcut.Count == 0
                ? 1
                : await _context.ProjeKapsamlari.MaxAsync(x => x.Sira, iptal) + 1;

            foreach (var ad in eksikler)
            {
                _context.ProjeKapsamlari.Add(new ProjeKapsami
                {
                    Ad = ad,
                    Sira = sonraki++,
                    Aktif = true,
                });
            }

            await _context.SaveChangesAsync(iptal);
            _gunluk.LogInformation("{Sayi} proje kapsamı eklendi.", eksikler.Count);
        }

        private async Task KullanicilariEkleAsync(CancellationToken iptal)
        {
            var mevcut = await _context.Kullanicilar
                .Select(x => x.KullaniciAdi)
                .ToListAsync(iptal);

            var eksikler = Kullanicilar.Where(k => !mevcut.Contains(k.Ad)).ToList();
            if (eksikler.Count == 0) return;

            // Belgelenen değişken RAPOR_VARSAYILAN_PAROLA önce gelir (FotografDeposu ile aynı desen);
            // Baslangic:VarsayilanParola config anahtarı geriye dönük uyumluluk için fallback kalır.
            var parola = Environment.GetEnvironmentVariable("RAPOR_VARSAYILAN_PAROLA");
            if (string.IsNullOrWhiteSpace(parola)) parola = _yapilandirma["Baslangic:VarsayilanParola"];
            if (string.IsNullOrWhiteSpace(parola)) parola = VarsayilanParola;

            foreach (var (ad, adSoyad, rol) in eksikler)
            {
                _context.Kullanicilar.Add(new Kullanici
                {
                    KullaniciAdi = ad,
                    AdSoyad = adSoyad,
                    ParolaOzeti = BCrypt.Net.BCrypt.HashPassword(parola),
                    Rol = rol,
                    IlkGirisMi = true,
                    Aktif = true,
                });
            }

            await _context.SaveChangesAsync(iptal);
            _gunluk.LogInformation(
                "{Sayi} kullanıcı eklendi; hepsi ilk girişte parola değiştirmek zorunda.",
                eksikler.Count);
        }
    }
}
