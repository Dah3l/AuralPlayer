// Configuración del módulo de aplicación Aural
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aural.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aural.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.3"
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

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ---- Jetpack Compose + Material 3 ----
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Reordenar arrastrando (cola y playlists): la que usan InnerTune/OuterTune ----
    implementation("sh.calvin.reorderable:reorderable:3.1.0")

    // ---- Desugaring (sabor NIO: es el que retroporta URLEncoder con Charset,
    // igual que en la app oficial de NewPipe) ----
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    // ---- Core KTX (ContextCompat y utilidades) ----
    implementation("androidx.core:core-ktx:1.13.1")

    // ---- Actividad y ciclo de vida ----
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")

    // ---- DataStore Preferences (ajustes) ----
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ---- Coroutines ----
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // await() sobre ListenableFuture (usado por MediaController)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")

    // ---- Media3 / ExoPlayer (reproducción + MediaSession + notificación) ----
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // ---- Coil (carga de carátulas desde MediaStore) ----
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ---- NewPipe Extractor (búsqueda y descarga de YouTube) ----
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")

}
