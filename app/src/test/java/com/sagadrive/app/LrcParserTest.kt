package com.sagadrive.app

import com.sagadrive.app.feature.lyrics.LrcParser
import com.sagadrive.app.feature.lyrics.LyricsSynchronizer
import org.junit.Assert.*
import org.junit.Test

class LrcParserTest {
    @Test fun parsesBasicLines() {
        val l = LrcParser.parse("[00:01.50]Hello\n[00:03.00]World")
        assertEquals(listOf(1500L, 3000L), l.map { it.timeMs })
        assertEquals("Hello", l[0].text)
    }
    @Test fun multipleTagsAndSorting() {
        val l = LrcParser.parse("[00:20.00][00:08.00]Chorus\n[00:10.00]Verse")
        assertEquals(listOf(8000L, 10000L, 20000L), l.map { it.timeMs })
    }
    @Test fun fractionVariants() {
        assertEquals(500L, LrcParser.parse("[00:00.5]a").single().timeMs)
        assertEquals(50L, LrcParser.parse("[00:00.05]a").single().timeMs)
        assertEquals(0L, LrcParser.parse("[00:00]a").single().timeMs)
    }
    @Test fun skipsMetadataAndMalformed() {
        val l = LrcParser.parse("[ti:Title]\n[ar:Artist]\nplain text\n[xx:yy]bad\n[00:99.00]badsec\n[00:02.00]ok")
        assertEquals(1, l.size)
        assertEquals("ok", l[0].text)
    }
    @Test fun unicodeAndBom() {
        val l = LrcParser.parse("\uFEFF[00:01.00]Selamat pagi, sayangku — café")
        assertEquals("Selamat pagi, sayangku — café", l.single().text)
    }
    @Test fun emptyInput() = assertTrue(LrcParser.parse("").isEmpty())

    @Test fun synchronizerUsesPositionAndOffset() {
        val l = LrcParser.parse("[00:01.00]a\n[00:05.00]b\n[00:09.00]c")
        assertEquals(-1, LyricsSynchronizer.indexAt(l, 500))
        assertEquals(0, LyricsSynchronizer.indexAt(l, 1000))
        assertEquals(1, LyricsSynchronizer.indexAt(l, 8999))
        assertEquals(2, LyricsSynchronizer.indexAt(l, 8500, offsetMs = 600))
        assertEquals(-1, LyricsSynchronizer.indexAt(emptyList(), 1000))
    }
}
