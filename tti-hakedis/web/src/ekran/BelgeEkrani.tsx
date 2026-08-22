import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiHatasi } from '../api'
import { DurumRozeti, SayfaBasligi, Serit, TUR_ADI, Yukleniyor, YuklemeHatasi } from '../bilesen'
import { git } from '../yonlendirme'
import { IslemOnayi } from './IslemOnayi'
import { BelgeEkleri } from './BelgeEkleri'
import { BelgeMalzemeleri } from './BelgeMalzemeleri'
import { BelgeDurumPaneli } from './DurumPaneli'
import { MiktarExcel } from './MiktarExcel'
import { Onizleme } from './Onizleme'
import { SozlesmeKarti } from './SozlesmeKarti'
import { TamamlananEkler } from './TamamlananEkler'
import { ekranBicimi, miktarGecerliMi, PozTablosu, sunucuBicimi } from './PozTablosu'
import type { Belge, Is, Kullanici, Poz } from '../tipler'

/** Miktar yazarken her tuşta istek gitmesin diye bekleme süresi. */
const BEKLEME_MS = 600

/**
 * Bir adımın durumu.
 *
 * `bekliyor` bilinçli olarak kullanılmıyor: üç adım da her zaman görünür ve doldurulabilir; sıra
 * kuralı işin durumundan gelir, adımlar arasından değil. Tip yine de tam: `kapali` (belge
 * düzenlenemez), `eksik` (gönderimi engelleyen bir şey var), `tamam`.
 */
type AdimDurumu = 'tamam' | 'eksik' | 'kapali'

/**
 * Gönderimi engelleyen bir eksik — ve **hangi adıma ait olduğu**.
 *
 * Kural sunucudadır (`BelgeServisi.ekleriDogrula`); buradaki liste onu tekrar yazmaz, önceden
 * bildirir. Cümleler tek bir yerde üretilir; hem adım başlığı hem alt şeritteki sayaç aynı diziyi
 * tüketir. Adım 1 (sözleşme) listede yer alamaz: belge fiyat sürümü olmadan yapısal olarak var
 * olamıyor (`belge.fiyat_versiyonu_id NOT NULL`) — tipin `2 | 3` olması bunun kaydı.
 */
/** Adım 4 yalnızca hakedişte var (EK3 malzeme listesi). */
type Eksik = { adim: 2 | 3 | 4; mesaj: string }

/**
 * Belge ekranı — üç belge türü için **tek** ekran.
 *
 * Sıra kullanıcı kararıdır (3 Ağustos 2026) ve üç aşamada da aynıdır:
 * 1. **Sözleşme** — belgenin birim fiyatlarının kaynağı; her aşama kendi sözleşmesini seçer (Faz N).
 * 2. **Miktar** — şablon indir/yükle **veya** tabloya elle giriş.
 * 3. **Dosyalar** — güzergâh dosyası (zorunlu) + görseller (opsiyonel), üç aşamada da (Faz P).
 *
 * [salt] `true` ise ekran **gözlem** modundadır: "Gözlemle" düğmesinden gelinir, hiçbir giriş açık
 * değildir ve yalnızca çıktı bölümü kullanılabilir.
 */
export function BelgeEkrani({ id, kullanici, salt = false }: { id: number; kullanici: Kullanici; salt?: boolean }) {
  const [belge, setBelge] = useState<Belge | null>(null)
  const [is, setIs] = useState<Is | null>(null)
  const [pozlar, setPozlar] = useState<Poz[] | null>(null)
  const [miktarlar, setMiktarlar] = useState<Record<string, string>>({})
  const [bekleyen, setBekleyen] = useState<Set<string>>(new Set())
  const [kaydediliyor, setKaydediliyor] = useState(false)
  const [hata, setHata] = useState<string | null>(null)
  /** Geçersiz miktar taşıyan poz kimlikleri — hem üstteki şerit hem satır kenarlığı bunu kullanır. */
  const [gecersiz, setGecersiz] = useState<string[]>([])
  const [gonderim, setGonderim] = useState<'gonderiliyor' | 'tamam' | null>(null)
  /**
   * EK3 malzeme listesinde kaydedilmemiş değişiklik var mı — kartın kendisi bildiriyor.
   *
   * Miktarlar gibi burada tutulmuyor: malzeme listesi kendi verisini çekiyor ve kaydediyor, tek
   * ihtiyaç "gönder"i kilitlemek için bir bayrak. Listeyi de yukarı taşımak, iki yerde yaşayan
   * ikinci bir kopya üretirdi.
   */
  const [malzemeKaydedilmedi, setMalzemeKaydedilmedi] = useState(false)
  // Kimliği sabit: `useEffect` bağımlılığı olduğu için her çizimde yenilenirse sonsuz döngü doğar
  // (aynı gerekçe `ek1Bildir`'de de yazılı).
  const malzemeBildir = useCallback((kaydedilmemisVar: boolean) => setMalzemeKaydedilmedi(kaydedilmemisVar), [])
  /** Kaydedilmemiş değişiklikle çıkılmak istendiğinde hedef rota tutulur; diyalog bunu kullanır. */
  const [cikisHedefi, setCikisHedefi] = useState<string | null>(null)
  /**
   * Güzergâh dosyası var mı. Kaynak bilgi `BelgeEkleri`'nde; o zaten kendi verisini çekiyor, bu
   * yüzden ikinci bir istek yapılmıyor — durumu yukarı bildiriyor.
   *
   * Callback `useCallback` ile sabitlenmek ZORUNDA: kimliği her render'da değişirse çocuktaki
   * `useEffect` yeniden koşar ve sonsuz istek döngüsü doğar.
   */
  const [ek1Var, setEk1Var] = useState(false)
  const ek1Bildir = useCallback((varMi: boolean) => setEk1Var(varMi), [])

  const zamanlayici = useRef<number | undefined>(undefined)
  // Bekleyen kaydetme isteği, zamanlayıcı tetiklendiğinde EN SON yazılan değerleri görmeli;
  // React state'i kapanışta (closure) donarsa bir tuş vuruşu sessizce kaybolur.
  const sonMiktarlar = useRef<Record<string, string>>({})

  /** Belge, iş ve poz listesini birlikte tazeler — sözleşme değişince poz listesi de değişir. */
  const yukle = useCallback(async (yeniBelge?: Belge) => {
    const yuklenen = yeniBelge ?? (await api.belge(id))
    const isKaydi = await api.is(yuklenen.isId)
    // Poz listesi belgenin **kendi** fiyat sürümünden gelir; türe göre farklı uç çağrılmıyor.
    // Eskiden fizibilite sözleşmenin canlı sürümünü okuyordu ve yönetici referans fiyatları
    // güncellediği an ekranda yeni fiyat görünüp belgeye eski fiyat yazılıyordu.
    const liste = await api.belgePozlari(yuklenen.id)

    const baslangic: Record<string, string> = {}
    for (const satir of yuklenen.satirlar) baslangic[satir.pozId] = ekranBicimi(satir.miktar)

    setBelge(yuklenen)
    setIs(isKaydi)
    setPozlar(liste)
    setMiktarlar(baslangic)
    setBekleyen(new Set())
    setGecersiz([])
    sonMiktarlar.current = baslangic
  }, [id])

  useEffect(() => {
    let iptal = false
    setHata(null)
    yukle().catch((e) => {
      if (!iptal) setHata(e instanceof ApiHatasi ? e.message : 'Belge yüklenemedi.')
    })
    return () => {
      iptal = true
      window.clearTimeout(zamanlayici.current)
    }
  }, [yukle])

  /**
   * Miktarları yazar. **Başarılıysa `true` döner** — çağıranın (özellikle [gonder]) sonucu
   * bilmesi zorunlu: geçersiz ya da kaydedilememiş bir değişiklikle gönderilen belge geri
   * alınamaz (D2) ve kullanıcının son yazdığı miktarı içermez.
   */
  const kaydet = useCallback(async (): Promise<boolean> => {
    const anlik = sonMiktarlar.current
    const bozukPozlar = Object.entries(anlik)
      .filter(([, deger]) => !miktarGecerliMi(deger))
      .map(([pozId]) => pozId)
    setGecersiz(bozukPozlar)
    if (bozukPozlar.length > 0) return false

    // Boş bırakılan poz gönderilmez: sunucu tam değiştirme yapıyor, yani gönderilmeyen satır
    // belgeden silinir. "Girilmemiş" ile "0 girilmiş" ayrımı böyle korunur (CLAUDE.md §5).
    const satirlar = Object.entries(anlik)
      .filter(([, deger]) => sunucuBicimi(deger) !== '')
      .map(([pozId, deger]) => ({ pozId, miktar: sunucuBicimi(deger) }))

    setKaydediliyor(true)
    setHata(null)
    try {
      setBelge(await api.satirlariYaz(id, satirlar))
      setBekleyen(new Set())
      return true
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Miktarlar kaydedilemedi.')
      return false
    } finally {
      setKaydediliyor(false)
    }
  }, [id])

  function miktarDegisti(pozId: string, deger: string) {
    const guncel = { ...sonMiktarlar.current, [pozId]: deger }
    sonMiktarlar.current = guncel
    setMiktarlar(guncel)
    setBekleyen((onceki) => new Set(onceki).add(pozId))

    window.clearTimeout(zamanlayici.current)
    zamanlayici.current = window.setTimeout(kaydet, BEKLEME_MS)
  }

  /** Kaydedilmemiş (bekleyen) değişiklik var mı — sekme kapatma ve ekran içi çıkış uyarısının koşulu. */
  const kirli = bekleyen.size > 0 || kaydediliyor

  // Sekme kapatma / yenileme / dış bağlantı: tarayıcının kendi "ayrılmak istiyor musunuz?" uyarısı.
  useEffect(() => {
    if (!kirli) return
    const uyar = (olay: BeforeUnloadEvent) => {
      olay.preventDefault()
      olay.returnValue = ''
    }
    window.addEventListener('beforeunload', uyar)
    return () => window.removeEventListener('beforeunload', uyar)
  }, [kirli])

  /** Ekran içi gezinme: kaydedilmemiş değişiklik varsa önce sor, yoksa doğrudan git. */
  function korumaliGit(hedef: string) {
    if (kirli) {
      window.clearTimeout(zamanlayici.current)
      setCikisHedefi(hedef)
    } else {
      git(hedef)
    }
  }

  async function gonder() {
    // EK3 kaydedilmemişse gönderme DURUR ve sebebi yazılır.
    //
    // Bu kontrol düğmeyi kapatmak yerine burada: kapalı düğmeye tıklamak hiçbir geri bildirim
    // üretmiyor ve kullanıcı "uyarmadı" diye görüyor (kullanıcı bildirimi, 19 Ağustos 2026).
    // Diğer eksikler düğmeyi kapatmaya devam ediyor — onlar veri eksikliği, bu ise "kaydetmeyi
    // unuttun" durumu ve tek tıkla düzeltiliyor.
    if (malzemeKaydedilmedi) {
      setHata(
        'Malzeme listesinde kaydedilmemiş değişiklik var. Önce "TTI Kullanılan Malzemeler" ' +
          'bölümünde "Malzemeleri Kaydet" deyin, sonra gönderin.',
      )
      return
    }

    // Bekleyen bir yazma varsa önce o tamamlanır: gönderme, son yazılan miktarı içermelidir.
    window.clearTimeout(zamanlayici.current)

    // Kaydetme başarısızsa GÖNDERİLMEZ. Aksi hâlde belge, kullanıcının son yazdığı miktarı
    // içermeyen hâliyle kapanırdı — ve gönderme geri alınamaz (D2). Hata şeridi zaten `kaydet`
    // içinde yazıldı; burada üstüne yazılmaz.
    if (!(await kaydet())) return

    setGonderim('gonderiliyor')
    setHata(null)
    try {
      setBelge(await api.gonder(id))
      setGonderim('tamam')
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Belge gönderilemedi.')
      // Hata şeridi katmanın altında kalmasın diye katman kapatılır.
      setGonderim(null)
    }
  }

  if (hata && !belge) return <YuklemeHatasi mesaj={hata} yenidenDene={() => void yukle()} />
  if (!belge || !is || !pozlar) return <Yukleniyor />

  /**
   * Düzenlenebilirlik **sunucudan gelir** (`DocumentStageRule.duzenlenebilirMi` sonucu). Buradaki
   * tek ek koşul, ekranın gözlem modunda açılmış olması.
   *
   * ~~Kural burada da yazılıydı~~ (Faz V'de kalktı): belge durumu + işin durumu ikilisi sunucuda,
   * burada ve mobilde üç kez duruyordu. `ONAY_BEKLIYOR` eklenirken biri unutulsaydı ekran kilitli
   * görünür ama sunucu yazmayı kabul ederdi — sessiz hata (CLAUDE.md §7/1).
   */
  const duzenlenebilir = !salt && belge.duzenlenebilir
  const yonetici = kullanici.rol === 'YONETICI'
  const kayitliSatirlar = new Map(belge.satirlar.map((satir) => [satir.pozId, satir]))
  const gecersizKume = new Set(gecersiz)
  const gecersizPozNolari = pozlar.filter((poz) => gecersizKume.has(poz.id)).map((poz) => poz.pozNo)
  // Ekranda girilmiş miktar var mı — henüz kaydedilmemiş olanlar dahil.
  const doluSatirVar = Object.values(miktarlar).some((deger) => sunucuBicimi(deger) !== '')

  // Gönderme ön koşulları — her madde ait olduğu adımı taşıyor (bkz. `Eksik`).
  const eksikler: Eksik[] = []
  if (!doluSatirVar) eksikler.push({ adim: 2, mesaj: 'En az bir poz için miktar girin.' })
  if (gecersiz.length > 0) eksikler.push({ adim: 2, mesaj: 'Geçersiz miktarları düzeltin.' })
  if (!ek1Var) eksikler.push({ adim: 3, mesaj: 'Güzergâh dosyasını (.kmz/.kml) yükleyin.' })
  // EK3 **boş bırakılabilir** (hiçbir alanı zorunlu değil), ama ekranda doldurulup kaydedilmemiş
  // satır varsa gönderim durur: kullanıcı malzemeleri girdiğini sanıp gönderir, belge onlarsız
  // kilitlenirdi ve fark ancak çıktı alınınca edilirdi.
  if (malzemeKaydedilmedi) {
    eksikler.push({ adim: 4, mesaj: 'Malzeme listesinde kaydedilmemiş değişiklik var — "Malzemeleri Kaydet" deyin.' })
  }

  const adimEksikleri = (adim: 2 | 3 | 4) => eksikler.filter((e) => e.adim === adim)
  const adimDurumu = (adim: 2 | 3 | 4): AdimDurumu =>
    !duzenlenebilir ? 'kapali' : adimEksikleri(adim).length > 0 ? 'eksik' : 'tamam'

  /** Ekranın modunu anlatan tek şerit — üst üste yığılan dört şeridin yerine. */
  const modNotu = salt
    ? 'Gözlem modu — belge salt okunur açıldı. Düzenlemek için proje listesinden "Güncelle" deyin.'
    : !duzenlenebilir
      ? `${belge.durumMetni} — bu aşama işleme alındı; artık değiştirilemez.`
      : `${belge.durumMetni} — yönetici işleme alana kadar düzeltme yapabilirsiniz.`

  return (
    <>
      <SayfaBasligi
        baslik={belge.belgeNo}
        // Etiketsiz `Ön Fizibilite · 2026-BURSA-001 · mtc079…` dizisinde ikinci ve üçüncü değerin
        // ne olduğu okunmuyordu.
        altBaslik={
          <>
            {TUR_ADI[belge.tur]} <span className="deger-etiket">SP No</span> {is.spNo}{' '}
            <span className="deger-etiket">Kablo ID</span> {is.kableNosid ?? '—'}
          </>
        }
      >
        <div className="dugme-grubu">
          <DurumRozeti durum={belge.durum} metin={belge.durumMetni} />
          <button className="ikincil" onClick={() => korumaliGit('/isler')}>
            Proje Listesi
          </button>
        </div>
      </SayfaBasligi>

      {/* Sayfa başında yalnızca iki şerit kalır: ekranın modu ve gerçek bir hata. Geçersiz miktar
          uyarısı ait olduğu adıma taşındı — üst üste dört şerit yığıldığında hiçbiri okunmuyordu. */}
      {modNotu && <Serit tur="bilgi">{modNotu}</Serit>}

      {hata && <Serit tur="hata">{hata}</Serit>}

      {/* --- Adım 1 — Sözleşme ------------------------------------------------ */}
      <SozlesmeKarti
        belge={belge}
        duzenlenebilir={duzenlenebilir}
        satirVar={belge.satirlar.length > 0}
        degisti={(yeni) => void yukle(yeni)}
        baslik={<AdimBasligi no={1} baslik="Sözleşme" durum={duzenlenebilir ? 'tamam' : 'kapali'} />}
      />

      {/* --- Adım 2 — Miktarlar ---------------------------------------------- */}
      <div className={adimDurumu(2) === 'eksik' ? 'kart eksik' : 'kart'}>
        <AdimBasligi
          no={2}
          baslik="Miktarlar"
          durum={adimDurumu(2)}
          not={<AdimEksikleri maddeler={adimEksikleri(2)} />}
          sag={
            duzenlenebilir && (
              <span className="adim-kayit">
                {kaydediliyor ? 'Kaydediliyor…' : bekleyen.size > 0 ? 'Kaydedilmemiş değişiklik var' : 'Kaydedildi'}
              </span>
            )
          }
        />
        {gecersiz.length > 0 && (
          <Serit tur="hata">
            Miktar yalnızca rakam ve tek ondalık ayırıcı içerebilir (örn. 12,5). <strong>Kaydedilmedi.</strong>
            <div className="kucuk">Düzeltilecek poz: {gecersizPozNolari.join(', ')}</div>
          </Serit>
        )}
        {duzenlenebilir && <MiktarExcel belgeId={belge.id} yuklendi={(yeni) => void yukle(yeni)} />}
        <PozTablosu
          pozlar={pozlar}
          miktarlar={miktarlar}
          kayitliSatirlar={kayitliSatirlar}
          bekleyen={bekleyen}
          gecersizPozlar={gecersizKume}
          duzenlenebilir={duzenlenebilir}
          miktarDegisti={miktarDegisti}
        />
      </div>

      {/* --- Adım 3 — Dosyalar ------------------------------------------------ */}
      {duzenlenebilir ? (
        <BelgeEkleri
          belgeId={belge.id}
          duzenlenebilir
          durumBildir={ek1Bildir}
          eksik={!ek1Var}
          baslik={
            <AdimBasligi
              no={3}
              baslik="Dosyalar"
              durum={adimDurumu(3)}
              not={<AdimEksikleri maddeler={adimEksikleri(3)} />}
            />
          }
        />
      ) : (
        <TamamlananEkler belge={belge} baslik={<AdimBasligi no={3} baslik="Dosyalar" durum="kapali" />} />
      )}

      {/* --- Adım 4 — EK3, YALNIZCA hakediş ------------------------------------
          Kart adım numarası alıyor ama `adimDurumu`/`eksikler` mekanizmasına **girmiyor**: üç
          alanı da opsiyonel olduğu için gönderimi hiçbir koşulda engellemiyor (kullanıcı kararı).
          Diğer iki aşamada kart hiç çizilmiyor — sunucu da o türlerde yazmayı 409 ile reddediyor. */}
      {belge.tur === 'HAKEDIS' && (
        <BelgeMalzemeleri
          belgeId={belge.id}
          duzenlenebilir={duzenlenebilir}
          durumBildir={malzemeBildir}
          eksik={malzemeKaydedilmedi}
          baslik={
            <AdimBasligi
              no={4}
              baslik="TTI Kullanılan Malzemeler"
              durum={adimDurumu(4)}
              not={<AdimEksikleri maddeler={adimEksikleri(4)} />}
            />
          }
        />
      )}

      {/* İndirme düğmeleri en altta: gönderilmiş bir belgede kullanıcı sayfanın ortasında
          PDF/Excel arıyordu. Yönetici iptal paneli (yıkıcı işlem) altta kalır — indirmeyi onun
          altına koymak yanlış bir sıralama olurdu. */}
      <Onizleme belge={belge} is={is} />

      {yonetici && !salt && (
        <div className="kart">
          <h2>Yönetici İşlemleri</h2>
          <BelgeDurumPaneli
            durum={belge.durum}
            sonAsama={belge.tur === 'HAKEDIS'}
            onayla={async () => setBelge(await api.belgeOnayla(belge.id))}
            durumDegistir={async (hedef, gerekce) => setBelge(await api.belgeDurumDegistir(belge.id, hedef, gerekce))}
          />
        </div>
      )}

      {gonderim && (
        <IslemOnayi
          durum={gonderim === 'gonderiliyor' ? 'calisiyor' : 'tamam'}
          mesaj={gonderim === 'gonderiliyor' ? 'Gönderiliyor…' : `${TUR_ADI[belge.tur]} belgesi sisteme yüklendi`}
          altMesaj={belge.durumMetni}
          kapat={() => setGonderim(null)}
          eylemler={<button onClick={() => git('/isler')}>Proje Listesine Git</button>}
        />
      )}

      {/* Kaydedilmemiş değişiklikle çıkış: kaydet ve çık / kaydetmeden çık / vazgeç. */}
      {cikisHedefi !== null && (
        <div className="katman" role="dialog" aria-modal="true">
          <div className="kart katman-kutu">
            <h3>Kaydedilmemiş değişiklik var</h3>
            <Serit tur="uyari">
              Bu belgede henüz kaydedilmemiş miktar değişikliği var. Çıkmadan önce kaydetmek ister misiniz?
            </Serit>
            {hata && <Serit tur="hata">{hata}</Serit>}
            <div className="dugme-grubu">
              <button
                disabled={kaydediliyor}
                onClick={async () => {
                  if (await kaydet()) {
                    const hedef = cikisHedefi ?? '/isler'
                    setCikisHedefi(null)
                    git(hedef)
                  }
                }}
              >
                {kaydediliyor ? 'Kaydediliyor…' : 'Kaydet ve çık'}
              </button>
              <button
                className="tehlike"
                disabled={kaydediliyor}
                onClick={() => {
                  const hedef = cikisHedefi ?? '/isler'
                  setCikisHedefi(null)
                  git(hedef)
                }}
              >
                Kaydetmeden çık
              </button>
              <button className="ikincil" disabled={kaydediliyor} onClick={() => setCikisHedefi(null)}>
                Vazgeç
              </button>
            </div>
          </div>
        </div>
      )}

      {/*
        Alt şerit yalnızca **toplam** ve **Gönder** taşır. Eksiklerin madde listesi ve kaydetme
        durumu ait oldukları adımlara taşındı: engel, engellediği şeyin yanında durmalı — alt
        şeritte tarif ettiği boş miktar hücresinden bir ekran boyu uzaktaydı.
      */}
      <div className="toplam-serit">
        <span>Toplam</span>
        {/* Toplam sunucudan gelir; web toplama yapmaz (CLAUDE.md §13). */}
        <span className="tutar">{belge.toplamGosterim}</span>
        <span style={{ flex: 1 }} />
        {/* "Gönder" yalnızca **taslakta** anlamlı; onay bekleyen belge yeniden gönderilmez, düzeltilir
            ve yönetici onaylar. Kararı sunucu veriyor (`gonderilebilir`), web türetmiyor. */}
        {belge.gonderilebilir && !salt && (
          <div className="gonder-alani">
            {/* Kaydedilmemiş malzeme, gönderimi engelleyen diğer eksiklerden ayrı yazılıyor: tek
                tıkla düzeltilebilen bir unutma ve kullanıcı ne yapacağını doğrudan okumalı. */}
            {malzemeKaydedilmedi && (
              <span className="dugme-notu">Önce “Malzemeleri Kaydet” deyin.</span>
            )}
            {eksikler.some((e) => e.adim !== 4) && (
              <span className="dugme-notu">
                Gönderilemiyor: yukarıda {eksikler.filter((e) => e.adim !== 4).length} eksik işaretli.
              </span>
            )}
            {/* EK3 eksiği düğmeyi KAPATMAZ (bkz. `gonder`): kapalı düğme tıklanınca sebebini
                söyleyemiyor. Tıklanır bırakılıp uyarı gönderme anında veriliyor. */}
            <button
              onClick={gonder}
              disabled={gonderim !== null || kaydediliyor || eksikler.some((e) => e.adim !== 4)}
            >
              {gonderim === 'gonderiliyor' ? 'Gönderiliyor…' : 'Gönder'}
            </button>
          </div>
        )}
      </div>
    </>
  )
}

/**
 * Numaralı adım başlığı.
 *
 * Üç adım (sözleşme → miktar → dosyalar) daha önce yalnızca kod yorumlarında numaralıydı; ekranda
 * eşit ağırlıkta dört kart görünüyor ve sıra ancak deneyerek öğreniliyordu.
 *
 * `bilesen.tsx`'e taşınmadı: üç çağrı yerinin üçü de bu dosyada, tek tüketicili bir "ortak" katman
 * CLAUDE.md §13'ün yasakladığı spekülatif soyutlama olurdu.
 */
function AdimBasligi({
  no,
  baslik,
  durum,
  not,
  sag,
}: {
  /** 4 yalnızca hakedişteki EK3 kartında kullanılır; diğer aşamalarda akış 3 adımda biter. */
  no: 1 | 2 | 3 | 4
  baslik: string
  durum: AdimDurumu
  /** Adımın kendi eksikleri. */
  not?: ReactNode
  /** Sağa yaslanan küçük durum metni (ör. kaydetme durumu). */
  sag?: ReactNode
}) {
  return (
    <div className="adim-basligi">
      <span className={`adim-no ${durum}`} aria-hidden="true">
        {durum === 'tamam' ? '✓' : no}
      </span>
      <div className="adim-metin">
        <h2>{baslik}</h2>
        {not}
      </div>
      {sag && <div className="adim-sag">{sag}</div>}
    </div>
  )
}

function AdimEksikleri({ maddeler }: { maddeler: Eksik[] }) {
  if (maddeler.length === 0) return null
  return (
    <ul className="adim-eksikleri">
      {maddeler.map((eksik) => (
        <li key={eksik.mesaj}>{eksik.mesaj}</li>
      ))}
    </ul>
  )
}
