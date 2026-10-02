package com.dailyschedule.app.domain.usecase.project

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.result.AppResult
import com.dailyschedule.app.core.result.asSuccess
import com.dailyschedule.app.domain.repository.ProjectRepository
import javax.inject.Inject

class ArchiveProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
) {

    suspend operator fun invoke(id: Long, archived: Boolean = true): AppResult<Unit> {
        projectRepository.setArchived(id, archived)
        AppLogger.i(TAG, "归档=$archived id=$id")
        return Unit.asSuccess()
    }

    private companion object {
        const val TAG = "ArchiveProject"
    }
}
