import { useCallback, useEffect, useState } from 'react'
import { api, ApiHatasi } from '../api'
import {
  ASAMALAR,
  BosListe,
  DurumRozeti,
  durumAilesi,
  SayfaBasligi,
  Serit,
  TUR_ADI,
  tarih,
  useOdaktaTazele,
  Yukleniyor,
  YuklemeHatasi,
} from '../bilesen'
import { git } from '../yonlendirme'
import { DurumPaneli } from './DurumPaneli'
import { SozlesmeSecici } from './SozlesmeSecici'
import type { BelgeOzeti, BelgeTuru, Bolge, DurumKaydi, Is, IsSuzgeci, Kullanici } from '../tipler'

/** Proje durumu — Belgeler ekranıyla aynı yeknesek dört değer (müdür isteği, madde 17). */
const IS_DURUMLARI: { deger: string; ad: string }[] = [
  { deger: '', ad: 'Tümü' },
  { deger: 'ONAY_BEKLIYOR', ad: 'Onay Bekliyor' },
  { deger: 'ISLENIYOR', ad: 'İşleniyor' },
  { deger: 'TAMAMLANDI', ad: 'Tamamlandı' },
  { deger: 'IPTAL', ad: 'İptal Edildi' },
]

/**
 * İş listesi — uygulamanın **çalışma ekranı** (3 Ağustos 2026 yeniden düzeni).
 *
 * Her iş tek bir kart: üstte tanımlayıcılar (SP No · Bölge · Kablo ID · Ek Aralığı · Route · Durum),
 * altında üç aşama satırı. Her aşama satırında o belgenin açılma tarihi, son değiştirilme tarihi,
 * kimin güncellediği, toplam tutarı ve iki düğmesi var: **Güncelle** (düzenle) ve **Gözlemle**
 * (salt okunur + çıktı indirme).
 *
 * Ayrı bir "İş Detayı" ekranı **yok**: aynı bilgi için ikinci bir tık gerekiyordu ve o ekranda
 * gösterilenlerin hepsi buraya sığıyor.
 */
export function IsListesi({
  kullanici,
  arama,
  sayiBildir,
}: {
  kullanici: Kullanici
  /** Kabuktaki arama kutusundan gelen (gecikmeli) metin — sunucuya `ara` olarak gider. */
  arama: string
  /** Kenar çubuğundaki proje sayacı. Listenin zaten elinde olan sayı; sunucuya ayrıca sorulmuyor. */
  sayiBildir: (sayi: number) => void
}) {
  const yonetici = kullanici.rol === 'YONETICI'
  const [suzgec, setSuzgec] = useState<IsSuzgeci>({})
  const [isler, setIsler] = useState<Is[] | null>(null)
  const [bolgeler, setBolgeler] = useState<Bolge[]>([])
  const [hata, setHata] = useState<string | null>(null)
  const [iniyor, setIniyor] = useState(false)
  /** Sözleşme seçici hangi (iş, aşama) için açık. */
  const [secici, setSecici] = useState<{ is: Is; tur: BelgeTuru } | null>(null)

  useEffect(() => {
    if (yonetici) api.bolgeler().then(setBolgeler).catch(() => setBolgeler([]))
  }, [yonetici])

  /**
   * [sessiz] `true` ise liste boşaltılmaz: sekmeye dönüldüğünde tazeleme yapılırken ekranın bir an
   * "Yükleniyor…"a düşmesi, duran bir listede gereksiz bir titreme oluyordu.
   */
  /** Ekranın ve indirilen dosyanın **aynı** süzgeci kullanması için tek yerde birleştirilir. */
  const tamSuzgec: IsSuzgeci = { ...suzgec, ara: arama.trim() || undefined }

  const cek = useCallback(
    async (sessiz = false) => {
      if (!sessiz) setIsler(null)
      setHata(null)
      try {
        const liste = await api.isler({ ...suzgec, ara: arama.trim() || undefined })
        setIsler(liste)
        sayiBildir(liste.length)
      } catch (e) {
        setHata(e instanceof ApiHatasi ? e.message : 'Projeler yüklenemedi.')
      }
    },
    [suzgec, arama, sayiBildir],
  )

  useEffect(() => {
    void cek()
  }, [cek])

  useOdaktaTazele(() => void cek(true))

  function degistir(alan: keyof IsSuzgeci, deger: string) {
    setSuzgec((onceki) => ({ ...onceki, [alan]: deger }))
  }

  /** Dosya **ekrandaki süzgeçle** iner: kullanıcı ne görüyorsa onu indirmiş olmalı. */
  async function exceleAktar() {
    setHata(null)
    setIniyor(true)
    try {
      await api.islerExcelIndir(tamSuzgec)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Excel indirilemedi.')
    } finally {
      setIniyor(false)
    }
  }

  return (
    <>
      <SayfaBasligi baslik="Projeler">
        <div className="dugme-grubu">
          <button className="ikincil" disabled={!isler?.length || iniyor} onClick={() => void exceleAktar()}>
            {iniyor ? 'Hazırlanıyor…' : 'Projeleri Excel olarak indir'}
          </button>
          {!yonetici && <button onClick={() => git('/isler/yeni')}>Yeni Proje</button>}
        </div>
      </SayfaBasligi>

      <div className="suzgec-satiri">
        <label className="suzgec-hap">
          <span className="gizli-metin">Proje durumu</span>
          <select value={suzgec.durum ?? ''} onChange={(e) => degistir('durum', e.target.value)}>
            {IS_DURUMLARI.map((d) => (
              <option key={d.deger} value={d.deger}>
                {d.deger === '' ? 'Tüm durumlar' : d.ad}
              </option>
            ))}
          </select>
        </label>

        {/* Bölge süzgeci yalnızca yöneticide anlamlı; bölge sorumlusu zaten tek bölge görüyor. */}
        {yonetici && (
          <label className="suzgec-hap">
            <span className="gizli-metin">Bölge</span>
            <select value={suzgec.bolgeKodu ?? ''} onChange={(e) => degistir('bolgeKodu', e.target.value)}>
              <option value="">Tüm bölgeler</option>
              {bolgeler.map((b) => (
                <option key={b.kod} value={b.kod}>
                  {b.ad}
                </option>
              ))}
            </select>
          </label>
        )}

        {/*
          Etkin süzgeç çipleri — hangi süzgecin açık olduğunu tek bakışta gösterir ve `×` ile kaldırır.
          Açılır listelerin kendisi zaten seçimi gösteriyor ama arama kutusu kabukta, listeden uzakta:
          "neden 3 proje görüyorum" sorusunun cevabı burada duruyor.
        */}
        {suzgec.durum && (
          <SuzgecCipi
            etiket={`Durum: ${IS_DURUMLARI.find((d) => d.deger === suzgec.durum)?.ad ?? suzgec.durum}`}
            kaldir={() => degistir('durum', '')}
          />
        )}
        {suzgec.bolgeKodu && (
          <SuzgecCipi
            etiket={`Bölge: ${bolgeler.find((b) => b.kod === suzgec.bolgeKodu)?.ad ?? suzgec.bolgeKodu}`}
            kaldir={() => degistir('bolgeKodu', '')}
          />
        )}
        {arama.trim() && <SuzgecCipi etiket={`Arama: ${arama.trim()}`} />}

        {/* Süzülmemiş toplamı sunucu vermiyor; "N projeden M'si" uydurma bir payda olurdu. */}
        {isler && <span className="suzgec-sayi">{isler.length} proje gösteriliyor</span>}
      </div>

      {hata && <YuklemeHatasi mesaj={hata} yenidenDene={() => void cek()} />}

      {!isler ? (
        hata ? null : (
          <Yukleniyor />
        )
      ) : isler.length === 0 ? (
        <BosListe
          baslik="Bu süzgeçle eşleşen proje yok."
          aciklama={yonetici ? 'Süzgeci genişletmeyi deneyin.' : 'Süzgeci genişletin ya da yeni bir proje açın.'}
        >
          {!yonetici && <button onClick={() => git('/isler/yeni')}>Yeni Proje</button>}
        </BosListe>
      ) : (
        isler.map((is) => (
          <IsKarti key={is.id} is={is} yonetici={yonetici} tazele={() => void cek(true)} sozlesmeSor={setSecici} />
        ))
      )}

      {secici && (
        <SozlesmeSecici
          tur={secici.tur}
          varsayilanKod={null}
          kapat={() => setSecici(null)}
          ac={async (sozlesmeKodu) => {
            const belge = await api.belgeOlustur(secici.is.id, secici.tur, sozlesmeKodu)
            git(`/belgeler/${belge.id}`)
          }}
        />
      )}
    </>
  )
}

/** Bir aşamanın stepper üzerindeki hâli. */
type AsamaHali = 'tamam' | 'etkin' | 'bekleyen'

/** Bir aşamanın hesaplanmış durumu — hem stepper hem satır düğmeleri için. */
interface AsamaDurumu {
  tur: BelgeTuru
  belge: BelgeOzeti | null
  hal: AsamaHali
  /** Bu aşama için **yeni** bir belge açılabilir mi (bir önceki aşama işleme alınmış). */
  acilabilir: boolean
}

/**
 * Üç aşamanın sırayla hâli — **yeknesek durum makinesi** (Ağustos 2026).
 *
 * Bir aşama, bir önceki aşama yönetici tarafından **işleme alındığında** (`ISLENIYOR`) açılabilir;
 * fizibilite ilk aşamadır, önkoşulu yoktur. İşleme alınmış (`ISLENIYOR`) ve tamamlanmış aşama
 * stepper'da "tamam" (yeşil, tikli) görünür; onay bekleyen aşama "etkin"dir.
 */
function asamaHalleri(is: Is): AsamaDurumu[] {
  const belgeler = new Map(is.belgeler.filter((b) => b.durum !== 'IPTAL').map((b) => [b.tur, b]))
  const projeAktif = is.durum !== 'IPTAL' && is.durum !== 'TAMAMLANDI'

  return ASAMALAR.map((tur, i) => {
    const belge = belgeler.get(tur) ?? null
    const onceki = i === 0 ? null : (belgeler.get(ASAMALAR[i - 1]!) ?? null)
    const acilabilir = belge === null && projeAktif && (i === 0 || onceki?.durum === 'ISLENIYOR')
    const hal: AsamaHali =
      belge?.durum === 'ISLENIYOR' || belge?.durum === 'TAMAMLANDI'
        ? 'tamam'
        : belge?.durum === 'ONAY_BEKLIYOR' || acilabilir
          ? 'etkin'
          : 'bekleyen'
    return { tur, belge, hal, acilabilir }
  })
}

function IsKarti({
  is,
  yonetici,
  tazele,
  sozlesmeSor,
}: {
  is: Is
  yonetici: boolean
  tazele: () => void
  sozlesmeSor: (secim: { is: Is; tur: BelgeTuru }) => void
}) {
  /**
   * Kartın açık olup olmadığı — **yerel** state.
   *
   * Yukarı taşınmadı: liste odakta sessizce tazeleniyor (`useOdaktaTazele`) ve `key={is.id}`
   * sayesinde yerel durum tam da o tazelemede korunuyor; `<main key={tazeleme}>` ise gezinmede
   * doğru şekilde atıyor. Kümeyi listede tutmak her tıkta bütün kartları yeniden çizerdi.
   */
  const [acik, setAcik] = useState(false)
  const [gecmisAcik, setGecmisAcik] = useState(false)
  const asamalar = asamaHalleri(is)
  const aile = durumAilesi(is.durum)
  // Yöneticinin işleme alabileceği aşama: gönderilmiş, onay bekleyen tek aşama (en çok bir tane).
  const onaydakiAsama = asamalar.find((a) => a.belge?.durum === 'ONAY_BEKLIYOR' && a.belge?.gonderme)?.belge ?? null

  // Son aşamanın tutarı — **proje toplamı değil**, o yüzden aşama adıyla birlikte basılıyor.
  // İstemcide toplamak yasak (CLAUDE.md §13) ve sunucu proje toplamı vermiyor.
  const sonBelge = [...asamalar].reverse().find((a) => a.belge !== null)?.belge ?? null

  // İade basılırsa geri alınacak aşama: en ileri AÇIK aşama. Kararı sunucu da aynı kuralla
  // veriyor (`BelgeServisi.onceyeIade`); buradaki tek iş düğmenin metnine adı yazmak ve açık
  // aşama yokken düğmeyi hiç çizmemek.
  const iadeEdilecek = [...asamalar].reverse().find((a) => a.belge !== null)?.tur ?? null
  const sonHareket = is.belgeler
    .filter((b) => b.sonGuncelleme)
    .sort((a, b) => Date.parse(b.sonGuncelleme!) - Date.parse(a.sonGuncelleme!))[0]

  return (
    <div className={`kart is-karti ${aile}`}>
      <div className="is-ust">
        <div className="is-kimlik">
          {/* Her değer etiketiyle: `2026-BURSA-001 · mtc079… · EK 123 · R-123` dizisinde hangisinin
              ne olduğu ancak biçimden tahmin ediliyordu. */}
          <span className="is-sp-alani">
            <span className="deger-etiket">SP No</span>
            <span className={is.durum === 'IPTAL' ? 'is-sp iptal' : 'is-sp'}>{is.spNo}</span>
          </span>
          <DurumRozeti durum={is.durum ?? 'YOK'} metin={is.durumMetni} />
          <span className="is-bolge">
            <KonumSimgesi />
            {is.bolgeAdi}
          </span>
        </div>

        <div className="is-sag">
          {sonBelge && (
            <span className="is-tutar">
              <span className="is-tutar-etiket">{TUR_ADI[sonBelge.tur]}</span>
              <strong>{sonBelge.toplamGosterim}</strong>
            </span>
          )}
          <button
            type="button"
            className={acik ? 'sade is-ac acik' : 'sade is-ac'}
            aria-expanded={acik}
            aria-controls={`is-${is.id}-ayrinti`}
            aria-label={`${is.spNo} ayrıntılarını ${acik ? 'gizle' : 'göster'}`}
            onClick={() => setAcik((onceki) => !onceki)}
          >
            <ChevronSimgesi />
          </button>
        </div>
      </div>

      <div className="is-alt">
        <Etiketli etiket="Kablo ID" deger={is.kableNosid} mono />
        <Etiketli etiket="Ek Aralığı" deger={is.ekAraligi} />
        <Etiketli etiket="Route" deger={is.route} />
        {sonHareket && (
          <span className="is-hareket">
            <Etiketli
              etiket="Son işlem"
              deger={`${tarih(sonHareket.sonGuncelleme)} · ${sonHareket.sonGuncelleyenAdSoyad ?? '—'}`}
            />
          </span>
        )}
      </div>

      <AsamaRayi asamalar={asamalar} />

      {is.iptalGerekce && <Serit tur="uyari">İptal gerekçesi: {is.iptalGerekce}</Serit>}

      {/* Koşullu unmount yerine `hidden`: `aria-controls` hiçbir zaman boşa düşmesin. */}
      <div id={`is-${is.id}-ayrinti`} className="is-ayrinti" hidden={!acik}>
        <div className="asamalar">
          {asamalar.map(({ tur, belge, hal, acilabilir }) => (
            <AsamaSatiri key={tur} tur={tur} belge={belge} hal={hal} acilabilir={acilabilir} is={is} sozlesmeSor={sozlesmeSor} />
          ))}
        </div>

        {yonetici && (
          <>
            <div className="is-islem">
              <DurumPaneli
                durum={is.durum}
                onaydakiBelge={onaydakiAsama}
                islemeAl={async () => {
                  if (onaydakiAsama) {
                    await api.belgeOnayla(onaydakiAsama.id)
                    tazele()
                  }
                }}
                durumDegistir={async (hedef, gerekce) => {
                  await api.isDurumDegistir(is.id, hedef, gerekce)
                  tazele()
                }}
                iadeEdilecekAsama={iadeEdilecek ? TUR_ADI[iadeEdilecek] : null}
                iade={async (gerekce) => {
                  await api.isIade(is.id, gerekce)
                  tazele()
                }}
              />
              <button
                type="button"
                className="ikincil"
                aria-expanded={gecmisAcik}
                onClick={() => setGecmisAcik((v) => !v)}
              >
                {gecmisAcik ? 'Durum geçmişini gizle' : 'Durum geçmişi'}
              </button>
            </div>
            {/* Log listesi eylem satırının ALTINDA, tam genişlikte; en yeni üstte. */}
            {gecmisAcik && <DurumGecmisiListe isId={is.id} />}
          </>
        )}
      </div>
    </div>
  )
}

/**
 * Üç aşamalı ray.
 *
 * ⚠️ Yüzde genişlikli bir ilerleme çubuğu **değil**: ayrık `<li>`ler ve sınıf değiştiricileri.
 * Yüzde hesabı `toFixed` çağırmayı davet ediyor ve `dogrula-faz-r.sh` onu `web/src`'te yasaklıyor.
 *
 * Bağlayıcı, **önceki aşama tamamsa** yeşil olur.
 */
function AsamaRayi({ asamalar }: { asamalar: { tur: BelgeTuru; belge: BelgeOzeti | null; hal: AsamaHali }[] }) {
  return (
    <ol className="asama-rayi" aria-label="Proje aşamaları">
      {asamalar.map(({ tur, belge, hal }, sira) => (
        <li key={tur} className={`asama-adim ${hal}`}>
          <span className={`asama-nokta ${hal}`} aria-hidden="true">
            {hal === 'tamam' && <TikSimgesi />}
          </span>
          <span className="asama-rayi-ad">{TUR_ADI[tur]}</span>
          {/* Erişilebilir durum sözcüğü: belge varsa sunucunun metni, yoksa "Henüz açılmadı". */}
          <span className="gizli-metin">{belge ? belge.durumMetni : 'Henüz açılmadı'}</span>
          {sira < asamalar.length - 1 && (
            <span className={hal === 'tamam' ? 'asama-baglayici tamam' : 'asama-baglayici'} aria-hidden="true" />
          )}
        </li>
      ))}
    </ol>
  )
}

function AsamaSatiri({
  tur,
  belge,
  hal,
  acilabilir,
  is,
  sozlesmeSor,
}: {
  tur: BelgeTuru
  belge: BelgeOzeti | null
  hal: AsamaHali
  acilabilir: boolean
  is: Is
  sozlesmeSor: (secim: { is: Is; tur: BelgeTuru }) => void
}) {
  /**
   * "Güncelle" yalnızca onay bekleyen (düzenlenebilir) aşamada anlamlı; işleme alınmış/tamamlanmış
   * belge bir daha değişmez ve sunucu denemeyi 409 ile reddeder. Belgesiz aşama ancak bir önceki aşama
   * işleme alınmışsa ("Başlat") açılabilir.
   */
  const guncellenebilir = belge ? belge.durum === 'ONAY_BEKLIYOR' : acilabilir

  /**
   * Kapalı bir düğmenin nedeni — metin sunucudan gelen durum adını (`is.durumMetni`) gömüyor.
   * Onay bekleyen/tamamlanmış aşamada not yok: rozet zaten söylüyor.
   */
  const not =
    belge === null && !acilabilir
      ? `Bu aşama, önceki aşama yönetici tarafından işleme alınınca açılabilir.`
      : null

  return (
    <div className="asama-satiri">
      <div className="asama-icerik">
        <div className="asama-ad">
          <span className={`asama-nokta ${hal}`} aria-hidden="true">
            {hal === 'tamam' && <TikSimgesi />}
          </span>
          <strong>{TUR_ADI[tur]}</strong>
        </div>

        <div className="asama-ozet">
          {belge ? (
            <>
              <span>
                <span className="asama-ozet-etiket">Açılma</span> {tarih(belge.olusturma)}
              </span>
              <span>
                <span className="asama-ozet-etiket">Değiştirilme</span> {tarih(belge.sonGuncelleme)}
              </span>
              <span>
                <span className="asama-ozet-etiket">Güncelleyen</span>{' '}
                {belge.sonGuncelleyenAdSoyad ?? '—'}
                {belge.sonGuncelleyenRol === 'YONETICI' ? ' (Yönetici)' : ''}
              </span>
              <span>
                <span className="asama-ozet-etiket">Toplam</span> <strong>{belge.toplamGosterim}</strong>
              </span>
            </>
          ) : (
            <span className="bos-not">{hal === 'etkin' ? 'Henüz açılmadı — sırada bu adım var' : 'Henüz açılmadı'}</span>
          )}
          {not && <div className="asama-not">{not}</div>}
        </div>
      </div>

      <div className="asama-durum">{belge && <DurumRozeti durum={belge.durum} metin={belge.durumMetni} />}</div>

      <div className="asama-dugmeler">
        <button
          // Sıradaki adım birincil düğme: kartta yapılacak tek şey odur.
          className={hal === 'etkin' ? '' : 'ikincil'}
          disabled={!guncellenebilir}
          onClick={() => (belge ? git(`/belgeler/${belge.id}`) : sozlesmeSor({ is, tur }))}
        >
          {belge ? 'Güncelle' : 'Başlat'}
        </button>
        <button className="sade" disabled={!belge} onClick={() => belge && git(`/belgeler/${belge.id}/gozlem`)}>
          Gözlemle
        </button>
      </div>
    </div>
  )
}

/** Durum kodu → Türkçe etiket; bilinmeyen/eski kod (ACIK/KAPANDI) ham gösterilir. */
function isDurumEtiket(kod: string | null): string {
  if (!kod) return '—'
  return IS_DURUMLARI.find((d) => d.deger === kod)?.ad ?? kod
}

/**
 * Yöneticinin proje durum-değişikliği günlüğü — yeni durum · gerekçe · kim · ne zaman, **en yeni
 * üstte** (sunucu `durum_kaydi`'nı zaten böyle sıralıyor). Yalnızca yönetici müdahaleleri (iptal,
 * serbest durum değiştirme) kaydedilir.
 *
 * Aç/kapa durumu üstteki kartta (`IsKarti`); bu bileşen yalnızca listeyi çizer ve eylem satırının
 * **altında tam genişlikte** durur (log log alt alta).
 */
function DurumGecmisiListe({ isId }: { isId: number }) {
  const [kayitlar, setKayitlar] = useState<DurumKaydi[] | null>(null)
  const [hata, setHata] = useState<string | null>(null)

  useEffect(() => {
    let iptal = false
    api
      .isDurumGecmisi(isId)
      .then((k) => {
        if (!iptal) setKayitlar(k)
      })
      .catch((e) => {
        if (!iptal) setHata(e instanceof ApiHatasi ? e.message : 'Geçmiş yüklenemedi.')
      })
    return () => {
      iptal = true
    }
  }, [isId])

  return (
    <div className="durum-gecmisi-liste">
      {hata && <Serit tur="hata">{hata}</Serit>}
      {kayitlar === null && !hata && <span className="bos-not">Yükleniyor…</span>}
      {kayitlar !== null && kayitlar.length === 0 && (
        <span className="bos-not">Bu projede durum değişikliği kaydı yok.</span>
      )}
      {kayitlar?.map((k, i) => (
        <div key={i} className="durum-gecmisi-satir">
          <div>
            <strong>{isDurumEtiket(k.yeni)}</strong>
            {k.onceki && <span className="bos-not"> (önceki: {isDurumEtiket(k.onceki)})</span>}
          </div>
          <div className="bos-not">
            {k.kullaniciAdSoyad} · {tarih(k.zaman)}
          </div>
          <div>{k.gerekce}</div>
        </div>
      ))}
    </div>
  )
}

/**
 * Etiket + değer, tek satırda.
 *
 * Etiket **soluk ve küçük değil**, yalnızca soluk: §8 küçük fontu yasaklıyor. [mono] kimlikler için
 * (kablo, SP no) — sabit genişlikli rakam kartlar arasında göz karşılaştırmasını kolaylaştırıyor.
 */
/**
 * [deger] `null` olabilir ve `—` basılır: "Yeni Tesis" projesinde kablo ve uçlar boş olabiliyor
 * (Faz W). Boş bırakmak etiketi değersiz gösteriyordu — satır bozuk sanılıyordu.
 */
function Etiketli({ etiket, deger, mono = false }: { etiket: string; deger: string | null; mono?: boolean }) {
  const dolu = deger?.trim()
  return (
    <span className="etiketli">
      <span className="deger-etiket">{etiket}</span>
      <span className={mono && dolu ? 'deger mono' : 'deger'}>{dolu || '—'}</span>
    </span>
  )
}

function TikSimgesi() {
  return (
    <svg className="tik-simge" viewBox="0 0 16 16" aria-hidden="true">
      <path d="M3.5 8.5 L6.5 11.5 L12.5 4.5" />
    </svg>
  )
}

function KonumSimgesi() {
  return (
    <svg className="konum-simge" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11z" />
      <circle cx="12" cy="10" r="2.6" />
    </svg>
  )
}

function ChevronSimgesi() {
  return (
    <svg className="chevron-simge" viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 9.5 L12 15.5 L18 9.5" />
    </svg>
  )
}

/** Etkin süzgeç çipi. [kaldir] verilmezse çip bilgilendirir ama kaldırılamaz (arama kabukta). */
function SuzgecCipi({ etiket, kaldir }: { etiket: string; kaldir?: () => void }) {
  return (
    <span className="suzgec-cipi">
      {etiket}
      {kaldir && (
        <button type="button" className="sade cip-kaldir" aria-label={`${etiket} süzgecini kaldır`} onClick={kaldir}>
          ×
        </button>
      )}
    </span>
  )
}
