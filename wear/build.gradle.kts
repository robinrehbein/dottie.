// Eigenständige Wear-OS-App: Timing-Spiel für runde Uhren, nutzt die
// Spiellogik aus :core — Classic- und Daily-Modus plus freischaltbare
// Skins. Bewusst abgespeckt gegenüber :app bleiben nur Teilen und
// Notifications — siehe README.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.robinrehbein.punkt.wear"
    compileSdk = 36

    defaultConfig {
        // Gleiche applicationId wie :app: Play Console führt Phone- und
        // Wear-APK unter einem gemeinsamen Store-Eintrag (Multi-Form-Factor-
        // Listing) — nötig für den späteren gemeinsamen Play-Eintrag.
        applicationId = "de.robinrehbein.pointless"
        minSdk = 30
        targetSdk = 35
        // Play verlangt eindeutige versionCodes über ALLE Artefakte einer
        // Paket-ID hinweg. Die Phone-App zählt ab 25 aufwärts, Wear bekommt
        // deshalb einen eigenen Bereich ab 100001 — so kollidieren die
        // beiden Zähler nie.
        // 100005 ging bereits als Build 110 raus (Augen-Kontur), der
        // Uhr-Abgleich bekommt deshalb 100006.
        versionCode = 100008
        versionName = "0.2.6-wear"
    }

    signingConfigs {
        create("release") {
            // Gleiche Logik wie in :app: Ein über PUNKT_KEYSTORE_FILE
            // gesetzter Keystore (CI nach der Rotation) gewinnt immer und
            // macht die Credentials zur Pflicht. Ohne die Variable signiert
            // der eingecheckte Test-Keystore (Passwort öffentlich) — er
            // darf NIE in den Play Store.
            fun env(name: String): String? =
                System.getenv(name)?.takeUnless { it.isBlank() }

            val envStorePath = env("PUNKT_KEYSTORE_FILE")
            if (envStorePath != null) {
                storeFile = file(envStorePath)
                storePassword = env("PUNKT_KEYSTORE_PASSWORD")
                    ?: error("PUNKT_KEYSTORE_FILE gesetzt, aber PUNKT_KEYSTORE_PASSWORD fehlt")
                keyAlias = env("PUNKT_KEY_ALIAS") ?: "punkt"
                keyPassword = env("PUNKT_KEY_PASSWORD")
                    ?: error("PUNKT_KEYSTORE_FILE gesetzt, aber PUNKT_KEY_PASSWORD fehlt")
            } else {
                storeFile = file("../punkt-release-key.keystore")
                storePassword = "punktapp123"
                keyAlias = "punkt"
                keyPassword = "punktapp123"
            }
        }
    }

    buildTypes {
        release {
            // Bewusst ohne Minify: Der Wear-Prototyp ist nie mit R8 getestet
            // worden, und auf der Uhr zählt Verlässlichkeit mehr als die
            // paar hundert KB — erst aktivieren, wenn es auf echter Hardware
            // geprüft werden kann.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":sync"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Wear-Compose bringt eigene Artefakt-Versionen mit (siehe
    // libs.versions.toml) — nicht Teil der androidx.compose-BOM.
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)

    // Play Billing, hier ausschliesslich LESEND (WearPatron): Der
    // Goenner-Kauf haengt am Google-Konto, nicht am Geraet, und Wear- und
    // Phone-APK teilen sich die Paket-ID — die Uhr fragt ihn deshalb
    // selbst ab, statt sich ein faelschbares Flag schicken zu lassen.
    // Gleiche Version wie in :app (libs.versions.toml).
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
}
