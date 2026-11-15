// Archivo de construcción raíz del proyecto Aural
// Stack 1.2.0 (2026-11-15): Kotlin 2.4.21 + AGP 9.3.1 + Gradle 9.7.0 —
// combo verificado, compatible con Compose Multiplatform 1.12.2
// (preparación del port a PC). El plugin de Compose lleva la MISMA
// versión que el plugin de Kotlin.
plugins {
    id("com.android.application") version "9.3.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
