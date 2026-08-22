import { useEffect, useState } from 'react'
import { api, ApiHatasi } from '../api'
import { SayfaBasligi, Serit, tarih, Yukleniyor, YuklemeHatasi } from '../bilesen'
import type { Bolge, Rol, YonetimKullanicisi } from '../tipler'

/**
 * Kullanıcı yönetimi — **yalnızca yönetici** (müdür geri bildirimi, madde 12 & 13).
 *
 * Kapsam: **yeni kullanıcı ekleme**, **ad soyad / rol / bölge düzenleme**, **kullanıcı adı** ve
 * **parola** değiştirme, **pasife alma / aktifleştirme**. Kullanıcı adı kimliktir ve ayrı bir eylemle
 * ("Adı Değiştir") değişir; parola da ayrı. Bir sorunda kullanıcı pasife alınır ve yenisi eklenir.
 *
 * Bir bölgeden yalnızca **bir aktif sorumlu** olur (`kullanici_bolge_ux`); ikincisini eklemek 409 döner.
 * Yeni **bölge** açmak hâlâ migration ister — bu ekran yalnızca var olan bölgelere kullanıcı ekler.
 */
export function Kullanicilar() {
  const [kullanicilar, setKullanicilar] = useState<YonetimKullanicisi[] | null>(null)
  const [bolgeler, setBolgeler] = useState<Bolge[]>([])
  const [hata, setHata] = useState<string | null>(null)
  const [basari, setBasari] = useState<string | null>(null)
  /** Hangi satırda hangi alan düzenleniyor — aynı anda tek satır açık kalır. */
  const [duzenlenen, setDuzenlenen] = useState<{ id: number; alan: 'parola' | 'ad' | 'bilgi' } | null>(null)
  const [yeniSifre, setYeniSifre] = useState('')
  const [yeniAd, setYeniAd] = useState('')
  /** Ad soyad / rol / bölge düzenleme formu (açık satır için). */
  const [bilgi, setBilgi] = useState<{ adSoyad: string; rol: Rol; bolgeKodu: string }>({
    adSoyad: '',
    rol: 'BOLGE_SORUMLUSU',
    bolgeKodu: '',
  })
  /** Yeni kullanıcı formu. */
  const [ekleAcik, setEkleAcik] = useState(false)
  const bosEkle = { kullaniciAdi: '', adSoyad: '', rol: 'BOLGE_SORUMLUSU' as Rol, bolgeKodu: '', yeniSifre: '' }
  const [ekle, setEkle] = useState(bosEkle)
  const [calisiyor, setCalisiyor] = useState(false)

  useEffect(() => {
    yenile()
    api
      .bolgeler()
      .then(setBolgeler)
      .catch(() => setBolgeler([]))
  }, [])

  function yenile() {
    api
      .kullanicilar()
      .then(setKullanicilar)
      .catch((e) => setHata(e instanceof ApiHatasi ? e.message : 'Kullanıcılar yüklenemedi.'))
  }

  async function calistir(islem: () => Promise<void>, basariliMesaj: string) {
    setHata(null)
    setBasari(null)
    setCalisiyor(true)
    try {
      await islem()
      setBasari(basariliMesaj)
      yenile()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'İşlem tamamlanamadı.')
    } finally {
      setCalisiyor(false)
    }
  }

  function sifirla(kullanici: YonetimKullanicisi) {
    calistir(
      async () => {
        await api.parolaSifirla(kullanici.id, yeniSifre)
        setDuzenlenen(null)
        setYeniSifre('')
      },
      `${kullanici.kullaniciAdi} kullanıcısının parolası değiştirildi. Yeni parolayı kendisine iletin.`,
    )
  }

  function adiDegistir(kullanici: YonetimKullanicisi) {
    const hedef = yeniAd.trim()
    calistir(
      async () => {
        await api.kullaniciAdiGuncelle(kullanici.id, hedef)
        setDuzenlenen(null)
        setYeniAd('')
      },
      `Kullanıcı adı "${kullanici.kullaniciAdi}" → "${hedef}" olarak değişti. Bundan sonra yeni adla giriş yapacak.`,
    )
  }

  function bilgiKaydet(kullanici: YonetimKullanicisi) {
    calistir(
      async () => {
        await api.kullaniciGuncelle(kullanici.id, {
          adSoyad: bilgi.adSoyad.trim(),
          rol: bilgi.rol,
          bolgeKodu: bilgi.rol === 'YONETICI' ? null : bilgi.bolgeKodu,
        })
        setDuzenlenen(null)
      },
      `${kullanici.kullaniciAdi} kullanıcısının bilgileri güncellendi.`,
    )
  }

  function aktiflikDegistir(kullanici: YonetimKullanicisi) {
    calistir(
      () => api.aktiflikDegistir(kullanici.id, !kullanici.aktif).then(() => undefined),
      `${kullanici.kullaniciAdi} ${kullanici.aktif ? 'pasife alındı' : 'aktifleştirildi'}.`,
    )
  }

  function olustur() {
    calistir(
      async () => {
        await api.kullaniciOlustur({
          kullaniciAdi: ekle.kullaniciAdi.trim(),
          adSoyad: ekle.adSoyad.trim(),
          rol: ekle.rol,
          bolgeKodu: ekle.rol === 'YONETICI' ? null : ekle.bolgeKodu,
          yeniSifre: ekle.yeniSifre,
        })
        setEkle(bosEkle)
        setEkleAcik(false)
      },
      `"${ekle.kullaniciAdi.trim()}" kullanıcısı oluşturuldu. Başlangıç parolasını kendisine iletin.`,
    )
  }

  function ac(kullanici: YonetimKullanicisi, alan: 'parola' | 'ad' | 'bilgi') {
    const aciktiMi = duzenlenen?.id === kullanici.id && duzenlenen.alan === alan
    setDuzenlenen(aciktiMi ? null : { id: kullanici.id, alan })
    setYeniSifre('')
    setYeniAd(kullanici.kullaniciAdi)
    setBilgi({
      adSoyad: kullanici.adSoyad,
      rol: kullanici.rol,
      bolgeKodu: kullanici.bolgeKodu ?? (bolgeler[0]?.kod ?? ''),
    })
  }

  const ekleGecerli =
    ekle.kullaniciAdi.trim().length >= 3 &&
    !/\s/.test(ekle.kullaniciAdi.trim()) &&
    ekle.adSoyad.trim() !== '' &&
    ekle.yeniSifre.trim().length >= 8 &&
    (ekle.rol === 'YONETICI' || ekle.bolgeKodu !== '')

  // Hata kontrolü yükleniyor kontrolünün ÜSTÜNDE: yetkisiz kullanıcıda liste hiç gelmiyor.
  if (hata && !kullanicilar) return <YuklemeHatasi mesaj={hata} yenidenDene={yenile} />
  if (!kullanicilar) return <Yukleniyor />

  return (
    <>
      <SayfaBasligi baslik="Kullanıcılar">
        <button onClick={() => setEkleAcik((o) => !o)}>{ekleAcik ? 'Formu kapat' : 'Yeni Kullanıcı'}</button>
      </SayfaBasligi>
      <Serit tur="bilgi">
        Yeni kullanıcı ekleyebilir; ad soyad, rol ve bölgeyi düzenleyebilirsiniz. Kullanıcı adı ve
        parola ayrı düğmelerden değişir. Bir bölgede yalnızca bir aktif sorumlu olur; gerekirse
        kullanıcıyı pasife alıp yenisini ekleyin.
      </Serit>
      {hata && <Serit tur="hata">{hata}</Serit>}
      {basari && <Serit tur="basari">{basari}</Serit>}

      {ekleAcik && (
        <div className="kart">
          <h2>Yeni kullanıcı</h2>
          <div className="alan">
            <label htmlFor="ekle-ad">Kullanıcı adı (en az 3 karakter, boşluksuz)</label>
            <input
              id="ekle-ad"
              type="text"
              autoComplete="off"
              value={ekle.kullaniciAdi}
              onChange={(e) => setEkle({ ...ekle, kullaniciAdi: e.target.value })}
            />
          </div>
          <div className="alan">
            <label htmlFor="ekle-adsoyad">Ad Soyad</label>
            <input
              id="ekle-adsoyad"
              type="text"
              value={ekle.adSoyad}
              onChange={(e) => setEkle({ ...ekle, adSoyad: e.target.value })}
            />
          </div>
          <RolBolge
            rol={ekle.rol}
            bolgeKodu={ekle.bolgeKodu}
            bolgeler={bolgeler}
            onRol={(rol) => setEkle({ ...ekle, rol })}
            onBolge={(bolgeKodu) => setEkle({ ...ekle, bolgeKodu })}
            idOnek="ekle"
          />
          <div className="alan">
            <label htmlFor="ekle-sifre">Başlangıç parolası (en az 8 karakter)</label>
            {/* Parola gizlenmiyor: yönetici kullanıcıya iletmek için ne yazdığını görmeli. */}
            <input
              id="ekle-sifre"
              type="text"
              autoComplete="off"
              value={ekle.yeniSifre}
              onChange={(e) => setEkle({ ...ekle, yeniSifre: e.target.value })}
            />
          </div>
          <div className="dugme-grubu">
            <button disabled={!ekleGecerli || calisiyor} onClick={olustur}>
              Kullanıcı Oluştur
            </button>
            <button className="ikincil" disabled={calisiyor} onClick={() => setEkleAcik(false)}>
              Vazgeç
            </button>
          </div>
        </div>
      )}

      <div className="kart tablo-sarici">
        <table>
          <thead>
            <tr>
              <th>Kullanıcı adı</th>
              <th>Ad Soyad</th>
              <th>Rol</th>
              <th>Bölge</th>
              <th>Durum</th>
              <th>Oluşturma</th>
              <th>İşlem</th>
            </tr>
          </thead>
          <tbody>
            {kullanicilar.map((kullanici) => (
              <tr key={kullanici.id} className={kullanici.aktif ? undefined : 'soluk'}>
                <td>
                  <strong>{kullanici.kullaniciAdi}</strong>
                </td>
                <td>{kullanici.adSoyad}</td>
                <td>{kullanici.rol === 'YONETICI' ? 'Yönetici' : 'Bölge sorumlusu'}</td>
                <td>{kullanici.bolgeAdi ?? <span className="soluk">tüm bölgeler</span>}</td>
                <td>
                  <span className={`rozet ${kullanici.aktif ? 'tamam' : 'iptal'}`}>
                    {kullanici.aktif ? 'Aktif' : 'Pasif'}
                  </span>
                </td>
                <td className="soluk">{tarih(kullanici.olusturma)}</td>
                <td>
                  <div className="dugme-grubu">
                    <button className="ikincil" disabled={calisiyor} onClick={() => ac(kullanici, 'bilgi')}>
                      Düzenle
                    </button>
                    <button className="ikincil" disabled={calisiyor} onClick={() => ac(kullanici, 'ad')}>
                      Adı Değiştir
                    </button>
                    <button className="ikincil" disabled={calisiyor} onClick={() => ac(kullanici, 'parola')}>
                      Parola Sıfırla
                    </button>
                    <button className="ikincil" disabled={calisiyor} onClick={() => aktiflikDegistir(kullanici)}>
                      {kullanici.aktif ? 'Pasife Al' : 'Aktifleştir'}
                    </button>
                  </div>

                  {duzenlenen?.id === kullanici.id && duzenlenen.alan === 'bilgi' && (
                    <div className="bosluk-ust">
                      <div className="alan">
                        <label htmlFor={`adsoyad-${kullanici.id}`}>Ad Soyad</label>
                        <input
                          id={`adsoyad-${kullanici.id}`}
                          type="text"
                          value={bilgi.adSoyad}
                          autoFocus
                          onChange={(e) => setBilgi({ ...bilgi, adSoyad: e.target.value })}
                        />
                      </div>
                      <RolBolge
                        rol={bilgi.rol}
                        bolgeKodu={bilgi.bolgeKodu}
                        bolgeler={bolgeler}
                        onRol={(rol) => setBilgi({ ...bilgi, rol })}
                        onBolge={(bolgeKodu) => setBilgi({ ...bilgi, bolgeKodu })}
                        idOnek={`bilgi-${kullanici.id}`}
                      />
                      <div className="dugme-grubu">
                        <button
                          disabled={
                            bilgi.adSoyad.trim() === '' ||
                            (bilgi.rol === 'BOLGE_SORUMLUSU' && bilgi.bolgeKodu === '') ||
                            calisiyor
                          }
                          onClick={() => bilgiKaydet(kullanici)}
                        >
                          Kaydet
                        </button>
                        <button className="ikincil" disabled={calisiyor} onClick={() => setDuzenlenen(null)}>
                          Vazgeç
                        </button>
                      </div>
                    </div>
                  )}

                  {duzenlenen?.id === kullanici.id && duzenlenen.alan === 'ad' && (
                    <div className="bosluk-ust">
                      <div className="alan">
                        <label htmlFor={`ad-${kullanici.id}`}>Yeni kullanıcı adı (en az 3 karakter, boşluksuz)</label>
                        <input
                          id={`ad-${kullanici.id}`}
                          type="text"
                          autoComplete="off"
                          value={yeniAd}
                          autoFocus
                          onChange={(e) => setYeniAd(e.target.value)}
                        />
                      </div>
                      <div className="dugme-grubu">
                        <button
                          disabled={yeniAd.trim().length < 3 || yeniAd.trim() === kullanici.kullaniciAdi || calisiyor}
                          onClick={() => adiDegistir(kullanici)}
                        >
                          Kaydet
                        </button>
                        <button className="ikincil" disabled={calisiyor} onClick={() => setDuzenlenen(null)}>
                          Vazgeç
                        </button>
                      </div>
                    </div>
                  )}

                  {duzenlenen?.id === kullanici.id && duzenlenen.alan === 'parola' && (
                    <div className="bosluk-ust">
                      <div className="alan">
                        <label htmlFor={`sifre-${kullanici.id}`}>Yeni parola (en az 8 karakter)</label>
                        <input
                          id={`sifre-${kullanici.id}`}
                          type="text"
                          autoComplete="off"
                          value={yeniSifre}
                          autoFocus
                          onChange={(e) => setYeniSifre(e.target.value)}
                        />
                      </div>
                      {/* Parola gizlenmiyor: yönetici kullanıcıya iletmek için ne yazdığını görmeli. */}
                      <div className="dugme-grubu">
                        <button disabled={yeniSifre.trim().length < 8 || calisiyor} onClick={() => sifirla(kullanici)}>
                          Kaydet
                        </button>
                        <button className="ikincil" disabled={calisiyor} onClick={() => setDuzenlenen(null)}>
                          Vazgeç
                        </button>
                      </div>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}

/** Rol seçimi + (bölge sorumlusuysa) bölge seçimi — hem ekleme hem düzenleme formunda. */
function RolBolge({
  rol,
  bolgeKodu,
  bolgeler,
  onRol,
  onBolge,
  idOnek,
}: {
  rol: Rol
  bolgeKodu: string
  bolgeler: Bolge[]
  onRol: (rol: Rol) => void
  onBolge: (bolgeKodu: string) => void
  idOnek: string
}) {
  return (
    <>
      <div className="alan">
        <label htmlFor={`${idOnek}-rol`}>Rol</label>
        <select id={`${idOnek}-rol`} value={rol} onChange={(e) => onRol(e.target.value as Rol)}>
          <option value="BOLGE_SORUMLUSU">Bölge sorumlusu</option>
          <option value="YONETICI">Yönetici</option>
        </select>
      </div>
      {rol === 'BOLGE_SORUMLUSU' && (
        <div className="alan">
          <label htmlFor={`${idOnek}-bolge`}>Bölge</label>
          <select id={`${idOnek}-bolge`} value={bolgeKodu} onChange={(e) => onBolge(e.target.value)}>
            <option value="">Seçin…</option>
            {bolgeler.map((b) => (
              <option key={b.kod} value={b.kod}>
                {b.ad}
              </option>
            ))}
          </select>
        </div>
      )}
    </>
  )
}
