import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Die geteilte Oberflaeche: dieselbe Compose-UI fuer die Android-App und
// das iPhone.
//
// Das geht nur, weil dieses Spiel alles im Code zeichnet — keine Layouts,
// keine Bild-Assets, nur Rechtecke auf einem Canvas. Genau davon laeuft
// Compose Multiplatform auf iOS (ueber Skia) unveraendert.
//
// Ziele:
//   androidTarget()       -> :app
//   iosArm64              -> iPhone
//   iosSimulatorArm64     -> Simulator auf Apple Silicon
//   iosX64                -> Simulator auf Intel-Macs
//
// Was NICHT hierher gehoert: alles, was nur ein Store kann — Werbung,
// Kaeufe, Play Games, der Abgleich mit der Uhr. Das bleibt in :app.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Wie in :core: ein XCFramework buendelt Geraet und beide
    // Simulator-Architekturen. Gebaut mit
    //   ./gradlew :ui:assembleDottieUiDebugXCFramework
    val xcf = XCFramework("DottieUi")
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "DottieUi"
            // Compose bringt Skia mit; als statisches Framework waechst
            // die App-Groesse nicht doppelt.
            isStatic = true
            xcf.add(this)
            export(project(":core"))
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // api statt implementation: Die Typen aus :core stehen in
                // den Signaturen der Composables, der Konsument muss sie
                // sehen.
                api(project(":core"))
                api(compose.runtime)
                api(compose.foundation)
                implementation(compose.material3)
                api(compose.ui)
                // api statt implementation: :app liest dieselben Texte
                // (Res.string.…) und braucht die Typen im Klassenpfad.
                @OptIn(ExperimentalComposeLibrary::class)
                api(compose.components.resources)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "de.robinrehbein.punkt.ui"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

compose.resources {
    // Der erzeugte Zugriffspunkt heisst Res und liegt im Paket der
    // Oberflaeche — `Res.string.app_name` statt `R.string.app_name`.
    publicResClass = true
    packageOfResClass = "de.robinrehbein.punkt.ui.resources"
    generateResClass = always
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}
