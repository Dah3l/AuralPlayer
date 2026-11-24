// Configuración del módulo de aplicación Aural
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    // Sin org.jetbrains.kotlin.android: AGP 9 trae Kotlin integrado.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aural.player"
    // compileSdk 37: lo exige Compose 1.12 (BOM 2026.09.00) + AGP 9.
    // targetSdk se queda en 34: sin cambios de comportamiento.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aural.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "1.2.0"

        // Solo ABIs de teléfonos reales (sin x86 de emuladores): las
        // librerías nativas de ML Kit pesan mucho y esto recorta el APK.
        ndk {
            abiFilters += "armeabi-v7a"
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            // Solo en CI: la keystore llega decodificada a una ruta temporal.
            // Sin estas variables de entorno (build local), esta config queda vacía.
            val ksPath = System.getenv("AURAL_KEYSTORE_PATH")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("AURAL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("AURAL_KEY_ALIAS")
                keyPassword = System.getenv("AURAL_KEY_PASSWORD")
            }
            // Apklis exige firma v1 (JAR) para validar la llave: con minSdk 26
            // Gradle la omite por defecto, así que se fuerza explícitamente.
            // (Nombres nuevos de AGP 9: enableV1Signing/enableV2Signing.)
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        debug {
            // Paquete aparte (com.aural.player.debug): la build de prueba
            // convive con la release instalada sin tocar ninguno de sus
            // datos, para probar a fondo antes de lanzar la versión.
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "Aural DEBUG")
        }
        release {
            // Firma con la keystore oficial cuando está disponible (CI)
            if (System.getenv("AURAL_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Desugaring: empaqueta APIs nuevas de Java para Android viejos.
        // NewPipeExtractor llama URLEncoder.encode(String, Charset), que
        // solo existe desde Android 13; con esto funciona desde el minSdk.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        // AGP 9 trae resValues apagado por defecto: el buildType debug
        // usa resValue para el nombre "Aural DEBUG", así que se enciende.
        resValues = true
    }

    packaging {
        jniLibs {
            // Comprimir las librerías nativas dentro del APK: la descarga
            // pesa mucho menos (las de ML Kit son lo más pesado de la app).
            // A cambio, se extraen al instalar (instalación más lenta y
            // más espacio ocupado). Para datos móviles, compensa.
            useLegacyPackaging = true
        }
    }
}

// Opciones del compilador Kotlin (DSL nuevo de AGP 9: kotlinOptions
// quedó atrás; esto es lo mismo que antes: bytecode JVM 17 + el
// opt-in de Material3 experimental). Va FUERA del bloque android.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    // ---- Módulo compartido (fase 3): lógica común con la versión PC ----
    implementation(project(":shared"))

    // ---- Jetpack Compose + Material 3 ----
    // BOM 2026.09.00 = Compose 1.12.1, la misma base de CMP 1.12.2.
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Reordenar arrastrando (cola y playlists): la que usan InnerTune/OuterTune ----
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    // ---- Tags reales (v1.2.0): escribir ID3/Vorbis/MP4 en el archivo ----
    implementation("net.jthink:jaudiotagger:3.0.1")

    // ---- Desugaring (sabor NIO: es el que retroporta URLEncoder con Charset,
    // igual que en la app oficial de NewPipe) ----
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // ---- Core KTX (ContextCompat y utilidades) ----
    implementation("androidx.core:core-ktx:1.19.0")

    // ---- Actividad y ciclo de vida ----
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    // v1.0.7: isla dinámica (overlay desde el servicio)
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.11.0")

    // ---- DataStore Preferences (ajustes) ----
    implementation("androidx.datastore:datastore-preferences:1.3.0")

    // ---- Coroutines ----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // await() sobre ListenableFuture (usado por MediaController)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    // await() sobre Task de Play Services (usado por ML Kit)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // ---- Media3 / ExoPlayer (reproducción + MediaSession + notificación) ----
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-common:1.11.1")

    // ---- Coil (carga de carátulas desde MediaStore) ----
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    // ---- NewPipe Extractor (búsqueda y descarga de YouTube) ----
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

    // ---- Realtime BPM Analyzer (tempo para la Mezcla DJ, sin dependencias) ----
    implementation("io.github.dlepaux:realtime-bpm-analyzer:6.3.0")

    // ---- ML Kit (traducción de letras en el teléfono + detección de idioma) ----
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")

}
