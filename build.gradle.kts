plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
}

// ktlint en todos los módulos, con la misma versión del catálogo.
val ktlintVersion = libs.versions.ktlint.get()
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
    }
}

// CI local en un único comando: ./gradlew ci
tasks.register("ci") {
    group = "verification"
    description = "Compila ambos flavours, pasa tests, lint, ktlint y comprueba que no hay permiso INTERNET."
    dependsOn(
        ":app:assemblePlayDebug",
        ":app:assembleFossDebug",
        ":app:testPlayDebugUnitTest",
        ":app:testFossDebugUnitTest",
        ":ocr:testPlayDebugUnitTest",
        ":ocr:testFossDebugUnitTest",
        ":data:testDebugUnitTest",
        ":domain:test",
        ":app:lintPlayDebug",
        ":app:lintFossDebug",
        ":app:verifyNoInternetPermission",
    )
    subprojects.forEach { dependsOn("${it.path}:ktlintCheck") }
}
