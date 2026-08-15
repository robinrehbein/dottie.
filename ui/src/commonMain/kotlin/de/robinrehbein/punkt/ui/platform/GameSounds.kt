package de.robinrehbein.punkt.ui.platform

import de.robinrehbein.punkt.game.SoundSetId

/**
 * Der Klang des Spiels — so, wie die Oberflaeche ihn braucht.
 *
 * WAS klingt, steht in `:core` ([de.robinrehbein.punkt.game.SoundBank]
 * beschreibt jeden Ton, [de.robinrehbein.punkt.game.ChipSynth] rechnet
 * die Samples). WIE es zum Lautsprecher kommt, ist Plattformsache:
 * Android nimmt SoundPool, iOS AVAudioEngine. Nur dieser Unterschied
 * steht hinter der Schnittstelle.
 */
interface GameSounds {

    /** Stumm geschaltet? Die Oberflaeche haelt das mit dem Speicher synchron. */
    var muted: Boolean

    /** Das gewaehlte Ton-Set. */
    var soundSet: SoundSetId

    fun start()

    /** Treffer-Blip; die Tonhoehe klettert pro 5er-Stufe eine Pentatonik hoch. */
    fun hit(score: Int)

    /** Muenz-Sound; jede Serien-Stufe klingt zwei Halbtoene hoeher. */
    fun perfect(streak: Int)

    fun chain()
    fun unlock()
    fun death()
    fun thud()
    fun newRecord()

    /**
     * Hoerprobe fuer die Auswahl: die Fanfare des angetippten Sets, auch
     * wenn es gerade nicht das gewaehlte ist. Ohne Probe waehlt man einen
     * Klang nach seinem Namen.
     */
    fun preview(set: SoundSetId)

    fun release()
}

/**
 * Das Ruetteln im Gehaeuse. Jede Stufe hat ihr eigenes Muster — ein
 * Treffer fuehlt sich anders an als ein Tod, und der Rekord anders als
 * beides.
 */
interface GameFeedback {
    fun score()
    fun perfect()
    fun unlock()
    fun death()
    fun thud()
    fun newRecord()
}
