-keep class com.zalexdev.stryker.BuildConfig { *; }

-keep class com.zalexdev.stryker.custom.** { *; }

-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity
-keep public class * extends android.app.Service

-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep class retrofit2.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn retrofit2.**

-keep class jcifs.** { *; }
-dontwarn jcifs.**

-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

-keep class inet.ipaddr.** { *; }
-dontwarn inet.ipaddr.**

-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

-keep class com.facebook.shimmer.** { *; }

-keep class net.cachapa.expandablelayout.** { *; }
-keep class com.nambimobile.widgets.efab.** { *; }
-keep class com.getkeepsafe.taptargetview.** { *; }

-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class com.zalexdev.stryker.wpair.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn org.jspecify.**

-dontwarn okio.**
-keep class okio.** { *; }
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-keep class com.stryker.terminal.** { *; }
-keep class com.stryker.neolang.** { *; }
-keep class com.github.wrdlbrnft.sortedlistadapter.** { *; }
-keep class de.mrapp.android.** { *; }
-dontwarn com.stryker.terminal.**
-dontwarn com.stryker.neolang.**

-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }
-dontwarn org.greenrobot.eventbus.**

-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**
