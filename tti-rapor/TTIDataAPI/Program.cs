using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.EntityFrameworkCore;
using TTIDataAPI.Models;
using TTIDataAPI.Services;

var builder = WebApplication.CreateBuilder(args);

// --- Veritabanı ---------------------------------------------------------------------------
// Bağlantı dizesi ortamdan gelir; konteynerde ConnectionStrings__Varsayilan ile verilir.
// Eksikse AÇILIŞTA dururuz: gömülü bir varsayılana düşmek, üretimde yanlış veritabanına
// sessizce bağlanmak demek olurdu.
var baglantiDizesi = builder.Configuration.GetConnectionString("Varsayilan");
if (string.IsNullOrWhiteSpace(baglantiDizesi))
{
    throw new InvalidOperationException(
        "Bağlantı dizesi yok. ConnectionStrings:Varsayilan ayarını appsettings.json ile ya da " +
        "ConnectionStrings__Varsayilan ortam değişkeniyle verin. " +
        "Örnek: Host=db;Port=5432;Database=tti;Username=rapor;Password=...;Search Path=rapor");
}

builder.Services.AddDbContext<AppDbContext>(options =>
    options
        .UseNpgsql(baglantiDizesi, npgsql =>
            // HasDefaultSchema tabloları `rapor`'a taşır ama migration geçmişi tablosunu TAŞIMAZ;
            // bu satır olmadan __EFMigrationsHistory `public` şemasına düşer ve iki projenin
            // paylaştığı tek şemayı kirletir.
            npgsql.MigrationsHistoryTable("__EFMigrationsHistory", AppDbContext.Sema))
        .UseSnakeCaseNamingConvention());

// --- Servisler ----------------------------------------------------------------------------
builder.Services.AddSingleton<FotografDeposu>();
builder.Services.AddSingleton<GuzergahDeposu>();
builder.Services.AddScoped<BaslangicVerisi>();

builder.Services.AddControllers(secenekler =>
{
    // Parola değiştirme zorunluluğu sunucuda uygulanır; yalnızca arayüzde olsaydı API'ye
    // doğrudan istek atan biri varsayılan parolayla çalışmaya devam ederdi.
    secenekler.Filters.Add<ParolaDegistirmeFiltresi>();
});
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// EK1/EK2 karşılığı saha fotoğrafı; tabletten çekilmiş bir görsel 3-8 MB olabiliyor.
builder.Services.Configure<Microsoft.AspNetCore.Http.Features.FormOptions>(o =>
{
    o.MultipartBodyLengthLimit = 10 * 1024 * 1024;
});

// --- Kimlik doğrulama ---------------------------------------------------------------------
// Çerez oturumu; JWT değil. On bir kullanıcı ve tek servis için token yenileme/iptal
// karmaşıklığı karşılıksız kalırdı — TTI tarafı da aynı gerekçeyle çerezde.
builder.Services
    .AddAuthentication(CookieAuthenticationDefaults.AuthenticationScheme)
    .AddCookie(secenekler =>
    {
        secenekler.Cookie.Name = "rapor-oturum";
        secenekler.Cookie.HttpOnly = true;
        secenekler.Cookie.SameSite = SameSiteMode.Strict;
        // TLS bu turda kapsam dışı; HTTPS'e geçildiğinde burası Always olacak.
        secenekler.Cookie.SecurePolicy = CookieSecurePolicy.None;
        secenekler.ExpireTimeSpan = TimeSpan.FromHours(8);
        secenekler.SlidingExpiration = true;

        // Arayüz tek sayfa uygulaması: giriş sayfasına yönlendirme yerine JSON durum kodu döner.
        // Yönlendirme dönseydi fetch çağrıları 200 + HTML alır ve hata sessizce yutulurdu.
        secenekler.Events.OnRedirectToLogin = ctx =>
        {
            ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
            return Task.CompletedTask;
        };
        secenekler.Events.OnRedirectToAccessDenied = ctx =>
        {
            ctx.Response.StatusCode = StatusCodes.Status403Forbidden;
            return Task.CompletedTask;
        };
    });

builder.Services.AddAuthorization();

// CORS YOK: arayüz aynı origin'den (wwwroot) servis ediliyor. Önceki AllowAnyOrigin politikası
// hiçbir işe yaramadan API'yi her siteye açıyordu.

var app = builder.Build();

// --- Şema ve migration --------------------------------------------------------------------
using (var kapsam = app.Services.CreateScope())
{
    var db = kapsam.ServiceProvider.GetRequiredService<AppDbContext>();
    // EnsureCreated() değil Migrate(): EnsureCreated şema bir kez oluştuktan sonra model
    // değişikliklerini SESSİZCE uygulamaz, hata ancak sorgu anında ortaya çıkar.
    db.Database.Migrate();

    await kapsam.ServiceProvider.GetRequiredService<BaslangicVerisi>().CalistirAsync();
}

if (app.Environment.IsDevelopment())
{
    // Swagger yalnızca geliştirmede. Üretimde açık bırakmak, API yüzeyinin tamamını
    // kimliksiz herkese ilan etmek demek.
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseDefaultFiles();
app.UseStaticFiles();

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

app.Run();
