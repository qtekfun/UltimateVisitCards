// Kotlin puro: sin imports de android.* (ver CONTRIBUTING.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    // Solo las anotaciones JSR-330 (@Inject, @Qualifier) que usa Hilt; sin Android.
    api(libs.javax.inject)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
