package com.dccleaner.app.desktop

import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption

internal class DesktopSingleInstanceLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock
) : AutoCloseable {
    override fun close() {
        runCatching { lock.release() }
        channel.close()
    }

    companion object {
        fun tryAcquire(root: File = DesktopPaths.appDataDir): DesktopSingleInstanceLock? {
            if (!root.exists() && !root.mkdirs()) {
                error("Could not create ${root.absolutePath}")
            }
            val channel = FileChannel.open(
                File(root, "dccleaner.lock").toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
            )
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                channel.close()
                return null
            }
            return DesktopSingleInstanceLock(channel, lock)
        }
    }
}
