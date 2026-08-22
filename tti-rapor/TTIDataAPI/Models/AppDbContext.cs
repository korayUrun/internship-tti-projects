using Microsoft.EntityFrameworkCore;

namespace TTIDataAPI.Models
{
    public class AppDbContext : DbContext
    {
        /// <summary>
        /// Bu servisin YAZDIĞI TEK şema. Veritabanı (`tti`) TTI Saha Hakediş ile paylaşılır;
        /// o taraf yalnızca `hakedis` şemasına yazar, burası yalnızca `rapor`'a.
        /// Şemalar arası okuma/yazma, ortak tablo ve view yoktur.
        /// </summary>
        public const string Sema = "rapor";

        public AppDbContext(DbContextOptions<AppDbContext> options) : base(options) { }

        public DbSet<KabloTalep> KabloTalepleri => Set<KabloTalep>();
        public DbSet<GuncellemeGecmisi> GuncellemeGecmisleri => Set<GuncellemeGecmisi>();
        public DbSet<Kullanici> Kullanicilar => Set<Kullanici>();
        public DbSet<KabloKatalog> KabloKataloglari => Set<KabloKatalog>();
        public DbSet<ProjeKapsami> ProjeKapsamlari => Set<ProjeKapsami>();

        protected override void OnModelCreating(ModelBuilder modelBuilder)
        {
            // Bu satır olmadan tablolar `public` şemasına düşer — ortak veritabanında iki projenin
            // çarpışabileceği tek yer orasıdır. Migration geçmişi tablosu buna DAHİL DEĞİL; onun
            // şeması Program.cs'te MigrationsHistoryTable ile ayrıca veriliyor.
            modelBuilder.HasDefaultSchema(Sema);

            modelBuilder.Entity<KabloTalep>(e =>
            {
                e.ToTable("kablo_talep");
                e.HasKey(x => x.Id);

                // İki kullanıcı aynı talebin statüsünü aynı anda değiştirirse, ikincisinin yazması
                // sessizce birincisini ezmek yerine DbUpdateConcurrencyException fırlatır.
                // PostgreSQL'in sistem sütunu `xmin` kullanılır; ayrı bir sürüm sütunu gerekmez.
#pragma warning disable CS0618 // Npgsql bunu "IsRowVersion kullanın" diye işaretledi; oradaki yol
                              // gölge sütunu CREATE TABLE'a da yazmaya kalkıyor. Davranış doğru
                              // çalıştığı için API burada bilerek korunuyor.
                e.UseXminAsConcurrencyToken();
#pragma warning restore CS0618

                e.Property(x => x.Olusturan).HasMaxLength(64).IsRequired();
                e.Property(x => x.Nosid).HasMaxLength(128).IsRequired();
                e.Property(x => x.LocationA).HasMaxLength(1024);
                e.Property(x => x.LocationB).HasMaxLength(1024);
                e.Property(x => x.ProjeGuzergah).HasMaxLength(128);
                e.Property(x => x.SahaSehir).HasMaxLength(128);
                e.Property(x => x.EkAraligi).HasMaxLength(128);
                e.Property(x => x.ArizaRiski).HasMaxLength(32).IsRequired();
                e.Property(x => x.GuzergahDosyaYolu).HasMaxLength(512);
                e.Property(x => x.GuzergahDosyaAdi).HasMaxLength(260);
                e.Property(x => x.FotografYolu).HasMaxLength(512);
                e.Property(x => x.IlkAciklama).HasMaxLength(4000);
                e.Property(x => x.Statu).HasMaxLength(32).IsRequired();
                e.Property(x => x.SonAciklama).HasMaxLength(4000);
                e.Property(x => x.SonDegistiren).HasMaxLength(64);

                e.Property(x => x.OlusturulmaTarihi).HasColumnType("timestamptz");
                e.Property(x => x.SonDegistirilmeTarihi).HasColumnType("timestamptz");

                // Liste ekranının süzdüğü/sıraladığı alanlar.
                e.HasIndex(x => x.Statu);
                e.HasIndex(x => x.Nosid);
                e.HasIndex(x => x.OlusturulmaTarihi);
                // Yetki süzgeci: rolü `user` olan kullanıcı yalnızca kendi kayıtlarını görür.
                e.HasIndex(x => x.Olusturan);
            });

            modelBuilder.Entity<GuncellemeGecmisi>(e =>
            {
                e.ToTable("guncelleme_gecmisi");
                e.HasKey(x => x.Id);

                e.Property(x => x.Degistiren).HasMaxLength(64).IsRequired();
                e.Property(x => x.EskiStatu).HasMaxLength(32).IsRequired();
                e.Property(x => x.YeniStatu).HasMaxLength(32).IsRequired();
                e.Property(x => x.Aciklama).HasMaxLength(4000);
                e.Property(x => x.Tarih).HasColumnType("timestamptz");

                e.HasOne(x => x.KabloTalep)
                    .WithMany(x => x.Gecmis)
                    .HasForeignKey(x => x.KabloTalepId)
                    .OnDelete(DeleteBehavior.Cascade);

                e.HasIndex(x => x.KabloTalepId);
            });

            modelBuilder.Entity<Kullanici>(e =>
            {
                e.ToTable("kullanici");
                e.HasKey(x => x.Id);

                e.Property(x => x.KullaniciAdi).HasMaxLength(64).IsRequired();
                e.Property(x => x.AdSoyad).HasMaxLength(128).IsRequired();
                e.Property(x => x.ParolaOzeti).HasMaxLength(256).IsRequired();
                e.Property(x => x.Rol).HasMaxLength(16).IsRequired();

                e.HasIndex(x => x.KullaniciAdi).IsUnique();

                // Kullanıcı yönetimi yetkisi yalnızca `admin` hesabında; kısmi benzersiz indeks
                // ikinci bir sistem yöneticisinin yanlışlıkla açılmasını veritabanı düzeyinde
                // engelliyor (yetki genişlemesi sessiz olmasın).
                e.HasIndex(x => x.SistemYoneticisiMi)
                    .IsUnique()
                    .HasFilter("sistem_yoneticisi_mi");
            });

            modelBuilder.Entity<KabloKatalog>(e =>
            {
                e.ToTable("kablo_katalog");
                e.HasKey(x => x.Id);

                e.Property(x => x.Nosid).HasMaxLength(128).IsRequired();
                e.Property(x => x.SahaSehir).HasMaxLength(128);
                e.Property(x => x.LocationA).HasMaxLength(1024);
                e.Property(x => x.LocationB).HasMaxLength(1024);

                e.HasIndex(x => x.Nosid).IsUnique();
            });

            modelBuilder.Entity<ProjeKapsami>(e =>
            {
                e.ToTable("proje_kapsami");
                e.HasKey(x => x.Id);

                e.Property(x => x.Ad).HasMaxLength(128).IsRequired();

                // Benzersizlik veritabanında: aynı kapsamı iki yönetici aynı anda eklemeye
                // kalkarsa denetleyicideki "var mı" kontrolü ikisinde de geçer, çakışmayı ancak
                // bu kısıt durdurur.
                e.HasIndex(x => x.Ad).IsUnique();
            });

            base.OnModelCreating(modelBuilder);
        }
    }
}
