package com.dailyschedule.app.domain.usecase.bootstrap

import com.dailyschedule.app.core.log.AppLogger
import com.dailyschedule.app.domain.repository.CategoryRepository
import javax.inject.Inject

/**
 * 首次启动时种子预置数据。
 *
 * MVP 只 seed 消费分类。预置项目不种 —— "考研数学/英语/408/政治"是个性化的，
 * 第一次打开让用户自己建，反而能让他建立项目概念。
 */
class EnsureSeedDataUseCase
    @Inject
    constructor(
        private val categoryRepository: CategoryRepository,
    ) {
        suspend operator fun invoke(nameResolver: (Int) -> String) {
            categoryRepository.ensureSeeded(nameResolver)
            AppLogger.i(TAG, "种子写入完成")
        }

        private companion object {
            const val TAG = "EnsureSeedData"
        }
    }
