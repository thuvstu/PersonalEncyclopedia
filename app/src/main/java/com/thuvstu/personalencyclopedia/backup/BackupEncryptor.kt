package com.thuvstu.personalencyclopedia.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM encryption using Android Keystore (§6.3)
 * Replaces `age` encryption from Knowledge OS v10.
 */
object BackupEncryptor {

    private const val KEYSTORE_ALIAS = "encyclopedia_backup_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val GCM_TAG_LENGTH = 128

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGen = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    /**
     * Encrypt file. Output format: [12-byte IV][ciphertext+tag]
     * ★#K1: ストリーミング化（8KBチャンク）。旧実装の readBytes() 全載せをやめ、
     * GB級DBでもOOMしない。出力形式は同一のため既存 .enc と互換あり。
     */
    fun encrypt(inputFile: File, outputFile: File) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        FileOutputStream(outputFile).use { fos ->
            fos.write(cipher.iv)
            CipherOutputStream(fos, cipher).use { cos ->
                FileInputStream(inputFile).use { fis ->
                    fis.copyTo(cos, 8192)
                }
            }
        }
    }

    /**
     * Decrypt file (streaming, same format).
     */
    fun decrypt(inputFile: File, outputFile: File) {
        val key = getOrCreateKey()
        FileInputStream(inputFile).use { fis ->
            val iv = ByteArray(12)
            var read = 0
            while (read < 12) {
                val n = fis.read(iv, read, 12 - read)
                if (n < 0) throw IllegalArgumentException("Truncated backup file")
                read += n
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(outputFile).use { fos ->
                    cis.copyTo(fos, 8192)
                }
            }
        }
    }
}