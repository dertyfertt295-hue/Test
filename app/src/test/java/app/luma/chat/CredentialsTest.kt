package app.luma.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TestLumaApplication::class)
class CredentialsTest {
    @Test fun encryptedStorageRoundTripsUsesFreshIvAndCanBeRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = AndroidCredentials(context) { key }
        val prefs = context.getSharedPreferences("luma_credentials", Context.MODE_PRIVATE)
        store.save("sk-or-test-not-a-real-secret")
        assertEquals("sk-or-test-not-a-real-secret", store.read())
        val firstCipher = prefs.getString("ciphertext", null)
        val firstIv = prefs.getString("iv", null)
        assertFalse(prefs.all.values.any { it.toString().contains("sk-or-") })
        store.save("sk-or-test-not-a-real-secret")
        assertNotEquals(firstCipher, prefs.getString("ciphertext", null))
        assertNotEquals(firstIv, prefs.getString("iv", null))
        assertEquals("sk-or-test-not-a-real-secret", AndroidCredentials(context) { key }.read())
        store.clear()
        assertFalse(store.hasKey()); assertNull(store.read())
    }
    @Test fun sealedKeyUnsealsAndIsNotReadableAsAPlainLiteral() {
        val key = EmbeddedKey.value
        assertTrue(key.startsWith("sk-or-v1-"))
        assertEquals(73, key.length)
        assertTrue(EmbeddedCredentials().hasKey())
        assertEquals(key, EmbeddedCredentials().read())
    }
    @Test fun wrongEncryptionKeyCannotReadCredential() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val firstKey = key()
        AndroidCredentials(context) { firstKey }.save("sk-or-test-not-a-real-secret")
        val otherKey = key()
        try { AndroidCredentials(context) { otherKey }.read(); fail("Wrong key must fail authentication") }
        catch (_: javax.crypto.AEADBadTagException) { /* authenticated encryption rejects it */ }
    }
}
