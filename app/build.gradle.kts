import java.time.Instant

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "ir.courseplanner.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "ir.courseplanner.app"
    minSdk = 24
    targetSdk = 36
    // NOTE: versionCode MUST be bumped on every user-facing APK release.
    // Android refuses to install an "update" with the same versionCode,
    // which is exactly why latest changes looked "missing" on the APK.
    versionCode = 15
    versionName = "2.4.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Embed git SHA + build time so any installed APK is verifiable
    // from Settings screen (no more guessing which build is on device).
    val gitShaProvider = providers.exec {
      commandLine("git", "rev-parse", "--short=7", "HEAD")
      isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim().ifBlank { "local" } }
    val gitSha = try { gitShaProvider.get() } catch (_: Exception) { "local" }
    buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    buildConfigField("String", "BUILD_TIME", "\"${Instant.now()}\"")
  }

  // Release signing: non-secret values (keystore path, alias) live in
  // gradle.properties; passwords come ONLY from the environment
  // (STORE_PASSWORD / KEY_PASSWORD) or -P flags — never from a committed file.
  signingConfigs {
    create("release") {
      storeFile = rootProject.file((findProperty("KEYSTORE_PATH") as String?) ?: "my-upload-key.jks")
      keyAlias = (findProperty("KEY_ALIAS") as String?) ?: "upload"
      storePassword = System.getenv("STORE_PASSWORD") ?: (findProperty("STORE_PASSWORD") as String?)
      keyPassword = System.getenv("KEY_PASSWORD") ?: (findProperty("KEY_PASSWORD") as String?)
    }
    // Released APKs are debug-signed, so the debug key IS the distribution key:
    // every release must carry the same certificate or Android refuses the
    // update over an installed build. Relying on AGP's implicit
    // ~/.android/debug.keystore proved unreliable in CI (the runner silently
    // generated a throwaway key), so CI restores the historical key and passes
    // its path through CI_KEYSTORE_PATH. Local builds leave it unset and keep
    // AGP's zero-setup default debug key.
    val ciDebugKeystore = (System.getenv("CI_KEYSTORE_PATH") ?: "").trim()
    if (ciDebugKeystore.isNotEmpty() && rootProject.file(ciDebugKeystore).exists()) {
      create("ciDebug") {
        storeFile = rootProject.file(ciDebugKeystore)
        storePassword = System.getenv("CI_KEYSTORE_PASSWORD") ?: "android"
        keyAlias = System.getenv("CI_KEY_ALIAS") ?: "androiddebugkey"
        keyPassword = System.getenv("CI_KEY_PASSWORD") ?: "android"
      }
    }
  }

  buildTypes {
    debug {
      // Pin the debug signer to the CI-restored keystore when one was provided.
      val ciDebugSigning = signingConfigs.findByName("ciDebug")
      if (ciDebugSigning != null) {
        signingConfig = ciDebugSigning
      }
    }
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Sign only when the keystore file and both passwords are present;
      // otherwise `assembleRelease` produces an unsigned APK (by design —
      // a machine without credentials must never ship a signed artifact).
      val releaseSigning = signingConfigs.getByName("release")
      if (releaseSigning.storeFile?.exists() == true &&
        releaseSigning.storePassword != null &&
        releaseSigning.keyPassword != null
      ) {
        signingConfig = releaseSigning
      }
    }
    // debug: AGP's default debug keystore (~/.android/debug.keystore,
    // auto-created on first build) — a fresh clone builds with zero setup.
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// NOTE (phase-0): Firebase / Gemini / network deps were removed because the app
// is offline-first and none of the Kotlin sources referenced them.
// gradle/libs.versions.toml only lists dependencies this module really uses —
// add the library there first when you actually need one.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.hilt.android)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.hilt.compiler)
}

// Room schema export: schemas are versioned in git so every future
// version bump MUST ship a Migration (see AppDatabase).
ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}
