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

# Room
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Ktor / coroutines
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# OpenCV (JNI)
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Tesseract4Android (JNI)
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# RuStore Billing
-keep class ru.rustore.sdk.** { *; }
-dontwarn ru.rustore.sdk.**
