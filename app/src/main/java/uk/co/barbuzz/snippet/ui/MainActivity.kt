package uk.co.barbuzz.snippet.ui

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomappbar.BottomAppBar
import com.google.android.material.bottomappbar.BottomAppBarTopEdgeTreatment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.snackbar.Snackbar
import io.github.inflationx.viewpump.ViewPumpContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import uk.co.barbuzz.snippet.R
import uk.co.barbuzz.snippet.db.DatabaseBackup.backupSnippetDatabase
import uk.co.barbuzz.snippet.db.SnippetRoomDatabase
import uk.co.barbuzz.snippet.model.Snippet
import uk.co.barbuzz.snippet.viewmodel.SnippetViewModel
import java.io.*
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity(), SnippetListAdapter.OnSnippetOnClickListener {

    private lateinit var adapter: SnippetListAdapter
    private lateinit var coordinatorLayout: CoordinatorLayout
    private lateinit var fab: FloatingActionButton
    private lateinit var mSnippetViewModel: SnippetViewModel
    private lateinit var snippetList: List<Snippet>
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        coordinatorLayout = findViewById<CoordinatorLayout>(R.id.coordinatorLayout)
        fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            val intent = Intent(this@MainActivity, SnippetActivity::class.java)
            startActivityForResult(intent, NEW_SNIPPET_REQUEST_CODE)
        }

        val bar = findViewById<BottomAppBar>(R.id.bottom_app_bar)
        val topEdge: BottomAppBarTopEdgeTreatment = BottomAppBarCutCornersTopEdge(
            bar.fabCradleMargin,
            bar.fabCradleRoundedCornerRadius,
            bar.cradleVerticalOffset
        )
        val babBackground = bar.background as MaterialShapeDrawable
        babBackground.shapeAppearanceModel = babBackground.shapeAppearanceModel
            .toBuilder()
            .setTopEdge(topEdge)
            .build()

        val emptyListText = findViewById<TextView>(R.id.empty_list_text)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerview)
        adapter = SnippetListAdapter(this, this)
        val linearLayoutManager = LinearLayoutManager(this)
        val dividerItemDecoration = DividerItemDecoration(
            recyclerView.context,
            linearLayoutManager.orientation
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.addItemDecoration(dividerItemDecoration)

        mSnippetViewModel = ViewModelProvider(this).get(SnippetViewModel::class.java)

        mSnippetViewModel.allSnippets.observe(this, Observer { snippets ->
            if (snippets.isNotEmpty()) {
                emptyListText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            } else {
                emptyListText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
            snippetList = snippets
            snippets.let { adapter.setSnippets(it) }
        })
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(ViewPumpContextWrapper.wrap(newBase!!))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_backup -> {
                backupDb()
                true
            }
            R.id.action_restore -> {
                restoreDbIntent()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    //TODO - GET BACKUP TO RESTORE TO ROOT OR DOWNLOAD FOLDER
    private fun backupDb() {
        val backupMsg = if (backupSnippetDatabase(this, ioScope)) {
            R.string.db_backup_success
        } else {
            R.string.db_backup_fail
        }
        Snackbar.make(coordinatorLayout, backupMsg, Snackbar.LENGTH_LONG)
            .setAnchorView(fab)
            .show()
    }

    override fun onSnippetClicked(position: Int) {
        val snippet = snippetList[position]
        val intent = Intent(this, SnippetActivity::class.java)
        intent.putExtra(SnippetActivity.EXTRA_SNIPPET, snippet)
        startActivityForResult(intent, EDIT_SNIPPET_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intentData: Intent?) {
        super.onActivityResult(requestCode, resultCode, intentData)

        if (requestCode == NEW_SNIPPET_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            intentData?.let { data ->
                val snippet = Snippet(
                    getId(),
                    data.getStringExtra(SnippetActivity.EXTRA_ABBREV),
                    data.getStringExtra(SnippetActivity.EXTRA_REPLY)
                )
                mSnippetViewModel.insert(snippet)
                Unit
            }
        } else if (requestCode == EDIT_SNIPPET_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            intentData?.let { data ->
                val snippetToEdit = data.getParcelableExtra<Snippet>(SnippetActivity.EXTRA_SNIPPET)
                val snippetToDelete =
                    data.getParcelableExtra<Snippet>(SnippetActivity.EXTRA_SNIPPET_DELETE)
                if (snippetToEdit != null) {
                    mSnippetViewModel.update(snippetToEdit)
                } else {
                    mSnippetViewModel.delete(snippetToDelete)
                }
                adapter.notifyDataSetChanged()
            }
        } else if (requestCode == RESTORE_SNIPPET_DATABASE_REQUEST_CODE) {
            restoreDb(intentData)
            adapter.notifyDataSetChanged()
        } else {
            Snackbar.make(coordinatorLayout, R.string.not_saved, Snackbar.LENGTH_LONG)
                .setAnchorView(fab)
                .show()
        }
    }

    private fun restoreDbIntent() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "*/*"
        startActivityForResult(Intent.createChooser(i, "Select DB File"), RESTORE_SNIPPET_DATABASE_REQUEST_CODE)
    }

    private fun restoreDb(intentData: Intent?) {
        val fileUri: Uri? = intentData?.data
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(fileUri!!)
            restoreDatabase(inputStream)
            inputStream?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun dbRestoredErrorMsg() {
        Snackbar.make(coordinatorLayout, R.string.db_not_restored, Snackbar.LENGTH_LONG)
            .setAnchorView(fab)
            .show()
    }

    private fun restoreDatabase(inputStreamNewDB: InputStream?) {
        val appDatabase = SnippetRoomDatabase.getDatabase(this, ioScope)
        appDatabase.close()

        val oldDB: File = getDatabasePath(SnippetRoomDatabase.DATABASE_NAME)
        if (inputStreamNewDB != null) {
            try {
                copyFile(inputStreamNewDB as FileInputStream, FileOutputStream(oldDB))
                Snackbar.make(coordinatorLayout, R.string.db_restored, Snackbar.LENGTH_LONG)
                    .setAnchorView(fab)
                    .show()
            } catch (e: IOException) {
                dbRestoredErrorMsg()
            }
        } else {
            dbRestoredErrorMsg()
        }
    }

    @Throws(IOException::class)
    fun copyFile(fromFile: FileInputStream, toFile: FileOutputStream) {
        var fromChannel: FileChannel? = null
        var toChannel: FileChannel? = null
        try {
            fromChannel = fromFile.channel
            toChannel = toFile.channel
            fromChannel.transferTo(0, fromChannel.size(), toChannel)
        } finally {
            try {
                fromChannel?.close()
            } finally {
                toChannel?.close()
            }
        }
    }

    private fun getId(): Long = Math.random().toLong()
}

private const val NEW_SNIPPET_REQUEST_CODE = 1
private const val EDIT_SNIPPET_REQUEST_CODE = 2
private const val RESTORE_SNIPPET_DATABASE_REQUEST_CODE = 3
