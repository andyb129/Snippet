package uk.co.barbuzz.snippet.db

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

object DatabaseBackup {

    private const val LOGGER = "DatabaseBackup"
    private const val MAXIMUM_DATABASE_FILE = 10
    const val FILE_NAME = "snippetdb_"
    const val FOLDER = "SnippetBackup"

    fun backupSnippetDatabase(context: Context, scope: CoroutineScope?) : Boolean {
        //Checking the availability state of the External Storage.
        val state: String = Environment.getExternalStorageState()
        if (Environment.MEDIA_MOUNTED != state) {
            return false
        }

        //Create a new file that points to the root directory, with the given name
        val appDatabase = SnippetRoomDatabase.getDatabase(context, scope!!)
        appDatabase.close()
        val dbfile = context.getDatabasePath(SnippetRoomDatabase.DATABASE_NAME)
        val sdir = File(context.getExternalFilesDir(null), FOLDER)
        val fileName =
            FILE_NAME + getDateFromMillisForBackup(System.currentTimeMillis())
        val sfpath = sdir.path + File.separator + fileName
        if (!sdir.exists()) {
            sdir.mkdirs()
        } else {
            checkAndDeleteBackupFile(sdir, sfpath)
        }
        val savefile = File(sfpath)
        if (savefile.exists()) {
            Log.d(LOGGER, "File exists. Deleting it and then creating new file.")
            savefile.delete()
        }
        try {
            if (savefile.createNewFile()) {
                val buffersize = 8 * 1024
                val buffer = ByteArray(buffersize)
                var bytes_read = buffersize
                val savedb: OutputStream = FileOutputStream(sfpath)
                val indb: InputStream = FileInputStream(dbfile)
                while (indb.read(buffer, 0, buffersize).also { bytes_read = it } > 0) {
                    savedb.write(buffer, 0, bytes_read)
                }
                savedb.flush()
                indb.close()
                savedb.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(LOGGER, "ex: $e")
            return false
        }
        return true
    }

    private fun getDateFromMillisForBackup(currentTimeMillis: Long): String {
        val date = Date()
        date.time = currentTimeMillis
        return SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(date)
    }

    private fun checkAndDeleteBackupFile(directory: File, path: String?) {
        val currentDateFile = File(path)
        var fileIndex = -1
        var lastModifiedTime = System.currentTimeMillis()
        if (!currentDateFile.exists()) {
            val files = directory.listFiles()
            if (files != null && files.size >= MAXIMUM_DATABASE_FILE) {
                for (i in files.indices) {
                    val file = files[i]
                    val fileLastModifiedTime = file.lastModified()
                    if (fileLastModifiedTime < lastModifiedTime) {
                        lastModifiedTime = fileLastModifiedTime
                        fileIndex = i
                    }
                }
                if (fileIndex != -1) {
                    val deletingFile = files[fileIndex]
                    if (deletingFile.exists()) {
                        deletingFile.delete()
                    }
                }
            }
        }
    }
}
