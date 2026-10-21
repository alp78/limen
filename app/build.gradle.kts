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

/** Where the build stages the repo's NOTICE so it can be packaged as an app asset. */
val noticeAssetDir = layout.buildDirectory.get().asFile.resolve("generated/noticeAsset")

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
    namespace = "dev.peringer.limen"
    compileSdk = 36
    // All five in-app languages and Android picker translations must work offline.
    bundle { language { enableSplit = false } }
    defaultConfig {
        applicationId = "dev.peringer.limen"
        // 29 because the finished protocol is written to MediaStore.Downloads, which arrived
        // with scoped storage; everything else here would run on 26.
        minSdk = 29
        targetSdk = 36
        // Bump the code before every Play upload: Play rejects a repeat, and the closed-test
        // and production tracks share the numbering. Code 3 is spent on the first closed test,
        // which shipped the protocol citing the Vaud RULV in every canton.
        // (This comment used to say 1.1.1 was pinned by the published privacy page stating the
        // version its permission claims were checked against. None of the five published
        // notices names a version, so nothing was holding the name back.)
        versionCode = 10
        versionName = "1.2.5"
        // Without this the androidTest sources compile and never run: AGP falls back to
        // the deprecated platform runner, which cannot execute an @RunWith(AndroidJUnit4)
        // class, and the suite reports green having done nothing.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                // Size over speed for libjxl: 14 % smaller than a Release build, and 11 % slower
                // to repack thirty photographs on a Pixel 10, 9.1 s against 8.2 s.
                arguments += "-DCMAKE_BUILD_TYPE=MinSizeRel"
            }
        }
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

    // The third-party notice ships INSIDE the APK, copied from the repo's NOTICE rather than
    // kept as a second copy under assets: Apache-2.0 section 4(d) asks for it to travel with
    // the distribution, and two hand-maintained copies of a licence notice drift apart.
    // A plain File, not a Provider: the Android SourceSet API rejects Providers outright.
    sourceSets["main"].assets.srcDir(noticeAssetDir)

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    // libjxl and a JNI shim, which repack the photographs in a backup as JPEG XL. The source
    // arrives through tools/fetch-deps.sh, pinned; the same pattern as Recast and Verbatim.
    ndkVersion = "28.2.13676358"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

val copyNotice by tasks.registering(Copy::class) {
    from(rootProject.file("NOTICE"))
    into(noticeAssetDir)
}
tasks.named("preBuild") { dependsOn(copyNotice) }

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
