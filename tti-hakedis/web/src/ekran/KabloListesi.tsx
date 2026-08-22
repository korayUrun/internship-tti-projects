import { useState } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit, useKatman } from '../bilesen'
import type { Bolge, Kable, KableIstegi } from '../tipler'

/** En az bu kadar karakterle arama yapılır — sunucudaki eşikle aynı. */
const EN_AZ_ARAMA = 3

const ARAMA_SINIRI = 200

/** Sürüm satırındaki "Kabloları düzenle" buraya odaklanıyor; iki dosya tek kimliği paylaşıyor. */
export const KABLO_ARAMA_ID = 'kablo-arama' 

/**
 * Kablo listesi ve elle ekleme/düzenleme (Faz V).
 *
 * **Liste toptan çekilmiyor**: envanter 900+ satır ve arama ucu zaten sayfalamanın yerini tutuyor
 * (Faz O kararı). Kullanıcı `mtc` ön dolu kutuya yazdıkça sonuç geliyor.
 *
 * ⚠️ **Elle düzenleme yeni sürüm doğurmaz** (kullanıcı kararı): değişiklik yürürlükteki envantere
 * anında yazılır. Bunun bilinen sonucu, bir sonraki **tam** NOS+ yüklemesinde dosyada olmayan elle
 * eklenmiş kablonun düşmesidir — bilgi şeridi bunu söylüyor ve yükleme onayı düşenleri zaten
 * listeliyor.
 *
 * **Silme yok**: kullanıcı istemedi ve `is_kaydi.kable_id` yabancı anahtarı var.
 */
export function KabloListesi({ bolgeler, degisti }: { bolgeler: Bolge[]; degisti: () => void }) {
  const [arama, setArama] = useState('mtc')
  const [sonuclar, setSonuclar] = useState<Kable[] | null>(null)
  const [hata, setHata] = useState<string | null>(null)
  const [araniyor, setAraniyor] = useState(false)
  /** Düzenleme formu açık mı ve hangi kablo için. Ekleme kaldırıldığı için `'yeni'` durumu yok. */
  const [form, setForm] = useState<Kable | null>(null)

  async function ara() {
    setHata(null)
    setAraniyor(true)
    try {
      // `limit` açıkça: sunucunun varsayılanına güvenmek kırpmayı görünmez kılıyor. 200 satır
      // yönetici ekranı için yeterli; daha dar bir arama zaten doğru çözüm.
      setSonuclar(await api.kableler(arama, ARAMA_SINIRI))
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Kablo araması başarısız.')
    } finally {
      setAraniyor(false)
    }
  }

  function kaydedildi() {
    setForm(null)
    degisti()
    void ara()
  }

  return (
    <div className="kart">
      <h2>Kablolar</h2>
      {/* ~~"elle yeni kablo ekleyin"~~ 18 Ağustos 2026'da kaldırıldı (kullanıcı isteği): envantere
          kablo eklemenin tek yolu tam envanter yüklemesi. Var olan kablonun DÜZELTİLMESİ duruyor —
          kullanıcı yalnızca eklemenin kalkmasını istedi. */}
      <p className="bos-not">
        Envanterde arama yapın ve gerekirse düzeltin. Değişiklik <strong>anında yürürlüğe girer</strong>;
        ayrı bir sürüm oluşmaz. Envantere <strong>yeni kablo eklemek</strong> için tam envanter
        dosyasını yükleyin — bir sonraki tam yüklemede dosyada bulunmayan kablolar listeden düşer ve
        yükleme onayı bunu önceden gösterir.
      </p>

      <div className="satir-form">
        <input
          id={KABLO_ARAMA_ID}
          aria-label="Kablo ara"
          placeholder="mtc…"
          className="genis-alan"
          value={arama}
          onChange={(e) => setArama(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && void ara()}
        />
        <button disabled={arama.trim().length < EN_AZ_ARAMA || araniyor} onClick={() => void ara()}>
          {araniyor ? 'Aranıyor…' : 'Ara'}
        </button>
      </div>

      {hata && <Serit tur="hata">{hata}</Serit>}

      {sonuclar && (
        <div className="tablo-sarici bosluk-ust">
          <table>
            <thead>
              <tr>
                <th style={{ width: 200 }}>Kablo ID</th>
                <th style={{ width: 90 }}>Bölge</th>
                <th>Uç A</th>
                <th>Uç B</th>
                <th className="satir-islem-sutunu" />
              </tr>
            </thead>
            <tbody>
              {sonuclar.length === 0 ? (
                <tr>
                  <td colSpan={5} className="bos-not">
                    Bu aramayla eşleşen kablo yok.
                  </td>
                </tr>
              ) : (
                sonuclar.map((kable) => (
                  <tr key={kable.nosid}>
                    <td className="mono">{kable.nosid}</td>
                    <td>{kable.bolgeKodu}</td>
                    <td>{kable.adresA}</td>
                    <td>{kable.adresB ?? '—'}</td>
                    <td className="satir-islem-sutunu">
                      <button className="ikincil" onClick={() => setForm(kable)}>
                        Düzenle
                      </button>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {form && (
        <KabloFormu
          kable={form}
          bolgeler={bolgeler}
          vazgec={() => setForm(null)}
          kaydedildi={kaydedildi}
        />
      )}
    </div>
  )
}

/**
 * Var olan kablonun düzeltme formu.
 *
 * Konum yolu tek bir metin alanında gösterilir ve olduğu gibi geri gönderilir. Segmentlere
 * bölünmüyor: `AND Plaza > Ground Floor` gibi birleşik bir segmenti geri ayırmak tek anlamlı değil
 * ve yanlış bölünmüş bir yol adresi sessizce bozardı. Yolu düzeltmek isteyen yönetici metni
 * doğrudan yazar; biçim örneği alanın altında duruyor.
 *
 * ~~Yeni kablo ekleme~~ **18 Ağustos 2026'da kaldırıldı** (kullanıcı isteği): uçları segment segment
 * girip sunucuya kurdurma akışı buradaydı. Envantere kablo eklemenin tek yolu artık tam envanter
 * dosyasını yüklemek. Sunucudaki `POST /api/kableler` ucu ve `KableElleYazici` **duruyor** —
 * kaldırılan yalnızca arayüz girişi.
 */
function KabloFormu({
  kable,
  bolgeler,
  vazgec,
  kaydedildi,
}: {
  kable: Kable
  bolgeler: Bolge[]
  vazgec: () => void
  kaydedildi: () => void
}) {
  const [nosid] = useState(kable.nosid)
  const [sahaAdi, setSahaAdi] = useState(kable.sahaAdi)
  const [danismanAd, setDanismanAd] = useState(kable.danismanAd ?? '')
  const [yolA, setYolA] = useState(kable.adresA ?? '')
  const [yolB, setYolB] = useState(kable.adresB ?? '')
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState(false)

  useKatman(calisiyor ? undefined : vazgec)

  const gecerli = sahaAdi !== '' && danismanAd.trim() !== '' && yolA.trim() !== ''

  async function kaydet() {
    setHata(null)
    setCalisiyor(true)
    try {
      const govde: KableIstegi = {
        nosid,
        sahaAdi,
        danismanAd,
        sehirA: kable.sehirA,
        sehirB: kable.sehirB,
        ucA: { konumYolu: yolA },
        ucB: { konumYolu: yolB },
      }
      await api.kableGuncelle(kable.nosid, govde)
      kaydedildi()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Kablo kaydedilemedi.')
    } finally {
      setCalisiyor(false)
    }
  }

  return (
    <div className="katman" role="dialog" aria-modal="true">
      <div className="kart katman-kutu genis">
        <h3>{kable.nosid} — düzenle</h3>

        {hata && <Serit tur="hata">{hata}</Serit>}

        <div className="satir-form">
          <div className="alan">
            <label htmlFor="kabloNosid">Kablo ID (nosid)</label>
            <input
              id="kabloNosid"
              placeholder="mtc04812a.cable*izm-bur"
              value={nosid}
              // Kimlik değiştirilemez: `is_kaydi.kable_nosid` ona bağlı ve düzenleme başka bir
              // kablonun üzerine yazmak anlamına gelirdi.
              disabled
            />
          </div>
          <div className="alan">
            <label htmlFor="kabloSaha">Bölge / Saha</label>
            <select id="kabloSaha" value={sahaAdi} onChange={(e) => setSahaAdi(e.target.value)}>
              {bolgeler.map((bolge) => (
                <option key={bolge.kod} value={bolge.sahaAdi}>
                  {bolge.ad}
                </option>
              ))}
            </select>
          </div>
          <div className="alan">
            <label htmlFor="kabloDanisman">Danışman</label>
            <input
              id="kabloDanisman"
              placeholder="Technical Consultant adı"
              value={danismanAd}
              onChange={(e) => setDanismanAd(e.target.value)}
            />
          </div>
        </div>

        <div className="bosluk-ust">
          <div className="alan">
            <label htmlFor="kabloYolA">Uç A — konum yolu</label>
            <textarea id="kabloYolA" rows={2} value={yolA} onChange={(e) => setYolA(e.target.value)} />
            <p className="bos-not">
              Biçim: <code>Ülke &gt;&gt; Şehir &gt;&gt; Adres &gt;&gt; Bina &gt; Kat &gt;&gt; Oda &gt;&gt; Kafes &gt;&gt; Ayak &gt;&gt; Raf</code>
              {' '}— boş segmentler yazılmaz.
            </p>
          </div>
          <div className="alan">
            <label htmlFor="kabloYolB">Uç B — konum yolu</label>
            <textarea id="kabloYolB" rows={2} value={yolB} onChange={(e) => setYolB(e.target.value)} />
          </div>
        </div>

        <div className="dugme-grubu bosluk-ust">
          <button disabled={!gecerli || calisiyor} onClick={() => void kaydet()}>
            {calisiyor ? 'Kaydediliyor…' : 'Değişikliği Kaydet'}
          </button>
          <button className="ikincil" disabled={calisiyor} onClick={vazgec}>
            Vazgeç
          </button>
        </div>
      </div>
    </div>
  )
}
