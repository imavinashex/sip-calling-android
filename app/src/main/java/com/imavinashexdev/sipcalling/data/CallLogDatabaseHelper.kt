package com.imavinashexdev.sipcalling.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.imavinashexdev.sipcalling.model.CallLogEntry
import com.imavinashexdev.sipcalling.model.CallType

class CallLogDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_CALL_LOGS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_PHONE_NUMBER TEXT NOT NULL,
                $COL_DISPLAY_NAME TEXT,
                $COL_TYPE TEXT NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_DURATION INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX idx_call_logs_timestamp ON $TABLE_CALL_LOGS ($COL_TIMESTAMP DESC)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CALL_LOGS")
        onCreate(db)
    }

    fun insert(entry: CallLogEntry): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_PHONE_NUMBER, entry.phoneNumber)
            put(COL_DISPLAY_NAME, entry.displayName)
            put(COL_TYPE, entry.type.name)
            put(COL_TIMESTAMP, entry.timestamp)
            put(COL_DURATION, entry.durationSeconds)
        }
        return db.insert(TABLE_CALL_LOGS, null, values)
    }

    fun getAll(): List<CallLogEntry> {
        val list = mutableListOf<CallLogEntry>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CALL_LOGS,
            null,
            null,
            null,
            null,
            null,
            "$COL_TIMESTAMP DESC"
        )

        cursor.use { c ->
            val idIndex = c.getColumnIndexOrThrow(COL_ID)
            val numberIndex = c.getColumnIndexOrThrow(COL_PHONE_NUMBER)
            val nameIndex = c.getColumnIndexOrThrow(COL_DISPLAY_NAME)
            val typeIndex = c.getColumnIndexOrThrow(COL_TYPE)
            val timeIndex = c.getColumnIndexOrThrow(COL_TIMESTAMP)
            val durationIndex = c.getColumnIndexOrThrow(COL_DURATION)

            while (c.moveToNext()) {
                val typeStr = c.getString(typeIndex)
                val type = try {
                    CallType.valueOf(typeStr)
                } catch (_: Exception) {
                    CallType.INCOMING
                }

                list.add(
                    CallLogEntry(
                        id = c.getLong(idIndex),
                        phoneNumber = c.getString(numberIndex),
                        displayName = c.getString(nameIndex) ?: "",
                        type = type,
                        timestamp = c.getLong(timeIndex),
                        durationSeconds = c.getLong(durationIndex)
                    )
                )
            }
        }
        return list
    }

    fun delete(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_CALL_LOGS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    fun clearAll(): Int {
        val db = writableDatabase
        return db.delete(TABLE_CALL_LOGS, null, null)
    }

    companion object {
        const val DATABASE_NAME = "sip_call_logs.db"
        const val DATABASE_VERSION = 1

        const val TABLE_CALL_LOGS = "call_logs"
        const val COL_ID = "id"
        const val COL_PHONE_NUMBER = "phone_number"
        const val COL_DISPLAY_NAME = "display_name"
        const val COL_TYPE = "type"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_DURATION = "duration_seconds"
    }
}
