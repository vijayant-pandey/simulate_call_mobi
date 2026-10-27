package com.fakecall.app

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CallLog
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

object Notifications {
    const val WAITING_ID = 1
    const val CALL_ID = 2
    private const val MISSED_ID = 3

    private const val CHANNEL_WAITING = "waiting"
    private const val CHANNEL_INCOMING = "incoming"
    private const val CHANNEL_ONGOING = "ongoing"
    private const val CHANNEL_MISSED = "missed"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_WAITING, NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName("Scheduled calls")
                    .build(),
                // The service plays the ringtone and vibration itself.
                NotificationChannelCompat.Builder(CHANNEL_INCOMING, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName("Incoming calls")
                    .setSound(null, null)
                    .setVibrationEnabled(false)
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_ONGOING, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Ongoing calls")
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_MISSED, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Missed calls")
                    .build(),
            ),
        )
    }

    fun waiting(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_WAITING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Call scheduled")
            .setContentIntent(openApp(context))
            .addAction(0, "Cancel", serviceAction(context, CallService.ACTION_END, 10))
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun incoming(context: Context, caller: CallerInfo): Notification {
        val callScreen = callScreen(context, null, 20)
        return NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(caller.displayName)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(callScreen)
            .setFullScreenIntent(callScreen, true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    person(caller),
                    serviceAction(context, CallService.ACTION_END, 21),
                    callScreen(context, CallActivity.ACTION_ANSWER, 22),
                ),
            )
            .build()
    }

    fun ongoing(context: Context, session: CallSession): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle(session.caller.displayName)
            .setContentText("Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setWhen(session.answeredAt)
            .setContentIntent(callScreen(context, null, 30))
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    person(session.caller),
                    serviceAction(context, CallService.ACTION_END, 31),
                ),
            )
            .build()

    fun showMissed(context: Context, caller: CallerInfo) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openCallLog = PendingIntent.getActivity(
            context,
            40,
            Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Missed call")
            .setContentText(caller.displayName)
            .setLargeIcon(PhotoStore.load(caller.photoPath, 256))
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setContentIntent(openCallLog)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(MISSED_ID, notification)
    }

    private fun person(caller: CallerInfo): Person =
        Person.Builder()
            .setName(caller.displayName)
            .setIcon(PhotoStore.load(caller.photoPath, 256)?.let(IconCompat::createWithBitmap))
            .setImportant(true)
            .build()

    private fun callScreen(context: Context, action: String?, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, CallActivity::class.java)
                .setAction(action)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, CallService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
}
