-- V19 — EK3 "TTI Kullanılan Malzemeler" (kullanıcı isteği, 18 Ağustos 2026).
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Neden `ek` tablosuna GİRMİYOR: `ek` dosya taşır (dosya_adi + depo_yolu + boyut_bayt) ve EK1/EK2
-- oradadır. Malzeme listesi dosya değil **yapısal veri** — ad, miktar, seri no. Aynı tabloya
-- sıkıştırmak `ek`'in üç sütununu boş bırakıp anlamını bulanıklaştırırdı.
--
-- Yalnızca HAKEDIS belgelerinde doldurulur; kural sunucuda (`BelgeMalzemeServisi`), veritabanında
-- değil: `belge.tur` bir metin sütunu ve tür kuralını iki yerde tutmak, biri değişince sessizce
-- ayrışan ikinci bir kural üretirdi.

create table belge_malzeme (
    id       bigserial primary key,
    belge_id bigint    not null references belge (id) on delete cascade,

    -- ÜÇÜ DE opsiyonel (kullanıcı kararı): saha bazen yalnızca seri no, bazen yalnızca ad
    -- biliyor. Zorunluluk koymak, eldeki eksik bilgiyi hiç kaydettirmemek demekti.
    ad       text,
    -- Miktar `numeric`: para değil ama ondalıklı olabilir (metre, m²). `text` tutulsaydı
    -- toplama/sıralama yapılamaz, `double` olsaydı §5'teki hassasiyet kuralı delinirdi.
    miktar   numeric check (miktar is null or miktar >= 0),
    seri_no  text,

    -- Giriş sırası korunur: liste kullanıcının yazdığı sırayla basılmalı (Excel ve PDF).
    sira     integer   not null
);

create index belge_malzeme_belge_ix on belge_malzeme (belge_id, sira);
