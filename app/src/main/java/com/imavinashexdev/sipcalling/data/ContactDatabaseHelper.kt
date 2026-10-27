package com.imavinashexdev.sipcalling.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.imavinashexdev.sipcalling.model.Contact

class ContactDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONTACTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_PHONE_NUMBER TEXT NOT NULL UNIQUE,
                $COL_EMAIL TEXT,
                $COL_STARRED INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_contacts_phone ON $TABLE_CONTACTS ($COL_PHONE_NUMBER)"
        )
        db.execSQL(
            "CREATE INDEX idx_contacts_name ON $TABLE_CONTACTS ($COL_NAME COLLATE NOCASE)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CONTACTS")
        onCreate(db)
    }

    fun insertOrUpdate(contact: Contact): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_NAME, contact.name)
            put(COL_PHONE_NUMBER, contact.phoneNumber)
            put(COL_EMAIL, contact.email)
            put(COL_STARRED, if (contact.isStarred) 1 else 0)
        }
        return db.insertWithOnConflict(
            TABLE_CONTACTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getAll(): List<Contact> {
        val list = mutableListOf<Contact>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONTACTS,
            null,
            null,
            null,
            null,
            null,
            "$COL_NAME COLLATE NOCASE ASC"
        )

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val nameIdx = c.getColumnIndexOrThrow(COL_NAME)
            val phoneIdx = c.getColumnIndexOrThrow(COL_PHONE_NUMBER)
            val emailIdx = c.getColumnIndexOrThrow(COL_EMAIL)
            val starredIdx = c.getColumnIndexOrThrow(COL_STARRED)

            while (c.moveToNext()) {
                list.add(
                    Contact(
                        id = c.getLong(idIdx),
                        name = c.getString(nameIdx),
                        phoneNumber = c.getString(phoneIdx),
                        email = c.getString(emailIdx) ?: "",
                        isStarred = c.getInt(starredIdx) == 1
                    )
                )
            }
        }
        return list
    }

    fun findByNumber(number: String): Contact? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CONTACTS,
            null,
            "$COL_PHONE_NUMBER = ?",
            arrayOf(number),
            null,
            null,
            null,
            "1"
        )

        cursor.use { c ->
            if (c.moveToFirst()) {
                val idIdx = c.getColumnIndexOrThrow(COL_ID)
                val nameIdx = c.getColumnIndexOrThrow(COL_NAME)
                val phoneIdx = c.getColumnIndexOrThrow(COL_PHONE_NUMBER)
                val emailIdx = c.getColumnIndexOrThrow(COL_EMAIL)
                val starredIdx = c.getColumnIndexOrThrow(COL_STARRED)

                return Contact(
                    id = c.getLong(idIdx),
                    name = c.getString(nameIdx),
                    phoneNumber = c.getString(phoneIdx),
                    email = c.getString(emailIdx) ?: "",
                    isStarred = c.getInt(starredIdx) == 1
                )
            }
        }
        return null
    }

    fun search(query: String): List<Contact> {
        val list = mutableListOf<Contact>()
        val db = readableDatabase
        val wildcard = "%$query%"
        val cursor = db.query(
            TABLE_CONTACTS,
            null,
            "$COL_NAME LIKE ? OR $COL_PHONE_NUMBER LIKE ?",
            arrayOf(wildcard, wildcard),
            null,
            null,
            "$COL_NAME COLLATE NOCASE ASC"
        )

        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_ID)
            val nameIdx = c.getColumnIndexOrThrow(COL_NAME)
            val phoneIdx = c.getColumnIndexOrThrow(COL_PHONE_NUMBER)
            val emailIdx = c.getColumnIndexOrThrow(COL_EMAIL)
            val starredIdx = c.getColumnIndexOrThrow(COL_STARRED)

            while (c.moveToNext()) {
                list.add(
                    Contact(
                        id = c.getLong(idIdx),
                        name = c.getString(nameIdx),
                        phoneNumber = c.getString(phoneIdx),
                        email = c.getString(emailIdx) ?: "",
                        isStarred = c.getInt(starredIdx) == 1
                    )
                )
            }
        }
        return list
    }

    fun delete(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_CONTACTS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    companion object {
        const val DATABASE_NAME = "sip_contacts.db"
        const val DATABASE_VERSION = 1

        const val TABLE_CONTACTS = "contacts"
        const val COL_ID = "id"
        const val COL_NAME = "name"
        const val COL_PHONE_NUMBER = "phone_number"
        const val COL_EMAIL = "email"
        const val COL_STARRED = "is_starred"
    }
}
