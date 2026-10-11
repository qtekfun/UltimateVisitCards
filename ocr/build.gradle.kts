import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Implementaciones de OcrService por flavour: src/play (ML Kit) y src/foss (Tesseract). Ver docs/01-arquitectura.md.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

android {
    namespace = "com.qtekfun.ultimatevisitcards.ocr"
    compileSdk = 37
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }
}

dependencies {
    api(project(":domain"))

    // ImageProxy para el análisis en vivo de la vista previa.
    implementation(libs.androidx.camera.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // OCR por flavour (docs/01-arquitectura.md): ML Kit con modelo empaquetado / Tesseract con traineddata en assets.
    "playImplementation"(libs.mlkit.text.recognition)
    "fossImplementation"(libs.tesseract4android)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.androidx.test.ext.junit)
}
