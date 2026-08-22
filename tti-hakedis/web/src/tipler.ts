/**
 * Sunucu yanıtlarının TypeScript karşılıkları.
 *
 * ⚠️ **Para ve miktar alanları `string`'dir**, `number` değil (sunucudaki `ParaJsonYapilandirmasi`).
 * Ham birim fiyat 17 haneye kadar çıkıyor; JSON sayısına çevrilse JavaScript'te `double`'a düşer ve
 * kuruş kayar. Tip olarak `string` kalması aynı zamanda caydırıcıdır: bu değerlerle aritmetik
 * yapılamaz, çünkü tutar ve toplam **sunucudan hazır gelir** (CLAUDE.md §13).
 *
 * Gösterime giren alan daima `*Gosterim` olanıdır (`"1.945,08 ₺"`); ham alan yalnızca sunucuya geri
 * gönderilirken kullanılır.
 */

export type Rol = 'BOLGE_SORUMLUSU' | 'YONETICI'

export type BelgeTuru = 'FIZIBILITE' | 'KESIF' | 'HAKEDIS'

export type BelgeDurumu = 'ONAY_BEKLIYOR' | 'ISLENIYOR' | 'TAMAMLANDI' | 'IPTAL'

/** Projenin durumu — güncel aşamanın izdüşümü. `null` = henüz aşama açılmamış (onay bekliyor sayılır). */
export type IsDurumu = 'ONAY_BEKLIYOR' | 'ISLENIYOR' | 'TAMAMLANDI' | 'IPTAL'

export type Varyant = 'FIYATLI' | 'TEKLIF'

export type EkTuru = 'EK1_ROTA' | 'EK2_GORSEL'

export interface Kullanici {
  kullaniciAdi: string
  adSoyad: string
  rol: Rol
  /** Yöneticide `null` — "tüm bölgeler" demektir, "bölgesiz" değil. */
  bolgeKodu: string | null
  /** Kenar çubuğu kullanıcı adı yerine bunu yazar; yöneticide `null` (bkz. `bolgeKodu`). */
  bolgeAdi: string | null
}

export interface Bolge {
  kod: string
  ad: string
  dosyaKodu: string
  /** Envanterdeki "Technical Consultant → Saha" karşılığı; elle kablo eklerken seçilir. */
  sahaAdi: string
}

export interface Kapsam {
  kod: string
  ad: string
  /**
   * Proje açılırken kablo seçimi zorunlu mu — **sunucu söyler** (`Scope.kableZorunluMu`).
   *
   * Web `kod === 'YENI_TESIS'` diye kendi listesini tutmaz: kural üç yerde (sunucu, web, mobil)
   * yaşasaydı yeni bir kapsam muaf olduğunda biri unutulur ve ekran zorunlu göstermeye devam
   * ederdi — sunucu kabul ederken kullanıcı proje açamazdı (CLAUDE.md §7/1).
   */
  kableZorunlu: boolean
}

export interface Sozlesme {
  kod: string
  ad: string
  /** Kaynak Excel'in 1. satırı; PDF başlığı olarak da bu kullanılır. */
  baslik: string
}

export interface Kable {
  nosid: string
  bolgeKodu: string
  adresA: string
  adresB: string | null
  sehirA: string | null
  sehirB: string | null
  danismanAd: string
  sahaAdi: string
}

/** Sözleşme poz satırı. Alan adları `:core:domain`'deki `PozItem` ile birebir aynı. */
export interface Poz {
  id: string
  pozNo: string
  sortKey: number[]
  name: string
  unit: string
  /** Ham birim fiyat — string; yalnızca sunucuya geri gönderilirken kullanılır. */
  unitPrice: string
  /**
   * Kullanıcıya gösterilecek hâli — sunucuda `MoneyFormatter` ile üretilir, **2 haneye yuvarlı**
   * (`"648,36 ₺"`). Belgeye çivilenen fiyat da yuvarlandığı için gösterilen sayı hesapta kullanılan
   * sayının aynısıdır: `fiyat × miktar = tutar` tam tutar.
   */
  unitPriceGosterim: string
  isGroup: boolean
  isPriced: boolean
}

export interface Uyari {
  kod: string
  adet: number
  mesaj: string
}

/**
 * İş listesi satır ekranında bir aşamanın özeti: aşama düğmesinin hizasında gösterilen tarih, son
 * güncelleyen ve toplam.
 */
export interface BelgeOzeti {
  id: number
  belgeNo: string
  tur: BelgeTuru
  durum: BelgeDurumu
  /** Sunucudan gelir; web durum metnini kendi içinde yazmaz (NEWPHASES.md E2). */
  durumMetni: string
  olusturma: string
  /** Bölge sorumlusunun "Gönder" dediği an; dolu ise yönetici işleme alabilir (kart düğmesi). */
  gonderme: string | null
  tamamlanma: string | null
  sonGuncelleme: string | null
  sonGuncelleyenAdSoyad: string | null
  sonGuncelleyenRol: Rol | null
  toplam: string
  toplamGosterim: string
}

/** İş listesi süzgeci. Boş alanlar sorguya hiç eklenmez. */
export interface IsSuzgeci {
  durum?: IsDurumu | ''
  bolgeKodu?: string
  /**
   * Serbest metin araması: SP no · kablo ID · route · ek aralığı. Sunucuda `ilike` ile uygulanır.
   * 3 karakterin altındaki arama sunucuda **yok sayılır** ve tam liste döner — yazarken ekranın
   * boşalması arıza gibi okunuyordu.
   */
  ara?: string
}

export interface Is {
  id: number
  bolgeKodu: string
  bolgeAdi: string
  kapsamKodu: string
  /** Kablo ve uçlar **boş olabilir**: "Yeni Tesis" kapsamında kablo seçimi zorunlu değil (Faz W). */
  kableNosid: string | null
  adresA: string | null
  adresB: string | null
  spNo: string
  route: string
  ekAraligi: string
  yil: number
  olusturma: string
  /** `null` = fizibilite bekleniyor. */
  durum: IsDurumu | null
  /** Kullanıcıya gösterilecek durum; metin sunucudan gelir. */
  durumMetni: string
  iptalGerekce: string | null
  belgeler: BelgeOzeti[]
  uyarilar: Uyari[]
}

export interface BelgeSatiri {
  pozId: string
  pozNo: string
  ad: string
  birim: string
  miktar: string
  miktarGosterim: string
  birimFiyat: string
  birimFiyatGosterim: string
  tutar: string
  tutarGosterim: string
}

export interface Belge {
  id: number
  isId: number
  belgeNo: string
  tur: BelgeTuru
  durum: BelgeDurumu
  durumMetni: string
  bolgeKodu: string
  yil: number
  siraNo: number
  /** Belgenin **kendi** sözleşmesi; üç aşama farklı sözleşme seçebilir. */
  sozlesmeKodu: string
  sozlesmeAdi: string
  fiyatVersiyonuId: number
  olusturma: string
  gonderme: string | null
  tamamlanma: string | null
  sonGuncelleme: string
  sonGuncelleyenAdSoyad: string
  sonGuncelleyenRol: Rol
  iptalGerekce: string | null
  /**
   * Miktar ve dosya girilebilir mi — **sunucu söyler**, web türetmez.
   *
   * Eskiden burada `durum === 'TASLAK'` diye yerel bir kural vardı ve aynı kural sunucuda, webde ve
   * mobilde üç kez yazılıydı. `ONAY_BEKLIYOR` eklenirken biri unutulsaydı ekran kilitli görünür ama
   * sunucu yazmayı kabul ederdi — hiçbir hata çıkmadan (CLAUDE.md §7/1).
   */
  duzenlenebilir: boolean
  /** "Gönder" düğmesi anlamlı mı — onay bekleyen (düzenlenebilir) aşamada gönderilebilir. */
  gonderilebilir: boolean
  satirlar: BelgeSatiri[]
  toplam: string
  toplamGosterim: string
}

/** Yöneticinin durum müdahalesinin kaydı — "bu belge neden geri açıldı" sorusunun cevabı. */
export interface DurumKaydi {
  onceki: string | null
  yeni: string
  gerekce: string
  kullaniciAdSoyad: string
  zaman: string
}

export interface BelgeListeSatiri {
  id: number
  isId: number
  belgeNo: string
  tur: BelgeTuru
  durum: BelgeDurumu
  durumMetni: string
  bolgeKodu: string
  bolgeAdi: string
  /** Bölgenin aktif bölge sorumlusu (madde 16). */
  sorumluAdSoyad: string
  /** Belgeyi fiilen açan kişi — sorumludan farklı olabilir. */
  olusturanAdSoyad: string
  spNo: string
  kableNosid: string
  ekAraligi: string
  route: string
  yil: number
  siraNo: number
  olusturma: string
  gonderme: string | null
  tamamlanma: string | null
  sonGuncelleme: string | null
  sonGuncelleyenAdSoyad: string | null
  sonGuncelleyenRol: Rol | null
  toplam: string
  toplamGosterim: string
}

export interface Ek {
  id: number
  belgeId: number
  tur: EkTuru
  dosyaAdi: string
  boyutBayt: number
  yukleme: string
  yukleyenAdSoyad: string | null
}

/**
 * EK3 "TTI Kullanılan Malzemeler" satırı — yalnızca hakediş aşamasında (18 Ağustos 2026).
 *
 * **Üç alan da opsiyonel**: saha bazen yalnızca seri no, bazen yalnızca ad biliyor. `miktar`
 * `string` — sayıya çevirme sunucuda (`QuantityParser`), web para/sayı aritmetiği yapmaz (§13).
 */
export interface Malzeme {
  ad: string | null
  miktar: string | null
  seriNo: string | null
}

/** Kullanıcı yönetimi satırı (yalnızca yönetici). Parola hash'i **hiçbir yanıtta yoktur**. */
export interface YonetimKullanicisi {
  id: number
  kullaniciAdi: string
  adSoyad: string
  rol: Rol
  bolgeKodu: string | null
  bolgeAdi: string | null
  aktif: boolean
  olusturma: string
}

/** Sözleşme yönetim listesi satırı (yalnızca yönetici). Pasif sözleşmeler de gelir. */
export interface SozlesmeYonetimSatiri {
  kod: string
  ad: string
  baslik: string
  aktif: boolean
  pozSayisi: number
  kaynakDosyaAdi: string | null
  olusturma: string | null
  /** Yayındaki referans fiyat sürümünün numarası; hiç liste yoksa `null`. */
  yayindakiSurum: number | null
  yayindakiSurumTarihi: string | null
  /** Bu sözleşme bir revizyonsa, revize ettiği (önceki) sözleşmenin kodu. */
  oncekiKod: string | null
  /** Bu sözleşme revize edildiyse, onun yerine geçen (yeni) sözleşmenin kodu. */
  yeniKod: string | null
}

/** Sözleşme yüklemesinin "ne olacak" cevabı — hiçbir şey yazılmadan üretilir. */
export interface SozlesmeOnizleme {
  kod: string
  ad: string
  baslik: string
  sayfaAdi: string
  sayfaAdlari: string[]
  pozSayisi: number
  grupSayisi: number
  fiyatliSayisi: number
  fiyatsizSayisi: number
  kodKullanimda: boolean
  uyarilar: string[]
}

// --- Kablo envanteri sürümleri (Faz U) ---------------------------------------------------------

/** Bir bölgede kaç kablo olduğu — hem yürürlükteki envanterde hem yüklenen dosyada. */
export interface BolgeAdedi {
  bolgeKodu: string
  adet: number
}

/** Yüklenmiş bir envanter dosyası. Yürürlükte olan, `kable` tablosunun kaynağıdır. */
export interface KableSurumu {
  id: number
  dosyaAdi: string
  satirSayisi: number
  /** CLI ile aktarıldıysa `null` (oturum yok). */
  yukleyen: string | null
  yuklenme: string
  yururlugeAlinma: string | null
  yururlukte: boolean
}

/** Kablo Envanteri ekranının açılış verisi — tek çağrı. */
export interface KableEnvanteri {
  aktifKableSayisi: number
  bolgeDagilimi: BolgeAdedi[]
  surumler: KableSurumu[]
}

/**
 * Bir sürümün yürürlükteki envantere göre farkı — "yürürlüğe alırsam ne olur".
 *
 * `acikProjeliDusen` uyarının çekirdeği: pasifleşecek kablolardan kaçının üzerinde hâlâ yürüyen bir
 * proje var. Engel değil, bilgi (kullanıcı kararı, 9 Ağustos 2026).
 */
export interface KableSurumFarki {
  surumId: number
  yururluktekiSurumId: number | null
  yeni: number
  degisen: number
  ayni: number
  dusen: number
  dusenOrnekleri: string[]
  acikProjeliDusen: number
  bolgeDagilimi: BolgeAdedi[]
}

export interface KableSurumYuklemeSonucu {
  surum: KableSurumu
  fark: KableSurumFarki
}

/** Yürürlüğe almanın raporu: izdüşümün gerçekte ne yaptığı. */
export interface KableIzdusumSonucu {
  surum: KableSurumu
  eklenen: number
  guncellenen: number
  pasiflesen: number
  aktifKableSayisi: number
  bolgeDagilimi: BolgeAdedi[]
}

/** Belge listesi süzgeci — yönetici görünümü. Boş alanlar sorguya hiç eklenmez. */
export interface BelgeSuzgeci {
  tur?: BelgeTuru | ''
  durum?: BelgeDurumu | ''
  bolgeKodu?: string
  yil?: string
  /** Serbest metin: belge no · SP no · kablo ID. `IsSuzgeci.ara` ile aynı eşik ve davranış. */
  ara?: string
}

/**
 * Yeni iş isteği — hepsi zorunlu, doğrulama sunucudaki `WorkOrderValidator`'da.
 *
 * ~~`bolgeKodu`~~ ve ~~`spNo`~~ Faz O'da çıktı: bölge oturumdan, SP No sunucudaki sayaçtan gelir.
 * ~~`sozlesmeKodu`~~ Faz N'de çıktı: sözleşme artık her belgede ayrı seçiliyor.
 */
export interface IsOlusturmaIstegi {
  kapsamKodu: string
  /** "Yeni Tesis" kapsamında boş bırakılabilir; diğerlerinde sunucu 400 döner. */
  kableNosid?: string
  route: string
  ekAraligi: string
  /**
   * Yalnızca **kablo seçilmediğinde** okunur: kablo varsa uçlar envanterden gelir ve buradaki
   * segmentler yok sayılır — adres, kablonun projeye dondurulmuş kopyasıdır (CLAUDE.md §7/5).
   */
  ucA?: KableUcu
  ucB?: KableUcu
}

/**
 * Elle kablo ekleme/düzenleme isteği (Faz V).
 *
 * `ucA`/`ucB` **ya segment ya hazır yol** taşır: yeni kablo formu segmentleri gönderir (Ülke, Şehir,
 * Adres … Raf), düzenleme formu var olan konum yolunu olduğu gibi geri gönderir. Birleştirme
 * sunucuda — web ayraçları (`>>`, `>`) kendisi kurmaz.
 */
export interface KableIstegi {
  nosid: string
  /** Bölgenin `sahaAdi` değeri; arayüz bölge listesinden seçtirir, serbest metin yazdırmaz. */
  sahaAdi: string
  danismanAd: string
  sehirA?: string | null
  sehirB?: string | null
  ucA?: KableUcu
  ucB?: KableUcu
}

export interface KableUcu {
  konumYolu?: string | null
  segmentler?: Record<string, string>
}
