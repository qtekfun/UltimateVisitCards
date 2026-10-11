package com.qtekfun.ultimatevisitcards.domain.text

import kotlin.test.assertEquals
import org.junit.Test

class CompanyNameNormalizerTest {
    private val normalizer = CompanyNameNormalizer.fromBundledDictionary()

    @Test
    fun `strips legal forms from every supported country`() {
        val cases = mapOf(
            "ABC Marine GmbH" to "abc marine",
            "Hamburg Süd GmbH & Co. KG" to "hamburg sud",
            "Navantia, S.A." to "navantia",
            "Navantia SA" to "navantia",
            "Astilleros del Norte S.L.U." to "astilleros del norte",
            "Fincantieri S.p.A." to "fincantieri",
            "Wärtsilä Oyj" to "wartsila",
            "Maersk A/S" to "maersk",
            "Damen Shipyards B.V." to "damen shipyards",
            "Hyundai Heavy Industries Co., Ltd." to "hyundai heavy industries",
            "Chantiers de l'Atlantique SAS" to "chantiers de l atlantique",
            "Acme Marine Inc." to "acme marine"
        )
        cases.forEach { (name, expected) -> assertEquals(expected, normalizer.normalize(name), name) }
    }

    @Test
    fun `the same company with different spellings has the same key`() {
        assertEquals(normalizer.normalize("ABC MARINE"), normalizer.normalize("abc marine gmbh"))
        assertEquals(normalizer.normalize("Électronique Navale SARL"), normalizer.normalize("electronique navale"))
    }

    @Test
    fun `never strips the whole name`() {
        assertEquals("ag", normalizer.normalize("AG"))
        assertEquals("co", normalizer.normalize("Co."))
    }

    @Test
    fun `only strips trailing legal forms`() {
        assertEquals("sa coruna logistics", normalizer.normalize("SA Coruña Logistics Ltd"))
    }

    @Test
    fun `blank and symbol-only names normalise to empty`() {
        assertEquals("", normalizer.normalize("  "))
        assertEquals("", normalizer.normalize("—"))
    }
}
