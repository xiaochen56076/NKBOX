package com.utilitybox.core.secret

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android 实现：优先用 **Android Keystore** 加密后存入 SharedPreferences。
 *
 * ★ 为什么不用 `androidx.security:security-crypto`（EncryptedSharedPreferences）：
 *   那需要新增一个依赖，而我们要做的事很小 —— 一把 AES/GCM 密钥 + 一次加解密。
 *   平台自带的 Keystore 从 API 23 起就支持（本项目 minSdk 24），零新依赖即可完成。
 *   这也符合本项目「依赖必须有明确且充分的理由」的规则。
 *
 * ★ 降级策略（重要）：若 Keystore 在该设备上不可用（极少数 ROM 会异常），
 *   **不阻断用户** —— 改为明文保存，但把 [isEncrypted] 置为 false，
 *   设置页会**如实显示**"未加密存储"，而不是假装安全。
 *   静默降级是安全类功能里最不该做的事。
 */
class KeystoreSecretStore(context: Context) : SecretStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var encryptedAvailable: Boolean? = null

    override val isEncrypted: Boolean
        get() = ensureKeyReady()

    override fun getApiKey(): String {
        val stored = prefs.getString(KEY_API, null) ?: return ""
        // 先试解密；失败说明不是密文（可能是降级写入的明文）或密钥已失效
        return decrypt(stored) ?: stored
    }

    override fun setApiKey(value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(KEY_API).apply()
            return
        }
        val payload = encrypt(value) ?: value
        prefs.edit().putString(KEY_API, payload).apply()
    }

    // ------------------------------------------------------------------
    // 通用槽位（P11 补 39）—— WebDAV 凭证等
    //
    // ★ 用**同一个** Keystore 密钥与同一套 AES/GCM，安全等级与 API Key 完全一致。
    //   加前缀是为了避免和 KEY_API 撞名（比如有人把 key 取成 "api_key"）。
    // ------------------------------------------------------------------

    override fun getSecret(key: String): String {
        val stored = prefs.getString(PREFIX_EXTRA + key, null) ?: return ""
        return decrypt(stored) ?: stored
    }

    override fun setSecret(key: String, value: String) {
        val slot = PREFIX_EXTRA + key
        if (value.isBlank()) {
            prefs.edit().remove(slot).apply()
            return
        }
        val payload = encrypt(value) ?: value
        prefs.edit().putString(slot, payload).apply()
    }

    // ------------------------------------------------------------------
    // 加解密
    // ------------------------------------------------------------------

    private fun ensureKeyReady(): Boolean {
        encryptedAvailable?.let { return it }
        val ok = try {
            obtainKey()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Keystore 不可用，将降级为明文存储", e)
            false
        }
        encryptedAvailable = ok
        return ok
    }

    private fun obtainKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 刻意**不**要求用户认证：Key 需要在后台请求时被读取，
                // 要求生物识别会让自动刷新无法工作。
                .build()
        )
        return gen.generateKey()
    }

    /** 返回 `Base64(iv):Base64(cipher)`；失败返回 null（由调用方决定降级） */
    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtainKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ct, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "加密失败", e)
        null
    }

    /** 解密；失败返回 null（调用方会按明文处理） */
    private fun decrypt(payload: String): String? = try {
        val parts = payload.split(":")
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, obtainKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "解密失败", e)
        null
    }

    private companion object {
        const val PREFS_NAME = "utilitybox_secrets"
        const val KEY_API = "api_key"
        const val PREFIX_EXTRA = "extra_"
        const val KEY_ALIAS = "utilitybox_api_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val TAG = "UbSecret"
    }
}
