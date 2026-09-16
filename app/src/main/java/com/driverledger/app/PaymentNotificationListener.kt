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

        tts = TextToSpeech(this) {
            if (it == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
            }
        }

        repo = TransactionRepository(
            AppDatabase.getInstance(applicationContext).transactionDao()
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName.lowercase()

        val extras = sbn.notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        val text = listOf(
            title,
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        ).joinToString(" ")

        val isTest = text.contains("DRIVER_LEDGER_TEST_PAYMENT")

        if (!isTest && !looksLikeIncomingPayment(text)) return

        val amount = extractAmount(text) ?: return

        val source = when {
            isTest -> "TEST notification"
            packageName.contains("paytm") -> "Paytm"
            isSmsApp(packageName) -> "SMS"
            else -> "Notification"
        }

        scope.launch {
            val wasNew = repo.addAutoDetectedPaymentIfNew(amount, source)

            if (wasNew) {
                withContext(Dispatchers.Main) {
                    tts?.speak(
                        "Payment received. ${amount.toSpeechAmount()} rupees.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "payment"
                    )
                }
            }
        }
    }

    private fun isSmsApp(packageName: String): Boolean {
        return packageName.contains("messaging") ||
                packageName.contains("mms") ||
                packageName.contains("sms")
    }

    private fun looksLikeIncomingPayment(text: String): Boolean {
        val s = text.lowercase()

        val incoming = listOf(
            "received",
            "credited",
            "credit of",
            "credited with",
            "money received",
            "payment received",
            "paid to you",
            "deposit",
            "deposited",
            "a/c credited",
            "account credited"
        )

        val outgoing = listOf(
            "debited",
            "debit",
            "sent",
            "paid by you",
            "you paid",
            "payment to",
            "withdrawn",
            "withdrawal",
            "recharge",
            "bill payment"
        )

        return incoming.any { s.contains(it) } &&
                outgoing.none { s.contains(it) }
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            """(?:rs|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
            """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rupees|rs|inr)"""
        )

        for (pattern in patterns) {
            val matcher = Pattern
                .compile(pattern, Pattern.CASE_INSENSITIVE)
                .matcher(text)

            if (matcher.find()) {
                return matcher.group(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
            }
        }

        return null
    }

    private fun Double.toSpeechAmount(): String =
        if (this % 1.0 == 0.0) {
            this.toInt().toString()
        } else {
            this.toString()
        }

    override fun onDestroy() {
        scope.cancel()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
