package it.fabio.codeeditor

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Salva/ripristina le schede aperte, così non si perde nulla se l'app viene chiusa. */
class SessionStore(context: Context) {

    private val dir = File(context.filesDir, "session").apply { mkdirs() }
    private val index = File(dir, "session.json")

    fun save(docs: List<Doc>, current: Int) {
        try {
            dir.listFiles()?.forEach { if (it.name.startsWith("doc_")) it.delete() }
            val arr = JSONArray()
            docs.forEachIndexed { i, d ->
                File(dir, "doc_$i.txt").writeText(d.text, Charsets.UTF_8)
                arr.put(JSONObject().apply {
                    put("name", d.name)
                    put("uri", d.uri?.toString() ?: JSONObject.NULL)
                    put("lang", d.language.name)
                    put("modified", d.modified)
                    put("selStart", d.selStart)
                    put("selEnd", d.selEnd)
                    put("crlf", d.crlf)
                    put("charset", d.charsetName)
                    put("bom", d.bom)
                })
            }
            index.writeText(JSONObject().put("current", current).put("docs", arr).toString())
        } catch (_: Exception) {
        }
    }

    fun restore(): Pair<List<Doc>, Int>? {
        return try {
            if (!index.exists()) return null
            val root = JSONObject(index.readText())
            val arr = root.getJSONArray("docs")
            val docs = ArrayList<Doc>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val lang = runCatching { Language.valueOf(o.getString("lang")) }.getOrDefault(Language.PLAIN)
                val d = Doc(
                    o.getString("name"),
                    if (o.isNull("uri")) null else Uri.parse(o.getString("uri")),
                    lang
                )
                d.text = File(dir, "doc_$i.txt").takeIf { it.exists() }?.readText(Charsets.UTF_8) ?: ""
                d.modified = o.optBoolean("modified")
                d.selStart = o.optInt("selStart")
                d.selEnd = o.optInt("selEnd")
                d.crlf = o.optBoolean("crlf")
                d.charsetName = o.optString("charset", "UTF-8")
                d.bom = o.optBoolean("bom")
                docs.add(d)
            }
            if (docs.isEmpty()) null else docs to root.optInt("current", 0).coerceIn(0, docs.size - 1)
        } catch (_: Exception) {
            null
        }
    }
}
