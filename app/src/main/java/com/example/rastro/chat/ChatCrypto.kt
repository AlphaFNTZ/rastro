package com.example.rastro.chat

import com.google.crypto.tink.*
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.crypto.tink.signature.SignatureConfig
import com.google.crypto.tink.hybrid.HpkePublicKey
import com.google.crypto.tink.signature.EcdsaPublicKey
import java.util.UUID

/** Tink HPKE + ECDSA. No home-made encryption primitives. */
class ChatCrypto(val encryption: KeysetHandle, val signing: KeysetHandle) {
    fun contact(name: String) = ChatContact(name, publicBytes(encryption), publicBytes(signing))
    fun seal(to: ChatContact, kind: String, plain: ByteArray, now: Long = System.currentTimeMillis()): ChatEnvelope {
        require(plain.size <= ChatEnvelope.MAX_TEXT_BYTES + 256)
        validate(to)
        val empty = ChatEnvelope(UUID.randomUUID().toString(), contact("local").id, to.id, kind, now, byteArrayOf(), byteArrayOf())
        val encrypted = empty.copy(cipher = publicHandle(to.encryption).getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java).encrypt(plain, empty.header()))
        return encrypted.copy(signature = signing.getPrimitive(RegistryConfiguration.get(), PublicKeySign::class.java).sign(encrypted.signedBytes()))
    }
    fun open(envelope: ChatEnvelope, from: ChatContact): ByteArray {
        require(envelope.to == contact("local").id && envelope.from == from.id)
        publicHandle(from.verification).getPrimitive(RegistryConfiguration.get(), PublicKeyVerify::class.java).verify(envelope.signature, envelope.signedBytes())
        return encryption.getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(envelope.cipher, envelope.header())
    }
    companion object {
        fun register() { HybridConfig.register(); SignatureConfig.register() }
        fun generate(): ChatCrypto { register(); return ChatCrypto(KeysetHandle.generateNew(KeyTemplates.get("DHKEM_X25519_HKDF_SHA256_HKDF_SHA256_AES_256_GCM")), KeysetHandle.generateNew(KeyTemplates.get("ECDSA_P256"))) }
        fun publicBytes(handle: KeysetHandle): ByteArray = TinkProtoKeysetFormat.serializeKeysetWithoutSecret(handle.publicKeysetHandle)
        fun publicHandle(bytes: ByteArray): KeysetHandle = TinkProtoKeysetFormat.parseKeysetWithoutSecret(bytes)
        fun validate(c: ChatContact) {
            val encryption = publicHandle(c.encryption)
            val signature = publicHandle(c.verification)
            require(encryption.size() == 1 && encryption.getAt(0).key is HpkePublicKey)
            require(signature.size() == 1 && signature.getAt(0).key is EcdsaPublicKey)
            encryption.getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java)
            signature.getPrimitive(RegistryConfiguration.get(), PublicKeyVerify::class.java)
        }
    }
}