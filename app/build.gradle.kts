import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * La versión vive en un único sitio: `appVersion` en gradle.properties (SemVer, opcionalmente `-rc.N`).
 * El versionCode se deriva de ella, sin fechas ni datos de la máquina (builds reproducibles):
 * X.Y.Z-rc.N -> (X*10000 + Y*100 + Z) * 100 + N, y 99 para la versión final (va después de sus rc).
 */
val appVersion = providers.gradleProperty("appVersion").get()

fun versionCodeOf(version: String): Int {
    val match = Regex("""(\d+)\.(\d+)\.(\d+)(?:-rc\.(\d+))?""").matchEntire(version)
        ?: error("appVersion debe ser X.Y.Z o X.Y.Z-rc.N: $version")
    val (major, minor, patch, rc) = match.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (rc.isEmpty() || rc.toInt() in 1..98))
    val base = major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()
    return base * 100 + (rc.toIntOrNull() ?: 99)
}

/** Firma de release desde el entorno (secretos de CI). Sin ella el APK de release queda sin firmar. */
val releaseKeystore: String? = System.getenv("UVC_KEYSTORE_FILE")

android {
    namespace = "com.qtekfun.ultimatevisitcards"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.qtekfun.ultimatevisitcards"
        minSdk = 29
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Una sola dimensión; el código específico vive en src/play y src/foss (nada de if sobre BuildConfig.FLAVOR).
    flavorDimensions += "distribution"
    productFlavors {
        create("play") { dimension = "distribution" }
        create("foss") { dimension = "distribution" }
    }

    // F-Droid: sin el blob cifrado de dependencias de Google en el APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("UVC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UVC_KEY_ALIAS")
                keyPassword = System.getenv("UVC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // El commit de git no entra en el APK: un build desde el código fuente debe coincidir (F-Droid).
            vcsInfo.include = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }

    androidResources {
        generateLocaleConfig = true
        // Solo los idiomas de la app: descarta las traducciones que traen las librerías de AndroidX.
        localeFilters += listOf("en", "es")
    }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_21 } }

/** Falla si el manifest fusionado de una variante declara el permiso INTERNET (regla dura de CLAUDE.md). */
abstract class VerifyNoInternetPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @TaskAction
    fun verify() {
        val manifest = mergedManifest.get().asFile.readText()
        val forbidden = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
            .filter { Regex("uses-permission[^>]*\"$it\"").containsMatchIn(manifest) }
        check(forbidden.isEmpty()) { "Permisos de red prohibidos en el manifest fusionado: $forbidden" }
    }
}

val verifyNoInternetPermission = tasks.register("verifyNoInternetPermission") {
    group = "verification"
    description = "Comprueba que ninguna variante declara permisos de red."
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<VerifyNoInternetPermission>("verifyNoInternetPermission$cap") {
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
        }
        verifyNoInternetPermission.configure { dependsOn(task) }
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ocr"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.room.runtime)
    testImplementation(libs.sqlite.bundled.jvm)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    kspTest(libs.hilt.compiler)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
