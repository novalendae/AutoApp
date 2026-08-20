# LuaJ usa reflexao para o luajava/coerce
-keep class org.luaj.vm2.** { *; }
-keep class com.kaizen.auto.runtime.lua.** { *; }
-dontwarn org.luaj.**
-dontwarn javax.script.**

# OpenCV
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Room / Kotlinx
-keep class com.kaizen.auto.data.db.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
