package com.dailyschedule.app.domain.usecase.bootstrap

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.domain.repository.PreferencesRepository
import java.time.ZoneId
import javax.inject.Inject

/**
 * 启动时检测时区变化，必要时记入 lastKnownZoneId 并返回是否变化。
 *
 * 调用方按返回值决定是否弹一次"时区变化"提示。
 * 不存业务日期的冗余字段 —— 跨时区长期记录的需求概率极低，检测到就说一句。
 */
class DetectTimezoneChangeUseCase @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) {

    suspend operator fun invoke(): Boolean {
        val current = ZoneId.systemDefault().id
        val changed = preferencesRepository.updateKnownZoneId(current)
        if (changed) AppLogger.w(TAG, "时区变化: ${preferencesRepository.get().lastKnownZoneId} → $current")
        return changed
    }

    private companion object {
        const val TAG = "DetectTimezoneChange"
    }
}
