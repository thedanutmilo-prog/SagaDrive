package com.sagadrive.app.feature.lyrics

data class LrcLine(val timeMs: Long, val text: String)

object LrcParser {
    private val tag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Parses synced LRC text. Metadata tags and malformed lines are skipped, never thrown on. */
    fun parse(raw: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        for (rawLine in raw.lineSequence()) {
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue
            val times = ArrayList<Long>()
            var i = 0
            while (true) {
                val m = tag.matchAt(line, i) ?: break
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                if (sec >= 60) { times.clear(); break }
                val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
                times += min * 60_000 + sec * 1_000 + frac
                i = m.range.last + 1
            }
            if (times.isEmpty()) continue
            val text = line.substring(i).trim()
            times.forEach { out += LrcLine(it, text) }
        }
        return out.sortedBy { it.timeMs }
    }
}

object LyricsSynchronizer {
    /**
     * Index of the line active at [positionMs] (from the real player position), or -1 before the first line.
     * [offsetMs] > 0 makes lyrics appear earlier.
     */
    fun indexAt(lines: List<LrcLine>, positionMs: Long, offsetMs: Long = 0): Int {
        val t = positionMs + offsetMs
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= t) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }
}
