import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api, oturumDusunceCagir } from './api'
import { basHarfler, Yukleniyor } from './bilesen'
import { BelgeEkrani } from './ekran/BelgeEkrani'
import { BelgeListesi } from './ekran/BelgeListesi'
import { Giris } from './ekran/Giris'
import { IsListesi } from './ekran/IsListesi'
import { KabloEnvanteri } from './ekran/KabloEnvanteri'
import { Kullanicilar } from './ekran/Kullanicilar'
import { Sozlesmeler } from './ekran/Sozlesmeler'
import { YeniIs } from './ekran/YeniIs'
import { etkinSekme, git, kirintiYolu, useRota } from './yonlendirme'
import type { Rota } from './yonlendirme'
import type { Kullanici } from './tipler'

/** Arama kutusunda her tuşta istek gitmesin diye bekleme süresi. */
const ARAMA_BEKLEME_MS = 300

/**
 * Kenar çubuğu bağlantısı.
 *
 * Gerçek bir `<a href>`: orta tıkla yeni sekmede açma ve bağlantı kopyalama bedavaya gelir. Sol tık
 * yine `git()`'ten geçer — tarayıcıya bırakılsaydı **aynı** adrese basmak `hashchange` üretmez ve
 * ekran tazelenmezdi (`yonlendirme.ts`'teki dinleyici kümesi tam bunun için var, Düzeltme Turu 2).
 */
function YanBag({ yol, etkin, children }: { yol: string; etkin: boolean; children: ReactNode }) {
  return (
    <a
      className={etkin ? 'yan-bag etkin' : 'yan-bag'}
      href={`#${yol}`}
      aria-current={etkin ? 'page' : undefined}
      onClick={(olay) => {
        olay.preventDefault()
        git(yol)
      }}
    >
      <span className="yan-nokta" aria-hidden="true" />
      <span className="yan-bag-etiket">{children}</span>
    </a>
  )
}

function Kirintilar({ rota }: { rota: Rota }) {
  const parcalar = kirintiYolu(rota)
  return (
    <nav className="kirinti" aria-label="Konum">
      <ol>
        {parcalar.map((parca, sira) => {
          const sonuncu = sira === parcalar.length - 1
          return (
            <li key={parca.ad}>
              {parca.yol && !sonuncu ? (
                <a
                  href={`#${parca.yol}`}
                  onClick={(olay) => {
                    olay.preventDefault()
                    git(parca.yol!)
                  }}
                >
                  {parca.ad}
                </a>
              ) : (
                <span aria-current={sonuncu ? 'page' : undefined}>{parca.ad}</span>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}

export function App() {
  const [kullanici, setKullanici] = useState<Kullanici | null>(null)
  const [aciliyor, setAciliyor] = useState(true)
  const { rota, tazeleme } = useRota()
  /** Kutuya yazılan ham metin (kontrollü giriş). */
  const [aramaGirisi, setAramaGirisi] = useState('')
  /** Gecikmeli hâli — listeye bu iniyor. */
  const [arama, setArama] = useState('')
  /** Proje sayısı; kenar çubuğundaki rozet. Listenin zaten elinde olan sayı — sunucuya sorulmuyor. */
  const [projeSayisi, setProjeSayisi] = useState<number | null>(null)

  // Sayfa yenilendiğinde oturum cookie'si hâlâ geçerli olabilir; kullanıcıyı yeniden giriş yapmaya
  // zorlamadan önce sunucuya sorulur. 401 beklenen bir cevaptır, hata değil.
  useEffect(() => {
    api
      .ben()
      .then(setKullanici)
      .catch(() => setKullanici(null))
      .finally(() => setAciliyor(false))
  }, [])

  useEffect(() => {
    oturumDusunceCagir(() => setKullanici(null))
  }, [])

  useEffect(() => {
    const zamanlayici = window.setTimeout(() => setArama(aramaGirisi), ARAMA_BEKLEME_MS)
    return () => window.clearTimeout(zamanlayici)
  }, [aramaGirisi])

  // Ekran değişince arama sıfırlanır: iki liste farklı alanlarda arıyor ve projelerde yazılan bir
  // route ("R-123") belgelerde hiçbir şeyle eşleşmez — kutu dolu, liste boş, arayüz bozuk görünürdü.
  useEffect(() => {
    setAramaGirisi('')
    setArama('')
  }, [rota.ad])

  async function cikis() {
    try {
      await api.cikis()
    } finally {
      setKullanici(null)
      git('/')
    }
  }

  if (aciliyor) return <Yukleniyor />
  if (!kullanici) return <Giris girisYapildi={setKullanici} />

  const yonetici = kullanici.rol === 'YONETICI'
  /**
   * Kenar çubuğu kullanıcı adı değil **bölge adı** gösterir (müdür isteği, Ağustos 2026): ekrana
   * bakan kişi için "hangi bölgenin verisine bakıyorum" sorusu "kim giriş yapmış"tan daha önemli.
   * Ad henüz gelmemişse koda düşülür — boş bir kimlik alanı bırakmaktansa kod okunur bir yedektir.
   */
  const kimlikMetni = yonetici ? 'Yönetici' : (kullanici.bolgeAdi ?? kullanici.bolgeKodu ?? 'Bölge atanmamış')
  const etkin = etkinSekme(rota)
  /**
   * Arama kutusu yalnızca **sunucuda karşılığı olan** ekranlarda çıkar; hiçbir şeyi süzmeyen bir
   * kutu, hiç olmamasından kötüdür. İpucu metni de ekranın gerçekten aradığı alanları sayar.
   */
  const aramaIpucu =
    rota.ad === 'isler'
      ? 'SP no, kablo ID veya route ara'
      : rota.ad === 'belgeler'
        ? 'Belge no, SP no veya kablo ID ara'
        : null

  return (
    <div className="uygulama">
      <nav className="yan-bar" aria-label="Ana gezinme">
        <div className="yan-marka">
          <span className="yan-marka-ad">Saha Hakediş</span>
          <span className="yan-marka-alt">TT INTERNATIONAL</span>
        </div>

        <div className="yan-baslik" id="yanBolum">
          ÇALIŞMA ALANI
        </div>
        <ul className="yan-liste" aria-labelledby="yanBolum">
          {/* Projeler önce: çalışma ekranı odur (CLAUDE.md §9), Belgeler arşivdir. */}
          <li>
            <YanBag yol="/isler" etkin={etkin === '/isler'}>
              Projeler
              {projeSayisi !== null && <span className="yan-sayi">{projeSayisi}</span>}
            </YanBag>
          </li>
          <li>
            <YanBag yol="/belgeler" etkin={etkin === '/belgeler'}>
              Belgeler
            </YanBag>
          </li>
          {/* Yönetici sekmeleri. Gizlemek bir güvenlik önlemi değil — yetki sunucuda uca bağlı;
              bölge sorumlusuna çalışmayacak düğme göstermemek için. */}
          {yonetici && (
            <>
              <li>
                <YanBag yol="/sozlesmeler" etkin={etkin === '/sozlesmeler'}>
                  Sözleşmeler
                </YanBag>
              </li>
              {/* Kablo envanteri Sözleşmeler'in yanında: ikisi de "sistemin beslendiği dış veri". */}
              <li>
                <YanBag yol="/kablo-envanteri" etkin={etkin === '/kablo-envanteri'}>
                  Kablo Envanteri
                </YanBag>
              </li>
              <li>
                <YanBag yol="/kullanicilar" etkin={etkin === '/kullanicilar'}>
                  Kullanıcılar
                </YanBag>
              </li>
            </>
          )}
        </ul>

        <div className="yan-bosluk" />

        <div className="yan-kullanici">
          <span className="yan-avatar" aria-hidden="true">
            {basHarfler(kimlikMetni)}
          </span>
          <span className="yan-kullanici-metin">
            <span className="yan-kullanici-ad">{kimlikMetni}</span>
            {/* Alt satır rolü söyler; üst satır zaten bölgeyi yazdığı için kod tekrar edilmez. */}
            <span className="yan-kullanici-rol">{yonetici ? 'Tüm bölgeler' : 'Bölge sorumlusu'}</span>
          </span>
          <button className="sade yan-cikis" onClick={cikis}>
            Çıkış
          </button>
        </div>
      </nav>

      <div className="ana">
        <header className="ust-serit">
          <Kirintilar rota={rota} />
          {aramaIpucu && (
            <label className="arama">
              <span className="gizli-metin">{aramaIpucu}</span>
              <AramaSimgesi />
              <input
                type="search"
                value={aramaGirisi}
                placeholder={aramaIpucu}
                onChange={(olay) => setAramaGirisi(olay.target.value)}
              />
            </label>
          )}
        </header>

        {/* `key={tazeleme}`: aynı sekmeye tekrar basıldığında da ekran yeniden bağlanır ve verisini
            baştan çeker. Rota değiştiğinde zaten farklı bileşen çiziliyor; bu, "zaten oradayım"
            durumunu kapsıyor. */}
        <main className="govde" key={tazeleme}>
          {rota.ad === 'belgeler' && <BelgeListesi kullanici={kullanici} arama={arama} />}
          {rota.ad === 'isler' && <IsListesi kullanici={kullanici} arama={arama} sayiBildir={setProjeSayisi} />}
          {rota.ad === 'yeniIs' && <YeniIs kullanici={kullanici} />}
          {rota.ad === 'belge' && <BelgeEkrani id={rota.id} kullanici={kullanici} />}
          {rota.ad === 'belgeGozlem' && <BelgeEkrani id={rota.id} kullanici={kullanici} salt />}
          {rota.ad === 'kullanicilar' && <Kullanicilar />}
          {rota.ad === 'sozlesmeler' && <Sozlesmeler />}
          {rota.ad === 'kabloEnvanteri' && <KabloEnvanteri />}
        </main>
      </div>
    </div>
  )
}

function AramaSimgesi() {
  return (
    <svg className="arama-simge" viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <line x1="16.5" y1="16.5" x2="21" y2="21" />
    </svg>
  )
}
