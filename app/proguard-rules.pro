# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep our classes
-keep class com.schedule.app.** { *; }

# Keep JS Bridge methods
-keepclassmembers class com.schedule.app.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
