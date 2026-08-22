import { useEffect, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { api, ApiHatasi } from '../api'
import { BosListe, SayfaBasligi, Serit, tarih, useAcilirMenu, useKatman, Yukleniyor, YuklemeHatasi } from '../bilesen'
import { KabloListesi } from './KabloListesi'
import { KABLO_ARAMA_ID } from './KabloListesi'
import type { Bolge, BolgeAdedi, KableEnvanteri as Envanter, KableSurumFarki, KableSurumu } from '../tipler'

/**
 * Kablo Envanteri — **yalnızca yönetici** (Faz U).
 *
 * Envanter NOS+'tan tam liste olarak geliyor ve bugüne kadar yalnızca `:tools:kable-import` ile,
 * geliştirici eliyle giriyordu. Bu ekran o yolu yöneticiye açıyor.
 *
 * ⚠️ **Yükleme envanteri değiştirmez.** İçe aktarım yıkıcıdır: dosyada olmayan her kablo pasifleşir,
 * yani eksik bir dosya envanteri sessizce eritir ve hiçbir hata çıkmaz. Bu yüzden akış ikiye
 * bölünmüştür — dosya önce **sürüm** olarak saklanır ve farkı gösterilir, canlı envanter ancak
 * "Yürürlüğe Al" ile değişir. Geri dönmek ayrı bir mekanizma değil: eski sürüm için aynı düğme.
 *
 * Pasifleşen kablonun kaydı silinmez ve projelere kopyalanmış adresler donmuştur (CLAUDE.md §7/5),
 * dolayısıyla geri dönüş geçmiş belgeleri bozmaz — yalnızca yeni proje aramasını etkiler.
 */
export function KabloEnvanteri() {
  const [envanter, setEnvanter] = useState<Envanter | null>(null)
  /** Elle kablo formundaki bölge/saha seçimi için; envanterle aynı açılışta çekilir. */
  const [bolgeler, setBolgeler] = useState<Bolge[]>([])
  const [hata, setHata] = useState<string | null>(null)
  const [basari, setBasari] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState<'yukleme' | 'yururluk' | 'silme' | null>(null)

  const [dosya, setDosya] = useState<File | null>(null)
  /** Yükleme bölümü sayfanın altında sürekli durmuyor; düğmeyle açılıyor. */
  const [yuklemeAcik, setYuklemeAcik] = useState(false)
  /** Yürürlüğe alma onayı hangi sürüm için açık — farkı da taşır. */
  const [onaylanacak, setOnaylanacak] = useState<{ surum: KableSurumu; fark: KableSurumFarki } | null>(null)
  const [silinecek, setSilinecek] = useState<KableSurumu | null>(null)
  const dosyaSecici = useRef<HTMLInputElement>(null)

  useEffect(() => {
    yenile()
    api.bolgeler().then(setBolgeler).catch(() => setBolgeler([]))
  }, [])

  function yenile() {
    api
      .kableEnvanteri()
      .then(setEnvanter)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Envanter yüklenemedi.'))
  }

  /**
   * "Kabloları düzenle" — envanterin kendisi zaten sayfadaki **Kablolar** bölümüdür; ayrı bir ekran
   * açılmıyor (S12: ikinci bir detay ekranı yok). Menü oraya götürüp arama kutusuna odaklanıyor.
   *
   * Yalnızca yürürlükteki sürümde etkin: arşiv sürümleri geçmişin donmuş anlık görüntüleridir ve
   * onlara yazan bir yol yok.
   */
  function kablolariDuzenle() {
    const kutu = document.getElementById(KABLO_ARAMA_ID)
    kutu?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    kutu?.focus()
  }

  async function envanteriIndir() {
    setHata(null)
    try {
      await api.kableEnvanteriIndir()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Envanter indirilemedi.')
    }
  }

  async function calistir(ad: 'yukleme' | 'yururluk' | 'silme', islem: () => Promise<void>) {
    setHata(null)
    setBasari(null)
    setCalisiyor(ad)
    try {
      await islem()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'İşlem tamamlanamadı.')
    } finally {
      setCalisiyor(null)
    }
  }

  /**
   * Dosyayı yükler ve **doğrudan onay penceresini açar**.
   *
   * Sunucu yükleme yanıtında farkı da döndüğü için ikinci bir çağrıya gerek yok; yönetici tek
   * adımda "ne olacak"ı görüp karar verir. Vazgeçerse sürüm listede kalır, envanter değişmemiştir.
   */
  function yukle() {
    calistir('yukleme', async () => {
      const sonuc = await api.kableSurumYukle(dosya!)
      setDosya(null)
      if (dosyaSecici.current) dosyaSecici.current.value = ''
      yenile()
      setOnaylanacak({ surum: sonuc.surum, fark: sonuc.fark })
    })
  }

  /** Listeden yürürlüğe alma (geri dönüş dâhil): önce fark çekilir, sonra onay sorulur. */
  function onayIste(surum: KableSurumu) {
    calistir('yururluk', async () => {
      setOnaylanacak({ surum, fark: await api.kableSurumFarki(surum.id) })
    })
  }

  function yururlugeAl(surum: KableSurumu) {
    calistir('yururluk', async () => {
      const sonuc = await api.kableSurumYururlugeAl(surum.id)
      setOnaylanacak(null)
      setBasari(
        `"${surum.dosyaAdi}" yürürlüğe alındı: ${sonuc.eklenen} yeni, ${sonuc.guncellenen} güncellendi, ` +
          `${sonuc.pasiflesen} pasifleştirildi. Envanterde ${sonuc.aktifKableSayisi} kablo var.`,
      )
      yenile()
    })
  }

  function sil(surum: KableSurumu) {
    calistir('silme', async () => {
      await api.kableSurumSil(surum.id)
      setSilinecek(null)
      setBasari(`"${surum.dosyaAdi}" sürümü listeden silindi. Envanter değişmedi.`)
      yenile()
    })
  }

  // Hata kontrolü yükleniyor kontrolünün ÜSTÜNDE: yetkisiz bir kullanıcıda liste hiç gelmez ve
  // alttaki şerit çizilmediği için ekran kalıcı olarak "Yükleniyor…" gösterirdi.
  if (hata && !envanter) return <YuklemeHatasi mesaj={hata} yenidenDene={yenile} />
  if (!envanter) return <Yukleniyor />

  const yururlukteki = envanter.surumler.find((s) => s.yururlukte) ?? null

  return (
    <>
      {onaylanacak && (
        <YururlukOnayi
          surum={onaylanacak.surum}
          fark={onaylanacak.fark}
          calisiyor={calisiyor === 'yururluk'}
          vazgec={() => setOnaylanacak(null)}
          onayla={() => yururlugeAl(onaylanacak.surum)}
        />
      )}

      {silinecek && (
        <SilmeOnayi
          surum={silinecek}
          calisiyor={calisiyor === 'silme'}
          vazgec={() => setSilinecek(null)}
          onayla={() => sil(silinecek)}
        />
      )}

      <SayfaBasligi
        baslik="Kablo Envanteri"
        altBaslik="NOS+ tam listesi — projeler kabloyu bu envanterden seçer"
      >
        {/* ~~"Envanteri indir (.xlsx)"~~ 18 Ağustos 2026'da buradan kaldırıldı (kullanıcı isteği):
            indirme artık sürüm satırlarının ⋮ menüsünde. Sayfa başlığında yalnızca yükleme kaldı. */}
        <button onClick={() => setYuklemeAcik((onceki) => !onceki)} aria-expanded={yuklemeAcik}>
          {yuklemeAcik ? 'Yükleme alanını kapat' : 'Yeni envanter yükle'}
        </button>
      </SayfaBasligi>

      <Serit tur="bilgi">
        <strong>Yükleme envanteri değiştirmez.</strong> Dosya önce sürüm olarak kaydedilir ve farkı
        gösterilir; canlı envanter <em>Yürürlüğe Al</em> ile değişir. Eski bir sürüme dönmek için
        listeden onu yürürlüğe almanız yeterli — dosyayı yeniden yüklemeniz gerekmez. Envanterden
        düşen kablolar <em>silinmez</em>, yalnızca yeni proje aramasında çıkmaz; açılmış projelerin
        adresi değişmez.
      </Serit>
      {hata && <Serit tur="hata">{hata}</Serit>}
      {basari && <Serit tur="basari">{basari}</Serit>}

      <div className="kart">
        <h2>Yürürlükteki envanter</h2>
        {yururlukteki ? (
          <>
            {/* Etiket + değer çiftleri `.etiketli` kalıbından geliyor (proje listesiyle aynı). */}
            <div className="envanter-ozet">
              <span className="etiketli">
                <span className="deger-etiket">Dosya</span>
                <span className="deger">{yururlukteki.dosyaAdi}</span>
              </span>
              <span className="etiketli">
                <span className="deger-etiket">Yürürlüğe alınma</span>
                <span className="deger">{tarih(yururlukteki.yururlugeAlinma)}</span>
              </span>
              <span className="etiketli">
                <span className="deger-etiket">Yükleyen</span>
                <span className="deger">{yururlukteki.yukleyen ?? 'İçe aktarım aracı'}</span>
              </span>
              <span className="etiketli">
                <span className="deger-etiket">Aktif kablo</span>
                <span className="deger">{envanter.aktifKableSayisi}</span>
              </span>
            </div>
            <BolgeDagilimi dagilim={envanter.bolgeDagilimi} />
          </>
        ) : (
          <p className="bos-not">
            Envanter henüz yüklenmedi. Bir dosya yükleyip yürürlüğe alana kadar kablo araması boş
            sonuç döner.
          </p>
        )}
      </div>

      {yuklemeAcik && (
        <YuklemeAlani
          dosya={dosya}
          calisiyor={calisiyor}
          dosyaSecici={dosyaSecici}
          dosyaSecildi={(secilen) => {
            setDosya(secilen)
            setHata(null)
            setBasari(null)
          }}
          yukle={yukle}
        />
      )}

      {/* Kablo listesi sürüm tablosunun ÜSTÜNDE: günlük iş tek bir kabloyu düzeltmek, sürüm
          yönetimi ise ayda bir yapılan bir işlem. */}
      <KabloListesi bolgeler={bolgeler} degisti={yenile} />

      {envanter.surumler.length === 0 ? (
        <BosListe
          baslik="Henüz envanter sürümü yok."
          aciklama="Kablo envanteri, NOS+'tan alınan tam listeyi içeren bir .xlsx dosyasından okunur."
        >
          {!yuklemeAcik && <button onClick={() => setYuklemeAcik(true)}>Yeni envanter yükle</button>}
        </BosListe>
      ) : (
        <div className="kart">
          <table>
            <thead>
              <tr>
                <th>Dosya</th>
                <th className="sayi">Satır</th>
                <th>Yüklenme</th>
                <th>Yükleyen</th>
                <th>Durum</th>
                <th className="satir-islem-sutunu" />
              </tr>
            </thead>
            <tbody>
              {envanter.surumler.map((surum) => (
                <SurumSatiri
                  key={surum.id}
                  surum={surum}
                  kilitli={calisiyor !== null}
                  yururlugeAl={() => onayIste(surum)}
                  duzenle={kablolariDuzenle}
                  indir={() => void envanteriIndir()}
                  sil={() => setSilinecek(surum)}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}

/** Bölge dağılımı — "doğru dosyayı mı yükledim" sorusunu satır sayısından iyi cevaplar. */
function BolgeDagilimi({ dagilim }: { dagilim: BolgeAdedi[] }) {
  if (dagilim.length === 0) return null
  return (
    <div className="dagilim bosluk-ust">
      {dagilim.map((bolge) => (
        <span key={bolge.bolgeKodu} className="dagilim-oge">
          {bolge.bolgeKodu}
          <strong>{bolge.adet}</strong>
        </span>
      ))}
    </div>
  )
}

/**
 * Yükleme bölümü. Sürükle-bırak alanı `<input type="file">`'ı **gizlemiyor**, sarmalıyor:
 * tarayıcının dosya seçici düğmesi klavye ve ekran okuyucu için tek erişim yolu.
 */
function YuklemeAlani({
  dosya,
  calisiyor,
  dosyaSecici,
  dosyaSecildi,
  yukle,
}: {
  dosya: File | null
  calisiyor: string | null
  dosyaSecici: React.RefObject<HTMLInputElement | null>
  dosyaSecildi: (secilen: File | null) => void
  yukle: () => void
}) {
  const [uzerinde, setUzerinde] = useState(false)

  return (
    <div className="kart">
      <h2>Yeni envanter yükle</h2>
      <p className="bos-not">
        NOS+'tan alınan tam listeyi yükleyin. Dosya okunur ve sürüm olarak kaydedilir; envanter
        değişmeden önce farkı görürsünüz.
      </p>

      <div
        className={uzerinde ? 'birak-alani uzerinde' : 'birak-alani'}
        onDragOver={(olay) => {
          olay.preventDefault()
          setUzerinde(true)
        }}
        onDragLeave={() => setUzerinde(false)}
        onDrop={(olay) => {
          olay.preventDefault()
          setUzerinde(false)
          dosyaSecildi(olay.dataTransfer.files?.[0] ?? null)
        }}
      >
        <div className="birak-baslik">{dosya ? dosya.name : 'Dosyayı buraya sürükleyin'}</div>
        {/* 10 MB sunucunun gerçek sınırı (`spring.servlet.multipart.max-file-size`). */}
        <div className="bos-not">Yalnızca .xlsx · en fazla 10 MB · `cable` ve `Saha` sayfaları</div>
        <input
          ref={dosyaSecici}
          type="file"
          accept=".xlsx"
          aria-label="Kablo envanteri dosyası (.xlsx)"
          disabled={calisiyor !== null}
          onChange={(e) => dosyaSecildi(e.target.files?.[0] ?? null)}
        />
      </div>

      <div className="dugme-grubu">
        <button disabled={!dosya || calisiyor !== null} onClick={yukle}>
          {calisiyor === 'yukleme' ? 'Okunuyor…' : 'Dosyayı Yükle'}
        </button>
        <span className="dugme-notu">Envanter bu adımda değişmez; farkı gördükten sonra onaylarsınız.</span>
      </div>
    </div>
  )
}

/**
 * Yürürlüğe alma onayı — ileri gitmek ve geri dönmek için **aynı** pencere.
 *
 * Uyarının çekirdeği pasifleşecek kablolar: sayı, örnekler ve üzerinde açık proje olanların adedi.
 * Yönetici engellenmez (kullanıcı kararı, 9 Ağustos 2026), ama körlemesine onaylayamaz.
 */
function YururlukOnayi({
  surum,
  fark,
  calisiyor,
  vazgec,
  onayla,
}: {
  surum: KableSurumu
  fark: KableSurumFarki
  calisiyor: boolean
  vazgec: () => void
  onayla: () => void
}) {
  useKatman(calisiyor ? undefined : vazgec)
  const geriDonus = fark.yururluktekiSurumId !== null && fark.yururluktekiSurumId > surum.id

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu">
        <h3>{geriDonus ? 'Önceki sürüme dön' : 'Envanteri yürürlüğe al'}</h3>

        <Serit tur="bilgi">
          <strong>{surum.dosyaAdi}</strong> · {surum.satirSayisi} kablo
          <div className="kucuk bosluk-ust">
            {fark.yeni} yeni · {fark.degisen} değişen · {fark.ayni} aynı · {fark.dusen} düşecek
          </div>
        </Serit>

        <BolgeDagilimi dagilim={fark.bolgeDagilimi} />

        {fark.dusen > 0 && (
          <Serit tur="uyari">
            Bu işlem <strong>{fark.dusen}</strong> kabloyu pasifleştirecek
            {fark.acikProjeliDusen > 0 && (
              <>
                ; bunların <strong>{fark.acikProjeliDusen}</strong> tanesinde açık proje var
              </>
            )}
            .
            <div className="kucuk bosluk-ust">
              Projeler ve belgeleri etkilenmez — adresleri projeye kopyalanıp dondurulmuştur. Bu
              kablolar yalnızca yeni proje aramasında çıkmaz.
            </div>
            {fark.dusenOrnekleri.length > 0 && (
              <div className="kucuk bosluk-ust">
                Örnek: {fark.dusenOrnekleri.join(', ')}
                {fark.dusen > fark.dusenOrnekleri.length && ' …'}
              </div>
            )}
          </Serit>
        )}

        <div className="dugme-grubu">
          <button disabled={calisiyor} onClick={onayla}>
            {calisiyor ? 'Uygulanıyor…' : geriDonus ? 'Evet, Bu Sürüme Dön' : 'Evet, Yürürlüğe Al'}
          </button>
          <button className="ikincil" disabled={calisiyor} onClick={vazgec}>
            Vazgeç
          </button>
        </div>
        <div className="kucuk">
          Vazgeçerseniz sürüm listede kalır; envanter değişmez ve daha sonra yürürlüğe alabilirsiniz.
        </div>
      </div>
    </div>
  )
}

function SilmeOnayi({
  surum,
  calisiyor,
  vazgec,
  onayla,
}: {
  surum: KableSurumu
  calisiyor: boolean
  vazgec: () => void
  onayla: () => void
}) {
  useKatman(calisiyor ? undefined : vazgec)

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu">
        <h3>Sürümü sil</h3>
        <Serit tur="uyari">
          <strong>{surum.dosyaAdi}</strong> sürümü ve okunmuş {surum.satirSayisi} satırı silinecek.
          <div className="kucuk bosluk-ust">
            Envanter değişmez — bu sürüm yürürlükte değil. Silindikten sonra bu dosyaya geri
            dönebilmek için yeniden yüklemeniz gerekir.
          </div>
        </Serit>
        <div className="dugme-grubu">
          <button className="tehlike" disabled={calisiyor} onClick={onayla}>
            {calisiyor ? 'Siliniyor…' : 'Evet, Sil'}
          </button>
          <button className="ikincil" disabled={calisiyor} onClick={vazgec}>
            Vazgeç
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * Sürüm tablosunun bir satırı.
 *
 * **Sağ tık aynı ⋮ menüsünü açar** (kullanıcı isteği, 10 Ağustos 2026): tabloda satıra sağ tıklayıp
 * işlem aramak beklenen davranıştı ve tarayıcının kendi menüsü çıkıyordu. Menü tek yerde kalıyor —
 * sağ tık ikinci bir seçenek listesi üretmiyor, var olanı açıyor.
 */
function SurumSatiri({
  surum,
  kilitli,
  yururlugeAl,
  duzenle,
  indir,
  sil,
}: {
  surum: KableSurumu
  kilitli: boolean
  yururlugeAl: () => void
  duzenle: () => void
  indir: () => void
  sil: () => void
}) {
  const menu = useRef<HTMLDetailsElement | null>(null)

  return (
    <tr
      onContextMenu={(olay) => {
        olay.preventDefault()
        if (menu.current) menu.current.open = true
      }}
    >
      <td>
        <strong>{surum.dosyaAdi}</strong>
        {surum.yururlukte && surum.yururlugeAlinma && (
          <div className="bos-not">Yürürlüğe alınma: {tarih(surum.yururlugeAlinma)}</div>
        )}
      </td>
      <td className="sayi">
        {surum.satirSayisi} <span className="bos-not">kablo</span>
      </td>
      <td>{tarih(surum.yuklenme)}</td>
      <td>{surum.yukleyen ?? <span className="bos-not">İçe aktarım aracı</span>}</td>
      <td>
        <span className={surum.yururlukte ? 'durum-nokta-alani tamam' : 'durum-nokta-alani acik'}>
          <span className="durum-nokta" aria-hidden="true" />
          {surum.yururlukte ? 'Yürürlükte' : 'Arşiv'}
        </span>
      </td>
      <td className="satir-islem-sutunu">
        <div className="satir-islem">
          {!surum.yururlukte && (
            <button className="ikincil" disabled={kilitli} onClick={yururlugeAl}>
              Yürürlüğe al
            </button>
          )}
          <SatirMenusu
            surum={surum}
            kilitli={kilitli}
            duzenle={duzenle}
            indir={indir}
            sil={sil}
            menuRef={menu}
          />
        </div>
      </td>
    </tr>
  )
}

/**
 * Satır menüsü: kabloları düzenle · envanteri indir · sürümü sil.
 *
 * ⚠️ **İndirme daima YÜRÜRLÜKTEKİ envanteri verir**, satırdaki sürümü değil: sunucuda sürüm bazlı
 * bir dışa aktarım ucu yok, `KableCiktisi` canlı `kable` tablosundan üretiyor. Bu yüzden seçenek
 * arşiv satırlarında **kapalı** ve nedeni yazılı — açık bırakmak, arşiv sürümünü indirdiğini sanan
 * kullanıcıya sessizce başka bir dosya verirdi.
 *
 * ⚠️ **Düzenleme yalnızca yürürlükteki sürümde** anlamlı: arşiv sürümleri geçmişin donmuş
 * anlık görüntüleridir ve onlara yazan bir yol yok (Faz V kararı — elle düzenleme doğrudan
 * yürürlükteki envantere yazılır). Arşiv satırında seçenek **kapalı** ve nedeni yazılı; gizlemek,
 * kullanıcıya "bu satırda neden düzenleme yok" sorusunu cevapsız bırakırdı.
 *
 * `ref` iki işe birden yarıyor: dışarı tıklayınca kapanma (`useAcilirMenu`) ve satıra **sağ
 * tıklayınca** menünün açılması (`SurumSatiri`).
 */
function SatirMenusu({
  surum,
  kilitli,
  duzenle,
  indir,
  sil,
  menuRef,
}: {
  surum: KableSurumu
  kilitli: boolean
  duzenle: () => void
  indir: () => void
  sil: () => void
  menuRef: RefObject<HTMLDetailsElement | null>
}) {
  const { ref, kapat } = useAcilirMenu()

  return (
    <details
      ref={(oge) => {
        ref.current = oge
        menuRef.current = oge
      }}
      className="satir-menu"
    >
      <summary className="sade" aria-label={`${surum.dosyaAdi} işlemleri`}>
        ⋮
      </summary>
      <div className="satir-menu-secenekler">
        <button
          className="sade"
          disabled={!surum.yururlukte}
          title={surum.yururlukte ? undefined : 'Yalnızca yürürlükteki envanter düzenlenebilir; önce "Yürürlüğe al" deyin.'}
          onClick={() => {
            kapat()
            duzenle()
          }}
        >
          Kabloları düzenle
        </button>
        <button
          className="sade"
          disabled={!surum.yururlukte}
          title={
            surum.yururlukte
              ? undefined
              : 'İndirme yürürlükteki envanteri verir; bu satır arşiv sürümü.'
          }
          onClick={() => {
            kapat()
            indir()
          }}
        >
          Envanteri indir (.xlsx)
        </button>
        <button
          className="sade tehlike-metin"
          disabled={kilitli || surum.yururlukte}
          title={surum.yururlukte ? 'Yürürlükteki sürüm silinemez' : undefined}
          onClick={() => {
            kapat()
            sil()
          }}
        >
          Sürümü sil
        </button>
      </div>
    </details>
  )
}
