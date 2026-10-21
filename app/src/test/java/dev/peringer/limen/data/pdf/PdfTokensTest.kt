package dev.peringer.limen.data.pdf

import dev.peringer.limen.theme.LimenPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PdfTokensTest {

  @Test fun theScaleCarriesTheAppsTokensOntoThePage() {
    assertEquals(24f, PdfTokens.dp(15f), 0.0001f)
    assertEquals(24f, PdfTokens.BODY, 0.0001f)
    assertEquals(19.2f, PdfTokens.PANEL_RADIUS, 0.0001f)
    assertEquals(2.4f, PdfTokens.BORDER, 0.0001f)
    assertEquals(25.6f, PdfTokens.PANEL_PAD, 0.0001f)
    assertEquals(67.2f, PdfTokens.DISPLAY, 0.0001f)
    assertEquals(19.2f, PdfTokens.LABEL_SMALL, 0.0001f)
  }

  @Test fun theGridIsA4AtOneHundredAndFiftyDpi() {
    assertEquals(1240f, PdfTokens.W, 0f)
    assertEquals(1754f, PdfTokens.H, 0f)
    // The annex's photograph width follows from the margins and the card padding.
    assertEquals(dev.peringer.limen.data.AnnexPlate.MAX_WIDTH, (PdfTokens.W - 2 * PdfTokens.MARGIN - 2 * PdfTokens.FRAME_PAD).toDouble(), 0.0)
  }

  @Test fun everyColourIsTheScreensColour() {
    assertEquals(LimenPalette.PLATE_RAISED.toInt(), PdfTokens.panel)
    assertEquals(LimenPalette.INPUT.toInt(), PdfTokens.input)
    assertEquals(LimenPalette.PLATE_EDGE.toInt(), PdfTokens.edge)
    assertEquals(LimenPalette.NAVY.toInt(), PdfTokens.navy)
    assertEquals(LimenPalette.INK.toInt(), PdfTokens.ink)
    assertEquals(LimenPalette.INK_DIM.toInt(), PdfTokens.inkDim)
    assertEquals(LimenPalette.WARNING.toInt(), PdfTokens.warning)
    assertEquals(LimenPalette.WARNING_OUTLINE.toInt(), PdfTokens.warningOutline)
    assertEquals(LimenPalette.POSITIVE.toInt(), PdfTokens.positive)
    assertEquals(0xFF233350.toInt(), PdfTokens.navy)
    assertEquals(0xFFFFFFFF.toInt(), PdfTokens.PAPER)
  }

  @Test fun theRecordIsSetInTheAppsFamilyAndNeverInAMonospace() {
    assertEquals("sans-serif", PdfTokens.FAMILY)
    assertFalse(PdfTokens.FAMILY.contains("mono", ignoreCase = true))
  }
}
