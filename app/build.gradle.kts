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
    versionName = "2.5.0"

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

  // Release signing: the private keystore lives OUTSIDE this repository and is
  // never committed. Non-secret values (keystore path, alias) come from
  // gradle.properties or the environment; passwords come ONLY from the
  // environment (RELEASE_KEYSTORE_PASSWORD / RELEASE_KEY_PASSWORD, or the
  // legacy STORE_PASSWORD / KEY_PASSWORD) or -P flags.
  //
  // The distribution key was ROTATED in 2026-09 after the historical debug key
  // leaked into public git history; the old certificate must not sign anything
  // again. See docs/SECURITY.md for the current fingerprint and the policy.
  signingConfigs {
    create("release") {
      storeFile = rootProject.file(
        System.getenv("RELEASE_KEYSTORE_PATH")
          ?: (findProperty("RELEASE_KEYSTORE_PATH") as String?)
          ?: (findProperty("KEYSTORE_PATH") as String?)
          ?: "termchin-release.jks",
      )
      keyAlias = System.getenv("RELEASE_KEY_ALIAS")
        ?: (findProperty("RELEASE_KEY_ALIAS") as String?)
        ?: (findProperty("KEY_ALIAS") as String?)
        ?: "termchin-release"
      storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        ?: System.getenv("STORE_PASSWORD")
        ?: (findProperty("STORE_PASSWORD") as String?)
      keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        ?: System.getenv("KEY_PASSWORD")
        ?: (findProperty("KEY_PASSWORD") as String?)
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Official releases are ALWAYS signed with the release keystore. There is
      // deliberately no fallback to the debug keystore and no unsigned
      // artifact: when credentials are missing, the `verifyReleaseSigning` task
      // registered below fails the build loudly.
      signingConfig = signingConfigs.getByName("release")
    }
    // debug: AGP's own debug keystore (~/.android/debug.keystore, auto-created
    // on first build) — a fresh clone still builds with zero setup, and the
    // debug key is never used to sign a released APK.
  }

  // --- Release signing guard -------------------------------------------------
  // A machine without credentials must never produce a release artifact, and a
  // misconfigured CI run must never publish a debug-signed or unsigned APK.
  // Every release packaging task therefore depends on this explicit check.
  val guardKeystoreFile: File? = signingConfigs.getByName("release").storeFile
  val guardAlias: String? = signingConfigs.getByName("release").keyAlias
  val guardStorePassword: String? = signingConfigs.getByName("release").storePassword
  val guardKeyPassword: String? = signingConfigs.getByName("release").keyPassword
  val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Fails when the release signing keystore or its passwords are missing."
    doLast {
      val missing = listOfNotNull(
        "keystore file $guardKeystoreFile (set RELEASE_KEYSTORE_PATH or KEYSTORE_PATH)".takeIf {
          guardKeystoreFile?.exists() != true
        },
        "key alias (set RELEASE_KEY_ALIAS or KEY_ALIAS)".takeIf { guardAlias.isNullOrBlank() },
        "store password (set RELEASE_KEYSTORE_PASSWORD or STORE_PASSWORD)".takeIf {
          guardStorePassword.isNullOrBlank()
        },
        "key password (set RELEASE_KEY_PASSWORD or KEY_PASSWORD)".takeIf {
          guardKeyPassword.isNullOrBlank()
        },
      )
      if (missing.isNotEmpty()) {
        throw GradleException(
          "Release signing is not configured — refusing to produce an unsigned release APK.\n" +
            "Missing: ${missing.joinToString("; ")}\n" +
            "Provide the RELEASE_* environment variables (CI: GitHub Actions secrets) or pass " +
            "-PRELEASE_KEYSTORE_PATH / -PRELEASE_KEYSTORE_PASSWORD / -PRELEASE_KEY_PASSWORD, then retry. " +
            "See docs/SECURITY.md for the signing policy.",
        )
      }
    }
  }
  tasks.matching {
    it.name == "assembleRelease" || it.name == "packageRelease" || it.name == "bundleRelease"
  }.configureEach { dependsOn(verifyReleaseSigning) }
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
