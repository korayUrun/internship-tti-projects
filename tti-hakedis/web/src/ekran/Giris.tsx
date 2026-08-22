import { useState } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit } from '../bilesen'
import ttiLogo from '../tti-logo.png'
import type { Kullanici } from '../tipler'

export function Giris({ girisYapildi }: { girisYapildi: (kullanici: Kullanici) => void }) {
  const [kullaniciAdi, setKullaniciAdi] = useState('')
  const [sifre, setSifre] = useState('')
  const [hata, setHata] = useState<string | null>(null)
  const [gonderiliyor, setGonderiliyor] = useState(false)
  /**
   * Hangi alanlara dokunuldu.
   *
   * Kırmızı uyarı ancak dokunulmuş bir alan boş kaldığında çıkar: form açılır açılmaz "zorunludur"
   * yazması, kullanıcıyı daha hiçbir şey yapmadan hatalı gösteriyordu. Düğmenin neden kapalı
   * olduğunu ise nötr not baştan söylüyor.
   */
  const [dokunulan, setDokunulan] = useState<Set<string>>(new Set())

  const adEksik = kullaniciAdi.trim() === ''
  const sifreEksik = sifre === ''
  const eksik = adEksik || sifreEksik

  function dokunuldu(alan: string) {
    setDokunulan((onceki) => new Set(onceki).add(alan))
  }

  async function gonder(olay: React.FormEvent) {
    olay.preventDefault()
    // Gönderim denendiyse iki alan da "dokunulmuş" sayılır: kullanıcı artık davranmış durumda.
    setDokunulan(new Set(['kullaniciAdi', 'sifre']))
    if (eksik) return
    setHata(null)
    setGonderiliyor(true)
    try {
      girisYapildi(await api.giris(kullaniciAdi.trim(), sifre))
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Sunucuya ulaşılamadı.')
    } finally {
      setGonderiliyor(false)
    }
  }

  return (
    <div className="giris-sayfasi">
      {/*
        Logo `src/` altından import ediliyor, `public/` altından değil: Vite onu `/assets/` içine
        hash'li adla koyuyor ve güvenlik yapılandırması yalnızca `/assets/**`'i kimliksiz açıyor.
        `public/` altındaki bir dosya giriş yapılmadan 401 dönerdi — tam da lazım olduğu anda.
      */}
      <div className="giris-marka">
        <img src={ttiLogo} alt="Türk Telekom International" />
      </div>

      <div className="kart giris-kart">
        <h1>Saha Hakediş</h1>
        <p className="giris-alt-baslik">Devam etmek için kurumsal hesabınızla giriş yapın.</p>
        {hata && <Serit tur="hata">{hata}</Serit>}
        <form onSubmit={gonder}>
          <div className="alan">
            <label htmlFor="kullaniciAdi">Kullanıcı adı</label>
            <input
              id="kullaniciAdi"
              value={kullaniciAdi}
              autoComplete="username"
              autoFocus
              onBlur={() => dokunuldu('kullaniciAdi')}
              onChange={(e) => setKullaniciAdi(e.target.value)}
            />
            {dokunulan.has('kullaniciAdi') && adEksik && (
              <div className="alan-hata">Kullanıcı adı zorunludur.</div>
            )}
          </div>
          <div className="alan">
            <label htmlFor="sifre">Parola</label>
            <input
              id="sifre"
              type="password"
              value={sifre}
              autoComplete="current-password"
              onBlur={() => dokunuldu('sifre')}
              onChange={(e) => setSifre(e.target.value)}
            />
            {dokunulan.has('sifre') && sifreEksik && <div className="alan-hata">Parola zorunludur.</div>}
          </div>
          <button type="submit" disabled={eksik || gonderiliyor} style={{ width: '100%' }}>
            {gonderiliyor ? 'Giriş yapılıyor…' : 'Giriş'}
          </button>
          {eksik && <div className="dugme-notu bosluk-ust">Devam etmek için iki alanı da doldurun.</div>}
        </form>
      </div>
    </div>
  )
}
