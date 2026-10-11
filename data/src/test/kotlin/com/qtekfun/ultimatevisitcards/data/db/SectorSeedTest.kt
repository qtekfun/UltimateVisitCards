package com.qtekfun.ultimatevisitcards.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.R
import com.qtekfun.ultimatevisitcards.data.testing.FakeClock
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** First launch seeds the editable sector list in the device language. */
@RunWith(AndroidJUnit4::class)
class SectorSeedTest {
    private val dispatcher = StandardTestDispatcher()

    private fun seeded(): TestData {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = SectorSeedCallback(
            names = { context.resources.getStringArray(R.array.default_sectors).toList() },
            clock = FakeClock(),
            ids = IdGenerator.Uuid
        )
        return TestData(dispatcher, callback)
    }

    @Test
    fun `seeds the English list in order`() = runTest(dispatcher) {
        val data = seeded()
        val sectors = data.sectors.observeAll().first()
        assertEquals(14, sectors.size)
        assertEquals("Shipyard", sectors.first().name)
        assertEquals("Other", sectors.last().name)
        assertEquals((0 until 14).toList(), sectors.map { it.sortOrder })
        data.close()
    }

    @Test
    @Config(qualifiers = "es")
    fun `seeds the Spanish list on a Spanish device`() = runTest(dispatcher) {
        val data = seeded()
        val names = data.sectors.observeAll().first().map { it.name }
        assertEquals(listOf("Astillero", "Armador", "Ship Management"), names.take(3))
        assertEquals("Otro", names.last())
        data.close()
    }

    @Test
    fun `seeded sectors are editable and new ones go last`() = runTest(dispatcher) {
        val data = seeded()
        val other = data.sectors.observeAll().first().last()
        data.sectors.rename(other.id, "Others")
        data.sectors.create("Offshore Wind")
        val names = data.sectors.observeAll().first().map { it.name }
        assertEquals(listOf("Others", "Offshore Wind"), names.takeLast(2))
        data.close()
    }
}
