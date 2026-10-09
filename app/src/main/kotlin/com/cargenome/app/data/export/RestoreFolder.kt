package com.cargenome.app.data.export

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.util.Log
import com.cargenome.app.data.db.CarGenomeDatabase
import java.io.File

/**
 * Seeds a fresh install from files dropped into `Android/data/<package>/files/restore/`:
 * `cargenome.db`, plus optional `attachments/` and `datastore/` folders. This is how data
 * moves between the debug and release builds, which are separate apps.
 *
 * The folder sits in shared storage, where on Android 8–9 any app with storage access can
 * write. It is therefore applied only while this install holds no cars or cards, so a stray
 * or planted file can never overwrite what the owner has entered. To restore over existing
 * data, clear the app's data first.
 *
 * Runs before Room opens the database, so it works on the files directly.
 */
object RestoreFolder {

    private const val TAG = "RestoreFolder"
    private const val SOURCE_DB = "cargenome.db"

    private val providerAuthorities = listOf(
        "com.cargenome.app.debug.fileprovider",
        "com.cargenome.app.fileprovider",
    )
    private val uriColumns = listOf(
        "vehicles" to "photoUri",
        "vehicles" to "insurancePdfUri",
        "attachments" to "uri",
    )

    /** Returns true when the restore folder was applied. */
    fun applyIfFreshInstall(context: Context): Boolean {
        val restoreDir = File(context.getExternalFilesDir(null) ?: return false, "restore")
        val source = File(restoreDir, SOURCE_DB)
        if (!source.isFile || source.length() == 0L) return false

        val target = context.getDatabasePath(CarGenomeDatabase.NAME)
        if (!holdsNoUserData(target)) {
            Log.w(TAG, "Ignoring ${source.path}: this install already has data")
            return false
        }

        return try {
            // Files first and the database last: until the database is in place the
            // install still counts as fresh, so a failed attempt is simply retried.
            copyFolder(File(restoreDir, "attachments"), File(context.filesDir, "attachments"))
            copyFolder(File(restoreDir, "datastore"), File(context.filesDir, "datastore"))
            installDatabase(source, target, "${context.packageName}.fileprovider")
            source.renameTo(File(restoreDir, "$SOURCE_DB.restored"))
            Log.i(TAG, "Restored database, attachments and settings from ${restoreDir.path}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore from ${restoreDir.path} failed", e)
            false
        }
    }

    private fun holdsNoUserData(db: File): Boolean {
        if (!db.exists()) return true
        return try {
            open(db, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
                DatabaseUtils.longForQuery(
                    sqlite,
                    "SELECT (SELECT COUNT(*) FROM vehicles) + (SELECT COUNT(*) FROM loyalty_cards)",
                    null,
                ) == 0L
            }
        } catch (e: SQLiteException) {
            // Unreadable or from an older schema: leave it alone rather than guess.
            Log.w(TAG, "Could not inspect ${db.path}", e)
            false
        }
    }

    private fun installDatabase(source: File, target: File, authority: String) {
        val dir = target.parentFile ?: error("No parent directory for ${target.path}")
        dir.mkdirs()
        val staged = File(dir, "${target.name}.restoring")
        try {
            source.copyTo(staged, overwrite = true)
            open(staged, SQLiteDatabase.OPEN_READWRITE).use { sqlite ->
                verifyIntegrity(sqlite)
                pointUrisAt(sqlite, authority)
            }
            // A leftover journal would be replayed onto the new file, so it goes first.
            journals(target).forEach { it.delete() }
            check(staged.renameTo(target)) { "Could not move the restored database into place" }
        } finally {
            (journals(staged) + staged).forEach { it.delete() }
        }
    }

    /**
     * The debug and release builds are separate apps with their own FileProvider
     * authority, and the stored URIs name it, so they are rewritten to this build's.
     */
    private fun pointUrisAt(sqlite: SQLiteDatabase, authority: String) {
        for (other in providerAuthorities - authority) {
            for ((table, column) in uriColumns) {
                try {
                    sqlite.execSQL(
                        "UPDATE $table SET $column = replace($column, ?, ?) WHERE $column IS NOT NULL",
                        arrayOf(other, authority),
                    )
                } catch (e: SQLiteDatabaseCorruptException) {
                    throw e
                } catch (e: SQLiteException) {
                    // An older database may predate the column; Room adds it on open.
                    Log.w(TAG, "Skipped $table.$column", e)
                }
            }
        }
    }

    /** The file comes from shared storage, so it is checked before it replaces anything. */
    private fun verifyIntegrity(sqlite: SQLiteDatabase) {
        val result = DatabaseUtils.stringForQuery(sqlite, "PRAGMA quick_check", null)
        if (result != "ok") throw SQLiteException("Restore database failed its integrity check: $result")
    }

    /**
     * The framework's default answer to a corrupt file is to delete it. Here that
     * would mean deleting the owner's database or passing off a broken restore as
     * an empty one, so fail instead.
     */
    private fun open(db: File, flags: Int): SQLiteDatabase =
        SQLiteDatabase.openDatabase(db.path, null, flags) {
            throw SQLiteException("Not a usable database: ${db.path}")
        }

    private fun copyFolder(from: File, to: File) {
        val files = from.listFiles()?.filter { it.isFile } ?: return
        to.mkdirs()
        files.forEach { it.copyTo(File(to, it.name), overwrite = true) }
    }

    private fun journals(db: File): List<File> =
        listOf("-wal", "-shm", "-journal").map { File(db.path + it) }
}
