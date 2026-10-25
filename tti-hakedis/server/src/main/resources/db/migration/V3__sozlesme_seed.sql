-- V3 — Örnek sözleşme ve ilk fiyat versiyonu.
--
-- Bu dosya kamuya açık depo için sentetik örnek veri içerir.

insert into sozlesme (kod, ad, baslik, sayfa_adi) values
    ('sample-contract-2026', 'Sample Contract 2026', 'Sample Route A-B', 'sample_contract_prices');

insert into poz (sozlesme_id, satir_kodu, poz_no, sort_key, ad, birim, grup_mu, fiyatli_mi, sira) values
    ((select id from sozlesme where kod = 'sample-contract-2026'), 'r1', '1', '{1}', 'General Works', '', true, false, 1),
    ((select id from sozlesme where kod = 'sample-contract-2026'), 'r2', '1.1', '{1,1}', 'Pole assembly', 'pcs', false, true, 2),
    ((select id from sozlesme where kod = 'sample-contract-2026'), 'r3', '1.2', '{1,2}', 'Pole disassembly', 'pcs', false, true, 3),
    ((select id from sozlesme where kod = 'sample-contract-2026'), 'r4', '2', '{2}', 'Fiber Works', '', true, false, 4),
    ((select id from sozlesme where kod = 'sample-contract-2026'), 'r5', '2.1', '{2,1}', 'Fiber cable deployment', 'm', false, true, 5);

insert into fiyat_versiyonu (sozlesme_id, ad, gecerlilik_baslangic, durum, aciklama) values
    ((select id from sozlesme where kod = 'sample-contract-2026'), 'Sample Price List v1', date '2026-01-01', 'YAYINDA',
     'Synthetic seed data for open-source repository');

insert into poz_fiyat (poz_id, versiyon_id, birim_fiyat) values
    ((select p.id from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = 'sample-contract-2026' and p.satir_kodu = 'r2'),
     (select fv.id from fiyat_versiyonu fv join sozlesme s on s.id = fv.sozlesme_id where s.kod = 'sample-contract-2026' and fv.durum = 'YAYINDA'),
     100.00),
    ((select p.id from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = 'sample-contract-2026' and p.satir_kodu = 'r3'),
     (select fv.id from fiyat_versiyonu fv join sozlesme s on s.id = fv.sozlesme_id where s.kod = 'sample-contract-2026' and fv.durum = 'YAYINDA'),
     80.00),
    ((select p.id from poz p join sozlesme s on s.id = p.sozlesme_id where s.kod = 'sample-contract-2026' and p.satir_kodu = 'r5'),
     (select fv.id from fiyat_versiyonu fv join sozlesme s on s.id = fv.sozlesme_id where s.kod = 'sample-contract-2026' and fv.durum = 'YAYINDA'),
     12.50);
