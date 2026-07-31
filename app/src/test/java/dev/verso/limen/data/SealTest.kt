package dev.verso.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** A plain JVM key pair standing in for the Android keystore. */
private class JvmSigner : SealSigner {
  private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.genKeyPair()
  override val publicKeyBase64: String =
    Base64.getEncoder().encodeToString(pair.public.encoded)
  override fun sign(message: ByteArray): ByteArray =
    Signature.getInstance(Seal.ALG).run {
      initSign(pair.private)
      update(message)
      sign()
    }
}

class SealTest {

  @get:Rule
  val tmp = TemporaryFolder()

  private fun protocol() = Protocol(
    id = "p1",
    kind = Kind.Exit,
    createdEpochMs = 1000,
    address = "Rue du Simplon 12",
    defects = listOf(Defect(id = "d1", roomId = "r0", text = "Water stain 3 cm", chargedToTenant = true)),
  )

  @Test
  fun contentHashIsDeterministic() {
    val photos = tmp.newFolder("photos")
    val ink = tmp.newFolder("ink")
    photos.resolve("d1-1.jpg").writeBytes(byteArrayOf(1, 2, 3))
    val a = Seal.contentHash(protocol(), photos, ink)
    val b = Seal.contentHash(protocol(), photos, ink)
    assertEquals(a, b)
    assertEquals(64, a.length)
  }

  @Test
  fun contentHashSeesEveryChange() {
    val photos = tmp.newFolder("photos")
    val ink = tmp.newFolder("ink")
    val base = Seal.contentHash(protocol(), photos, ink)
    // A changed field changes the hash.
    assertNotEquals(base, Seal.contentHash(protocol().copy(remarks = "x"), photos, ink))
    // A changed photo byte changes the hash.
    photos.resolve("d1-1.jpg").writeBytes(byteArrayOf(9))
    assertNotEquals(base, Seal.contentHash(protocol(), photos, ink))
  }

  @Test
  fun contentHashIgnoresTheSealItself() {
    val photos = tmp.newFolder("photos")
    val ink = tmp.newFolder("ink")
    val sealed = protocol().copy(
      seal = SealInfo(contentHash = "aa", pdfHash = "bb", signature = "cc", epochMs = 5),
      exportedEpochMs = 99,
    )
    assertEquals(Seal.contentHash(protocol(), photos, ink), Seal.contentHash(sealed, photos, ink))
  }

  @Test
  fun sealSignsAndVerifies() {
    val signer = JvmSigner()
    val seal = Seal.makeSeal(signer, contentHash = "ab12", pdfHash = "cd34", epochMs = 777)
    assertTrue(Seal.verifySeal(seal))
    // Any tampering with the sealed digests breaks verification.
    assertFalse(Seal.verifySeal(seal.copy(pdfHash = "cd35")))
    assertFalse(Seal.verifySeal(seal.copy(epochMs = 778)))
    assertFalse(Seal.verifySeal(seal.copy(publicKey = JvmSigner().publicKeyBase64)))
  }

  @Test
  fun counterSealRoundTripsThroughItsFile() {
    val signer = JvmSigner()
    val c = Seal.makeCounterSeal(
      signer,
      forContentHash = "ab",
      forPdfHash = "cd",
      name = "M. Perret",
      role = Role.Landlord,
      epochMs = 42,
    )
    val decoded = Seal.decodeCounterSeal(Seal.encodeCounterSeal(c))!!
    assertEquals(c, decoded)
    assertTrue(Seal.verifyCounterSeal(decoded))
    // A renamed signer or a swapped document digest is caught.
    assertFalse(Seal.verifyCounterSeal(decoded.copy(name = "Someone else")))
    assertFalse(Seal.verifyCounterSeal(decoded.copy(forPdfHash = "ee")))
  }

  @Test
  fun decodeRejectsGarbage() {
    assertEquals(null, Seal.decodeCounterSeal("not json at all"))
  }

  @Test
  fun fingerprintsReadable() {
    val hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    assertEquals("0123 4567 89AB CDEF", Seal.fingerprint(hex))
    assertEquals(8, Seal.grouped(hex).split(" ").size)
  }

  @Test
  fun sealedProtocolSurvivesTheStore() {
    val store = Store(tmp.newFolder("root"))
    val signer = JvmSigner()
    val sealed = protocol().copy(
      seal = Seal.makeSeal(signer, "ab", "cd", 1),
      counterSeal = Seal.makeCounterSeal(signer, "ab", "cd", "N. Test", Role.Tenant, 2),
    )
    store.save(sealed)
    val back = store.load("p1")!!
    assertEquals(sealed.seal, back.seal)
    assertEquals(sealed.counterSeal, back.counterSeal)
    assertTrue(Seal.verifySeal(back.seal!!))
    assertTrue(Seal.verifyCounterSeal(back.counterSeal!!))
  }
}
