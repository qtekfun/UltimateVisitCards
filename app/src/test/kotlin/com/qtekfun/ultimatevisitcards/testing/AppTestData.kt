package com.qtekfun.ultimatevisitcards.testing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.qtekfun.ultimatevisitcards.data.db.AppDatabase
import com.qtekfun.ultimatevisitcards.data.repo.RoomBusinessCardRepository
import com.qtekfun.ultimatevisitcards.data.repo.RoomEventRepository
import com.qtekfun.ultimatevisitcards.data.repo.SearchIndexer
import com.qtekfun.ultimatevisitcards.data.storage.ImageStore
import com.qtekfun.ultimatevisitcards.domain.util.Clock
import com.qtekfun.ultimatevisitcards.domain.util.IdGenerator
import com.qtekfun.ultimatevisitcards.work.OcrScheduler
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher

/** Remembers the cards put in the OCR queue. */
class RecordingScheduler : OcrScheduler {
    val enqueued = mutableListOf<String>()

    override fun enqueue(cardId: String) {
        enqueued += cardId
    }
}

/** In-memory database, repositories and an image store in [root], all on [context] (the test dispatcher). */
class AppTestData(context: CoroutineDispatcher, root: File) {
    val app: Context = ApplicationProvider.getApplicationContext()
    val db: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(app)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(context)
        .build()

    /** Ticks one second per read, so creation order is visible in «newest first» lists. */
    val clock = object : Clock {
        private var now = 1_767_225_600_000L

        override fun nowMillis(): Long = now.also { now += 1_000 }
    }
    val indexer = SearchIndexer(db)
    val cards = RoomBusinessCardRepository(db, indexer, clock, IdGenerator.Uuid)
    val events = RoomEventRepository(db, indexer, clock, IdGenerator.Uuid)
    val store = ImageStore(File(root, "files").apply { mkdirs() }, File(root, "cache").apply { mkdirs() }, context)

    fun close() = db.close()

    companion object {
        /** A small synthetic card on a dark table (fast to process, no real data). */
        fun cardPhoto(width: Int = 480, height: Int = 640): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            val canvas = Canvas(this)
            canvas.drawColor(Color.rgb(60, 50, 40))
            canvas.drawRect(width * 0.1f, height * 0.35f, width * 0.9f, height * 0.65f, Paint().apply { color = Color.rgb(235, 235, 230) })
        }
    }
}
