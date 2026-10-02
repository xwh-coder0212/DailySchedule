package com.dailyschedule.app.domain.usecase.project

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 按新顺序重排待办。
 *
 * ## 为什么校验"列表必须与库里的待办完全一致"
 * `reorder(ids)` 的语义是"把 id 为 ids[i] 的那条 sortOrder 改成 i"。
 * 如果传进来的列表少了一条（界面数据陈旧、另一处刚删/建过），
 * 少的那条会保留旧 sortOrder，从而插到别的待办中间 —— 用户看到的是
 * "我明明只是把数学挪到最上面，怎么政治跑到了第二"。
 *
 * 与其静默产生一个错序，不如拒绝，让界面重新读一次列表再排。
 */
class ReorderProjectsUseCase
    @Inject
    constructor(
        private val projectRepository: ProjectRepository,
    ) {
        suspend operator fun invoke(orderedIds: List<Long>): AppResult<Unit> {
            if (orderedIds.isEmpty()) return Unit.asSuccess()

            if (orderedIds.size != orderedIds.distinct().size) {
                return AppError.Validation("排序列表里有重复的待办").asFailure()
            }

            val existingIds = projectRepository.observeAll().first().map { it.id }.toSet()
            if (orderedIds.toSet() != existingIds) {
                return AppError.Validation("待办列表已经变化，请重新打开排序").asFailure()
            }

            projectRepository.reorder(orderedIds)
            AppLogger.i(TAG, "重排 ${orderedIds.size} 个待办")
            return Unit.asSuccess()
        }

        private companion object {
            const val TAG = "ReorderProjects"
        }
    }
