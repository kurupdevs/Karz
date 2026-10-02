package com.kurupdevs.karz.data.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kurupdevs.karz.MainActivity
import com.kurupdevs.karz.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives EMI reminder pushes sent by the sendReminders Cloud Function and
 * keeps the FCM token on the user doc fresh.
 *
 * Declared in AndroidManifest.xml. Tapping a reminder deep-links into the app
 * with the loan id; the screens decide where that lands.
 */
class KarzMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching {
                if (ServiceLocator.isInitialized) {
                    ServiceLocator.profile.saveFcmToken(token)
                }
            }.onFailure { Log.w(TAG, "could not save FCM token", it) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        if (!type.startsWith("emi_")) return
        val title = message.notification?.title ?: "Karz"
        val body = message.notification?.body ?: return
        showReminder(title, body, message.data["loanId"])
    }

    private fun showReminder(title: String, body: String, loanId: String?) {
        createChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            loanId?.let { putExtra(EXTRA_LOAN_ID, it) }
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, loanId?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(loanId?.hashCode() ?: 0, notification)
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "EMI reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Payment due and missed EMI reminders" }
            )
        }
    }

    companion object {
        private const val TAG = "KarzMessaging"
        private const val CHANNEL_ID = "emi_reminders"
        const val EXTRA_LOAN_ID = "extra_loan_id"
    }
}
