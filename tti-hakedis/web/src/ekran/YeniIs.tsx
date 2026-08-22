import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { api, ApiHatasi } from '../api'
import { SayfaBasligi, Serit, useKatman, Yukleniyor, YuklemeHatasi } from '../bilesen'
import { git } from '../yonlendirme'
import { ucTam, UcSegmentleri } from './UcSegmentleri'
import type { Is, Kable, Kapsam, Kullanici } from '../tipler'

/** Kable araması için sunucuya gitmeden önce beklenen süre. */
const ARAMA_BEKLEME_MS = 300

/** Sunucu bu uzunluğun altındaki aramada boş liste döner (`KableSorgulari.EN_AZ_ARAMA`). */
const EN_AZ_ARAMA = 3

/**
 * Bir aramada gösterilen en fazla kablo. Envanterin tamamı 900+ satır; `mtc2` gibi kısa bir arama
 * yüzlerce satır getirir ve tabloya sığmaz.
 *
 * Liste bu sayıya değdiğinde kullanıcıya **söyleniyor**: sessiz kırpma, aradığı kabloyu listede
 * göremeyen kullanıcıya "bu kablo sistemde yok" dedirtiyordu.
 */
const ARAMA_SINIRI = 50

/**
 * Ekran 1 — yeni iş. Dört alan kaldı; doğrulamanın **kararı sunucudadır** (`WorkOrderValidator`),
 * buradaki kontrol yalnızca "Kaydet" düğmesini erken açmamak içindir.
 *
 * Faz N/O'da üç alan ekrandan çıktı:
 * - **Bölge** — oturumdan gelir; iş, işi açan kullanıcının bölgesine yazılır.
 * - **SP No** — sunucu üretir (`2026-BURSA-001`), elle yazılmaz.
 * - **Sözleşme** — artık işin değil, her belgenin kendi seçimi.
 */
export function YeniIs({ kullanici }: { kullanici: Kullanici }) {
  const [kapsamlar, setKapsamlar] = useState<Kapsam[]>([])
  const [yukleniyor, setYukleniyor] = useState(true)

  const [kapsamKodu, setKapsamKodu] = useState('')
  const [route, setRoute] = useState('')
  const [ekAraligi, setEkAraligi] = useState('')

  // Kutu `mtc` ön dolu açılır (kullanıcı kararı): envanterdeki her kimlik bu önekle başlıyor ve
  // kullanıcı yalnızca numarayı yazıyor.
  const [arama, setArama] = useState('mtc')
  const [kableler, setKableler] = useState<Kable[]>([])
  const [araniyor, setAraniyor] = useState(false)
  const [seciliKable, setSeciliKable] = useState<Kable | null>(null)
  /**
   * Kablosuz projede elle girilen uç konumları (Faz W). Yolu **sunucu** kurar; web ayraçları
   * birleştirmez (bkz. `UcSegmentleri`).
   */
  const [segmentlerA, setSegmentlerA] = useState<Record<string, string>>({})
  const [segmentlerB, setSegmentlerB] = useState<Record<string, string>>({})

  const [hata, setHata] = useState<string | null>(null)
  const [kaydediliyor, setKaydediliyor] = useState(false)
  const [olusanIs, setOlusanIs] = useState<Is | null>(null)
  /** Dokunulmuş alanlar — kırmızı uyarı yalnızca bunlarda çıkar (bkz. `Giris`). */
  const [dokunulan, setDokunulan] = useState<Set<string>>(new Set())

  const zamanlayici = useRef<number | undefined>(undefined)

  const kapsamlariCek = useCallback(() => {
    setHata(null)
    setYukleniyor(true)
    api
      .kapsamlar()
      .then(setKapsamlar)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Listeler yüklenemedi.'))
      .finally(() => setYukleniyor(false))
  }, [])

  useEffect(kapsamlariCek, [kapsamlariCek])

  function dokunuldu(alan: string) {
    setDokunulan((onceki) => new Set(onceki).add(alan))
  }

  /**
   * Kablo bu kapsamda zorunlu mu — **sunucu söyler** (`Kapsam.kableZorunlu`). Web
   * `kod === 'YENI_TESIS'` diye kendi listesini tutmaz (CLAUDE.md §7/1).
   *
   * Kapsam henüz seçilmemişken zorunlu sayılıyor: sunucudaki `WorkOrderValidator` de öyle davranıyor
   * ve ikisi ayrışsaydı düğme açık görünüp istek 400 alırdı.
   */
  const kableZorunlu = kapsamlar.find((k) => k.kod === kapsamKodu)?.kableZorunlu ?? true

  /**
   * Kable araması **sunucuda** yapılır (Faz O).
   *
   * Envanterin tamamı artık çekilmiyor: bölge kısıtı kalktığı için liste 900+ satıra çıktı ve her
   * açılışta indirmek anlamsız. Kullanıcı `mtc` sonrasına numarayı yazdıkça sonuç geliyor.
   *
   * **Kablonun zorunlu olmadığı kapsamda arama hiç yapılmaz** (Faz W): orada iş belirli bir kablo
   * kimliğinin kapsamında değil, dolayısıyla envanterde eşleşme aramak anlamsız — kullanıcı yazdığı
   * her değerde "Eşleşen kablo yok." uyarısı görüyordu.
   */
  useEffect(() => {
    window.clearTimeout(zamanlayici.current)
    if (seciliKable || !kableZorunlu) {
      setKableler([])
      setAraniyor(false)
      return
    }

    const desen = arama.trim()
    if (desen.length < EN_AZ_ARAMA) {
      setKableler([])
      setAraniyor(false)
      return
    }

    setAraniyor(true)
    zamanlayici.current = window.setTimeout(() => {
      api
        .kableler(desen, ARAMA_SINIRI)
        .then(setKableler)
        .catch(() => setKableler([]))
        .finally(() => setAraniyor(false))
    }, ARAMA_BEKLEME_MS)

    return () => window.clearTimeout(zamanlayici.current)
  }, [arama, seciliKable, kableZorunlu])

  /**
   * Eksik alanların adları — hem düğmenin altındaki nötr not hem `eksik` bayrağı buradan türer.
   * Tek kaynak: iki yerde ayrı ayrı sayılsa biri güncellendiğinde diğeri sessizce eskirdi.
   */
  /** Uç segmentleri yalnızca kablonun zorunlu olmadığı kapsamda (Yeni Tesis) girilir — orada zorunlu. */
  const uclarElleGiriliyor = !kableZorunlu

  const eksikAlanlar: string[] = []
  if (!kapsamKodu) eksikAlanlar.push('Kapsam')
  if (kableZorunlu && !seciliKable) eksikAlanlar.push('Kablo')
  if (!route.trim()) eksikAlanlar.push('Route')
  if (!ekAraligi.trim()) eksikAlanlar.push('Ek Aralığı')
  // Yeni Tesis'te Kablo ID sistemce atanır; bunun yerine uç adresleri (ülke/şehir/adres) zorunlu.
  if (uclarElleGiriliyor && !ucTam(segmentlerA)) eksikAlanlar.push('Uç A (ülke/şehir/adres)')
  if (uclarElleGiriliyor && !ucTam(segmentlerB)) eksikAlanlar.push('Uç B (ülke/şehir/adres)')
  const eksik = eksikAlanlar.length > 0

  /**
   * Kablo arama bölümünün durumu.
   *
   * Beş dal daha önce tek bir iç içe üçlü ifadeydi ve hangi durumun ne çizdiği okunmuyordu. Dalların
   * kendisi değişmedi, yalnızca adlandı.
   */
  const aramaDurumu: 'secili' | 'kisa' | 'araniyor' | 'bos' | 'sonuc' = seciliKable
    ? 'secili'
    : arama.trim().length < EN_AZ_ARAMA
      ? 'kisa'
      : araniyor
        ? 'araniyor'
        : kableler.length === 0
          ? 'bos'
          : 'sonuc'

  async function kaydet() {
    setHata(null)
    setKaydediliyor(true)
    try {
      const olusan = await api.isOlustur({
        kapsamKodu,
        // Zorunlu kapsamda envanterden seçilen kablonun nosid'i; Yeni Tesis'te Kablo ID'yi **sunucu
        // üretir** (mtc…), istemci göndermez.
        kableNosid: kableZorunlu ? seciliKable?.nosid || undefined : undefined,
        route: route.trim(),
        ekAraligi: ekAraligi.trim(),
        // Yeni Tesis'te uçlar zorunlu ve daima gönderilir; sunucu yolu kurar. Kablo seçili
        // kapsamlarda uçlar envanterden gelir, gönderilmez.
        ucA: uclarElleGiriliyor ? { segmentler: segmentlerA } : undefined,
        ucB: uclarElleGiriliyor ? { segmentler: segmentlerB } : undefined,
      })
      // Uyarılar iş oluştuktan SONRA gösterilir ve **hiçbir şeyi durdurmaz** (D1/4): aynı kablo
      // üzerinde birden fazla iş meşrudur. Onay diyaloğu yapılmaz — kullanıcıyı meşru bir işlemde
      // ikinci kez onaylatmak gereksiz sürtünme (D1/5).
      setOlusanIs(olusan)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Proje oluşturulamadı.')
    } finally {
      setKaydediliyor(false)
    }
  }

  if (yukleniyor) return <Yukleniyor />

  /**
   * Proje açıldı bildirimi — **katman** olarak (kullanıcı isteği, 4 Ağustos 2026).
   *
   * Ayrı bir başarı sayfası yerine açılır pencere: kullanıcı arkada doldurduğu formu görmeye devam
   * ediyor, dolayısıyla numaranın hangi projeye ait olduğu bağlamdan kopmuyor. Kapatma yolu tek:
   * proje listesine gitmek — açılan projenin sıradaki adımı zaten orada.
   */
  const projeAcildiKatmani = olusanIs && (
    // Kapatma yolu bilinçli olarak tek: proje listesine gitmek. Escape verilmiyor — kullanıcı
    // numarayı okumadan katmanı kapatıp "hangi proje açıldı" sorusuyla kalmasın.
    <ProjeAcildi>
      <div className="kart katman-kutu">
        <h3>Bu proje açıldı</h3>
        <Serit tur="basari">
          <strong>{olusanIs.spNo}</strong> projesi açıldı
          {olusanIs.kableNosid && ` (${olusanIs.kableNosid})`}. Numara sunucu tarafından üretildi.
        </Serit>
        {olusanIs.uyarilar.map((uyari) => (
          <Serit key={uyari.kod} tur="uyari">
            {uyari.mesaj}
          </Serit>
        ))}
        <Serit tur="bilgi">
          Sıradaki adım: proje listesinde bu projenin <strong>Ön Fizibilite</strong> satırındaki
          "Güncelle" düğmesine basıp sözleşmeyi seçin.
        </Serit>
        <div className="dugme-grubu">
          <button onClick={() => git('/isler')}>Proje Listesine Git</button>
        </div>
      </div>
    </ProjeAcildi>
  )

  return (
    <>
      {projeAcildiKatmani}
      <SayfaBasligi baslik="Yeni Proje" />
      {/* Kapsam listesi gelmediyse form doldurulamaz; tek çıkış yolu tekrar denemek. */}
      {hata && kapsamlar.length === 0 ? (
        <YuklemeHatasi mesaj={hata} yenidenDene={kapsamlariCek} />
      ) : (
        hata && <Serit tur="hata">{hata}</Serit>
      )}

      <div className="kart">
        <h2>Tanım</h2>
        <div className="satir">
          <div className="alan">
            <label htmlFor="bolge">Bölge</label>
            {/* Salt okunur bilgi: iş her koşulda oturumdaki kullanıcının bölgesine açılır (Faz O). */}
            <input id="bolge" value={kullanici.bolgeKodu ?? '—'} disabled />
          </div>

          <div className="alan">
            <label htmlFor="kapsam">Kapsam</label>
            <select
              id="kapsam"
              value={kapsamKodu}
              onBlur={() => dokunuldu('kapsam')}
              onChange={(e) => setKapsamKodu(e.target.value)}
            >
              <option value="">Seçiniz…</option>
              {kapsamlar.map((k) => (
                <option key={k.kod} value={k.kod}>
                  {k.ad}
                </option>
              ))}
            </select>
            {dokunulan.has('kapsam') && !kapsamKodu && <div className="alan-hata">Kapsam seçilmelidir.</div>}
          </div>
        </div>
        <div className="bos-not">
          SP No sunucu tarafından üretilir; sözleşme her belge açılırken ayrıca seçilir.
        </div>
      </div>

      {/*
        Kablo kartı kapsama göre **iki ayrı moddur** (Faz W):

        - Kablo zorunluysa (dört kapsam): envanter araması + sonuç tablosu, bugünkü akış.
        - Zorunlu değilse (Yeni Tesis): **arama yok**. İş belirli bir kablo kimliğinin kapsamında
          değil, dolayısıyla envanterde eşleşme aramak anlamsızdı ve kullanıcı yazdığı her değerde
          "Eşleşen kablo yok." uyarısı görüyordu. Yazılan değer olduğu gibi kaydedilir; uçlar da
          elle girilir.
      */}
      <div className="kart">
        <h2>Kablo</h2>

        {kableZorunlu ? (
          <KableSecimi
            arama={arama}
            aramaDegisti={(deger) => {
              setArama(deger)
              setSeciliKable(null)
            }}
            durum={aramaDurumu}
            kableler={kableler}
            seciliKable={seciliKable}
            sec={setSeciliKable}
          />
        ) : (
          <>
            <p className="bos-not">
              Bu kapsamda iş belirli bir envanter kablosunun kapsamında değildir. <strong>Kablo ID
              sistem tarafından otomatik atanır</strong> (mtc…); envantere eklenmez. Uç konumlarını
              aşağıda girmeniz zorunludur.
            </p>
            <div className="alan">
              <label htmlFor="kableAuto">Kablo ID</label>
              <input id="kableAuto" value="Sistem atayacak (mtc…)" disabled />
            </div>

            <div className="uc-girisi">
              <p className="bos-not">
                Uç A ve Uç B için <strong>ülke, şehir ve adres</strong> zorunludur; bina, kat, oda …
                segmentleri opsiyoneldir. Girilen konum, envanterden gelen adreslerle aynı biçimde
                saklanır; boş segmentler yazılmaz.
              </p>
              <div className="uc-izgara">
                <UcSegmentleri baslik="UÇ A" yon="A" degerler={segmentlerA} degistir={setSegmentlerA} zorunlu />
                <UcSegmentleri baslik="UÇ B" yon="B" degerler={segmentlerB} degistir={setSegmentlerB} zorunlu />
              </div>
            </div>
          </>
        )}
      </div>

      <div className="kart">
        <h2>Proje Tanımlayıcıları</h2>
        <div className="satir">
          <div className="alan">
            <label htmlFor="route">Route</label>
            <input
              id="route"
              value={route}
              onBlur={() => dokunuldu('route')}
              onChange={(e) => setRoute(e.target.value)}
            />
            {dokunulan.has('route') && !route.trim() && <div className="alan-hata">Route zorunludur.</div>}
          </div>
          <div className="alan">
            <label htmlFor="ekAraligi">Ek Aralığı</label>
            <input
              id="ekAraligi"
              value={ekAraligi}
              onBlur={() => dokunuldu('ekAraligi')}
              onChange={(e) => setEkAraligi(e.target.value)}
            />
            {dokunulan.has('ekAraligi') && !ekAraligi.trim() && (
              <div className="alan-hata">Ek Aralığı zorunludur.</div>
            )}
          </div>
        </div>
        <div className="dugme-grubu bosluk-ust">
          <button onClick={kaydet} disabled={eksik || kaydediliyor}>
            {kaydediliyor ? 'Kaydediliyor…' : 'Projeyi Oluştur'}
          </button>
          <button className="ikincil" onClick={() => git('/isler')}>
            Vazgeç
          </button>
        </div>
        {/* Nötr ve baştan görünür: eksiği söyler, kullanıcıyı suçlamaz. */}
        {eksik && <div className="dugme-notu bosluk-ust">Kaydetmek için gereken: {eksikAlanlar.join(', ')}.</div>}
      </div>
    </>
  )
}

/** Proje açıldı katmanı — kaydırma kilidi var, Escape yok (tek çıkış proje listesi). */
function ProjeAcildi({ children }: { children: ReactNode }) {
  useKatman()
  return (
    <div className="katman" role="dialog" aria-modal="true">
      {children}
    </div>
  )
}

/**
 * Envanterden kablo seçimi — kablonun **zorunlu olduğu** kapsamlarda kullanılır.
 *
 * Kablonun zorunlu olmadığı kapsamda (Yeni Tesis) bu bölüm hiç çizilmez: orada iş belirli bir kablo
 * kimliğinin kapsamında değil, dolayısıyla envanterde eşleşme aramak anlamsız (Faz W).
 */
function KableSecimi({
  arama,
  aramaDegisti,
  durum,
  kableler,
  seciliKable,
  sec,
}: {
  arama: string
  aramaDegisti: (deger: string) => void
  durum: 'secili' | 'kisa' | 'araniyor' | 'bos' | 'sonuc'
  kableler: Kable[]
  seciliKable: Kable | null
  sec: (kable: Kable | null) => void
}) {
  return (
    <>
      <div className="alan">
        <label htmlFor="kableArama">Kablo ID</label>
        <input id="kableArama" value={arama} placeholder="mtc…" onChange={(e) => aramaDegisti(e.target.value)} />
      </div>

      {durum === 'secili' && seciliKable && (
        <Serit tur="bilgi">
          <strong>{seciliKable.nosid}</strong>
          {/* Şehir ayrıca yazılmıyor: konum yolunun ikinci segmenti zaten şehir (`KableXlsxParser`). */}
          <div className="kucuk">Uç A: {seciliKable.adresA}</div>
          <div className="kucuk">Uç B: {seciliKable.adresB ?? '—'}</div>
          <div className="kucuk">Bölge: {seciliKable.bolgeKodu}</div>
          <button className="sade" onClick={() => sec(null)}>
            Değiştir
          </button>
        </Serit>
      )}

      {durum === 'kisa' && (
        <div className="bos-not">Aramak için en az {EN_AZ_ARAMA} karakter yazın (ör. mtc296).</div>
      )}

      {durum === 'araniyor' && <Yukleniyor metin="Aranıyor…" />}

      {durum === 'bos' && <div className="bos-not">Eşleşen kablo yok.</div>}

      {durum === 'sonuc' && (
        <>
          {kableler.length === ARAMA_SINIRI && (
            <div className="bos-not">
              İlk {ARAMA_SINIRI} sonuç gösteriliyor; aradığınız kablo listede yoksa numaranın devamını
              yazarak aramayı daraltın.
            </div>
          )}
          <div className="tablo-sarici" style={{ maxHeight: 320, overflowY: 'auto' }}>
            <table>
              <thead>
                <tr>
                  <th>Kablo ID</th>
                  <th>Bölge</th>
                  <th>Uç A</th>
                  <th>Uç B</th>
                </tr>
              </thead>
              <tbody>
                {kableler.map((k) => (
                  <tr key={k.nosid} className="tiklanabilir" onClick={() => sec(k)}>
                    <td>{k.nosid}</td>
                    <td className="kucuk">{k.bolgeKodu}</td>
                    {/* Konum yolu 240 karaktere kadar çıkıyor; listede kırpılıyor, seçilince
                        yukarıdaki şeritte tam hâliyle görünüyor. */}
                    <td className="kucuk kirpik" title={k.adresA}>
                      {k.adresA}
                    </td>
                    <td className="kucuk kirpik" title={k.adresB ?? ''}>
                      {k.adresB ?? '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </>
  )
}
