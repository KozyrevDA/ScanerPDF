# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ru.aiscanner.docs.** {
    *** Companion;
}
-keepclasseswithmembers class ru.aiscanner.docs.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Ktor / OkHttp
-dontwarn okhttp3.**
-dontwarn org.slf4j.**
