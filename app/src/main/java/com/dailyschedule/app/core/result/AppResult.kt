package com.dailyschedule.app.core.result

/**
 * 结果容器。用自定义 sealed 而非 Kotlin [Result]，因为失败原因是 [AppError]（非 Throwable）。
 *
 * 用法：
 * ```kotlin
 * val result = appCatching { dao.insert(session) }
 * result.onFailure { AppLogger.e("Session", "insert failed", it.cause) }
 * ```
 *
 * 挂起场景不要在这里包一层：取消异常（CancellationException）不能被吞掉，
 * 应在 UseCase 内部显式 try/catch 后转 asFailure()。
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: AppError) : AppResult<Nothing>
}

fun <T> T.asSuccess(): AppResult<T> = AppResult.Success(this)

fun AppError.asFailure(): AppResult<Nothing> = AppResult.Failure(this)

inline fun <T> appCatching(block: () -> T): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (t: Throwable) {
        AppResult.Failure(AppError.from(t))
    }

inline fun <T, R> AppResult<T>.mapApp(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(value)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

/** 失败时取默认值。仅用于"失败不影响主流程"的场合（如读取偏好）。 */
fun <T> AppResult<T>.getOrElse(defaultValue: T): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> defaultValue
    }

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
