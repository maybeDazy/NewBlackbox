package top.niunaijun.blackboxa.container

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabaseLockedException
import android.util.Log
import java.io.IOException
import java.util.UUID

class ContainerRegistry(context: Context) {
    private val dbHelper = ContainerRegistryDbHelper(context)
    private val containerStorage = ContainerStorage(context)

    fun warmUp() {
        dbHelper.writableDatabase.use {
            // Force open to prevent first-launch sqlite race.
        }
    }

    @Throws(IOException::class)
    fun createContainer(packageName: String, displayName: String): ContainerRecord {
        val containerId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        containerStorage.ensureContainerDirectories(containerId)

        val values = ContentValues().apply {
            put("container_id", containerId)
            put("package_name", packageName)
            put("display_name", displayName)
            put("created_at", createdAt)
            put("state", "ACTIVE")
        }

        try {
            val inserted = dbHelper.writableDatabase.insertOrThrow("containers", null, values)
            if (inserted == -1L) {
                throw IOException("Failed to insert container metadata for $containerId")
            }
        } catch (e: SQLiteDatabaseLockedException) {
            throw IOException("Container DB locked while creating $containerId", e)
        }

        return ContainerRecord(
            containerId = containerId,
            packageName = packageName,
            displayName = displayName,
            createdAt = createdAt,
            state = "ACTIVE",
        )
    }

    fun listContainers(packageName: String? = null): List<ContainerRecord> {
        val db = dbHelper.readableDatabase
        val selection = if (packageName != null) "package_name=?" else null
        val args = if (packageName != null) arrayOf(packageName) else null

        return db.query(
            "containers",
            arrayOf("container_id", "package_name", "display_name", "created_at", "state"),
            selection,
            args,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            val results = mutableListOf<ContainerRecord>()
            while (cursor.moveToNext()) {
                try {
                    results.add(
                        ContainerRecord(
                            containerId = cursor.getString(0),
                            packageName = cursor.getString(1),
                            displayName = cursor.getString(2),
                            createdAt = cursor.getLong(3),
                            state = cursor.getString(4),
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Invalid container row skipped: ${e.message}")
                }
            }
            results
        }
    }

    companion object {
        private const val TAG = "ContainerRegistry"
    }
}
