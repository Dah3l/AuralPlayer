// App de escritorio de AuralPlayer (Windows, vía Compose Multiplatform).
// Se empaqueta con jpackage en EXE desde GitHub Actions
// (runner windows-latest, workflow desktop.yml).
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
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
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aural.player.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "AuralPlayer"
            packageVersion = "1.2.0"
            description = "AuralPlayer — El sonido, sin ruido."
            copyright = "© 2026 Dah3l"
            windows {
                menuGroup = "AuralPlayer"
                shortcut = true
            }
        }
    }
}
