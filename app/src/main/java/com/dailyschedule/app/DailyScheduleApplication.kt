package com.dailyschedule.app

import android.app.Application
import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.core.log.CrashHandler
import com.dailyschedule.app.domain.usecase.bootstrap.AppStartupUseCase
import com.dailyschedule.app.domain.usecase.bootstrap.EnsureSeedDataUseCase
import com.dailyschedule.app.timer.TimerNotifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DailyScheduleApplication : Application() {
    @Inject lateinit var appStartup: AppStartupUseCase

    @Inject lateinit var ensureSeedData: EnsureSeedDataUseCase

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        // 崩溃落盘必须最先装：后面任何初始化出问题才有线索可查
        CrashHandler(this).install()

        // 通知通道：保证 ID 与名称稳定，删除会丢失用户当前的 channel 偏好
        TimerNotifications.ensureChannels(this)

        AppLogger.i(TAG, "Application onCreate")

        // 启动恢复 + 种子写入，全部异步，不阻塞冷启动
        appScope.launch {
            runCatching {
                ensureSeedData { resId ->
                    getString(resId)
                }
                appStartup()
            }.onFailure { t ->
                AppLogger.e(TAG, "启动恢复失败", t)
            }
        }
    }

    private companion object {
        const val TAG = "DailyScheduleApp"
    }
}
