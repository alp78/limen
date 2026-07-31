package dev.verso.limen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.verso.limen.R

/**
 * The mark: the user's badge, verbatim. A navy seal ringed in gold, LIMEN PACTORUM ET
 * IURIUM around a cross carrying the key, the signed scroll at its foot. The raster is
 * the identity; the launcher icon carries the same art on its paper disc.
 */
@Composable
fun LimenMark(size: Dp = 30.dp, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.limen_mark),
    contentDescription = null,
    modifier = modifier.size(size),
  )
}
