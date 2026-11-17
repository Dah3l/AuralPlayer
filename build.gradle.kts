// Archivo de construcción raíz del proyecto Aural
// Stack 1.2.0 (2026-11-15): AGP 9.3.1 con Kotlin integrado (built-in
// Kotlin): el plugin org.jetbrains.kotlin.android YA NO se aplica,
// es incompatible con el DSL nuevo de AGP 9. La versión de KGP se
// fija con el classpath de buildscript y el plugin de Compose lleva
// la MISMA versión. Combo verificado: KGP 2.4.21 + Gradle 9.7.0,
// compatible con Compose Multiplatform 1.12.2 (port a PC).
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    id("com.android.library") version "9.3.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
}
