import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Reines Kotlin-JVM-Modul (keine Android-Abhängigkeiten): enthält die
// Spiellogik, die auch ein künftiges :wear-Modul ohne Android-Kontext
// wiederverwenden kann.
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "11"
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

dependencies {
    // Nur zum Testen: Die Skin-Muster (SkinPaint) rechnen hier, also
    // gehören ihre Tests auch hierher. Die restliche Spiellogik wird
    // weiterhin aus :app geprüft (TimingGameTest), wo der Test-Stack
    // ohnehin steht.
    testImplementation(libs.junit)
}
