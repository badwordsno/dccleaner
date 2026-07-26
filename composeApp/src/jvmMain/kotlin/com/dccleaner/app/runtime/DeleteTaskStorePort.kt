package com.dccleaner.app.runtime

import com.dccleaner.app.model.CollectedPost
import com.dccleaner.app.model.DeleteTaskProgress
import com.dccleaner.app.model.DeleteTaskState

interface DeleteTaskStorePort {
    fun getAll(): List<DeleteTaskProgress>
    fun getForLogin(loginId: String): List<DeleteTaskProgress>
    fun get(taskId: String): DeleteTaskProgress?
    fun save(task: DeleteTaskProgress): Boolean
    fun updateState(
        taskId: String,
        state: DeleteTaskState,
        message: String,
        captchaRequired: Boolean = false
    ): DeleteTaskProgress?
    fun remove(taskId: String): Boolean
    fun saveQueue(taskId: String, posts: List<CollectedPost>): Boolean
    fun loadRemainingQueue(taskId: String, cursor: Int): List<CollectedPost>?
    fun hasQueue(taskId: String): Boolean
    fun deleteQueue(taskId: String): Boolean
    fun saveCollectedPage(taskId: String, page: Int, posts: List<CollectedPost>): Boolean
    fun findNextMissingCollectedPage(taskId: String, totalPages: Int): Int
    fun loadCollectedPages(taskId: String, totalPages: Int): List<CollectedPost>?
    fun deleteCollectedPages(taskId: String): Boolean
}

fun interface RuntimeLogSink {
    suspend fun addLog(tag: String, message: String)
}

object NoOpRuntimeLogSink : RuntimeLogSink {
    override suspend fun addLog(tag: String, message: String) = Unit
}

interface RuntimeNotifier {
    fun notify(title: String, message: String)
}

object NoOpRuntimeNotifier : RuntimeNotifier {
    override fun notify(title: String, message: String) = Unit
}
