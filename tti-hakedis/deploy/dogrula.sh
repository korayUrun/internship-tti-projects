#!/bin/bash
# Sunucu doğrulama yardımcıları.
#
#   bash deploy/dogrula.sh            → Faz A/B kontrollerini koştur
#   source deploy/dogrula.sh          → yalnızca yardımcı fonksiyonları yükle (giris, istek)
#
# CSRF açık olduğu için her oturum önce bir GET ile XSRF-TOKEN cookie'si alır, sonra POST'larda
# X-XSRF-TOKEN başlığını gönderir.

KOK="${KOK:-http://localhost:8081}"

# giris <kullanici> <sifre> → cookie kavanozunun yolunu basar
giris() {
  local kullanici="$1" sifre="$2" jar="/tmp/tti-$1.jar"
  rm -f "$jar"
  curl -s -c "$jar" "$KOK/api/saglik" > /dev/null
  local token
  token=$(awk '/XSRF-TOKEN/{print $7}' "$jar")
  curl -s -b "$jar" -c "$jar" -H "X-XSRF-TOKEN: $token" -H 'Content-Type: application/json' \
    -d "{\"kullaniciAdi\":\"$kullanici\",\"sifre\":\"$sifre\"}" "$KOK/api/auth/giris" > /dev/null
  printf '%s' "$jar"
}

# istek <jar> <METOD> <yol> [gövde] → yanıt gövdesini basar
istek() {
  local jar="$1" metod="$2" yol="$3" govde="${4:-}"
  local token
  token=$(awk '/XSRF-TOKEN/{print $7}' "$jar")
  if [ -n "$govde" ]; then
    curl -s -b "$jar" -X "$metod" -H "X-XSRF-TOKEN: $token" -H 'Content-Type: application/json' \
      -d "$govde" "$KOK$yol"
  else
    curl -s -b "$jar" -X "$metod" -H "X-XSRF-TOKEN: $token" "$KOK$yol"
  fi
}

# durum <jar> <yol> → GET isteğinin yalnızca HTTP kodunu basar
durum() {
  curl -s -b "$1" -o /dev/null -w '%{http_code}' "$KOK$2"
}

# cagir <jar> <METOD> <yol> [gövde] → SON_KOD ve SON_GOVDE değişkenlerini doldurur.
#
# `istek` yalnızca gövdeyi, `durum` yalnızca kodu basıyor; iş/belge uçlarını doğrularken ikisi birlikte
# gerekiyor (ör. "201 döndü VE yanıtta AYNI_SP_VAR uyarısı var").
cagir() {
  local jar="$1" metod="$2" yol="$3" govde="${4:-}"
  local token yanit
  token=$(awk '/XSRF-TOKEN/{print $7}' "$jar")
  if [ -n "$govde" ]; then
    yanit=$(curl -s -b "$jar" -w $'\n%{http_code}' -X "$metod" -H "X-XSRF-TOKEN: $token" \
      -H 'Content-Type: application/json' -d "$govde" "$KOK$yol")
  else
    yanit=$(curl -s -b "$jar" -w $'\n%{http_code}' -X "$metod" -H "X-XSRF-TOKEN: $token" "$KOK$yol")
  fi
  SON_KOD=$(printf '%s' "$yanit" | tail -1)
  SON_GOVDE=$(printf '%s' "$yanit" | sed '$d')
}

sayac() {
  grep -o "$2" <<< "$1" | wc -l | tr -d ' '
}

# Doğrudan çalıştırıldıysa Faz A/B kontrollerini koştur.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  echo "=== Kimliksiz erişim (401 beklenir) ==="
  for uc in sozlesmeler kableler bolgeler kapsamlar auth/ben; do
    printf '  %-14s -> %s\n' "$uc" "$(curl -s -o /dev/null -w '%{http_code}' "$KOK/api/$uc")"
  done

  JAR_AVR=$(giris ist-avr CHANGE_ME_PASSWORD)
  JAR_ANK=$(giris ankara CHANGE_ME_PASSWORD)
  JAR_YON=$(giris yonetici CHANGE_ME_PASSWORD)

  # Faz O: kable bölge kısıtı KALKTI — her kullanıcı her kabloyu görebilir. Buna karşılık liste
  # aramasız boş döner (envanter 900+ satır) ve üç karakterden kısa arama da boş.
  echo "=== Kable araması (0 / 0 / >0 beklenir) ==="
  printf '  aramasız  -> %s kable\n' "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/kableler")" '"nosid"')"
  printf '  kısa (mt) -> %s kable\n' "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/kableler?ara=mt")" '"nosid"')"
  printf '  mtc       -> %s kable\n' "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/kableler?ara=mtc")" '"nosid"')"

  # Düzeltme Turu 4: proje listesinin serbest metin araması. Kableden BİLİNÇLİ olarak ayrışıyor —
  # eşik altı arama burada tam listeyi döndürür (yazarken ekranın boşalması arıza gibi okunuyordu),
  # kablede boş liste döndürür. İki satırın da aynı sayıyı vermesi beklenen davranıştır.
  echo "=== Proje araması (eşik altı = aramasızla AYNI sayı; mtc <= aramasız) ==="
  printf '  aramasız   -> %s proje\n' "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/isler")" '"spNo"')"
  printf '  kısa (mt)  -> %s proje\n' "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/isler?ara=mt")" '"spNo"')"
  printf '  mtc        -> %s proje\n' "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/isler?ara=mtc")" '"spNo"')"
  printf '  saçma dize -> %s proje (0 beklenir)\n' \
    "$(sayac "$(curl -s -b "$JAR_AVR" "$KOK/api/isler?ara=zzzyokboyle")" '"spNo"')"
  # Excel ucu aynı parametreyi almazsa indirilen dosya ekrandan sessizce ayrışır.
  printf '  liste.xlsx?ara=mtc -> %s (200 beklenir)\n' \
    "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_AVR" "$KOK/api/isler/liste.xlsx?ara=mtc")"

  echo "=== Çapraz bölge kablesi artık erişilebilir (200 / 200 beklenir) ==="
  ANK_NOSID=$(curl -s -b "$JAR_ANK" "$KOK/api/kableler?ara=mtc&limit=1" | sed -n 's/.*"nosid":"\([^"]*\)".*/\1/p')
  printf '  ist-avr  -> %s\n' "$(durum "$JAR_AVR" "/api/kableler/$ANK_NOSID")"
  printf '  yonetici -> %s\n' "$(durum "$JAR_YON" "/api/kableler/$ANK_NOSID")"

  # Faz P: birim fiyat poz listesinde de 2 haneye YUVARLI gelir — belgeye çivilenen fiyatla aynı
  # olsun ve "fiyat × miktar = tutar" tam tutsun diye. Ham 14 ondalıklı değer `poz_fiyat`'ta durur.
  # Sabit fiyat aranmıyor: sözleşmenin yayındaki sürümü değişmiş olabilir. Kilitlenen şey biçim —
  # her birim fiyat **string** ve **en fazla 2 ondalık** olmalı (Faz P).
  echo "=== Para: birim fiyat string ve 2 hane ==="
  curl -s -b "$JAR_AVR" "$KOK/api/sozlesmeler/2026-sample-rota-1/pozlar" | python3 -c "
import sys, json
from decimal import Decimal

pozlar = json.load(sys.stdin)
if not all(isinstance(p['unitPrice'], str) for p in pozlar):
    print('  HATA: birim fiyat string değil (JSON sayısına düşerse kuruş kayar)')
else:
    print(f'  {len(pozlar)} pozun hepsinde birim fiyat string -> DOĞRU')

uzun = [p['pozNo'] for p in pozlar if -Decimal(p['unitPrice']).as_tuple().exponent > 2]
if uzun:
    print(f'  HATA: 2 haneden uzun birim fiyat: {\", \".join(uzun[:5])}')
else:
    print('  hiçbir birim fiyat 2 haneden uzun değil -> DOĞRU')
"

  # --- Faz U: kablo envanteri sürümleri -------------------------------------------------------
  #
  # Kilitlenen iddia **izdüşümün tersinirliği**: aynı dosya yüklenip yürürlüğe alındığında ve sonra
  # eski sürüme dönüldüğünde aktif kablo sayısı birebir aynı kalmalı. Bu tutmazsa geri dönüş
  # düğmesi envanteri sessizce bozuyor demektir ve hiçbir hata çıkmaz.
  #
  # Test verisi yazar (yeni sürüm satırı); yalnızca geliştirme veritabanında koşturun.
  ENVANTER="${ENVANTER:-tools/kable/cable_data_28072026.xlsx}"
  if [ -f "$ENVANTER" ]; then
    echo "=== Kablo envanteri sürümleri (Faz U) ==="
    kableSayisi() { curl -s -b "$1" "$KOK/api/kable-surumleri" | sed -n 's/.*"aktifKableSayisi":\([0-9]*\).*/\1/p'; }
    surumSayisi() { sayac "$(curl -s -b "$1" "$KOK/api/kable-surumleri")" '"dosyaAdi"'; }

    ONCEKI_KABLE=$(kableSayisi "$JAR_YON")
    ONCEKI_SURUM=$(surumSayisi "$JAR_YON")
    YURURLUKTEKI=$(curl -s -b "$JAR_YON" "$KOK/api/kable-surumleri" \
      | python3 -c "import sys,json; s=[x for x in json.load(sys.stdin)['surumler'] if x['yururlukte']]; print(s[0]['id'] if s else '')")
    printf '  başlangıç -> %s kablo, %s sürüm (yürürlükteki: %s)\n' "$ONCEKI_KABLE" "$ONCEKI_SURUM" "${YURURLUKTEKI:-yok}"

    # Yetki: bölge sorumlusu envanter yükleyemez (403). Bu satır olmasaydı uç
    # `anyRequest().authenticated()`'e düşse de hiçbir test fark etmezdi.
    TOKEN_AVR=$(awk '/XSRF-TOKEN/{print $7}' "$JAR_AVR")
    printf '  bölge sorumlusu yükleme -> %s (403 beklenir)\n' \
      "$(curl -s -o /dev/null -w '%{http_code}' -b "$JAR_AVR" -X POST -H "X-XSRF-TOKEN: $TOKEN_AVR" \
         -F "dosya=@$ENVANTER" "$KOK/api/kable-surumleri")"

    # Yükleme envanteri DEĞİŞTİRMEZ: sürüm sayısı artar, kablo sayısı aynı kalır.
    TOKEN_YON=$(awk '/XSRF-TOKEN/{print $7}' "$JAR_YON")
    YENI_SURUM=$(curl -s -b "$JAR_YON" -X POST -H "X-XSRF-TOKEN: $TOKEN_YON" \
      -F "dosya=@$ENVANTER" "$KOK/api/kable-surumleri" \
      | python3 -c "import sys,json; print(json.load(sys.stdin)['surum']['id'])")
    printf '  yükleme sonrası -> %s kablo (değişmemeli), %s sürüm (bir artmalı)\n' \
      "$(kableSayisi "$JAR_YON")" "$(surumSayisi "$JAR_YON")"

    cagir "$JAR_YON" POST "/api/kable-surumleri/$YENI_SURUM/yururluk"
    printf '  yürürlüğe alma -> %s · %s kablo\n' "$SON_KOD" "$(kableSayisi "$JAR_YON")"

    # Yürürlükteki sürüm silinemez (409): `kable` onun izdüşümü, silinseydi envanterin kaynağı
    # okunamaz hâle gelirdi.
    cagir "$JAR_YON" DELETE "/api/kable-surumleri/$YENI_SURUM"
    printf '  yürürlükteki sürümü silme -> %s (409 beklenir)\n' "$SON_KOD"

    if [ -n "$YURURLUKTEKI" ]; then
      cagir "$JAR_YON" POST "/api/kable-surumleri/$YURURLUKTEKI/yururluk"
      SONRAKI_KABLE=$(kableSayisi "$JAR_YON")
      printf '  eski sürüme dönüş -> %s · %s kablo\n' "$SON_KOD" "$SONRAKI_KABLE"
      if [ "$SONRAKI_KABLE" = "$ONCEKI_KABLE" ]; then
        echo '  izdüşüm tersinir: geri dönüşte kablo sayısı ilk hâline döndü -> DOĞRU'
      else
        echo "  HATA: geri dönüşte kablo sayısı $ONCEKI_KABLE değil $SONRAKI_KABLE"
      fi
      # Testin bıraktığı sürümü temizle (artık yürürlükte değil).
      cagir "$JAR_YON" DELETE "/api/kable-surumleri/$YENI_SURUM"
      printf '  test sürümünü silme -> %s (200 beklenir), %s sürüm kaldı\n' "$SON_KOD" "$(surumSayisi "$JAR_YON")"
    else
      echo '  (yürürlükte sürüm yoktu; geri dönüş adımı atlandı)'
    fi
  else
    echo "=== Kablo envanteri sürümleri: $ENVANTER yok, atlandı ==="
  fi
fi
