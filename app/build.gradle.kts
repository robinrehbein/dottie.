plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.robinrehbein.punkt"
    compileSdk = 36

    defaultConfig {
        // Sichtbarer Name ist seit v2.15 "Dottie." — die Paket-ID bleibt
        // trotzdem de.robinrehbein.pointless: Sie ist im Play-Eintrag mit
        // laufendem internem Test registriert und fuer Nutzer unsichtbar
        // (nur in der Store-URL). Ein ID-Wechsel wuerde den kompletten
        // Play-Eintrag samt Testern verwerfen. (Historie: bis v2.13 hiess
        // die App "PUNKT.", deren Paket-ID ist ohne den verlorenen
        // Original-Key fuer immer blockiert.)
        applicationId = "de.robinrehbein.pointless"
        minSdk = 28
        targetSdk = 36
        // v2.17: 21 Punkt-Skins (gemustert, bewegt, auf den Lauf
        // reagierend) und der Himmel im Tag-Nacht-Umlauf.
        versionCode = 28
        versionName = "2.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Ein über PUNKT_KEYSTORE_FILE gesetzter Keystore (CI nach der
            // Rotation, siehe PUBLISHING.md) gewinnt immer; dann sind alle
            // Credentials Pflicht und es gibt keinen Fallback. Ohne diese
            // Variable signiert der eingecheckte Test-Keystore, dessen
            // Passwort öffentlich ist — er darf NIE in den Play Store.
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    
    bundle {
        language {
            enableSplit = true
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Play Games Services v2 (Bestenlisten) — inaktiv, bis in
    // res/values/games.xml echte IDs stehen (siehe PUBLISHING.md).
    implementation(libs.play.services.games)
    // Monetarisierung: AdMob (Rewarded + Interstitial), UMP-Consent und
    // Play Billing ("Werbung entfernen") — inaktiv, bis in
    // res/values/ads.xml echte IDs stehen (siehe PUBLISHING.md).
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)
    // WorkManager für die tägliche Daily-Challenge-Erinnerung
    implementation(libs.androidx.work.runtime)

    // ViewModel und Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // DataStore für Settings
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    // Compose Animation
    implementation("androidx.compose.animation:animation:1.5.4")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}