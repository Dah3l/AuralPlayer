// Módulo compartido de AuralPlayer (Compose Multiplatform).
// Fase 3: JVM (escritorio) + Android. La app Android depende de
// este módulo; la lógica común vive en commonMain y cada
// plataforma aporta lo suyo (androidMain / jvmMain).
// AGP 9: el target Android usa el plugin com.android.kotlin
// .multiplatform.library con el bloque kotlin.android — el
// plugin com.android.library + androidTarget() es incompatible
// con el DSL nuevo de AGP 9 (ambos builds fallaban al aplicar
// el plugin multiplatform en este archivo).
// Fase 3c: red (Ktor) y JSON (kotlinx-serialization, solo API de
// árbol: sin plugin de serialización) multiplataforma. Cada
// plataforma aporta su motor Ktor. Las fechas van por SystemDates
// (expect/actual con java.time, sin librerías): kotlinx-datetime
// no llegaba al instalador de escritorio (1.2.11–1.2.13).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    android {
        namespace = "com.aural.player.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        named("commonMain") {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.animation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("io.ktor:ktor-client-core:3.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
            }
        }
        named("androidMain") {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:3.6.1")
            }
        }
        named("jvmMain") {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.6.1")
            }
        }
    }
}
