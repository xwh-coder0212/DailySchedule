package com.dailyschedule.app.core.transfer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 备份文件的编解码。
 *
 * ## 版本是两套，别混
 * - [SCHEMA_VERSION] 描述**这份 JSON 的结构**。它变，说明字段增删改名了。
 * - `BackupDocument.dbVersion` 描述**写出这份文件时的 Room schema**，
 *   只用于排查问题与人类阅读。
 *
 * 恢复时的判断只看 `schemaVersion`：一个 v1 的备份文件，无论它来自哪个
 * db 版本，结构都是 v1，都能读。反过来，`dbVersion` 比当前大并不代表文件
 * 读不了 —— 用 dbVersion 当门禁会把「同一份 v1 文件在新版本 App 上恢复」
 * 这种完全正常的场景挡掉。
 *
 * ## Json 配置逐条都是刻意的
 * - `prettyPrint = true` —— 用户可能自己打开看，甚至是自己改一个字再导回来。
 *   单行几 MB 的 JSON 谁也看不懂。
 * - `encodeDefaults = true` —— 默认值也写出来。省掉它们会让文件变成
 *   「有的记录有 `needsReview` 字段、有的没有」，看着像两种数据。
 * - `explicitNulls = true` —— null 明写，不靠"字段缺失"暗示 null。
 * - `ignoreUnknownKeys = false` —— **不认识就报错**。这条不是保守，是必须：
 *   打开它以后，把别的 App 的 JSON 丢进来会静默解码成一个字段残缺的对象。
 *   上面 [BackupDocument] 为什么不给字段默认值，是同一个道理。
 *
 * 枚举（`status` / `mode` / `source` / `type`）走 kotlinx.serialization 的
 * 默认枚举序列化器：**未知值直接抛异常**，不会像 Dao 层那样回落到默认值。
 * 这是刻意的差别 —— Dao 读一列失败不该让整行读不出来，而导入一份
 * 含未知状态的备份必须当场停下，因为它后面就是一次清库。
 */
object BackupCodec {

    /** 文件身份标识。写进 `format` 字段，导入时先比对它 */
    const val FORMAT_ID = "dailyschedule.backup"

    /** 当前 JSON 结构版本。字段有增删改名就 +1，并在 [BackupValidator] 里加迁移 */
    const val SCHEMA_VERSION = 1

    /** 建议文件名里的扩展名（不含点） */
    const val FILE_EXTENSION = "json"

    /**
     * 读取时允许的最大字节数。
     *
     * 本地数据量级：一条会话约 200 字节，一万条约 2 MB。给到 64 MB 已经很宽松，
     * 目的是挡住「用户误选了一个几 GB 的文件」——那种情况下先 OOM 再报错，
     * 用户看到的是闪退而不是"选错文件了"。
     */
    const val MAX_BYTES: Long = 64L * 1024 * 1024

    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    fun encode(document: BackupDocument): String = json.encodeToString(document)

    fun decode(text: String): BackupDocument = json.decodeFromString(text)
}
