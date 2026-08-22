-- V5 — İş ve belge iptali: sıra kuralının tek istisnası (NEWPHASES.md D5).
--
-- Neden gerekiyor: D3(b) "bölgede o türde nerede kalındıysa oradan devam" diyor. Yanlış açılmış ya da
-- düşmüş bir iş bu kuyruğu süresiz kilitliyordu ve geri alma yolu yoktu. İptal, kilidin tek çıkışıdır.
--
-- İptal SİLME DEĞİL: kayıt yerinde kalır, yalnızca durumu değişir. Böylece "bu iş neden yok" sorusunun
-- cevabı veritabanında durur. İptal edenin kimliği, tarihi ve gerekçesi zorunludur — disiplin kuralının
-- istisnası iz bırakmadan kullanılamaz.

-- ---------------------------------------------------------------------------
-- İş iptali — sırayı açan işlem.
-- İptal edilmiş iş, bölge sırası hesabında ATLANIR: arkasındaki işler beklemekten kurtulur.
-- Tamamlanmış belgeleri olduğu gibi kalır (ön fizibilite bitmiş, proje düşmüş — meşru bir son).
-- ---------------------------------------------------------------------------
alter table is_kaydi add column durum text not null default 'AKTIF' check (durum in ('AKTIF', 'IPTAL'));
alter table is_kaydi add column iptal_gerekce text;
alter table is_kaydi add column iptal_eden_id bigint references kullanici (id);
alter table is_kaydi add column iptal_tarihi timestamptz;

-- İptal izleri ya tamamen boş (aktif iş) ya da tamamen dolu olmak zorunda: gerekçesiz iptal edilmiş
-- bir iş, kilidin neden açıldığını sonradan kimsenin bilememesi anlamına gelir.
alter table is_kaydi add constraint is_kaydi_iptal_izi_tutarli check (
    (durum = 'AKTIF' and iptal_gerekce is null and iptal_eden_id is null and iptal_tarihi is null) or
    (durum = 'IPTAL' and iptal_gerekce is not null and iptal_eden_id is not null and iptal_tarihi is not null)
);

-- Bölge sırası sorgusu artık yalnızca aktif işleri tarıyor.
create index is_kaydi_bolge_durum_ix on is_kaydi (bolge_id, durum);

-- ---------------------------------------------------------------------------
-- Belge iptali — yanlış açılmış TASLAK'ı çöpe atar, sırayı AÇMAZ.
-- `belge.durum` içindeki 'IPTAL' değeri V2'den beri tanımlıydı; eksik olan iptal izleriydi.
-- Tamamlanmış belge iptal edilemez (D2): tamamlanmış belge hiçbir yoldan değişmez.
-- ---------------------------------------------------------------------------
alter table belge add column iptal_gerekce text;
alter table belge add column iptal_eden_id bigint references kullanici (id);
alter table belge add column iptal_tarihi timestamptz;

alter table belge add constraint belge_iptal_izi_tutarli check (
    (durum <> 'IPTAL' and iptal_gerekce is null and iptal_eden_id is null and iptal_tarihi is null) or
    (durum = 'IPTAL' and iptal_gerekce is not null and iptal_eden_id is not null and iptal_tarihi is not null)
);

-- V2'deki `unique (is_id, tur)` iptal edilmiş belgeyi de sayıyordu: bir taslak iptal edildikten sonra
-- aynı iş aynı türde YENİ belge açamıyordu, yani iptal işe yaramıyordu. Kısıt iptalleri dışlıyor.
--
-- Numara YENİDEN KULLANILMAZ: iptal edilen 003, iptal olarak seride kalır; yeni belge 004 alır.
-- Bir kez üretilmiş (ekrana çıkmış, yazdırılmış olabilecek) numaranın başka bir belgeye verilmesi,
-- iptal iznini numara karışıklığına çevirirdi.
alter table belge drop constraint belge_is_id_tur_key;

create unique index belge_is_tur_ux on belge (is_id, tur) where durum <> 'IPTAL';
