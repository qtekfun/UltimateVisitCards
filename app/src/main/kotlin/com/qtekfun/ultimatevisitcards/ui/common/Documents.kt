package com.qtekfun.ultimatevisitcards.ui.common

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Opens documents the user picked with the system file picker (SAF): no storage permission needed. */
class Documents @Inject constructor(@ApplicationContext private val context: Context) {
    fun openInput(uri: Uri): InputStream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    fun openOutput(uri: Uri): OutputStream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")

    /** Removes a document this app created (an interrupted or failed export). */
    fun delete(uri: Uri) {
        if (uri.scheme == "file") {
            uri.path?.let { java.io.File(it).delete() }
            return
        }
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: java.io.FileNotFoundException) {
            // Already gone.
        } catch (e: IllegalArgumentException) {
            // Not a document provider URI: nothing this app can delete.
        } catch (e: SecurityException) {
            // The provider does not let us delete it; the user sees the failure message.
        } catch (e: UnsupportedOperationException) {
            // The provider does not support deleting.
        }
    }

    /** Writes to [uri] (call it off the main thread); the stream is closed afterwards. */
    suspend fun <T> write(uri: Uri, block: suspend (OutputStream) -> T): T {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        return stream.use { block(it) }
    }
}
