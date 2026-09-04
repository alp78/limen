package dev.peringer.limen.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import dev.peringer.limen.data.SwissCanton
import dev.peringer.limen.theme.Limen
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FLAG_CACHE_KIB = 1024

private object CantonFlagCache : LruCache<String, Bitmap>(FLAG_CACHE_KIB) {
  override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
}

/** A bundled Drymos-source flag used only to identify a canton beside readable text. */
@Composable
fun CantonFlag(
  canton: SwissCanton?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
) {
  canton ?: return
  val context = LocalContext.current.applicationContext
  val targetPixels = with(LocalDensity.current) { size.roundToPx().coerceAtLeast(32) }
  val bitmap by produceState<Bitmap?>(null, context, canton, targetPixels) {
    value = withContext(Dispatchers.IO) {
      loadCantonFlag(context, canton.code, targetPixels)
    }
  }
  val shape = RoundedCornerShape(3.dp)

  Box(
    modifier = modifier
      .size(size)
      .clip(shape)
      .background(Limen.colors.input)
      .border(1.dp, Limen.colors.plateEdge, shape),
    contentAlignment = Alignment.Center,
  ) {
    bitmap?.let {
      Image(
        bitmap = it.asImageBitmap(),
        contentDescription = contentDescription,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
      )
    }
  }
}

private fun loadCantonFlag(context: Context, code: String, targetPixels: Int): Bitmap? = runCatching {
  val cacheKey = "$code@$targetPixels"
  CantonFlagCache.get(cacheKey) ?: context.assets
    .open("cantons/${code.lowercase(Locale.ROOT)}.svg")
    .use { input ->
      val svg = SVG.getFromInputStream(input)
      // Several Commons files have intrinsic dimensions but no viewBox. AndroidSVG needs a
      // viewBox to scale them, otherwise Fribourg becomes black and Luzern or Zug looks blank.
      if (svg.documentViewBox == null && svg.documentWidth > 0f && svg.documentHeight > 0f) {
        svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
      }
      svg.documentWidth = targetPixels.toFloat()
      svg.documentHeight = targetPixels.toFloat()
      svg.documentPreserveAspectRatio = PreserveAspectRatio.LETTERBOX
      val bitmap = Bitmap.createBitmap(targetPixels, targetPixels, Bitmap.Config.ARGB_8888)
      svg.renderToCanvas(Canvas(bitmap))
      CantonFlagCache.put(cacheKey, bitmap)
      bitmap
    }
}.getOrNull()
