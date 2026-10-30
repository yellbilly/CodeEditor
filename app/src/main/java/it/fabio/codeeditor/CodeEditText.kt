package it.fabio.codeeditor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat

class MatchSpan(color: Int) : BackgroundColorSpan(color)

/**
 * EditText per codice: numeri di riga, evidenziazione della riga corrente,
 * colorazione sintassi, annulla/ripeti, auto-indentazione, trova/sostituisci.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    var history = UndoManager()
    var language: Language = Language.PLAIN
        set(value) {
            field = value
            rehighlight()
        }
    var colors: EditorColors = EditorThemes.LIGHT
        set(value) {
            field = value
            applyColors()
        }
    var showLineNumbers = true
        set(value) {
            field = value
            updateGutter(force = true)
        }

    /** Chiamato a ogni modifica fatta dall'utente (per il flag "modificato"). */
    var onUserEdit: (() -> Unit)? = null
    var onCursorChanged: (() -> Unit)? = null
    var onCtrlKey: ((Int) -> Boolean)? = null

    private var noHistory = false
    private var bypassFilter = false
    private var pendingStart = 0
    private var pendingOld = ""

    private val handler = Handler(Looper.getMainLooper())
    private val highlightRunnable = Runnable { rehighlight() }

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint()
    private var gutterWidth = 0
    private var lastDigits = 0

    private val dp = resources.displayMetrics.density

    init {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setHorizontallyScrolling(true)
        isVerticalScrollBarEnabled = true
        filters = arrayOf(autoIndentFilter())
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                pendingStart = start
                pendingOld = if (noHistory) "" else s.subSequence(start, start + count).toString()
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (noHistory) return
                history.record(pendingStart, pendingOld, s.subSequence(start, start + count).toString())
            }

            override fun afterTextChanged(s: Editable) {
                updateGutter()
                if (!noHistory) onUserEdit?.invoke()
                handler.removeCallbacks(highlightRunnable)
                handler.postDelayed(highlightRunnable, 150)
            }
        })
        applyColors()
        updateGutter(force = true)
    }

    // ------------------------------------------------------------------ aspetto

    private fun applyColors() {
        setBackgroundColor(colors.background)
        setTextColor(colors.text)
        highlightColor = colors.selection
        invalidate()
        rehighlight()
    }

    /** "monospace" = font fisso di sistema; "courier" = Courier Prime (incluso nell'app). */
    fun setFontFamily(family: String) {
        val tf = when (family) {
            "courier" -> ResourcesCompat.getFont(context, R.font.courier_prime) ?: Typeface.MONOSPACE
            else -> Typeface.MONOSPACE
        }
        typeface = tf
        updateGutter(force = true)
        requestLayout()
        invalidate()
    }

    private var fontSp = 14f

    fun setFontSizeSp(sp: Float) {
        fontSp = sp
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        updateGutter(force = true)
    }

    fun fontSizeSp(): Float = fontSp

    fun setWrap(wrap: Boolean) {
        setHorizontallyScrolling(!wrap)
        scrollTo(0, scrollY)
        requestLayout()
    }

    private fun updateGutter(force: Boolean = false) {
        val lines = lineCountLogical()
        val digits = maxOf(2, lines.toString().length)
        if (!force && digits == lastDigits) return
        lastDigits = digits
        gutterPaint.typeface = typeface
        gutterPaint.textSize = textSize
        gutterWidth = if (showLineNumbers)
            (gutterPaint.measureText("9".repeat(digits)) + 14 * dp).toInt() else 0
        val left = if (showLineNumbers) gutterWidth + (6 * dp).toInt() else (8 * dp).toInt()
        setPadding(left, (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
    }

    private fun lineCountLogical(): Int {
        val t = text ?: return 1
        var n = 1
        for (i in 0 until t.length) if (t[i] == '\n') n++
        return n
    }

    override fun onDraw(canvas: Canvas) {
        val l = layout
        val t = text
        if (l != null && t != null) {
            // riga corrente
            val sel = selectionStart.coerceIn(0, t.length)
            val cur = l.getLineForOffset(sel)
            fillPaint.color = colors.currentLine
            canvas.drawRect(
                scrollX.toFloat(), (totalPaddingTop + l.getLineTop(cur)).toFloat(),
                (scrollX + width).toFloat(), (totalPaddingTop + l.getLineBottom(cur)).toFloat(), fillPaint
            )
        }
        super.onDraw(canvas)

        if (!showLineNumbers || l == null || t == null) return

        val left = scrollX.toFloat()
        fillPaint.color = colors.gutterBg
        canvas.drawRect(left, scrollY.toFloat(), left + gutterWidth, (scrollY + height).toFloat(), fillPaint)
        fillPaint.color = colors.gutterText
        canvas.drawRect(left + gutterWidth - 1f, scrollY.toFloat(), left + gutterWidth, (scrollY + height).toFloat(), fillPaint)

        val first = l.getLineForVertical((scrollY - totalPaddingTop).coerceAtLeast(0))
        val last = l.getLineForVertical((scrollY + height - totalPaddingTop).coerceAtLeast(0))
        val curLine = l.getLineForOffset(selectionStart.coerceIn(0, t.length))

        var n = 1
        val firstStart = l.getLineStart(first)
        for (i in 0 until firstStart) if (t[i] == '\n') n++

        gutterPaint.textAlign = Paint.Align.RIGHT
        for (i in first..last) {
            val start = l.getLineStart(i)
            val isStart = start == 0 || t[start - 1] == '\n'
            if (isStart && i > first) n++
            if (isStart) {
                gutterPaint.color = if (i == curLine) colors.text else colors.gutterText
                val y = (totalPaddingTop + l.getLineBaseline(i)).toFloat()
                canvas.drawText(n.toString(), left + gutterWidth - 7 * dp, y, gutterPaint)
            }
        }
    }

    // --------------------------------------------------------------- sintassi

    fun rehighlight() {
        val e = text ?: return
        SyntaxHighlighter.highlight(e, language, colors)
    }

    // ----------------------------------------------------------- testo / undo

    /** Carica un testo senza registrarlo nella cronologia. */
    fun loadText(content: String, selStart: Int = 0, selEnd: Int = selStart) {
        noHistory = true
        bypassFilter = true
        setText(content)
        bypassFilter = false
        noHistory = false
        val len = text?.length ?: 0
        setSelection(selStart.coerceIn(0, len), selEnd.coerceIn(0, len))
        updateGutter(force = true)
        rehighlight()
    }

    fun undo() {
        val e = history.popUndo() ?: return
        applyUntracked(e.start, e.start + e.newText.length, e.oldText)
        setSelection((e.start + e.oldText.length).coerceAtMost(text?.length ?: 0))
        onUserEdit?.invoke()
    }

    fun redo() {
        val e = history.popRedo() ?: return
        applyUntracked(e.start, e.start + e.oldText.length, e.newText)
        setSelection((e.start + e.newText.length).coerceAtMost(text?.length ?: 0))
        onUserEdit?.invoke()
    }

    private fun applyUntracked(start: Int, end: Int, with: String) {
        noHistory = true
        bypassFilter = true
        text?.replace(start, end, with)
        bypassFilter = false
        noHistory = false
        rehighlight()
    }

    /** Sostituzione registrata nella cronologia ma senza auto-indentazione. */
    private fun replaceRange(start: Int, end: Int, with: String) {
        bypassFilter = true
        text?.replace(start, end, with)
        bypassFilter = false
    }

    // ------------------------------------------------------- auto-indentazione

    private fun autoIndentFilter() = InputFilter { source, _, _, dest, dstart, _ ->
        if (bypassFilter || source.length != 1 || source[0] != '\n') return@InputFilter null
        val ls = lineStartIn(dest, dstart)
        var i = ls
        while (i < dstart && (dest[i] == ' ' || dest[i] == '\t')) i++
        val sb = StringBuilder("\n").append(dest, ls, i)
        var k = dstart - 1
        while (k >= ls && (dest[k] == ' ' || dest[k] == '\t')) k--
        if (k >= ls) {
            val c = dest[k]
            if (c == '{' || c == '(' || c == '[' || (c == ':' && language == Language.PYTHON)) sb.append("    ")
        }
        sb.toString()
    }

    private fun lineStartIn(s: CharSequence, pos: Int): Int {
        var i = pos
        while (i > 0 && s[i - 1] != '\n') i--
        return i
    }

    // --------------------------------------------------------- operazioni sulle righe

    private fun lineStart(pos: Int): Int = lineStartIn(text!!, pos)

    private fun lineEnd(pos: Int): Int {
        val t = text!!
        var i = pos
        while (i < t.length && t[i] != '\n') i++
        return i
    }

    /** Intervallo [inizio, fine) delle righe toccate dalla selezione (fine esclude il '\n' finale). */
    private fun selectedLinesRange(): Pair<Int, Int> {
        val s = selectionStart.coerceAtLeast(0)
        var e = selectionEnd.coerceAtLeast(0)
        if (e > s && text!![e - 1] == '\n') e--
        return lineStart(s) to lineEnd(e)
    }

    fun goToLine(line: Int) {
        val t = text ?: return
        var n = 1
        var i = 0
        while (n < line && i < t.length) {
            if (t[i] == '\n') n++
            i++
        }
        setSelection(i)
        requestFocus()
    }

    fun duplicateLine() {
        val t = text ?: return
        val (s, e) = selectedLinesRange()
        val block = t.substring(s, e)
        replaceRange(e, e, "\n$block")
        setSelection(e + 1 + block.length)
    }

    fun deleteLine() {
        val t = text ?: return
        val (s, e) = selectedLinesRange()
        val end = if (e < t.length) e + 1 else e
        val start = if (end == e && s > 0) s - 1 else s
        replaceRange(start, end, "")
        setSelection(start.coerceAtMost(text!!.length))
    }

    fun changeCase(upper: Boolean) {
        val t = text ?: return
        val s = selectionStart
        val e = selectionEnd
        if (s == e) return
        val sub = t.substring(s, e)
        replaceRange(s, e, if (upper) sub.uppercase() else sub.lowercase())
        setSelection(s, e)
    }

    fun toggleComment() {
        val t = text ?: return
        val prefix = language.lineComment
        if (prefix != null) {
            val (s, e) = selectedLinesRange()
            val lines = t.substring(s, e).split("\n")
            val allCommented = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith(prefix) }
            val out = lines.joinToString("\n") { line ->
                if (line.isBlank()) line
                else {
                    val indent = line.length - line.trimStart().length
                    if (allCommented) {
                        val rest = line.substring(indent + prefix.length)
                        line.substring(0, indent) + rest.removePrefix(" ")
                    } else line.substring(0, indent) + prefix + " " + line.substring(indent)
                }
            }
            replaceRange(s, e, out)
            setSelection(s, s + out.length)
            return
        }
        val (open, close) = when (language) {
            Language.HTML, Language.XML -> "<!-- " to " -->"
            Language.CSS -> "/* " to " */"
            else -> return
        }
        var s = selectionStart
        var e = selectionEnd
        if (s == e) { s = lineStart(s); e = lineEnd(e) }
        val sub = t.substring(s, e)
        val out = if (sub.startsWith(open) && sub.endsWith(close))
            sub.removePrefix(open).removeSuffix(close) else open + sub + close
        replaceRange(s, e, out)
        setSelection(s, s + out.length)
    }

    /** Tab: inserisce spazi fino al prossimo multiplo di 4, o indenta le righe selezionate. */
    fun indent() {
        val t = text ?: return
        val s = selectionStart
        val e = selectionEnd
        val multiline = s != e && t.substring(s, e).contains('\n')
        if (!multiline) {
            val col = s - lineStart(s)
            replaceRange(s, e, " ".repeat(4 - col % 4))
            return
        }
        val (ls, le) = selectedLinesRange()
        val out = t.substring(ls, le).split("\n").joinToString("\n") { if (it.isEmpty()) it else "    $it" }
        replaceRange(ls, le, out)
        setSelection(ls, ls + out.length)
    }

    fun unindent() {
        val t = text ?: return
        val (ls, le) = selectedLinesRange()
        val out = t.substring(ls, le).split("\n").joinToString("\n") { line ->
            var n = 0
            while (n < 4 && n < line.length && line[n] == ' ') n++
            if (n == 0 && line.startsWith("\t")) line.substring(1) else line.substring(n)
        }
        replaceRange(ls, le, out)
        setSelection(ls, ls + out.length)
    }

    fun insertSymbol(sym: String) {
        val s = selectionStart.coerceAtLeast(0)
        val e = selectionEnd.coerceAtLeast(0)
        replaceRange(minOf(s, e), maxOf(s, e), sym)
    }

    // --------------------------------------------------------------- trova

    fun buildRegex(query: String, caseSensitive: Boolean, useRegex: Boolean): Regex? {
        if (query.isEmpty()) return null
        return try {
            val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            Regex(if (useRegex) query else Regex.escape(query), opts)
        } catch (e: Exception) {
            null
        }
    }

    fun clearMatchMarks() {
        val e = text ?: return
        for (s in e.getSpans(0, e.length, MatchSpan::class.java)) e.removeSpan(s)
    }

    fun markAllMatches(regex: Regex?) {
        clearMatchMarks()
        val e = text ?: return
        if (regex == null) return
        var count = 0
        for (m in regex.findAll(e.toString())) {
            if (m.value.isEmpty()) continue
            e.setSpan(MatchSpan(colors.match), m.range.first, m.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (++count >= 3000) break
        }
    }

    /** Seleziona la prossima/precedente occorrenza; ritorna false se non ce ne sono. */
    fun find(regex: Regex, forward: Boolean): Boolean {
        val src = text?.toString() ?: return false
        val found: MatchResult?
        if (forward) {
            val from = selectionEnd.coerceIn(0, src.length)
            found = regex.find(src, from) ?: regex.find(src, 0)
        } else {
            val before = selectionStart
            var prev: MatchResult? = null
            var lastAny: MatchResult? = null
            for (r in regex.findAll(src)) {
                if (r.value.isEmpty()) continue
                lastAny = r
                if (r.range.first < before) prev = r
            }
            found = prev ?: lastAny
        }
        val m = found ?: return false
        if (m.value.isEmpty()) return false
        setSelection(m.range.first, m.range.last + 1)
        requestFocus()
        return true
    }

    fun replaceCurrent(regex: Regex, replacement: String, useRegex: Boolean): Boolean {
        val t = text ?: return false
        val s = selectionStart
        val e = selectionEnd
        if (s != e) {
            val selected = t.substring(s, e)
            if (regex.matchEntire(selected) != null) {
                val rep = if (useRegex) regex.replace(selected, replacement)
                else replacement
                replaceRange(s, e, rep)
                setSelection(s + rep.length)
            }
        }
        return find(regex, true)
    }

    fun replaceAll(regex: Regex, replacement: String, useRegex: Boolean): Int {
        val src = text?.toString() ?: return 0
        val matches = regex.findAll(src).filter { it.value.isNotEmpty() }.toList()
        if (matches.isEmpty()) return 0
        val from = matches.first().range.first
        val to = matches.last().range.last + 1
        val chunk = src.substring(from, to)
        val out = if (useRegex) regex.replace(chunk, replacement)
        else regex.replace(chunk, Regex.escapeReplacement(replacement))
        replaceRange(from, to, out)
        setSelection((from + out.length).coerceAtMost(text!!.length))
        return matches.size
    }

    // --------------------------------------------------------------- eventi

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCursorChanged?.invoke()
        invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_Z -> { if (event.isShiftPressed) redo() else undo(); return true }
                KeyEvent.KEYCODE_Y -> { redo(); return true }
                KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_X ->
                    return super.onKeyDown(keyCode, event)
                else -> if (onCtrlKey?.invoke(keyCode) == true) return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_TAB) {
            if (event.isShiftPressed) unindent() else indent()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(highlightRunnable)
        super.onDetachedFromWindow()
    }
}
