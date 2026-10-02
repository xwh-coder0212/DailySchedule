package com.dailyschedule.app.core.result

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppResultTest {
    @Test
    fun `appCatching 成功时返回 Success`() {
        val result = appCatching { 42 }

        assertThat(result).isInstanceOf(AppResult.Success::class.java)
        assertThat((result as AppResult.Success).value).isEqualTo(42)
    }

    @Test
    fun `appCatching 捕获 IllegalArgumentException 转为 Validation`() {
        val result = appCatching { throw IllegalArgumentException("金额必须大于 0") }

        val error = (result as AppResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Validation::class.java)
        assertThat(error.message).isEqualTo("金额必须大于 0")
        assertThat(error.cause).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `appCatching 捕获 IOException 转为 Storage`() {
        val result = appCatching { throw java.io.IOException("disk full") }

        assertThat((result as AppResult.Failure).error).isInstanceOf(AppError.Storage::class.java)
    }

    @Test
    fun `appCatching 未知异常转为 Unknown 而不是被吞掉`() {
        val result = appCatching { error("boom") }

        val error = (result as AppResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        // Unknown 出现说明某处 catch 写得不够具体，cause 必须保留以便排查
        assertThat(error.cause).isNotNull()
    }

    @Test
    fun `mapApp 只在成功时变换`() {
        val ok: AppResult<Int> = 10.asSuccess()
        val fail: AppResult<Int> = AppError.NotFound("找不到会话").asFailure()

        assertThat(ok.mapApp { it * 2 }.getOrNull()).isEqualTo(20)
        assertThat(fail.mapApp { it * 2 }.getOrNull()).isNull()
    }

    @Test
    fun `onFailure 只在失败时触发且保持原值`() {
        var captured: AppError? = null
        val result: AppResult<Int> = AppError.Database("constraint").asFailure()

        val returned = result.onFailure { captured = it }
        result.onSuccess { error("成功分支不该被调用") }

        assertThat(captured).isInstanceOf(AppError.Database::class.java)
        assertThat(returned).isSameInstanceAs(result)
    }

    @Test
    fun `getOrElse 对失败返回默认值`() {
        val fail: AppResult<Int> = AppError.Timer("非法状态转换").asFailure()

        assertThat(fail.getOrElse(-1)).isEqualTo(-1)
        assertThat(1.asSuccess().getOrElse(-1)).isEqualTo(1)
    }

    @Test
    fun `onSuccess 不会被失败结果触发`() {
        var called = false
        val fail: AppResult<Int> = AppError.Validation("bad").asFailure()
        fail.onSuccess { called = true }

        assertThat(called).isFalse()
    }
}
