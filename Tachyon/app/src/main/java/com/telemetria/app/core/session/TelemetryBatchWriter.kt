package com.telemetria.app.core.session

import com.telemetria.app.core.database.dao.TelemetryPointDao
import com.telemetria.app.core.database.entity.TelemetryPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TelemetryBatchWriter(
    private val pointDao: TelemetryPointDao,
    private val scope: CoroutineScope,
    private val batchSize: Int = 30,
    private val flushIntervalMillis: Long = 500L
) {
    private val channel = Channel<TelemetryPointEntity>(capacity = 1000)
    private val mutex = Mutex()
    private val memoryBuffer = mutableListOf<TelemetryPointEntity>()
    private var workerJob: Job? = null

    init {
        startWorker()
    }

    private fun startWorker() {
        workerJob = scope.launch(Dispatchers.IO) {
            var lastFlushTime = System.currentTimeMillis()

            while (isActive) {
                // Read from channel with timeout to trigger periodic flush
                val point = try {
                    kotlinx.coroutines.withTimeoutOrNull(flushIntervalMillis) {
                        channel.receive()
                    }
                } catch (e: Exception) {
                    null
                }

                if (point != null) {
                    mutex.withLock {
                        memoryBuffer.add(point)
                    }
                }

                val now = System.currentTimeMillis()
                val shouldFlushBySize = mutex.withLock { memoryBuffer.size >= batchSize }
                val shouldFlushByTime = (now - lastFlushTime >= flushIntervalMillis) && mutex.withLock { memoryBuffer.isNotEmpty() }

                if (shouldFlushBySize || shouldFlushByTime) {
                    flushInternal()
                    lastFlushTime = System.currentTimeMillis()
                }
            }
        }
    }

    fun enqueue(point: TelemetryPointEntity) {
        channel.trySend(point)
    }

    suspend fun flush() {
        mutex.withLock {
            flushInternalUnsafe()
        }
    }

    private suspend fun flushInternal() {
        mutex.withLock {
            flushInternalUnsafe()
        }
    }

    private suspend fun flushInternalUnsafe() {
        if (memoryBuffer.isEmpty()) return
        val batch = memoryBuffer.toList()
        memoryBuffer.clear()
        try {
            pointDao.insertPoints(batch)
        } catch (e: Exception) {
            e.printStackTrace()
            // If failed, reinsert at beginning or log
        }
    }

    suspend fun close() {
        channel.close()
        workerJob?.cancel()
        flush()
    }
}

