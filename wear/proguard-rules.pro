# R8-Regeln für die Wear-App.
#
# Eigene Klassen (:wear, :core, :sync) bleiben vollständig: Enum-Namen
# landen in den Prefs und im Data-Layer-Abgleich mit dem Telefon
# (Skin, Welt, Ton), und das Telefon liest sie mit seinen eigenen —
# ebenfalls ungekürzten — Namen. Der Code ist klein, der Gewinn durch R8
# steckt in Compose und Kotlin-Stdlib.
-keep class de.robinrehbein.punkt.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Play Billing, Play Services und AndroidX bringen ihre eigenen
# Consumer-Regeln mit; hier ist nichts weiter nötig.
