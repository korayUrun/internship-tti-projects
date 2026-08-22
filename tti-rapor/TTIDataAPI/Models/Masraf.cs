namespace TTIDataAPI.Models
{
    // İNCELENECEK: Bu model kullanılmıyor — AppDbContext'te DbSet'i yok, hiçbir denetleyici
    // ona dokunmuyor, dolayısıyla migration'a da girmiyor ve veritabanında karşılığı olan bir
    // tablo yok. Silinmedi çünkü masraf takibinin kapsamda olup olmadığı henüz netleşmedi.
    //
    // Kullanıma alınacaksa önce iki soru cevaplanmalı:
    //   1. Masraf takibi bu projenin işi mi, yoksa TTI hakediş tarafının mı?
    //   2. `Tutar` için TTI'nin para kuralları geçerli olacak mı (BigDecimal karşılığı `decimal`,
    //      numeric(18,2), satır bazında HALF_UP yuvarlama)? Öyleyse hesaplama tek yerde yaşamalı.
    //
    // Bir sonraki geliştirici bunu "ölü kod" sanıp silmesin diye bu not burada duruyor.
    public class Masraf
    {
        public int Id { get; set; }
        public string CalisanAdi { get; set; } = string.Empty;
        public decimal Tutar { get; set; }
        public string Kategori { get; set; } = string.Empty;
        public string Aciklama { get; set; } = string.Empty;
        public DateTime Tarih { get; set; } = DateTime.UtcNow;
    }
}
