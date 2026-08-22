import type {
  Belge,
  BelgeDurumu,
  BelgeListeSatiri,
  BelgeSuzgeci,
  BelgeTuru,
  Bolge,
  DurumKaydi,
  Ek,
  EkTuru,
  Is,
  IsDurumu,
  IsOlusturmaIstegi,
  IsSuzgeci,
  Kable,
  KableEnvanteri,
  KableIstegi,
  KableIzdusumSonucu,
  KableSurumFarki,
  KableSurumYuklemeSonucu,
  Kapsam,
  Kullanici,
  Malzeme,
  Poz,
  Rol,
  Sozlesme,
  SozlesmeOnizleme,
  SozlesmeYonetimSatiri,
  Varyant,
  YonetimKullanicisi,
} from './tipler'

/**
 * Sunucu hatası. Gövde her uçta aynı biçimdedir (`HataYakalayici`): `{ hata, mesaj }`.
 * [mesaj] doğrudan kullanıcıya gösterilir — sunucu mesajları Türkçe ve eyleme dönük yazılmıştır
 * ("önce 2. işin hakedişini tamamlayın"); web bunları yeniden yazmaz.
 */
export class ApiHatasi extends Error {
  constructor(
    readonly durum: number,
    readonly kod: string,
    mesaj: string,
  ) {
    super(mesaj)
    this.name = 'ApiHatasi'
  }
}

/** Oturum düştüğünde tetiklenir; uygulama giriş ekranına döner. */
let oturumDustu: () => void = () => {}
export function oturumDusunceCagir(geriCagirma: () => void) {
  oturumDustu = geriCagirma
}

function cerez(ad: string): string | null {
  const eslesme = document.cookie.match(new RegExp('(?:^|; )' + ad + '=([^;]*)'))
  return eslesme?.[1] ? decodeURIComponent(eslesme[1]) : null
}

/**
 * CSRF token'ı `XSRF-TOKEN` cookie'sinden okunur ve `X-XSRF-TOKEN` başlığında geri gönderilir
 * (`CookieCsrfTokenRepository.withHttpOnlyFalse`).
 *
 * Cookie sunucudan geçen ilk istekte yazılır. Üretimde sayfanın kendisi sunucudan indiği için token
 * zaten vardır; `npm run dev`'de sayfayı Vite sunar, bu yüzden token yoksa önce bir GET yapılır.
 */
async function csrfBasligi(): Promise<Record<string, string>> {
  let token = cerez('XSRF-TOKEN')
  if (!token) {
    await fetch('/api/saglik', { credentials: 'same-origin' })
    token = cerez('XSRF-TOKEN')
  }
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

interface IstekSecenekleri {
  yontem?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  govde?: unknown
  form?: FormData
  /** Giriş ucunda 401 beklenen bir sonuçtur; oturum düşürme tetiklenmemeli. */
  oturumKontrolunuAtla?: boolean
}

async function istek(yol: string, secenekler: IstekSecenekleri = {}): Promise<Response> {
  const yontem = secenekler.yontem ?? 'GET'
  const basliklar: Record<string, string> = {}
  let govde: BodyInit | undefined

  if (secenekler.form) {
    govde = secenekler.form
  } else if (secenekler.govde !== undefined) {
    basliklar['Content-Type'] = 'application/json'
    govde = JSON.stringify(secenekler.govde)
  }

  if (yontem !== 'GET') Object.assign(basliklar, await csrfBasligi())

  const yanit = await fetch(yol, { method: yontem, headers: basliklar, body: govde, credentials: 'same-origin' })

  if (yanit.ok) return yanit

  if (yanit.status === 401 && !secenekler.oturumKontrolunuAtla) oturumDustu()

  // Hata gövdesi JSON değilse (ör. vekil hatası) ham metin gösterilir — sessizce yutulmaz.
  const metin = await yanit.text()
  let kod = 'BILINMEYEN'
  let mesaj = metin || `Sunucu ${yanit.status} döndü.`
  try {
    const govdeJson = JSON.parse(metin) as { hata?: string; mesaj?: string }
    kod = govdeJson.hata ?? kod
    mesaj = govdeJson.mesaj ?? mesaj
  } catch {
    /* JSON değil: ham metin mesaj olarak kalır */
  }
  throw new ApiHatasi(yanit.status, kod, mesaj)
}

async function json<T>(yol: string, secenekler: IstekSecenekleri = {}): Promise<T> {
  const yanit = await istek(yol, secenekler)
  return (await yanit.json()) as T
}

/**
 * Dosya indirme. Sunucudan gelen `Content-Disposition` adı korunur: dosya adı belgenin kimliğidir
 * (`TTI_IZMIR_2026_KESIF_001_TEKLIF.pdf`) ve istemcide yeniden türetilmez.
 */
async function indir(yol: string, yedekAd?: string): Promise<void> {
  const yanit = await istek(yol)
  const basliktakiAd = yanit.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/)?.[1]
  const veri = await yanit.blob()

  const url = URL.createObjectURL(veri)
  const bag = document.createElement('a')
  bag.href = url
  bag.download = basliktakiAd ?? yedekAd ?? 'belge'
  document.body.appendChild(bag)
  bag.click()
  bag.remove()
  URL.revokeObjectURL(url)
}

/** İş süzgeçlerinin sorgu dizesi; boş bırakılan alan hiç eklenmez. */
function isSorgusu(suzgec: IsSuzgeci): string {
  const parametreler = new URLSearchParams()
  for (const [anahtar, deger] of Object.entries(suzgec)) {
    if (deger) parametreler.set(anahtar, deger)
  }
  const sorgu = parametreler.toString()
  return sorgu ? `?${sorgu}` : ''
}

export const api = {
  // --- Oturum -------------------------------------------------------------
  giris: (kullaniciAdi: string, sifre: string) =>
    json<Kullanici>('/api/auth/giris', {
      yontem: 'POST',
      govde: { kullaniciAdi, sifre },
      oturumKontrolunuAtla: true,
    }),
  cikis: () => istek('/api/auth/cikis', { yontem: 'POST' }).then(() => undefined),
  ben: () => json<Kullanici>('/api/auth/ben', { oturumKontrolunuAtla: true }),

  // --- Sabit listeler -----------------------------------------------------
  bolgeler: () => json<Bolge[]>('/api/bolgeler'),
  kapsamlar: () => json<Kapsam[]>('/api/kapsamlar'),
  sozlesmeler: () => json<Sozlesme[]>('/api/sozlesmeler'),
  /**
   * Kable araması — **bölge kısıtı yok** (Faz O): her kullanıcı her kabloyu seçebilir.
   *
   * Liste tamamı çekilmiyor: envanter 900+ satır ve arayüz `mtc` ön dolu bir kutu gösterip kullanıcı
   * yazdıkça süzüyor. Üç karakterden kısa arama sunucuda boş liste döner.
   *
   * `limit` **açıkça** gönderilir: sunucunun varsayılanına güvenmek, kırpmayı istemcide fark
   * edilemez kılıyordu — arayüz kaç sonucun kesildiğini bilemeden tam liste sanıyordu.
   */
  kableler: (ara: string, limit: number) =>
    json<Kable[]>(`/api/kableler?ara=${encodeURIComponent(ara)}&limit=${limit}`),

  // --- İşler --------------------------------------------------------------
  /** Süzgeçler **sunucuda** uygulanır; boş bırakılan alan sorguya hiç eklenmez. */
  isler: (suzgec: IsSuzgeci = {}) => json<Is[]>(`/api/isler${isSorgusu(suzgec)}`),
  /**
   * Listenin Excel hâli — **ekrandaki süzgeçlerle aynı**.
   *
   * Aynı sorgu dizesi kullanılıyor ki dosya, kullanıcının o an gördüğü listenin birebir aynısı olsun;
   * dosyayı sunucu üretiyor (istemcide Excel üretimi yasak, CLAUDE.md §13).
   */
  islerExcelIndir: (suzgec: IsSuzgeci = {}) => indir(`/api/isler/liste.xlsx${isSorgusu(suzgec)}`),
  is: (id: number) => json<Is>(`/api/isler/${id}`),
  isOlustur: (istekGovdesi: IsOlusturmaIstegi) => json<Is>('/api/isler', { yontem: 'POST', govde: istekGovdesi }),
  /**
   * İşin durumunu değiştirir — **yalnızca yönetici**, her durumdan her duruma (Faz V).
   * Gerekçe her geçişte zorunlu; müdahale sunucuda kayda geçer.
   *
   * Otomatik geçişler (fizibilite açılınca `ACIK`, hakediş onaylanınca `KAPANDI`) bu uçtan geçmez.
   */
  isDurumDegistir: (isId: number, durum: IsDurumu, gerekce: string) =>
    json<Is>(`/api/isler/${isId}/durum`, { yontem: 'POST', govde: { durum, gerekce } }),
  isDurumGecmisi: (isId: number) => json<DurumKaydi[]>(`/api/isler/${isId}/durum-gecmisi`),
  /**
   * **Önceki aşamaya iade** — projenin en ileri açık aşamasını geri alır (18 Ağustos 2026).
   *
   * Aşama iptal işaretlenir: listede "Henüz açılmadı — sırada bu adım var" hâline döner ve bölge
   * sorumlusu **her şeyi baştan girer** (miktarlar, EK1/EK2, hakedişte EK3). Tekrar çağrıldıkça
   * geriye yürür: hakediş → keşif → fizibilite. Gerekçe zorunlu, müdahale kayda geçer.
   */
  isIade: (isId: number, gerekce: string) =>
    json<Is>(`/api/isler/${isId}/iade`, { yontem: 'POST', govde: { gerekce } }),

  // --- Belgeler -----------------------------------------------------------
  /**
   * Belge listesi. Süzgeçler **sunucuda** uygulanır (SQL `where`).
   *
   * Boş bırakılan alan sorguya hiç eklenmez: `?tur=` göndermek sunucuda tip dönüşüm hatası verir.
   */
  belgeler: (suzgec: BelgeSuzgeci = {}) => {
    const parametreler = new URLSearchParams()
    for (const [anahtar, deger] of Object.entries(suzgec)) {
      if (deger) parametreler.set(anahtar, deger)
    }
    const sorgu = parametreler.toString()
    return json<BelgeListeSatiri[]>(`/api/belgeler${sorgu ? `?${sorgu}` : ''}`)
  },
  belge: (id: number) => json<Belge>(`/api/belgeler/${id}`),
  /**
   * Belgenin poz listesi — fiyatlar **belgenin çivilendiği sürümden** gelir.
   *
   * Belge türüne göre farklı uç çağırmak (fizibilitede sözleşme, keşif/hakedişte iş) yönetici
   * referans fiyatları güncellediği an ekran ile kaydı ayrıştırıyordu: ekranda yeni fiyat görünüyor,
   * belgeye eski fiyat yazılıyordu. Tek uç bu ayrışmayı yapısal olarak imkânsız kılıyor.
   */
  belgePozlari: (belgeId: number) => json<Poz[]>(`/api/belgeler/${belgeId}/pozlar`),
  /** Belge açılırken sözleşme **zorunlu**: belge daima bir fiyat sürümüne çivilidir (Faz N). */
  belgeOlustur: (isId: number, tur: BelgeTuru, sozlesmeKodu: string) =>
    json<Belge>(`/api/isler/${isId}/belgeler`, { yontem: 'POST', govde: { tur, sozlesmeKodu } }),
  /** Sözleşmeyi değiştirir — **girilmiş miktarlar silinir**; poz kimlikleri sözleşmeye özeldir. */
  belgeSozlesmesiDegistir: (belgeId: number, sozlesmeKodu: string) =>
    json<Belge>(`/api/belgeler/${belgeId}/sozlesme`, { yontem: 'PUT', govde: { sozlesmeKodu } }),
  /** Miktar girişi — **tam değiştirme**: gönderilmeyen poz belgeden silinir. */
  satirlariYaz: (belgeId: number, satirlar: { pozId: string; miktar: string }[]) =>
    json<Belge>(`/api/belgeler/${belgeId}/satirlar`, { yontem: 'PUT', govde: { satirlar } }),
  /**
   * Gönderir. Hedef duruma **tür** karar verir: fizibilite doğrudan tamamlanır, keşif ve hakediş
   * yönetici onayına düşer (Faz V). Karar sunucuda; web hangi türün onaya gittiğini bilmez.
   */
  gonder: (belgeId: number) => json<Belge>(`/api/belgeler/${belgeId}/gonder`, { yontem: 'POST' }),

  // --- Miktar Excel'i (Faz P) ---------------------------------------------
  /** Şablon **girilmiş miktarlarla dolu** iner: kullanıcı yalnızca değişecekleri düzeltir. */
  miktarSablonuIndir: (belgeId: number) => indir(`/api/belgeler/${belgeId}/miktar-sablonu.xlsx`),
  /** Doldurulmuş şablon — **tam değiştirme**. Tek sorunlu satır varsa hiçbir şey yazılmaz (400). */
  miktarYukle: (belgeId: number, dosya: File) => {
    const form = new FormData()
    form.append('dosya', dosya)
    return json<Belge>(`/api/belgeler/${belgeId}/miktar-yukleme`, { yontem: 'POST', form })
  },

  // --- Ekler --------------------------------------------------------------
  ekler: (belgeId: number) => json<Ek[]>(`/api/belgeler/${belgeId}/ekler`),
  ekYukle: (belgeId: number, tur: EkTuru, dosya: File) => {
    const form = new FormData()
    form.append('tur', tur)
    form.append('dosya', dosya)
    return json<Ek>(`/api/belgeler/${belgeId}/ekler`, { yontem: 'POST', form })
  },
  ekSil: (ekId: number) => istek(`/api/ekler/${ekId}`, { yontem: 'DELETE' }).then(() => undefined),
  ekIndir: (ekId: number) => indir(`/api/ekler/${ekId}`),

  /**
   * EK3 "TTI Kullanılan Malzemeler" — yalnızca hakediş aşamasında (18 Ağustos 2026).
   *
   * Yazma **tam değiştirmedir** ([satirlariYaz] ile aynı semantik): gönderilen liste ne ise belgenin
   * malzemeleri o olur. Tamamen boş satırları sunucu eler; miktarı sunucu ayrıştırır.
   */
  malzemeler: (belgeId: number) => json<Malzeme[]>(`/api/belgeler/${belgeId}/malzemeler`),
  malzemeleriYaz: (belgeId: number, malzemeler: Malzeme[]) =>
    json<Malzeme[]>(`/api/belgeler/${belgeId}/malzemeler`, { yontem: 'PUT', govde: { malzemeler } }),

  // --- Çıktı --------------------------------------------------------------
  pdfIndir: (belgeId: number, varyant: Varyant) => indir(`/api/belgeler/${belgeId}/pdf?varyant=${varyant}`),
  excelIndir: (belgeId: number, varyant: Varyant) => indir(`/api/belgeler/${belgeId}/excel?varyant=${varyant}`),

  // --- Yönetici: işleme al (onay), durum, iptal ---------------------------
  /** "İşleme Al": `ONAY_BEKLIYOR → ISLENIYOR` (hakediş: → `TAMAMLANDI`). Bir sonraki aşamayı açar. */
  belgeOnayla: (belgeId: number) => json<Belge>(`/api/belgeler/${belgeId}/onayla`, { yontem: 'POST' }),
  /** Serbest durum müdahalesi — beklet/işleme al/iptal dışındaki durumlar için; gerekçe zorunlu. */
  belgeDurumDegistir: (belgeId: number, durum: BelgeDurumu, gerekce: string) =>
    json<Belge>(`/api/belgeler/${belgeId}/durum`, { yontem: 'POST', govde: { durum, gerekce } }),
  belgeDurumGecmisi: (belgeId: number) => json<DurumKaydi[]>(`/api/belgeler/${belgeId}/durum-gecmisi`),
  /** Yanlış açılmış belgeyi çöpe atar; aynı iş o türde yeni bir belge açabilir. */
  belgeIptal: (belgeId: number, gerekce: string) =>
    json<Belge>(`/api/belgeler/${belgeId}/iptal`, { yontem: 'POST', govde: { gerekce } }),

  // --- Yönetici: sözleşmeler (Faz L) --------------------------------------
  /** Yönetim listesi: pasif sözleşmeler ve sürüm bilgisi dâhil. */
  sozlesmeYonetimi: () => json<SozlesmeYonetimSatiri[]>('/api/sozlesmeler/yonetim'),
  /** Dosyayı **yazmadan** işler: hangi kod/ad/başlık türeyecek, kaç poz var, kod çakışıyor mu. */
  sozlesmeOnizle: (dosya: File, sayfa?: string) => {
    const form = new FormData()
    form.append('dosya', dosya)
    if (sayfa) form.append('sayfa', sayfa)
    return json<SozlesmeOnizleme>('/api/sozlesmeler/onizleme', { yontem: 'POST', form })
  },
  /**
   * Sözleşme + poz listesi + ilk fiyat sürümü — sunucuda tek transaction.
   *
   * [ad] verilirse sözleşme adı olarak kullanılır; boş bırakılırsa dosyadan türeyen ad. Serbest
   * metindir — büyük harf, boşluk, `_` ve `-` dahil her şey kabul edilir.
   *
   * ~~`kod`~~ **18 Ağustos 2026'da kaldırıldı** (kullanıcı isteği): kod içsel bir anahtar (URL
   * parçası + tekillik kilidi), yöneticinin kararı değil. Sunucu onu dosyadan türetiyor ve çakışma
   * olursa boştaki ilk koda kayıyor — yükleme artık kod yüzünden reddedilmiyor.
   * ~~`oncekiKod` (revizyon)~~ aynı gün kaldırıldı: "Revize Et" akışı arayüzden çıktı. Sunucu iki
   * parametreyi de hâlâ tanıyor, web ikisini de göndermiyor.
   */
  sozlesmeYukle: (dosya: File, sayfa?: string, ad?: string) => {
    const form = new FormData()
    form.append('dosya', dosya)
    if (sayfa) form.append('sayfa', sayfa)
    if (ad && ad.trim()) form.append('ad', ad.trim())
    return json<SozlesmeYonetimSatiri>('/api/sozlesmeler', { yontem: 'POST', form })
  },
  /** Ad, başlık, aktiflik. Pasife çekmek yalnızca **yeni belge açılmasını** engeller. */
  sozlesmeBilgiGuncelle: (kod: string, alanlar: { ad: string; baslik: string; aktif: boolean }) =>
    json<SozlesmeYonetimSatiri>(`/api/sozlesmeler/${encodeURIComponent(kod)}`, { yontem: 'PUT', govde: alanlar }),
  /**
   * Sözleşmeyi siler — **yalnızca hiç belge açılmamışsa** (18 Ağustos 2026, kullanıcı isteği).
   *
   * Kullanımdaki sözleşmede sunucu **409** döner ve mesaj kaç belge olduğunu söyler; kullanımdan
   * çıkarmanın yolu orada pasife almaktır. Kontrol sunucuda: web'de tekrarlansaydı iki yerde yaşayan
   * ve biri unutulduğunda sessizce ayrışan ikinci bir kural olurdu.
   */
  sozlesmeSil: (kod: string) =>
    istek(`/api/sozlesmeler/${encodeURIComponent(kod)}`, { yontem: 'DELETE' }).then(() => undefined),
  /** Sözleşmeye yeni iş kalemi ekler ve güncel poz listesini döner (Faz V). */
  sozlesmePozEkle: (
    kod: string,
    kalem: { pozNo: string; ad: string; birim: string; birimFiyat: string; grupSatirKodu?: string },
  ) => json<Poz[]>(`/api/sozlesmeler/${encodeURIComponent(kod)}/pozlar`, { yontem: 'POST', govde: kalem }),
  /** Boş sözleşme formatı: doldurulup [sozlesmeYukle] ile geri yüklenir. */
  sozlesmeFormatiIndir: () => indir('/api/sozlesmeler/format.xlsx'),
  /**
   * Var olan sözleşmenin **dolu** `.xlsx` hâli.
   *
   * Sözleşme değiştirilemez (§7/4); bu dosya "düzelt ve yeni kodla yükle" akışının girdisidir ve
   * sütun düzeni boş şablonla aynıdır, yani doğrudan [sozlesmeYukle]'ye verilebilir.
   */
  sozlesmeIndir: (kod: string) => indir(`/api/sozlesmeler/${encodeURIComponent(kod)}/liste.xlsx`),
  /** Sözleşmenin poz listesi — satır menüsündeki "Poz listesini görüntüle". */
  sozlesmePozlari: (kod: string) => json<Poz[]>(`/api/sozlesmeler/${encodeURIComponent(kod)}/pozlar`),

  // --- Yönetici: kablo envanteri (Faz U) ----------------------------------
  /** Ekranın açılış çağrısı: yürürlükteki envanterin özeti + tüm sürümler. */
  kableEnvanteri: () => json<KableEnvanteri>('/api/kable-surumleri'),
  /**
   * Dosyayı sürüm olarak kaydeder — **canlı envanter değişmez**.
   *
   * Yanıttaki `fark`, yürürlüğe almadan önce görülen "ne olacak" özetidir. Ayrı bir "önizleme" ucu
   * yok: yükleme zaten yazmıyor, dolayısıyla ikinci bir çağrıya gerek kalmıyor.
   */
  kableSurumYukle: (dosya: File) => {
    const form = new FormData()
    form.append('dosya', dosya)
    return json<KableSurumYuklemeSonucu>('/api/kable-surumleri', { yontem: 'POST', form })
  },
  /** Sürümün yürürlükteki envantere göre farkı. Geri dönüş onayı da bunu kullanır. */
  kableSurumFarki: (id: number) => json<KableSurumFarki>(`/api/kable-surumleri/${id}/fark`),
  /** Sürümü yürürlüğe alır: `kable` tablosu bu sürümün izdüşümü olur. Geri dönüş de bu uçtur. */
  kableSurumYururlugeAl: (id: number) =>
    json<KableIzdusumSonucu>(`/api/kable-surumleri/${id}/yururluk`, { yontem: 'POST' }),
  /** Yürürlükte olmayan sürümü siler; yürürlüktekinde sunucu 409 döner. */
  kableSurumSil: (id: number) =>
    istek(`/api/kable-surumleri/${id}`, { yontem: 'DELETE' }).then(() => undefined),
  /**
   * Yürürlükteki envanterin `.xlsx` hâli — elle yapılan düzeltmeler dâhil (Faz V).
   *
   * Veritabanı asıl kaynak: yüklenen ham dosya saklanmıyor, bu çıktı her zaman güncel hâli üretiyor
   * ve doğrudan [kableSurumYukle]'ye geri verilebilir.
   */
  kableEnvanteriIndir: () => indir('/api/kable-surumleri/envanter.xlsx'),

  // --- Yönetici: elle kablo ekleme/düzenleme (Faz V) -----------------------
  /**
   * Yeni kablo. **Yeni sürüm doğmaz**: değişiklik yürürlükteki envantere anında yazılır.
   *
   * Konum yolu **sunucuda** kuruluyor: web segmentleri gönderir, ayraçları (`>>`, `>`) kendisi
   * birleştirmez — kural iki yerde yaşasaydı elle eklenen kablo dosyadan gelenden sessizce ayrışırdı.
   */
  kableEkle: (govde: KableIstegi) => json<Kable>('/api/kableler', { yontem: 'POST', govde }),
  /** Var olan kablonun düzeltilmesi; düzenleme formu konum yolunu hazır metin olarak gönderir. */
  kableGuncelle: (nosid: string, govde: KableIstegi) =>
    json<Kable>(`/api/kableler/${encodeURIComponent(nosid)}`, { yontem: 'PUT', govde }),

  // --- Yönetici: kullanıcılar (Faz I) -------------------------------------
  kullanicilar: () => json<YonetimKullanicisi[]>('/api/kullanicilar'),
  /** Yeni kullanıcı (madde 12). Bölge sorumlusunda `bolgeKodu` zorunlu; yöneticide `null`. */
  kullaniciOlustur: (govde: {
    kullaniciAdi: string
    adSoyad: string
    rol: Rol
    bolgeKodu: string | null
    yeniSifre: string
  }) => json<YonetimKullanicisi>('/api/kullanicilar', { yontem: 'POST', govde }),
  /** Ad soyad / rol / bölge düzenlemesi (madde 13); kullanıcı adı ve parola ayrı uçlardan. */
  kullaniciGuncelle: (id: number, govde: { adSoyad: string; rol: Rol; bolgeKodu: string | null }) =>
    json<YonetimKullanicisi>(`/api/kullanicilar/${id}`, { yontem: 'PUT', govde }),
  /** Kullanıcı adı giriş kimliğidir: değişince kullanıcı yeni adla giriş yapar. */
  kullaniciAdiGuncelle: (id: number, kullaniciAdi: string) =>
    json<YonetimKullanicisi>(`/api/kullanicilar/${id}/kullanici-adi`, { yontem: 'PUT', govde: { kullaniciAdi } }),
  parolaSifirla: (id: number, yeniSifre: string) =>
    json<YonetimKullanicisi>(`/api/kullanicilar/${id}/parola`, { yontem: 'PUT', govde: { yeniSifre } }),
  aktiflikDegistir: (id: number, aktif: boolean) =>
    json<YonetimKullanicisi>(`/api/kullanicilar/${id}/aktif`, { yontem: 'PUT', govde: { aktif } }),
}
