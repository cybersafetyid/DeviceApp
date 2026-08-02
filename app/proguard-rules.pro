-keep class com.net2software.mobile.netlibs.** { *; }
-keep class com.net2software.filelog.** { *; }
-keep class com.example.multitripandroid.** { *; }
-keepclasseswithmembernames class com.net2software.mobile.netlibs.** {
    native <methods>;
}

-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn javax.naming.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticMarkerBinder
