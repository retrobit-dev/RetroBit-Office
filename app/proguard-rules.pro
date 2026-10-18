# Keep POI
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
# Keep ML Kit
-keep class com.google.mlkit.** { *; }
# Keep HMS
-keep class com.huawei.hms.** { *; }
