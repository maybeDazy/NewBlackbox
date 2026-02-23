package top.niunaijun.blackboxa.container

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ContainerRegistryDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS containers (
                container_id TEXT PRIMARY KEY,
                package_name TEXT NOT NULL,
                display_name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                state TEXT NOT NULL DEFAULT 'ACTIVE'
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_containers_package_name ON containers(package_name)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_containers_package_name ON containers(package_name)"
            )
        }
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA journal_mode=WAL")
        db.execSQL("PRAGMA busy_timeout=3000")
    }

    companion object {
        private const val DATABASE_NAME = "container_registry.db"
        private const val DATABASE_VERSION = 2
    }
}
