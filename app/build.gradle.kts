plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "de.robinrehbein.punkt"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.robinrehbein.punkt"
        minSdk = 28
        targetSdk = 36
        versionCode = 13
        versionName = "2.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials kommen bevorzugt aus der Umgebung (CI: GitHub
            // Secrets PUNKT_KEYSTORE_PASSWORD / PUNKT_KEY_PASSWORD). Der
            // Fallback hält lokale Test-Builds am Laufen — vor einem
            // echten Store-Release MUSS der Keystore rotiert und der
            // Fallback entfernt werden (Passwort stand im Repo-Verlauf).
            fun secret(name: String, fallback: String): String =
                System.getenv(name)?.takeUnless { it.isBlank() } ?: fallback

            storeFile = file("../punkt-release-key.keystore")
            storePassword = secret("PUNKT_KEYSTORE_PASSWORD", "punktapp123")
            keyAlias = secret("PUNKT_KEY_ALIAS", "punkt")
            keyPassword = secret("PUNKT_KEY_PASSWORD", "punktapp123")
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

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