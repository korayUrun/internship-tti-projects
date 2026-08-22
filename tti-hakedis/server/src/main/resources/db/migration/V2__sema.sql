-- V2 — Sözleşme/fiyat, iş ve belge şeması.

-- ---------------------------------------------------------------------------
-- Sözleşme ve poz listesi.
-- `poz.satir_kodu`, mevcut `PozItem.id` ("r5") ile birebir aynıdır: poz numarası tekrar edebildiği için
-- satır kimliği olarak kullanılamaz, kimlik kaynak dosyadaki satır sırasından türer.
-- `poz_no` ASLA sayıya çevrilmez ("1,5", "4.4.", "4.10"); sıralama `sort_key` dizisiyle yapılır,
-- böylece 4.10 > 4.9 olur (PozOrdering.compareSortKeys ile aynı davranış).
-- ---------------------------------------------------------------------------
create table sozlesme (
    id        bigserial primary key,
    kod       text      not null unique,
    ad        text      not null,
    baslik    text      not null,
    sayfa_adi text,
    aktif     boolean   not null default true
);

create table poz (
    id          bigserial primary key,
    sozlesme_id bigint    not null references sozlesme (id) on delete cascade,
    satir_kodu  text      not null,
    poz_no      text      not null,
    sort_key    integer[] not null,
    ad          text      not null,
    birim       text      not null,
    grup_mu     boolean   not null,
    fiyatli_mi  boolean   not null,
    sira        integer   not null,
    unique (sozlesme_id, satir_kodu)
);

-- Excel çıktısı sözleşmenin TÜM satırlarını kaynak sıraya göre yazar; bu indeks o sorguyu karşılar.
create index poz_sozlesme_sira_ix on poz (sozlesme_id, sira);

-- ---------------------------------------------------------------------------
-- Fiyat versiyonlama — FİYAT DONDURMANIN ilk yarısı.
-- Yönetici birim fiyatları güncellediğinde yeni bir versiyon yayına alınır; geçmiş belgeler
-- bağlı oldukları versiyonda kalır ve tutarları değişmez.
-- ---------------------------------------------------------------------------
create table fiyat_versiyonu (
    id                   bigserial primary key,
    sozlesme_id          bigint    not null references sozlesme (id),
    ad                   text      not null,
    gecerlilik_baslangic date      not null,
    durum                text      not null check (durum in ('TASLAK', 'YAYINDA', 'ARSIV')),
    aciklama             text,
    olusturan_id         bigint    references kullanici (id),
    olusturma            timestamptz not null default now()
);

-- Bir sözleşmenin aynı anda yalnızca bir yayında versiyonu olur.
create unique index fiyat_versiyonu_yayin_ux on fiyat_versiyonu (sozlesme_id) where durum = 'YAYINDA';

-- Birim fiyat tipi bilinçli olarak KISITSIZ `numeric`.
-- Kaynak veri 14 ondalık taşıyor (648.35852723050107); numeric(18,7) bu değeri keser ve
-- hesap kaynak Excel ile uyuşmaz.
create table poz_fiyat (
    poz_id      bigint  not null references poz (id) on delete cascade,
    versiyon_id bigint  not null references fiyat_versiyonu (id) on delete cascade,
    birim_fiyat numeric not null check (birim_fiyat >= 0),
    primary key (poz_id, versiyon_id)
);

-- ---------------------------------------------------------------------------
-- İş kaydı.
-- Kable envanteri dışarıdan yenilendiği için adresler işe DONDURULARAK kopyalanır: aksi hâlde
-- envanter güncellendiğinde altı ay önce tamamlanmış bir hakedişin adresi kendiliğinden değişir.
-- Aynı gerekçeyle `bolge_id` de kopyalanır; yetki geçmiş belgelerde kableden değil işten okunur.
-- ---------------------------------------------------------------------------
create table is_kaydi (
    id           bigserial primary key,
    bolge_id     smallint  not null references bolge (id),
    kable_id     bigint    not null references kable (id),
    kable_nosid  text      not null,
    adres_a      text      not null,
    adres_b      text,
    sp_no        text      not null,
    route        text      not null,
    ek_araligi   text      not null,
    kapsam_id    smallint  not null references kapsam (id),
    sozlesme_id  bigint    not null references sozlesme (id),
    olusturan_id bigint    not null references kullanici (id),
    olusturma    timestamptz not null default now()
);

create index is_kaydi_bolge_ix on is_kaydi (bolge_id);

-- ---------------------------------------------------------------------------
-- Belge.
-- Her (iş, tür) için tek belge vardır; üç tür ayrı kayıtlardır.
-- `fiyat_versiyonu_id` FİYAT DONDURMANIN ikinci yarısıdır (satır bazlı kopya için bkz. belge_satir).
-- ---------------------------------------------------------------------------
create table belge (
    id                 bigserial primary key,
    is_id              bigint    not null references is_kaydi (id) on delete cascade,
    tur                text      not null check (tur in ('FIZIBILITE', 'KESIF', 'HAKEDIS')),
    bolge_id           smallint  not null references bolge (id),
    yil                smallint  not null,
    sira_no            integer   not null,
    belge_no           text      not null unique,
    durum              text      not null check (durum in ('TASLAK', 'TAMAMLANDI', 'IPTAL')),
    fiyat_versiyonu_id bigint    not null references fiyat_versiyonu (id),
    olusturan_id       bigint    not null references kullanici (id),
    olusturma          timestamptz not null default now(),
    tamamlanma         timestamptz,
    unique (is_id, tur),
    unique (bolge_id, yil, tur, sira_no)
);

-- Aşama kuralı (b): bir bölgede aynı anda birden fazla açık keşif olamaz.
-- Kısmi unique index kullanmak, kuralı yarış durumunda bile veritabanı seviyesinde garanti eder;
-- servis katmanındaki kontrol yalnızca anlaşılır hata mesajı üretmek içindir.
create unique index belge_acik_kesif_ux on belge (bolge_id) where tur = 'KESIF' and durum = 'TASLAK';

-- `tutar` sütunu bilinçli olarak YOKTUR: hesaplanan tutar saklanmaz (CLAUDE.md §7).
-- Birim fiyat satıra kopyalandığı ve miktar burada durduğu için tutar
-- PricingCalculator.rowAmount ile birebir yeniden üretilebilir.
create table belge_satir (
    belge_id    bigint  not null references belge (id) on delete cascade,
    poz_id      bigint  not null references poz (id),
    miktar      numeric not null check (miktar >= 0),
    birim_fiyat numeric not null,
    primary key (belge_id, poz_id)
);

-- Belge numarası sayacı — (bölge, yıl, tür) başına ayrı sayaç.
-- Sequence kullanılamaz: her üçlü için bağımsız ve boşluksuz bir seri gerekiyor.
--   insert into belge_sayac (bolge_id, yil, tur, son_no) values (?, ?, ?, 1)
--   on conflict (bolge_id, yil, tur) do update set son_no = belge_sayac.son_no + 1
--   returning son_no;
-- Tek atomik ifade olduğu için eşzamanlı isteklerde çakışma oluşmaz.
create table belge_sayac (
    bolge_id smallint not null references bolge (id),
    yil      smallint not null,
    tur      text     not null check (tur in ('FIZIBILITE', 'KESIF', 'HAKEDIS')),
    son_no   integer  not null check (son_no > 0),
    primary key (bolge_id, yil, tur)
);

-- ---------------------------------------------------------------------------
-- Ekler: EK1 güzergâh dosyası (.kmz/.kml), EK2 saha görselleri.
-- ---------------------------------------------------------------------------
create table ek (
    id          bigserial primary key,
    belge_id    bigint    not null references belge (id) on delete cascade,
    tur         text      not null check (tur in ('EK1_ROTA', 'EK2_GORSEL')),
    dosya_adi   text      not null,
    depo_yolu   text      not null,
    boyut_bayt  bigint    not null check (boyut_bayt >= 0),
    yukleme     timestamptz not null default now()
);

create index ek_belge_ix on ek (belge_id);
