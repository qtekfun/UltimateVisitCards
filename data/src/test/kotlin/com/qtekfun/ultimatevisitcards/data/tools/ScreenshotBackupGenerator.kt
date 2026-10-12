package com.qtekfun.ultimatevisitcards.data.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qtekfun.ultimatevisitcards.data.backup.BackupExporter
import com.qtekfun.ultimatevisitcards.data.backup.BackupOptions
import com.qtekfun.ultimatevisitcards.data.backup.BackupResult
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.data.testing.TestData
import com.qtekfun.ultimatevisitcards.data.testing.value
import com.qtekfun.ultimatevisitcards.domain.extract.CardField
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedFields
import com.qtekfun.ultimatevisitcards.domain.extract.ExtractedValue
import com.qtekfun.ultimatevisitcards.domain.model.CardSide
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.repository.BusinessCardDraft
import com.qtekfun.ultimatevisitcards.domain.repository.CompanyDraft
import com.qtekfun.ultimatevisitcards.domain.repository.ContactDraft
import com.qtekfun.ultimatevisitcards.domain.repository.EventDraft
import java.io.File
import java.util.UUID
import kotlin.test.assertIs
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Not a test: builds the **synthetic** backups restored on a phone to take the store screenshots
 * (docs/09-publicacion.md). Every company, person, phone and address is invented; emails and websites use
 * the reserved `.example` domain. Skipped unless `UVC_SCREENSHOT_DIR` is set:
 *
 * `UVC_SCREENSHOT_DIR=/tmp/shots ./gradlew :data:testDebugUnitTest --tests '*ScreenshotBackupGenerator'`
 *
 * writes `screenshots-es.zip` and `screenshots-en.zip` (sector names match the ones seeded in each language,
 * so restoring links them instead of duplicating).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotBackupGenerator {
    private val dispatcher = StandardTestDispatcher()
    private val folder = TemporaryFolder().apply { create() }
    private val opened = mutableListOf<TestData>()

    @After
    fun close() {
        opened.forEach { it.close() }
        folder.delete()
    }

    private class Lang(
        val code: String,
        val sectors: List<String>,
        val events: List<Triple<String, String, Long>>,
        /** (feminine, masculine) */
        val titles: List<Pair<String, String>>,
        val notes: List<String>
    )

    private val spanish = Lang(
        "es",
        listOf("Astillero", "Equipamiento naval", "Fabricante de motores", "Electrónica", "Ingeniería", "Servicios marítimos", "Clasificación"),
        listOf(
            Triple("SMM 2026", "Hamburgo", 1_788_220_800_000L),
            Triple("Navalia 2026", "Vigo", 1_779_408_000_000L),
            Triple("Posidonia 2026", "Atenas", 1_780_617_600_000L)
        ),
        listOf(
            "Directora comercial" to "Director comercial", "Jefa de compras" to "Jefe de compras", "Ingeniera naval" to "Ingeniero naval",
            "Directora técnica" to "Director técnico", "Export Manager" to "Export Manager", "Responsable de proyectos" to "Responsable de proyectos",
            "Sales Manager" to "Sales Manager", "Superintendente" to "Superintendente", "CEO" to "CEO", "Key Account Manager" to "Key Account Manager"
        ),
        listOf("Interesado en hélices de paso variable. Enviar oferta antes de diciembre.", "Conocido en la cena del stand. Retomar en enero.", "")
    )

    private val english = Lang(
        "en",
        listOf("Shipyard", "Marine Equipment", "Engine Manufacturer", "Electronics", "Engineering", "Maritime Services", "Classification Society"),
        listOf(
            Triple("SMM 2026", "Hamburg", 1_788_220_800_000L),
            Triple("Navalia 2026", "Vigo", 1_779_408_000_000L),
            Triple("Posidonia 2026", "Athens", 1_780_617_600_000L)
        ),
        listOf(
            "Sales Director", "Head of Purchasing", "Naval Architect", "Technical Director", "Export Manager", "Project Manager",
            "Sales Manager", "Superintendent", "CEO", "Key Account Manager"
        ).map { it to it },
        listOf("Interested in controllable pitch propellers. Send a quote before December.", "Met at the stand dinner. Follow up in January.", "")
    )

    /** name, country, sector index, website host, card colour. */
    private val companies = listOf(
        listOf("Altamar Ingeniería Naval", "Spain", 4, "altamar-naval.example", "#0B5D7A"),
        listOf("Kestrel Propulsion GmbH", "Germany", 2, "kestrel-propulsion.example", "#1F3A5F"),
        listOf("Seongho Heavy Works", "South Korea", 0, "seongho-hw.example", "#8C1C13"),
        listOf("Marea Coatings S.L.", "Spain", 1, "mareacoatings.example", "#2E7D32"),
        listOf("Vento Hydraulics S.r.l.", "Italy", 1, "ventohydraulics.example", "#6A1B9A"),
        listOf("Skarvik Winches AS", "Norway", 1, "skarvik.example", "#37474F"),
        listOf("Okada Marine Electric", "Japan", 3, "okada-me.example", "#C62828"),
        listOf("Lumen Navigation Ltd", "United Kingdom", 3, "lumen-nav.example", "#00695C"),
        listOf("Thalassa Ship Management", "Greece", 5, "thalassa-sm.example", "#1565C0"),
        listOf("Brisa Offshore", "Portugal", 5, "brisa-offshore.example", "#EF6C00"),
        listOf("Nordwerft Kiel", "Germany", 0, "nordwerft.example", "#283593"),
        listOf("Atlas Marine Register", "France", 6, "atlas-register.example", "#4E342E")
    )

    /** first name, last name, feminine (for the Spanish job titles). */
    private val people = listOf(
        Triple("Lucía", "Ferrer", true), Triple("Jonas", "Becker", false), Triple("Min-jun", "Park", false), Triple("Elena", "Salgado", true),
        Triple("Marco", "Bellini", false), Triple("Ingrid", "Solberg", true), Triple("Haruto", "Okada", false), Triple("Olivia", "Hughes", true),
        Triple("Nikos", "Papadakis", false), Triple("Inês", "Carvalho", true), Triple("Felix", "Hartmann", false), Triple("Claire", "Dubois", true),
        Triple("Pablo", "Arenas Mora", false), Triple("Anna", "Krüger", true), Triple("Ji-woo", "Choi", true), Triple("Carmen", "Rivas", true),
        Triple("Giulia", "Conti", true), Triple("Erik", "Lund", false), Triple("Yuki", "Tanaka", true), Triple("James", "Whitfield", false),
        Triple("Eleni", "Georgiou", true), Triple("Tiago", "Moreira", false), Triple("Lena", "Vogel", true), Triple("Julien", "Moreau", false)
    )

    @Test
    fun generate() = runTest(dispatcher) {
        val out = System.getenv("UVC_SCREENSHOT_DIR")
        assumeTrue("UVC_SCREENSHOT_DIR not set: nothing to generate", !out.isNullOrBlank())
        File(out!!).mkdirs()
        listOf(spanish, english).forEach { build(it, File(out, "screenshots-${it.code}.zip")) }
    }

    private suspend fun build(lang: Lang, target: File) {
        val data = TestData(dispatcher).also { opened += it }
        val store = ImageStore(folder.newFolder("${lang.code}-files"), folder.newFolder("${lang.code}-cache"), dispatcher)
        val sectors = lang.sectors.map { data.sectors.create(it).value() }
        val events = lang.events.map { (name, place, start) ->
            data.events.create(EventDraft(name, location = place, startDate = start, endDate = start + 3 * DAY)).value()
        }
        val companyRows = companies.map { c ->
            data.companies.create(CompanyDraft(c[0] as String, sectors[c[2] as Int].id, c[1] as String, website = "www.${c[3]}")).value()
        }

        people.forEachIndexed { i, (first, last, feminine) ->
            val c = companies[i % companies.size]
            val company = companyRows[i % companies.size]
            val event = events[i % events.size]
            val title = lang.titles[i % lang.titles.size].let { if (feminine) it.first else it.second }
            val host = c[3] as String
            val email = "${first.lowercase().filter { it.isLetter() }.take(1)}.${last.substringBefore(' ').lowercase().fold()}@$host"
            val phone = "+34 600 %03d %03d".format((100 + i * 7) % 1000, (200 + i * 31) % 1000)
            data.clock.now = lang.events[i % events.size].third + (i % 3) * DAY + i * 37 * MINUTE
            val contact = data.contacts.create(
                ContactDraft(
                    first,
                    last,
                    jobTitle = title,
                    companyId = company.id,
                    country = c[1] as String,
                    phone = phone,
                    email = email,
                    website = "www.$host",
                    notes = lang.notes[i % lang.notes.size].ifEmpty { null }
                )
            ).value()
            val cardId = UUID.randomUUID().toString()
            saveCard(store, cardId, cardImage(c[0] as String, "$first $last", title, phone, email, "www.$host", c[4] as String))
            data.cards.create(draft(store, cardId, event.id, data.clock.now), id = cardId).value()
            data.cards.updateOcr(cardId, OcrStatus.REVIEWED, rawOcrText = "${c[0]}\n$first $last\n$title\n$phone\n$email", ocrEngine = "mlkit").value()
            data.cards.attachToContact(cardId, contact.id).value()
        }

        // Two cards from the last day of the fair, read but not reviewed yet: the «pending review» queue.
        listOf(
            listOf("Sofía", "Navarro", "Altamar Ingeniería Naval", 0),
            listOf("Henrik", "Aalto", "Skarvik Winches AS", 5)
        ).forEachIndexed { i, (first, last, companyName, index) ->
            val c = companies[index as Int]
            val title = lang.titles[(i + 3) % lang.titles.size].let { if (i == 0) it.first else it.second }
            val email = "${(first as String).lowercase().fold().take(1)}.${(last as String).lowercase()}@${c[3]}"
            val phone = "+34 611 0${40 + i} 7${10 + i}"
            val cardId = UUID.randomUUID().toString()
            val captured = lang.events[0].third + 2 * DAY + (i + 1) * 3 * MINUTE
            saveCard(store, cardId, cardImage(companyName as String, "$first $last", title, phone, email, "www.${c[3]}", c[4] as String))
            data.cards.create(draft(store, cardId, events[0].id, captured), id = cardId).value()
            val fields = ExtractedFields(
                mapOf(
                    CardField.FIRST_NAME to ExtractedValue(first, 0.94f, "$first $last"),
                    CardField.LAST_NAME to ExtractedValue(last, 0.94f, "$first $last"),
                    CardField.JOB_TITLE to ExtractedValue(title, 0.52f, title),
                    CardField.COMPANY to ExtractedValue(companyName, 0.9f, companyName),
                    CardField.PHONE to ExtractedValue(phone, 0.97f, phone),
                    CardField.EMAIL to ExtractedValue(email, 0.99f, email),
                    CardField.WEBSITE to ExtractedValue("www.${c[3]}", 0.96f, "www.${c[3]}"),
                    CardField.COUNTRY to ExtractedValue(c[1] as String, 0.81f, c[1] as String)
                )
            )
            data.cards.updateOcr(
                cardId,
                OcrStatus.PENDING_REVIEW,
                rawOcrText = "$companyName\n$first $last\n$title",
                extractedJson = fields.toJson(),
                ocrEngine = "mlkit"
            )
                .value()
        }

        target.outputStream().use { stream ->
            val result = BackupExporter(data.db, store, data.clock, dispatcher)
                .export(stream, { target.inputStream() }, BackupOptions(appVersion = "screenshots"))
            assertIs<BackupResult.Done>(result)
        }
        println("SCREENSHOT-DATA | ${target.path} | ${target.length() / 1024} KiB")
    }

    private fun String.fold() = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).filter { it.isLetter() && it.code < 128 }

    private fun draft(store: ImageStore, cardId: String, eventId: String, capturedAt: Long) = BusinessCardDraft(
        store.originalPath(cardId, CardSide.FRONT),
        store.processedPath(cardId, CardSide.FRONT),
        eventId = eventId,
        capturedAt = capturedAt
    )

    private suspend fun saveCard(store: ImageStore, cardId: String, card: Bitmap) {
        store.saveProcessed(cardId, CardSide.FRONT, card)
        // The «original»: the same card on a table, as the camera saw it.
        val photo = Bitmap.createBitmap(1400, 1000, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.rgb(92, 74, 58))
            rotate(-4f, 700f, 500f)
            drawBitmap(card, 175f, 160f, null)
        }
        store.saveOriginal(cardId, CardSide.FRONT, photo)
    }

    /** A plain, invented 85 × 55 mm business card: colour band, company, name, title and contact lines. */
    private fun cardImage(company: String, name: String, title: String, phone: String, email: String, web: String, colour: String): Bitmap {
        val accent = Color.parseColor(colour)
        val bitmap = Bitmap.createBitmap(1050, 680, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(250, 249, 246))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = accent
        canvas.drawRect(RectF(0f, 0f, 1050f, 120f), paint)
        fun text(value: String, x: Float, y: Float, size: Float, colour: Int, bold: Boolean = false) {
            paint.color = colour
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(value, x, y, paint)
        }
        text(company.uppercase(), 70f, 80f, 44f, Color.WHITE, bold = true)
        text(name, 70f, 250f, 58f, Color.rgb(33, 33, 33), bold = true)
        text(title, 70f, 310f, 36f, accent)
        text(phone, 70f, 470f, 34f, Color.rgb(66, 66, 66))
        text(email, 70f, 525f, 34f, Color.rgb(66, 66, 66))
        text(web, 70f, 580f, 34f, Color.rgb(66, 66, 66))
        return bitmap
    }

    private companion object {
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
    }
}
