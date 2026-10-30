package it.fabio.codeeditor

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.tabs.TabLayout
import it.fabio.codeeditor.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var session: SessionStore

    private val docs = ArrayList<Doc>()
    private var current = -1
    private var suppressTabs = false

    private var saveAsTarget: Doc? = null
    private var saveAsAfter: (() -> Unit)? = null

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) openUri(uri)
        }

    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) finishSaveAs(uri) else {
                saveAsTarget = null
                saveAsAfter = null
            }
        }

    // ------------------------------------------------------------------ ciclo di vita

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            prefs.getInt("night", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        session = SessionStore(this)

        setupEditor()
        setupToolbar()
        setupTabs()
        setupFindBar()
        setupSymbolBar()

        val restored = session.restore()
        if (restored != null) {
            docs.addAll(restored.first)
            current = restored.second
            rebuildTabs()
            showDoc()
        } else {
            newDoc()
        }
        handleIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.findBar.visibility == View.VISIBLE) closeFind()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        syncFromEditor()
        session.save(docs, current)
    }

    private fun handleIntent(i: Intent?) {
        val data = i?.data ?: return
        if (i.action == Intent.ACTION_VIEW || i.action == Intent.ACTION_EDIT) openUri(data)
    }

    private fun isNight() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // -------------------------------------------------------------------- impostazione UI

    private fun setupEditor() {
        val ed = b.editor
        ed.colors = if (isNight()) EditorThemes.DARK else EditorThemes.LIGHT
        ed.setFontFamily(prefs.getString("fontFamily", "monospace") ?: "monospace")
        ed.setFontSizeSp(prefs.getFloat("font", 14f))
        ed.setWrap(prefs.getBoolean("wrap", false))
        ed.showLineNumbers = prefs.getBoolean("lines", true)
        ed.onUserEdit = { onEdited() }
        ed.onCursorChanged = { updateStatus() }
        ed.onCtrlKey = { handleCtrl(it) }
        applyChrome()
    }

    private fun applyChrome() {
        val c = b.editor.colors
        b.status.setBackgroundColor(c.gutterBg)
        b.status.setTextColor(c.gutterText)
        b.symbolBarScroll.setBackgroundColor(c.gutterBg)
    }

    private fun setupToolbar() {
        b.toolbar.inflateMenu(R.menu.main_menu)
        b.toolbar.menu.findItem(R.id.action_wrap).isChecked = prefs.getBoolean("wrap", false)
        b.toolbar.menu.findItem(R.id.action_line_numbers).isChecked = prefs.getBoolean("lines", true)
        b.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new -> newDoc()
                R.id.action_open -> openLauncher.launch(arrayOf("*/*"))
                R.id.action_save -> cur()?.let { save(it) }
                R.id.action_save_as -> cur()?.let { syncFromEditor(); saveAs(it, null) }
                R.id.action_close_tab -> cur()?.let { closeTab(it) }
                R.id.action_undo -> b.editor.undo()
                R.id.action_redo -> b.editor.redo()
                R.id.action_find -> showFind()
                R.id.action_goto -> showGoTo()
                R.id.action_duplicate_line -> b.editor.duplicateLine()
                R.id.action_delete_line -> b.editor.deleteLine()
                R.id.action_comment -> b.editor.toggleComment()
                R.id.action_upper -> b.editor.changeCase(true)
                R.id.action_lower -> b.editor.changeCase(false)
                R.id.action_language -> showLanguageDialog()
                R.id.action_wrap -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("wrap", item.isChecked).apply()
                    b.editor.setWrap(item.isChecked)
                }
                R.id.action_line_numbers -> {
                    item.isChecked = !item.isChecked
                    prefs.edit().putBoolean("lines", item.isChecked).apply()
                    b.editor.showLineNumbers = item.isChecked
                }
                R.id.action_font_family -> showFontDialog()
                R.id.action_about -> showAbout()
                R.id.action_theme -> toggleTheme()
                R.id.action_font_bigger -> changeFont(+1f)
                R.id.action_font_smaller -> changeFont(-1f)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun updateMenuState() {
        val m = b.toolbar.menu
        val h = b.editor.history
        m.findItem(R.id.action_undo)?.isEnabled = h.canUndo
        m.findItem(R.id.action_redo)?.isEnabled = h.canRedo
    }

    private fun toggleTheme() {
        syncFromEditor()
        session.save(docs, current)
        val mode = if (isNight()) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        prefs.edit().putInt("night", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode) // ricrea l'activity; la sessione viene ripristinata
    }

    private fun changeFont(delta: Float) {
        val size = (b.editor.fontSizeSp() + delta).coerceIn(8f, 32f)
        b.editor.setFontSizeSp(size)
        prefs.edit().putFloat("font", size).apply()
    }

    private fun setupSymbolBar() {
        val symbols = listOf(
            "TAB", "{", "}", "(", ")", "[", "]", ";", ":", "\"", "'", "<", ">", "/", "\\",
            "=", "+", "-", "*", "&", "|", "!", "_", "#", "$", "%", "@", "~", "^", "?"
        )
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        val dp = resources.displayMetrics.density
        for (s in symbols) {
            val t = TextView(this).apply {
                text = s
                textSize = if (s == "TAB") 13f else 17f
                typeface = android.graphics.Typeface.MONOSPACE
                gravity = Gravity.CENTER
                minWidth = (42 * dp).toInt()
                setPadding((8 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
                setBackgroundResource(tv.resourceId)
                setTextColor(b.editor.colors.text)
                isFocusable = false
                setOnClickListener {
                    if (s == "TAB") b.editor.indent() else b.editor.insertSymbol(s)
                }
            }
            b.symbolBar.addView(t)
        }
    }

    // ---------------------------------------------------------------------- schede

    private fun cur(): Doc? = docs.getOrNull(current)

    private fun tabTitle(d: Doc) = (if (d.modified) "● " else "") + d.name

    private fun setupTabs() {
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!suppressTabs) switchTo(tab.position)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun rebuildTabs() {
        suppressTabs = true
        b.tabs.removeAllTabs()
        for ((i, d) in docs.withIndex()) {
            val tab = b.tabs.newTab().setText(tabTitle(d))
            b.tabs.addTab(tab, i == current)
            tab.view.setOnLongClickListener {
                closeTab(d)
                true
            }
        }
        suppressTabs = false
        b.tabs.post { b.tabs.getTabAt(current)?.select() }
    }

    private fun refreshTab(d: Doc) {
        val i = docs.indexOf(d)
        if (i >= 0) b.tabs.getTabAt(i)?.text = tabTitle(d)
    }

    private fun selectTabUi() {
        suppressTabs = true
        b.tabs.getTabAt(current)?.select()
        suppressTabs = false
    }

    private fun switchTo(i: Int) {
        if (i == current || i !in docs.indices) return
        syncFromEditor()
        current = i
        showDoc()
    }

    private fun syncFromEditor() {
        val d = cur() ?: return
        d.text = b.editor.text?.toString() ?: ""
        d.selStart = b.editor.selectionStart.coerceAtLeast(0)
        d.selEnd = b.editor.selectionEnd.coerceAtLeast(0)
    }

    private fun showDoc() {
        val d = cur() ?: return
        b.editor.history = d.history
        b.editor.language = d.language
        b.editor.loadText(d.text, d.selStart, d.selEnd)
        if (b.findBar.visibility == View.VISIBLE) markMatches()
        updateStatus()
        updateMenuState()
    }

    private fun addDoc(d: Doc) {
        syncFromEditor()
        docs.add(d)
        current = docs.size - 1
        rebuildTabs()
        showDoc()
    }

    private fun untitledName(): String {
        val base = getString(R.string.untitled)
        var n = 1
        var name = base
        while (docs.any { it.uri == null && it.name == name }) {
            n++
            name = "$base $n"
        }
        return name
    }

    private fun newDoc() = addDoc(Doc(untitledName()))

    private fun onEdited() {
        val d = cur() ?: return
        if (!d.modified) {
            d.modified = true
            refreshTab(d)
        }
        updateMenuState()
        updateStatus()
    }

    private fun closeTab(d: Doc) {
        if (d === cur()) syncFromEditor()
        if (!d.modified) {
            removeDoc(d)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_title)
            .setMessage(getString(R.string.unsaved_message, d.name))
            .setPositiveButton(R.string.save) { _, _ -> save(d) { removeDoc(d) } }
            .setNegativeButton(R.string.discard) { _, _ -> removeDoc(d) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun removeDoc(d: Doc) {
        val idx = docs.indexOf(d)
        if (idx < 0) return
        if (d === cur()) syncFromEditor()
        docs.removeAt(idx)
        if (docs.isEmpty()) {
            current = -1
            newDoc()
            return
        }
        current = if (idx < current) current - 1 else minOf(current, docs.size - 1)
        rebuildTabs()
        showDoc()
    }

    // ------------------------------------------------------------------- file

    private fun openUri(uri: Uri) {
        docs.firstOrNull { it.uri == uri }?.let {
            switchTo(docs.indexOf(it))
            selectTabUi()
            return
        }
        try {
            val name = FileIo.displayName(this, uri)
            val loaded = FileIo.read(this, uri)
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            val d = Doc(name, uri, Language.fromFileName(name))
            d.text = loaded.text
            d.crlf = loaded.crlf
            d.charsetName = loaded.charsetName
            d.bom = loaded.bom

            // sostituisce la scheda vuota iniziale, come fa Notepad++
            val only = docs.singleOrNull()
            if (only != null && only.uri == null && !only.modified && b.editor.text.isNullOrEmpty()) {
                docs.clear()
                current = -1
            }
            addDoc(d)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_open, Toast.LENGTH_LONG).show()
        }
    }

    private fun save(d: Doc, after: (() -> Unit)? = null) {
        if (d === cur()) syncFromEditor()
        val uri = d.uri
        if (uri == null) {
            saveAs(d, after)
            return
        }
        try {
            FileIo.write(this, uri, d)
            d.modified = false
            refreshTab(d)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            after?.invoke()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_save, Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAs(d: Doc, after: (() -> Unit)?) {
        saveAsTarget = d
        saveAsAfter = after
        saveAsLauncher.launch(d.name)
    }

    private fun finishSaveAs(uri: Uri) {
        val d = saveAsTarget ?: return
        val after = saveAsAfter
        saveAsTarget = null
        saveAsAfter = null
        try {
            d.uri = uri
            d.name = FileIo.displayName(this, uri)
            if (d.language == Language.PLAIN) d.language = Language.fromFileName(d.name)
            FileIo.write(this, uri, d)
            d.modified = false
            refreshTab(d)
            if (d === cur()) b.editor.language = d.language
            updateStatus()
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            after?.invoke()
        } catch (e: Exception) {
            d.uri = null
            Toast.makeText(this, R.string.error_save, Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------- scorciatoie da tastiera

    private fun handleCtrl(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_S -> cur()?.let { save(it) }
            KeyEvent.KEYCODE_O -> openLauncher.launch(arrayOf("*/*"))
            KeyEvent.KEYCODE_N -> newDoc()
            KeyEvent.KEYCODE_W -> cur()?.let { closeTab(it) }
            KeyEvent.KEYCODE_F, KeyEvent.KEYCODE_H -> showFind()
            KeyEvent.KEYCODE_G -> showGoTo()
            KeyEvent.KEYCODE_D -> b.editor.duplicateLine()
            KeyEvent.KEYCODE_L -> b.editor.deleteLine()
            KeyEvent.KEYCODE_SLASH -> b.editor.toggleComment()
            else -> return false
        }
        return true
    }

    // ------------------------------------------------------------------ stato

    private fun updateStatus() {
        val d = cur() ?: return
        val t = b.editor.text ?: return
        val pos = b.editor.selectionStart.coerceIn(0, t.length)
        var line = 1
        var lastNl = -1
        var total = 1
        for (i in 0 until t.length) {
            if (t[i] == '\n') {
                total++
                if (i < pos) {
                    line++
                    lastNl = i
                }
            }
        }
        val col = pos - lastNl
        val info = "${d.encodingLabel} · ${if (d.crlf) "CRLF" else "LF"} · ${d.language.label}"
        b.status.text = getString(R.string.status_format, line, col, total, t.length, info)
    }

    // --------------------------------------------------------------- dialoghi

    private fun showGoTo() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.goto_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_goto)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let { b.editor.goToLine(it) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFontDialog() {
        val keys = arrayOf("monospace", "courier")
        val labels = arrayOf(getString(R.string.font_monospace), getString(R.string.font_courier))
        val now = keys.indexOf(prefs.getString("fontFamily", "monospace")).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_font)
            .setSingleChoiceItems(labels, now) { dlg, which ->
                prefs.edit().putString("fontFamily", keys[which]).apply()
                b.editor.setFontFamily(keys[which])
                dlg.dismiss()
            }
            .show()
    }

    private fun showAbout() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        val msg = getString(R.string.about_body, getString(R.string.app_name), version)
        val tv = TextView(this).apply {
            text = msg
            setTextIsSelectable(true)
            val p = (20 * resources.displayMetrics.density).toInt()
            setPadding(p, p / 2, p, 0)
            android.text.util.Linkify.addLinks(this, android.text.util.Linkify.EMAIL_ADDRESSES)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.about_title)
            .setView(tv)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.about_write) { _, _ ->
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:fabiosirigu@gmail.com")))
            }
            .show()
    }

    private fun showLanguageDialog() {
        val d = cur() ?: return
        val langs = Language.values()
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_language)
            .setSingleChoiceItems(langs.map { it.label }.toTypedArray(), langs.indexOf(d.language)) { dlg, which ->
                d.language = langs[which]
                b.editor.language = d.language
                updateStatus()
                dlg.dismiss()
            }
            .show()
    }

    // ------------------------------------------------------------ trova / sostituisci

    private fun setupFindBar() {
        b.findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, bf: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = markMatches()
        })
        b.optCase.setOnCheckedChangeListener { _, _ -> markMatches() }
        b.optRegex.setOnCheckedChangeListener { _, _ -> markMatches() }
        b.findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findStep(true)
                true
            } else false
        }
        b.findNext.setOnClickListener { findStep(true) }
        b.findPrev.setOnClickListener { findStep(false) }
        b.findClose.setOnClickListener { closeFind() }

        b.replaceOne.setOnClickListener {
            val r = findRegex() ?: return@setOnClickListener
            val ok = b.editor.replaceCurrent(r, b.replaceInput.text.toString(), b.optRegex.isChecked)
            if (!ok) Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show()
            markMatches()
        }
        b.replaceAll.setOnClickListener {
            val r = findRegex() ?: return@setOnClickListener
            val n = b.editor.replaceAll(r, b.replaceInput.text.toString(), b.optRegex.isChecked)
            val msg = if (n == 0) getString(R.string.not_found) else getString(R.string.replaced_n, n)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            markMatches()
        }
    }

    private fun findRegex(): Regex? {
        val q = b.findInput.text.toString()
        if (q.isEmpty()) {
            b.findInput.error = null
            return null
        }
        val r = b.editor.buildRegex(q, b.optCase.isChecked, b.optRegex.isChecked)
        b.findInput.error = if (r == null) getString(R.string.error_regex) else null
        return r
    }

    private fun markMatches() {
        if (b.findBar.visibility != View.VISIBLE) return
        b.editor.markAllMatches(findRegex())
    }

    private fun findStep(forward: Boolean) {
        val r = findRegex() ?: return
        if (!b.editor.find(r, forward)) Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show()
    }

    private fun showFind() {
        b.findBar.visibility = View.VISIBLE
        val ed = b.editor
        val s = ed.selectionStart
        val e = ed.selectionEnd
        if (e > s) {
            val sel = ed.text!!.substring(s, e)
            if (!sel.contains('\n')) b.findInput.setText(sel)
        }
        b.findInput.requestFocus()
        b.findInput.selectAll()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(b.findInput, InputMethodManager.SHOW_IMPLICIT)
        markMatches()
    }

    private fun closeFind() {
        b.findBar.visibility = View.GONE
        b.editor.clearMatchMarks()
        b.editor.requestFocus()
    }
}
