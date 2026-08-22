using System.ComponentModel.DataAnnotations;

namespace TTIDataAPI.Dtos
{
    /// <summary>Yöneticinin açılır listeye yeni proje kapsamı ekleme isteği.</summary>
    /// <remarks>
    /// `Sira` ve `Aktif` istekte YOK: sıra sunucuda kaldığı yerden verilir, yeni kayıt daima
    /// aktiftir. İstemciden alınsalardı, listenin sırasını bozmak ya da eklenirken pasif bir
    /// kapsam yaratmak mümkün olurdu (over-posting).
    /// </remarks>
    public class KapsamEkleIstegi
    {
        [Required(ErrorMessage = "Kapsam adı zorunludur.")]
        [MaxLength(128, ErrorMessage = "Kapsam adı en fazla 128 karakter olabilir.")]
        public string Ad { get; set; } = string.Empty;
    }
}
