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

        /*
         * First identify the payment app.
         */
        val source = when {
            packageName.contains("paytm") -> "Paytm"
            packageName.contains("phonepe") -> "PhonePe"
            packageName.contains("famapp") -> "FamApp"

            /*
             * Google Pay can use different package identifiers depending
             * on the version/device. These cover the common identifiers.
             */
            packageName.contains("googlequicksearchbox") ||
                    packageName.contains("googlepay") ||
                    packageName.contains("gpay") ||
                    packageName.contains("walletnfcrel") -> "GPay"

            else -> return
        }

        /*
         * Only process notifications that actually look like
         * incoming payments.
         */
        if (!looksLikeIncomingPayment(text)) return

        val amount = extractAmount(text) ?: return

        scope.launch {

            val wasNew = repo.addAutoDetectedPaymentIfNew(
                amount = amount,
                source = source
            )

            if (wasNew) {
                withContext(Dispatchers.Main) {

                    if (tts == null) return@withContext

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

    private fun looksLikeIncomingPayment(text: String): Boolean {

        val s = text.lowercase().trim()

        /*
         * Ignore obvious outgoing payments.
         */
        if (
            Regex(
                """\b(?:you|i)\s+(?:sent|paid)\b""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(s)
        ) {
            return false
        }

        /*
         * Paytm / FamApp:
         *
         * "Amit Khanna sent ₹100"
         * "Rajni Kapoor sent ₹1"
         */
        val personSentPayment = Regex(
            """.+?\bsent\s+(?:₹|rs\.?|inr)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\b""",
            RegexOption.IGNORE_CASE
        )

        if (personSentPayment.containsMatchIn(s)) {
            return true
        }

        /*
         * Google Pay:
         *
         * "Lakshay Khanna paid you ₹1"
         * "Lakshay Khanna paid you 1 rupees"
         */
        val personPaidYou = Regex(
            """.+?\bpaid\s+you\s+(?:₹|rs\.?|inr)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\b""",
            RegexOption.IGNORE_CASE
        )

        if (personPaidYou.containsMatchIn(s)) {
            return true
        }

        /*
         * Generic incoming notification wording.
         */
        val incoming = listOf(
            "received",
            "credited",
            "credit of",
            "credited with",
            "money received",
            "payment received",
            "paid to you",
            "paid you",
            "deposit",
            "deposited",
            "a/c credited",
            "account credited"
        )

        val outgoing = listOf(
            "debited",
            "debit",
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

            /*
             * ₹1
             * ₹100
             * ₹1.50
             */
            """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",

            /*
             * 1 rupees
             * 100 rupees
             * 1 rs
             */
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
