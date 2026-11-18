plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Podpis release: jeśli ustawione są zmienne środowiskowe KEYSTORE_*, użyj własnego klucza
// (stały podpis = aktualizacje APK bez odinstalowania). W przeciwnym razie klucz debug.
val ksFile: String? = System.getenv("KEYSTORE_FILE")

android {
    namespace = "pl.homelab.mtlskiosk"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.homelab.mtlskiosk"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures {
        buildConfig = true
    }

    // Każdy flavor = osobna aplikacja (własny applicationId, nazwa, ikona, dane, cert).
    // Nowa usługa: dopisz blok poniżej + opcjonalnie ikonę w app/src/<nazwa>/res.
    flavorDimensions += "service"
    productFlavors {
        fun com.android.build.api.dsl.ApplicationProductFlavor.kiosk(name: String, url: String) {
            resValue("string", "app_name", name)
            buildConfigField("String", "DEFAULT_URL", "\"$url\"")
        }
        create("generic") {
            dimension = "service"
            kiosk("mTLS Kiosk", "")
        }
        create("agh") {
            dimension = "service"
            applicationIdSuffix = ".agh"
            kiosk("AdGuard Home", "https://agh.example.com")
        }
        create("vaultwarden") {
            dimension = "service"
            applicationIdSuffix = ".vaultwarden"
            kiosk("Vaultwarden", "https://vw.example.com")
        }
        create("immich") {
            dimension = "service"
            applicationIdSuffix = ".immich"
            kiosk("Immich", "https://immich.example.com")
        }
    }

    signingConfigs {
        create("release") {
            if (ksFile != null) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
