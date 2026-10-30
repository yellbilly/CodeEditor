package it.fabio.codeeditor

import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.BitSet

/** Span dedicato, così possiamo rimuovere solo i nostri colori. */
class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

enum class Token { COMMENT, STRING, KEYWORD, NUMBER, TYPE, ANNOTATION, TAG, ATTR, FUNCTION }

class EditorColors(
    val background: Int,
    val text: Int,
    val gutterBg: Int,
    val gutterText: Int,
    val currentLine: Int,
    val selection: Int,
    val match: Int,
    val tokens: Map<Token, Int>
)

object EditorThemes {
    val LIGHT = EditorColors(
        background = 0xFFFFFFFF.toInt(),
        text = 0xFF1E1E1E.toInt(),
        gutterBg = 0xFFF0F0F0.toInt(),
        gutterText = 0xFF8A8A8A.toInt(),
        currentLine = 0xFFE8F2FF.toInt(),
        selection = 0x553390FF,
        match = 0x66FFD54F,
        tokens = mapOf(
            Token.COMMENT to 0xFF008000.toInt(),
            Token.STRING to 0xFFA31515.toInt(),
            Token.KEYWORD to 0xFF0000FF.toInt(),
            Token.NUMBER to 0xFF098658.toInt(),
            Token.TYPE to 0xFF267F99.toInt(),
            Token.ANNOTATION to 0xFF795E26.toInt(),
            Token.TAG to 0xFF800000.toInt(),
            Token.ATTR to 0xFFE50000.toInt(),
            Token.FUNCTION to 0xFF795E26.toInt()
        )
    )

    val DARK = EditorColors(
        background = 0xFF1E1E1E.toInt(),
        text = 0xFFD4D4D4.toInt(),
        gutterBg = 0xFF252526.toInt(),
        gutterText = 0xFF858585.toInt(),
        currentLine = 0xFF2A2D2E.toInt(),
        selection = 0x66264F78,
        match = 0x66A8801C,
        tokens = mapOf(
            Token.COMMENT to 0xFF6A9955.toInt(),
            Token.STRING to 0xFFCE9178.toInt(),
            Token.KEYWORD to 0xFF569CD6.toInt(),
            Token.NUMBER to 0xFFB5CEA8.toInt(),
            Token.TYPE to 0xFF4EC9B0.toInt(),
            Token.ANNOTATION to 0xFFDCDCAA.toInt(),
            Token.TAG to 0xFF569CD6.toInt(),
            Token.ATTR to 0xFF9CDCFE.toInt(),
            Token.FUNCTION to 0xFFDCDCAA.toInt()
        )
    )
}

enum class Language(
    val label: String,
    val extensions: List<String>,
    val lineComment: String?
) {
    PLAIN("Testo semplice", listOf("txt", "log", "md", "csv"), null),
    KOTLIN("Kotlin", listOf("kt", "kts"), "//"),
    JAVA("Java", listOf("java"), "//"),
    PYTHON("Python", listOf("py"), "#"),
    JAVASCRIPT("JavaScript / TypeScript", listOf("js", "mjs", "ts", "jsx", "tsx"), "//"),
    C_CPP("C / C++", listOf("c", "h", "cpp", "hpp", "cc", "ino"), "//"),
    HTML("HTML", listOf("html", "htm"), null),
    XML("XML", listOf("xml", "svg", "gradle.xml"), null),
    CSS("CSS", listOf("css", "scss"), null),
    JSON("JSON", listOf("json"), null),
    YAML("YAML", listOf("yaml", "yml"), "#"),
    SHELL("Shell", listOf("sh", "bash", "zsh"), "#"),
    SQL("SQL", listOf("sql"), "--");

    companion object {
        fun fromFileName(name: String): Language {
            val lower = name.lowercase()
            val ext = lower.substringAfterLast('.', "")
            return values().firstOrNull { ext in it.extensions } ?: PLAIN
        }
    }
}

object SyntaxHighlighter {

    private const val MAX_CHARS = 400_000

    private class Rule(val regex: Regex, val token: Token, val group: Int = 0)

    private fun kw(vararg words: String) =
        Regex("\\b(?:" + words.joinToString("|") + ")\\b")

    private val DQ = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
    private val SQ = Regex("'(?:\\\\.|[^'\\\\\\n])*'")
    private val NUM = Regex("\\b(?:0[xX][0-9a-fA-F_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFlLuU]*)\\b")
    private val BLOCK_COMMENT = Regex("/\\*[\\s\\S]*?(?:\\*/|\\z)")
    private val LINE_SLASH = Regex("//[^\\n]*")
    private val LINE_HASH = Regex("#[^\\n]*")
    private val TYPE_CAP = Regex("\\b[A-Z][A-Za-z0-9_]*\\b")
    private val FUNC_CALL = Regex("\\b([a-zA-Z_][A-Za-z0-9_]*)(?=\\()", RegexOption.MULTILINE)

    private val rules: Map<Language, List<Rule>> = mapOf(
        Language.KOTLIN to listOf(
            Rule(BLOCK_COMMENT, Token.COMMENT), Rule(LINE_SLASH, Token.COMMENT),
            Rule(Regex("\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)"), Token.STRING),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("@[A-Za-z_][A-Za-z0-9_]*"), Token.ANNOTATION),
            Rule(kw("fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for",
                "while", "do", "return", "break", "continue", "in", "is", "as", "import", "package",
                "private", "public", "protected", "internal", "override", "open", "abstract", "final",
                "sealed", "data", "enum", "companion", "const", "lateinit", "by", "init", "try", "catch",
                "finally", "throw", "null", "true", "false", "this", "super", "suspend", "inline",
                "typealias", "operator", "vararg", "where", "out"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER), Rule(TYPE_CAP, Token.TYPE), Rule(FUNC_CALL, Token.FUNCTION, 1)
        ),
        Language.JAVA to listOf(
            Rule(BLOCK_COMMENT, Token.COMMENT), Rule(LINE_SLASH, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("@[A-Za-z_][A-Za-z0-9_]*"), Token.ANNOTATION),
            Rule(kw("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
                "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
                "throw", "throws", "transient", "try", "void", "volatile", "while", "var", "record",
                "null", "true", "false"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER), Rule(TYPE_CAP, Token.TYPE), Rule(FUNC_CALL, Token.FUNCTION, 1)
        ),
        Language.PYTHON to listOf(
            Rule(Regex("\"\"\"[\\s\\S]*?(?:\"\"\"|\\z)"), Token.STRING),
            Rule(Regex("'''[\\s\\S]*?(?:'''|\\z)"), Token.STRING),
            Rule(LINE_HASH, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("@[A-Za-z_][A-Za-z0-9_.]*"), Token.ANNOTATION),
            Rule(kw("and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
                "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
                "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with",
                "yield", "None", "True", "False", "self", "match", "case"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER), Rule(TYPE_CAP, Token.TYPE), Rule(FUNC_CALL, Token.FUNCTION, 1)
        ),
        Language.JAVASCRIPT to listOf(
            Rule(BLOCK_COMMENT, Token.COMMENT), Rule(LINE_SLASH, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("`(?:\\\\.|[^`\\\\])*`"), Token.STRING),
            Rule(kw("async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
                "default", "delete", "do", "else", "export", "extends", "finally", "for", "function",
                "if", "import", "in", "instanceof", "let", "new", "of", "return", "static", "super",
                "switch", "this", "throw", "try", "typeof", "var", "void", "while", "with", "yield",
                "null", "undefined", "true", "false", "interface", "type", "enum", "implements",
                "public", "private", "protected", "readonly", "as", "from"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER), Rule(TYPE_CAP, Token.TYPE), Rule(FUNC_CALL, Token.FUNCTION, 1)
        ),
        Language.C_CPP to listOf(
            Rule(BLOCK_COMMENT, Token.COMMENT), Rule(LINE_SLASH, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("^[ \\t]*#[ \\t]*\\w+", RegexOption.MULTILINE), Token.ANNOTATION),
            Rule(kw("auto", "break", "case", "char", "class", "const", "constexpr", "continue", "default",
                "do", "double", "else", "enum", "explicit", "extern", "float", "for", "friend", "goto",
                "if", "inline", "int", "long", "namespace", "new", "delete", "operator", "private",
                "protected", "public", "register", "return", "short", "signed", "sizeof", "static",
                "struct", "switch", "template", "this", "typedef", "typename", "union", "unsigned",
                "using", "virtual", "void", "volatile", "while", "bool", "true", "false", "nullptr",
                "NULL", "byte", "String"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER), Rule(TYPE_CAP, Token.TYPE), Rule(FUNC_CALL, Token.FUNCTION, 1)
        ),
        Language.HTML to listOf(
            Rule(Regex("<!--[\\s\\S]*?(?:-->|\\z)"), Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("</?[A-Za-z][A-Za-z0-9:-]*|/?>|<!DOCTYPE"), Token.TAG),
            Rule(Regex("\\b[A-Za-z-:]+(?==)"), Token.ATTR)
        ),
        Language.XML to listOf(
            Rule(Regex("<!--[\\s\\S]*?(?:-->|\\z)"), Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("</?[A-Za-z_][A-Za-z0-9_:.-]*|/?>|<\\?xml|\\?>"), Token.TAG),
            Rule(Regex("\\b[A-Za-z_][A-Za-z0-9_:.-]*(?==)"), Token.ATTR)
        ),
        Language.CSS to listOf(
            Rule(BLOCK_COMMENT, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("[.#][A-Za-z_-][A-Za-z0-9_-]*(?=[^;]*\\{)"), Token.TYPE),
            Rule(Regex("[A-Za-z-]+(?=\\s*:)"), Token.ATTR),
            Rule(Regex("#[0-9a-fA-F]{3,8}\\b|\\b\\d+(?:\\.\\d+)?(?:px|em|rem|%|vh|vw|s|ms|deg)?"), Token.NUMBER),
            Rule(Regex("@[A-Za-z-]+"), Token.ANNOTATION)
        ),
        Language.JSON to listOf(
            Rule(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"(?=\\s*:)"), Token.ATTR),
            Rule(DQ, Token.STRING),
            Rule(kw("true", "false", "null"), Token.KEYWORD),
            Rule(Regex("-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?"), Token.NUMBER)
        ),
        Language.YAML to listOf(
            Rule(LINE_HASH, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("^[ \\t-]*[A-Za-z_][A-Za-z0-9_.-]*(?=\\s*:)", RegexOption.MULTILINE), Token.ATTR),
            Rule(kw("true", "false", "null", "yes", "no"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER)
        ),
        Language.SHELL to listOf(
            Rule(LINE_HASH, Token.COMMENT),
            Rule(DQ, Token.STRING), Rule(SQ, Token.STRING),
            Rule(Regex("\\$\\{?[A-Za-z_][A-Za-z0-9_]*\\}?"), Token.TYPE),
            Rule(kw("if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac",
                "function", "return", "in", "echo", "export", "local", "exit", "source", "cd"), Token.KEYWORD),
            Rule(NUM, Token.NUMBER)
        ),
        Language.SQL to listOf(
            Rule(BLOCK_COMMENT, Token.COMMENT), Rule(Regex("--[^\\n]*"), Token.COMMENT),
            Rule(SQ, Token.STRING), Rule(DQ, Token.STRING),
            Rule(Regex("\\b(?:select|from|where|insert|into|values|update|set|delete|create|table|drop|alter|" +
                "join|inner|left|right|outer|on|group|by|order|having|limit|as|and|or|not|null|primary|key|" +
                "foreign|references|index|distinct|union|all|case|when|then|else|end|like|in|is|between|" +
                "exists|count|sum|avg|min|max|asc|desc)\\b", RegexOption.IGNORE_CASE), Token.KEYWORD),
            Rule(NUM, Token.NUMBER)
        )
    )

    fun highlight(text: Editable, language: Language, colors: EditorColors) {
        for (s in text.getSpans(0, text.length, SyntaxSpan::class.java)) text.removeSpan(s)
        val list = rules[language] ?: return
        if (text.length > MAX_CHARS) return

        val src = text.toString()
        val claimed = BitSet(src.length)
        for (rule in list) {
            val color = colors.tokens[rule.token] ?: continue
            for (m in rule.regex.findAll(src)) {
                val range = if (rule.group == 0) m.range else (m.groups[rule.group]?.range ?: continue)
                if (range.isEmpty()) continue
                val start = range.first
                val endEx = range.last + 1
                if (!claimed.get(start, endEx).isEmpty) continue
                claimed.set(start, endEx)
                text.setSpan(SyntaxSpan(color), start, endEx, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}
