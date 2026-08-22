-- YENİ PROJE ŞABLONU — kopyalayın, <proje> yerine gerçek adı yazın, sıradaki numarayla kaydedin.
--
-- Neden ayrı veritabanı, ayrı şema değil:
-- Şema ayrımında `select ... from baska_sema.tablo` GEÇERLİ bir SQL ifadesidir; yalnızca yetki
-- kontrolünden düşer ve yetki, hatayı çözmeye çalışan iyi niyetli biri tarafından verilebilir.
-- Veritabanları arası sorgu ise PostgreSQL'in dilinde YOKTUR: `postgres_fdw` kurulmadıkça
-- imkânsızdır ve o kurulum incelemede görünür. Yazarların değiştiği bir ortamda sınırın yapısal
-- olması, disipline dayanmasından üstündür.
--
-- ⚠️ `POSTGRES_USER` BİR UYGULAMA HESABI DEĞİLDİR. Postgres imajı onu SUPERUSER olarak
-- oluşturur ve superuser tüm yetki kontrollerini atlar; o rolle bağlanan bir uygulama
-- `revoke connect` ne yazarsanız yazın HER projenin verisini okur. Bu rol yalnızca BAKIM
-- hesabıdır (provizyon, pg_dump, elle psql). Aşağıdaki rol ayrıcalıksızdır — öyle kalmalı.
--
-- Çalıştırma (bakım rolüyle, tek seferlik):
--     docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/NN-<proje>.sql

create user <proje> with password '<PAROLA — .env ile aynı>';

create database <proje> owner <proje>;

-- ⚠️ ATLANMAMASI GEREKEN ADIM.
-- PostgreSQL yeni bir veritabanına bağlanma yetkisini VARSAYILAN OLARAK PUBLIC'e verir; yani
-- makinedeki her rol oraya bağlanabilir. Veri okuyamaz ama bağlanır ve katalogları görür.
-- Bu iki satır olmadan "veritabanı ayrımı" sandığınızdan zayıftır.
revoke connect on database <proje> from public;
grant  connect on database <proje> to <proje>;

-- Şema `public` kimsenin çalışma alanı değildir; proje kendi adında bir şema kullanır ve onu
-- migration'ı açar. (EF Core: HasDefaultSchema + MigrationsHistoryTable · Flyway: default-schema)
\connect <proje>
revoke all on schema public from public;

-- Migration'ın kendi şemasını açabilmesi için gereken tek yetki. Şemayı ELLE AÇMAYIN:
-- sürüm tablosuyla çakışabilir, o iş migration'ındır.
grant create on database <proje> to <proje>;
