-- Proje 3: TTI Merkezi Veri Yönetim Sistemi — veritabanı `data`, rol `data`, şema `data`.
--
-- Çalıştırma (bakım rolüyle, bir kez, `db` ilk açılıştan sonra):
--     docker exec -i tti-db psql -U tti_hakedis -d tti -v ON_ERROR_STOP=1 < provizyon/03-data.sql
--
-- ⚠️ Konteynerin otomatik başlatma betikleri (initdb.d) yalnızca veritabanı İLK KEZ
-- oluşturulurken çalışır; volume'de veri varsa hiç çalışmazlar. Bu yüzden elle.
--
-- ⚠️ Bu dosya `-d tti` ile açılır çünkü `create database` çalıştırmak için bir yere bağlı olmak
-- gerekir. Aşağıdaki ifadelerin hiçbiri `tti` veritabanının İÇİNDEKİ bir şeyi değiştirmez:
-- ikisi küme (cluster) düzeyi, üçü hedefi `data` olan yetki ifadeleri. `\connect data`'dan
-- sonrası zaten yeni veritabanının içidir.

-- Ayrıcalıksız rol. `POSTGRES_USER` (bakım hesabı) bir uygulama hesabı DEĞİLDİR: imaj onu
-- SUPERUSER olarak açar ve superuser tüm yetki kontrollerini atlar (bkz. 01-tti.sql).
create user data with password 'degistir-beni';   -- .env → DATA_DB_PAROLA ile AYNI olmalı

create database data owner data;

-- ⚠️ Atlanırsa "veritabanı ayrımı" sandığınızdan zayıf olur: PostgreSQL bağlanma yetkisini
-- varsayılan olarak PUBLIC'e verir. Veri okuyamaz ama bağlanır ve katalogları görür.
revoke connect on database data from public;
grant  connect on database data to data;

-- Şemayı Alembic'in ilk migration'ı açar (`create schema if not exists data`, alembic/env.py).
-- Şemayı ELLE AÇMAYIN: sürüm tablosuyla çakışabilir, o iş migration'ındır. Gereken tek yetki:
grant create on database data to data;

\connect data
revoke all on schema public from public;
