// Módulo compartido de AuralPlayer (Compose Multiplatform).
// Fase 1: solo JVM (escritorio). El target Android se agrega en la
// fase 2, cuando la app Android pase a depender de este módulo.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}
