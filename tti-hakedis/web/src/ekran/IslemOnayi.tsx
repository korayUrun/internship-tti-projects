import type { ReactNode } from 'react'
import { useKatman } from '../bilesen'

/**
 * Geri alınamaz bir işlemin geri bildirimi: istek sürerken dönen halka, bittiğinde tik animasyonu.
 *
 * İki yerde kullanılıyor:
 * - **Belge gönderme** (D2 — geri alınamaz): tek göstergesi durum rozetinin sessizce değişmesiydi;
 *   kullanıcı "gitti mi, kaydedildi mi" diye tereddüt ediyordu.
 * - **Fiyat listesi onayı** (E1 — aynı işe ikinci liste yüklenemez): onaydan sonra ekranda hiçbir şey
 *   olmuyordu, yönetici onayın geçip geçmediğini anlayamıyordu.
 *
 * **Kendiliğinden kapanmaz**: alttaki düğmeye basmaya vakit kalsın diye (2 saniyelik otomatik kapanma
 * o düğmeyi kullanılamaz kılıyordu). Kapatma yolları: X, "Kapat" ve katmanın boş alanına tıklama.
 *
 * [mesaj] ekran sabitidir; [altMesaj] ise **durum metnidir** ve sunucudan geldiği gibi verilir — web
 * hiçbir yerde durum metni yazmaz (NEWPHASES.md E2).
 */
export function IslemOnayi({
  durum,
  mesaj,
  altMesaj,
  kapat,
  eylemler,
}: {
  durum: 'calisiyor' | 'tamam'
  calisiyorMesaji?: string
  mesaj: string
  altMesaj?: string
  kapat: () => void
  /** Alt düğmeler; verilmezse yalnızca "Kapat" görünür. */
  eylemler?: ReactNode
}) {
  // İşlem SÜRERKEN Escape kapatmaz: gönderme geri alınamaz (D2) ve katmanı kapatmak kullanıcıya
  // "vazgeçtim" hissi verirdi — oysa istek yolda ve durdurulamaz.
  useKatman(durum === 'tamam' ? kapat : undefined)

  return (
    <div className="gonderim-katmani" role="status" aria-live="polite" onClick={durum === 'tamam' ? kapat : undefined}>
      {/* Kutuya yapılan tıklama katmana sızmamalı; yoksa düğmeye basmak katmanı da kapatırdı. */}
      <div className="gonderim-kutusu" onClick={(olay) => olay.stopPropagation()}>
        {durum === 'calisiyor' ? (
          <>
            <div className="donen-halka" />
            <div className="mesaj">{mesaj}</div>
          </>
        ) : (
          <>
            <div className="tik-halka">
              <svg viewBox="0 0 52 52" aria-hidden="true">
                <path d="M14 27 L23 36 L38 18" />
              </svg>
            </div>
            <button className="sade kapat-dugmesi" aria-label="Kapat" onClick={kapat}>
              ✕
            </button>
            <div className="mesaj">{mesaj}</div>
            {altMesaj && <div className="alt-mesaj">{altMesaj}</div>}
            <div className="dugme-grubu bosluk-ust" style={{ justifyContent: 'center' }}>
              {eylemler}
              <button className="ikincil" onClick={kapat}>
                Kapat
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
