import { useRef, useState } from 'react'
import { api, ApiHatasi } from '../api'
import { Serit } from '../bilesen'
import type { Belge } from '../tipler'

/**
 * Miktarların Excel ile girilmesi: **şablon indir → doldur → yükle** (kullanıcı kararı, 3 Ağustos
 * 2026). Tabloya elle giriş alternatif olarak duruyor; ikisi aynı sonucu veriyor.
 *
 * Şablon **girilmiş miktarlarla dolu** iner. Boş inseydi, ekranda birkaç satır girmiş bir kullanıcı
 * şablonu yükleyerek girdiklerini silerdi — yükleme tam değiştirmedir.
 *
 * Sunucu tek bir sorunlu satırda **hiçbir şey yazmaz** ve satır satır neyin bozuk olduğunu döner;
 * hata metni olduğu gibi gösterilir (ApiHatasi mesajları Türkçe ve eyleme dönük).
 */
export function MiktarExcel({ belgeId, yuklendi }: { belgeId: number; yuklendi: (yeni: Belge) => void }) {
  const secici = useRef<HTMLInputElement>(null)
  const [hata, setHata] = useState<string | null>(null)
  const [calisiyor, setCalisiyor] = useState<'indirme' | 'yukleme' | null>(null)

  async function indir() {
    setHata(null)
    setCalisiyor('indirme')
    try {
      await api.miktarSablonuIndir(belgeId)
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Şablon indirilemedi.')
    } finally {
      setCalisiyor(null)
    }
  }

  async function yukle(dosya: File | undefined) {
    if (!dosya) return
    setHata(null)
    setCalisiyor('yukleme')
    try {
      yuklendi(await api.miktarYukle(belgeId, dosya))
    } catch (e) {
      setHata(e instanceof ApiHatasi ? e.message : 'Dosya yüklenemedi.')
    } finally {
      setCalisiyor(null)
      // Aynı dosya ikinci kez seçilebilsin: `change` olayı aynı değerde tetiklenmiyor.
      if (secici.current) secici.current.value = ''
    }
  }

  return (
    <div className="miktar-excel">
      <div className="dugme-grubu">
        <button className="ikincil" disabled={calisiyor !== null} onClick={() => void indir()}>
          {calisiyor === 'indirme' ? 'İndiriliyor…' : 'Miktar Şablonu İndir'}
        </button>
        <button className="ikincil" disabled={calisiyor !== null} onClick={() => secici.current?.click()}>
          {calisiyor === 'yukleme' ? 'Yükleniyor…' : 'Doldurulmuş Şablonu Yükle'}
        </button>
        <input
          ref={secici}
          type="file"
          accept=".xlsx"
          hidden
          onChange={(e) => void yukle(e.target.files?.[0])}
        />
      </div>
      <div className="soluk kucuk">
        Şablon mevcut miktarlarla dolu iner. Yükleme <strong>tam değiştirmedir</strong>: dosyada boş
        bırakılan kalem belgeden silinir.
      </div>
      {/* Hata çok satırlı gelir (hangi poz, ne sorun); `pre-wrap` satırları korur. */}
      {hata && (
        <Serit tur="hata">
          <span style={{ whiteSpace: 'pre-wrap' }}>{hata}</span>
        </Serit>
      )}
    </div>
  )
}
