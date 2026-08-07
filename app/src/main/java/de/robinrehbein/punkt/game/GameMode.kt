package de.robinrehbein.punkt.game

/**
 * Die zwei Spielprinzip-Kandidaten, zum direkten Vergleich im Spiel
 * umschaltbar. Highscores werden pro Modus getrennt gespeichert.
 */
enum class GameMode(val displayName: String) {
    GRAVITY_FLIP("FLIP"),
    TIME_STOP("STOPP")
}
