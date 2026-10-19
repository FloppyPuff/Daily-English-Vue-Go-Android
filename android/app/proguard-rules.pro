# Daily English Glass keeps R8 shrinking off (isMinifyEnabled = false), so this
# file only carries rules that must survive a future switch to a minified build.

# Room generates implementations that are looked up by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# kotlinx.serialization resolves serializers reflectively for @Serializable types.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.dailyenglish.nativeapp.** {
    *** Companion;
    static ** INSTANCE;
}
-keepclasseswithmembers class com.dailyenglish.nativeapp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit keeps interface methods and generic signatures for the API contract.
-keep,allowobfuscation interface com.dailyenglish.nativeapp.data.remote.** { *; }
-keepattributes Signature, Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
