-keepattributes *Annotation*,JavascriptInterface,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.paregi.animetv.** { *; }
-keep class com.paregi.androidtv.animetvjmto.** { *; }
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
