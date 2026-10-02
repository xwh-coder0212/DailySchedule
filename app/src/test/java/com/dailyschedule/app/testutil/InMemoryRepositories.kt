package com.dailyschedule.app.testutil

import com.dailyschedule.app.core.model.ExpenseType
import com.dailyschedule.app.core.model.SessionStatus
import com.dailyschedule.app.domain.model.Category
import com.dailyschedule.app.domain.model.CategoryAmount
import com.dailyschedule.app.domain.model.Expense
import com.dailyschedule.app.domain.model.FocusSession
import com.dailyschedule.app.domain.model.Project
import com.dailyschedule.app.domain.model.ProjectDuration
import com.dailyschedule.app.domain.repository.CategoryRepository
import com.dailyschedule.app.domain.repository.ExpenseRepository
import com.dailyschedule.app.domain.repository.PreferencesRepository
import com.dailyschedule.app.domain.repository.ProjectRepository
import com.dailyschedule.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 内存版 Repository。覆盖所有测试需要的接口，足以跑通当前 UseCase 单测。 */
class InMemoryRepositories {

    private val categories = MutableStateFlow<List<Category>>(emptyList())
    private val projects = MutableStateFlow<List<Project>>(emptyList())
    private val sessions = MutableStateFlow<List<FocusSession>>(emptyList())
    private val expenses = MutableStateFlow<List<Expense>>(emptyList())
    private var nextId = 1L

    private fun nextId(): Long = nextId.also { nextId++ }

    val categoryRepo = object : CategoryRepository {
        override fun observeAll(): Flow<List<Category>> = categories
        override fun observeEnabled(): Flow<List<Category>> =
            categories.map { it.filter { c -> c.isEnabled } }
        override suspend fun getById(id: Long): Category? = categories.value.firstOrNull { it.id == id }
        override suspend fun create(category: Category): Long {
            val saved = category.copy(id = nextId())
            categories.value = categories.value + saved
            return saved.id
        }
        override suspend fun update(category: Category) {
            categories.value = categories.value.map { if (it.id == category.id) category else it }
        }
        override suspend fun reorder(orderedIds: List<Long>) {
            val map = categories.value.associateBy { it.id }
            categories.value = orderedIds.mapIndexedNotNull { i, id -> map[id]?.copy(sortOrder = i) }
        }
        override suspend fun delete(id: Long): Boolean {
            if (expenses.value.any { it.categoryId == id }) return false
            val target = categories.value.firstOrNull { it.id == id } ?: return false
            if (target.isPreset) return false
            categories.value = categories.value - target
            return true
        }
        override suspend fun countExpenses(categoryId: Long): Int =
            expenses.value.count { it.categoryId == categoryId }
        override suspend fun ensureSeeded(nameResolver: (Int) -> String) {
            if (categories.value.isNotEmpty()) return
            // 真实种子的子集，仅供测试
            categories.value = listOf(
                Category(1, "餐饮", "ic_food", "#F4A261", true, true, 0),
                Category(2, "交通", "ic_transit", "#2A9D8F", true, true, 1),
            )
        }
    }

    val projectRepo = object : ProjectRepository {
        override fun observeAll(): Flow<List<Project>> = projects
        override fun observeActive(): Flow<List<Project>> = projects.map { it.filter { p -> !p.isArchived } }
        override fun observeArchived(): Flow<List<Project>> = projects.map { it.filter { p -> p.isArchived } }
        override suspend fun getById(id: Long): Project? = projects.value.firstOrNull { it.id == id }
        override suspend fun create(project: Project): Long {
            val saved = project.copy(id = nextId())
            projects.value = projects.value + saved
            return saved.id
        }
        override suspend fun update(project: Project) {
            projects.value = projects.value.map { if (it.id == project.id) project else it }
        }
        override suspend fun setArchived(id: Long, archived: Boolean) {
            projects.value = projects.value.map {
                if (it.id == id) it.copy(isArchived = archived) else it
            }
        }
        override suspend fun reorder(orderedIds: List<Long>) {
            val map = projects.value.associateBy { it.id }
            projects.value = orderedIds.mapIndexedNotNull { i, id -> map[id]?.copy(sortOrder = i) }
        }
        override suspend fun delete(id: Long) {
            projects.value = projects.value.filterNot { it.id == id }
        }
    }

    val sessionRepo = object : SessionRepository {
        override fun observeActive(): Flow<FocusSession?> = sessions.map { list ->
            list.firstOrNull { it.status == SessionStatus.RUNNING || it.status == SessionStatus.PAUSED }
        }
        override suspend fun getActive(): FocusSession? = sessions.value.firstOrNull {
            it.status == SessionStatus.RUNNING || it.status == SessionStatus.PAUSED
        }
        override suspend fun getById(id: Long): FocusSession? = sessions.value.firstOrNull { it.id == id }
        override fun observeTimeline(startMs: Long, endMs: Long): Flow<List<FocusSession>> = sessions.map { list ->
            list.filter { it.status == SessionStatus.COMPLETED && it.startWallClockMs in startMs until endMs }
                .sortedBy { it.startWallClockMs }
        }
        override fun observeInRange(startMs: Long, endMs: Long): Flow<List<FocusSession>> = sessions.map { list ->
            list.filter { it.status == SessionStatus.COMPLETED && it.startWallClockMs in startMs until endMs }
                .sortedByDescending { it.startWallClockMs }
        }
        override fun observeTotalDuration(startMs: Long, endMs: Long): Flow<Long> = sessions.map { list ->
            list.filter { it.status == SessionStatus.COMPLETED && it.startWallClockMs in startMs until endMs }
                .sumOf { it.durationMs ?: 0L }
        }
        override fun observeCompletedCount(startMs: Long, endMs: Long): Flow<Int> = sessions.map { list ->
            list.count { it.status == SessionStatus.COMPLETED && it.startWallClockMs in startMs until endMs }
        }
        override fun observeCompletedCountOfProject(
            projectId: Long,
            startMs: Long,
            endMs: Long,
        ): Flow<Int> = sessions.map { list ->
            list.count {
                it.status == SessionStatus.COMPLETED &&
                    it.projectId == projectId &&
                    it.startWallClockMs in startMs until endMs
            }
        }
        override fun observeDurationByProject(startMs: Long, endMs: Long): Flow<List<ProjectDuration>> =
            sessions.map { list ->
                list.filter { it.status == SessionStatus.COMPLETED && it.startWallClockMs in startMs until endMs }
                    .groupBy { it.projectId }
                    .mapNotNull { (pid, ss) ->
                        val p = projects.value.firstOrNull { it.id == pid } ?: return@mapNotNull null
                        ProjectDuration(p.id, p.name, p.colorHex, p.iconName, ss.sumOf { it.durationMs ?: 0L })
                    }
                    .sortedByDescending { it.totalDurationMs }
            }
        override fun observeTotalDurationOfProject(
            projectId: Long,
            startMs: Long,
            endMs: Long,
        ): Flow<Long> = sessions.map { list ->
            list.filter { it.status == SessionStatus.COMPLETED && it.projectId == projectId && it.startWallClockMs in startMs until endMs }
                .sumOf { it.durationMs ?: 0L }
        }
        override fun observeNeedsReview(): Flow<List<FocusSession>> = sessions.map { it.filter { s -> s.needsReview } }
        override fun observeByProject(projectId: Long, limit: Int): Flow<List<FocusSession>> = sessions.map { list ->
            list.filter { it.projectId == projectId && it.status == SessionStatus.COMPLETED }
                .sortedByDescending { it.startWallClockMs }
                .take(limit)
        }
        override suspend fun insert(session: FocusSession): Long {
            val saved = session.copy(id = nextId())
            sessions.value = sessions.value + saved
            return saved.id
        }
        override suspend fun update(session: FocusSession) {
            sessions.value = sessions.value.map { if (it.id == session.id) session else it }
        }
        override suspend fun delete(id: Long): Boolean {
            val target = sessions.value.firstOrNull { it.id == id } ?: return false
            // 与真实 DAO 的 `status = 'COMPLETED'` 条件保持一致：
            // 活动会话有前台服务挂着，删掉它会让通知永远停在"专注中"。
            if (target.status != SessionStatus.COMPLETED) return false
            sessions.value = sessions.value - target
            return true
        }
        override suspend fun findInconsistent(): List<FocusSession> = emptyList()
    }

    val expenseRepo = object : ExpenseRepository {
        override fun observeInRange(type: ExpenseType, startMs: Long, endMs: Long): Flow<List<Expense>> =
            expenses.map { list -> list.filter { it.type == type && it.occurredAt in startMs until endMs }
                .sortedByDescending { it.occurredAt } }
        override fun observeRecent(type: ExpenseType, limit: Int): Flow<List<Expense>> =
            expenses.map { list -> list.filter { it.type == type }
                .sortedByDescending { it.occurredAt }.take(limit) }
        override fun observeTotalCents(type: ExpenseType, startMs: Long, endMs: Long): Flow<Long> =
            expenses.map { list -> list.filter { it.type == type && it.occurredAt in startMs until endMs }
                .sumOf { it.amountCents } }
        override fun observeTotalByCategory(
            type: ExpenseType,
            startMs: Long,
            endMs: Long,
        ): Flow<List<CategoryAmount>> = expenses.map { list ->
            list.filter { it.type == type && it.occurredAt in startMs until endMs }
                .groupBy { it.categoryId }
                .mapNotNull { (cid, es) ->
                    val c = categories.value.firstOrNull { it.id == cid } ?: return@mapNotNull null
                    CategoryAmount(c.id, c.name, c.colorHex, c.iconName, es.sumOf { it.amountCents })
                }
                .sortedByDescending { it.totalCents }
        }
        override fun observeTotalOfProject(projectId: Long, type: ExpenseType): Flow<Long> =
            expenses.map { list -> list.filter { it.projectId == projectId && it.type == type }
                .sumOf { it.amountCents } }
        override fun observeOfProject(projectId: Long): Flow<List<Expense>> =
            expenses.map { list -> list.filter { it.projectId == projectId }
                .sortedByDescending { it.occurredAt } }
        override fun observeAll(): Flow<List<Expense>> =
            expenses.map { list -> list.sortedBy { it.occurredAt } }
        override suspend fun getById(id: Long): Expense? = expenses.value.firstOrNull { it.id == id }
        override suspend fun add(expense: Expense): Long {
            val saved = expense.copy(id = nextId())
            expenses.value = expenses.value + saved
            return saved.id
        }
        override suspend fun update(expense: Expense) {
            expenses.value = expenses.value.map { if (it.id == expense.id) expense else it }
        }
        override suspend fun delete(id: Long) {
            expenses.value = expenses.value.filterNot { it.id == id }
        }
        override suspend fun count(): Int = expenses.value.size
    }

    val preferencesRepo = object : PreferencesRepository {
        private val state = MutableStateFlow(com.dailyschedule.app.domain.model.AppPreferences())
        override fun observe(): Flow<com.dailyschedule.app.domain.model.AppPreferences> = state
        override suspend fun get(): com.dailyschedule.app.domain.model.AppPreferences = state.value
        override suspend fun setDayStartHour(hour: Int) { state.value = state.value.copy(dayStartHour = hour) }
        override suspend fun setWeekStartDay(day: Int) { state.value = state.value.copy(weekStartDay = day) }
        override suspend fun setLastUsedProjectId(projectId: Long?) {
            state.value = state.value.copy(lastUsedProjectId = projectId)
        }
        override suspend fun setDefaultPomodoroMinutes(minutes: Int) {
            state.value = state.value.copy(defaultPomodoroMinutes = minutes)
        }
        override suspend fun setThemeMode(mode: com.dailyschedule.app.core.model.ThemeMode) {
            state.value = state.value.copy(themeMode = mode)
        }
        override suspend fun setThemePack(pack: com.dailyschedule.app.core.model.ThemePack) {
            state.value = state.value.copy(themePack = pack)
        }
        override suspend fun setDynamicColor(enabled: Boolean) {
            state.value = state.value.copy(dynamicColor = enabled)
        }
        override suspend fun updateKnownZoneId(zoneId: String): Boolean {
            val changed = state.value.lastKnownZoneId != zoneId
            state.value = state.value.copy(lastKnownZoneId = zoneId)
            return changed
        }
        override suspend fun replaceAll(preferences: com.dailyschedule.app.domain.model.AppPreferences) {
            state.value = preferences
        }
    }
}
