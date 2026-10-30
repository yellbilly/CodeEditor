package it.fabio.codeeditor

import android.net.Uri

/** Un documento aperto in una scheda. */
class Doc(
    var name: String,
    var uri: Uri? = null,
    var language: Language = Language.PLAIN
) {
    /** Testo con a-capo normalizzati a '\n' (i CRLF originali si ripristinano al salvataggio). */
    var text: String = ""
    var modified = false
    var selStart = 0
    var selEnd = 0
    var crlf = false
    var charsetName = "UTF-8"
    var bom = false
    val history = UndoManager()

    val encodingLabel: String
        get() = if (charsetName == "UTF-8") (if (bom) "UTF-8 BOM" else "UTF-8") else "ANSI"
}
