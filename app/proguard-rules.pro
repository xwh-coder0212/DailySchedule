# DailySchedule ProGuard / R8 规则

## ---------- Room ----------
# Room 生成的 *_Impl 通过反射实例化
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Room 用到的数据库列名在 @ColumnInfo 里显式声明时不需要保留，
# 但导出/导入用的字段名需要保留在序列化模型上。
-keepclassmembers class com.dailyschedule.app.data.db.** { *; }

## ---------- kotlinx.serialization ----------
-keepclassmembers class com.dailyschedule.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.dailyschedule.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }

## ---------- 崩溃与日志 ----------
# 保留异常堆栈行号，便于阅读本地落盘的崩溃日志
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

## ---------- 通用 ----------
-dontwarn org.jetbrains.annotations.**
