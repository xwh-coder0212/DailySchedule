package com.dailyschedule.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.dailyschedule.app.core.time.AndroidClock
import com.dailyschedule.app.core.time.Clock
import com.dailyschedule.app.data.db.ALL_MIGRATIONS
import com.dailyschedule.app.data.db.AppDatabase
import com.dailyschedule.app.data.db.DatabaseCallback
import com.dailyschedule.app.data.db.dao.CategoryDao
import com.dailyschedule.app.data.db.dao.ExpenseDao
import com.dailyschedule.app.data.db.dao.ProjectDao
import com.dailyschedule.app.data.db.dao.SessionDao
import com.dailyschedule.app.data.repository.BackupRepositoryImpl
import com.dailyschedule.app.data.repository.CategoryRepositoryImpl
import com.dailyschedule.app.data.repository.ExpenseRepositoryImpl
import com.dailyschedule.app.data.repository.PreferencesRepositoryImpl
import com.dailyschedule.app.data.repository.ProjectRepositoryImpl
import com.dailyschedule.app.data.repository.SessionRepositoryImpl
import com.dailyschedule.app.domain.repository.BackupRepository
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindClock(impl: AndroidClock): Clock
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("dailyschedule_prefs") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "dailyschedule.db",
    )
        .addCallback(DatabaseCallback())
        .addMigrations(*ALL_MIGRATIONS)
        // 永远不用 fallbackToDestructiveMigration ——
        // 本地数据是用户的事实记录，宁可迁移失败弹错，也不能被静默清空。
        .build()

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    @Singleton
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
}
