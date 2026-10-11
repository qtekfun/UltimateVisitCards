package com.qtekfun.ultimatevisitcards.ui.common

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/** Opens documents the user picked with the system file picker (SAF): no storage permission needed. */
class Documents @Inject constructor(@ApplicationContext private val context: Context) {
    /** Writes to [uri] (call it off the main thread); the stream is closed afterwards. */
    suspend fun <T> write(uri: Uri, block: suspend (OutputStream) -> T): T {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        return stream.use { block(it) }
    }
}
