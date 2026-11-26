package com.example.data

/**
 * A single saved cart line. Only the product id and quantity are persisted —
 * prices are never stored; the live Firestore price always wins on restore.
 */
data class SavedCartEntry(val productId: String, val quantity: Int)

/**
 * Minimal JSON encode/decode for the saved cart.
 *
 * Pure Kotlin with no Android and no org.json dependency (org.json's JVM
 * stubs throw in unit tests — see the v1.0.12 Cloudinary fix). Fully
 * defensive: any malformed input decodes to an empty list and never throws.
 *
 * Format: [{"id":"<productId>","qty":<quantity>}, ...]
 */
object CartJson {

  fun encode(entries: List<SavedCartEntry>): String {
    val clean = entries.filter { it.productId.isNotBlank() && it.quantity >= 1 }
    return buildString {
      append('[')
      clean.forEachIndexed { index, entry ->
        if (index > 0) append(',')
        append("{\"id\":\"")
        append(escape(entry.productId))
        append("\",\"qty\":")
        append(entry.quantity)
        append('}')
      }
      append(']')
    }
  }

  fun decode(raw: String?): List<SavedCartEntry> {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return emptyList()
    return runCatching { CartParser(text).parse() }.getOrElse { emptyList() }
  }

  private fun escape(value: String): String {
    val out = StringBuilder(value.length)
    for (ch in value) {
      when (ch) {
        '\\' -> out.append("\\\\")
        '"' -> out.append("\\\"")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\t' -> out.append("\\t")
        else -> out.append(ch)
      }
    }
    return out.toString()
  }

  /** Strict little parser for exactly the format [encode] writes. */
  private class CartParser(private val text: String) {
    private var pos = 0

    fun parse(): List<SavedCartEntry> {
      skipWhitespace()
      expect('[')
      skipWhitespace()
      val entries = mutableListOf<SavedCartEntry>()
      val seenIds = mutableSetOf<String>()
      if (consume(']')) {
        expectEnd()
        return entries
      }
      while (true) {
        skipWhitespace()
        val entry = parseEntry()
        if (entry != null && seenIds.add(entry.productId)) entries.add(entry)
        skipWhitespace()
        when {
          consume(',') -> continue
          consume(']') -> break
          else -> throw IllegalStateException("Expected ',' or ']' at $pos")
        }
      }
      expectEnd()
      return entries
    }

    /** Returns null for an entry that is well-formed but unusable (blank id, qty < 1). */
    private fun parseEntry(): SavedCartEntry? {
      expect('{')
      skipWhitespace()
      var id: String? = null
      var qty: Int? = null
      if (consume('}')) return null // Empty object: skip it.
      while (true) {
        skipWhitespace()
        val key = parseString()
        skipWhitespace()
        expect(':')
        skipWhitespace()
        when (key) {
          "id" -> id = parseString()
          "qty" -> qty = parseNumber()
          else -> skipValue()
        }
        skipWhitespace()
        when {
          consume(',') -> continue
          consume('}') -> break
          else -> throw IllegalStateException("Expected ',' or '}' at $pos")
        }
      }
      val cleanId = id?.trim().orEmpty()
      val cleanQty = qty ?: 0
      if (cleanId.isEmpty() || cleanQty < 1) return null
      return SavedCartEntry(cleanId, cleanQty)
    }

    private fun parseString(): String {
      expect('"')
      val out = StringBuilder()
      while (pos < text.length) {
        val ch = text[pos++]
        when (ch) {
          '"' -> return out.toString()
          '\\' -> {
            if (pos >= text.length) throw IllegalStateException("Dangling escape")
            when (val esc = text[pos++]) {
              '"' -> out.append('"')
              '\\' -> out.append('\\')
              '/' -> out.append('/')
              'b' -> out.append('\b')
              'f' -> out.append('\u000C')
              'n' -> out.append('\n')
              'r' -> out.append('\r')
              't' -> out.append('\t')
              'u' -> {
                if (pos + 4 > text.length) throw IllegalStateException("Bad unicode escape")
                val hex = text.substring(pos, pos + 4)
                val code =
                  hex.toIntOrNull(16) ?: throw IllegalStateException("Bad unicode escape")
                out.append(code.toChar())
                pos += 4
              }
              else -> throw IllegalStateException("Unknown escape \\$esc")
            }
          }
          else -> out.append(ch)
        }
      }
      throw IllegalStateException("Unterminated string")
    }

    private fun parseNumber(): Int {
      val start = pos
      if (pos < text.length && text[pos] == '-') pos++
      while (pos < text.length && text[pos].isDigit()) pos++
      if (pos == start || (pos == start + 1 && text[start] == '-')) {
        throw IllegalStateException("Expected number at $start")
      }
      return text.substring(start, pos).toIntOrNull()
        ?: throw IllegalStateException("Number out of range at $start")
    }

    private fun skipValue() {
      skipWhitespace()
      when {
        pos < text.length && text[pos] == '"' -> parseString()
        pos < text.length && (text[pos] == '-' || text[pos].isDigit()) -> parseNumber()
        text.startsWith("true", pos) -> pos += 4
        text.startsWith("false", pos) -> pos += 5
        text.startsWith("null", pos) -> pos += 4
        else -> throw IllegalStateException("Unexpected value at $pos")
      }
    }

    private fun skipWhitespace() {
      while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? = if (pos < text.length) text[pos] else null

    private fun consume(expected: Char): Boolean {
      if (peek() == expected) {
        pos++
        return true
      }
      return false
    }

    private fun expect(expected: Char) {
      if (peek() != expected) throw IllegalStateException("Expected '$expected' at $pos")
      pos++
    }

    private fun expectEnd() {
      skipWhitespace()
      if (pos != text.length) throw IllegalStateException("Trailing content at $pos")
    }
  }
}
