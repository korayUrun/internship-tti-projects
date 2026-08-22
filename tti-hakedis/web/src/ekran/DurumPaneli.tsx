import { useState } from 'react'
import type { ReactNode } from 'react'
import { ApiHatasi } from '../api'
import { Serit, useKatman } from '../bilesen'
import type { BelgeDurumu, IsDurumu } from '../tipler'

/**
 * Gerekçeli onay katmanı — yöneticinin her durum müdahalesinin ortak kabuğu (Faz V).
 *
 * Gerekçe **her geçişte zorunlu**: yetki genişledi (yönetici artık her durumdan her duruma
 * geçebiliyor), dolayısıyla "bu kapanmış proje neden yeniden açıldı" sorusunun cevabı kayıtta
 * durmak zorunda. Sunucu da gerekçesiz isteği 400 ile reddediyor.
 *
 * Kırmızı uyarı katman açılır açılmaz çıkmaz: kullanıcı daha hiçbir şey yapmadan hatalı sayılıyordu.
 * Nötr not baştan görünür (düğmenin neden kapalı olduğunu söyler), kırmızı uyarı ancak alana
 * dokunulup boş bırakıldığında çıkar (CLAUDE.md §8).
 */
export function GerekceKatmani({
  baslik,
  aciklama,
  onayMetni,
  yikici = false,
  vazgec,
  onayla,
  children,
}: {
  baslik: string
  aciklama: ReactNode
  onayMetni: string
  /** Kırmızı çerçeve + kırmızı düğme: geri alınması zor ya da veri kaybettiren işlemler. */
  yikici?: boolean
  vazgec: () => void
  onayla: (gerekce: string) => Promise<void>
  /** Gerekçenin üstüne giren ek alanlar (ör. hedef durum seçimi). */
  children?: ReactNode
}) {
  const [gerekce, setGerekce] = useState('')
  const [dokunuldu, setDokunuldu] = useState(false)
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  useKatman(calisiyor ? undefined : vazgec)

  async function calistir() {
    setHata(null)
    setCalisiyor(true)
    try {
      await onayla(gerekce.trim())
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'İşlem tamamlanamadı.')
    } finally {
      setCalisiyor(false)
    }
  }

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className={yikici ? 'kart katman-kutu yikici' : 'kart katman-kutu'}>
        <h3>{baslik}</h3>
        <Serit tur="uyari">{aciklama}</Serit>
        {hata && <Serit tur="hata">{hata}</Serit>}

        {children}

        <div className="alan">
          <label htmlFor="durumGerekce">Gerekçe (zorunlu)</label>
          <textarea
            id="durumGerekce"
            rows={3}
            value={gerekce}
            autoFocus
            onBlur={() => setDokunuldu(true)}
            onChange={(e) => setGerekce(e.target.value)}
          />
        </div>

        <div className="dugme-grubu">
          <button
            className={yikici ? 'tehlike' : undefined}
            disabled={gerekce.trim() === '' || calisiyor}
            onClick={() => void calistir()}
          >
            {calisiyor ? 'Uygulanıyor…' : onayMetni}
          </button>
          <button className="ikincil" disabled={calisiyor} onClick={vazgec}>
            Vazgeç
          </button>
        </div>
        <GerekceNotu gerekce={gerekce} dokunuldu={dokunuldu} />
      </div>
    </div>
  )
}

/** Yöneticinin serbestçe seçebildiği proje durumları — yeknesek dört değer. */
const IS_DURUMLARI: { deger: IsDurumu; ad: string }[] = [
  { deger: 'ONAY_BEKLIYOR', ad: 'Onay Bekliyor' },
  { deger: 'ISLENIYOR', ad: 'İşleniyor' },
  { deger: 'TAMAMLANDI', ad: 'Tamamlandı' },
  { deger: 'IPTAL', ad: 'İptal Edildi' },
]

/** Belge (aşama) durumları — yeknesek dört değer. */
const BELGE_DURUMLARI: { deger: BelgeDurumu; ad: string }[] = [
  { deger: 'ONAY_BEKLIYOR', ad: 'Onay Bekliyor (geri aç)' },
  { deger: 'ISLENIYOR', ad: 'İşleniyor' },
  { deger: 'TAMAMLANDI', ad: 'Tamamlandı' },
  { deger: 'IPTAL', ad: 'İptal Edildi' },
]

/**
 * İşin durumunu yöneten panel — **yalnızca yönetici** (yetki sunucuda uca bağlı, burada ekran gizlenir).
 *
 * İki hızlı eylem (**İşleme Al**, **İptal Et**) ve bir serbest geçiş (**Durumu Değiştir**). Hızlı
 * eylemler serbest geçişin özel hâli; ayrı durmalarının sebebi günlük akışın iki tıkta bitmesi.
 *
 * ~~Geri alma yok~~ Faz V'de kalktı: yönetici her duruma çekebiliyor. Buna karşılık her müdahale
 * gerekçe istiyor ve kayda geçiyor — kapı açıldı ama iz zorunlu kaldı.
 *
 * ⚠️ **İşleme alma fizibiliteyi taslak hâlde de kilitler**; kutu bunu açıkça söylüyor.
 * ⚠️ **İptali geri almak, iptalle birlikte kapatılmış belgeleri geri getirmez** — her belge ayrı bir
 * karar ve belge ekranından açılıyor. Kutu bunu da söylüyor.
 */
export function DurumPaneli({
  durum,
  onaydakiBelge,
  iadeEdilecekAsama,
  islemeAl,
  durumDegistir,
  iade,
}: {
  durum: IsDurumu | null
  /** Yöneticinin işleme alabileceği aşama (gönderilmiş, onay bekleyen) — yoksa `null`. */
  onaydakiBelge: { id: number; tur: string; durumMetni: string } | null
  /** İade basılırsa geri alınacak aşamanın adı — açık aşama yoksa `null` (düğme çizilmez). */
  iadeEdilecekAsama: string | null
  islemeAl: () => Promise<void>
  durumDegistir: (hedef: IsDurumu, gerekce: string) => Promise<void>
  iade: (gerekce: string) => Promise<void>
}) {
  const [acik, setAcik] = useState<'isleme' | 'iptal' | 'serbest' | 'iade' | null>(null)
  const [hedef, setHedef] = useState<IsDurumu>('ISLENIYOR')
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  const iptalEdilebilir = durum !== null && durum !== 'IPTAL'
  // Hakediş son aşama olduğu için işleme almak onu doğrudan tamamlar.
  const sonAsama = onaydakiBelge?.tur === 'HAKEDIS'

  function kapat() {
    setAcik(null)
    setHata(null)
  }

  async function islemeCalistir() {
    setHata(null)
    setCalisiyor(true)
    try {
      await islemeAl()
      kapat()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Aşama işleme alınamadı.')
    } finally {
      setCalisiyor(false)
    }
  }

  return (
    <>
      <div className="dugme-grubu">
        {onaydakiBelge && (
          <button onClick={() => setAcik('isleme')}>{sonAsama ? 'Onayla (Tamamla)' : 'İşleme Al'}</button>
        )}
        <button
          className="ikincil"
          onClick={() => {
            setHedef(durum === 'ONAY_BEKLIYOR' ? 'ISLENIYOR' : 'ONAY_BEKLIYOR')
            setAcik('serbest')
          }}
        >
          Durumu Değiştir
        </button>
        {/* İade, "Durumu Değiştir"in yanında ve `tehlike` DEĞİL: yıkıcı ama iptal kadar değil —
            proje yaşamaya devam eder, yalnızca bir aşama baştan girilir. Onay kutusu uyarıyor. */}
        {iadeEdilecekAsama && (
          <button className="ikincil" onClick={() => setAcik('iade')}>
            Önceki Aşamaya İade Et
          </button>
        )}
        {iptalEdilebilir && (
          <button className="tehlike" onClick={() => setAcik('iptal')}>
            Projeyi İptal Et
          </button>
        )}
      </div>

      {acik === 'isleme' && (
        <div className="katman" role="dialog" aria-modal="true">
          <div className="kart katman-kutu">
            <h3>{sonAsama ? 'Hakedişi onayla' : 'Aşamayı işleme al'}</h3>
            <Serit tur="uyari">
              {onaydakiBelge?.durumMetni} aşaması{' '}
              {sonAsama ? (
                <>
                  <strong>tamamlanacak</strong> ve proje kapanacak.
                </>
              ) : (
                <>
                  <strong>işleme alınacak</strong>: içeriği dondurulur ve bir sonraki aşama açılır.
                </>
              )}
              <div className="kucuk bosluk-ust">Çıktı arşive yazılır.</div>
            </Serit>
            {hata && <Serit tur="hata">{hata}</Serit>}
            <div className="dugme-grubu">
              <button disabled={calisiyor} onClick={() => void islemeCalistir()}>
                {calisiyor ? 'Uygulanıyor…' : sonAsama ? 'Evet, Onayla' : 'Evet, İşleme Al'}
              </button>
              <button className="ikincil" disabled={calisiyor} onClick={kapat}>
                Vazgeç
              </button>
            </div>
          </div>
        </div>
      )}

      {acik === 'iade' && (
        <GerekceKatmani
          baslik="Önceki Aşamaya İade Et"
          yikici
          aciklama={
            <>
              <strong>{iadeEdilecekAsama}</strong> aşaması geri alınacak: girilen{' '}
              <strong>miktarlar ve yüklenen dosyalar</strong> bu aşamadan düşecek ve aşama{' '}
              <em>“Henüz açılmadı”</em> hâline dönecek. Bölge sorumlusunun aşamayı{' '}
              <strong>baştan girmesi</strong> gerekir.
              <div className="kucuk bosluk-ust">
                Kayıt silinmez, iptal edilmiş olarak geçmişte kalır — kim ne girmişti bilgisi
                korunur. Bir aşama daha geri almak isterseniz düğmeye tekrar basın: sıra{' '}
                <strong>hakediş → 1. keşif → ön fizibilite</strong> şeklinde geriye yürür.
              </div>
            </>
          }
          onayMetni="Evet, İade Et"
          vazgec={kapat}
          onayla={async (gerekce) => {
            await iade(gerekce)
            kapat()
          }}
        />
      )}

      {acik === 'iptal' && (
        <GerekceKatmani
          baslik="Projeyi İptal Et"
          yikici
          aciklama={
            <>
              Proje iptal edilecek ve <strong>açık belgeleri</strong> (taslak ve onay bekleyen) de
              iptal olacak. Onaylanmış belgelere dokunulmaz.
              <div className="kucuk bosluk-ust">
                Kayıt silinmez, iptal edilmiş olarak listede kalır. İptal sonradan geri alınabilir,
                ancak <strong>iptal olan belgeler kendiliğinden geri gelmez</strong> — her biri belge
                ekranından ayrıca açılır.
              </div>
            </>
          }
          onayMetni="Projeyi İptal Et"
          vazgec={kapat}
          onayla={async (gerekce) => {
            await durumDegistir('IPTAL', gerekce)
            kapat()
          }}
        />
      )}

      {acik === 'serbest' && (
        <GerekceKatmani
          baslik="Proje durumunu değiştir"
          aciklama={
            <>
              Durum doğrudan seçilen değere çekilecek. Bu, akışın dışına çıkan bir müdahaledir:
              kapanmış bir projeyi yeniden açmak ya da yanlış işleme alınmış bir projeyi geri almak
              için.
              <div className="kucuk bosluk-ust">
                Kim, ne zaman ve hangi gerekçeyle değiştirdiği kaydedilir.
              </div>
            </>
          }
          onayMetni="Durumu Değiştir"
          vazgec={kapat}
          onayla={async (gerekce) => {
            await durumDegistir(hedef, gerekce)
            kapat()
          }}
        >
          <div className="alan">
            <label htmlFor="isHedefDurum">Yeni durum</label>
            <select id="isHedefDurum" value={hedef} onChange={(e) => setHedef(e.target.value as IsDurumu)}>
              {IS_DURUMLARI.filter((secenek) => secenek.deger !== durum).map((secenek) => (
                <option key={secenek.deger} value={secenek.deger}>
                  {secenek.ad}
                </option>
              ))}
            </select>
          </div>
        </GerekceKatmani>
      )}
    </>
  )
}

/**
 * Belgenin durumunu yöneten panel — **yalnızca yönetici**.
 *
 * Günlük akış tek düğme: **İşleme Al** (yalnızca onay bekleyen aşamada). İşleme almak aşamayı
 * dondurur ve bir sonraki aşamayı açar (hakediş için doğrudan tamamlar). ~~İade~~ kaldırıldı
 * (müdür kararı: geri gönderme yok).
 *
 * **Durumu Değiştir** akışın dışı içindir (yanlış işleme alınmış bir aşamayı geri açmak gibi) ve her
 * durumda görünür.
 */
export function BelgeDurumPaneli({
  durum,
  sonAsama = false,
  onayla,
  durumDegistir,
}: {
  durum: BelgeDurumu
  /** Hakediş mi — işleme almak onu doğrudan tamamlar (İşleniyor değil). */
  sonAsama?: boolean
  onayla: () => Promise<void>
  durumDegistir: (hedef: BelgeDurumu, gerekce: string) => Promise<void>
}) {
  const [acik, setAcik] = useState<'onay' | 'serbest' | null>(null)
  const [hedef, setHedef] = useState<BelgeDurumu>('ONAY_BEKLIYOR')
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  const onaydaMi = durum === 'ONAY_BEKLIYOR'

  function kapat() {
    setAcik(null)
    setHata(null)
  }

  async function onaylaCalistir() {
    setHata(null)
    setCalisiyor(true)
    try {
      await onayla()
      kapat()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Aşama işleme alınamadı.')
    } finally {
      setCalisiyor(false)
    }
  }

  return (
    <>
      <div className="dugme-grubu">
        {onaydaMi && <button onClick={() => setAcik('onay')}>{sonAsama ? 'Onayla (Tamamla)' : 'İşleme Al'}</button>}
        <button
          className="ikincil"
          onClick={() => {
            setHedef(durum === 'ONAY_BEKLIYOR' ? 'ISLENIYOR' : 'ONAY_BEKLIYOR')
            setAcik('serbest')
          }}
        >
          Durumu Değiştir
        </button>
      </div>

      {acik === 'onay' && (
        <div className="katman" role="dialog" aria-modal="true">
          <div className="kart katman-kutu">
            <h3>{sonAsama ? 'Hakedişi onayla' : 'Aşamayı işleme al'}</h3>
            <Serit tur="uyari">
              {sonAsama ? (
                <>
                  Aşama <strong>tamamlanacak</strong> ve proje kapanacak.
                </>
              ) : (
                <>
                  Aşama <strong>işleme alınacak</strong>: içeriği dondurulur ve bir sonraki aşama açılır.
                </>
              )}
              <div className="kucuk bosluk-ust">Çıktılar arşive yazılır.</div>
            </Serit>
            {hata && <Serit tur="hata">{hata}</Serit>}
            <div className="dugme-grubu">
              <button disabled={calisiyor} onClick={() => void onaylaCalistir()}>
                {calisiyor ? 'Uygulanıyor…' : sonAsama ? 'Evet, Onayla' : 'Evet, İşleme Al'}
              </button>
              <button className="ikincil" disabled={calisiyor} onClick={kapat}>
                Vazgeç
              </button>
            </div>
          </div>
        </div>
      )}

      {acik === 'serbest' && (
        <GerekceKatmani
          baslik="Aşama durumunu değiştir"
          aciklama={
            <>
              Durum doğrudan seçilen değere çekilecek. Bu, akışın dışına çıkan bir müdahaledir:
              yanlış onaylanmış bir aşamayı geri açmak ya da iptal edilmiş bir belgeyi diriltmek için.
              <div className="kucuk bosluk-ust">
                Kim, ne zaman ve hangi gerekçeyle değiştirdiği kaydedilir. Onaylanmış bir belgenin
                arşive yazılmış çıktısı silinmez; yeniden onaylandığında üzerine yazılır.
              </div>
            </>
          }
          onayMetni="Durumu Değiştir"
          vazgec={kapat}
          onayla={async (gerekce) => {
            await durumDegistir(hedef, gerekce)
            kapat()
          }}
        >
          <div className="alan">
            <label htmlFor="belgeHedefDurum">Yeni durum</label>
            <select
              id="belgeHedefDurum"
              value={hedef}
              onChange={(e) => setHedef(e.target.value as BelgeDurumu)}
            >
              {BELGE_DURUMLARI.filter((secenek) => secenek.deger !== durum).map((secenek) => (
                <option key={secenek.deger} value={secenek.deger}>
                  {secenek.ad}
                </option>
              ))}
            </select>
          </div>
        </GerekceKatmani>
      )}
    </>
  )
}

function GerekceNotu({ gerekce, dokunuldu }: { gerekce: string; dokunuldu: boolean }) {
  if (gerekce.trim() !== '') return null
  return dokunuldu ? (
    <p className="alan-hata">Gerekçe zorunludur.</p>
  ) : (
    <p className="bos-not">Gerekçe yazılmadan işlem tamamlanamaz.</p>
  )
}
