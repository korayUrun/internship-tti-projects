-- `rapor` rolünü ve KENDİ VERİTABANINI açar.
--
-- ⚠️ Bu dosya YALNIZCA geliştirme compose'unda otomatik çalışır: PostgreSQL'in
-- /docker-entrypoint-initdb.d betikleri veritabanı İLK KEZ oluşturulurken koşar, volume'de
-- veri varsa hiç çalışmazlar. Sunucuda aynı satırlar elle çalıştırılır
-- (tti-sunucu/provizyon/02-rapor.sql — orası otoritedir).

create user rapor with password 'rapor';

-- Şema değil VERİTABANI. Şema ayrımında `select ... from hakedis.tablo` geçerli bir SQL'dir ve
-- yalnızca yetkiden düşer; veritabanları arası sorgu ise PostgreSQL'in dilinde YOKTUR
-- (postgres_fdw gerekir). Yazarların değiştiği bir ortamda sınırın yapısal olması, disipline
-- dayanmasından üstündür. Ayrıca geri yükleme bağımsızlaşır: `dropdb rapor` + `pg_restore`
-- diğer projelerin canlı verisine hiç dokunmaz.
create database rapor owner rapor;

-- ⚠️ ATLANMAMASI GEREKEN ADIM. PostgreSQL yeni bir veritabanına bağlanma yetkisini VARSAYILAN
-- OLARAK PUBLIC'e verir; yani makinedeki her rol buraya bağlanabilir. Veri okuyamaz ama bağlanır
-- ve katalogları görür. Bu iki satır olmadan "veritabanı ayrımı" sandığınızdan zayıftır.
revoke connect on database rapor from public;
grant  connect on database rapor to rapor;

-- Şemayı EF Core'un ilk migration'ı açar (EnsureSchema). Bunun için gereken tek yetki:
grant create on database rapor to rapor;

-- Paylaşılan veritabanında `public` kimsenin çalışma alanı değildir.
\connect rapor
revoke all on schema public from public;
