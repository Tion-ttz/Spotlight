package com.spotlight.app.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 有道 jsonapi 的解析测试（离线样本，不联网）。 */
class YoudaoParseTest {

    @Test
    fun parsesWordWithPhoneticAndSenses() {
        val body = """
            {"ec":{"word":[{"usphone":"ˌserənˈdɪpəti","ukphone":"ˌserənˈdɪpəti",
            "trs":[{"tr":[{"l":{"i":["n. 意外发现美好事物的运气，机缘巧合"]}}]},
                   {"tr":[{"l":{"i":["n. 机缘巧合的例子"]}}]}]}]}}
        """.trimIndent()

        val word = YoudaoDictionary.parse(body, "serendipity")
        assertNotNull(word)
        assertEquals("有道词典", word!!.provider)
        assertEquals("ˌserənˈdɪpəti", word.phonetic)
        assertTrue(word.senses.isNotEmpty())
        assertEquals("n.", word.senses[0].partOfSpeech)
        assertEquals("意外发现美好事物的运气，机缘巧合", word.senses[0].definitions[0].definition)
    }

    @Test
    fun parsesPhraseTranslation() {
        val body = """{"fanyi":{"tran":"早上好"}}"""
        val word = YoudaoDictionary.parse(body, "good morning")
        assertNotNull(word)
        assertEquals("早上好", word!!.senses[0].definitions[0].definition)
        assertEquals("有道翻译", word.provider)
    }

    @Test
    fun returnsNullForEmptyBody() {
        assertEquals(null, YoudaoDictionary.parse("{}", "xyz"))
    }
}
