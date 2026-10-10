package tw.hoonsor.ime.app.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import tw.hoonsor.ime.contracts.SecretProvider
import tw.hoonsor.ime.contracts.SecretStore
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API key 保存：用 Android Keystore 的 AES-GCM 金鑰加密後存在 SharedPreferences。
 * 金鑰本身留在 Keystore 裡、無法匯出；備份已關閉（allowBackup=false）。
 * 不提供列出全部 key 的方法；任何錯誤訊息都不含 key 內容。
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val sp = context.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    override suspend fun get(provider: SecretProvider): String? = getBlocking(provider)

    override suspend fun set(provider: SecretProvider, value: String?) = setBlocking(provider, value)

    fun getBlocking(provider: SecretProvider): String? {
        val enc = sp.getString(provider.name, null) ?: return null
        return try {
            val raw = Base64.decode(enc, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "decrypt failed for ${provider.name}: ${e.javaClass.simpleName}")
            null
        }
    }

    fun setBlocking(provider: SecretProvider, value: String?) {
        val v = value?.trim()
        if (v.isNullOrEmpty()) {
            sp.edit().remove(provider.name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(v.toByteArray(Charsets.UTF_8))
        sp.edit().putString(provider.name, Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)).apply()
    }

    fun has(provider: SecretProvider) = sp.contains(provider.name)

    /** 顯示用：只露出最後 4 碼。 */
    fun masked(provider: SecretProvider): String? =
        getBlocking(provider)?.let { if (it.length <= 4) "••••" else "••••" + it.takeLast(4) }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build())
        return gen.generateKey()
    }

    companion object {
        private const val TAG = "SecretStore"
        private const val ALIAS = "zhuyin_ime_secrets"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
