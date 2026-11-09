package com.waenhancer.web

/** Minimal JSON writing and reading, so the web server has no Android dependency. */
object WebJson {

    fun quote(value: String?): String {
        if (value == null) return "null"
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                // '<' is escaped so JSON is safe even if a browser ever treats it as HTML.
                '<' -> sb.append("\\u003c")
                else -> if (ch < ' ' || ch == ' ' || ch == ' ') {
                    sb.append("\\u").append(String.format("%04x", ch.code))
                } else sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }

    /** Builds an object from pairs; values are String, Number, Boolean, null, [Raw], Map or List. */
    fun obj(vararg pairs: Pair<String, Any?>): String = value(linkedMapOf(*pairs))

    /** Already encoded JSON to embed as is. */
    class Raw(val json: String)

    fun value(v: Any?): String = when (v) {
        null -> "null"
        is Raw -> v.json
        is String -> quote(v)
        is Boolean -> v.toString()
        is Double -> if (v.isNaN() || v.isInfinite()) "null" else v.toString()
        is Float -> if (v.isNaN() || v.isInfinite()) "null" else v.toString()
        is Number -> v.toString()
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { quote(it.key.toString()) + ":" + value(it.value) }
        is Iterable<*> -> v.joinToString(",", "[", "]") { value(it) }
        else -> quote(v.toString())
    }

    class ParseError(message: String) : Exception(message)

    /** Parses JSON into Map, List, String, Double, Boolean or null. */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipSpace()
        val v = p.readValue(0)
        p.skipSpace()
        if (p.pos != text.length) throw ParseError("trailing data")
        return v
    }

    /** Parses a JSON object; anything else gives an empty map. */
    fun parseObject(text: String): Map<String, Any?> = try {
        @Suppress("UNCHECKED_CAST")
        (parse(text) as? Map<String, Any?>) ?: emptyMap()
    } catch (_: ParseError) {
        emptyMap()
    }

    private class Parser(val s: String) {
        var pos = 0

        fun skipSpace() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun readValue(depth: Int): Any? {
            if (depth > 16) throw ParseError("too deep")
            if (pos >= s.length) throw ParseError("unexpected end")
            return when (val c = s[pos]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c in '0'..'9') readNumber() else throw ParseError("unexpected character")
            }
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseError("bad literal")
            pos += word.length
            return value
        }

        private fun readNumber(): Double {
            val start = pos
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] in "+-.eE")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw ParseError("bad number")
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw ParseError("unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw ParseError("bad escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000c')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseError("bad unicode escape")
                                val code = s.substring(pos, pos + 4).toIntOrNull(16) ?: throw ParseError("bad unicode escape")
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> throw ParseError("bad escape")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            pos++
            val map = LinkedHashMap<String, Any?>()
            skipSpace()
            if (pos < s.length && s[pos] == '}') { pos++; return map }
            while (true) {
                skipSpace()
                if (pos >= s.length || s[pos] != '"') throw ParseError("expected key")
                val key = readString()
                skipSpace()
                if (pos >= s.length || s[pos] != ':') throw ParseError("expected colon")
                pos++
                skipSpace()
                map[key] = readValue(depth + 1)
                skipSpace()
                if (pos >= s.length) throw ParseError("unterminated object")
                when (s[pos++]) {
                    ',' -> continue
                    '}' -> return map
                    else -> throw ParseError("expected comma")
                }
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            pos++
            val list = ArrayList<Any?>()
            skipSpace()
            if (pos < s.length && s[pos] == ']') { pos++; return list }
            while (true) {
                skipSpace()
                list.add(readValue(depth + 1))
                skipSpace()
                if (pos >= s.length) throw ParseError("unterminated array")
                when (s[pos++]) {
                    ',' -> continue
                    ']' -> return list
                    else -> throw ParseError("expected comma")
                }
            }
        }
    }
}
