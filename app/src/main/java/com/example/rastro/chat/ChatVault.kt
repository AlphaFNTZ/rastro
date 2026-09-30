package com.example.rastro.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.*
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Fails closed if the Keystore/keyset cannot be opened; never falls back to plaintext. */
class ChatVault(private val context: Context) : Aead {
    private val prefs = context.getSharedPreferences("rastro-chat-keys", Context.MODE_PRIVATE)
    private val alias = "rastro.chat.v1"
    private val key: SecretKey
    init {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(alias)) {
            check(!prefs.contains("enc") && !prefs.contains("sign") && !context.getDatabasePath("rastro-chat.db").exists()) { "Chave local indisponível; identidade não foi recriada" }
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
        key = ks.getKey(alias, null) as SecretKey
    }
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key); c.updateAAD(associatedData)
        return c.iv + c.doFinal(plaintext)
    }
    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
        require(ciphertext.size >= 28)
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, ciphertext.copyOfRange(0, 12))); c.updateAAD(associatedData)
        return c.doFinal(ciphertext.copyOfRange(12, ciphertext.size))
    }
    fun identity(): ChatCrypto {
        ChatCrypto.register()
        val enc = prefs.getString("enc", null); val sign = prefs.getString("sign", null)
        check((enc == null) == (sign == null)) { "Identidade incompleta" }
        if (enc != null && sign != null) return ChatCrypto(read(enc), read(sign))
        val marker = File(context.noBackupFilesDir, "chat-identity-created")
        check(!marker.exists()) { "Identidade local perdida; não foi recriada automaticamente" }
        val fresh = ChatCrypto.generate()
        check(prefs.edit().putString("enc", write(fresh.encryption)).putString("sign", write(fresh.signing)).commit())
        check(marker.createNewFile()) { "Não foi possível registrar a identidade" }
        return fresh
    }
    private fun write(handle: KeysetHandle): String = java.util.Base64.getEncoder().encodeToString(TinkProtoKeysetFormat.serializeEncryptedKeyset(handle, this, "rastro-chat-keys-v1".toByteArray()))
    private fun read(value: String): KeysetHandle = TinkProtoKeysetFormat.parseEncryptedKeyset(java.util.Base64.getDecoder().decode(value), this, "rastro-chat-keys-v1".toByteArray())
}