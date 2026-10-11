package com.qtekfun.ultimatevisitcards.data.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.content.OperationApplicationException
import android.os.RemoteException
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import com.qtekfun.ultimatevisitcards.domain.export.VCardContact
import com.qtekfun.ultimatevisitcards.domain.export.VCardWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Outcome of adding a contact to the phone's address book. */
sealed interface PhoneContactResult {
    data class Added(val rawContactId: Long) : PhoneContactResult

    data class Failed(val reason: Reason) : PhoneContactResult

    enum class Reason {
        /** WRITE_CONTACTS not granted: the UI offers the .vcf instead. */
        PERMISSION,

        /** The contacts provider refused or failed. */
        PROVIDER,
    }
}

/**
 * Writes a contact straight into the phone's contacts (docs/05 §5) in one batch: name, phones (work and
 * mobile), email, company and title, website, address and note. The raw contact goes to the device's local
 * or default account (no account type/name).
 *
 * Needs `WRITE_CONTACTS`, requested on demand by the UI when the user taps «Add to contacts». It never
 * reads the address book (no `READ_CONTACTS`); duplicates are avoided with `Contact.exportedToPhoneAt`.
 */
class PhoneContactsWriter @Inject constructor(@ApplicationContext private val context: Context) {
    fun add(contact: VCardContact): PhoneContactResult {
        val ops = operations(contact)
        return try {
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val uri = results.firstOrNull()?.uri ?: return PhoneContactResult.Failed(PhoneContactResult.Reason.PROVIDER)
            PhoneContactResult.Added(ContentUris.parseId(uri))
        } catch (e: SecurityException) {
            PhoneContactResult.Failed(PhoneContactResult.Reason.PERMISSION)
        } catch (e: RemoteException) {
            PhoneContactResult.Failed(PhoneContactResult.Reason.PROVIDER)
        } catch (e: OperationApplicationException) {
            PhoneContactResult.Failed(PhoneContactResult.Reason.PROVIDER)
        }
    }

    internal fun operations(contact: VCardContact): ArrayList<ContentProviderOperation> {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                .build(),
        )

        /** A data row of [mimeType] linked to the raw contact inserted first (back reference 0). */
        fun data(mimeType: String, vararg values: Pair<String, Any?>) {
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, mimeType)
            values.forEach { (key, value) -> builder.withValue(key, value) }
            ops += builder.build()
        }

        fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }

        data(
            StructuredName.CONTENT_ITEM_TYPE,
            StructuredName.GIVEN_NAME to contact.firstName.clean(),
            StructuredName.FAMILY_NAME to contact.lastName.clean(),
            StructuredName.DISPLAY_NAME to VCardWriter.displayName(contact).clean(),
        )
        contact.phone.clean()?.let { data(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it, Phone.TYPE to Phone.TYPE_WORK) }
        contact.mobile.clean()?.let { data(Phone.CONTENT_ITEM_TYPE, Phone.NUMBER to it, Phone.TYPE to Phone.TYPE_MOBILE) }
        contact.email.clean()?.let { data(Email.CONTENT_ITEM_TYPE, Email.ADDRESS to it, Email.TYPE to Email.TYPE_WORK) }
        if (contact.companyName.clean() != null || contact.jobTitle.clean() != null) {
            data(
                Organization.CONTENT_ITEM_TYPE,
                Organization.COMPANY to contact.companyName.clean(),
                Organization.TITLE to contact.jobTitle.clean(),
                Organization.TYPE to Organization.TYPE_WORK,
            )
        }
        contact.website.clean()?.let { data(Website.CONTENT_ITEM_TYPE, Website.URL to it, Website.TYPE to Website.TYPE_WORK) }
        if (contact.address.clean() != null || contact.country.clean() != null) {
            data(
                StructuredPostal.CONTENT_ITEM_TYPE,
                StructuredPostal.STREET to contact.address.clean(),
                StructuredPostal.COUNTRY to contact.country.clean(),
                StructuredPostal.TYPE to StructuredPostal.TYPE_WORK,
            )
        }
        contact.notes.clean()?.let { data(Note.CONTENT_ITEM_TYPE, Note.NOTE to it) }
        return ops
    }
}
