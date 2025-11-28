plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.mtkresearch.breeze"
  compileSdk = 34

  defaultConfig {
    minSdk = 21
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
    freeCompilerArgs = listOf("-Xjvm-default=all")
  }

  buildFeatures {
    viewBinding = false
  }
}

dependencies {
  // API contract
  implementation(project(":breeze-api"))
  
  // EdgeAI SDK for AI features (chat, ASR, TTS)
  implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-v0.1.8")
  
  // Core dependencies
  implementation(project(":core-util"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  
  // Android dependencies
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.lifecycle:lifecycle-process:2.6.2")
  implementation("com.google.android.material:material:1.10.0")
  
  // Jackson for JSON serialization (used by EdgeAI)
  implementation("com.fasterxml.jackson.core:jackson-databind:2.14.0")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
  
  // Signal database access (for HistoryExtractor)
  compileOnly(project(":libsignal-service"))
}
