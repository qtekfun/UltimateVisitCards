plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.room) apply false
}

// ktlint en todos los módulos, con la misma versión del catálogo.
val ktlintVersion = libs.versions.ktlint.get()
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set(ktlintVersion)
    }
}

// Tests con poca memoria: un fork, heap acotado y temporales en build/ (disco), nunca en /tmp.
subprojects {
    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        maxHeapSize = "1g"
        // Robolectric toca internals del JDK (FileDescriptor, etc.) que Java 17+ cierra por defecto.
        jvmArgs(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        )
        val tmp = layout.buildDirectory.dir("tmp/test").get().asFile
        doFirst { tmp.mkdirs() }
        systemProperty("java.io.tmpdir", tmp.absolutePath)
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
