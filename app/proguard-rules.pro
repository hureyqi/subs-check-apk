# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in ${sdk.dir}/tools/proguard/proguard-android.txt

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontnote okhttp3.**
-dontwarn okhttp3.**

# Okio
-dontwarn okio.**

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.coroutines.Continuation

# SnakeYAML
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# Coroutines
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.internal.synchronization.**
-keep class kotlinx.coroutines.android.** { *; }

# Model classes
-keep class com.subscheck.apk.** { *; }
