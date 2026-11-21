package com.exambase.backend.auth

import jakarta.mail.Address
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import java.util.Properties

interface EmailSender {
    fun send(to: String, subject: String, body: String)
}

/** DEVELOPMENT ONLY. Writes the email (including one-time codes) to the server log. */
class LogEmailSender : EmailSender {
    private val log = LoggerFactory.getLogger(LogEmailSender::class.java)
    override fun send(to: String, subject: String, body: String) {
        log.info("DEV EMAIL to={} subject={} body={}", to, subject, body)
    }
}

/** SMTP sender for production. Credentials are supplied only through environment secrets. */
class SmtpEmailSender(
    private val host: String,
    private val port: Int,
    private val username: String?,
    private val password: String?,
    private val from: String,
    private val startTls: Boolean = true,
) : EmailSender {
    init {
        require(host.isNotBlank()) { "SMTP_HOST must not be blank." }
        require(port in 1..65535) { "SMTP_PORT must be between 1 and 65535." }
        require(from.isNotBlank()) { "SMTP_FROM must not be blank." }
        require((username == null) == (password == null)) { "SMTP_USERNAME and SMTP_PASSWORD must be configured together." }
    }

    override fun send(to: String, subject: String, body: String) {
        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", (username != null).toString())
            put("mail.smtp.starttls.enable", startTls.toString())
            put("mail.smtp.starttls.required", startTls.toString())
            put("mail.smtp.connectiontimeout", "10000")
            put("mail.smtp.timeout", "15000")
            put("mail.smtp.writetimeout", "15000")
            put("mail.smtp.ssl.checkserveridentity", "true")
        }
        val session = if (username != null) Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
        }) else Session.getInstance(props)
        val message = MimeMessage(session).apply {
            setFrom(InternetAddress(from))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true).map { it as Address }.toTypedArray())
            setSubject(subject, Charsets.UTF_8.name())
            setText(body, Charsets.UTF_8.name())
        }
        try {
            Transport.send(message)
        } catch (e: Exception) {
            throw IllegalStateException("Email delivery failed. Check SMTP host, credentials, and sender verification.", e)
        }
    }
}
