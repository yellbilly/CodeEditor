package it.fabio.codeeditor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object FileIo {

    private const val MAX_BYTES = 8 * 1024 * 1024

    class Loaded(val text: String, val crlf: Boolean, val charsetName: String, val bom: Boolean)

    fun read(context: Context, uri: Uri): Loaded {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw java.io.IOException("stream nullo")
        if (bytes.size > MAX_BYTES) throw java.io.IOException("file troppo grande")

        var offset = 0
        var bom = false
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            offset = 3
            bom = true
        }

        var charsetName = "UTF-8"
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.size - offset))
                .toString()
        } catch (e: CharacterCodingException) {
            charsetName = "windows-1252"
            String(bytes, offset, bytes.size - offset, Charset.forName("windows-1252"))
        }

        val crlf = text.contains("\r\n")
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        return Loaded(normalized, crlf, charsetName, bom)
    }

    fun write(context: Context, uri: Uri, doc: Doc) {
        val content = if (doc.crlf) doc.text.replace("\n", "\r\n") else doc.text
        val body = content.toByteArray(Charset.forName(doc.charsetName))
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            if (doc.bom && doc.charsetName == "UTF-8") {
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            }
            out.write(body)
            out.flush()
        } ?: throw java.io.IOException("stream nullo")
    }

    fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val name = c.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }
}
