# Compose 与 Kotlin 元数据由 AGP 自动处理，这里只保留极少数反射入口。
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-dontwarn org.jetbrains.annotations.**

# 备份 JSON 依赖字段名（org.json 是字符串键，不涉及反射，但保留模型可读性）
-keepclassmembers class com.offline.due.data.** { *; }
