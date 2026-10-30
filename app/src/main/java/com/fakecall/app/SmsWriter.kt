package com.fakecall.app

import android.app.Service
import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.provider.Telephony

/**
 * Adds text messages to the phone's real inbox (the Messages app).
 * Android only allows this while this app is the default SMS app, so the user
 * makes it the SMS app for a moment and then switches back to Messages.
 */
object SmsWriter {

    fun isDefaultSmsApp(context: Context): Boolean =
        Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    /** Shows Android's "Make Simulate Call your default SMS app?" prompt. */
    fun requestDefaultIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 29) {
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                .putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
        }

    /** Remembers the current SMS app (e.g. Samsung Messages) before this app takes over. */
    fun rememberCurrentSmsApp(context: Context) {
        val current = Telephony.Sms.getDefaultSmsPackage(context)
        if (current != null && current != context.packageName) {
            prefs(context).edit().putString(KEY_PREVIOUS_SMS_APP, current).apply()
        }
    }

    /**
     * Opens the previous SMS app, which asks to become the SMS app again when it isn't.
     * Android only lets an app ask for this for itself, so this app can't show that prompt.
     * Falls back to the Default apps settings if the previous app isn't known.
     */
    fun switchBackIntent(context: Context): Intent =
        prefs(context).getString(KEY_PREVIOUS_SMS_APP, null)
            ?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    private fun prefs(context: Context) = context.getSharedPreferences("sms", Context.MODE_PRIVATE)

    private const val KEY_PREVIOUS_SMS_APP = "previous_sms_app"

    /** Adds a message of [type] to Messages, dated [date]. Received messages are left unread. */
    fun add(
        context: Context,
        address: String,
        body: String,
        date: Long,
        type: SmsType = SmsType.INBOX,
        dateSent: Long = date,
    ): Boolean {
        val unread = type == SmsType.INBOX
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.DATE_SENT, dateSent)
            put(Telephony.Sms.READ, if (unread) 0 else 1)
            put(Telephony.Sms.SEEN, if (unread) 0 else 1)
            put(Telephony.Sms.TYPE, type.providerType)
        }
        return runCatching { context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) != null }
            .getOrDefault(false)
    }
}

/** Where the message shows up in Messages. */
enum class SmsType(val label: String, val description: String, val providerType: Int) {
    INBOX("Inbox", "A received text, left unread.", Telephony.Sms.MESSAGE_TYPE_INBOX),
    SENT("Sent", "A text you sent.", Telephony.Sms.MESSAGE_TYPE_SENT),
    DRAFT("Draft", "An unsent draft.", Telephony.Sms.MESSAGE_TYPE_DRAFT),
    OUTBOX(
        "Outbox",
        "A text waiting to send. Messages might try to send it for real, so use a number you don't mind texting.",
        Telephony.Sms.MESSAGE_TYPE_OUTBOX,
    ),
    FAILED("Failed", "A text that failed to send.", Telephony.Sms.MESSAGE_TYPE_FAILED),
}

/** Saves real texts that arrive while this app is briefly the SMS app, so none are lost. */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val first = parts.firstOrNull() ?: return
        SmsWriter.add(
            context,
            address = first.displayOriginatingAddress.orEmpty(),
            body = parts.joinToString("") { it.displayMessageBody.orEmpty() },
            date = System.currentTimeMillis(),
            dateSent = first.timestampMillis,
        )
    }
}

/** Android requires an SMS app to accept picture messages; this app doesn't handle them. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Android requires an SMS app to offer quick replies to declined calls; this app doesn't send them. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
