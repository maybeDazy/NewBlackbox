package top.niunaijun.blackboxa.container

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock

class ContainerStorage(private val context: Context) {

    @Throws(IOException::class)
    fun ensureContainerDirectories(containerId: String): File {
        val lockFile = File(ContainerPaths.containersRoot(context), ".lock")
        ensureDir(lockFile.parentFile)

        RandomAccessFile(lockFile, "rw").channel.use { channel ->
            val lock: FileLock = channel.lock()
            try {
                val root = ContainerPaths.containerRoot(context, containerId)
                ensureDir(root)
                ensureDir(ContainerPaths.virtualDataRoot(context, containerId))
                ensureDir(ContainerPaths.virtualDeRoot(context, containerId))
                ensureDir(ContainerPaths.webViewRoot(context, containerId))
                return root
            } finally {
                lock.release()
            }
        }
    }

    @Throws(IOException::class)
    private fun ensureDir(dir: File) {
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Cannot create directory: ${dir.absolutePath}")
        }
        if (!dir.isDirectory) {
            throw IOException("Path is not a directory: ${dir.absolutePath}")
        }
        if (!dir.canWrite()) {
            throw IOException("Directory not writable: ${dir.absolutePath}")
        }
    }
}
