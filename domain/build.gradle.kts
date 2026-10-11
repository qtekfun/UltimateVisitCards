// Kotlin puro: sin imports de android.* (ver CLAUDE.md).
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
