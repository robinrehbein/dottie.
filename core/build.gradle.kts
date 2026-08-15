import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Multiplattform-Modul mit der kompletten Spiellogik — die einzige
// Quelle der Wahrheit für Regeln, Farben und Freischaltungen.
//
// Ziele:
//   jvm()                 -> :app, :wear und :sync (Android liest die
//                            JVM-Variante; Kotlins Plattform-Regel lässt
//                            androidJvm-Konsumenten jvm-Produzenten nutzen)
//   iosArm64              -> iPhone
//   iosSimulatorArm64     -> Simulator auf Apple-Silicon-Macs und in der CI
//
// Bis v2.23 war das ein reines Kotlin-JVM-Modul, und der iOS-Port hat die
// Spiellogik von Hand in Swift nachgebaut. Der Handport entfällt damit —
// siehe ARCHITEKTUR.md.
//
// Die Apple-Ziele lassen sich nur auf einem Mac übersetzen. Auf Linux
// konfiguriert Gradle sie trotzdem; gebaut werden sie dort nicht (siehe
// kotlin.native.ignoreDisabledTargets in gradle.properties).
plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting

        val jvmTest by getting {
            dependencies {
                // Die Tests bleiben JVM-only, und zwar bewusst: Sie
                // erzeugen und lesen parity/golden-vectors.txt, und
                // Dateizugriff hat in commonMain keine Entsprechung.
                implementation(libs.junit)
            }
        }
    }
}

// Bytecode-Ziel 11 wie bisher — die Android-Module compilieren ebenfalls
// gegen 11, ein höherer Wert hier würde sie brechen. Ueber die Tasks
// gesetzt statt über kotlin { jvm { compilerOptions } }: Letzteres ist in
// Kotlin 2.0 noch experimentell und meldet das bei jedem Lauf.
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<Test>().configureEach {
    // Reicht -Dparity.update=true an die Test-JVM weiter: Damit schreibt
    // ParityVectorsTest parity/golden-vectors.txt neu, statt dagegen zu
    // pruefen (siehe parity/README.md). Ohne die Weitergabe kaeme das
    // Flag nie im Test an, weil Gradle Tests in einer eigenen JVM startet.
    val parityUpdate = System.getProperty("parity.update")
    if (parityUpdate != null) {
        systemProperty("parity.update", parityUpdate)
    }
    // Beim Neuschreiben darf der Task nicht als UP-TO-DATE durchgewunken
    // werden — sonst laeuft er gar nicht erst an.
    outputs.upToDateWhen { parityUpdate != "true" }
}
