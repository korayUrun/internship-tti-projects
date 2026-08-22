-- V11 — Faz O: işin durum makinesi dörde çıkıyor, SP No sunucu tarafından üretiliyor.
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.

-- ===========================================================================
-- 1) İş durumu: AKTIF|IPTAL → (null)|ACIK|ISLENIYOR|KAPANDI|IPTAL
--
--   (null)  Fizibilite bekleniyor — iş açıldı, henüz belgesi yok
--   ACIK    Fizibilite belgesi açıldı; fizibilite düzenlenebilir
--   ISLENIYOR  Yönetici işleme aldı; fizibilite KİLİTLİ, keşif açılabilir
--   KAPANDI Hakediş gönderildi (otomatik)
--   IPTAL   Yönetici iptal etti (ACIK ya da ISLENIYOR iken)
--
-- Beşinci bir 'YENI' değeri yerine `null` seçildi: kullanıcı durum kümesini dört değer olarak
-- tanımladı ve "henüz durumu yok" gerçekten bir durum değil, durumun yokluğu.
-- ===========================================================================
alter table is_kaydi drop constraint is_kaydi_iptal_izi_tutarli;
alter table is_kaydi drop constraint is_kaydi_durum_check;
alter table is_kaydi alter column durum drop default;
alter table is_kaydi alter column durum drop not null;

-- Var olan AKTIF işler belge durumlarına bakılarak yeni kümeye dağıtılır.
-- Sıra önemli: en dar koşuldan (KAPANDI) en genişe (ACIK) doğru gitmiyoruz, bu yüzden her update
-- yalnızca hâlâ 'AKTIF' olanlara dokunuyor ve son update kalanları süpürüyor.
update is_kaydi i set durum = null
 where i.durum = 'AKTIF'
   and not exists (select 1 from belge b where b.is_id = i.id and b.tur = 'FIZIBILITE' and b.durum <> 'IPTAL');

update is_kaydi i set durum = 'KAPANDI'
 where i.durum = 'AKTIF'
   and exists (select 1 from belge b where b.is_id = i.id and b.tur = 'HAKEDIS' and b.durum = 'TAMAMLANDI');

update is_kaydi i set durum = 'ISLENIYOR'
 where i.durum = 'AKTIF'
   and exists (select 1 from belge b where b.is_id = i.id and b.tur = 'KESIF' and b.durum <> 'IPTAL');

update is_kaydi set durum = 'ACIK' where durum = 'AKTIF';

alter table is_kaydi add constraint is_kaydi_durum_check
    check (durum is null or durum in ('ACIK', 'ISLENIYOR', 'KAPANDI', 'IPTAL'));

-- `is distinct from` kullanılıyor: `durum` artık nullable ve `null <> 'IPTAL'` NULL üretip kısıtı
-- sessizce "bilinmiyor" hâline düşürürdü (NULL kısıtı ihlal saymaz, ama niyet de bu değil).
alter table is_kaydi add constraint is_kaydi_iptal_izi_tutarli check (
    (durum is distinct from 'IPTAL' and iptal_gerekce is null and iptal_eden_id is null and iptal_tarihi is null) or
    (durum = 'IPTAL' and iptal_gerekce is not null and iptal_eden_id is not null and iptal_tarihi is not null)
);

-- ===========================================================================
-- 2) SP No artık üretiliyor: 2026-BURSA-001
--
-- ~~D1: SP No serbest metin, kısıtsız~~ — kullanıcı kararı (3 Ağustos 2026) ile geçersiz. Elle
-- yazılan numara hem yavaştı hem tutarsızdı; artık bölge + yıl başına artan bir sayaçtan geliyor
-- ve tekil.
-- ===========================================================================
create table is_sayac (
    bolge_id smallint not null references bolge (id),
    yil      smallint not null,
    son_no   integer  not null check (son_no > 0),
    primary key (bolge_id, yil)
);

-- Elle girilmiş eski numaralar kaybolmasın: ucuz sigorta, geliştirme verisinde bile.
alter table is_kaydi add column eski_sp_no text;
update is_kaydi set eski_sp_no = sp_no;

with numaralar as (
    select i.id,
           b.dosya_kodu,
           i.yil,
           row_number() over (partition by i.bolge_id, i.yil order by i.id) as sira
      from is_kaydi i join bolge b on b.id = i.bolge_id
)
update is_kaydi i
   set sp_no = n.yil || '-' || n.dosya_kodu || '-' || lpad(n.sira::text, 3, '0')
  from numaralar n
 where n.id = i.id;

insert into is_sayac (bolge_id, yil, son_no)
select bolge_id, yil, count(*) from is_kaydi group by bolge_id, yil;

-- Artık üretildiği için tekil olabilir; D1'in "aynı SP ile ikinci iş açılabilmeli" gerekçesi düştü.
create unique index is_kaydi_sp_no_ux on is_kaydi (sp_no);

-- ===========================================================================
-- 3) Kable bölge kısıtı kalkıyor (kullanıcı kararı): her kullanıcı her kabloyu seçebilir.
--
-- `kable.bolge_id` sütunu KALIYOR — envanterin kendi bilgisi ve `kableler` yanıtında gösteriliyor.
-- Değişen yalnızca sorgudaki süzgeç. İşin bölgesi artık kableden değil, **işi açan kullanıcıdan**
-- geliyor (IsServisi).
-- ===========================================================================
-- (şema değişikliği gerekmiyor; not olarak bırakıldı)
