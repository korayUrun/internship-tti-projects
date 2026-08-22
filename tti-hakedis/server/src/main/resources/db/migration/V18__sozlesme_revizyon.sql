-- V18 — Sözleşme revizyonu: Excel round-trip ile yeni sürüm (müdür isteği, Ağustos 2026).
--
-- Yayımlanmış bir migration ASLA düzenlenmez; değişiklik gerekirse yeni bir V numarası eklenir.
--
-- Pozlar "değişken" hâle geliyor: yönetici sözleşmeyi Excel olarak indirip serbestçe düzenleyip
-- (fiyat/ad/birim/yeni satır/araya satır/satır çıkarma) YENİ BİR KODLA yükler. Yeni sözleşme,
-- düzenlendiği sözleşmeye `onceki_id` ile bağlanır ve eskisi pasife alınır. Geçmiş belgeler eski
-- sözleşmenin/sürümün pozlarına ve dondurulmuş `belge_satir.birim_fiyat`'ına çivili kaldığı için
-- tutarları DEĞİŞMEZ (CLAUDE.md §5/§7/4). Bu, in-place fiyat mutasyonu yerine daima yeni bir
-- sözleşme doğuran güvenli revizyon yoludur.

alter table sozlesme add column onceki_id bigint references sozlesme (id);

-- Revizyon zincirinde bir sözleşme en fazla bir kez "önceki" olabilir (tek doğrusal geçmiş).
create unique index sozlesme_onceki_ux on sozlesme (onceki_id) where onceki_id is not null;
