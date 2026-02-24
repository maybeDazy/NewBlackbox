package daize.pro.cloner.container

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
                state TEXT NOT NULL DEFAULT 'ACTIVE',
                virtual_user_id INTEGER NOT NULL DEFAULT 0,
                source TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_containers_package_name ON containers(package_name)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_containers_user_package ON containers(virtual_user_id, package_name)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_containers_package_name ON containers(package_name)"
            )
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE containers ADD COLUMN virtual_user_id INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE containers ADD COLUMN source TEXT")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_containers_user_package ON containers(virtual_user_id, package_name)"
            )
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.rawQuery("PRAGMA busy_timeout=3000", null).use {
            // no-op: executing pragma via query API for Android compatibility
        }
    }

    companion object {
        private const val DATABASE_NAME = "container_registry.db"
        private const val DATABASE_VERSION = 3
    }
}
