package com.dailyschedule.app.domain.usecase.session

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import javax.inject.Inject

/**
 * 补录一段已经发生过的专注时间。
 *
 * ## 它和「开始计时」是两条完全不同的路径
 * 计时是**记录过程**：先知道开始，结束时才知道时长。
 * 补录是**记录结论**：用户直接给时长，时间戳是事后补出来的。
 * 所以补录不走 `TimerStartUseCase` 那套（那套会拉起前台服务、写 lastUsedProjectId、
 * 拒绝已有活动会话），它只写一条已结束的历史记录，并且打上 `MANUAL` 来源标记。
 *
 * ## 补录为什么也要严格校验
 * 补录允许用户「凭记忆填数字」，但不允许填出**不可能的数字**。
 * 不校验的后果是统计里出现「一天 37 小时」这种数据，而这类脏数据一旦落库，
 * 后面所有的周/月汇总都不可信，且很难倒查出是哪一条造成的。
 */
class RecordManualSessionUseCase
    @Inject
    constructor(
        private val sessionRepository: SessionRepository,
        private val projectRepository: ProjectRepository,
        private val clock: Clock,
    ) {
        /**
         * 参数用一个对象装起来，而不是四个并列参数。
         * `durationMs` 与 `startWallClockMs` 都是 Long，并列传参时写反了不会有任何提示 ——
         * 一个 90 分钟的记录会变成 90 毫秒的、落在 1970 年附近的记录。
         */
        data class Request(
            /** null = 不归属任何待办（补录时允许） */
            val projectId: Long?,
            val durationMs: Long,
            /** 由界面算好：所选日期的锚点时刻往前推 durationMs */
            val startWallClockMs: Long,
            val note: String? = null,
        )

        suspend operator fun invoke(request: Request): AppResult<FocusSession> {
            validate(request)?.let { return it.asFailure() }

            val now = clock.wallClockMillis()
            val session =
                FocusSession.recorded(
                    projectId = request.projectId,
                    durationMs = request.durationMs,
                    startWallClockMs = request.startWallClockMs,
                    // 只把「全是空白」的备注归一成 null，不做其它加工 ——
                    // 用户写的备注原样保存，App 不替用户润色事实
                    note = request.note?.takeIf { it.isNotBlank() },
                    nowWallClockMs = now,
                )
            val id = sessionRepository.insert(session)
            AppLogger.i(
                TAG,
                "补录专注记录 id=$id 时长=${request.durationMs}ms 待办=${request.projectId}",
            )
            return session.copy(id = id).asSuccess()
        }

        /** 返回非 null 即为校验失败原因 */
        private suspend fun validate(request: Request): AppError? {
            // 时长上下限与「修改时长」共用同一套，避免两处漂移
            SessionDurationRules.validate(request.durationMs)?.let { return it }

            // 用「结束时刻」而不是「开始时刻」判断是否越界：
            // 开始时刻在现在、时长 3 小时，同样是一条落在未来的记录
            val endWallClockMs = request.startWallClockMs + request.durationMs
            if (endWallClockMs > clock.wallClockMillis() + SessionDurationRules.FUTURE_TOLERANCE_MS) {
                return AppError.Validation("补录的结束时间不能晚于现在")
            }

            if (request.projectId != null && projectRepository.getById(request.projectId) == null) {
                return AppError.NotFound("要补录的待办不存在，可能已被删除")
            }

            return null
        }

        private companion object {
            const val TAG = "RecordManualSession"
        }
    }
