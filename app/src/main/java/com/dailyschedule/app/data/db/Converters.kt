package com.dailyschedule.app.data.db

import androidx.room.TypeConverter
import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionMode
import com.dailyschedule.app.core.model.SessionSource
import com.dailyschedule.app.core.model.SessionStatus

/**
 * 枚举 ⇄ 字符串。
 *
 * 数据库一律存枚举的 `name()` 字符串。序号存储的风险已在各枚举的 KDoc 说明：
 * 一旦重排，历史数据静默错位。
 */
class Converters {
    @TypeConverter
    fun toSessionStatus(value: String?): SessionStatus? = SessionStatus.fromRaw(value)

    @TypeConverter
    fun fromSessionStatus(value: SessionStatus?): String? = value?.name

    @TypeConverter
    fun toSessionMode(value: String?): SessionMode? = SessionMode.fromRaw(value)

    @TypeConverter
    fun fromSessionMode(value: SessionMode?): String? = value?.name

    /**
     * 读不到（列缺失、值不认识）时回落到 [SessionSource.DEFAULT]，而不是返回 null。
     * 字段是 NOT NULL 的，返回 null 会让整行读取直接抛异常 ——
     * 一条元数据不认识，不该导致整个历史记录读不出来。
     */
    @TypeConverter
    fun toSessionSource(value: String?): SessionSource = SessionSource.fromRaw(value) ?: SessionSource.DEFAULT

    @TypeConverter
    fun fromSessionSource(value: SessionSource): String = value.name

    @TypeConverter
    fun toExpenseType(value: String?): ExpenseType? = ExpenseType.fromRaw(value)

    @TypeConverter
    fun fromExpenseType(value: ExpenseType?): String? = value?.name
}
