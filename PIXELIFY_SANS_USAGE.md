# Pixelify Sans Font Usage Guide

Die Pixelify Sans Schriftart von Google Fonts ist jetzt erfolgreich in deiner Android-App konfiguriert.

## Konfiguration

### 1. Font-Ressource
- **Datei**: [`app/src/main/res/font/pixelify_sans.xml`](app/src/main/res/font/pixelify_sans.xml)
- **Typ**: Google Fonts Provider (lädt automatisch von Google herunter)

### 2. Typography Definition
- **Datei**: [`app/src/main/java/de/robinrehbein/punkt/ui/theme/Type.kt`](app/src/main/java/de/robinrehbein/punkt/ui/theme/Type.kt)
- **FontFamily**: `PixelifySans` mit verschiedenen FontWeights
- **Material 3 Typography**: Alle Textstile verwenden Pixelify Sans

### 3. Zertifikate
- **Datei**: [`app/src/main/res/values/font_certs.xml`](app/src/main/res/values/font_certs.xml)
- **Zweck**: Google Play Services Zertifikate für Font-Download

### 4. Dependency
- **Datei**: [`app/build.gradle.kts`](app/build.gradle.kts)
- **Dependency**: `com.google.android.gms:play-services-base:18.2.0`

## Verwendung

### Methode 1: Material Theme Typography (Empfohlen)
```kotlin
Text(
    text = "Dein Text",
    style = MaterialTheme.typography.headlineLarge
)
```

**Verfügbare Stile:**
- `displayLarge/Medium/Small` - Große Überschriften (57sp/45sp/36sp)
- `headlineLarge/Medium/Small` - Überschriften (32sp/28sp/24sp)
- `titleLarge/Medium/Small` - Titel (22sp/16sp/14sp)
- `bodyLarge/Medium/Small` - Fließtext (16sp/14sp/12sp)
- `labelLarge/Medium/Small` - Labels/Buttons (14sp/12sp/11sp)

### Methode 2: Direkte FontFamily Verwendung
```kotlin
Text(
    text = "Dein Text",
    fontFamily = PixelifySans,
    fontSize = 20.sp,
    fontWeight = FontWeight.Bold
)
```

### Methode 3: Custom TextStyle
```kotlin
val customStyle = TextStyle(
    fontFamily = PixelifySans,
    fontSize = 18.sp,
    fontWeight = FontWeight.Medium,
    color = Color.White
)

Text(
    text = "Dein Text",
    style = customStyle
)
```

## Beispiele aus deiner App

### GameScreen Implementierung
Die [`GameScreen.kt`](app/src/main/java/de/robinrehbein/punkt/ui/screens/GameScreen.kt) verwendet bereits die Typography-Stile:

```kotlin
// Score Anzeige
Text(
    text = "Score: $score",
    color = Color.White,
    style = MaterialTheme.typography.titleMedium
)

// Game Over Titel
Text(
    text = "GAME OVER",
    color = Color.Red,
    style = MaterialTheme.typography.headlineLarge
)

// Feedback Nachrichten
Text(
    text = message,
    color = color,
    style = MaterialTheme.typography.headlineSmall
)
```

## Vorteile dieser Implementierung

1. **Automatischer Download**: Font wird bei Bedarf von Google heruntergeladen
2. **Konsistenz**: Einheitliche Schriftart in der gesamten App
3. **Material Design**: Folgt Material 3 Typography-Richtlinien
4. **Performance**: Effiziente Schriftart-Verwaltung
5. **Wartbarkeit**: Zentrale Konfiguration in Type.kt

## Troubleshooting

Falls die Schriftart nicht lädt:
1. Internetverbindung prüfen (für ersten Download)
2. Google Play Services auf dem Gerät aktualisieren
3. App neu starten nach Font-Änderungen

Die Schriftart wird automatisch in allen Text-Komponenten verwendet, die `MaterialTheme.typography` Stile verwenden.