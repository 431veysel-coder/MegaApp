# ProGuard rules for MegaApp
-keep class com.megaapp.superbrowser.** { *; }
-keep interface com.megaapp.superbrowser.** { *; }

# WebView JavaScript Interface
-keepclassmembers class com.megaapp.superbrowser.MinisBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Moshi
-keep class com.squareup.moshi.** { *; }

# OkHttp
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin Coroutines
-keep class kotlinx.coroutines.** { *; }

# AndroidX
-keep class androidx.** { *; }

# Google Crypto Tink
-keep class com.google.crypto.tink.** { *; }

# Keep annotations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod