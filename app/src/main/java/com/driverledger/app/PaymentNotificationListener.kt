package com.driverledger.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import com.driverledger.app.data.AppDatabase
import com.driverledger.app.data.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

class PaymentNotificationListener : NotificationListenerService() {
    private var tts: TextToSpeech? = null
    private lateinit var repo: TransactionRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this) { if (it == TextToSpeech.SUCCESS) tts?.language = Locale("en", "IN") }
        repo = TransactionRepository(AppDatabase.getInstance(applicationContext).transactionDao())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.lowercase()
        val source = when {
            packageName.contains("phonepe") -> "PhonePe auto"
            packageName.contains("google.android.apps.nbu.paisa.user") -> "GPay auto"
            packageName.contains("paytm") -> "Paytm auto"
            else -> null
        } ?: return

        val extras = sbn.notification.extras
        val text = listOf(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        ).joinToString(" ")

        if (!looksLikeIncomingPayment(text)) return
        val amount = extractAmount(text) ?: return

        // Persist first, and only announce it out loud if it wasn't a duplicate repost
        // of a notification we already logged.
        scope.launch {
            val wasNew = repo.addAutoDetectedPaymentIfNew(amount, source)
            if (wasNew) {
                withContext(Dispatchers.Main) {
                    tts?.speak("Payment received. ${amount.toSpeechAmount()} rupees.", TextToSpeech.QUEUE_FLUSH, null, "payment")
                }
            }
        }
    }

    private fun looksLikeIncomingPayment(text: String): Boolean {
        val s = text.lowercase()
        val incoming = listOf("received", "credited", "paid to you", "payment received", "money received")
        val outgoing = listOf("sent", "debited", "paid ", "payment to")
        return incoming.any { s.contains(it) } && !outgoing.any { s.contains(it) }
    }

    private fun extractAmount(text: String): Double? {
        val p = Pattern.compile("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(text)
        return if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
    }

    private fun Double.toSpeechAmount(): String =
        if (this % 1.0 == 0.0) this.toInt().toString() else this.toString()

    override fun onDestroy() {
        scope.cancel()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
