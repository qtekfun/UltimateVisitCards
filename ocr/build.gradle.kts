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
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
