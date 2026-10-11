// Kotlin puro: sin imports de android.* (ver CLAUDE.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    // Solo las anotaciones JSR-330 (@Inject, @Qualifier) que usa Hilt; sin Android.
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
