package de.robinrehbein.punkt.game

import kotlin.math.log2

/**
 * Klangwerk aller Ton-Sets — die dritte Sammlung neben den Skins und den
 * Kulissen und die einzige Quelle für Spielklänge in Kotlin, wie
 * [ScenePaint] für die Kulisse und [SkinPaint] für den Vogel.
 *
 * Ein Ton-Set beschreibt dieselben acht Ereignisse noch einmal in einem
 * anderen Charakter. Es entscheidet dabei nie über einen Treffer: Die
 * Zone bleibt gleich breit, das Gnadenfenster gleich lang, und jedes Set
 * gibt zu jedem Ereignis eine Rückmeldung. Genau deshalb ist der Klang —
 * wie die Kulisse — eine verdienbare Fläche und keine Spielregel.
 *
 * Die Klänge sind bewusst Daten und kein Synthese-Code pro Port: [Tone]
 * beschreibt Frequenz, Dauer, Lautstärke, Abklingrate, Pulsbreite und
 * Wellenform, [Noise] den Rauschanteil darüber, und alle Ports (Android,
 * Wear, iOS) werfen dieselbe Tabelle in denselben Baukasten aus
 * [ChipSynth] — Rechteck, Dreieck, Gleitton, Rauschen. Ohne diese
 * Trennung müsste jedes neue Set in drei Sprachen nachgebaut werden, und
 * liefe in dreien auseinander.
 *
 * Alle Zahlen sind Floats in denselben Einheiten wie [ChipSynth]: Hertz,
 * Sekunden, Anteil 0..1 (Lautstärke, Pulsbreite) und Abklingrate pro
 * Sekunde. Damit bleibt das Modul frei von Android- und Audio-Typen und
 * in Unit-Tests prüfbar.
 */
enum class SoundSetId {
    KLASSIK, GLOCKE, AMBOSS, TROMMEL, ORGEL, PFEIFE, LASER, ROBOTER
}

/**
 * Die Ereignisse, für die das Spiel einen Klang hat — genau die, die
 * GameAudio (Telefon) anbietet. Ein Set, das eines davon auslässt, wäre
 * kein anderer Charakter, sondern ein stummer Moment; [SoundSet] besteht
 * deshalb darauf, dass alle acht beschrieben sind.
 */
enum class SoundEvent {
    /** Lauf-Start. */
    START,

    /** Treffer in der Zone; die Tonhöhe steuert [ChipSynth.hitRate]. */
    HIT,

    /** Perfekt-Treffer; die Serie steuert [ChipSynth.perfectRate]. */
    PERFECT,

    /** Folge-Zone der Kette. */
    CHAIN,

    /** Twist oder Stufe freigeschaltet. */
    UNLOCK,

    /** Neuer Rekord. */
    RECORD,

    /** Tod. */
    DEATH,

    /** Aufschlag am Boden, wenn das Ergebnis feststeht. */
    THUD
}

/**
 * Ein Ton. Bleibt [fromHz] gleich [toHz], ist es eine Rechteckwelle
 * ([ChipSynth.square]); sonst ein Gleitton ([ChipSynth.sweep]).
 *
 * [duty] ist die Pulsbreite der Rechteckwelle und der eigentliche
 * Charakterregler: 0,5 klingt rund und voll, 0,125 dünn und nasal. Beim
 * Gleitton und beim Dreieck steht sie immer auf 0,5 — beide kennen keine
 * Pulsbreite, und eine Zahl, die niemand liest, wäre eine Lüge in der
 * Tabelle (SoundSetTest besteht darauf).
 *
 * [wave] ist die Form. Sie steht am Ton und nicht am Set: Ein Set darf
 * mischen, und der Amboss tut es auch nicht — aber die Glocke braucht
 * ihre weiche Form bis in den Gleitton des Todes hinein, und das ist
 * eine Eigenschaft des Tons, nicht der Sammlung.
 */
data class Tone(
    val fromHz: Float,
    val toHz: Float,
    val seconds: Float,
    val volume: Float,
    val decay: Float,
    val duty: Float,
    val wave: Wave = Wave.PULS
)

/**
 * Rauschanteil, der über die Töne gelegt wird ([ChipSynth.mix]). Er
 * beginnt immer zugleich mit dem ersten Ton: Rauschen ist hier das
 * Geräusch des Aufpralls, nicht ein eigener Ton hinterher.
 */
data class Noise(
    val seconds: Float,
    val volume: Float,
    val decay: Float
)

/**
 * Der Klang eines Ereignisses: [tones] laufen nacheinander
 * ([ChipSynth.concat]), [noise] liegt darüber. Null heißt "dieses
 * Ereignis kommt ohne Rauschen aus" — nicht "Rauschen mit Lautstärke 0".
 */
data class Voice(
    val tones: List<Tone>,
    val noise: Noise? = null
)

/**
 * Ein komplettes Ton-Set. Die Karte trägt jedes Ereignis genau einmal;
 * ein Set mit Lücke käme sonst erst im Spiel als Stille heraus.
 */
class SoundSet(val voices: Map<SoundEvent, Voice>) {
    init {
        require(voices.keys == SoundEvent.entries.toSet()) {
            "Ein Ton-Set beschreibt alle Ereignisse: ${SoundEvent.entries}"
        }
    }
}

object SoundBank {

    // ===== Grenzen, die jedes Set einhalten muss =====

    /**
     * Der Tonumfang, in dem ein Set spielen darf. Unten hört der kleine
     * Uhren-Lautsprecher auf, überhaupt etwas zu bewegen; oben wird jeder
     * Blip zum Piepsen, das im Dauerlauf weh tut. Die Grenzen gelten für
     * die Tabelle, nicht für das Ergebnis: [ChipSynth.hitRate] hebt den
     * Treffer noch einmal um bis zu neun Halbtöne.
     */
    const val MIN_HZ = 50f
    const val MAX_HZ = 2500f

    /**
     * Wie lang ein einzelner Ton sein darf. Die Obergrenze ist keine
     * Geschmacksfrage: Ein Treffer-Klang, der länger dauert als der
     * Abstand zweier Treffer, überlagert sich mit sich selbst.
     */
    const val MIN_SECONDS = 0.02f
    const val MAX_SECONDS = 0.8f

    /**
     * Lautstärke-Fenster. Über 0,6 fängt die Summe aus Ton und Rauschen
     * an, in [ChipSynth.mix] zu clippen; unter 0,05 hört im Spiel niemand
     * mehr, dass etwas passiert ist.
     */
    const val MIN_VOLUME = 0.05f
    const val MAX_VOLUME = 0.6f

    /**
     * Abklingrate pro Sekunde. 0 wäre ein Ton, der nur am Ende
     * abgeschnitten wird (das übernimmt der End-Fade in ChipSynth), 40
     * ist bereits ein reiner Klick.
     */
    const val MAX_DECAY = 40f

    /**
     * Wie weit zwei Sets bei demselben Ereignis mindestens auseinander
     * liegen müssen — als Frequenzverhältnis des ersten Tons, also eine
     * Quarte. Ein Set ist eine Sammlung, keine Nuance: Wer es
     * freischaltet, soll den Unterschied im ersten Treffer hören und
     * nicht erst im Vergleich zweier Aufnahmen.
     */
    const val MIN_PITCH_RATIO = 1.25f

    // ===== Die Ton-Sets =====

    /**
     * Der Bestand. Jeder Wert stammt aus ChipSynth.effects() vor der
     * Einführung der Ton-Sets und ist absichtlich unverändert: Wer die
     * Umstellung auf SoundBank hört, hat sie falsch gemacht.
     *
     * Charakter: NES-Blips, mittlere Lage, volle Pulsbreite, kurzes
     * perkussives Abklingen — hell und freundlich.
     */
    private val KLASSIK = SoundSet(
        mapOf(
            // Lauf-Start: dezenter, weicher Blip.
            SoundEvent.START to voice(tone(440f, 0.06f, 0.22f, 20f)),
            // Treffer: kurzer satter Blip; die Tonhöhe steuert hitRate().
            SoundEvent.HIT to voice(tone(660f, 0.07f, 0.38f, 18f)),
            // Perfekt: der klassische Münz-Sound — zwei Töne schnell aufwärts.
            SoundEvent.PERFECT to voice(
                tone(988f, 0.06f, 0.32f, 12f),
                tone(1319f, 0.16f, 0.38f, 9f)
            ),
            // Ketten-Zone: zwei flinke hohe Blips.
            SoundEvent.CHAIN to voice(
                tone(880f, 0.05f, 0.3f, 20f),
                tone(1175f, 0.07f, 0.3f, 18f)
            ),
            // Twist/Stufe freigeschaltet: kleine Fanfare aufwärts.
            SoundEvent.UNLOCK to voice(
                tone(523f, 0.07f, 0.3f, 14f),
                tone(659f, 0.07f, 0.3f, 14f),
                tone(784f, 0.07f, 0.3f, 14f),
                tone(1046f, 0.2f, 0.34f, 8f)
            ),
            // Neuer Rekord: längerer Jingle mit ausklingendem Schlusston.
            SoundEvent.RECORD to voice(
                tone(784f, 0.09f, 0.32f, 10f),
                tone(1046f, 0.09f, 0.32f, 10f),
                tone(1319f, 0.09f, 0.32f, 10f),
                tone(1568f, 0.3f, 0.36f, 6f)
            ),
            // Tod: fallender Sweep plus Rausch-Burst — der Rage-Moment.
            SoundEvent.DEATH to voice(
                glide(700f, 90f, 0.35f, 0.42f, 4f),
                noise = Noise(0.12f, 0.32f, 22f)
            ),
            // Dumpfer Aufschlag, wenn das Ergebnis feststeht.
            SoundEvent.THUD to voice(tone(100f, 0.09f, 0.5f, 14f))
        )
    )

    /**
     * Glocke: weich und rund. Alles liegt eine Oktave höher als im
     * Bestand, jeder Ton ist ein Dreieck ([Wave.DREIECK]) und klingt
     * lange nach (Abklingrate 2 bis 5 statt 8 bis 20) — statt zu tickern,
     * singt das Set.
     *
     * Die Form trägt hier mehr als die Tonhöhe. Ein Rechteck in dieser
     * Lage sticht: Es hat auch die geraden Oberwellen, und die fallen
     * nur linear ab. Das Dreieck lässt die geraden weg und dämpft den
     * Rest quadratisch — erst dadurch wird aus „hoher Blip" ein Ton, der
     * nach Spieluhr klingt statt nach Wecker.
     *
     * Kein einziges Ereignis trägt Rauschen, auch der Tod nicht: Hier
     * zerbricht nichts, hier geht das Licht aus. Der Tod ist deshalb der
     * längste Einzelton des Sets und nicht der härteste.
     */
    private val GLOCKE = SoundSet(
        mapOf(
            SoundEvent.START to voice(dreieck(659f, 0.16f, 0.18f, 5f)),
            // Der Treffer darf hier ausklingen, statt zu klicken: Bei
            // schnellen Läufen überlappen sich zwei Glocken — genau das
            // macht den Charakter aus.
            SoundEvent.HIT to voice(dreieck(988f, 0.2f, 0.26f, 5f)),
            SoundEvent.PERFECT to voice(
                dreieck(1319f, 0.14f, 0.24f, 4f),
                dreieck(1976f, 0.3f, 0.26f, 3f)
            ),
            SoundEvent.CHAIN to voice(
                dreieck(1568f, 0.12f, 0.22f, 5f),
                dreieck(2093f, 0.18f, 0.22f, 4f)
            ),
            // Drei Töne statt vier: Eine Fanfare, die nachklingt, braucht
            // weniger Stufen, sonst verwischen sie ineinander.
            SoundEvent.UNLOCK to voice(
                dreieck(784f, 0.14f, 0.2f, 4f),
                dreieck(1047f, 0.14f, 0.2f, 4f),
                dreieck(1568f, 0.36f, 0.24f, 2.5f)
            ),
            SoundEvent.RECORD to voice(
                dreieck(1047f, 0.16f, 0.22f, 3f),
                dreieck(1319f, 0.16f, 0.22f, 3f),
                dreieck(2093f, 0.5f, 0.26f, 2f)
            ),
            // Ein langer Gleitton nach unten, ohne Rauschen: Das Set
            // nimmt dem Tod die Härte, nicht die Länge. Auch er ist ein
            // Dreieck — ein Rechteck-Gleitton wäre der eine harte Moment
            // in einem Set, das sonst keinen hat.
            SoundEvent.DEATH to voice(
                glide(932f, 294f, 0.55f, 0.28f, 2.5f, Wave.DREIECK)
            ),
            SoundEvent.THUD to voice(dreieck(220f, 0.26f, 0.3f, 5f))
        )
    )

    /**
     * Amboss: hart, tief und sparsam. Jeder Ton liegt unter 450 Hz, jede
     * Pulsbreite bei höchstens einem Viertel (dünn und nasal), jede
     * Abklingrate bei mindestens 6 — nichts hallt nach, alles schlägt
     * einmal auf und ist weg.
     *
     * Sparsam heißt hier wörtlich: Wo der Bestand vier Töne in die
     * Fanfare legt, legt der Amboss drei; Treffer und Tod bekommen
     * stattdessen einen Rauschanteil, der aus dem Ton einen Schlag macht.
     * Das Set ist das leiseste in der Wahrnehmung und das lauteste in der
     * Spitze — genau umgekehrt zur Glocke.
     */
    private val AMBOSS = SoundSet(
        mapOf(
            SoundEvent.START to voice(tone(110f, 0.05f, 0.3f, 30f, duty = 0.125f)),
            // Der kurze Rauschanteil ist der Anschlag: Ohne ihn bliebe
            // ein tiefer Blip, mit ihm wird daraus ein Hammerschlag.
            SoundEvent.HIT to voice(
                tone(220f, 0.05f, 0.4f, 34f, duty = 0.125f),
                noise = Noise(0.03f, 0.18f, 40f)
            ),
            SoundEvent.PERFECT to voice(
                tone(330f, 0.05f, 0.38f, 30f, duty = 0.25f),
                tone(440f, 0.1f, 0.42f, 22f, duty = 0.25f)
            ),
            SoundEvent.CHAIN to voice(
                tone(262f, 0.04f, 0.34f, 36f, duty = 0.125f),
                tone(392f, 0.05f, 0.34f, 32f, duty = 0.125f)
            ),
            SoundEvent.UNLOCK to voice(
                tone(147f, 0.06f, 0.36f, 22f, duty = 0.25f),
                tone(220f, 0.06f, 0.36f, 22f, duty = 0.25f),
                tone(294f, 0.16f, 0.4f, 12f, duty = 0.25f)
            ),
            SoundEvent.RECORD to voice(
                tone(196f, 0.07f, 0.38f, 18f, duty = 0.25f),
                tone(294f, 0.07f, 0.38f, 18f, duty = 0.25f),
                tone(392f, 0.26f, 0.42f, 9f, duty = 0.25f)
            ),
            // Kürzer als im Bestand und mit mehr Rauschen: Der Amboss
            // lässt den Tod nicht ausklingen, er schlägt ihn ab.
            SoundEvent.DEATH to voice(
                glide(300f, 60f, 0.3f, 0.44f, 6f),
                noise = Noise(0.18f, 0.4f, 12f)
            ),
            SoundEvent.THUD to voice(tone(70f, 0.12f, 0.55f, 10f, duty = 0.25f))
        )
    )

    /*
     * Die fünf Sets nach Glocke und Amboss. Damit sich acht Sets bei
     * jedem Ereignis um mindestens eine Quarte unterscheiden
     * ([MIN_PITCH_RATIO]), hat jedes eine feste Spur im Tonumfang:
     *
     *   TROMMEL < AMBOSS < ORGEL < PFEIFE < KLASSIK < GLOCKE < LASER < ROBOTER
     *
     * Beim Aufschlag (THUD) und in der Kette (CHAIN) weicht die Spur ab,
     * weil der Bestand dort zu dicht an Amboss oder Glocke liegt. Die
     * Tonhöhe allein trägt den Charakter aber nicht: Jedes Set hat
     * zusätzlich eine eigene Form (Rauschen, Länge, Gleitrichtung,
     * Pulsbreite), die SoundSetTest festhält.
     */

    /**
     * Der Fell-Anschlag der TROMMEL: kurz, mittellaut, schnell weg. Steht
     * vor der Tabelle, weil ein object von oben nach unten initialisiert:
     * weiter unten wäre er beim Aufbau der TROMMEL noch null.
     */
    private val fell = Noise(0.05f, 0.2f, 30f)

    /**
     * Trommel: ganz unten, jedes Ereignis mit Rauschen. Weiche Dreiecke
     * geben dem Schlag den Kessel, das Rauschen das Fell. Kurz und
     * trocken, damit schnelle Treffer wie ein Wirbel klingen.
     */
    private val TROMMEL = SoundSet(
        mapOf(
            SoundEvent.START to voice(dreieck(80f, 0.08f, 0.4f, 20f), noise = fell),
            SoundEvent.HIT to voice(dreieck(150f, 0.06f, 0.42f, 24f), noise = fell),
            SoundEvent.PERFECT to voice(
                dreieck(247f, 0.05f, 0.38f, 22f),
                dreieck(330f, 0.1f, 0.4f, 16f),
                noise = fell
            ),
            SoundEvent.CHAIN to voice(
                dreieck(196f, 0.04f, 0.36f, 26f),
                dreieck(262f, 0.06f, 0.36f, 22f),
                noise = fell
            ),
            // Der Wirbel: vier gleiche Schläge und ein tieferer Schluss.
            SoundEvent.UNLOCK to voice(
                dreieck(110f, 0.05f, 0.36f, 26f),
                dreieck(110f, 0.05f, 0.36f, 26f),
                dreieck(110f, 0.05f, 0.36f, 26f),
                dreieck(165f, 0.2f, 0.42f, 10f),
                noise = Noise(0.2f, 0.16f, 12f)
            ),
            SoundEvent.RECORD to voice(
                dreieck(147f, 0.06f, 0.36f, 22f),
                dreieck(147f, 0.06f, 0.36f, 22f),
                dreieck(196f, 0.06f, 0.36f, 22f),
                dreieck(220f, 0.3f, 0.42f, 8f),
                noise = Noise(0.3f, 0.18f, 10f)
            ),
            SoundEvent.DEATH to voice(
                glide(196f, 55f, 0.4f, 0.44f, 5f, Wave.DREIECK),
                noise = Noise(0.25f, 0.34f, 10f)
            ),
            SoundEvent.THUD to voice(dreieck(55f, 0.14f, 0.55f, 12f), noise = fell)
        )
    )

    /**
     * Orgel: tief-mittlere Lage, volle Pulsbreite und kaum Abklingen.
     * Die Töne stehen, statt zu verklingen. Das einzige Set, dessen
     * Klänge man hält und nicht anschlägt.
     */
    private val ORGEL = SoundSet(
        mapOf(
            SoundEvent.START to voice(tone(175f, 0.2f, 0.2f, 3f)),
            SoundEvent.HIT to voice(tone(294f, 0.14f, 0.24f, 4f)),
            SoundEvent.PERFECT to voice(
                tone(440f, 0.1f, 0.22f, 3f),
                tone(587f, 0.24f, 0.24f, 2f)
            ),
            SoundEvent.CHAIN to voice(
                tone(349f, 0.1f, 0.22f, 3f),
                tone(440f, 0.14f, 0.22f, 3f)
            ),
            SoundEvent.UNLOCK to voice(
                tone(196f, 0.14f, 0.2f, 2f),
                tone(247f, 0.14f, 0.2f, 2f),
                tone(294f, 0.14f, 0.2f, 2f),
                tone(392f, 0.5f, 0.24f, 1.5f)
            ),
            SoundEvent.RECORD to voice(
                tone(262f, 0.16f, 0.2f, 2f),
                tone(330f, 0.16f, 0.2f, 2f),
                tone(392f, 0.16f, 0.2f, 2f),
                tone(523f, 0.6f, 0.24f, 1f)
            ),
            SoundEvent.DEATH to voice(glide(392f, 98f, 0.7f, 0.26f, 1.5f)),
            SoundEvent.THUD to voice(tone(131f, 0.3f, 0.28f, 4f))
        )
    )

    /**
     * Pfeife: Jeder Ton gleitet nach oben wie eine Lotusflöte, weich als
     * Dreieck. Nur der Tod gleitet lang nach unten: die Pfeife, der die
     * Luft ausgeht.
     */
    private val PFEIFE = SoundSet(
        mapOf(
            SoundEvent.START to voice(glide(262f, 392f, 0.12f, 0.26f, 6f, Wave.DREIECK)),
            SoundEvent.HIT to voice(glide(392f, 587f, 0.09f, 0.3f, 8f, Wave.DREIECK)),
            SoundEvent.PERFECT to voice(
                glide(587f, 784f, 0.07f, 0.28f, 6f, Wave.DREIECK),
                glide(784f, 1175f, 0.14f, 0.3f, 5f, Wave.DREIECK)
            ),
            SoundEvent.CHAIN to voice(
                glide(523f, 659f, 0.06f, 0.26f, 8f, Wave.DREIECK),
                glide(659f, 880f, 0.08f, 0.26f, 7f, Wave.DREIECK)
            ),
            SoundEvent.UNLOCK to voice(
                glide(262f, 523f, 0.14f, 0.26f, 4f, Wave.DREIECK),
                glide(523f, 1047f, 0.28f, 0.3f, 3f, Wave.DREIECK)
            ),
            SoundEvent.RECORD to voice(
                glide(392f, 523f, 0.1f, 0.26f, 5f, Wave.DREIECK),
                glide(523f, 784f, 0.1f, 0.26f, 5f, Wave.DREIECK),
                glide(784f, 1568f, 0.34f, 0.3f, 3f, Wave.DREIECK)
            ),
            SoundEvent.DEATH to voice(glide(523f, 131f, 0.6f, 0.3f, 2.5f, Wave.DREIECK)),
            SoundEvent.THUD to voice(glide(175f, 110f, 0.12f, 0.34f, 10f, Wave.DREIECK))
        )
    )

    /**
     * Laser: hoch und abwärts. Jeder Ton ist ein kurzer Gleitton von oben
     * nach unten, das „Piu“ aus Weltraum-Automaten. Der Tod bekommt als
     * einziges Ereignis Rauschen: die Explosion.
     */
    private val LASER = SoundSet(
        mapOf(
            SoundEvent.START to voice(glide(1047f, 523f, 0.08f, 0.2f, 14f)),
            SoundEvent.HIT to voice(glide(1319f, 440f, 0.07f, 0.24f, 16f)),
            SoundEvent.PERFECT to voice(
                glide(1760f, 880f, 0.06f, 0.22f, 14f),
                glide(2093f, 784f, 0.12f, 0.24f, 10f)
            ),
            SoundEvent.CHAIN to voice(
                glide(1175f, 587f, 0.05f, 0.22f, 18f),
                glide(1568f, 659f, 0.07f, 0.22f, 16f)
            ),
            SoundEvent.UNLOCK to voice(
                glide(1047f, 523f, 0.06f, 0.2f, 14f),
                glide(1319f, 659f, 0.06f, 0.2f, 14f),
                glide(1568f, 784f, 0.2f, 0.24f, 8f)
            ),
            SoundEvent.RECORD to voice(
                glide(1319f, 659f, 0.07f, 0.2f, 12f),
                glide(1568f, 784f, 0.07f, 0.2f, 12f),
                glide(2093f, 523f, 0.3f, 0.24f, 6f)
            ),
            SoundEvent.DEATH to voice(
                glide(1319f, 65f, 0.5f, 0.3f, 4f),
                noise = Noise(0.3f, 0.34f, 8f)
            ),
            SoundEvent.THUD to voice(glide(294f, 73f, 0.12f, 0.4f, 12f))
        )
    )

    /**
     * Roboter: ganz oben, dünn und im Stakkato. Achtel-Pulsbreite (der
     * nasalste Klang, den der Baukasten hat), sehr kurze Töne, schnelles
     * Abklingen und dafür mehr Töne pro Ereignis als jedes andere Set.
     * Leise gestimmt, weil hohe Blips sonst stechen.
     */
    private val ROBOTER = SoundSet(
        mapOf(
            SoundEvent.START to voice(
                tone(1319f, 0.03f, 0.16f, 30f, duty = 0.125f),
                tone(1760f, 0.03f, 0.16f, 30f, duty = 0.125f)
            ),
            SoundEvent.HIT to voice(tone(1760f, 0.035f, 0.18f, 32f, duty = 0.125f)),
            SoundEvent.PERFECT to voice(
                tone(2349f, 0.03f, 0.16f, 30f, duty = 0.125f),
                tone(1760f, 0.03f, 0.16f, 30f, duty = 0.125f),
                tone(2349f, 0.05f, 0.18f, 26f, duty = 0.125f)
            ),
            SoundEvent.CHAIN to voice(
                tone(1976f, 0.03f, 0.16f, 32f, duty = 0.125f),
                tone(1976f, 0.03f, 0.16f, 32f, duty = 0.125f)
            ),
            SoundEvent.UNLOCK to voice(
                tone(1319f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(1568f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(1760f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(2093f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(2349f, 0.08f, 0.18f, 20f, duty = 0.125f)
            ),
            SoundEvent.RECORD to voice(
                tone(1760f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(2093f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(1760f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(2093f, 0.03f, 0.16f, 28f, duty = 0.125f),
                tone(2349f, 0.12f, 0.18f, 16f, duty = 0.125f)
            ),
            // Abschalten: ein schneller Gleitton nach unten, wie ein
            // Roboter, dem der Strom ausgeht.
            SoundEvent.DEATH to voice(glide(1760f, 220f, 0.35f, 0.2f, 6f)),
            SoundEvent.THUD to voice(tone(392f, 0.04f, 0.24f, 30f, duty = 0.125f))
        )
    )

    /** Das komplette Set. */
    /** Alle Ton-Sets in Sammlungs-Reihenfolge (siehe [SkinPaint.ORDER]). */
    val ORDER: List<SoundSetId> = SoundSetId.entries.toList()

    /** Alle Klang-Ereignisse in fester Reihenfolge. */
    val EVENTS: List<SoundEvent> = SoundEvent.entries.toList()

    fun of(id: SoundSetId): SoundSet = when (id) {
        SoundSetId.KLASSIK -> KLASSIK
        SoundSetId.GLOCKE -> GLOCKE
        SoundSetId.AMBOSS -> AMBOSS
        SoundSetId.TROMMEL -> TROMMEL
        SoundSetId.ORGEL -> ORGEL
        SoundSetId.PFEIFE -> PFEIFE
        SoundSetId.LASER -> LASER
        SoundSetId.ROBOTER -> ROBOTER
    }

    /** Der Klang eines Ereignisses — der Weg, den alle Ports gehen. */
    fun voice(id: SoundSetId, event: SoundEvent): Voice = of(id).voices.getValue(event)

    /**
     * Drei Balkenhöhen (0..1) für die Vorschau-Kachel in der Auswahl:
     * Treffer, Perfekt und Rekord. Ein Ton-Set hat kein Bild — die Kachel
     * zeigt deshalb, was man gleich hört: wo das Set liegt, nicht wie es
     * aussieht. Die Glocke ergibt einen hohen, der Amboss einen flachen
     * Balkenzug.
     *
     * Gemessen wird in Oktaven und nicht in Hertz: Zwischen 200 und 400
     * liegt für das Ohr derselbe Schritt wie zwischen 1000 und 2000, eine
     * lineare Kachel hätte alle tiefen Sets flach an den Boden gedrückt.
     */
    fun chips(id: SoundSetId): List<Float> =
        listOf(SoundEvent.HIT, SoundEvent.PERFECT, SoundEvent.RECORD).map { event ->
            val hz = voice(id, event).tones.first().fromHz
            (log2(hz / MIN_HZ) / log2(MAX_HZ / MIN_HZ)).coerceIn(0f, 1f)
        }

    // ===== Freischaltung =====

    /**
     * Ton-Sets werden verdient wie Kulissen, aber an eigenen Achsen und
     * an Schwellen, auf denen sonst nichts liegt: Ein Set, das zusammen
     * mit einer Kulisse fällt, wird für die Kulisse gehalten.
     *
     * Die Glocke hängt am Können (eine Perfekt-Serie von 20 ist der
     * längste Serien-Wert im Spiel), der Amboss an der Ausdauer (25.000
     * Punkte insgesamt, das Zweieinhalbfache der MEER-Schwelle). Wer nur
     * eines von beidem betreibt, bekommt genau ein neues Set.
     *
     * Die fünf späteren Sets verteilen sich auf die übrigen Achsen:
     * Trommel an 150 Läufen, Orgel an einer Daily-Serie von 10, Pfeife an
     * Rekord 90, Laser an 30 Spieltagen, Roboter an 10.000 Punkten
     * insgesamt. Keine dieser Zahlen trägt schon einen Skin oder eine
     * Welt.
     */
    fun isUnlocked(id: SoundSetId, stats: SkinStats): Boolean = when (id) {
        SoundSetId.KLASSIK -> true
        SoundSetId.GLOCKE -> stats.bestPerfectStreak >= 20
        SoundSetId.AMBOSS -> stats.totalScore >= 25_000
        SoundSetId.TROMMEL -> stats.runCount >= 150
        SoundSetId.ORGEL -> stats.bestDailyStreak >= 10
        SoundSetId.PFEIFE -> stats.bestScore >= 90
        SoundSetId.LASER -> stats.daysPlayed >= 30
        SoundSetId.ROBOTER -> stats.totalScore >= 10_000
    }

    /** Wie viele Ton-Sets offen sind — reine Leistungsanzeige. */
    fun unlockedCount(stats: SkinStats): Int =
        SoundSetId.entries.count { isUnlocked(it, stats) }

    /** Ton-Set zu einem gespeicherten Namen, KLASSIK als Fallback. */
    fun fromName(name: String?): SoundSetId =
        SoundSetId.entries.firstOrNull { it.name == name } ?: SoundSetId.KLASSIK

    // ===== Tabellen-Werkzeug =====

    /** Eine Rechteckwelle fester Höhe. */
    private fun tone(
        hz: Float,
        seconds: Float,
        volume: Float,
        decay: Float,
        duty: Float = 0.5f
    ) = Tone(hz, hz, seconds, volume, decay, duty)

    /**
     * Ein Dreieck-Ton fester Höhe — dieselben vier Zahlen, nur die weiche
     * Form. Ohne Pulsbreite, weil ein Dreieck keine hat.
     */
    private fun dreieck(
        hz: Float,
        seconds: Float,
        volume: Float,
        decay: Float
    ) = Tone(hz, hz, seconds, volume, decay, 0.5f, Wave.DREIECK)

    /** Ein Gleitton; die Pulsbreite steht fest, weil `sweep` keine kennt. */
    private fun glide(
        fromHz: Float,
        toHz: Float,
        seconds: Float,
        volume: Float,
        decay: Float,
        wave: Wave = Wave.PULS
    ) = Tone(fromHz, toHz, seconds, volume, decay, 0.5f, wave)

    private fun voice(vararg tones: Tone, noise: Noise? = null) = Voice(tones.toList(), noise)
}
