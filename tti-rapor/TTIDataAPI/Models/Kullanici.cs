namespace TTIDataAPI.Models
{
    /// <summary>
    /// Uygulama kullanıcısı. Parola BCrypt özeti olarak saklanır, asla düz metin değil.
    /// </summary>
    /// <remarks>
    /// Giriş ekranı arayüzde baştan beri vardı ama kullanıcı listesi ve parolalar
    /// index.html içinde düz metin duruyordu; üstelik kontrol `pass === "123" || pass === girilen`
    /// olduğu için ilk girişte **herhangi bir parola** kabul ediliyordu. Doğrulama sunucuya taşındı.
    ///
    /// JWT kullanılmıyor: on bir kullanıcı ve tek servis için token yenileme/iptal karmaşıklığı
    /// karşılıksız kalırdı. TTI tarafı da aynı gerekçeyle çerez oturumunda.
    /// </remarks>
    public class Kullanici
    {
        public int Id { get; set; }

        public string KullaniciAdi { get; set; } = string.Empty;
        public string AdSoyad { get; set; } = string.Empty;
        public string ParolaOzeti { get; set; } = string.Empty;

        /// <summary>Sabitler.RolYonetici veya Sabitler.RolKullanici.</summary>
        public string Rol { get; set; } = Sabitler.RolKullanici;

        /// <summary>true ise arayüz parola değiştirmeden başka bir işleme izin vermez.</summary>
        public bool IlkGirisMi { get; set; } = true;

        public bool Aktif { get; set; } = true;

        /// <summary>
        /// Kullanıcı yönetimi yetkisi: başkalarının giriş adını ve parolasını değiştirebilir.
        /// </summary>
        /// <remarks>
        /// Rol yerine AYRI bir bayrak (kullanıcı kararı, 18 Ağustos 2026): yetki yalnızca `admin`
        /// hesabında olacak, mevcut dört yönetici bugünkü yetkileriyle kalacak. Üçüncü bir rol
        /// değeri eklenseydi `Roles = admin` yazan her uç (katalog yükleme, kapsam ekleme) ve
        /// `IsInRole` ile kurulan kayıt görünürlüğü süzgeci tek tek gözden geçirilmek zorunda kalırdı;
        /// biri atlanınca hata sessiz olurdu. Bayrak, var olan yetki düzenine hiç dokunmuyor.
        /// </remarks>
        public bool SistemYoneticisiMi { get; set; }
    }
}
