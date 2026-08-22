# R8 keep kuralları (Faz J).
#
# Release build'de küçültme açık. İki kütüphane kural istiyor:
#
# 1) kotlinx.serialization — çözümleme derleme zamanında üretilen serializer sınıflarıyla yapılır,
#    ama bunlara `Companion.serializer()` / `$$serializer.INSTANCE` üzerinden **isimle** ulaşılır.
#    R8 bunları kullanılmıyor sanıp atarsa hata derlemede değil, cihazda ilk API çağrısında çıkar.
#    Kurallar kotlinx.serialization'ın kendi belgelerinden alınmıştır. Navigation Compose'un
#    type-safe rotaları da (`@Serializable data object WorkOrderListRoute`) bunlara bağlıdır.
#
# 2) OkHttp — isteğe bağlı çalışma zamanı bağımlılıklarını (Conscrypt, BouncyCastle) referans eder;
#    onlar APK'da olmadığı için yalnızca uyarı bastırılır.

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses

# @Serializable sınıfın Companion'ı korunur.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Companion üzerindeki serializer() korunur.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# object olarak tanımlı @Serializable tipler — rotalar bu biçimde.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Üretilen serializer sınıfları.
-keep,includedescriptorclasses class **$$serializer { *; }

# OkHttp / Okio — isteğe bağlı bağımlılıklar APK'da yok.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
