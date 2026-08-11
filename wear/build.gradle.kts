// Eigenständiger Wear-OS-Prototyp: Ein-Modus-Timing-Spiel für runde Uhren,
// nutzt die Spiellogik aus :core. Bewusst abgespeckt gegenüber :app (kein
// Daily, keine Skins, kein Teilen, keine Notifications) — siehe README.md.
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
        applicationId = "de.robinrehbein.punkt"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-wear-proto"
    }

    // Kein eigener signingConfig: Für den Prototyp reicht die automatische
    // Debug-Signatur. Ein unsignierter Release-Build ist für ein Wear-Modul
    // ohne Play-Store-Anbindung unkritisch.

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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Wear-Compose bringt eigene Artefakt-Versionen mit (siehe
    // libs.versions.toml) — nicht Teil der androidx.compose-BOM.
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
}
