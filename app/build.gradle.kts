import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

/**
 * The stamp the debug APK wears, so a phone can say which build it runs. It is the newest
 * moment in the source tree, NOT the clock: read from the clock through a plain call it
 * would be frozen by the configuration cache and could even go backwards, which is worse
 * than no stamp because it lies. A ValueSource is re-read on every build, and keying it to
 * the sources means the stamp moves exactly when the code does.
 */
abstract class SourceStamp : ValueSource<String, SourceStamp.Params> {
  interface Params : ValueSourceParameters {
    val dir: DirectoryProperty
  }

  override fun obtain(): String {
    val newest = parameters.dir.get().asFile.walkTopDown()
      .filter { it.isFile }
      .maxOfOrNull { it.lastModified() } ?: 0L
    return SimpleDateFormat("yyyyMMdd-HHmm").format(Date(newest))
  }
}

val buildStamp: String = providers.of(SourceStamp::class.java) {
  parameters.dir.set(layout.projectDirectory.dir("src"))
}.get()

val limenSigningFile = providers.gradleProperty("limenSigningProperties")
  .map(::file)
  .orElse(file(System.getProperty("user.home")).resolve(".gradle/limen-signing.properties"))
  .get()
val limenSigning = Properties().apply {
  if (limenSigningFile.isFile) limenSigningFile.inputStream().use(::load)
}
val hasLimenSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
  .all { !limenSigning.getProperty(it).isNullOrBlank() }

android {
    namespace = "dev.verso.limen"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.verso.limen"
        // 29 because the finished protocol is written to MediaStore.Downloads, which arrived
        // with scoped storage; everything else here would run on 26.
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        // Without this the androidTest sources compile and never run: AGP falls back to
        // the deprecated platform runner, which cannot execute an @RunWith(AndroidJUnit4)
        // class, and the suite reports green having done nothing.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasLimenSigning) {
            create("limenRelease") {
                storeFile = file(limenSigning.getProperty("storeFile"))
                storePassword = limenSigning.getProperty("storePassword")
                keyAlias = limenSigning.getProperty("keyAlias")
                keyPassword = limenSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Instrumented navigation checks use a separate package, so they cannot read,
            // replace or delete the handover records held by the release app on a phone.
            applicationIdSuffix = ".uitest"
            // Every debug APK carries the minute it was built, so the phone can say which
            // one it is running: Settings > Apps > Limen shows the version. Without this
            // every build reads "1.0" and a stale copy on a device is indistinguishable
            // from a fresh one, which is exactly the confusion it costs an evening to find.
            versionNameSuffix = " " + buildStamp
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasLimenSigning) signingConfig = signingConfigs.getByName("limenRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

/**
 * The house style bans the em-dash in source, comments and user-visible strings alike.
 * Enforced rather than remembered, and wired into `check` so it runs with the tests.
 */
val noEmDash by tasks.registering {
    val sources = fileTree("src") { include("**/*.kt", "**/*.xml") }
    // Captured as values, not read through the Project at execution time: the
    // configuration cache cannot serialize a script object reference.
    val root = projectDir
    inputs.files(sources)
    doLast {
        val offenders =
            sources.filter { it.readText().contains('—') }.map { it.relativeTo(root) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "em-dash (U+2014) is banned in this repository, found in:\n" +
                    offenders.joinToString("\n") { "  $it" }
            )
        }
    }
}

tasks.named("check") { dependsOn(noEmDash) }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  // Only to read the rotation flag of photos coming back from the system camera, so a
  // defect picture taken sideways prints upright on the protocol.
  implementation(libs.androidx.exifinterface)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.libphonenumber)
  implementation("com.caverock:androidsvg-aar:1.4")

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Official Material icons keep universal actions legible and familiar.
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  // Not imported anywhere, and not removable for that reason: compose-ui-test-junit4 drags in
  // espresso 3.5.0, which reflects on InputManager.getInstance, a hidden API newer Android no
  // longer has. Every instrumented test then dies in Espresso.onIdle. This line is a version
  // floor, not a dependency.
  androidTestImplementation(libs.androidx.test.espresso.core)
}
