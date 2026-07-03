package com.example.network

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    // In a real app, this should be securely exchanged. For mesh routing simplicity, we use a shared key.
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private val key = SecretKeySpec("12345678901234567890123456789012".toByteArray(), "AES")
    private val iv = IvParameterSpec("1234567890123456".toByteArray())

    fun encrypt(data: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)
            val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            data // fallback if crypto fails unexpectedly
        }
    }

    fun decrypt(encryptedData: String): String {
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            val decoded = Base64.decode(encryptedData, Base64.NO_WRAP)
            val decrypted = cipher.doFinal(decoded)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            encryptedData // if not encrypted basically
        }
    }

    fun encryptStream(inputStream: java.io.InputStream, outputStream: java.io.OutputStream) {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val cos = javax.crypto.CipherOutputStream(outputStream, cipher)
        inputStream.use { input ->
            cos.use { output ->
                input.copyTo(output)
            }
        }
    }

    fun decryptStream(inputStream: java.io.InputStream, outputStream: java.io.OutputStream) {
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        val cis = javax.crypto.CipherInputStream(inputStream, cipher)
        cis.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }
}
