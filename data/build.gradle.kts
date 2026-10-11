import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Room + FTS4, repositorios, ImageStore, backup y exportaciones (fase 1 en adelante). Sin flavours.
plugins {
    alias(libs.plugins.android.library)
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

android {
    namespace = "com.qtekfun.ultimatevisitcards.data"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":domain"))

    testImplementation(libs.junit)
}
