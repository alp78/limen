plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.verso.limen"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.verso.limen"
        // 29 because the finished protocol lands in MediaStore.Downloads, which arrived
        // with scoped storage; everything else here would run on 26.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Without this the androidTest sources compile and never run: AGP falls back to
        // the deprecated platform runner, which cannot execute an @RunWith(AndroidJUnit4)
        // class, and the suite reports green having done nothing.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // No icon pack: every mark in this instrument is a few lines of Canvas, as in the rest
  // of the portfolio.
  debugImplementation(libs.androidx.compose.ui.tooling)

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
