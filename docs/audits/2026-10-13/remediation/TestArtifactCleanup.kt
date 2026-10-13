package dev.peringer.limen

import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/** Explicit housekeeping for test-owned public downloads, never the production package. */
class TestArtifactCleanup {
  @Test fun removeOwnedDownloads() {
    if (InstrumentationRegistry.getArguments().getString("cleanup") != "true") return
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    check(context.packageName == "dev.peringer.limen.uitest")
    val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val deleted = context.contentResolver.delete(uri, "owner_package_name = ?", arrayOf(context.packageName))
    println("Removed $deleted test-owned downloads")
  }
}
