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
    //
    // RECORTE SOLO-AUDIO: del zip completo (~197 MB) se copian solo
    // las librerías base y los plugins de audio. Fuera: interfaz
    // gráfica, video, subtítulos, streaming, idiomas y utilidades.
    // libavcodec_plugin queda como decodificador universal de
    // respaldo; los demuxers/codecs listados cubren mp3, m4a/aac,
    // flac, ogg, opus, wav, wma, aiff y demás formatos de música.
    // OBLIGATORIO: plugins/logger/libfile_logger_plugin.dll NO se
    // puede recortar: --file-logging y --logfile (el registro de
    // diagnóstico de VlcPlayer) solo los registra ese plugin, y
    // libvlc_new devuelve NULL si recibe una opción desconocida
    // (config_LoadCmdLine, 2ª pasada, es fatal). Sin el plugin,
    // VLC no inicia: "Failed to get a new native library instance"
    // (verificado contra el fuente de VLC 3.0.24, 2026-11-17).
    from(zipTree(vlcZipFile.get().asFile)) {
        val top = "vlc-$vlcVersion"
        include(
            "$top/libvlc.dll",
            "$top/libvlccore.dll",
            "$top/plugins/logger/libfile_logger_plugin.dll",
            "$top/plugins/audio_output/**",
            "$top/plugins/audio_mixer/**",
            "$top/plugins/audio_filter/**",
            "$top/plugins/access/libfilesystem_plugin.dll",
            "$top/plugins/access/libhttp_plugin.dll",
            "$top/plugins/access/libhttps_plugin.dll",
            "$top/plugins/codec/libavcodec_plugin.dll",
            "$top/plugins/codec/libfaad_plugin.dll",
            "$top/plugins/codec/libflac_plugin.dll",
            "$top/plugins/codec/libvorbis_plugin.dll",
            "$top/plugins/codec/libopus_plugin.dll",
            "$top/plugins/codec/libmpg123_plugin.dll",
            "$top/plugins/codec/libspeex_plugin.dll",
            "$top/plugins/codec/libaraw_plugin.dll",
            "$top/plugins/codec/liblpcm_plugin.dll",
            "$top/plugins/codec/libadpcm_plugin.dll",
            "$top/plugins/codec/libg711_plugin.dll",
            "$top/plugins/codec/libspdif_plugin.dll",
            "$top/plugins/codec/libaes3_plugin.dll",
            "$top/plugins/demux/libmp4_plugin.dll",
            "$top/plugins/demux/libogg_plugin.dll",
            "$top/plugins/demux/libflacsys_plugin.dll",
            "$top/plugins/demux/libasf_plugin.dll",
            "$top/plugins/demux/libwav_plugin.dll",
            "$top/plugins/demux/libaiff_plugin.dll",
            "$top/plugins/demux/libau_plugin.dll",
            "$top/plugins/demux/libcaf_plugin.dll",
            "$top/plugins/demux/libmkv_plugin.dll",
            "$top/plugins/demux/libmpc_plugin.dll",
            "$top/plugins/demux/libtta_plugin.dll",
            "$top/plugins/demux/libvoc_plugin.dll",
            "$top/plugins/demux/libxa_plugin.dll",
            "$top/plugins/demux/librawaud_plugin.dll",
            "$top/plugins/demux/libes_plugin.dll",
            "$top/plugins/packetizer/libpacketizer_copy_plugin.dll",
            "$top/plugins/packetizer/libpacketizer_flac_plugin.dll",
            "$top/plugins/packetizer/libpacketizer_mpeg4audio_plugin.dll",
            "$top/plugins/packetizer/libpacketizer_mpegaudio_plugin.dll"
        )
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
        val required = listOf(
            "libvlc.dll",
            "libvlccore.dll",
            "plugins/codec/libavcodec_plugin.dll",
            "plugins/codec/libfaad_plugin.dll",
            "plugins/demux/libmp4_plugin.dll",
            "plugins/access/libfilesystem_plugin.dll",
            "plugins/audio_output/libdirectsound_plugin.dll",
            "plugins/logger/libfile_logger_plugin.dll"
        )
        val missing = required.filter { !File(vlcDir, it).exists() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "prepareVlc dejó incompleto el VLC en ${vlcDir.absolutePath}. Faltan: ${missing.joinToString(", ")}."
            )
        }
    }
}

// Cinturón y tirantes: además del dependsOn del DSL, amarrar la
// preparación de VLC a cada tarea de empaquetado/ejecución. OJO:
// prepareAppResources es la tarea del plugin que lee los recursos;
// sin esta dependencia, Gradle la corre antes de prepareVlc y el
// paquete sale sin las librerías (y Gradle 9 lo rechaza).
tasks.matching {
    it.name in setOf(
        "packageExe", "createDistributable", "run",
        "runDistributable", "prepareAppResources"
    )
}.configureEach { dependsOn("prepareVlc") }

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
                // Directas aunque :shared ya las trae: el empaquetado
                // de escritorio no arrastró las transitivas de shared
                // y la app moría en tiempo de ejecución con
                // NoClassDefFoundError (1.2.11–1.2.13). Las fechas
                // ya no usan librería (SystemDates con java.time).
                implementation("io.ktor:ktor-client-core:3.6.1")
                implementation("io.ktor:ktor-client-cio:3.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
                implementation("uk.co.caprica:vlcj:4.12.1")
                implementation("net.jthink:jaudiotagger:3.0.1")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aural.player.desktop.MainKt"
        // Tope de memoria de la JVM: sin esto el heap crece libre
        // (en un PC de 12 GB el tope por defecto es ~3 GB) y la app
        // se iba a 1 GB+ solo con las portadas.
        applicationDefaultJvmArgs = listOf("-Xmx768m")
        dependsOn("prepareVlc")
        nativeDistributions {
            targetFormats(TargetFormat.Exe)
            packageName = "AuralPlayer"
            // Sube en cada build de prueba: con upgradeUuid fijo, una
            // versión mayor reemplaza la instalada sin desinstalar.
            packageVersion = "1.2.17"
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
