package app.luma.chat

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialStore {
    fun read(): String?
    fun save(value: String)
    fun clear()
    fun hasKey(): Boolean
}

/** Only ciphertext and a random IV go into preferences; AES key stays in Android Keystore. */
class AndroidCredentials(context: Context, private val keyProvider: (() -> SecretKey)? = null) : CredentialStore {
    private val prefs = context.getSharedPreferences("luma_credentials", Context.MODE_PRIVATE)
    private val alias = "luma_openrouter_aes_v1"
    private fun key(): SecretKey {
        keyProvider?.let { return it() }
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized override fun read(): String? {
        val encrypted = prefs.getString("ciphertext", null) ?: return null
        val iv = prefs.getString("iv", null) ?: error("Missing IV")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        return String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    }
    @Synchronized override fun save(value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        check(prefs.edit().putString("ciphertext", Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
    }
    @Synchronized override fun clear() { check(prefs.edit().clear().commit()) }
    override fun hasKey(): Boolean = prefs.contains("ciphertext")
}

/**
 * The key shipped inside the app.
 *
 * The XOR pad only keeps the literal out of a plain `strings` dump of the APK.
 * It is NOT protection: anyone who installs the app can recover this key with a
 * decompiler in minutes, and every request they make is billed to it. Rotate it
 * at openrouter.ai the moment the APK leaves a trusted circle.
 */
internal object EmbeddedKey {
    private const val PAD = "iskra-luma-2026-shell"
    private const val SEALED = "GhhGHRMAGkRAVUlRBgdUSBcMVA8JCEVbRgVLDkxVUEkGCVQOSENYUV5fWRIJFgIaCE0MV0sEVAIHHEZaU1UNUBBdQFQdCERaWQ=="
    val value: String by lazy {
        val pad = PAD.toByteArray(Charsets.UTF_8)
        val raw = java.util.Base64.getDecoder().decode(SEALED)
        String(ByteArray(raw.size) { (raw[it].toInt() xor pad[it % pad.size].toInt()).toByte() }, Charsets.UTF_8)
    }
}

/** Serves the sealed key; there is no per-user key to store or clear any more. */
class EmbeddedCredentials : CredentialStore {
    override fun read(): String = EmbeddedKey.value
    override fun save(value: String) = Unit
    override fun clear() = Unit
    override fun hasKey() = true
}

open class LumaApplication : Application() {
    open val credentials: CredentialStore by lazy { EmbeddedCredentials() }
    open val accounts: AccountStore by lazy { LocalAccountStore(this) }
    open val gateway: ChatGateway by lazy { OpenRouterGateway(credentials) }
    open val syncEnabled: Boolean = true
    internal open val syncAddress: SyncAddress by lazy { SyncAddress(this) }
    internal val history: AccountHistory by lazy {
        AccountHistory(this, if (syncEnabled) PcHistoryTransport(this, syncAddress::urls) else null)
    }
}
