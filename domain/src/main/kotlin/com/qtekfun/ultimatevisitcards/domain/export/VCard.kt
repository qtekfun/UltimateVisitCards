package com.qtekfun.ultimatevisitcards.domain.export

import java.io.StringWriter
import java.io.Writer

/** What a vCard carries for one contact (docs/05 §4). Blank values are left out. */
data class VCardContact(
    val firstName: String = "",
    val lastName: String = "",
    val jobTitle: String? = null,
    val companyName: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: String? = null,
    val country: String? = null,
    val notes: String? = null
)

/**
 * vCard 3.0 (RFC 2426) writer: UTF-8 text, CRLF line endings, values escaped (`\`, `,`, `;`, newlines) and
 * lines folded at 75 octets without splitting a character. The card photo is not included (off by default
 * in docs/05 and not supported yet).
 */
object VCardWriter {
    private const val MAX_OCTETS = 75
    private const val CRLF = "\r\n"

    /** Writes one card per contact to [out] and flushes it. */
    fun write(contacts: List<VCardContact>, out: Writer) {
        contacts.forEach { writeCard(it, out) }
        out.flush()
    }

    fun toString(contact: VCardContact): String = StringWriter().also { writeCard(contact, it) }.toString()

    private fun writeCard(contact: VCardContact, out: Writer) {
        fun line(text: String) = out.write(fold(text) + CRLF)
        fun property(name: String, value: String?) {
            if (!value.isNullOrBlank()) line("$name:${escape(value.trim())}")
        }
        line("BEGIN:VCARD")
        line("VERSION:3.0")
        line("FN:${escape(displayName(contact))}")
        line("N:${escape(contact.lastName.trim())};${escape(contact.firstName.trim())};;;")
        property("ORG", contact.companyName)
        property("TITLE", contact.jobTitle)
        property("TEL;TYPE=WORK,VOICE", contact.phone)
        property("TEL;TYPE=CELL", contact.mobile)
        property("EMAIL;TYPE=INTERNET,WORK", contact.email)
        property("URL", contact.website)
        if (!contact.address.isNullOrBlank() || !contact.country.isNullOrBlank()) {
            // ADR components: PO box; extended; street; locality; region; postal code; country.
            line("ADR;TYPE=WORK:;;${escape(contact.address.orEmpty().trim())};;;;${escape(contact.country.orEmpty().trim())}")
        }
        property("NOTE", contact.notes)
        line("END:VCARD")
    }

    /** FN is mandatory: the name, else the company, else the email. */
    fun displayName(contact: VCardContact): String = "${contact.firstName.trim()} ${contact.lastName.trim()}".trim()
        .ifEmpty { contact.companyName?.trim().orEmpty() }
        .ifEmpty { contact.email?.trim().orEmpty() }

    internal fun escape(value: String): String = buildString {
        for (c in value.replace("\r\n", "\n").replace('\r', '\n')) {
            when (c) {
                '\\' -> append("\\\\")
                ',' -> append("\\,")
                ';' -> append("\\;")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    /** Splits [line] into physical lines of at most 75 UTF-8 octets; continuation lines start with a space. */
    internal fun fold(line: String): String {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var octets = 0
        var limit = MAX_OCTETS
        var i = 0
        while (i < line.length) {
            val codePoint = line.codePointAt(i)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                parts += current.toString()
                current.clear()
                octets = 0
                // The leading space of a continuation line counts towards its 75 octets.
                limit = MAX_OCTETS - 1
            }
            current.appendCodePoint(codePoint)
            octets += size
            i += Character.charCount(codePoint)
        }
        parts += current.toString()
        return parts.joinToString("$CRLF ")
    }
}
