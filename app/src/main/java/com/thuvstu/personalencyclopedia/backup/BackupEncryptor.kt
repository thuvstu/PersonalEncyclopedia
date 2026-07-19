package com.thuvstu.personalencyclopedia.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
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
     */
    fun encrypt(inputFile: File, outputFile: File) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val plaintext = FileInputStream(inputFile).use { it.readBytes() }
        val ciphertext = cipher.doFinal(plaintext)

        FileOutputStream(outputFile).use { fos ->
            fos.write(iv)
            fos.write(ciphertext)
        }
    }

    /**
     * Decrypt file.
     */
    fun decrypt(inputFile: File, outputFile: File) {
        val key = getOrCreateKey()
        val data = FileInputStream(inputFile).use { it.readBytes() }

        val iv = data.copyOfRange(0, 12)
        val ciphertext = data.copyOfRange(12, data.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val plaintext = cipher.doFinal(ciphertext)
        FileOutputStream(outputFile).use { it.write(plaintext) }
    }
}