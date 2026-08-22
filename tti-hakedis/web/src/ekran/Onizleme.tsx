import { useState } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit, TUR_ADI, tarih } from '../bilesen'
import type { Belge, Is, Varyant } from '../tipler'

/**
 * Önizleme — belgenin PDF'te görünecek hâli.
 *
 * PDF gibi **yalnızca miktarı girilmiş satırları** listeler; sunucudan gelen `satirlar` zaten bu
 * kümedir. Excel ise sözleşmenin tüm satırlarını taşır — iki çıktı bilinçli olarak farklı sorulara
 * cevap veriyor (CLAUDE.md §11), bu yüzden ekranda Excel'in aynası değil PDF'in aynası gösterilir.
 *
 * İndirme **belge türüne göre** değişir: fizibilite ve keşifte iki varyant vardır — `FIYATLI` iç
 * kullanım, `TEKLIF` aracı firmaya gidecek fiyatsız kopya (E6). **Hakedişte tek çıktı** kalır
 * (kullanıcı kararı, 31 Temmuz 2026): fiyat pazarlığı çoktan bitmiştir, fiyatsız hakediş kopyasının
 * gideceği bir yer yoktur ve dört düğme yalnızca yanlış dosyayı indirme ihtimali üretiyordu.
 * Sunucu ucu değişmedi — `?varyant=TEKLIF` hâlâ çalışıyor, yalnızca arayüzde sunulmuyor.
 */
export function Onizleme({ belge, is }: { belge: Belge; is: Is }) {
  const [hata, setHata] = useState<string | null>(null)
  const [inen, setInen] = useState<string | null>(null)

  async function indir(anahtar: string, calistir: () => Promise<void>) {
    setHata(null)
    setInen(anahtar)
    try {
      await calistir()
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya indirilemedi.')
    } finally {
      setInen(null)
    }
  }

  function IndirmeDugmesi({ etiket, anahtar, calistir }: { etiket: string; anahtar: string; calistir: () => Promise<void> }) {
    return (
      <button className="ikincil" disabled={inen !== null} onClick={() => indir(anahtar, calistir)}>
        {inen === anahtar ? 'İndiriliyor…' : etiket}
      </button>
    )
  }

  const varyantlar: { varyant: Varyant; ad: string }[] =
    belge.tur === 'HAKEDIS'
      ? [{ varyant: 'FIYATLI', ad: '' }]
      : [
          { varyant: 'FIYATLI', ad: ' — Fiyatlı' },
          { varyant: 'TEKLIF', ad: ' — Teklif (fiyatsız)' },
        ]

  return (
    <div className="kart">
      <h2>Önizleme</h2>
      {hata && <Serit tur="hata">{hata}</Serit>}

      <div className="tablo-sarici">
        <table>
          <tbody>
            <Bilgi etiket="Belge No" deger={belge.belgeNo} />
            <Bilgi etiket="Belge Türü" deger={TUR_ADI[belge.tur]} />
            <Bilgi etiket="Durum" deger={belge.durumMetni} />
            <Bilgi etiket="Bölge" deger={belge.bolgeKodu} />
            <Bilgi etiket="Kapsam" deger={is.kapsamKodu} />
            <Bilgi etiket="Sözleşme" deger={belge.sozlesmeAdi} />
            <Bilgi etiket="SP No" deger={is.spNo} />
            <Bilgi etiket="Route" deger={is.route} />
            <Bilgi etiket="Ek Aralığı" deger={is.ekAraligi} />
            <Bilgi etiket="KableID" deger={is.kableNosid} />
            <Bilgi etiket="Uç A" deger={is.adresA} />
            <Bilgi etiket="Uç B" deger={is.adresB ?? '—'} />
            <Bilgi etiket="Oluşturma" deger={tarih(belge.olusturma)} />
          </tbody>
        </table>
      </div>

      <h3 className="bosluk-ust">Girilmiş Kalemler</h3>
      {belge.satirlar.length === 0 ? (
        <div className="bos-not">Henüz miktar girilmemiş.</div>
      ) : (
        <div className="tablo-sarici">
          <table>
            <thead>
              <tr>
                <th>Poz No</th>
                <th>İş Kalemi</th>
                <th>Birim</th>
                <th className="sayi">Br Fiyat</th>
                <th className="sayi">Miktar</th>
                <th className="sayi">Tutar</th>
              </tr>
            </thead>
            <tbody>
              {belge.satirlar.map((satir) => (
                <tr key={satir.pozId}>
                  <td>{satir.pozNo}</td>
                  <td>{satir.ad}</td>
                  <td>{satir.birim}</td>
                  {/* Gösterim metinlerinin hepsi sunucuda üretilir; web biçimlendirme yapmaz. */}
                  <td className="sayi">{satir.birimFiyatGosterim}</td>
                  <td className="sayi">{satir.miktarGosterim}</td>
                  <td className="sayi">{satir.tutarGosterim}</td>
                </tr>
              ))}
              <tr>
                <td colSpan={5} className="sayi">
                  <strong>Toplam</strong>
                </td>
                <td className="sayi">
                  <strong>{belge.toplamGosterim}</strong>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      <h3 className="bosluk-ust">İndir</h3>
      <div className="dugme-grubu">
        {varyantlar.map(({ varyant, ad }) => (
          <IndirmeDugmesi
            key={`pdf-${varyant}`}
            anahtar={`pdf-${varyant}`}
            etiket={`PDF${ad}`}
            calistir={() => api.pdfIndir(belge.id, varyant)}
          />
        ))}
        {varyantlar.map(({ varyant, ad }) => (
          <IndirmeDugmesi
            key={`excel-${varyant}`}
            anahtar={`excel-${varyant}`}
            etiket={`Excel${ad}`}
            calistir={() => api.excelIndir(belge.id, varyant)}
          />
        ))}
      </div>
    </div>
  )
}

/** [deger] `null` olabilir: kablosuz "Yeni Tesis" projesi (Faz W). */
function Bilgi({ etiket, deger }: { etiket: string; deger: string | null }) {
  return (
    <tr>
      <td className="soluk" style={{ width: 200, fontWeight: 600 }}>
        {etiket}
      </td>
      <td>{deger?.trim() || '—'}</td>
    </tr>
  )
}
