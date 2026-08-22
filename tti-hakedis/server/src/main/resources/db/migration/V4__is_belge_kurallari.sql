-- V4 — Faz D: SP No arama indeksi, işin yılı, bölge içi tür sırası kısıtı.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.

-- ---------------------------------------------------------------------------
-- SP No: kısıt YOK, arama indeksi VAR (NEWPHASES.md D1).
--
-- Bir SP genelde bir *projeyi*, iş ise bir *kabloyu* tanımlıyor; aynı SP altında birden fazla kablo
-- çalışması olağan. UNIQUE konulsaydı bölge sorumlusu ikinci kabloyu sisteme hiç giremez ve bunu
-- ancak sahada fark ederdi. Sonradan kısıt eklemek kolay, kaldırmak veri temizliği ister.
--
-- İndeksin iki işi var: `GET /api/isler?spNo=` süzgeci ve iş oluşturmada "bu SP ile kaç iş var"
-- uyarı sayımı (AYNI_SP_VAR).
-- ---------------------------------------------------------------------------
create index is_kaydi_bolge_sp_ix on is_kaydi (bolge_id, sp_no);

-- ---------------------------------------------------------------------------
-- İşin yılı (NEWPHASES.md D4) — belge numarasının ve sayacın yıl bileşeni.
--
-- Yıl işin AÇILDIĞI tarihten gelir ve o işin üç belgesi de aynı yılı taşır: Aralık'ta açılan bir işin
-- Ocak'ta çekilen hakedişi yine o yılın belgesidir. Bu yüzden yıl her belge üretiminde yeniden
-- hesaplanmaz, işin üzerinde ayrı bir sütunda durur.
--
-- `extract(year from timestamptz)` oturumun saat dilimine göre değişir (yılbaşı gecesi sonucu kaydırır);
-- sütunu açıkça tutmak bu belirsizliği tamamen kaldırır. Mevcut satırlar için Europe/Istanbul'a göre
-- doldurulur — sunucu ve veritabanı konteynerleri bu dilimde çalışıyor.
-- ---------------------------------------------------------------------------
alter table is_kaydi add column yil smallint;

update is_kaydi set yil = extract(year from (olusturma at time zone 'Europe/Istanbul'))::smallint
 where yil is null;

alter table is_kaydi alter column yil set not null;
alter table is_kaydi add constraint is_kaydi_yil_makul check (yil between 2020 and 2100);

-- ---------------------------------------------------------------------------
-- Aşama kuralı (b): bölge içinde tür sırası (NEWPHASES.md D3).
--
-- N numaralı işin T türündeki belgesi, ancak N'den önce açılmış tüm işlerin T türü tamamlanmışsa
-- açılabilir. Bunun doğal sonucu: bir bölgede bir türden en fazla BİR açık (TASLAK) belge olabilir.
-- V2'deki index yalnızca KESIF'i koruyordu; kural üç türe genişlediği için index de genişletiliyor.
--
-- Kısmi unique index, kuralı yarış durumunda bile veritabanı seviyesinde garanti eder; servis
-- katmanındaki DocumentStageRule kontrolü anlaşılır hata mesajı üretmek içindir.
-- IPTAL ve TAMAMLANDI belgeler index'e girmez, dolayısıyla sınırlamaya takılmaz.
-- ---------------------------------------------------------------------------
drop index belge_acik_kesif_ux;

create unique index belge_acik_tur_ux on belge (bolge_id, tur) where durum = 'TASLAK';

-- Bekleyen önceki iş sorgusu: bölgedeki işleri sırayla tarar ve her biri için o türde TAMAMLANDI
-- belge arar. `belge (is_id, tur)` unique kısıtı iş tarafını karşılıyor; durum süzgeci için ek indeks
-- gerekmiyor (bölge başına iş sayısı yüzler mertebesinde).
