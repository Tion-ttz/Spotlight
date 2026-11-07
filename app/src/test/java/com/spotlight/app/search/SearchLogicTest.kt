package com.spotlight.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.spotlight.app.core.SearchEngine

class CalculatorTest {

    private fun eval(expr: String) = Calculator.evaluate(expr)?.replace(",", "")

    @Test
    fun basicArithmetic() {
        assertEquals("3", eval("1+2"))
        assertEquals("6", eval("2*3"))
        assertEquals("0.5", eval("1/2"))
        assertEquals("2.5", eval("5 - 2.5"))
        assertEquals("7", eval("1 + 2 * 3"))
        assertEquals("9", eval("(1 + 2) * 3"))
    }

    @Test
    fun powerAndFunctions() {
        assertEquals("8", eval("2^3"))
        assertEquals("4", eval("sqrt(16)"))
        assertEquals("0", eval("sin(0)"))
        assertEquals("1", eval("cos(0)"))
        assertEquals("3.14159", eval("pi").let { it?.take(7) })
        assertNotNull(eval("log(100)"))
    }

    @Test
    fun percentage() {
        assertEquals("25", eval("100*25%"))
    }

    @Test
    fun invalidExpressionsReturnNull() {
        assertNull(Calculator.evaluate("1+"))
        assertNull(Calculator.evaluate("abc"))
        assertNull(Calculator.evaluate("1/0"))
    }

    @Test
    fun expressionDetection() {
        assertTrue(Calculator.looksLikeExpression("1+2"))
        assertTrue(Calculator.looksLikeExpression("sqrt(9)"))
        assertTrue(!Calculator.looksLikeExpression("hello"))
        assertTrue(!Calculator.looksLikeExpression("微信"))
    }
}

class FuzzyTest {

    @Test
    fun scoringOrder() {
        assertEquals(1000, Fuzzy.score("abc", "abc"))
        assertTrue(Fuzzy.score("abc", "abcdef") in 800..900)
        assertTrue(Fuzzy.score("def", "abcdef") in 700..800)
        assertEquals(0, Fuzzy.score("xyz", "abcdef"))
    }

    @Test
    fun highlightRanges() {
        val ranges = Fuzzy.highlightRanges("def", "abcdef")
        assertEquals(1, ranges.size)
        assertEquals(3, ranges[0].first)
    }

    @Test
    fun multiKeywordTakesWorst() {
        val score = Fuzzy.score("we chat", "WeChat")
        assertTrue(score > 0)
    }
}

class SearchEngineTest {

    @Test
    fun encodesQuery() {
        val url = SearchEngine.GOOGLE.buildUrl("hello world")
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("hello+world"))
    }

    @Test
    fun indexRoundTrip() {
        assertEquals(SearchEngine.BING, SearchEngine.fromIndex(1))
        assertEquals(SearchEngine.GOOGLE, SearchEngine.fromIndex(99))
    }
}
