// Configuración del módulo de aplicación Aural
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aural.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aural.player"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // Compatible con Kotlin 1.9.24
    }
}

dependencies {
    // ---- Jetpack Compose + Material 3 ----
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

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
