package com.fakecall.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat

/** Adds an entry to the phone's real call history (Phone app → Recents). */
object CallLogWriter {

    fun write(context: Context, caller: CallerInfo, date: Long, durationSeconds: Long, type: Int): Boolean {
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED
        if (!allowed) return false

        val missed = type == CallLog.Calls.MISSED_TYPE
        val values = ContentValues().apply {
            put(CallLog.Calls.NUMBER, caller.number)
            put(CallLog.Calls.DATE, date)
            put(CallLog.Calls.DURATION, durationSeconds)
            put(CallLog.Calls.TYPE, type)
            put(CallLog.Calls.NEW, if (missed) 1 else 0)
            put(CallLog.Calls.IS_READ, if (missed) 0 else 1)
            if (caller.name.isNotBlank()) put(CallLog.Calls.CACHED_NAME, caller.name)
        }
        return runCatching { context.contentResolver.insert(CallLog.Calls.CONTENT_URI, values) != null }
            .getOrDefault(false)
    }
}
