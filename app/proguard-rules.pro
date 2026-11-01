# 当前版本未开启代码混淆；保留规则以便二期启用 minify 时参考
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# Supabase SDK 与 Ktor 使用反射/序列化，保留其模型类
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
