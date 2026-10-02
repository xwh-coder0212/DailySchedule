package com.dailyschedule.app.domain.usecase.project

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.domain.repository.ProjectRepository
import javax.inject.Inject

/**
 * 彻底删除一个项目。
 *
 * 与归档不同：会话/消费记录保留（外键 SET_NULL），但项目本身消失。
 * 这种操作不可恢复（带确认弹窗），所以也就不再独立提供"撤回"。
 */
class DeleteProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {

    suspend operator fun invoke(id: Long): AppResult<Unit> {
        if (projectRepository.getById(id) == null) {
            return AppError.NotFound("项目不存在").asFailure()
        }
        projectRepository.delete(id)
        AppLogger.w(TAG, "彻底删除项目 id=$id（外键 SET_NULL 保留历史）")
        return Unit.asSuccess()
    }

    private companion object {
        const val TAG = "DeleteProject"
    }
}
