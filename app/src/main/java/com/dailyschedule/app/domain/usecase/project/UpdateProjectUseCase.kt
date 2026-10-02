package com.dailyschedule.app.domain.usecase.project

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppError
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asFailure
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.domain.repository.ProjectRepository
import javax.inject.Inject

class UpdateProjectUseCase
    @Inject
    constructor(
        private val projectRepository: ProjectRepository,
        private val clock: Clock,
    ) {
        suspend operator fun invoke(updated: Project): AppResult<Unit> {
            if (updated.name.trim().isEmpty()) {
                return AppError.Validation("项目名不能为空").asFailure()
            }
            if (projectRepository.getById(updated.id) == null) {
                return AppError.NotFound("项目不存在").asFailure()
            }
            val toSave = updated.copy(updatedAt = clock.wallClockMillis())
            projectRepository.update(toSave)
            AppLogger.i(TAG, "改项目 id=${updated.id}")
            return Unit.asSuccess()
        }

        private companion object {
            const val TAG = "UpdateProject"
        }
    }
