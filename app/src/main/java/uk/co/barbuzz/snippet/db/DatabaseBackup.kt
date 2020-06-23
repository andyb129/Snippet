package uk.co.barbuzz.snippet.db

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackup {
    private const val DATABASE_NAME = ""
    private const val FILE_NAME = ""
    private const val LOGGER = ""
    private const val SHAREDPREF = ""
    private const val MODE_PRIVATE = 0
    private const val MAXIMUM_DATABASE_FILE = 10

    fun backupDatabase(context: Context, scope: CoroutineScope?) {
        val appDatabase = SnippetRoomDatabase.getDatabase(context, scope!!)
        appDatabase.close()
        val dbfile = context.getDatabasePath(DATABASE_NAME)
        val sdir = File(getFilePath(context, 0), "backup")
        val fileName =
            FILE_NAME + getDateFromMillisForBackup(System.currentTimeMillis())
        val sfpath = sdir.path + File.separator + fileName
        if (!sdir.exists()) {
            sdir.mkdirs()
        } else {
            //Directory Exists. Delete a file if count is 5 already. Because we will be creating a new.
            //This will create a conflict if the last backup file was also on the same date. In that case,
            //we will reduce it to 4 with the function call but the below code will again delete one more file.
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
                val sharedPreferences =
                    context.getSharedPreferences(SHAREDPREF, MODE_PRIVATE)
                sharedPreferences.edit().putString("backupFileName", fileName).apply()
                updateLastBackupTime(sharedPreferences)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(LOGGER, "ex: $e")
        }
    }

    private fun getDateFromMillisForBackup(currentTimeMillis: Long): String {
        val date = Date()
        date.time = currentTimeMillis
        return SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(date)
    }

    private fun updateLastBackupTime(sharedPreferences: SharedPreferences) {}

    private fun getFilePath(context: Context, i: Int): File? {
        return null
    }

    private fun checkAndDeleteBackupFile(directory: File, path: String?) {
        //This is to prevent deleting extra file being deleted which is mentioned in previous comment lines.
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
