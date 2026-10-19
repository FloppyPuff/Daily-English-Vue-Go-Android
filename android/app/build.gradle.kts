import org.gradle.api.tasks.Sync

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt)
  alias(libs.plugins.ksp)
}

// The API origin is deployment-specific, so it never lives in the repository.
// Pass it with -PapiBaseUrl=https://your-host/ or the API_BASE_URL environment
// variable; ~/.gradle/gradle.properties works too for a daily-driver machine.
// The placeholder below resolves to nothing so a misconfigured build fails
// loudly at runtime instead of silently talking to someone else's server.
val apiBaseUrl: String = (project.findProperty("apiBaseUrl") as String?)
  ?: providers.environmentVariable("API_BASE_URL").orNull
  ?: "https://daily-english.invalid/"

// Release signing is optional and credential-free by default. Supply all four
// values (properties or environment) to get a signed APK; otherwise the release
// variant stays unsigned and must be signed before it can be installed.
val signStoreFile: String? = (project.findProperty("keystoreFile") as String?)
  ?: providers.environmentVariable("KEYSTORE_FILE").orNull
val signStorePassword: String? = (project.findProperty("keystorePassword") as String?)
  ?: providers.environmentVariable("KEYSTORE_PASSWORD").orNull
val signKeyAlias: String? = (project.findProperty("keyAlias") as String?)
  ?: providers.environmentVariable("KEY_ALIAS").orNull
val signKeyPassword: String? = (project.findProperty("keyPassword") as String?)
  ?: providers.environmentVariable("KEY_PASSWORD").orNull
val canSignRelease =
  listOf(signStoreFile, signStorePassword, signKeyAlias, signKeyPassword).none { it.isNullOrBlank() }

android {
  namespace = "com.dailyenglish.nativeapp"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.dailyenglish.quietglass"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.2.0-quietglass"
    buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
  }

  signingConfigs {
    if (canSignRelease) {
      create("release") {
        storeFile = file(signStoreFile!!)
        storePassword = signStorePassword
        keyAlias = signKeyAlias
        keyPassword = signKeyPassword
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (canSignRelease) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }
  testOptions {
    unitTests.isIncludeAndroidResources = true
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

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

// The word/sentence/paper banks live once, in ../content at the repository root.
// Gradle copies them into assets before every build so the APK never carries a
// second, hand-maintained copy.
val contentSource = rootProject.projectDir.resolve("../content")
val syncContent = tasks.register<Sync>("syncContent") {
  // Bind to a local so the doFirst closure does not capture the script object;
  // the configuration cache cannot serialize script references.
  val source: File = contentSource
  from(source) {
    include("words.json", "sentences.json", "papers/**")
  }
  into(layout.projectDirectory.dir("src/main/assets/content"))
  doFirst {
    check(source.isDirectory) {
      "Missing content directory: ${source.canonicalPath}. " +
        "Generate the corpus first (see content/README.md) before building the Android app."
    }
  }
}
tasks.named("preBuild") { dependsOn(syncContent) }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  ksp(libs.androidx.hilt.compiler)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  androidTestImplementation(libs.androidx.room.testing)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.kotlinx.serialization.json)

  implementation(libs.retrofit)
  implementation(libs.retrofit.kotlinx.serialization)
  implementation(libs.okhttp)
  implementation(libs.okhttp.logging)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.okhttp.mockwebserver)

  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
