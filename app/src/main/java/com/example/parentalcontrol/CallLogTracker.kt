package com.example.parentalcontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class CallLogTracker(private val context: Context) {

    companion object {
        private const val TAG = "CallLogTracker"
    }

    private val supabase = SupabaseManager.getInstance()

    suspend fun trackAndUploadCallLogs() = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG permission not granted")
            return@withContext
        }

        try {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            // Get calls from the last 24 hours
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis

            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            val selection = "${CallLog.Calls.DATE} >= ?"
            val selectionArgs = arrayOf(startTime.toString())

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)
                    val name = cursor.getString(nameIndex) ?: "Unknown"
                    val type = cursor.getInt(typeIndex)
                    val date = cursor.getLong(dateIndex)
                    val duration = cursor.getInt(durationIndex)

                    val callTypeString = when (type) {
                        CallLog.Calls.INCOMING_TYPE -> "INCOMING"
                        CallLog.Calls.OUTGOING_TYPE -> "OUTGOING"
                        CallLog.Calls.MISSED_TYPE -> "MISSED"
                        CallLog.Calls.REJECTED_TYPE -> "REJECTED"
                        else -> "UNKNOWN"
                    }

                    val formattedDate = Instant.fromEpochMilliseconds(date)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .toString()

                    val callRecord = CallLogRecord(
                        device_id = deviceId,
                        call_type = callTypeString,
                        contact_name = name,
                        phone_number = number,
                        duration_seconds = duration,
                        timestamp = formattedDate
                    )

                    // Upload each record
                    supabase.saveCallLog(callRecord)
                }
            }
            Log.i(TAG, "Call logs synced successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error tracking call logs: ${e.message}")
        }
    }
}
