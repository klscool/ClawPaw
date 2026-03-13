# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留行号便于崩溃堆栈定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin
-keepattributes *Annotation*, InnerClasses
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }

# OkHttp / Okio（WebSocket 等）
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# JSch（SSH，反射加载算法等）
-keep class com.jcraft.jsch.** { *; }
# JSch 可选依赖：Android 上不用 Windows Pageant、Unix socket、Log4j/SLF4J、GSS/Kerberos，忽略缺失类
-dontwarn com.sun.jna.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

# Bouncy Castle（加密提供者）
-keep class org.bouncycastle.** { *; }

# NanoHttpd（HTTP 服务）
-keep class fi.iki.elonen.** { *; }

# ZXing 扫码
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }

# Health Connect
-keep class androidx.health.connect.** { *; }

# CameraX（R8 对 VirtualCamera 构造函数优化会报错，保持不优化）
-keep class androidx.camera.core.streamsharing.** { *; }

# Compose（避免 Composable 被误删）
-keep class androidx.compose.** { *; }