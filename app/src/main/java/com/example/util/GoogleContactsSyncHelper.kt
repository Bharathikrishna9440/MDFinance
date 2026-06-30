package com.example.util

import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.RawContacts
import android.util.Log
import com.example.data.Customer

object GoogleContactsSyncHelper {
    private const val TAG = "GoogleContactsSync"
    private const val PREF_MAP_NAME = "google_contacts_sync_map"

    fun getGoogleAccounts(context: Context): List<String> {
        return try {
            val am = AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            accounts.map { it.name }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Google accounts via AccountManager", e)
            emptyList()
        }
    }

    private fun getSavedRawContactId(context: Context, customerUuid: String): Long? {
        val prefs = context.getSharedPreferences(PREF_MAP_NAME, Context.MODE_PRIVATE)
        val id = prefs.getLong(customerUuid, -1L)
        return if (id == -1L) null else id
    }

    private fun saveRawContactId(context: Context, customerUuid: String, rawContactId: Long) {
        val prefs = context.getSharedPreferences(PREF_MAP_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(customerUuid, rawContactId).apply()
    }

    fun isContactStillExists(context: Context, rawContactId: Long): Boolean {
        val uri = RawContacts.CONTENT_URI
        val projection = arrayOf(RawContacts._ID)
        val selection = "${RawContacts._ID} = ?"
        val selectionArgs = arrayOf(rawContactId.toString())
        return try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun findRawContactIdByPhone(context: Context, accountEmail: String, phone: String): Long? {
        if (phone.isBlank()) return null
        val cleanPhone = phone.filter { it.isDigit() }
        val uri = Phone.CONTENT_URI
        val projection = arrayOf(Phone.RAW_CONTACT_ID, Phone.NUMBER)
        val selection = "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?"
        val selectionArgs = arrayOf("com.google", accountEmail)

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val number = cursor.getString(cursor.getColumnIndexOrThrow(Phone.NUMBER)) ?: ""
                    val cleanNumber = number.filter { it.isDigit() }
                    if (cleanNumber.endsWith(cleanPhone) || cleanPhone.endsWith(cleanNumber)) {
                        return cursor.getLong(cursor.getColumnIndexOrThrow(Phone.RAW_CONTACT_ID))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding raw contact by phone: $phone", e)
        }
        return null
    }

    fun formatCustomerNameForSync(rawName: String, collectionDay: String): String {
        val capitalized = rawName.trim().split("\\s+".toRegex()).joinToString(" ") { word ->
            word.lowercase(java.util.Locale.US).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString()
            }
        }
        val dayName = collectionDay.trim()
        return "$capitalized ($dayName)"
    }

    private fun getOrCreateContactGroup(context: Context, accountEmail: String, groupTitle: String): Long? {
        if (groupTitle.isBlank()) return null
        val uri = ContactsContract.Groups.CONTENT_URI
        val projection = arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE)
        val selection = "${ContactsContract.Groups.TITLE} = ? AND ${ContactsContract.Groups.ACCOUNT_NAME} = ? AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ?"
        val selectionArgs = arrayOf(groupTitle, accountEmail, "com.google")

        var groupId: Long? = null
        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    groupId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying group: $groupTitle", e)
        }

        if (groupId != null) {
            return groupId
        }

        // Create group if it doesn't exist
        try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Groups.CONTENT_URI)
                .withValue(ContactsContract.Groups.TITLE, groupTitle)
                .withValue(ContactsContract.Groups.ACCOUNT_NAME, accountEmail)
                .withValue(ContactsContract.Groups.ACCOUNT_TYPE, "com.google")
                .withValue(ContactsContract.Groups.GROUP_VISIBLE, 1)
                .build())
            val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            if (results.isNotEmpty() && results[0].uri != null) {
                groupId = android.content.ContentUris.parseId(results[0].uri!!)
                Log.i(TAG, "Successfully created group: $groupTitle with ID: $groupId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating group: $groupTitle", e)
        }

        return groupId
    }

    @Synchronized
    fun syncCustomerToGoogleContacts(context: Context, customer: Customer, accountEmail: String): Boolean {
        if (accountEmail.isBlank() || customer.phone.isBlank()) {
            Log.w(TAG, "Sync skipped: accountEmail or customer phone is blank.")
            return false
        }

        try {
            var rawContactId = getSavedRawContactId(context, customer.uuid)
            
            if (rawContactId != null && !isContactStillExists(context, rawContactId)) {
                Log.i(TAG, "Saved contact ID $rawContactId no longer exists. Will re-search or re-create.")
                rawContactId = null
            }

            if (rawContactId == null) {
                rawContactId = findRawContactIdByPhone(context, accountEmail, customer.phone)
            }

            val formattedName = formatCustomerNameForSync(customer.name, customer.collectionDay)

            if (rawContactId != null) {
                // UPDATE existing contact
                Log.i(TAG, "Updating existing Google Contact with ID $rawContactId for client: $formattedName")
                val ops = ArrayList<ContentProviderOperation>()

                // Name update operation (checks if StructuredName exists, otherwise inserts or updates)
                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(rawContactId.toString(), StructuredName.CONTENT_ITEM_TYPE)
                    )
                    .withValue(StructuredName.DISPLAY_NAME, formattedName)
                    .build())

                // Phone update operation
                ops.add(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(rawContactId.toString(), Phone.CONTENT_ITEM_TYPE)
                    )
                    .withValue(Phone.NUMBER, customer.phone)
                    .build())

                // Remove existing group memberships for this raw contact to avoid duplicate or outdated ones
                ops.add(ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    )
                    .build())

                // Add to the new group
                val groupId = getOrCreateContactGroup(context, accountEmail, customer.collectionDay)
                if (groupId != null) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                        .build())
                }

                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                saveRawContactId(context, customer.uuid, rawContactId)
                return true
            } else {
                // INSERT new contact
                Log.i(TAG, "Inserting new Google Contact for client: $formattedName under account: $accountEmail")
                val ops = ArrayList<ContentProviderOperation>()

                ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, "com.google")
                    .withValue(RawContacts.ACCOUNT_NAME, accountEmail)
                    .build())

                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, formattedName)
                    .build())

                ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, customer.phone)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .build())

                val groupId = getOrCreateContactGroup(context, accountEmail, customer.collectionDay)
                if (groupId != null) {
                    ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                        .build())
                }

                val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                if (results.isNotEmpty() && results[0].uri != null) {
                    val rawId = android.content.ContentUris.parseId(results[0].uri!!)
                    saveRawContactId(context, customer.uuid, rawId)
                    Log.i(TAG, "Successfully created contact with ID: $rawId")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync customer to Google Contacts: ${customer.name}", e)
        }
        return false
    }
}
