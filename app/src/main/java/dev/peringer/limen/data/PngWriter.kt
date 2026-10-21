package dev.peringer.limen.data

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * A lossless 8-bit RGB PNG, written a row at a time, for the annex plates.
 *
 * Android's own encoder tries every row filter at full compression, which took most of a
 * minute for thirty plates at their annex size. Every row here takes the Paeth filter, which
 * suits photographs, and the fastest deflate: a few times quicker, a little larger, and the
 * same pixels, which are all the annex asks of it. Rows arrive one by one, so the whole
 * image is never held twice.
 */
internal object PngWriter {

  private val SIGNATURE = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
  private const val CHUNK = 256 * 1024

  /** Writes a [width] × [height] PNG to [out]; [row] fills its array with row y as ARGB. */
  fun write(out: OutputStream, width: Int, height: Int, row: (Int, IntArray) -> Unit) {
    require(width > 0 && height > 0)
    val data = DataOutputStream(out)
    data.write(SIGNATURE)
    chunk(data, "IHDR", ByteArrayOutputStream().also { header ->
      DataOutputStream(header).apply { writeInt(width); writeInt(height); write(byteArrayOf(8, 2, 0, 0, 0)) }
    }.toByteArray())

    val deflater = Deflater(Deflater.BEST_SPEED)
    val pending = ByteArrayOutputStream(CHUNK + 64 * 1024)
    val buffer = ByteArray(64 * 1024)
    fun drain(finishing: Boolean) {
      while (true) {
        val n = deflater.deflate(buffer)
        if (n > 0) pending.write(buffer, 0, n)
        if (pending.size() >= CHUNK) { chunk(data, "IDAT", pending.toByteArray()); pending.reset() }
        if (n == 0 && (finishing && deflater.finished() || !finishing && deflater.needsInput())) break
      }
    }

    val pixels = IntArray(width)
    val stride = width * 3
    var previous = ByteArray(stride)
    var current = ByteArray(stride)
    val filtered = ByteArray(stride + 1).also { it[0] = 4 }
    for (y in 0 until height) {
      row(y, pixels)
      for (x in 0 until width) {
        val argb = pixels[x]
        current[3 * x] = (argb shr 16).toByte()
        current[3 * x + 1] = (argb shr 8).toByte()
        current[3 * x + 2] = argb.toByte()
      }
      for (i in 0 until stride) {
        val a = if (i >= 3) current[i - 3].toInt() and 0xFF else 0
        val b = previous[i].toInt() and 0xFF
        val c = if (i >= 3) previous[i - 3].toInt() and 0xFF else 0
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        val predictor = if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
        filtered[i + 1] = ((current[i].toInt() and 0xFF) - predictor).toByte()
      }
      deflater.setInput(filtered)
      drain(finishing = false)
      val swap = previous; previous = current; current = swap
    }
    deflater.finish()
    drain(finishing = true)
    deflater.end()
    if (pending.size() > 0) chunk(data, "IDAT", pending.toByteArray())
    chunk(data, "IEND", ByteArray(0))
    data.flush()
  }

  private fun chunk(out: DataOutputStream, type: String, body: ByteArray) {
    val name = type.toByteArray(Charsets.US_ASCII)
    out.writeInt(body.size)
    out.write(name)
    out.write(body)
    out.writeInt(CRC32().apply { update(name); update(body) }.value.toInt())
  }
}
