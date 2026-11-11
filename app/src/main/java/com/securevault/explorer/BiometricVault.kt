package com.securevault.explorer

import com.lumen.explorer.R
import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Optional fingerprint / face unlock. The vault password is encrypted with an Android-Keystore key that
 * can only be used after a successful biometric prompt (and is invalidated if biometrics change).
 */
object BiometricVault {
    private const val ALIAS = "sv_bio_key"
    private const val K_BLOB = "_bio_blob"
    private const val K_IV = "_bio_iv"
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun enrolled(): Boolean = VaultApp.prefs.raw(K_BLOB) != null

    fun disable() {
        VaultApp.prefs.remove(K_BLOB)
        VaultApp.prefs.remove(K_IV)
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun createKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(b.build())
        return kg.generateKey()
    }

    private fun loadKey(): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getKey(ALIAS, null) as? SecretKey
    }

    private fun prompt(activity: FragmentActivity, cipher: Cipher, onOk: (Cipher) -> Unit, onFail: (String) -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.bio_title))
            .setSubtitle(activity.getString(R.string.bio_subtitle))
            .setNegativeButtonText(activity.getString(R.string.bio_cancel))
            .setAllowedAuthenticators(AUTH)
            .build()
        val bp = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher
                if (c == null) onFail("No cipher") else onOk(c)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        })
        Session.externalBusy = true
        bp.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }

    /** Store [password] behind biometrics. Call only after the user re-entered the real password. */
    fun enable(activity: FragmentActivity, password: CharArray, done: (Boolean, String?) -> Unit) {
        try {
            val key = createKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            prompt(activity, cipher, { c ->
                try {
                    val ct = c.doFinal(String(password).toByteArray(Charsets.UTF_8))
                    VaultApp.prefs.put(K_BLOB, Base64.encodeToString(ct, Base64.NO_WRAP))
                    VaultApp.prefs.put(K_IV, Base64.encodeToString(c.iv, Base64.NO_WRAP))
                    done(true, null)
                } catch (e: Exception) {
                    done(false, e.message)
                }
            }, { msg -> done(false, msg) })
        } catch (e: Exception) {
            done(false, e.message)
        }
    }

    /** Returns the stored password after a successful biometric prompt. */
    fun unlock(activity: FragmentActivity, done: (CharArray?, String?) -> Unit) {
        val blob = VaultApp.prefs.raw(K_BLOB)
        val iv = VaultApp.prefs.raw(K_IV)
        if (blob == null || iv == null) {
            done(null, "Biometric unlock is not set up")
            return
        }
        try {
            val key = loadKey() ?: run {
                disable()
                done(null, "Biometric key missing – set it up again")
                return
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            prompt(activity, cipher, { c ->
                try {
                    val pt = c.doFinal(Base64.decode(blob, Base64.NO_WRAP))
                    done(String(pt, Charsets.UTF_8).toCharArray(), null)
                } catch (e: Exception) {
                    done(null, e.message)
                }
            }, { msg -> done(null, msg) })
        } catch (e: KeyPermanentlyInvalidatedException) {
            disable()
            done(null, "Biometrics changed – please unlock with your password and re-enable")
        } catch (e: Exception) {
            done(null, e.message)
        }
    }
}
