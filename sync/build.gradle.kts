plugins {
    // Ohne version: Das AGP liegt ueber :app/:wear schon auf dem
    // Buildscript-Classpath, ein zweites Mal mit Version angefordert
    // lehnt Gradle ab ("already on the classpath").
    id("com.android.library")
    alias(libs.plugins.kotlin.android)
}

// Der Handy-Uhr-Abgleich. Eigenes Modul, weil :app und :wear exakt
// dieselbe Data-Layer-Mechanik brauchen und sich sonst hundert Zeilen
// teilen müssten, die man garantiert nur auf einer Seite pflegt. Die
// Zusammenführungs-Regeln selbst liegen eine Ebene tiefer in :core
// (SyncState) und sind dort ohne Android testbar.
android {
    namespace = "de.robinrehbein.punkt.sync"
    compileSdk = 36

    defaultConfig {
        // Das Minimum der beiden Nutzer: :app steht auf 28, :wear auf 30.
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.play.services.wearable)
}
