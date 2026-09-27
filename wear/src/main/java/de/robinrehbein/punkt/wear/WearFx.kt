package de.robinrehbein.punkt.wear

import de.robinrehbein.punkt.game.TimingGame

/**
 * Die kurzen Effekte der Spielwelt, wie FxState am Telefon: Blitz und
 * Wackeln beim Tod, der Goldring bei jeder Freischaltung und die Uhr
 * seit dem Tod (für die Bomben-Funken). Reine Zahlen, ohne Compose-State:
 * Gelesen wird beim Zeichnen, und neu gezeichnet wird ohnehin jeden Frame.
 *
 * Die Werte stammen vom Telefon (GameScreen, Timing.kt), damit sich ein
 * Tod auf beiden Geräten gleich anfühlt.
 */
internal class WearFx {

    /** Deckkraft des weißen Blitzes, 1 im Todesmoment, fällt mit 3,5/s. */
    var flashAlpha = 0f
        private set

    /** Restzeit des Wackelns nach dem Tod. */
    var shakeTime = 0f
        private set

    /** Restzeit des Goldrings (Twist, Stufe, Rekord), 0 = aus. */
    var celebrateTime = 0f
        private set

    /** Sekunden seit dem Tod, negativ = kein Tod in diesem Lauf. */
    var deathTime = -1f
        private set

    /** Ein Frame von [dt] Sekunden. */
    fun step(dt: Float) {
        flashAlpha = (flashAlpha - dt * FLASH_FADE_PER_SECOND).coerceAtLeast(0f)
        shakeTime = (shakeTime - dt).coerceAtLeast(0f)
        celebrateTime = (celebrateTime - dt).coerceAtLeast(0f)
        if (deathTime >= 0f) deathTime += dt
    }

    /** Tod: Blitz, Wackeln, Feier aus, Todesuhr läuft. */
    fun died() {
        flashAlpha = 1f
        shakeTime = SHAKE_SECONDS
        celebrateTime = 0f
        deathTime = 0f
    }

    /** Freischaltung, neue Stufe oder Rekord im Lauf. */
    fun celebrate() {
        celebrateTime = CELEBRATE_SECONDS
    }

    /** Neuer Lauf oder App im Hintergrund: alles auf Anfang. */
    fun reset() {
        flashAlpha = 0f
        shakeTime = 0f
        celebrateTime = 0f
        deathTime = -1f
    }

    companion object {
        /** Wie am Telefon (CELEBRATE_SECONDS in ui/.../world/Timing.kt). */
        const val CELEBRATE_SECONDS = 1.1f

        /** Wie am Telefon (fx.shakeTime = 0.4f beim Tod). */
        const val SHAKE_SECONDS = 0.4f

        /** Wie am Telefon (flashAlpha - dt * 3.5f). */
        const val FLASH_FADE_PER_SECOND = 3.5f

        /**
         * Wackel-Ausschlag je Sekunde Restzeit, als Anteil der Bildgröße.
         * Am Telefon 28 px bei 2400 px Höhe; auf der Uhr dieselbe Relation.
         */
        const val SHAKE_SHARE = 28f / 2400f

        /** Dauer der Bomben-Funken (TRAP_BOOM_SECONDS am Telefon). */
        const val BOOM_SECONDS = 0.45f

        /**
         * Verzögerung der Bomben-Funken nach dem Tod (TRAP_BOOM_DELAY am
         * Telefon): das Ende des Freeze, zugleich mit dem Platzen des Vogels.
         */
        const val BOOM_DELAY = TimingGame.DEATH_FREEZE_SECONDS
    }
}
