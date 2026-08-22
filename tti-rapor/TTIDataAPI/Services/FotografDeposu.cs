namespace TTIDataAPI.Services
{
    /// <summary>
    /// Saha fotoğraflarını diske yazar. Veritabanında yalnızca göreli yol tutulur.
    /// </summary>
    /// <remarks>
    /// Önceki hâlde fotoğraf base64 metin olarak satırın içindeydi: kodlama ~%33 şişiriyor ve
    /// `GET /api/kablo` her listelemede TÜM fotoğrafları JSON'a gömüp gönderiyordu. Ağaç düzeni
    /// TTI'nin YUKLEME_KOK yaklaşımıyla aynı mantıkta — dosyalar uygulamadan ve veritabanından
    /// bağımsız olarak da erişilebilir olmalı.
    ///
    /// Ortak davranış (kök çözümü, yol dolaşımı koruması, silme) <see cref="DosyaDeposu"/>'nda.
    /// Fotoğraflar alt klasör KULLANMAZ: yolları veritabanında `{yil}/{talepId}/{guid}.jpg`
    /// olarak duruyor ve düzeni değiştirmek var olan kayıtların dosyalarını bulunamaz kılardı.
    /// </remarks>
    public class FotografDeposu : DosyaDeposu
    {
        public FotografDeposu(IConfiguration yapilandirma, ILogger<FotografDeposu> gunluk)
            : base(yapilandirma, gunluk, new[] { ".jpg", ".jpeg", ".png", ".webp" })
        {
        }
    }
}
