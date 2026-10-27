package com.imavinashexdev.sipcalling.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log
import com.imavinashexdev.sipcalling.model.Contact
import com.imavinashexdev.sipcalling.util.PhoneNumberNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ContactRepository {

    private const val TAG = "ContactRepository"
    private var dbHelper: ContactDatabaseHelper? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    fun initialize(context: Context) {
        if (dbHelper != null) return
        dbHelper = ContactDatabaseHelper(context.applicationContext)
        loadContacts()
    }

    fun loadContacts() {
        scope.launch {
            val list = dbHelper?.getAll() ?: emptyList()
            _contacts.value = list
        }
    }

    fun insertContact(name: String, rawNumber: String, email: String = "") {
        scope.launch {
            val normalized = PhoneNumberNormalizer.normalize(rawNumber)
            if (normalized.isNotBlank() && name.isNotBlank()) {
                val contact = Contact(
                    name = name.trim(),
                    phoneNumber = normalized,
                    email = email.trim()
                )
                dbHelper?.insertOrUpdate(contact)
                val updated = dbHelper?.getAll() ?: emptyList()
                _contacts.value = updated
            }
        }
    }

    fun deleteContact(id: Long) {
        scope.launch {
            dbHelper?.delete(id)
            val updated = dbHelper?.getAll() ?: emptyList()
            _contacts.value = updated
        }
    }

    /**
     * Looks up a contact by their phone number or SIP address.
     * Uses PhoneNumberNormalizer so that numbers with or without country code match correctly.
     * Essential for Caller ID resolution!
     */
    fun findContactByNumber(rawNumberOrUri: String): Contact? {
        val normalized = PhoneNumberNormalizer.normalize(rawNumberOrUri)
        if (normalized.isBlank()) return null
        return dbHelper?.findByNumber(normalized)
    }

    /**
     * Imports contacts from Android device phonebook,
     * stripping all international country codes and applying the leading '0' rule to every number.
     * @return Number of imported contacts
     */
    suspend fun importDeviceContacts(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
            )

            cursor?.use { c ->
                val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (c.moveToNext()) {
                    val name = if (nameIndex != -1) c.getString(nameIndex) else null
                    val number = if (numberIndex != -1) c.getString(numberIndex) else null

                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        val normalized = PhoneNumberNormalizer.normalize(number)
                        if (normalized.isNotBlank()) {
                            dbHelper?.insertOrUpdate(
                                Contact(
                                    name = name.trim(),
                                    phoneNumber = normalized
                                )
                            )
                            count++
                        }
                    }
                }
            }

            val updated = dbHelper?.getAll() ?: emptyList()
            _contacts.value = updated
            Log.i(TAG, "Successfully imported and normalized $count device contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing device contacts", e)
        }
        count
    }

    fun search(query: String): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return _contacts.value
        return dbHelper?.search(trimmed) ?: emptyList()
    }
}
