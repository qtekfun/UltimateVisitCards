package com.qtekfun.ultimatevisitcards.data.contacts

import android.Manifest
import android.content.ContentUris
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.qtekfun.ultimatevisitcards.domain.export.VCardContact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On a device: a contact written by the app appears in the phone's contacts with every field. Reading them
 * back needs READ_CONTACTS, granted to the test only (the app never asks for it).
 */
@RunWith(AndroidJUnit4::class)
class PhoneContactsWriterTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.WRITE_CONTACTS, Manifest.permission.READ_CONTACTS)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var rawContactId: Long? = null

    @After
    fun cleanUp() {
        rawContactId?.let {
            context.contentResolver.delete(
                ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, it).buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
                null,
                null
            )
        }
    }

    @Test
    fun everyFieldReachesTheAddressBook() {
        val contact = VCardContact(
            firstName = "Ana",
            lastName = "Ruiz Pérez",
            jobTitle = "Directora Comercial",
            companyName = "Navaltech S.L.",
            phone = "+34 910 123 456",
            mobile = "+34 600 111 222",
            email = "ana@navaltech-demo.es",
            website = "www.navaltech-demo.es",
            address = "C/ Ficticia 12, 28001 Madrid",
            country = "Spain",
            notes = "Conocida en SMM 2026"
        )
        val result = PhoneContactsWriter(context).add(contact)
        assertTrue("$result", result is PhoneContactResult.Added)
        val id = (result as PhoneContactResult.Added).rawContactId
        rawContactId = id

        val rows = mutableMapOf<String, MutableList<Map<String, String?>>>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
            arrayOf(id.toString()),
            null
        )!!.use { cursor ->
            while (cursor.moveToNext()) {
                val mime = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE))
                val row = (1..15).associate { n -> "data$n" to cursor.getString(cursor.getColumnIndexOrThrow("data$n")) }
                rows.getOrPut(mime) { mutableListOf() } += row
            }
        }
        val name = rows.getValue(StructuredName.CONTENT_ITEM_TYPE).single()
        assertEquals("Ana", name[StructuredName.GIVEN_NAME])
        assertEquals("Ruiz Pérez", name[StructuredName.FAMILY_NAME])
        val phones = rows.getValue(Phone.CONTENT_ITEM_TYPE).associate { it[Phone.TYPE] to it[Phone.NUMBER] }
        assertEquals("+34 910 123 456", phones[Phone.TYPE_WORK.toString()])
        assertEquals("+34 600 111 222", phones[Phone.TYPE_MOBILE.toString()])
        assertEquals("ana@navaltech-demo.es", rows.getValue(Email.CONTENT_ITEM_TYPE).single()[Email.ADDRESS])
        val org = rows.getValue(Organization.CONTENT_ITEM_TYPE).single()
        assertEquals("Navaltech S.L.", org[Organization.COMPANY])
        assertEquals("Directora Comercial", org[Organization.TITLE])
        assertEquals("www.navaltech-demo.es", rows.getValue(Website.CONTENT_ITEM_TYPE).single()[Website.URL])
        val postal = rows.getValue(StructuredPostal.CONTENT_ITEM_TYPE).single()
        assertEquals("C/ Ficticia 12, 28001 Madrid", postal[StructuredPostal.STREET])
        assertEquals("Spain", postal[StructuredPostal.COUNTRY])
        assertEquals("Conocida en SMM 2026", rows.getValue(Note.CONTENT_ITEM_TYPE).single()[Note.NOTE])
    }
}
