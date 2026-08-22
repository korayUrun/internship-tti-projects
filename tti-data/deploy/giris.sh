#!/bin/sh
# Konteyner açılış betiği: önce migration, sonra sunucu.
#
# Migration'ı konteyner AÇILIŞTA KENDİSİ koşar (YENI-PROJE.md §2) — elle SQL çalıştırılmaz.
# Bunu compose'un `command:` override'ıyla yapmıyoruz: o override PLATFORM deposunda yaşar,
# yani uygulamanın açılış sözleşmesini, uygulamanın yazarlarının hiç görmediği bir dosya
# sahiplenmiş olurdu.
set -e

# Yeniden deneme döngüsü REBOOT içindir: compose'un `depends_on: service_healthy` koşulu
# yalnızca `docker compose up` sırasında geçerlidir. Makine yeniden başladığında Docker yalnızca
# restart politikasını uygular ve SIRAYA UYMAZ — bu konteyner db'den önce kalkabilir. Döngü
# olmadan alembic düşer ve `restart: unless-stopped` bunu sonsuz bir çökme döngüsüne çevirir.
i=1
until alembic upgrade head; do
  if [ "$i" -ge 30 ]; then
    echo "HATA: veritabanı 150 saniye içinde hazır olmadı — vazgeçiliyor." >&2
    exit 1
  fi
  echo "veritabanı hazır değil, 5 sn sonra yeniden denenecek ($i/30)"
  i=$((i + 1))
  sleep 5
done

# ⚠️ --workers 1: havuz sınırı (pool_size + max_overflow = 10) SÜREÇ BAŞINADIR. İkinci bir
# worker beyan edilen bütçeyi sessizce ikiye katlar ve DİĞER projeleri düşürür
# (app/database.py ve tti-sunucu/.env.example'daki bütçe tablosu).
exec uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 1
