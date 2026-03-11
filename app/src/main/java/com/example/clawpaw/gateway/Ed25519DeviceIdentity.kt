package com.example.clawpaw.gateway

import android.util.Base64
import org.bouncycastle.crypto.Signer
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 与 OpenClaw 官方 node (infra/device-identity.ts) 一致：
 * Ed25519 密钥、deviceId=SHA256(rawPublicKey).hex、签名与公钥用 Base64URL。
 */
object Ed25519DeviceIdentity {

    /** Base64URL：+ → -, / → _, 去掉末尾 = */
    fun base64UrlEncode(bytes: ByteArray): String {
        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return b64.replace('+', '-').replace('/', '_').replace("=", "")
    }

    /** Base64URL 解码 */
    fun base64UrlDecode(input: String): ByteArray {
        var s = input.replace('-', '+').replace('_', '/')
        val pad = when (s.length % 4) {
            2 -> "=="
            3 -> "="
            else -> ""
        }
        return Base64.decode(s + pad, Base64.NO_WRAP)
    }

    /** deviceId = SHA256(rawPublicKey).hex，与官方 fingerprintPublicKey 一致 */
    fun fingerprint(publicKeyRaw: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKeyRaw)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /** 生成 Ed25519 密钥对，返回 (privateKey 32 bytes, publicKey 32 bytes) */
    fun generateKeyPair(): Pair<ByteArray, ByteArray> {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val kp = gen.generateKeyPair()
        val priv = (kp.private as Ed25519PrivateKeyParameters).encoded
        val pub = (kp.public as Ed25519PublicKeyParameters).encoded
        return priv to pub
    }

    /** 对 UTF-8 消息签名，返回 64 字节 Ed25519 签名（与 Node crypto.sign(null, msg, key) 一致） */
    fun sign(privateKeyRaw: ByteArray, message: String): ByteArray {
        val priv = Ed25519PrivateKeyParameters(privateKeyRaw, 0)
        val signer: Signer = Ed25519Signer()
        signer.init(true, priv)
        signer.update(message.toByteArray(Charsets.UTF_8), 0, message.length)
        return signer.generateSignature()
    }
}
