package dev.verso.limen.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.util.Base64

/**
 * This phone's sealing key: one EC P-256 pair in the Android keystore, created on first
 * use and never leaving the hardware. Losing the phone loses the key, which is right:
 * the seal attests "this very device, at that moment", nothing more personal than that.
 */
object DeviceKey : SealSigner {

  private const val ALIAS = "limen-seal"

  private fun keyStore(): KeyStore =
    KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  private fun ensureKey(): KeyStore.PrivateKeyEntry {
    val ks = keyStore()
    if (!ks.containsAlias(ALIAS)) {
      KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
        initialize(
          KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        )
        generateKeyPair()
      }
    }
    return ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
  }

  override val publicKeyBase64: String
    get() = Base64.getEncoder().encodeToString(ensureKey().certificate.publicKey.encoded)

  override fun sign(message: ByteArray): ByteArray =
    Signature.getInstance(Seal.ALG).run {
      initSign(ensureKey().privateKey)
      update(message)
      sign()
    }
}
