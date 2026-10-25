-- V1 — Lookup tabloları, kullanıcılar ve kable envanteri (boş).
--
-- Yayımlanmış bir migration ASLA düzenlenmez (Flyway checksum'ı tutmaz ve sunucu açılmaz);
-- değişiklik gerekirse yeni bir V numarası eklenir.

-- ---------------------------------------------------------------------------
-- Bölge — `:core:domain` içindeki Region enum'unun veritabanı karşılığı.
-- `kod` enum adıyla birebir aynıdır (EnumSenkronDogrulayici açılışta doğrular).
-- `dosya_kodu` belge numarasında ve arşiv klasör adında kullanılır.
-- `xlsx_saha_adi` kable envanteri dosyasındaki "Saha" sayfasının karşılığıdır: eşleşme kodda
-- sabitlenmez, böylece yeni bir dosyada yazım değişirse tek satır güncellemesiyle çözülür.
-- ---------------------------------------------------------------------------
create table bolge (
    id            smallint primary key,
    kod           text     not null unique,
    dosya_kodu    text     not null unique,
    ad            text     not null,
    xlsx_saha_adi text     not null unique,
    sira          smallint not null
);

insert into bolge (id, kod, dosya_kodu, ad, xlsx_saha_adi, sira) values
    (1, 'IST_AVR', 'IST-AVR', 'İstanbul Avrupa',  'İstanbul Avp',   1),
    (2, 'IST_AND', 'IST-AND', 'İstanbul Anadolu', 'Istanbul And',   2),
    (3, 'EDIRNE',  'EDIRNE',  'Edirne',           'Edirne',         3),
    (4, 'ANKARA',  'ANKARA',  'Ankara',           'Ankara',         4),
    (5, 'KUTAHYA', 'KUTAHYA', 'Kütahya-Şırnak',   'Kutahya_Sirnak', 5),
    (6, 'IZMIR',   'IZMIR',   'İzmir',            'İzmir',          6),
    (7, 'BURSA',   'BURSA',   'Bursa',            'Bursa',          7);

-- ---------------------------------------------------------------------------
-- Kapsam — Scope enum'unun karşılığı. Etiketler mobil `strings.xml` ile aynı tutuldu.
-- ---------------------------------------------------------------------------
create table kapsam (
    id   smallint primary key,
    kod  text     not null unique,
    ad   text     not null,
    sira smallint not null
);

insert into kapsam (id, kod, ad, sira) values
    (1, 'DEPLASE_IYILESTIRME',   'Deplase / İyileştirme', 1),
    (2, 'ARIZA_SONRASI_ONARIM',  'Arıza Sonrası Onarım',  2),
    (3, 'ZORUNLU_DEPLASE',       'Zorunlu Deplase',       3),
    (4, 'YENI_TESIS',            'Yeni Tesis',            4),
    (5, 'YATAY_SONDAJ',          'Yatay Sondaj',          5);

-- ---------------------------------------------------------------------------
-- Kullanıcı — kayıt olma yoktur, hesaplar buradan gelir.
-- Bölge sorumlusu tam olarak bir bölgeye bağlıdır; yöneticinin bölgesi yoktur (tümünü görür).
-- Parolalar yalnızca BCrypt hash'i olarak durur.
-- `ad_kullanici_adi` ileride AD/LDAP'a geçilirse eşleştirme için baştan bırakıldı.
-- ---------------------------------------------------------------------------
create table kullanici (
    id               bigserial primary key,
    kullanici_adi    text      not null unique,
    ad_soyad         text      not null,
    sifre_hash       text      not null,
    rol              text      not null check (rol in ('BOLGE_SORUMLUSU', 'YONETICI')),
    bolge_id         smallint  references bolge (id),
    ad_kullanici_adi text,
    aktif            boolean   not null default true,
    olusturma        timestamptz not null default now(),
    constraint kullanici_rol_bolge_tutarli check (
        (rol = 'BOLGE_SORUMLUSU' and bolge_id is not null) or
        (rol = 'YONETICI' and bolge_id is null)
    )
);

-- Bir bölgeden yalnızca bir aktif sorumlu olur.
create unique index kullanici_bolge_ux on kullanici (bolge_id) where rol = 'BOLGE_SORUMLUSU' and aktif;

insert into kullanici (kullanici_adi, ad_soyad, sifre_hash, rol, bolge_id) values
    ('ist-avr',  'İstanbul Avrupa Bölge Sorumlusu',  '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 1),
    ('ist-and',  'İstanbul Anadolu Bölge Sorumlusu', '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 2),
    ('edirne',   'Edirne Bölge Sorumlusu',           '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 3),
    ('ankara',   'Ankara Bölge Sorumlusu',           '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 4),
    ('kutahya',  'Kütahya-Şırnak Bölge Sorumlusu',   '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 5),
    ('izmir',    'İzmir Bölge Sorumlusu',            '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 6),
    ('bursa',    'Bursa Bölge Sorumlusu',            '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'BOLGE_SORUMLUSU', 7),
    ('yonetici', 'Yönetici',                         '$2b$12$guFCt0vfHa2PaxA2QZVG6uaLMPMHRHletet0Qm/bSNTP9yuYCApea', 'YONETICI',        null);

-- ---------------------------------------------------------------------------
-- Kable envanteri — dış kaynaktan (xlsx) içe aktarılır, `:tools:kable-import` doldurur.
-- Yetkilendirmenin temeli budur: bölge sorumlusu yalnızca kendi bölgesinin kablelerini görür.
-- Bölge, envanterdeki "Technical Consultant" sütununun `bolge.xlsx_saha_adi` ile eşleşmesinden türer.
-- Kayıtlar SİLİNMEZ: bir kable envanterden düşerse `aktif = false` olur, yoksa ona bağlı eski işlerin
-- yabancı anahtarı kırılır.
-- ---------------------------------------------------------------------------
create table kable (
    id          bigserial primary key,
    nosid       text      not null unique,
    bolge_id    smallint  not null references bolge (id),
    danisman_ad text      not null,
    adres_a     text      not null,
    adres_b     text,
    sehir_a     text,
    sehir_b     text,
    aktif       boolean   not null default true,
    kaynak      text      not null default 'XLSX',
    guncelleme  timestamptz not null default now()
);

create index kable_bolge_ix on kable (bolge_id) where aktif;
