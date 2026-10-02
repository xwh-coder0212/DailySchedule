package com.dailyschedule.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dailyschedule.app.data.db.dao.CategoryDao
import com.dailyschedule.app.data.db.dao.ExpenseDao
import com.dailyschedule.app.data.db.dao.ProjectDao
import com.dailyschedule.app.data.db.dao.SessionDao
import com.dailyschedule.app.data.db.entity.CategoryEntity
import com.dailyschedule.app.data.db.entity.ExpenseEntity
import com.dailyschedule.app.data.db.entity.FocusSessionEntity
import com.dailyschedule.app.data.db.entity.ProjectEntity

@Database(
    entities = [
        ProjectEntity::class,
        FocusSessionEntity::class,
        ExpenseEntity::class,
        CategoryEntity::class,
    ],
    version = AppDatabase.DB_VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    abstract fun sessionDao(): SessionDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    companion object {
        /**
         * Room schema 版本。
         *
         * 写成常量并被 `@Database(version = ...)` 引用，而不是在两处各写一个字面量：
         * JSON 备份文件里要写一个 `dbVersion` 字段供排查问题，那个值必须与这里一致。
         * 两处各写一个数字，某天加迁移时改了注释里的那个、忘了另一个，
         * 备份文件就会开始说假话，而且没有任何东西会报错。
         */
        const val DB_VERSION: Int = 2
    }
}

/**
 * 新建数据库时的额外安装步骤。
 *
 * 只有一个职责：装上「同一时刻最多一个活动会话」的数据库层守卫。
 * 具体 SQL 与它为什么是触发器而不是部分唯一索引，见 [SessionUniquenessGuard]。
 *
 * ## 注意 onCreate 与迁移是两条路
 * 全新安装走这里；已有数据的库走 `MIGRATION_1_2`。两边都必须调用同一个
 * [SessionUniquenessGuard.install]，否则会出现「新装用户有守卫、升级用户没有」
 * 这种没有任何测试能发现的偏差。
 */
class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        SessionUniquenessGuard.install(db)
    }
}
