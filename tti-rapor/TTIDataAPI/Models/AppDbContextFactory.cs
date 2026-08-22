using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace TTIDataAPI.Models
{
    /// <summary>
    /// `dotnet ef` komutlarının kullandığı tasarım zamanı fabrikası.
    /// </summary>
    /// <remarks>
    /// Bu sınıf olmadan EF, uygulamanın giriş noktasını çalıştırarak DbContext'i bulmaya çalışır;
    /// Program.cs ise açılışta Database.Migrate() çağırdığı için `dotnet ef migrations add`
    /// gerçek veritabanına bağlanmayı dener ve veritabanı ayakta değilse komut düşer.
    /// Fabrika varken EF doğrudan buraya gelir, Program.cs hiç çalışmaz.
    ///
    /// Buradaki bağlantı dizesi yalnızca SQL üretmek için kullanılır; migration dosyası
    /// oluşturmak için veritabanının ayakta olması gerekmez.
    /// </remarks>
    public class AppDbContextFactory : IDesignTimeDbContextFactory<AppDbContext>
    {
        public AppDbContext CreateDbContext(string[] args)
        {
            var baglanti = Environment.GetEnvironmentVariable("ConnectionStrings__Varsayilan")
                           ?? "Host=localhost;Port=5432;Database=rapor;Username=rapor;Password=rapor;Search Path=rapor;Maximum Pool Size=10";

            var secenekler = new DbContextOptionsBuilder<AppDbContext>()
                .UseNpgsql(baglanti, npgsql =>
                    npgsql.MigrationsHistoryTable("__EFMigrationsHistory", AppDbContext.Sema))
                .UseSnakeCaseNamingConvention()
                .Options;

            return new AppDbContext(secenekler);
        }
    }
}
