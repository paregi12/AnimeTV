-keepattributes *Annotation*,JavascriptInterface,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.paregi.animetv.MainActivity { <init>(); }
-keep class com.paregi.animetv.TvChannelRefreshJobService { <init>(); }
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
