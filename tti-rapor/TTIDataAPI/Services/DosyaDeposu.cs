namespace TTIDataAPI.Services
{
    /// <summary>
    /// Kullanıcının yüklediği dosyaları YUKLEME_KOK altına yazan ortak taban.
    /// Veritabanında yalnızca köke GÖRELİ yol tutulur.
    /// </summary>
    /// <remarks>
    /// İki tür yükleme var (saha fotoğrafı ve güzergâh dosyası) ve ikisinin de kök çözümü, yol
    /// dolaşımı koruması ve silme davranışı aynı. Bu sınıf çıkarılmadan önce ikinci tür,
    /// birincisinin kopyalanmasıyla eklenecekti; kopyalanan kodda düzeltilen bir güvenlik
    /// kontrolünün diğerine taşınmaması sessiz bir açık üretir.
    ///
    /// Türeyen sınıf yalnızca İKİ şeyi belirler: hangi uzantılara izin verildiği ve dosyaların
    /// hangi alt klasöre yazıldığı.
    /// </remarks>
    public abstract class DosyaDeposu
    {
        private readonly string _kok;
        private readonly ILogger _gunluk;
        private readonly string[] _izinliUzantilar;

        /// <summary>Kök altındaki tür klasörü; null ise dosyalar doğrudan `{yil}/{talepId}` altına yazılır.</summary>
        private readonly string? _altKlasor;

        protected DosyaDeposu(
            IConfiguration yapilandirma,
            ILogger gunluk,
            string[] izinliUzantilar,
            string? altKlasor = null)
        {
            _gunluk = gunluk;
            _izinliUzantilar = izinliUzantilar;
            _altKlasor = altKlasor;

            // Boş dize "ayarlanmamış" sayılır: appsettings.json'daki yer tutucu değer, ortam
            // değişkenini sessizce gölgelemesin.
            _kok = IlkDolu(
                yapilandirma["Yukleme:Kok"],
                Environment.GetEnvironmentVariable("RAPOR_YUKLEME_KOK"),
                Path.Combine(AppContext.BaseDirectory, "yukleme"));
        }

        public string Kok => _kok;

        /// <summary>İzin verilen uzantılar — hata iletisinde kullanıcıya gösterilir.</summary>
        public IReadOnlyList<string> IzinliUzantilar => _izinliUzantilar;

        private static string IlkDolu(params string?[] adaylar) =>
            adaylar.First(a => !string.IsNullOrWhiteSpace(a))!;

        /// <summary>Kök klasör yazılabilir mi — sağlık ucu bunu raporlar.</summary>
        public bool YazilabilirMi()
        {
            try
            {
                Directory.CreateDirectory(_kok);
                var deneme = Path.Combine(_kok, ".yazma-denemesi");
                File.WriteAllText(deneme, string.Empty);
                File.Delete(deneme);
                return true;
            }
            catch (Exception hata)
            {
                // SELinux etiketi (:Z) eksikse hata tam burada çıkar. Sessizce yutulursa uygulama
                // açılır, sağlık kontrolü geçer ve sorun ilk yüklemede ortaya çıkar.
                _gunluk.LogWarning(hata, "Yükleme kökü yazılabilir değil: {Kok}", _kok);
                return false;
            }
        }

        /// <summary>Dosyayı kaydeder ve köke göre GÖRELİ yolu döner.</summary>
        public async Task<string> KaydetAsync(int talepId, IFormFile dosya, CancellationToken iptal)
        {
            // Doğrulama UZANTI üzerinden: tarayıcının gönderdiği içerik türü (MIME) istemciden
            // gelir ve serbestçe uydurulabilir. .kmz için tarayıcılar zaten sıklıkla
            // "application/octet-stream" gönderir.
            var uzanti = Path.GetExtension(dosya.FileName).ToLowerInvariant();
            if (!_izinliUzantilar.Contains(uzanti))
            {
                throw new ArgumentException(
                    $"Desteklenmeyen dosya türü: {uzanti}. İzin verilenler: {string.Join(", ", _izinliUzantilar)}");
            }

            var goreliKlasor = Path.Combine(DateTime.UtcNow.Year.ToString(), talepId.ToString());
            if (_altKlasor is not null) goreliKlasor = Path.Combine(_altKlasor, goreliKlasor);

            var tamKlasor = Path.Combine(_kok, goreliKlasor);
            Directory.CreateDirectory(tamKlasor);

            // Disk adı GUID: kullanıcının dosya adı doğrudan yola yazılsaydı hem çakışırdı hem de
            // içindeki `/` ve `..` ile kökün dışına çıkmanın yolu olurdu. Özgün ad veritabanında
            // ayrı bir sütunda saklanır ve yalnızca indirmede kullanılır.
            var ad = $"{Guid.NewGuid():N}{uzanti}";
            var goreliYol = Path.Combine(goreliKlasor, ad);

            await using var akis = File.Create(Path.Combine(_kok, goreliYol));
            await dosya.CopyToAsync(akis, iptal);

            return goreliYol.Replace(Path.DirectorySeparatorChar, '/');
        }

        /// <summary>Göreli yolu mutlak yola çevirir; kökün dışına çıkan yolu reddeder.</summary>
        public string? TamYol(string? goreliYol)
        {
            if (string.IsNullOrWhiteSpace(goreliYol)) return null;

            var tam = Path.GetFullPath(Path.Combine(_kok, goreliYol));
            var kokTam = Path.GetFullPath(_kok);

            // Yol dolaşımı (path traversal) koruması: DB'ye `../../etc/passwd` yazılmış olsa bile
            // kökün dışına çıkılamaz.
            if (!tam.StartsWith(kokTam, StringComparison.Ordinal)) return null;

            return File.Exists(tam) ? tam : null;
        }

        public void Sil(string? goreliYol)
        {
            var tam = TamYol(goreliYol);
            if (tam is null) return;

            try
            {
                File.Delete(tam);
            }
            catch (Exception hata)
            {
                // Kayıt silindi, dosya kaldı: veri kaybı değil, artık dosya. Gürültü yapmadan logla.
                _gunluk.LogWarning(hata, "Dosya silinemedi: {Yol}", tam);
            }
        }
    }
}
