-- Proje 1: TTI Saha Hakediş — veritabanı `tti`, uygulama rolü `tti_uygulama`, şema `hakedis`.
--
-- Çalıştırma:
--     docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/01-tti.sql
--
-- ⚠️⚠️ EN ÖNEMLİ MADDE: `POSTGRES_USER` BİR UYGULAMA HESABI DEĞİLDİR.
--
-- Postgres imajı `POSTGRES_USER` ile verilen rolü **SUPERUSER** olarak oluşturur ve superuser
-- TÜM yetki kontrollerini atlar. Bir uygulama o rolle bağlanırsa, `revoke connect` ne yazarsanız
-- yazın makinedeki HER projenin verisini okuyabilir — kurduğunuz sınır tek yönlü kalır ve bunu
-- hiçbir hata göstermez. (Bu tam olarak yaşandı: TTI `tti_hakedis` ile bağlanırken
-- `rapor.kullanici` tablosunu okuyabiliyordu.)
--
-- Kural: `POSTGRES_USER` yalnızca BAKIM hesabıdır (provizyon, pg_dump, elle psql).
--        Her proje kendi AYRICALIKSIZ rolüyle bağlanır.

-- --- Uygulama rolü ------------------------------------------------------------------------
create role tti_uygulama login password 'degistir-beni';   -- .env → DB_PAROLA ile AYNI olmalı

grant connect on database tti to tti_uygulama;
-- Flyway'in `create-schemas: true` ile `hakedis` şemasını açabilmesi için:
grant create  on database tti to tti_uygulama;

-- --- Var olan bir kurulumdan geçiyorsanız: sahipliği devredin --------------------------------
-- Flyway migration çalıştırabilmek için şemanın ve tablolarının SAHİBİ olmalıdır.
-- (Sıfır kurulumda bu bloğa gerek yoktur; şemayı zaten tti_uygulama açar.)
--
-- `reassign owned by` KULLANILMAZ: bootstrap rolü sistem nesnelerinin de sahibidir ve komut
-- "cannot reassign ownership ... required by the database system" ile düşer.
do $$
declare r record;
begin
  if exists (select 1 from pg_namespace where nspname='hakedis') then
    execute 'alter schema hakedis owner to tti_uygulama';
    for r in select tablename from pg_tables where schemaname='hakedis' loop
      execute format('alter table hakedis.%I owner to tti_uygulama', r.tablename);
    end loop;
    for r in select sequencename from pg_sequences where schemaname='hakedis' loop
      execute format('alter sequence hakedis.%I owner to tti_uygulama', r.sequencename);
    end loop;
    for r in select viewname from pg_views where schemaname='hakedis' loop
      execute format('alter view hakedis.%I owner to tti_uygulama', r.viewname);
    end loop;
  end if;
end $$;

-- --- Sınırlar -------------------------------------------------------------------------------
-- ⚠️ PostgreSQL her veritabanına bağlanma yetkisini varsayılan olarak PUBLIC'e verir; yani
-- diğer projelerin rolleri de buraya bağlanabilir. Veri okuyamazlar ama bağlanır ve katalogları
-- görürler. Kapatıyoruz.
revoke connect on database tti from public;

-- `public` kimsenin çalışma alanı değildir.
revoke all on schema public from public;

-- Şema `hakedis`'i Flyway kendisi açar (application.yml → flyway.create-schemas: true).
-- ELLE AÇMAYIN: sürüm tablosuyla çakışabilir.
