package dev.peringer.limen.data

/** A lossless repacking of a JPEG and its inverse; null when the input does not take it. */
internal interface JpegPacker {
  fun pack(jpeg: ByteArray): ByteArray?
  fun unpack(packed: ByteArray): ByteArray?
}

/**
 * JPEG XL's lossless JPEG recompression, from libjxl built into the app, for the photographs
 * in a backup. A JPEG is general compression's worst case: Deflate took 1.3 % off thirty
 * camera originals and XZ 1.5 %. Repacked, they came out 20.8 % smaller, and each unpacked to
 * the identical file, metadata and a Pixel's appended gain map included, so the evidence
 * hashes recorded against the originals hold after a restore. The photographs on the phone
 * and in the PDF stay JPEG.
 */
internal object JpegXl : JpegPacker {

  /** libjxl, or null where it is not built in, as on the JVM that runs the unit tests. */
  val loaded: JpegPacker? = if (runCatching { System.loadLibrary("limenjxl") }.isSuccess) this else null

  external override fun pack(jpeg: ByteArray): ByteArray?

  external override fun unpack(packed: ByteArray): ByteArray?
}
