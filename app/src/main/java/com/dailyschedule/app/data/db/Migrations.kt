package com.dailyschedule.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dailyschedule.app.core.model.SessionSource

/**
 * v1 → v2：`focus_sessions` 增加 `source` 列（计时器产生 / 用户补录），
 * 并把「活动会话唯一性」的强制手段从部分唯一索引换成触发器。
 *
 * ## 为什么必须有 DEFAULT
 * SQLite 的 `ALTER TABLE ... ADD COLUMN` 声明了 `NOT NULL` 就**必须**给默认值，
 * 否则已有行不知道该填什么，语句直接报错。存量记录全都是计时器跑出来的，
 * 填 `TIMER` 是在陈述事实，不是权宜之计。
 *
 * ## 顺带修掉的升级即崩缺陷
 * v1 的部分唯一索引 `idx_single_active_session` 由裸 SQL 创建、不在 Room 元数据里，
 * 而 Room 校验 schema 时会比对索引集合 → 升级时判为「多了个索引」并抛
 * `Migration didn't properly handle`。全新安装不触发校验，所以这个缺陷一直藏着。
 * 现在由 [SessionUniquenessGuard.install] 删掉旧索引、装上触发器。
 *
 * ## 为什么不用 autoMigration / destructiveMigration
 * 1. `fallbackToDestructiveMigration` 会把用户的事实记录静默清空 ——
 *    这条禁止写在 `DataModule.provideAppDatabase` 的注释里，不重复。
 * 2. `autoMigration` 也能加列，但它把「迁移内容」藏进了注解，
 *    看不到实际执行的 SQL，也覆盖不了这里的删索引 / 建触发器。
 *
 * ## 与实体注解的强耦合（踩过一次）
 * `FocusSessionEntity.source` 上的 `@ColumnInfo(defaultValue = "'TIMER'")`
 * **必须**与本文件的 DEFAULT 字面量完全一致。Room 校验 schema 时默认值也参与比对，
 * 两边不一致同样会抛 `Migration didn't properly handle`。
 * 这条由 `Migration1To2Test` 守着。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `focus_sessions` ADD COLUMN `source` TEXT NOT NULL " +
                "DEFAULT '${SessionSource.DEFAULT.name}'"
        )
        SessionUniquenessGuard.install(db)
    }
}

/** 供 [com.dailyschedule.app.di.DatabaseModule] 一次性注册，避免以后加迁移时漏挂 */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
