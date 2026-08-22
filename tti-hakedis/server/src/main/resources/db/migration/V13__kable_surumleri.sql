-- V13 — Kable envanteri sürümleri (Faz U).
--
-- Envanter bugüne kadar yalnızca `:tools:kable-import` ile, geliştirici eliyle giriyordu. Yönetici
-- NOS+'tan gelen tam envanteri kendisi yükleyecek; bu iki tablo o yüklemenin **geri alınabilir**
-- olmasını sağlıyor.
--
-- Neden sürüm: içe aktarım yıkıcıdır. Dosyada olmayan her kable `aktif = false` olur (KableYazici),
-- yani eksik yüklenen bir dosya envanteri sessizce eritir ve hiçbir hata çıkmaz. Sürümlerle
-- yükleme ile **yürürlüğe alma** ayrışıyor: yükleme canlı envantere dokunmaz, yalnızca dosyayı
-- saklar ve farkı gösterir. Geri dönüş de aynı mekanizma — eski sürümü yeniden yürürlüğe almak.
--
-- ⚠️ `kable` tablosu YERİNDE KALIYOR ve yürürlükteki sürümün **izdüşümü** olur. Sürüm satırlarını
-- doğrudan sorgulasaydık `is_kaydi.kable_id` yabancı anahtarı, `kable_bolge_ix` ve tüm arama uçları
-- sürüm parametresi taşımak zorunda kalırdı. Sürümleme mevcut şemanın altına giriyor, üstüne değil.

create table kable_surum (
    id               bigserial   primary key,
    dosya_adi        text        not null,
    satir_sayisi     int         not null,
    -- CLI ile (`:tools:kable-import`) yapılan içe aktarımda oturum yoktur → null.
    yukleyen_id      bigint      references kullanici (id),
    yuklenme         timestamptz not null default now(),
    yururluge_alinma timestamptz,
    yururlukte       boolean     not null default false
);

-- Aynı anda en fazla bir sürüm yürürlükte olabilir. Kural veritabanında: uygulamada unutulsaydı
-- `kable` hangi sürümün izdüşümü olduğunu söyleyemez hâle gelirdi.
create unique index kable_surum_yururlukte_ux on kable_surum (yururlukte) where yururlukte;

-- Dosyanın okunmuş hâli. `bolge_id` DEĞİL `saha_adi` saklanıyor: bölgeye çevirme izdüşüm anında
-- `bolge.xlsx_saha_adi` üzerinden yapılır, böylece eşleşme sonradan düzeltilirse eski sürüm de
-- doğru bölgeye düşer.
create table kable_surum_satiri (
    surum_id    bigint not null references kable_surum (id) on delete cascade,
    nosid       text   not null,
    danisman_ad text   not null,
    saha_adi    text   not null,
    adres_a     text   not null,
    adres_b     text,
    sehir_a     text,
    sehir_b     text,
    primary key (surum_id, nosid)
);

-- ---------------------------------------------------------------------------
-- Başlangıç sürümü: bugün `kable` tablosunda ne varsa 1 numaralı sürüm odur.
--
-- Olmasaydı ekran ilk açılışta boş görünür ve **bugünkü hâle geri dönmek mümkün olmazdı**: ilk
-- yükleme yürürlüğe alındığı anda önceki envanterin kaydı hiçbir yerde durmuyor olurdu.
-- Envanteri hiç içe aktarılmamış bir veritabanında (yeni kurulum) sürüm oluşturulmaz.
-- ---------------------------------------------------------------------------
insert into kable_surum (dosya_adi, satir_sayisi, yururlukte, yururluge_alinma)
select 'cable_data_28072026.xlsx (içe aktarılmış)', count(*), true, now()
  from kable
 where aktif
having count(*) > 0;

insert into kable_surum_satiri (surum_id, nosid, danisman_ad, saha_adi, adres_a, adres_b, sehir_a, sehir_b)
select s.id, k.nosid, k.danisman_ad, b.xlsx_saha_adi, k.adres_a, k.adres_b, k.sehir_a, k.sehir_b
  from kable k
  join bolge b on b.id = k.bolge_id
  cross join kable_surum s
 where k.aktif
   and s.yururlukte;
