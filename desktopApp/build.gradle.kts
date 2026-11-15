// App de escritorio de AuralPlayer (Windows, vía Compose Multiplatform).
// Se empaqueta con jpackage en EXE desde GitHub Actions
// (runner windows-latest, workflow desktop.yml).
//
// Las librerías de VLC viajan DENTRO del instalador: la tarea
// prepareVlc baja el zip oficial de VLC y lo deja en los recursos
// de la app (appResourcesRootDir); el usuario no necesita VLC
// instalado. El zip se baja una sola vez por runner (tarea con
// salidas declaradas) y pesa ~79 MB.
import org.gradle.api.GradleException
import org.gradle.api.file.RelativePath
import org.gradle.api.tasks.Copy
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val vlcVersion = "3.0.24"
val vlcZipFile = layout.buildDirectory.file("vlc/vlc-$vlcVersion-win64.zip")

tasks.register("downloadVlc") {
    outputs.file(vlcZipFile)
    doLast {
        val zip = vlcZipFile.get().asFile
        fun isValidZip(file: File): Boolean {
            if (!file.exists() || file.length() < 50_000_000L) return false
            val magic = file.inputStream().use { it.readNBytes(4) }
            return magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        }
        if (!isValidZip(zip)) {
            zip.parentFile.mkdirs()
            if (zip.exists()) zip.delete()
            // download.videolan.org sirve el archivo directo (200 +
            // application/zip). get.videolan.org redirige a espejos y
            // a veces devuelve una página HTML en vez del zip.
            val connection = URI("https://download.videolan.org/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip")
                .toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw GradleException("No se pudo bajar VLC: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                zip.outputStream().use { output -> input.copyTo(output) }
            }
            if (!isValidZip(zip)) {
                zip.delete()
                throw GradleException("La descarga de VLC no es un zip válido.")
            }
        }
    }
}

tasks.register<Copy>("prepareVlc") {
    dependsOn("downloadVlc")
    // El zip trae una carpeta superior (vlc-3.0.24/): se quita para
    // que libvlc.dll y plugins/ queden directo en vlc/.
    from(zipTree(vlcZipFile.get().asFile)) {
        eachFile {
            val segments = relativePath.segments
            if (segments.size > 1) {
                relativePath = RelativePath(true, *segments.drop(1).toTypedArray())
            } else {
                exclude()
            }
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("appResources/windows-x64/vlc"))
    // Fallo ruidoso si las librerías no quedaron: mejor que el build
    // falle aquí que un instalador sin VLC.
    doLast {
        val vlcDir = layout.buildDirectory.dir("appResources/windows-x64/vlc").get().asFile
        val dll = File(vlcDir, "libvlc.dll")
        val plugins = File(vlcDir, "plugins")
        if (!dll.exists() || !plugins.isDirectory) {
            throw GradleException(
                "prepareVlc no dejó las librerías de VLC en ${vlcDir.absolutePath} " +
                    "(libvlc.dll: ${dll.exists()}, plugins: ${plugins.exists()})."
            )
        }
    }
}

// Cinturón y tirantes: además del dependsOn del DSL, amarrar la
// preparación de VLC a cada tarea de empaquetado/ejecución.
tasks.matching { it.name in setOf("packageExe", "createDistributable", "run", "runDistributable") }
    .configureEach { dependsOn("prepareVlc") }

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        named("jvmMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(project(":shared"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("uk.co.caprica:vlcj:4.12.1")
                implementation("net.jthink:jaudiotagger:3.0.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aural.player.desktop.MainKt"
        dependsOn("prepareVlc")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "AuralPlayer"
            // Sube en cada build de prueba: con upgradeUuid fijo, una
            // versión mayor reemplaza la instalada sin desinstalar.
            packageVersion = "1.2.2"
            description = "AuralPlayer — El sonido, sin ruido."
            copyright = "© 2026 Dah3l"
            // Recursos extra (las librerías de VLC) dentro del paquete.
            appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
            // Todos los módulos del JDK: evita ClassNotFoundException en
            // el runtime recortado (JNA/VLCJ tocan módulos no obvios).
            includeAllModules = true
            windows {
                menuGroup = "AuralPlayer"
                shortcut = true
                // Código fijo para que las actualizaciones con una
                // versión mayor reemplacen la instalada. NUNCA cambiar.
                upgradeUuid = "1960c8b2-00ee-4247-852c-c6f85b17e563"
            }
        }
    }
}
