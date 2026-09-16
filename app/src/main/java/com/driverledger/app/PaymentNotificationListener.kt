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

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("en", "IN")
            }
        }

        repo = TransactionRepository(
            AppDatabase.getInstance(applicationContext).transactionDao()
        )
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        val extras = sbn.notification.extras

        /*
         * Notifications can put the useful text in different fields,
         * so collect all of the common ones.
         */
        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()

        val text = extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            .orEmpty()

        val bigText = extras
            .getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            .orEmpty()

        val combinedText = listOf(
            title,
            text,
            bigText
        )
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        if (combinedText.isBlank()) return

        /*
         * Only continue if this looks like an incoming payment.
         */
        if (!looksLikeIncomingPayment(combinedText)) return

        /*
         * Extract the actual payment amount.
         */
        val amount = extractAmount(combinedText) ?: return

        /*
         * Try to identify the payment app.
         *
         * If Android gives us an unfamiliar package name,
         * we still process the payment instead of rejecting it.
         */
        val source = identifySource(sbn.packageName)

        scope.launch {

            /*
             * Existing repository protection prevents the same
             * amount/source from being inserted repeatedly within
             * the duplicate window.
             */
            val wasNew = repo.addAutoDetectedPaymentIfNew(
                amount = amount,
                source = source
            )

            if (!wasNew) return@launch

            withContext(Dispatchers.Main) {

                val speech =
                    "Payment received. ${amount.toSpeechAmount()} rupees."

                tts?.speak(
                    speech,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "payment"
                )
            }
        }
    }

    private fun identifySource(packageName: String): String {

        val p = packageName.lowercase()

        return when {

            p.contains("paytm") ->
                "Paytm"

            p.contains("phonepe") ->
                "PhonePe"

            p.contains("famapp") ->
                "FamApp"

            p.contains("googlequicksearchbox") ||
                    p.contains("googlepay") ||
                    p.contains("gpay") ||
                    p.contains("walletnfcrel") ->
                "GPay"

            else ->
                "Notification"
        }
    }

    private fun looksLikeIncomingPayment(
        text: String
    ): Boolean {

        val s = text
            .lowercase()
            .replace("\n", " ")
            .trim()

        /*
         * ---------------------------------------------------------
         * 1. Reject obvious outgoing payments.
         * ---------------------------------------------------------
         */

        val outgoingPatterns = listOf(

            // "You paid ₹100"
            Regex(
                """\byou\s+paid\b""",
                RegexOption.IGNORE_CASE
            ),

            // "You sent ₹100"
            Regex(
                """\byou\s+sent\b""",
                RegexOption.IGNORE_CASE
            ),

            // "I paid ₹100"
            Regex(
                """\bi\s+paid\b""",
                RegexOption.IGNORE_CASE
            ),

            // "I sent ₹100"
            Regex(
                """\bi\s+sent\b""",
                RegexOption.IGNORE_CASE
            ),

            "payment sent",
            "money sent",
            "payment made",
            "debited",
            "debit alert",
            "withdrawal",
            "withdrawn",
            "recharge successful",
            "bill payment"
        )

        if (outgoingPatterns.any {
                when (it) {
                    is Regex -> it.containsMatchIn(s)
                    is String -> s.contains(it)
                    else -> false
                }
            }) {
            return false
        }

        /*
         * ---------------------------------------------------------
         * 2. Strong incoming-payment patterns.
         * ---------------------------------------------------------
         */

        /*
         * GPay:
         *
         * "Lakshay Khanna paid you ₹1"
         * "Lakshay Khanna paid you 1 rupees"
         * "Rahul paid you Rs 100"
         */
        val paidYou = Regex(
            """\bpaid\s+you\s+(?:(?:₹|rs\.?|inr)\s*)?[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\b""",
            RegexOption.IGNORE_CASE
        )

        if (paidYou.containsMatchIn(s)) {
            return true
        }

        /*
         * Paytm / FamApp:
         *
         * "Rajni Kapoor sent ₹1"
         * "Rajni Kapoor sent 1 rupees"
         */
        val personSent = Regex(
            """.+?\bsent\s+(?:(?:₹|rs\.?|inr)\s*)?[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\b""",
            RegexOption.IGNORE_CASE
        )

        if (personSent.containsMatchIn(s)) {
            return true
        }

        /*
         * "₹1 received"
         * "1 rupees received"
         */
        val amountReceived = Regex(
            """(?:(?:₹|rs\.?|inr)\s*)?[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\s+received\b""",
            RegexOption.IGNORE_CASE
        )

        if (amountReceived.containsMatchIn(s)) {
            return true
        }

        /*
         * ---------------------------------------------------------
         * 3. Generic incoming-payment wording.
         * ---------------------------------------------------------
         */

        val incomingKeywords = listOf(
            "payment received",
            "money received",
            "amount received",
            "received payment",
            "you received",
            "received from",
            "credited",
            "credit of",
            "credited with",
            "account credited",
            "a/c credited",
            "deposit received",
            "deposited"
        )

        if (incomingKeywords.any { s.contains(it) }) {
            return true
        }

        /*
         * "₹1 has been received"
         */
        val hasBeenReceived = Regex(
            """(?:₹|rs\.?|inr)?\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s*(?:rupees|rs|inr)?\s+has\s+been\s+received\b""",
            RegexOption.IGNORE_CASE
        )

        if (hasBeenReceived.containsMatchIn(s)) {
            return true
        }

        return false
    }

    private fun extractAmount(
        text: String
    ): Double? {

        /*
         * The order matters.
         *
         * These cover:
         *
         * ₹1
         * ₹100
         * ₹1.50
         * Rs 100
         * INR 100
         * 100 rupees
         * 100 rs
         * 100 INR
         */
        val patterns = listOf(

            // ₹100 / Rs 100 / INR 100
            """(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",

            // 100 rupees / 100 rs / 100 INR
            """([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:rupees|rs|inr)\b"""
        )

        for (pattern in patterns) {

            val matcher = Pattern
                .compile(
                    pattern,
                    Pattern.CASE_INSENSITIVE
                )
                .matcher(text)

            if (matcher.find()) {

                return matcher
                    .group(1)
                    ?.replace(",", "")
                    ?.toDoubleOrNull()
            }
        }

        return null
    }

    private fun Double.toSpeechAmount(): String {

        return if (this % 1.0 == 0.0) {
            this.toInt().toString()
        } else {
            this.toString()
        }
    }

    override fun onDestroy() {

        scope.cancel()

        tts?.stop()
        tts?.shutdown()
        tts = null

        super.onDestroy()
    }
}
