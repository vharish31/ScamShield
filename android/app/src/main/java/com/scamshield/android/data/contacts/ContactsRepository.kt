package com.scamshield.android.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.scamshield.android.data.local.TrustedContactDao
import com.scamshield.android.data.local.TrustedContactEntity
import com.scamshield.android.domain.contacts.PhoneNumberNormalizer
import com.scamshield.android.domain.model.CallerTrustStatus
import com.scamshield.android.domain.model.TrustedContact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class CallerClassificationResult(
    val trustStatus: CallerTrustStatus,
    val canonicalNumber: String?,
    val matchedContactName: String?,
    val contactsPermissionGranted: Boolean
)

/**
 * Reads and caches device contacts purely on-device.
 * Privacy guarantee: saved contacts are NEVER uploaded or sent to remote services.
 */
@Singleton
class ContactsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trustedContactDao: TrustedContactDao
) {

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    val trustedContactsFlow: Flow<List<TrustedContact>> =
        trustedContactDao.observeDistinctContacts().map { entities ->
            entities.map {
                TrustedContact(
                    id = it.contactId,
                    displayName = it.displayName,
                    rawNumber = it.rawNumber,
                    normalizedNumber = it.canonicalNumber
                )
            }
        }

    val trustedCountFlow: Flow<Int> = trustedContactDao.observeDistinctCount()

    suspend fun syncDeviceContacts(): Result<Int> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) {
            trustedContactDao.clearAll()
            return@withContext Result.failure(
                SecurityException("Contacts permission is required to verify trusted saved contacts locally.")
            )
        }

        try {
            val syncedAt = System.currentTimeMillis()
            val byLookupKey = linkedMapOf<String, TrustedContactEntity>()
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getString(idIdx) ?: continue
                    val displayName = cursor.getString(nameIdx)?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: "Saved Contact"
                    val rawNumber = cursor.getString(numIdx) ?: continue
                    val canonical = PhoneNumberNormalizer.normalizeToCanonical(rawNumber) ?: continue
                    val lookupKeys = PhoneNumberNormalizer.extractLookupKeys(rawNumber)

                    lookupKeys.forEach { key ->
                        byLookupKey.putIfAbsent(
                            key,
                            TrustedContactEntity(
                                lookupKey = key,
                                contactId = contactId,
                                displayName = displayName,
                                rawNumber = rawNumber,
                                canonicalNumber = canonical,
                                syncedAtMillis = syncedAt
                            )
                        )
                    }
                }
            }

            trustedContactDao.replaceAll(byLookupKey.values.toList())
            val uniqueContacts = byLookupKey.values.map { it.canonicalNumber }.toSet().size
            Result.success(uniqueContacts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Evaluates whether an incoming caller matches a saved device contact.
     * Falls back to querying Room index or refreshing ContactsContract if needed.
     */
    suspend fun classifyCaller(rawPhoneNumber: String?): CallerClassificationResult =
        withContext(Dispatchers.IO) {
            val permissionGranted = hasContactsPermission()
            val canonical = PhoneNumberNormalizer.normalizeToCanonical(rawPhoneNumber)
            if (canonical == null) {
                return@withContext CallerClassificationResult(
                    trustStatus = CallerTrustStatus.UNAVAILABLE_NUMBER,
                    canonicalNumber = null,
                    matchedContactName = null,
                    contactsPermissionGranted = permissionGranted
                )
            }

            if (!permissionGranted) {
                return@withContext CallerClassificationResult(
                    trustStatus = CallerTrustStatus.UNKNOWN,
                    canonicalNumber = canonical,
                    matchedContactName = null,
                    contactsPermissionGranted = false
                )
            }

            val keys = PhoneNumberNormalizer.extractLookupKeys(rawPhoneNumber)
            if (keys.isEmpty()) {
                return@withContext CallerClassificationResult(
                    trustStatus = CallerTrustStatus.UNKNOWN,
                    canonicalNumber = canonical,
                    matchedContactName = null,
                    contactsPermissionGranted = true
                )
            }

            // If local contact index hasn't been synced yet, sync once cleanly
            if (trustedContactDao.entryCount() == 0) {
                syncDeviceContacts()
            }

            val match = trustedContactDao.findFirstByLookupKeys(keys.toList())
            if (match != null) {
                CallerClassificationResult(
                    trustStatus = CallerTrustStatus.TRUSTED,
                    canonicalNumber = match.canonicalNumber,
                    matchedContactName = match.displayName,
                    contactsPermissionGranted = true
                )
            } else {
                CallerClassificationResult(
                    trustStatus = CallerTrustStatus.UNKNOWN,
                    canonicalNumber = canonical,
                    matchedContactName = null,
                    contactsPermissionGranted = true
                )
            }
        }
}

