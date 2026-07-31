package dev.verso.limen.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

private class TestSigner : SealSigner {
  private val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.genKeyPair()
  override val publicKeyBase64: String = Base64.getEncoder().encodeToString(pair.public.encoded)
  override fun sign(message: ByteArray): ByteArray =
    Signature.getInstance(Seal.ALG).run { initSign(pair.private); update(message); sign() }
}

class LedgerTest {

  @get:Rule
  val tmp = TemporaryFolder()

  @Test
  fun chainGrowsAndVerifies() {
    val ledger = Ledger(tmp.root)
    val tenant = TestSigner()
    val landlord = TestSigner()
    ledger.append("d1", EventKind.DossierOpened, "opened", "aa", tenant, "Anna", Role.Tenant, 1)
    ledger.append("d1", EventKind.DefectAdded, "defect · Kitchen · Sink", "bb", landlord, "Marc", Role.Landlord, 2)
    ledger.append("d1", EventKind.SignatureKept, "record · tenant", "cc", tenant, "Anna", Role.Tenant, 3)
    val events = ledger.events("d1")
    assertEquals(3, events.size)
    assertEquals(listOf(1, 2, 3), events.map { it.seq })
    // Each link names its predecessor.
    assertEquals(events[0].hash(), events[1].prevHash)
    assertEquals(events[1].hash(), events[2].prevHash)
    assertNull(ledger.verify("d1"))
  }

  @Test
  fun tamperingBreaksTheChainWhereItHappens() {
    val ledger = Ledger(tmp.root)
    val tenant = TestSigner()
    ledger.append("d1", EventKind.DossierOpened, "opened", "aa", tenant, "Anna", Role.Tenant, 1)
    ledger.append("d1", EventKind.DefectAdded, "defect · Bath", "bb", tenant, "Anna", Role.Tenant, 2)
    ledger.append("d1", EventKind.Exported, "pdf", "cc", tenant, "Anna", Role.Tenant, 3)
    // Rewrite event 2's summary in the file, as a party editing their own storage would.
    val f = tmp.root.resolve("ledger/d1.jsonl")
    f.writeText(f.readText().replace("defect · Bath", "defect · Bath contested"))
    val broken = ledger.verify("d1")
    assertNotNull(broken)
    assertEquals(2, broken)
  }

  @Test
  fun forgedAuthorFailsVerification() {
    val ledger = Ledger(tmp.root)
    val tenant = TestSigner()
    ledger.append("d1", EventKind.DossierOpened, "opened", "aa", tenant, "Anna", Role.Tenant, 1)
    // Claiming someone else's key without their signature is caught.
    val f = tmp.root.resolve("ledger/d1.jsonl")
    val impostor = TestSigner()
    f.writeText(f.readText().replace(tenant.publicKeyBase64, impostor.publicKeyBase64))
    assertEquals(1, ledger.verify("d1"))
  }

  @Test
  fun keyCardRoundTripAndTamper() {
    val signer = TestSigner()
    val card = Pairing.makeCard(signer, "Marc Perret", Role.Landlord, 42)
    val decoded = Pairing.decodeCard(Pairing.encodeCard(card))
    assertNotNull(decoded)
    assertEquals("Marc Perret", decoded!!.name)
    // A renamed card no longer matches its self-signature.
    assertNull(Pairing.decodeCard(Pairing.encodeCard(card).replace("Marc Perret", "Someone Else")))
    assertNull(Pairing.decodeCard("not a card"))
  }

  @Test
  fun jointFingerprintIsOrderBlind() {
    val a = TestSigner().publicKeyBase64
    val b = TestSigner().publicKeyBase64
    assertEquals(Pairing.jointFingerprint(a, b), Pairing.jointFingerprint(b, a))
    assertFalse(Pairing.jointFingerprint(a, b) == Pairing.jointFingerprint(a, TestSigner().publicKeyBase64))
  }

  @Test
  fun pairRecordPersists() {
    val pair = PairRecord("Anna", Role.Tenant, TestSigner().publicKeyBase64, 7)
    Pairing.save(tmp.root, pair)
    assertEquals(pair, Pairing.load(tmp.root))
    Pairing.clear(tmp.root)
    assertNull(Pairing.load(tmp.root))
    assertTrue(true)
  }
}
