# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep public class com.android.installreferrer.** { *; }

-keep class android.support.v4.media.session.MediaSessionCompat { *; }
-keep class android.support.v4.media.session.MediaSessionCompat$Token { *; }

-keepclassmembers class android.support.v4.media.session.MediaSessionCompat {
    public static final int FLAG_HANDLES_MEDIA_BUTTONS;
    public static final int FLAG_HANDLES_TRANSPORT_CONTROLS;
    public <init>(android.content.Context, java.lang.String);
    public void setFlags(int);
    public void setActive(boolean);
    public android.support.v4.media.session.MediaSessionCompat$Token getSessionToken();
}

-keep class androidx.media.app.NotificationCompat$MediaStyle { *; }
-keepclassmembers class androidx.media.app.NotificationCompat$MediaStyle {
    public <init>();
    public androidx.media.app.NotificationCompat$MediaStyle setMediaSession(
        android.support.v4.media.session.MediaSessionCompat$Token
    );
    android.support.v4.media.session.MediaSessionCompat$Token mToken;
}
-keep class com.appsflyer.** { *; }
-keep class kotlin.jvm.internal.** { *; }

-keep interface retrofit2.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

-keep class com.bytedance.sdk.** { *; }
-keep class com.facebook.** { *; }
