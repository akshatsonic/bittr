package com.bitter.model

object Mention {
    const val PREFIX = '@'

    private val TOKEN = Regex("""@\{([a-z0-9-]+)\}""")

    data class Candidate(val username: String, val displayName: String)

    sealed interface Segment {
        data class Text(val text: String) : Segment
        data class Mention(val username: String, val raw: String) : Segment
    }

    data class Active(val query: String, val range: IntRange)

    data class Token(val username: String, val range: IntRange, val raw: String)

    data class Visualized(
        val text: String,
        val originalToTransformed: IntArray,
        val transformedToOriginal: IntArray,
        val mentionRanges: List<IntRange>,
    )

    fun token(username: String): String = "@{$username}"

    fun findAll(content: String): List<Token> =
        TOKEN.findAll(content).map { Token(it.groupValues[1], it.range, it.value) }.toList()

    fun parse(content: String): List<Segment> {
        val segments = mutableListOf<Segment>()
        var last = 0
        for (t in findAll(content)) {
            if (t.range.first > last) {
                segments.add(Segment.Text(content.substring(last, t.range.first)))
            }
            segments.add(Segment.Mention(t.username, t.raw))
            last = t.range.last + 1
        }
        if (last < content.length) {
            segments.add(Segment.Text(content.substring(last)))
        }
        return segments
    }

    fun activeMention(content: String, cursor: Int): Active? {
        if (cursor < 1) return null
        var i = cursor - 1
        while (i >= 0 && !content[i].isWhitespace() && content[i] != '}') {
            if (content[i] == PREFIX && (i == 0 || content[i - 1] != '{')) {
                return Active(content.substring(i + 1, cursor), i..(cursor - 1))
            }
            i--
        }
        return null
    }

    fun insert(content: String, active: Active, username: String): String =
        content.replaceRange(active.range, token(username))

    fun deleteBefore(content: String, cursor: Int): String {
        if (cursor <= 0) return content
        val last = findAll(content.substring(0, cursor)).lastOrNull()
        if (last != null && last.range.last + 1 == cursor) {
            return content.removeRange(last.range)
        }
        return content.removeRange(cursor - 1, cursor)
    }

    fun visualize(content: String, resolve: (String) -> String): Visualized {
        val tokens = findAll(content)
        val text = StringBuilder()
        val originalToTransformed = IntArray(content.length + 1)
        val mentionRanges = mutableListOf<IntRange>()
        var origPos = 0
        var visPos = 0

        fun mapText(from: Int, to: Int) {
            for (k in from..to) originalToTransformed[k] = visPos + (k - from)
            text.append(content, from, to)
            visPos += (to - from)
            origPos = to
        }

        for (t in tokens) {
            mapText(origPos, t.range.first)
            val visual = "@${resolve(t.username)}"
            val vs = visPos
            text.append(visual)
            visPos += visual.length
            val ve = visPos
            mentionRanges.add(vs until ve)
            val rs = t.range.first
            val re = t.range.last + 1
            originalToTransformed[rs] = vs
            for (k in rs + 1 until re) originalToTransformed[k] = ve
            originalToTransformed[re] = ve
            origPos = re
        }
        mapText(origPos, content.length)

        val transformedToOriginal = IntArray(text.length + 1)
        var tVisStart = 0
        var tOrigStart = 0
        for (t in tokens) {
            val rs = t.range.first
            val re = t.range.last + 1
            val textLen = rs - tOrigStart
            for (d in 0..textLen) transformedToOriginal[tVisStart + d] = tOrigStart + d
            tVisStart += textLen
            tOrigStart = rs
            val vs = tVisStart
            val ve = tVisStart + 1 + resolve(t.username).length
            transformedToOriginal[vs] = rs
            for (k in vs + 1..ve) transformedToOriginal[k] = re
            tVisStart = ve
            tOrigStart = re
        }
        val tailLen = content.length - tOrigStart
        for (d in 0..tailLen) transformedToOriginal[tVisStart + d] = tOrigStart + d

        return Visualized(text.toString(), originalToTransformed, transformedToOriginal, mentionRanges)
    }

    fun filter(candidates: List<Candidate>, query: String): List<Candidate> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return candidates
        return candidates.filter {
            it.displayName.lowercase().startsWith(q) || it.username.lowercase().startsWith(q)
        }
    }
}
