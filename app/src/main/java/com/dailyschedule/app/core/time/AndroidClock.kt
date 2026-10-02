package com.dailyschedule.app.core.time

import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/** 真实时间源。生产环境唯一实现。 */
@Singleton
class AndroidClock
    @Inject
    constructor() : Clock {
        override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

        override fun wallClockMillis(): Long = System.currentTimeMillis()
    }
