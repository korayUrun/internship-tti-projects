-- V17 — Yeknesek durum makinesi (müdür isteği, Ağustos 2026).
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Eski iki katmanlı model (proje ACIK|ISLENIYOR|KAPANDI + belge TASLAK|ONAY_BEKLIYOR|TAMAMLANDI)
-- müdürü karıştırıyordu. Üç aşama da (ön fizibilite, keşif, hakediş) tek yeknesek döngüye indi:
--   ONAY_BEKLIYOR → ISLENIYOR → TAMAMLANDI (+ IPTAL her aşamada).
-- Proje durumu artık güncel (en ileri, iptal olmayan) aşamanın durumunun izdüşümüdür; Projeler ve
-- Belgeler ekranları aynı dört değeri kullanır. `TASLAK` ve `İade` kaldırıldı: açılan aşama doğrudan
-- ONAY_BEKLIYOR doğar; yönetici yalnızca beklet / işleme al / iptal yapar.
--
-- `belge_durum_check`'in Kotlin ikizi `DocumentStatus`'tur; eşleşmeyi `DocumentStageRuleTest`
-- kilitliyor. `is_kaydi_durum_check`'in ikizi `IsDurumu`/`WorkOrderStatus`'tur.

-- ===========================================================================
-- 1) Belge (aşama) durumu: TASLAK|ONAY_BEKLIYOR|TAMAMLANDI|IPTAL
--                        → ONAY_BEKLIYOR|ISLENIYOR|TAMAMLANDI|IPTAL
-- ===========================================================================
alter table belge drop constraint belge_durum_check;

-- Taslak = henüz gönderilmemiş; yeni modelde onay bekleyen düzenlenebilir aşamayla aynı.
update belge set durum = 'ONAY_BEKLIYOR' where durum = 'TASLAK';

-- Eski TAMAMLANDI (onaylanmış) fizibilite/keşif: bir sonrası (iptal olmayan) aşama başlamışsa
-- TAMAMLANDI kalır; başlamamışsa yeni modelde "işleme alınmış, güncel aşama" = ISLENIYOR.
-- Hakediş son aşama olduğu için TAMAMLANDI kalır.
update belge b set durum = 'ISLENIYOR'
 where b.durum = 'TAMAMLANDI'
   and b.tur <> 'HAKEDIS'
   and not exists (
       select 1 from belge n
        where n.is_id = b.is_id and n.durum <> 'IPTAL'
          and array_position(array['FIZIBILITE', 'KESIF', 'HAKEDIS'], n.tur)
            > array_position(array['FIZIBILITE', 'KESIF', 'HAKEDIS'], b.tur)
   );

alter table belge add constraint belge_durum_check
    check (durum in ('ONAY_BEKLIYOR', 'ISLENIYOR', 'TAMAMLANDI', 'IPTAL'));

-- ===========================================================================
-- 2) Proje durumu: (null)|ACIK|ISLENIYOR|KAPANDI|IPTAL
--                → ONAY_BEKLIYOR|ISLENIYOR|TAMAMLANDI|IPTAL  (güncel aşamadan türetilir)
-- ===========================================================================
alter table is_kaydi drop constraint is_kaydi_durum_check;

-- Önce kaba eşleme (aşaması olmayan projeler için de geçerli bir değer kalsın).
update is_kaydi set durum = 'ONAY_BEKLIYOR' where durum is null or durum = 'ACIK';
update is_kaydi set durum = 'TAMAMLANDI'   where durum = 'KAPANDI';

-- Sonra iptal olmayan projelerin durumunu güncel (en ileri, iptal olmayan) aşamadan türet.
update is_kaydi i set durum = d.durum
  from (
        select distinct on (is_id) is_id, durum
          from belge
         where durum <> 'IPTAL'
         order by is_id, array_position(array['FIZIBILITE', 'KESIF', 'HAKEDIS'], tur) desc
       ) d
 where d.is_id = i.id and i.durum <> 'IPTAL';

alter table is_kaydi add constraint is_kaydi_durum_check
    check (durum in ('ONAY_BEKLIYOR', 'ISLENIYOR', 'TAMAMLANDI', 'IPTAL'));

-- Yeni proje (henüz aşama açılmamış talep) doğrudan "Onay Bekliyor" doğar (müdür kararı).
alter table is_kaydi alter column durum set default 'ONAY_BEKLIYOR';
