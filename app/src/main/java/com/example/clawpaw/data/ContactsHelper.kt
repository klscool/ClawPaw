package com.example.clawpaw.data

import android.content.Context
import android.provider.ContactsContract
import org.json.JSONArray
import org.json.JSONObject

/**
 * 联系人：读取联系人列表（需 READ_CONTACTS 权限）。
 */
object ContactsHelper {

    fun getContacts(context: Context, limit: Int = 500): JSONArray {
        val arr = JSONArray()
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME
        )
        context.contentResolver.query(uri, projection, null, null, ContactsContract.Contacts.DISPLAY_NAME + " ASC")?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val id = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: ""
                val phones = getPhonesForContact(context, id)
                arr.put(JSONObject().apply {
                    put("id", id)
                    put("displayName", name)
                    put("phones", phones)
                })
                count++
            }
        }
        return arr
    }

    private fun getPhonesForContact(context: Context, contactId: String): JSONArray {
        val arr = JSONArray()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(uri, projection, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?", arrayOf(contactId), null)?.use { cursor ->
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                arr.put(cursor.getString(numIdx) ?: "")
            }
        }
        return arr
    }
}
