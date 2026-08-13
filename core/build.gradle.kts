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

dependencies {
    // Nur zum Testen: Die Skin-Muster (SkinPaint) rechnen hier, also
    // gehören ihre Tests auch hierher. Die restliche Spiellogik wird
    // weiterhin aus :app geprüft (TimingGameTest), wo der Test-Stack
    // ohnehin steht.
    testImplementation(libs.junit)
}
