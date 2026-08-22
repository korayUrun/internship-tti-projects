import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiHatasi } from '../api'
import { BosListe, SayfaBasligi, Serit, tarih, useAcilirMenu, useKatman, Yukleniyor, YuklemeHatasi } from '../bilesen'
import type { Poz, SozlesmeOnizleme, SozlesmeYonetimSatiri } from '../tipler'

/** Üst sekmeler. Sıra kullanıcı kararı: **Tümü** önce ve varsayılan. */
type Gorunum = 'tumu' | 'aktif' | 'pasif'

const SEKMELER: { deger: Gorunum; ad: string }[] = [
  { deger: 'tumu', ad: 'Tümü' },
  { deger: 'aktif', ad: 'Aktif' },
  { deger: 'pasif', ad: 'Pasif' },
]

/**
 * Sözleşme yönetimi — **yalnızca yönetici** (Faz L).
 *
 * Bu ekrandan önce sisteme sözleşme eklemenin tek yolu bir Flyway migration'ıydı: yeni bir sözleşme
 * geldiğinde ya da tek bir birim fiyat düzeltilmesi gerektiğinde geliştirici müdahalesi ve sunucu
 * dağıtımı gerekiyordu.
 *
 * Yükleme iki adımlıdır ve önizleme atlanamaz: dosyadan kaç poz okunduğunu yönetici yazma öncesinde
 * görür. İkinci adım tek düğme: **Sözleşmeyi Kaydet**.
 *
 * **Kod hiçbir zaman düzenlenmez** — içsel anahtar (URL parçası + tekillik kilidi); sunucu dosyadan
 * türetiyor ve çakışırsa boştaki ilk koda kayıyor (18 Ağustos 2026). Yükleme ekranında kalan tek
 * karar noktası: bu dosya mı, evet mi hayır mı.
 *
 * **Ad ve başlık** ~~düzenlenemezdi~~ (4 Ağustos 2026: "dosyadan doğru türüyor"); 18 Ağustos 2026'da
 * satır menüsündeki **"Adı düzenle"** ile geri geldi (kullanıcı isteği): dosyadan türeyen ad her
 * zaman okunaklı olmuyor ve düzeltmek için sözleşmeyi yeniden yüklemek gerekiyordu.
 *
 * **Silme** ~~Faz V'de kaldırılmıştı~~ ve 18 Ağustos 2026'da **koşullu olarak geri geldi** (kullanıcı
 * isteği): yalnızca **hiç belge açılmamış** sözleşmeler silinebilir. Kaldırılma gerekçesi "poz
 * listesini ve fiyat sürümlerini birlikte götüren, geri dönüşü olmayan işlem" idi; koşul o
 * gerekçenin can alıcı kısmını kaldırıyor — belgesi olmayan sözleşmenin ardında donmuş tutar yok.
 * Kullanımdaki sözleşmede sunucu 409 döner ve kullanıcı **pasife almaya** yönlendirilir.
 *
 * ~~**Revize Et** (Excel round-trip ile yeni sürüm)~~ **18 Ağustos 2026'da arayüzden kaldırıldı**
 * (kullanıcı isteği). Fiyat/poz değiştirmenin yolu değişmedi, yalnızca kılavuzlu kısayolu kalktı:
 * sözleşmenin Excel'i satır menüsünden indirilir, düzenlenir ve **"Yeni sözleşme yükle"** ile yeni
 * bir kodla yüklenir; eskisi elle pasife alınır. Sunucu `oncekiKod` parametresini hâlâ tanıyor ve
 * var olan revizyon zincirleri listede görünmeye devam ediyor — kaldırılan yalnızca akıştır.
 */
export function Sozlesmeler() {
  const [sozlesmeler, setSozlesmeler] = useState<SozlesmeYonetimSatiri[] | null>(null)
  const [hata, setHata] = useState<string | null>(null)
  const [basari, setBasari] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState<'onizleme' | 'yukleme' | null>(null)

  const [dosya, setDosya] = useState<File | null>(null)
  const [onizleme, setOnizleme] = useState<SozlesmeOnizleme | null>(null)
  const [sayfa, setSayfa] = useState('')
  /** Yönetici girdiği sözleşme adı (madde 3); boş bırakılırsa dosyadan türeyen ad kullanılır. */
  const [ad, setAd] = useState('')
  /** Hangi sekme açık. Varsayılan **Tümü**: yönetici önce ne olduğunu görmek istiyor. */
  const [gorunum, setGorunum] = useState<Gorunum>('tumu')
  /** Yükleme bölümü sayfanın altında sürekli durmuyor; düğmeyle açılıyor. */
  const [yuklemeAcik, setYuklemeAcik] = useState(false)
  /** Poz listesi katmanı hangi sözleşme için açık. */
  const [pozlariGorunen, setPozlariGorunen] = useState<SozlesmeYonetimSatiri | null>(null)
  /**
   * Aktiflik değişimi onayı hangi sözleşme için açık.
   *
   * **İki yön de onay ister** (kullanıcı kararı, 6 Ağustos 2026): pasife almak o sözleşmeyle yeni
   * belge açılmasını kapatır, aktife almak yeniden açar. İkisi de bütün bölge sorumlularının
   * gördüğü seçenek listesini değiştiriyor.
   */
  const [aktifligiDegisecek, setAktifligiDegisecek] = useState<SozlesmeYonetimSatiri | null>(null)
  /**
   * Silme onayı hangi sözleşme için açık.
   *
   * "Kullanımda mı" kontrolü **burada yapılmıyor**: kararı sunucu veriyor (belge sayısına bakıp 409
   * dönüyor). Web'de tekrarlansaydı iki yerde yaşayan ve biri unutulduğunda sessizce ayrışan ikinci
   * bir kural olurdu (§7/1) — üstelik listede belge sayısı bilgisi hiç yok.
   */
  const [silinecek, setSilinecek] = useState<SozlesmeYonetimSatiri | null>(null)
  /**
   * Ad/başlık düzenleme katmanı hangi sözleşme için açık.
   *
   * ~~Ad ve başlık düzenlenemezdi~~ (4 Ağustos 2026: "ikisi de dosyadan doğru türüyor"); 18 Ağustos
   * 2026'da ad düzenleme kullanıcı isteğiyle geri geldi — dosyadan türeyen ad her zaman okunaklı
   * olmuyor ve düzeltmek için sözleşmeyi yeniden yüklemek gerekiyordu. **Kod değişmez**: içsel
   * anahtar (URL parçası + tekillik kilidi) ve ona dokunmak geçmiş bağları koparırdı.
   */
  const [adiDuzenlenen, setAdiDuzenlenen] = useState<SozlesmeYonetimSatiri | null>(null)
  const dosyaSecici = useRef<HTMLInputElement>(null)

  useEffect(() => {
    yenile()
  }, [])

  function yenile() {
    api
      .sozlesmeYonetimi()
      .then(setSozlesmeler)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Sözleşmeler yüklenemedi.'))
  }

  async function calistir(ad: 'onizleme' | 'yukleme', islem: () => Promise<void>) {
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

  /** Dosya değişince önizleme geçersizdir; eski önizlemeyle yükleme yapılmasın. */
  function dosyaSecildi(secilen: File | null) {
    setDosya(secilen)
    setOnizleme(null)
    setSayfa('')
    setAd('')
    setHata(null)
    setBasari(null)
  }

  function onizle() {
    calistir('onizleme', async () => setOnizleme(await api.sozlesmeOnizle(dosya!, sayfa || undefined)))
  }

  function kaydet() {
    calistir('yukleme', async () => {
      const sonuc = await api.sozlesmeYukle(dosya!, sayfa || undefined, ad)
      setBasari(`"${sonuc.ad}" sözleşmesi kaydedildi. Fiyat listesi yayında.`)
      setDosya(null)
      setOnizleme(null)
      setSayfa('')
      setAd('')
      if (dosyaSecici.current) dosyaSecici.current.value = ''
      yenile()
    })
  }

  /**
   * Kaydedilebilir mi? Tek koşul: dosya seçilmiş ve önizlenmiş olsun.
   *
   * ~~Kod çakışması engeldi~~ (18 Ağustos 2026): kod artık sorulmuyor, sunucu boştaki ilk koda
   * kayıyor. Çakışma yalnızca bir bilgi şeridi olarak görünüyor.
   */
  const kaydedilebilir = dosya !== null && onizleme !== null

  /** Boş sözleşme formatı: doldurulup aşağıdaki yükleme akışıyla geri gelir. */
  async function formatIndir() {
    setHata(null)
    try {
      await api.sozlesmeFormatiIndir()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Format indirilemedi.')
    }
  }

  /** Aktiflik değiştirme — mevcut `sozlesmeBilgiGuncelle` ucu; ad ve başlık olduğu gibi gönderilir. */
  async function aktifligiDegistir(sozlesme: SozlesmeYonetimSatiri) {
    setHata(null)
    setBasari(null)
    setCalisiyor('yukleme')
    try {
      await api.sozlesmeBilgiGuncelle(sozlesme.kod, {
        ad: sozlesme.ad,
        baslik: sozlesme.baslik,
        aktif: !sozlesme.aktif,
      })
      setAktifligiDegisecek(null)
      setBasari(`"${sozlesme.ad}" ${sozlesme.aktif ? 'pasife alındı' : 'aktife alındı'}.`)
      yenile()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Durum değiştirilemedi.')
    } finally {
      setCalisiyor(null)
    }
  }

  /**
   * Sözleşmeyi siler. Kullanımdaysa sunucu 409 döner; mesaj kaç belge olduğunu ve pasife almayı
   * söylediği için olduğu gibi gösteriliyor, üstüne kendi metnimizi yazmıyoruz.
   */
  async function sil(sozlesme: SozlesmeYonetimSatiri) {
    setHata(null)
    setBasari(null)
    setCalisiyor('yukleme')
    try {
      await api.sozlesmeSil(sozlesme.kod)
      setSilinecek(null)
      setBasari(`"${sozlesme.ad}" silindi.`)
      yenile()
    } catch (e) {
      // Onay kutusu AÇIK kalır: hata kutunun içinde görünür, kullanıcı "pasife al" seçeneğini
      // oradan okur. Kapatsaydık mesaj listenin üstünde kaybolur ve sebebi kaybolurdu.
      setHata(e instanceof ApiHatasi ? e.message : 'Sözleşme silinemedi.')
    } finally {
      setCalisiyor(null)
    }
  }

  /**
   * Ad ve başlığı kaydeder. `aktif` olduğu gibi geri gönderilir: uç üç alanı birden alıyor ve
   * göndermemek sözleşmeyi sessizce pasife çekerdi.
   */
  async function adiKaydet(sozlesme: SozlesmeYonetimSatiri, ad: string, baslik: string) {
    setHata(null)
    setBasari(null)
    setCalisiyor('yukleme')
    try {
      await api.sozlesmeBilgiGuncelle(sozlesme.kod, { ad, baslik, aktif: sozlesme.aktif })
      setAdiDuzenlenen(null)
      setBasari(`"${ad}" güncellendi.`)
      yenile()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Sözleşme güncellenemedi.')
    } finally {
      setCalisiyor(null)
    }
  }

  async function sozlesmeIndir(sozlesme: SozlesmeYonetimSatiri) {
    setHata(null)
    try {
      await api.sozlesmeIndir(sozlesme.kod)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Sözleşme indirilemedi.')
    }
  }

  // Hata kontrolü yükleniyor kontrolünün ÜSTÜNDE: yetkisiz bir kullanıcıda liste hiç gelmez ve
  // alttaki şerit çizilmediği için ekran kalıcı olarak "Yükleniyor…" gösterirdi.
  if (hata && !sozlesmeler) return <YuklemeHatasi mesaj={hata} yenidenDene={yenile} />
  if (!sozlesmeler) return <Yukleniyor />

  const sayilar: Record<Gorunum, number> = {
    tumu: sozlesmeler.length,
    aktif: sozlesmeler.filter((s) => s.aktif).length,
    pasif: sozlesmeler.filter((s) => !s.aktif).length,
  }
  const gorunenler = sozlesmeler.filter(
    (s) => gorunum === 'tumu' || (gorunum === 'aktif' ? s.aktif : !s.aktif),
  )

  return (
    <>
      {pozlariGorunen && (
        <PozKatmani sozlesme={pozlariGorunen} kapat={() => setPozlariGorunen(null)} eklendi={yenile} />
      )}

      {aktifligiDegisecek && (
        <AktiflikOnayi
          sozlesme={aktifligiDegisecek}
          calisiyor={calisiyor === 'yukleme'}
          vazgec={() => setAktifligiDegisecek(null)}
          onayla={() => void aktifligiDegistir(aktifligiDegisecek)}
        />
      )}

      {adiDuzenlenen && (
        <AdDuzenleme
          sozlesme={adiDuzenlenen}
          calisiyor={calisiyor === 'yukleme'}
          hata={hata}
          vazgec={() => {
            setAdiDuzenlenen(null)
            setHata(null)
          }}
          kaydet={(ad, baslik) => void adiKaydet(adiDuzenlenen, ad, baslik)}
        />
      )}

      {silinecek && (
        <SilmeOnayi
          sozlesme={silinecek}
          calisiyor={calisiyor === 'yukleme'}
          hata={hata}
          vazgec={() => {
            setSilinecek(null)
            setHata(null)
          }}
          onayla={() => void sil(silinecek)}
        />
      )}

      <SayfaBasligi
        baslik="Sözleşmeler"
        altBaslik="Poz listeleri ve birim fiyatlar — belgeler bu sözleşmelerden fiyat çeker"
      >
        <button onClick={() => setYuklemeAcik((onceki) => !onceki)} aria-expanded={yuklemeAcik}>
          {yuklemeAcik ? 'Yükleme alanını kapat' : 'Yeni sözleşme yükle'}
        </button>
      </SayfaBasligi>

      <Serit tur="bilgi">
        <strong>Pozları veya fiyatları düzenlemek için</strong> satır menüsünden <em>İndir (.xlsx)</em>{' '}
        deyin, dosyayı düzenleyin ve <em>Yeni sözleşme yükle</em> ile <strong>yeni bir kodla</strong>{' '}
        yükleyin; eskisini <em>Pasife al</em> ile kapatın. Yayındaki bir fiyat yerinde değiştirilemez —{' '}
        <strong>geçmiş belgeler etkilenmez</strong>, fiyatları belgeye dondurulmuştur.
      </Serit>
      {hata && <Serit tur="hata">{hata}</Serit>}
      {basari && <Serit tur="basari">{basari}</Serit>}

      <div className="suzgec-satiri">
        {/*
          Sekme sayıları **tam kümeyi** gerektiriyor, bu yüzden süzme istemcide: uç yalnızca
          yöneticiye açık, tek istekte hepsini döndürüyor ve burada bir yetki kararı yok
          (§7/3 yetki süzgecini şart koşar — bu bir görüntüleme süzgeci).
        */}
        <div className="sekme-grubu" role="tablist" aria-label="Sözleşme durumu">
          {SEKMELER.map((sekme) => (
            <button
              key={sekme.deger}
              role="tab"
              aria-selected={gorunum === sekme.deger}
              className={gorunum === sekme.deger ? 'sekme-dugme etkin' : 'sekme-dugme'}
              onClick={() => setGorunum(sekme.deger)}
            >
              {sekme.ad}
              <span className="sekme-sayi">{sayilar[sekme.deger]}</span>
            </button>
          ))}
        </div>

        <button className="ikincil sablon-dugme" onClick={() => void formatIndir()}>
          Şablon indir (.xlsx)
        </button>
      </div>

      {yuklemeAcik && (
        <YuklemeAlani
          dosya={dosya}
          onizleme={onizleme}
          sayfa={sayfa}
          calisiyor={calisiyor}
          kaydedilebilir={kaydedilebilir}
          dosyaSecici={dosyaSecici}
          dosyaSecildi={dosyaSecildi}
          sayfaSecildi={setSayfa}
          ad={ad}
          adDegisti={setAd}
          onizle={onizle}
          kaydet={kaydet}
        />
      )}

      {gorunenler.length === 0 ? (
        <BosListe
          baslik={
            sozlesmeler.length === 0
              ? 'Henüz sözleşme yüklenmedi.'
              : 'Bu sekmede sözleşme yok.'
          }
          aciklama="Sözleşme, poz listesi ve birim fiyatları içeren bir .xlsx dosyasından okunur."
        >
          {!yuklemeAcik && <button onClick={() => setYuklemeAcik(true)}>Yeni sözleşme yükle</button>}
        </BosListe>
      ) : (
        // `.tablo-sarici` **bilinçli olarak yok**: `overflow-x: auto` bir kırpma bağlamı yaratıyor
        // ve son satırın ⋮ menüsü kartın altında kesiliyordu. Bu tablonun beş sütunu da dar, metin
        // sarıyor, yani yatay kaydırmaya ihtiyacı yok.
        <div className="kart">
          <table>
            <thead>
              {/* ~~Kod ve Kaynak sütunları~~ kaldırıldı (4 Ağustos 2026): kod içsel bir anahtar,
                  kaynak dosya adı ise yükleme anının notu — ikisi de yöneticinin kararına girmiyor. */}
              <tr>
                <th>Sözleşme</th>
                <th className="sayi">Poz</th>
                <th>Yüklenme tarihi</th>
                <th>Durum</th>
                <th className="satir-islem-sutunu" />
              </tr>
            </thead>
            <tbody>
              {gorunenler.map((sozlesme) => (
                <tr key={sozlesme.kod}>
                  <td>
                    <strong>{sozlesme.ad}</strong>
                    <div className="bos-not">{sozlesme.baslik}</div>
                    {/* Revizyon zinciri (madde: pozların Excel round-trip ile revizyonu). */}
                    {sozlesme.yeniKod && (
                      <div className="bos-not">↦ Revize edildi → {sozlesme.yeniKod}</div>
                    )}
                    {sozlesme.oncekiKod && (
                      <div className="bos-not">↤ Revizyon (önceki: {sozlesme.oncekiKod})</div>
                    )}
                  </td>
                  <td className="sayi">
                    {sozlesme.pozSayisi} <span className="bos-not">poz</span>
                  </td>
                  {/*
                    Sözleşmenin sisteme yüklendiği an. ~~`v1`/`v3` sürüm rozeti~~ kaldırıldı: Faz N'de
                    fiyat düzenleme çıktığından beri her sözleşmenin **tek** bir sürümü var (yükleme
                    anında yazılıyor), dolayısıyla numara hiçbir şey anlatmıyordu.

                    Tarih `olusturma`'dan geliyor, `yayindakiSurumTarihi`'nden değil: ikincisi
                    saatsiz bir gün (`LocalDate`) ve `tarih()` onu gece yarısı UTC sayıp her satırda
                    "03:00" basıyordu.
                  */}
                  <td>{tarih(sozlesme.olusturma)}</td>
                  <td>
                    <span className={sozlesme.aktif ? 'durum-nokta-alani tamam' : 'durum-nokta-alani iptal'}>
                      <span className="durum-nokta" aria-hidden="true" />
                      {sozlesme.aktif ? 'Aktif' : 'Pasif'}
                    </span>
                  </td>
                  {/* `satir-islem-sutunu` sarmayı kapatıyor: düğme ile ⋮ satırdan satıra bir yan
                      yana bir alt alta düşüyordu. */}
                  <td className="satir-islem-sutunu">
                    <div className="satir-islem">
                      <button
                        className="ikincil"
                        disabled={calisiyor !== null}
                        onClick={() => setAktifligiDegisecek(sozlesme)}
                      >
                        {sozlesme.aktif ? 'Pasife al' : 'Aktife al'}
                      </button>
                      <SatirMenusu
                        sozlesme={sozlesme}
                        kilitli={calisiyor !== null}
                        pozlariGoster={() => setPozlariGorunen(sozlesme)}
                        adiDuzenle={() => {
                          setHata(null)
                          setAdiDuzenlenen(sozlesme)
                        }}
                        indir={() => void sozlesmeIndir(sozlesme)}
                        silmeyiAc={() => {
                          setHata(null)
                          setSilinecek(sozlesme)
                        }}
                      />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

    </>
  )
}

/**
 * Yükleme bölümü — sayfanın altında sürekli durmak yerine "Yeni sözleşme yükle" ile açılır.
 *
 * Sürükle-bırak alanı `<input type="file">`'ı **gizlemiyor**, sarmalıyor: tarayıcının dosya seçici
 * düğmesi klavye ve ekran okuyucu için tek erişim yolu ve gizlenirse yerine kendi elle yazdığımız
 * bir düğme + `ref.click()` gerekirdi.
 */
function YuklemeAlani({
  dosya,
  onizleme,
  sayfa,
  calisiyor,
  kaydedilebilir,
  dosyaSecici,
  dosyaSecildi,
  sayfaSecildi,
  ad,
  adDegisti,
  onizle,
  kaydet,
}: {
  dosya: File | null
  onizleme: SozlesmeOnizleme | null
  sayfa: string
  calisiyor: string | null
  kaydedilebilir: boolean
  dosyaSecici: React.RefObject<HTMLInputElement | null>
  dosyaSecildi: (secilen: File | null) => void
  sayfaSecildi: (deger: string) => void
  ad: string
  adDegisti: (deger: string) => void
  onizle: () => void
  kaydet: () => void
}) {
  const [uzerinde, setUzerinde] = useState(false)

  return (
    <div className="kart">
      <h2>Yeni sözleşme yükle</h2>
      <p className="bos-not">
        Poz listesi ve birim fiyatları içeren Excel dosyasını yükleyin. Kaydetmeden önce içeriği
        önizleyebilirsiniz.
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
        <div className="bos-not">Yalnızca .xlsx · en fazla 10 MB</div>
        <input
          ref={dosyaSecici}
          type="file"
          accept=".xlsx"
          aria-label="Sözleşme dosyası (.xlsx)"
          disabled={calisiyor !== null}
          onChange={(e) => dosyaSecildi(e.target.files?.[0] ?? null)}
        />
      </div>

      {/* Çok sayfalı dosyada sunucu hangi sayfanın okunacağını sorar; yanlış sayfayı sessizce
          okumak yanlış fiyatları yazmak demek. */}
      {onizleme && onizleme.sayfaAdlari.length > 1 && (
        <div className="alan">
          <label htmlFor="sozlesme-sayfa">Okunacak sayfa</label>
          <select id="sozlesme-sayfa" value={sayfa} onChange={(e) => sayfaSecildi(e.target.value)}>
            {onizleme.sayfaAdlari.map((secenek) => (
              <option key={secenek} value={secenek}>
                {secenek}
              </option>
            ))}
          </select>
        </div>
      )}

      <div className="dugme-grubu">
        <button disabled={!dosya || calisiyor !== null} onClick={onizle}>
          {calisiyor === 'onizleme' ? 'İşleniyor…' : 'Önizle'}
        </button>
        <span className="dugme-notu">Kaydetmeden önce poz sayısını ve fiyatları kontrol edersiniz.</span>
      </div>

      {onizleme && (
        <div className="bosluk-ust">
          <Serit tur="bilgi">
            {onizleme.sayfaAdi} sayfasından <strong>{onizleme.pozSayisi}</strong> satır okundu:{' '}
            {onizleme.grupSayisi} grup başlığı, {onizleme.fiyatliSayisi} fiyatlı kalem,{' '}
            {onizleme.fiyatsizSayisi} fiyatsız kalem.
          </Serit>
          {onizleme.uyarilar.map((uyari) => (
            <Serit key={uyari} tur="uyari">
              {uyari}
            </Serit>
          ))}

          {/* Çakışma artık ENGEL DEĞİL (sunucu boştaki ilk koda kayıyor), ama sessiz de değil:
              aynı dosyayı ikinci kez yüklediğini fark etmeyen yönetici iki kayıt üretmesin. */}
          {onizleme.kodKullanimda && (
            <Serit tur="uyari">
              Bu isimde bir sözleşme <strong>zaten var</strong>. Yüklemeye devam ederseniz{' '}
              <strong>ayrı bir kayıt</strong> olarak eklenir; eskisini kullanımdan çıkarmak için
              listeden <em>Pasife al</em> deyin.
            </Serit>
          )}

          {/* Sözleşme adı: ekranda kalan TEK karar noktası. Serbest metin — büyük harf, boşluk,
              `_` ve `-` dahil her şey kabul; içsel kod bundan bağımsız olarak sunucuda üretiliyor. */}
          <div className="alan bosluk-ust">
            <label htmlFor="sozlesme-ad">Sözleşme adı (opsiyonel)</label>
            <input
              id="sozlesme-ad"
              type="text"
              value={ad}
              placeholder={onizleme.ad}
              onChange={(e) => adDegisti(e.target.value)}
            />
            <div className="bos-not">Boş bırakılırsa dosyadan gelen ad kullanılır: "{onizleme.ad}"</div>
          </div>

          <div className="dugme-grubu">
            <button disabled={!kaydedilebilir || calisiyor !== null} onClick={kaydet}>
              {calisiyor === 'yukleme' ? 'Kaydediliyor…' : 'Sözleşmeyi Kaydet'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * Satır menüsü: poz listesini görüntüle · indir · sil.
 *
 * ⚠️ **Birim fiyat düzenleme bilinçli olarak yok** (kullanıcı kararı, 6 Ağustos 2026 · Faz V'de
 * korundu). Yayındaki bir fiyatı yerinde değiştirmek §7/4'ü bozar ve somut bir sessiz hataya yol
 * açar: açık bir taslağın ekranı poz listesini sürümden okur, girilmiş satırlar ise fiyatı
 * `belge_satir`'da dondurmuştur — sürüm değişirse ekranda yeni fiyat görünüp belgeye eski fiyat
 * yazılır (§7/4b). Değişiklik yolu "indir → düzelt → yeni kodla yükle".
 *
 * **Yeni kalem eklemek** bu gerekçenin dışında: hiçbir mevcut satır kodu kaymıyor, hiçbir fiyat
 * değişmiyor. Ekleme, poz listesi katmanının içinde.
 *
 * ~~Revize Et~~ 18 Ağustos 2026'da kaldırıldı (kullanıcı isteği): aynı işi "İndir → düzenle → yeni
 * kodla yükle" zaten yapıyordu, akış yalnızca onun kılavuzlu kısayoluydu.
 *
 * **Sil** en altta ve `tehlike-metin` sınıflı (dolgulu `.tehlike` bir menü listesinde fazla ağır
 * durur — aynı ayrım `KabloEnvanteri`'nde de var): menüdeki tek geri dönüşsüz eylem, kazara
 * tıklanmaması için ötekilerden görsel olarak ayrılıyor ve ayrıca onay istiyor.
 *
 * Açılıp kapanma `<details>`'ten, **dışarı tıklayınca ve Escape ile kapanma** `useAcilirMenu`'den
 * geliyor — native `<details>` bunları kendiliğinden yapmıyor.
 */
function SatirMenusu({
  sozlesme,
  kilitli,
  pozlariGoster,
  adiDuzenle,
  indir,
  silmeyiAc,
}: {
  sozlesme: SozlesmeYonetimSatiri
  kilitli: boolean
  pozlariGoster: () => void
  adiDuzenle: () => void
  indir: () => void
  silmeyiAc: () => void
}) {
  const { ref, kapat } = useAcilirMenu()

  /** Seçim yapıldıktan sonra menü açık kalmamalı. */
  function sec(eylem: () => void) {
    kapat()
    eylem()
  }

  return (
    <details ref={ref} className="satir-menu">
      <summary className="sade" aria-label={`${sozlesme.ad} işlemleri`}>
        ⋮
      </summary>
      <div className="satir-menu-secenekler">
        <button className="sade" onClick={() => sec(pozlariGoster)}>
          Poz listesini görüntüle
        </button>
        <button className="sade" disabled={kilitli} onClick={() => sec(adiDuzenle)}>
          Adı düzenle
        </button>
        <button className="sade" disabled={kilitli} onClick={() => sec(indir)}>
          İndir (.xlsx)
        </button>
        <button className="sade tehlike-metin" disabled={kilitli} onClick={() => sec(silmeyiAc)}>
          Sil
        </button>
      </div>
    </details>
  )
}

/**
 * Ad ve başlık düzenleme katmanı.
 *
 * İki alan birlikte duruyor çünkü `PUT /api/sozlesmeler/{kod}` ikisini de istiyor ve ikisi de
 * kullanıcıya görünen metin: **ad** listede ve belge ekranındaki seçicide, **başlık** PDF'in üst
 * satırında basılıyor. **Kod alanı yok** — içsel anahtar, değişirse geçmiş bağlar kopar (§7/4).
 */
function AdDuzenleme({
  sozlesme,
  calisiyor,
  hata,
  vazgec,
  kaydet,
}: {
  sozlesme: SozlesmeYonetimSatiri
  calisiyor: boolean
  hata: string | null
  vazgec: () => void
  kaydet: (ad: string, baslik: string) => void
}) {
  const [ad, setAd] = useState(sozlesme.ad)
  const [baslik, setBaslik] = useState(sozlesme.baslik)

  useKatman(calisiyor ? undefined : vazgec)

  // Sunucu ikisini de boş kabul etmiyor; düğmeyi kapatmak hatayı beklemeden söylüyor.
  const gecerli = ad.trim() !== '' && baslik.trim() !== ''

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu">
        <h3>Sözleşmeyi düzenle</h3>

        <div className="alan">
          <label htmlFor="sozlesme-duzenle-ad">Sözleşme adı</label>
          <input
            id="sozlesme-duzenle-ad"
            type="text"
            value={ad}
            disabled={calisiyor}
            onChange={(e) => setAd(e.target.value)}
          />
          <div className="bos-not">Listede ve belge ekranındaki sözleşme seçiminde görünür.</div>
        </div>

        <div className="alan bosluk-ust">
          <label htmlFor="sozlesme-duzenle-baslik">Başlık</label>
          <input
            id="sozlesme-duzenle-baslik"
            type="text"
            value={baslik}
            disabled={calisiyor}
            onChange={(e) => setBaslik(e.target.value)}
          />
          <div className="bos-not">PDF çıktısının en üstünde basılan metin.</div>
        </div>

        <Serit tur="bilgi">
          Poz listesi, fiyatlar ve sözleşme kodu <strong>değişmez</strong>; yalnızca bu iki metin
          güncellenir. Geçmiş belgeler etkilenmez.
        </Serit>
        {hata && <Serit tur="hata">{hata}</Serit>}

        <div className="dugme-grubu">
          <button disabled={!gecerli || calisiyor} onClick={() => kaydet(ad.trim(), baslik.trim())}>
            {calisiyor ? 'Kaydediliyor…' : 'Kaydet'}
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
 * Silme onayı — menüdeki tek **geri dönüşsüz** eylem, bu yüzden kutu `hata` şeridiyle uyarıyor.
 *
 * Hata kutunun İÇİNDE gösteriliyor: sunucu "bu sözleşmeyle N belge açılmış, pasife alın" derse
 * kullanıcı mesajı kararı verdiği yerde okumalı. Kutu kapanıp mesaj listenin üstünde kalsaydı,
 * eylemle sebebi arasındaki bağ kopardı.
 */
function SilmeOnayi({
  sozlesme,
  calisiyor,
  hata,
  vazgec,
  onayla,
}: {
  sozlesme: SozlesmeYonetimSatiri
  calisiyor: boolean
  hata: string | null
  vazgec: () => void
  onayla: () => void
}) {
  useKatman(calisiyor ? undefined : vazgec)

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu">
        <h3>Sözleşmeyi sil</h3>
        <Serit tur="hata">
          <strong>{sozlesme.ad}</strong> ve <strong>{sozlesme.pozSayisi} poz</strong> içeren fiyat
          listesi kalıcı olarak silinecek. <strong>Bu işlem geri alınamaz.</strong>
          <div className="kucuk bosluk-ust">
            Yalnızca <strong>hiç belge açılmamış</strong> sözleşmeler silinebilir. Bu sözleşmeyle
            fizibilite/keşif/hakediş açılmışsa işlem reddedilir — o durumda kullanımdan çıkarmanın yolu{' '}
            <em>Pasife al</em>: mevcut belgeler çalışmaya devam eder, yalnızca yeni belge açılamaz.
          </div>
        </Serit>
        {hata && <Serit tur="hata">{hata}</Serit>}
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
 * Aktife/pasife alma onayı.
 *
 * İki yön de soruluyor: sözleşme listesi bütün bölge sorumlularının belge açarken gördüğü seçenek
 * kümesidir, dolayısıyla her iki yön de onların ekranını değiştiriyor. Pasife almak **yıkıcı
 * değildir** — mevcut belgeler etkilenmez, yalnızca yeni belge açılamaz; bu yüzden kutu kırmızı
 * çerçeveli değil.
 */
function AktiflikOnayi({
  sozlesme,
  calisiyor,
  vazgec,
  onayla,
}: {
  sozlesme: SozlesmeYonetimSatiri
  calisiyor: boolean
  vazgec: () => void
  onayla: () => void
}) {
  useKatman(calisiyor ? undefined : vazgec)
  const pasifeAliniyor = sozlesme.aktif

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu">
        <h3>{pasifeAliniyor ? 'Sözleşmeyi pasife al' : 'Sözleşmeyi aktife al'}</h3>
        <Serit tur={pasifeAliniyor ? 'uyari' : 'bilgi'}>
          <strong>{sozlesme.ad}</strong>{' '}
          {pasifeAliniyor
            ? 'pasife alınacak. Bundan sonra bu sözleşmeyle yeni belge açılamaz.'
            : 'aktife alınacak. Bölge sorumluları yeni belge açarken bu sözleşmeyi yeniden seçebilecek.'}
          <div className="kucuk bosluk-ust">
            {pasifeAliniyor
              ? 'Mevcut belgeler etkilenmez: fiyatları belgeye dondurulmuştur. İstenirse aynı ekrandan yeniden aktife alınabilir.'
              : 'Poz listesi ve fiyatlar yüklendiği gibi kalır; aktiflik dışında hiçbir şey değişmez.'}
          </div>
        </Serit>
        <div className="dugme-grubu">
          <button disabled={calisiyor} onClick={onayla}>
            {calisiyor
              ? 'Uygulanıyor…'
              : pasifeAliniyor
                ? 'Evet, Pasife Al'
                : 'Evet, Aktife Al'}
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
 * Poz listesi katmanı: görüntüleme **ve** yeni kalem ekleme (Faz V).
 *
 * Ayrı bir sözleşme detay **ekranı** açılmıyor (S12 korunuyor) — ekleme aynı katmanın içinde, her
 * grup başlığının altındaki "+ Bu gruba kalem ekle" satırından açılıyor. Yönetici kalemi göreceği
 * yerde ekliyor; "hangi grubun altına" sorusu ayrı bir seçim kutusu gerektirmiyor.
 *
 * Kaydetmeden sonra liste **sunucudan gelen** yanıtla değişiyor: sıra ve satır kodu sunucuda
 * hesaplanıyor, web tahmin etmiyor.
 */
function PozKatmani({
  sozlesme,
  kapat,
  eklendi,
}: {
  sozlesme: SozlesmeYonetimSatiri
  kapat: () => void
  /** Poz sayısı listede görünüyor; ekleme sonrası dış liste tazelenmeli. */
  eklendi: () => void
}) {
  const [pozlar, setPozlar] = useState<Poz[] | null>(null)
  const [hata, setHata] = useState<string | null>(null)
  /** Ekleme formu hangi grup başlığının altında açık. `''` = grupsuz (listenin sonu). */
  const [formGrubu, setFormGrubu] = useState<string | null>(null)

  useKatman(kapat)

  const cek = useCallback(() => {
    setHata(null)
    api
      .sozlesmePozlari(sozlesme.kod)
      .then(setPozlar)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Poz listesi yüklenemedi.'))
  }, [sozlesme.kod])

  useEffect(cek, [cek])

  function kaydedildi(yeniListe: Poz[]) {
    setPozlar(yeniListe)
    setFormGrubu(null)
    eklendi()
  }

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu genis">
        <h3>{sozlesme.ad} — poz listesi</h3>
        <p className="bos-not">{sozlesme.baslik}</p>

        {hata ? (
          <YuklemeHatasi mesaj={hata} yenidenDene={cek} />
        ) : !pozlar ? (
          <Yukleniyor />
        ) : (
          <div className="tablo-sarici poz-sarici">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 90 }}>Poz No</th>
                  <th>İş Kalemi</th>
                  <th style={{ width: 80 }}>Birim</th>
                  <th className="sayi" style={{ width: 130 }}>
                    Br Fiyat
                  </th>
                </tr>
              </thead>
              <tbody>
                {pozlar.map((poz, sira) => (
                  <PozSatiri
                    key={poz.id}
                    poz={poz}
                    sozlesmeKodu={sozlesme.kod}
                    // Ekleme çağrısı bir sonraki grup başlığından önceye yazıyor; düğme bu yüzden
                    // grubun SON satırının altında değil, grup başlığının kendi satırında duruyor.
                    formAcik={formGrubu === poz.id}
                    formAc={() => setFormGrubu(poz.id)}
                    formKapat={() => setFormGrubu(null)}
                    kaydedildi={kaydedildi}
                    sonSatirMi={sira === pozlar.length - 1}
                  />
                ))}
              </tbody>
            </table>
          </div>
        )}

        <div className="dugme-grubu bosluk-ust">
          <button className="ikincil" onClick={kapat}>
            Kapat
          </button>
        </div>
      </div>
    </div>
  )
}

/** Tek poz satırı; grup başlıklarında ayrıca "kalem ekle" düğmesi ve açılan form. */
function PozSatiri({
  poz,
  sozlesmeKodu,
  formAcik,
  formAc,
  formKapat,
  kaydedildi,
  sonSatirMi,
}: {
  poz: Poz
  sozlesmeKodu: string
  formAcik: boolean
  formAc: () => void
  formKapat: () => void
  kaydedildi: (pozlar: Poz[]) => void
  sonSatirMi: boolean
}) {
  return (
    <>
      <tr className={poz.isGroup ? 'grup' : undefined}>
        <td>{poz.pozNo}</td>
        <td colSpan={poz.isGroup ? 3 : 1}>
          {poz.name}
          {poz.isGroup && !formAcik && (
            <button className="sade kucuk" onClick={formAc}>
              + Bu gruba kalem ekle
            </button>
          )}
        </td>
        {!poz.isGroup && (
          <>
            <td>{poz.unit}</td>
            {/* Fiyat sunucudan biçimlenmiş gelir; web para biçimlendirmesi yapmaz. */}
            <td className="sayi hesaplanan">{poz.isPriced ? poz.unitPriceGosterim : '—'}</td>
          </>
        )}
      </tr>
      {formAcik && (
        <PozEklemeSatiri
          sozlesmeKodu={sozlesmeKodu}
          grupSatirKodu={poz.id}
          vazgec={formKapat}
          kaydedildi={kaydedildi}
        />
      )}
      {/* Grup başlığı olmayan bir listede ekleme yolu kalmasın diye son satırın altında da bir
          giriş noktası var; grup seçilmediğinde kalem listenin sonuna eklenir. */}
      {sonSatirMi && !formAcik && <SonaEkleSatiri sozlesmeKodu={sozlesmeKodu} kaydedildi={kaydedildi} />}
    </>
  )
}

/** Listenin sonuna ekleme — grup seçilmeden. */
function SonaEkleSatiri({ sozlesmeKodu, kaydedildi }: { sozlesmeKodu: string; kaydedildi: (pozlar: Poz[]) => void }) {
  const [acik, setAcik] = useState(false)

  if (acik) {
    return (
      <PozEklemeSatiri
        sozlesmeKodu={sozlesmeKodu}
        grupSatirKodu={undefined}
        vazgec={() => setAcik(false)}
        kaydedildi={(pozlar) => {
          setAcik(false)
          kaydedildi(pozlar)
        }}
      />
    )
  }

  return (
    <tr>
      <td colSpan={4}>
        <button className="sade kucuk" onClick={() => setAcik(true)}>
          + Listenin sonuna kalem ekle
        </button>
      </td>
    </tr>
  )
}

/**
 * Satır içi ekleme formu.
 *
 * Birim fiyat **metin** olarak gönderiliyor: `,` ve `.` ikisi de kabul ediliyor ve ayrıştırma
 * sunucuda (`QuantityParser`). Web sayıya çevirseydi 17 haneli hassasiyet `double`'a düşerdi
 * (CLAUDE.md §5).
 */
function PozEklemeSatiri({
  sozlesmeKodu,
  grupSatirKodu,
  vazgec,
  kaydedildi,
}: {
  sozlesmeKodu: string
  grupSatirKodu?: string
  vazgec: () => void
  kaydedildi: (pozlar: Poz[]) => void
}) {
  const [pozNo, setPozNo] = useState('')
  const [ad, setAd] = useState('')
  const [birim, setBirim] = useState('')
  const [birimFiyat, setBirimFiyat] = useState('')
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  const gecerli = pozNo.trim() !== '' && ad.trim() !== '' && birim.trim() !== '' && birimFiyat.trim() !== ''

  async function kaydet() {
    setHata(null)
    setCalisiyor(true)
    try {
      kaydedildi(await api.sozlesmePozEkle(sozlesmeKodu, { pozNo, ad, birim, birimFiyat, grupSatirKodu }))
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Kalem eklenemedi.')
    } finally {
      setCalisiyor(false)
    }
  }

  return (
    <tr className="poz-ekleme">
      <td colSpan={4}>
        <div className="satir-form">
          <input
            aria-label="Poz numarası"
            placeholder="4.11"
            value={pozNo}
            onChange={(e) => setPozNo(e.target.value)}
          />
          <input
            aria-label="İş kalemi adı"
            placeholder="İş kaleminin adı"
            className="genis-alan"
            value={ad}
            onChange={(e) => setAd(e.target.value)}
          />
          <input aria-label="Birim" placeholder="Ad." value={birim} onChange={(e) => setBirim(e.target.value)} />
          <input
            aria-label="Birim fiyat"
            placeholder="1.234,56"
            inputMode="decimal"
            value={birimFiyat}
            onChange={(e) => setBirimFiyat(e.target.value)}
          />
          <button disabled={!gecerli || calisiyor} onClick={() => void kaydet()}>
            {calisiyor ? 'Ekleniyor…' : 'Ekle'}
          </button>
          <button className="ikincil" disabled={calisiyor} onClick={vazgec}>
            Vazgeç
          </button>
        </div>
        {hata && <Serit tur="hata">{hata}</Serit>}
      </td>
    </tr>
  )
}
