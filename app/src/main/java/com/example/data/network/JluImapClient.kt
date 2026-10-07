package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Store

data class ImapLoginResult(
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

class JluImapClient {

    companion object {
        const val HOST = "exchange.uni-giessen.de"
        const val PORT = 993
    }

    suspend fun verifyLogin(username: String, password: String): ImapLoginResult =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || password.isBlank()) {
                return@withContext ImapLoginResult(false, "Kennung und Passwort sind erforderlich.")
            }

            val properties = Properties().apply {
                put("mail.imaps.host", HOST)
                put("mail.imaps.port", PORT.toString())
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.auth.login.disable", "false")
                put("mail.imaps.auth.plain.disable", "false")
                put("mail.imaps.connectiontimeout", "12000")
                put("mail.imaps.timeout", "15000")
                put("mail.imaps.writetimeout", "15000")
            }

            var store: Store? = null
            try {
                val session = Session.getInstance(properties)
                store = session.getStore("imaps")
                store.connect(HOST, PORT, username.trim(), password)
                val inbox = store.getFolder("INBOX")
                if (!inbox.exists()) {
                    return@withContext ImapLoginResult(false, "Login succeeded, but the JLU mailbox could not be opened.")
                }
                ImapLoginResult(true)
            } catch (_: AuthenticationFailedException) {
                ImapLoginResult(false, "Login failed: incorrect Kennung or password.")
            } catch (e: MessagingException) {
                val message = e.message?.lowercase().orEmpty()
                val authFailure = message.contains("authentication") ||
                    message.contains("authenticate") ||
                    message.contains("login failed") ||
                    message.contains("invalid credentials")
                ImapLoginResult(
                    false,
                    if (authFailure) {
                        "Login failed: incorrect Kennung or password."
                    } else {
                        "Login failed: " + (e.message ?: "unable to connect to the JLU mail server.")
                    }
                )
            } catch (_: Exception) {
                ImapLoginResult(false, "Login failed: unable to connect to the JLU mail server.")
            } finally {
                try {
                    store?.close()
                } catch (_: Exception) {
                }
            }
        }
}
