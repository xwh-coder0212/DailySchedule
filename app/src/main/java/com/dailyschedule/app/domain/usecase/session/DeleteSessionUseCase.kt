package com.dailyschedule.app.domain.usecase.session

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 删除一条专注记录。
 *
 * 先读一次再删，而不是直接用 `delete` 的返回值判成败：
 * 那个布尔同时表示"不存在"和"是活动会话"，而这两种情况该给用户看的话不一样 ——
 * 一个说"这条已经不在了"，一个说"正在计时，先结束它"。
 * 多一次查询换一句准确的话，值得。
 */
class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {

    suspend operator fun invoke(sessionId: Long): AppResult<Unit> {
        val existing = sessionRepository.getById(sessionId)
            ?: return AppError.NotFound("这条专注记录不存在，可能已被删除").asFailure()

        if (existing.status != SessionStatus.COMPLETED) {
            return AppError.Validation("正在进行的计时不能删除，请先结束它").asFailure()
        }

        if (!sessionRepository.delete(sessionId)) {
            // 走到这里说明上一步读到的行在删除前被别处改回了活动态（极端竞态）。
            // 不吞掉：返回失败让界面如实说"没删掉"。
            return AppError.Unknown("删除失败，请重试").asFailure()
        }

        AppLogger.i(TAG, "删记录 id=$sessionId")
        return Unit.asSuccess()
    }

    private companion object {
        const val TAG = "DeleteSession"
    }
}
