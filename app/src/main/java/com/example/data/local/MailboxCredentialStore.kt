package com.example.data.local

import android.content.Context
import android.util.Base64
import com.example.data.model.MailboxAccount
import com.example.data.model.MailboxPermission
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class MailboxCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(mailbox: MailboxAccount, password: String) {
        val encrypted = encrypt(password)
        prefs.edit()
            .putString("id", mailbox.id)
            .putString("displayName", mailbox.displayName)
            .putString("emailAddress", mailbox.emailAddress)
            .putBoolean("isSharedMailbox", mailbox.isSharedMailbox)
            .putString("permission", mailbox.permission.name)
            .putString("department", mailbox.department)
            .putString("username", mailbox.username)
            .putString("endpointUrl", mailbox.endpointUrl)
            .putString("password", encrypted)
            .apply()
    }

    fun load(): Pair<MailboxAccount, String>? {
        val id = prefs.getString("id", null) ?: return null
        val encrypted = prefs.getString("password", null) ?: return null
        val username = prefs.getString("username", null).orEmpty()
        val endpoint = prefs.getString("endpointUrl", null).orEmpty()
        if (username.isBlank() || endpoint.isBlank()) return null

        val permission = runCatching {
            MailboxPermission.valueOf(
                prefs.getString("permission", MailboxPermission.OWNER.name) ?: MailboxPermission.OWNER.name
            )
        }.getOrDefault(MailboxPermission.OWNER)

        val mailbox = MailboxAccount(
            id = id,
            displayName = prefs.getString("displayName", username).orEmpty(),
            emailAddress = prefs.getString("emailAddress", "").orEmpty(),
            isSharedMailbox = prefs.getBoolean("isSharedMailbox", false),
            permission = permission,
            department = prefs.getString("department", "").orEmpty(),
            username = username,
            endpointUrl = endpoint
        )
        val password = runCatching { decrypt(encrypted) }.getOrNull() ?: return null
        if (password.isBlank()) return null
        return mailbox to password
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return String(
            cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
            StandardCharsets.UTF_8
        )
    }

    companion object {
        private const val PREFS = "jlu_mail_secure_account"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "jlu_mail_exchange_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
