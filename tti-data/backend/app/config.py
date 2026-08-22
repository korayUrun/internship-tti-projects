from pydantic import ValidationError
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    # ⚠️ env_file BİLEREK YOK. Bu imaj sunucuda compose'un `environment:` bloğuyla beslenir;
    # imaja sızmış eski bir `.env` dosyası o bloğu SESSİZCE ezer ve uygulama yanlış veritabanına
    # bağlanır. `.dockerignore` `**/.env`'i zaten dışlıyor — bu iki koruma birbirini yedekliyor.
    # Yerel geliştirmede değişkenler `deploy/docker-compose.yml`den gelir.
    model_config = SettingsConfigDict(env_file_encoding="utf-8")

    # --- Zorunlu: VARSAYILANI YOK, eksikse uygulama AÇILMAZ --------------------------------
    # (YENI-PROJE.md §4) Gömülü varsayılana düşmek, üretimde yanlış veritabanına sessizce
    # bağlanmayı ya da tahmin edilebilir bir yönetici parolasıyla açılmayı mümkün kılar.
    database_url: str
    db_schema: str
    jwt_secret_key: str
    admin_username: str
    admin_password: str
    yukleme_kok: str
    surum: str

    # --- Davranış ayarları: varsayılanı olabilir (ortama bağlı değiller) --------------------
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 60 * 8

    # ⚠️ Havuz tavanı: pool_size + max_overflow = 10. Bu sayı `tti-sunucu/.env.example`deki
    # bütçe tablosuna BEYAN EDİLMİŞTİR ve makinedeki tüm projelerce paylaşılan
    # `max_connections`tan pay alır. Değiştirmeden önce YENI-PROJE.md §6'ya bakın.
    db_pool_size: int = 5
    db_max_overflow: int = 5

    row_count_drop_warning_ratio: float = 0.5

    # Panel ile aynı origin'den servis edildiği için üretimde BOŞTUR (Caddy yola göre ayırır).
    # Boşken CORS middleware'i hiç eklenmez — bkz. app/main.py.
    cors_allowed_origins: str = ""

    @property
    def cors_allowed_origins_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_allowed_origins.split(",") if origin.strip()]

    @property
    def sema(self) -> str | None:
        """Şema adı; boş dize None demektir (SQLite testleri şema tanımaz)."""
        return self.db_schema or None


try:
    settings = Settings()
except ValidationError as hata:
    # pydantic zaten yükseltiyor; buradaki tek katkı mesajın okunabilir olması. Konteyner
    # log'una bakan kişi "hangi değişken eksik" sorusunu ilk ekranda görmeli.
    raise SystemExit(
        "AYAR HATASI — zorunlu ortam değişkenleri eksik. Uygulama AÇILMIYOR.\n"
        "Gömülü varsayılana düşmek, üretimde yanlış veritabanına sessizce bağlanmak demektir\n"
        "(tti-sunucu/YENI-PROJE.md §4).\n\n"
        f"{hata}"
    ) from hata
