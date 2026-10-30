package it.fabio.codeeditor

/**
 * Cronologia di annulla/ripeti basata su modifiche (posizione, testo vecchio, testo nuovo).
 * Le battute consecutive vengono fuse in un'unica voce, come in Notepad++.
 */
class UndoManager {

    class Edit(val start: Int, var oldText: String, var newText: String, var time: Long)

    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()

    val canUndo get() = undoStack.isNotEmpty()
    val canRedo get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun record(start: Int, oldText: String, newText: String) {
        if (oldText.isEmpty() && newText.isEmpty()) return
        val now = System.currentTimeMillis()
        redoStack.clear()

        val last = undoStack.lastOrNull()
        if (last != null && now - last.time < MERGE_MS) {
            // digitazione continua: inserimento di un carattere subito dopo l'ultimo inserimento
            if (last.oldText.isEmpty() && oldText.isEmpty() &&
                newText.length == 1 && newText != "\n" &&
                start == last.start + last.newText.length && !last.newText.endsWith("\n")
            ) {
                last.newText += newText
                last.time = now
                return
            }
            // cancellazioni continue con backspace
            if (last.newText.isEmpty() && newText.isEmpty() &&
                oldText.length == 1 && start + 1 == last.start
            ) {
                undoStack[undoStack.size - 1] = Edit(start, oldText + last.oldText, "", now)
                return
            }
        }
        undoStack.add(Edit(start, oldText, newText, now))
        if (undoStack.size > MAX_ENTRIES) undoStack.removeAt(0)
    }

    fun popUndo(): Edit? {
        val e = undoStack.removeLastOrNull() ?: return null
        redoStack.add(e)
        return e
    }

    fun popRedo(): Edit? {
        val e = redoStack.removeLastOrNull() ?: return null
        undoStack.add(e)
        return e
    }

    companion object {
        private const val MERGE_MS = 1200L
        private const val MAX_ENTRIES = 2000
    }
}
