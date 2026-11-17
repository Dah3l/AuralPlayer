// Módulo compartido de AuralPlayer (Compose Multiplatform).
// Fase 3: JVM (escritorio) + Android. La app Android depende de
// este módulo; la lógica común vive en commonMain y cada
// plataforma aporta lo suyo (androidMain / jvmMain).
// AGP 9: el target Android usa el plugin com.android.kotlin
// .multiplatform.library con el bloque kotlin.android — el
// plugin com.android.library + androidTarget() es incompatible
// con el DSL nuevo de AGP 9 (ambos builds fallaban al aplicar
// el plugin multiplatform en este archivo).
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
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}
