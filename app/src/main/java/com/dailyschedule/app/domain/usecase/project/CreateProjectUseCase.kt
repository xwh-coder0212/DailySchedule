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

class CreateProjectUseCase @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        name: String,
        iconName: String,
        colorHex: String,
        dailyTargetMinutes: Int? = null,
    ): AppResult<Project> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return AppError.Validation("项目名不能为空").asFailure()
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return AppError.Validation("项目名过长").asFailure()
        }
        if (!isValidColorHex(colorHex)) {
            return AppError.Validation("颜色格式不正确，应为 #RRGGBB").asFailure()
        }
        if (dailyTargetMinutes != null && dailyTargetMinutes !in 1..(24 * 60)) {
            return AppError.Validation("日目标应在 1~1440 分钟之间").asFailure()
        }

        val now = clock.wallClockMillis()
        val project = Project(
            name = trimmed,
            iconName = iconName,
            colorHex = colorHex,
            dailyTargetMinutes = dailyTargetMinutes,
            createdAt = now,
            updatedAt = now,
        )
        val id = projectRepository.create(project)
        AppLogger.i(TAG, "创建项目 id=$id name=$trimmed")
        return project.copy(id = id).asSuccess()
    }

    private fun isValidColorHex(hex: String): Boolean =
        hex.length == 7 && hex[0] == '#' && hex.substring(1).all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }

    private companion object {
        const val TAG = "CreateProject"
        const val MAX_NAME_LENGTH = 20
    }
}
