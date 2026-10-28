plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // Le namespace (package Kotlin genere pour R/BuildConfig) reste com.scanlist.app :
    // le renommer imposerait de reecrire le "package" et les imports de tous les fichiers
    // source, sans aucun effet sur la coexistence des deux applications. C'est
    // l'applicationId ci-dessous qui identifie l'app pour Android — lui seul importe pour
    // eviter le conflit avec ScanList, application independante (decision 23 de
    // consignes/contexte.md).
    namespace = "com.scanlist.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.suiviemat.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Cle de debug versionnee, partagee par le poste Windows, la CI et les sessions
    // cloud. Sans elle, chaque machine signe avec sa propre cle, et un APK de test ne
    // peut pas remplacer le precedent sans desinstallation — donc sans perdre les
    // dossiers du telephone. Elle n'est PAS secrete : jamais pour signer un APK distribue
    // (la cle de release est a part, voir consignes/plan-suivimat.md § 0).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // Les modeles ML Kit embarques pesent lourd et sont livres pour quatre ABI.
    // Filtrer divise l'APK par ~4, mais un APK arm64 seul refuse de s'installer sur
    // l'emulateur x86_64 : le debug embarque donc les deux architectures — sauf avec
    // -Papk.phoneOnly, utilise par la CI pour l'APK depose dans release/, destine au seul
    // telephone (17 Mo de moins a chaque version conservee dans l'historique git).
    val phoneOnly = providers.gradleProperty("apk.phoneOnly").isPresent
    buildTypes {
        debug {
            ndk {
                abiFilters += if (phoneOnly) listOf("arm64-v8a") else listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            // Materiel cible : Galaxy S25, arm64 uniquement.
            ndk {
                abiFilters += "arm64-v8a"
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Schemas Room exportes : necessaires pour tester les migrations futures.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Base
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit (modeles embarques => scan hors-ligne)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
