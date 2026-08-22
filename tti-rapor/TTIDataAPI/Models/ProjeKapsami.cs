namespace TTIDataAPI.Models
{
    /// <summary>
    /// Formdaki "Proje Kapsamı" açılır listesinin kayıtları.
    /// </summary>
    /// <remarks>
    /// Liste önceden yalnızca <see cref="Sabitler.ProjeKapsamlari"/> içinde sabit bir diziydi:
    /// sahada listede olmayan bir kapsamla karşılaşıldığında kayıt açmanın hiçbir yolu yoktu,
    /// yeni bir madde eklemek kod değişikliği + yeni dağıtım demekti. Liste artık burada durur;
    /// koddaki dizi yalnızca ilk açılışta tohum olarak kullanılır.
    ///
    /// Silme yok, <see cref="Aktif"/> var: bir kapsam kullanımdan kalksa bile eski taleplerin
    /// <c>ProjeGuzergah</c> metni ona referans verir; satırı silmek o kayıtları geçmişte
    /// doğrulanamaz hâle getirirdi.
    /// </remarks>
    public class ProjeKapsami
    {
        public int Id { get; set; }

        /// <summary>Kullanıcıya görünen ad; talepte <c>ProjeGuzergah</c> olarak METİN saklanır.</summary>
        public string Ad { get; set; } = string.Empty;

        /// <summary>Açılır listedeki sıra. Yeni eklenen kayıt en sona gider.</summary>
        public int Sira { get; set; }

        public bool Aktif { get; set; } = true;
    }
}
