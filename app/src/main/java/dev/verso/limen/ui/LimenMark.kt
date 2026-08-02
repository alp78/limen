package dev.verso.limen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.verso.limen.R

/** The fixed Limen seal, drawn directly from the supplied SVG master as an Android vector. */
@Composable
fun LimenMark(size: Dp = 30.dp, modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(R.drawable.limen_logo_master),
    contentDescription = null,
    modifier = modifier.size(size),
    contentScale = ContentScale.Fit,
  )
}
