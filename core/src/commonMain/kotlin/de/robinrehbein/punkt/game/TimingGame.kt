package de.robinrehbein.punkt.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/** Der Zustand, in dem ein Lauf gerade steckt. */
enum class GamePhase { READY, RUNNING, DYING, OVER }

/** Die Erschwernisse, die sich mit steigendem Score freischalten. */
enum class Twist { PULSE, DRIFT, GHOST, FAKE, CHAIN }

/**
 * Was in einem Frame passiert ist.
 *
 * Die Fälle stehen auf oberster Ebene statt in [TimingGame]
 * verschachtelt, und das ist Absicht: Kotlin/Native flacht
 * verschachtelte Klassen beim Export nach Objective-C ab. Der Name, den
 * Swift dann sieht, steht nirgends im Kotlin-Code — er entsteht erst im
 * erzeugten Header. Auf oberster Ebene heißt jeder Fall in beiden
 * Sprachen gleich.
 */
sealed interface GameEvent

data object GameEventStarted : GameEvent

data object GameEventHit : GameEvent

data object GameEventPerfectHit : GameEvent

data object GameEventChainNext : GameEvent

data class GameEventTwistUnlocked(val twist: Twist) : GameEvent

data object GameEventDied : GameEvent

data object GameEventSettled : GameEvent

/**
 * Ein Tap in READY, der noch nicht zählt: Der Punkt steht nicht im Grün.
 *
 * Der Tap kostet nichts. Die Phase bleibt READY, Zone und Zufallsquelle
 * bleiben unberührt, der Punkt kreist weiter. Telefon, Uhr und iOS
 * antworten darauf mit Wackeln, Haptik-Tick und „NOCH NICHT“.
 */
data object GameEventNotYet : GameEvent

/**
 * Warum der letzte Lauf geendet hat.
 *
 * Aus dem Zustand beim [GameEventDied] allein lässt sich das nicht sicher
 * ablesen: Das Event kommt erst im nächsten `update()`, und `die()` setzt
 * `elapsed` zurück. Deshalb hält die Engine die Ursache im Moment des
 * Todes fest ([TimingGame.lastDeathCause]).
 *
 * - [EARLY]: Tap vor der Zone („ZU FRÜH“).
 * - [LATE]: Tap hinter der Zone, jenseits der Spät-Gnade („ZU SPÄT“).
 * - [MISSED]: Zone ohne Tap überfahren („VERPASST“).
 * - [TRAP]: Tap in die Fallen-Zone („BOOM!“). Geht vor EARLY und LATE.
 * - [NONE]: Kein Tod seit dem letzten Start.
 *
 * Auf oberster Ebene aus demselben Grund wie die Events (ObjC-Export).
 */
enum class DeathCause { NONE, EARLY, LATE, MISSED, TRAP }

/**
 * Pure-Kotlin Engine für das "Stopp"-Spielprinzip: Timing-Präzision.
 *
 * Der Punkt läuft automatisch auf einer Kreisbahn. Irgendwo auf der Bahn
 * liegt eine Zielzone — ein Tap, während der Punkt in der Zone ist,
 * zählt einen Treffer: Die Laufrichtung dreht um, die Zone springt an
 * eine neue Position, das Tempo steigt und die Zone schrumpft. Ein Tap
 * außerhalb der Zone oder ein Überfahren der Zone ohne Tap ist sofort
 * das Ende.
 *
 * Start: In READY kreist der Punkt langsam. Der erste Tap im Grün ist
 * schon Treffer 1 (normal oder perfekt) und startet den Lauf genau dort.
 * Ein Tap daneben kostet nichts ([GameEventNotYet]).
 *
 * Scoring: Ein normaler Treffer zählt +1. Perfekte Treffer (Zonenmitte)
 * bauen eine Serie auf: +2 für den ersten, +3, +4, +5 für jeden weiteren
 * in Folge (Deckel bei +5). Ein normaler Treffer beendet die Serie —
 * ohne Strafe, aber der Bonus beginnt von vorn.
 *
 * Die physische Schwierigkeit (Tempo, Zonenbreite) hängt an der Anzahl
 * der TREFFER, nicht am Score: Perfekte Treffer sind reiner Bonus und
 * beschleunigen das Spiel nicht doppelt. Twist-Freischaltungen und
 * Himmelsstufen bleiben dagegen Score-basiert — sie sind Belohnung.
 *
 * Damit es nicht langweilig wird, schalten sich mit steigendem Score
 * "Twists" frei, die pro Zone zufällig gemischt werden:
 * - PULSE (ab 5): Die Zone pulsiert, wächst und schrumpft rhythmisch.
 * - DRIFT (ab 10): Die Zone wandert langsam auf der Bahn.
 * - GHOST (ab 15): Eine Nebelbank direkt vor der Zone verdeckt den Punkt
 *   (siehe [TimingGame.isInFog]). Die Texte sagen NEBEL, der Name bleibt.
 * - FAKE (ab 20): Eine Köder-Zone auf dem Weg — Tap darin ist tödlich.
 * - CHAIN (ab 25): Zwei Zonen direkt nacheinander ohne Richtungswechsel.
 *
 * Winkel sind in Radiant, Geschwindigkeiten in Radiant pro Sekunde.
 */
class TimingGame(private var random: Random) {

    /**
     * Ohne eigene Zufallsquelle — der Normalfall im Spiel.
     *
     * Als zweiter Konstruktor statt als Standardwert am Parameter, weil
     * Kotlin/Native Standardwerte nicht exportiert: Swift sähe sonst nur
     * `init(random:)` und müsste sich erst einen `kotlin.random.Random`
     * beschaffen.
     */
    constructor() : this(Random.Default)

    var phase: GamePhase = GamePhase.READY
        private set

    /** Position des Punkts auf der Bahn. */
    var angle: Float = 0f
        private set

    /** Laufrichtung: +1 = im Uhrzeigersinn, -1 = dagegen. */
    var direction: Int = 1
        private set

    var zoneCenter: Float = READY_ZONE_CENTER
        private set

    /** Basisbreite der Zone (halb); die effektive Breite kann pulsieren. */
    var zoneHalfWidth: Float = BASE_ZONE_HALF
        private set

    var score: Int = 0
        private set

    /** Anzahl der Treffer — die Basis für Tempo und Zonenbreite. */
    var hits: Int = 0
        private set

    /** Aktuelle Serie perfekter Treffer in Folge. */
    var perfectStreak: Int = 0
        private set

    /** Punkte des letzten Treffers, für die Anzeige ("PERFEKT! +3"). */
    var lastHitPoints: Int = 0
        private set

    var elapsed: Float = 0f
        private set

    /** Zeit seit dem letzten Treffer, für Animationen. */
    var timeSinceHit: Float = 99f
        private set

    /** War der letzte Treffer ein perfekter? Für Anzeige-Effekte. */
    var lastHitPerfect: Boolean = false
        private set

    /** Die für die aktuelle Zone aktiven Twists. */
    val activeTwists = mutableSetOf<Twist>()

    /** Köder-Zone (nur relevant, wenn FAKE aktiv ist). */
    var hasFakeZone: Boolean = false
        private set
    var fakeZoneCenter: Float = 0f
        private set

    /**
     * Warum der letzte Lauf endete. Gesetzt im selben Moment wie der Tod
     * (im tödlichen Tap bzw. beim Überfahren), zurückgesetzt bei jedem
     * Start. Die Engine selbst liest den Wert nie.
     */
    var lastDeathCause: DeathCause = DeathCause.NONE
        private set

    /**
     * BLIND!-Bonus: Ein gültiger Treffer, solange der Punkt unter NEBEL
     * noch in der Nebelbank steckt, zählt +1 extra (normal +1, also +2)
     * und setzt die Perfekt-Serie nicht zurück. Standardmäßig an.
     *
     * Aus: Ein Treffer im Nebel ist ein ganz normaler Treffer (+1, Serie
     * vorbei) und [lastHitBlind] bleibt `false`.
     */
    var blindBonus: Boolean = true

    /**
     * War der letzte Treffer ein gewerteter Blindtreffer (BLIND! +1)? Für
     * den BLIND!-Pop. Nur `true`, wenn [blindBonus] an ist, NEBEL aktiv war
     * und der Punkt beim Tap in der Nebelbank stand.
     */
    var lastHitBlind: Boolean = false
        private set

    /**
     * Zeitpunkt (in `elapsed`), an dem der Puls beim Tod stehen blieb.
     * Ohne das Einfrieren atmete die Zone in DYING ab Phase 0 weiter, und
     * der Vogel stand im Freeze womöglich sichtbar im Grün, obwohl die
     * Anzeige ZU FRÜH sagt.
     */
    private var frozenPulseTime: Float = 0f

    /** [zoneAge] im Moment des Todes, für DYING und OVER. */
    private var zoneAgeAtDeath: Float = 0f

    /** Wie viele Ketten-Zonen nach der aktuellen noch folgen. */
    var chainRemaining: Int = 0
        private set

    /** Drift-Richtung relativ zur Laufrichtung: +1 = flieht, -1 = kommt entgegen. */
    private var driftSign: Int = 1

    /** Bereits angekündigte Twists (Banner nur einmal pro Lauf). */
    private val announcedTwists = mutableSetOf<Twist>()

    /**
     * Nur für Tests/Debug: erzwingt ein festes Twist-Set für alle
     * folgenden Zonen statt der zufälligen Auswahl.
     */
    var twistOverride: Set<Twist>? = null

    /**
     * Events aus tap() werden gepuffert und mit dem nächsten update()
     * ausgeliefert, damit der UI-Loop eine einzige Event-Quelle hat.
     */
    private val pendingEvents = mutableListOf<GameEvent>()

    /** Relative Position des Punkts zur Zone: negativ = davor, 0 = Mitte. */
    fun relativeToZone(): Float = wrapToPi(direction * (angle - zoneCenter))

    /** Effektive halbe Zonenbreite — pulsiert, wenn PULSE aktiv ist. */
    fun effectiveZoneHalf(): Float {
        if (Twist.PULSE !in activeTwists) return zoneHalfWidth
        // Nach dem Tod steht der Puls still: vor und nach dem tödlichen
        // Tap ist die Zone gleich breit (siehe frozenPulseTime).
        val pulseTime = if (phase == GamePhase.DYING || phase == GamePhase.OVER) {
            frozenPulseTime
        } else {
            elapsed
        }
        val pulse = PULSE_MIN_SHARE + (1f - PULSE_MIN_SHARE) *
            (0.5f + 0.5f * sin(pulseTime * PULSE_SPEED))
        return zoneHalfWidth * pulse
    }

    /**
     * Wie lange die aktuelle Zone schon steht, in Sekunden. Die Uhr für
     * Darstellungen, die mit der Zone beginnen (Lauflicht der Bomben,
     * Einrollen des Nebels) — ohne eigene Zufallszahl und ohne eigene Uhr.
     *
     * In READY und RUNNING `min(elapsed, timeSinceHit)`: seit dem Start
     * bzw. seit dem letzten Treffer, der die Zone neu gesetzt hat. In DYING
     * und OVER der Wert beim Tod, damit das Bild im Freeze stehen bleibt.
     */
    val zoneAge: Float
        get() = when (phase) {
            GamePhase.DYING, GamePhase.OVER -> zoneAgeAtDeath
            else -> minOf(elapsed, timeSinceHit)
        }

    /**
     * Anfang der Nebelbank, relativ zur Zone wie [relativeToZone]
     * (negativ = davor): [FOG_SECONDS] Laufzeit vor der Zonenkante.
     *
     * Gerechnet mit der Grundbreite [zoneHalfWidth], nicht mit der
     * pulsierenden — die Wolke soll nicht mitatmen.
     */
    fun fogStart(): Float = -zoneHalfWidth - currentSpeed() * FOG_SECONDS

    /**
     * Ende der Nebelbank, relativ zur Zone: im ersten Viertel der Zone
     * (`-h + FOG_END_SHARE·h`). Der PERFEKT-Kern liegt immer dahinter.
     */
    fun fogEnd(): Float = -zoneHalfWidth + zoneHalfWidth * FOG_END_SHARE

    /**
     * Steht der Punkt gerade in der Nebelbank? Reine Geometrie, gilt in
     * jeder Phase und unabhängig vom Twist. Ob der Nebel wirkt, entscheidet
     * [isDotVisible] (nur in RUNNING und nur unter NEBEL).
     */
    val isInFog: Boolean
        get() {
            val rel = relativeToZone()
            return rel >= fogStart() && rel <= fogEnd()
        }

    /** Steht der Punkt gerade in der (effektiven) Zielzone? */
    val isInZone: Boolean get() = abs(relativeToZone()) <= effectiveZoneHalf()

    /**
     * Halbe Breite des PERFEKT-Fensters — und zwar genau die, die die
     * Renderer als hellen Kern zeichnen.
     *
     * Die Aufrundung auf ein halbes Segment stammt aus der Zeichnung: Die
     * Bahn besteht aus [TRACK_SEGMENTS] Blöcken, ein Kern schmaler als ein
     * Block ließe sich nicht darstellen. Früher rundete nur das Bild auf,
     * gewertet wurde ohne — unter PULS war der leuchtende Kern dadurch bis
     * zu 61 % breiter als das Fenster, das er versprach. Wer sichtbar
     * mittig tippte, bekam trotzdem nur einen normalen Treffer.
     *
     * Jetzt gilt die Aufrundung für beide, und die Richtung ist Absicht:
     * Das Trefferfenster wächst auf das Bild, nicht das Bild schrumpft auf
     * das Fenster. Niemand wird dafür bestraft, dass er trifft, was er
     * sieht. Die Zone bleibt die Obergrenze — sonst wäre bei sehr schmaler
     * Zone plötzlich jeder Treffer perfekt.
     */
    fun perfectHalf(): Float {
        val half = effectiveZoneHalf()
        return minOf(half, maxOf(half * PERFECT_SHARE, SEGMENT_HALF))
    }

    /**
     * Halbe Breite der Fallen-Zone — dieselbe wie die der echten Zone,
     * Pulsieren eingeschlossen.
     *
     * Vorher zeichneten die Renderer die Falle mit der Grundbreite,
     * während die Zone atmete. Damit war die Falle während PULS fast immer
     * die breitere von beiden; wer das einmal bemerkte, musste FALLE +
     * PULS nie wieder raten. Eine Falle, die sich selbst verrät, ist keine.
     */
    fun fakeZoneHalf(): Float = effectiveZoneHalf()

    /**
     * Ist der Punkt gerade sichtbar? Unsichtbar nur in RUNNING unter NEBEL
     * ([Twist.GHOST]), solange er in der Nebelbank steckt. Die Sichtbarkeit
     * hängt damit an der Bahn statt an der Uhr, ohne Zufall. In READY,
     * DYING und OVER ist der Punkt immer zu sehen.
     */
    val isDotVisible: Boolean
        get() = phase != GamePhase.RUNNING || Twist.GHOST !in activeTwists || !isInFog

    fun currentSpeed(): Float =
        (BASE_SPEED + hits * SPEED_PER_HIT).coerceAtMost(MAX_SPEED)

    /**
     * Startet den Lauf aus READY, ohne den Tap zu werten: Die Zone springt
     * an eine neue Stelle, der Punkt läuft mit Spieltempo. Das ist das
     * bisherige Verhalten eines Taps in READY. [GameEventStarted] landet
     * wie bei [tap] im Puffer und kommt mit dem nächsten [update].
     *
     * Außerhalb von READY passiert nichts (Rückgabe `null`).
     */
    fun start(): GameEvent? {
        if (phase != GamePhase.READY) return null
        beginRun()
        pendingEvents.add(GameEventStarted)
        return GameEventStarted
    }

    private fun beginRun() {
        phase = GamePhase.RUNNING
        elapsed = 0f
        lastDeathCause = DeathCause.NONE
        lastHitBlind = false
        spawnZone()
    }

    /**
     * Verarbeitet einen Tap.
     *
     * - READY: Steht der Punkt im Grün, ist der Tap Treffer 1 und startet
     *   den Lauf genau dort. [GameEventStarted] steht dabei vor dem
     *   Treffer im Puffer, sonst setzte der Started-Handler der Apps die
     *   frische Perfekt-Serie gleich wieder zurück. Daneben kommt
     *   [GameEventNotYet]: keine Zufallszahl, Phase und Zone bleiben. Eine
     *   Spät-Gnade gibt es in READY nicht.
     * - RUNNING: der Stopp-Versuch.
     * - OVER: nach kurzer Sperre gegen Wut-Taps der Sofort-Neustart
     *   (direkt nach RUNNING, wie bisher).
     */
    fun tap(): GameEvent? {
        val event: GameEvent? = when (phase) {
            GamePhase.READY -> {
                val rel = relativeToZone()
                if (abs(rel) <= effectiveZoneHalf()) {
                    val perfect = abs(rel) <= perfectHalf()
                    phase = GamePhase.RUNNING
                    elapsed = 0f
                    lastDeathCause = DeathCause.NONE
                    lastHitBlind = false
                    pendingEvents.add(GameEventStarted)
                    registerHit(perfect, blind = false)
                    if (perfect) GameEventPerfectHit else GameEventHit
                } else {
                    GameEventNotYet
                }
            }
            GamePhase.RUNNING -> {
                val rel = relativeToZone()
                val half = effectiveZoneHalf()
                if (abs(rel) <= half) {
                    val perfect = abs(rel) <= perfectHalf()
                    // BLIND: vor registerHit festhalten, der Treffer setzt
                    // die Zone neu. Der Perfekt-Kern liegt nie im Nebel.
                    val blind = blindBonus && Twist.GHOST in activeTwists && isInFog
                    registerHit(perfect, blind)
                    if (perfect) GameEventPerfectHit else GameEventHit
                } else if (rel > half && rel <= half + currentSpeed() * LATE_TAP_FORGIVENESS_SECONDS) {
                    // Touch-Latenz-Gnade: Auf der Auslauf-Seite zählt ein
                    // minimal verspäteter Tap noch als normaler Treffer —
                    // Android braucht ~60-90ms, bis ein Tap ankommt, und in
                    // der Zeit ist der Punkt sonst längst aus der Zone.
                    // Wer zu früh tappt, war dagegen wirklich zu früh.
                    registerHit(perfect = false, blind = false)
                    GameEventHit
                } else {
                    // Auch ein Tap in der Fallen-Zone landet hier: Sie ist
                    // mechanisch einfach "daneben" — ihre Gefahr ist optisch.
                    // Die Ursache wird vor die() festgehalten, solange
                    // elapsed und damit der Puls noch stimmen.
                    lastDeathCause = when {
                        isInFakeZone() -> DeathCause.TRAP
                        rel < 0f -> DeathCause.EARLY
                        else -> DeathCause.LATE
                    }
                    die()
                    GameEventDied
                }
            }
            GamePhase.DYING -> null
            GamePhase.OVER -> {
                if (elapsed >= RESTART_LOCK_SECONDS) {
                    // Sofort-Neustart: aus der Wut direkt in den nächsten Lauf.
                    reset()
                    beginRun()
                    GameEventStarted
                } else {
                    null
                }
            }
        }
        if (event != null) pendingEvents.add(event)
        return event
    }

    /**
     * Ersetzt die Zufallsquelle — vor jedem Daily-Lauf mit dem Tages-Seed
     * aufgerufen, damit jeder Versuch des Tages dieselbe Zonen- und
     * Twist-Abfolge bekommt. `null` stellt echten Zufall wieder her.
     */
    fun reseed(seed: Long) {
        random = Random(seed)
    }

    /**
     * Stellt echten Zufall wieder her — der Weg zurück aus der Daily in
     * den freien Lauf.
     *
     * Früher war das `reseed(null)`. Ein nullbares `Long` wird beim
     * Export nach Objective-C zu einem eingepackten `KotlinLong?`, und
     * der Aufrufer müsste die Zahl erst verpacken, um sie gleich wieder
     * auspacken zu lassen. Zwei Namen sind hier billiger als eine Box.
     */
    fun reseedSystem() {
        random = Random.Default
    }

    /** Setzt alles auf den READY-Zustand zurück (Rekord bleibt beim Store). */
    fun reset() {
        phase = GamePhase.READY
        angle = 0f
        direction = 1
        zoneCenter = READY_ZONE_CENTER
        zoneHalfWidth = BASE_ZONE_HALF
        score = 0
        hits = 0
        perfectStreak = 0
        lastHitPoints = 0
        elapsed = 0f
        timeSinceHit = 99f
        lastHitPerfect = false
        activeTwists.clear()
        hasFakeZone = false
        chainRemaining = 0
        announcedTwists.clear()
        pendingEvents.clear()
        lastDeathCause = DeathCause.NONE
        lastHitBlind = false
        frozenPulseTime = 0f
        zoneAgeAtDeath = 0f
    }

    /**
     * Schreibt einen Frame fort und liefert die dabei aufgetretenen Events.
     */
    fun update(deltaSeconds: Float): List<GameEvent> {
        val dt = deltaSeconds.coerceIn(0f, MAX_DELTA)
        elapsed += dt
        timeSinceHit += dt
        val events = mutableListOf<GameEvent>()
        events.addAll(pendingEvents)
        pendingEvents.clear()

        when (phase) {
            GamePhase.READY -> {
                angle = wrapTwoPi(angle + direction * READY_SPEED * dt)
            }
            GamePhase.RUNNING -> {
                angle = wrapTwoPi(angle + direction * currentSpeed() * dt)
                if (Twist.DRIFT in activeTwists) {
                    zoneCenter = wrapTwoPi(
                        zoneCenter + direction * driftSign * DRIFT_SPEED * dt
                    )
                }
                // Zone ohne Tap überfahren → vorbei. Geprüft wird gegen die
                // volle Basisbreite, damit eine pulsierende Zone fair bleibt.
                // Der Puffer ist zeitbasiert (und größer als die Tap-Gnade),
                // damit sich das Überfahren auf jeder Tempo-Stufe gleich
                // anfühlt und späte Taps nicht vom Tod überholt werden.
                if (relativeToZone() > zoneHalfWidth + currentSpeed() * PASS_BUFFER_SECONDS) {
                    lastDeathCause = DeathCause.MISSED
                    die()
                    events.add(GameEventDied)
                }
            }
            GamePhase.DYING -> {
                // Kurzer Freeze für Flash und Shake, dann der Mario-Hüpfer:
                // Das Game-Over-Overlay erscheint erst, wenn der Vogel aus
                // dem Bild gefallen ist (Settled = "aufgeschlagen").
                if (elapsed >= DEATH_FREEZE_SECONDS + DEATH_FALL_SECONDS) {
                    phase = GamePhase.OVER
                    elapsed = 0f
                    events.add(GameEventSettled)
                }
            }
            GamePhase.OVER -> Unit
        }
        return events
    }

    /**
     * Wertet einen Treffer. [blind] ist ein gewerteter Blindtreffer
     * (BLIND! +1): +1 extra, die Perfekt-Serie bleibt stehen.
     */
    private fun registerHit(perfect: Boolean, blind: Boolean) {
        hits++
        lastHitBlind = blind
        if (perfect) {
            perfectStreak++
            lastHitPoints = (PERFECT_BASE_SCORE - 1 + perfectStreak)
                .coerceAtMost(PERFECT_MAX_SCORE)
        } else if (blind) {
            lastHitPoints = 1 + BLIND_BONUS_POINTS
        } else {
            perfectStreak = 0
            lastHitPoints = 1
        }
        score += lastHitPoints
        timeSinceHit = 0f
        lastHitPerfect = perfect
        zoneHalfWidth = (BASE_ZONE_HALF - hits * ZONE_SHRINK_PER_HIT)
            .coerceAtLeast(MIN_ZONE_HALF)

        if (chainRemaining > 0) {
            // Ketten-Zone: gleiche Richtung, die nächste kommt sofort.
            chainRemaining--
            hasFakeZone = false
            activeTwists.remove(Twist.FAKE)
            spawnChainZone()
            pendingEvents.add(GameEventChainNext)
        } else {
            direction = -direction
            spawnZone()
        }
    }

    private fun spawnZone() {
        // Der Mindestabstand ist zeitbasiert: Egal wie schnell der Punkt
        // schon kreist, bleiben immer mindestens MIN_REACTION_SECONDS bis
        // zur neuen Zone — sonst stirbt man an Physik statt an Skill.
        val minDistance = maxOf(MIN_ZONE_DISTANCE, currentSpeed() * MIN_REACTION_SECONDS)
        val maxDistance = maxOf(MAX_ZONE_DISTANCE, minDistance + 0.4f)
        val distance = minDistance + random.nextFloat() * (maxDistance - minDistance)
        zoneCenter = wrapTwoPi(angle + direction * distance)
        chooseTwists()

        driftSign = if (random.nextBoolean()) 1 else -1
        chainRemaining = if (Twist.CHAIN in activeTwists) CHAIN_LENGTH else 0

        hasFakeZone = false
        if (Twist.FAKE in activeTwists) {
            val maxFakeDistance = distance - zoneHalfWidth * 3f
            if (maxFakeDistance > FAKE_MIN_DISTANCE) {
                val fakeDistance = FAKE_MIN_DISTANCE +
                    random.nextFloat() * (maxFakeDistance - FAKE_MIN_DISTANCE)
                fakeZoneCenter = wrapTwoPi(angle + direction * fakeDistance)
                hasFakeZone = true
            }
        }
    }

    /** Folge-Zone einer Kette: näher dran, keine neue Twist-Auswahl. */
    private fun spawnChainZone() {
        val minDistance = maxOf(CHAIN_MIN_DISTANCE, currentSpeed() * MIN_REACTION_SECONDS)
        val maxDistance = maxOf(CHAIN_MAX_DISTANCE, minDistance + 0.3f)
        val distance = minDistance + random.nextFloat() * (maxDistance - minDistance)
        zoneCenter = wrapTwoPi(angle + direction * distance)
    }

    private fun chooseTwists() {
        activeTwists.clear()
        val override = twistOverride
        if (override != null) {
            activeTwists.addAll(override)
            return
        }

        val unlocked = Twist.entries.filter { score >= unlockScore(it) }

        // Ein frisch freigeschalteter Twist wird garantiert gezeigt
        // und einmalig angekündigt.
        val fresh = unlocked.firstOrNull { it !in announcedTwists }
        if (fresh != null) {
            activeTwists.add(fresh)
            announcedTwists.add(fresh)
            pendingEvents.add(GameEventTwistUnlocked(fresh))
        }

        val shuffled = unlocked.shuffled(random)
        for (twist in shuffled) {
            if (activeTwists.size >= MAX_ACTIVE_TWISTS) break
            if (twist in activeTwists) continue
            if (conflictsWithActive(twist)) continue
            if (random.nextFloat() < TWIST_PROBABILITY) {
                activeTwists.add(twist)
            }
        }
    }

    /**
     * Kuratierte Kombis: NEBEL (GHOST) + BOMBEN (FAKE) stapelt fehlende Information
     * (unsichtbarer Punkt) mit tödlicher Fehlinformation (Köder-Zone) —
     * Tode daraus fühlen sich nach Zufall an, nicht nach Skill. Alle
     * anderen Paare bleiben erlaubt, Härte ist sonst gewollt.
     */
    private fun conflictsWithActive(candidate: Twist): Boolean =
        FORBIDDEN_COMBOS.any { pair ->
            candidate in pair && activeTwists.any { it != candidate && it in pair }
        }

    /**
     * Steht der Punkt in der Fallen-Zone? Dieselbe Rechnung wie in den
     * Renderern, die die Falle zeichnen (Abstand zur Mitte ≤ [fakeZoneHalf]).
     */
    private fun isInFakeZone(): Boolean =
        hasFakeZone && abs(wrapToPi(angle - fakeZoneCenter)) <= fakeZoneHalf()

    private fun die() {
        if (phase != GamePhase.RUNNING) return
        // Puls und Zonen-Uhr einfrieren, bevor elapsed zurückgesetzt wird.
        frozenPulseTime = elapsed
        zoneAgeAtDeath = minOf(elapsed, timeSinceHit)
        phase = GamePhase.DYING
        elapsed = 0f
    }

    companion object {
        const val MAX_DELTA = 1f / 30f

        // Tempo (Radiant pro Sekunde)
        const val BASE_SPEED = 2.4f
        const val SPEED_PER_HIT = 0.07f
        const val MAX_SPEED = 5.2f
        const val READY_SPEED = 1.2f

        /** Wo die Zone in READY steht, vor dem ersten Start. */
        const val READY_ZONE_CENTER = 1.8f

        // Zielzone (Radiant)
        const val BASE_ZONE_HALF = 0.40f
        const val ZONE_SHRINK_PER_HIT = 0.005f
        const val MIN_ZONE_HALF = 0.15f
        const val PERFECT_SHARE = 0.35f

        /**
         * Aus wie vielen Blöcken die Bahn besteht. Stand bisher in jedem
         * Renderer einzeln; seit der PERFEKT-Kern auch gewertet wird, ist
         * die Zahl Spielregel und gehört hierher.
         */
        const val TRACK_SEGMENTS = 60

        /**
         * Halbe Winkelbreite eines Bahn-Blocks.
         *
         * `kotlin.math.PI` statt `Math.PI`: Letzteres ist `java.lang.Math`
         * und stünde in einem Multiplattform-Modul nicht zur Verfügung.
         * Der Rest der Datei rechnet ohnehin schon mit der Stdlib-Konstante
         * (siehe TWO_PI weiter unten) — derselbe Wert, nur ohne JVM darunter.
         */
        const val SEGMENT_HALF = (PI / TRACK_SEGMENTS).toFloat()
        const val MIN_ZONE_DISTANCE = 1.1f
        const val MAX_ZONE_DISTANCE = 2.8f

        // Fairness (Sekunden): Reaktionszeit bis zur Zone, Gnadenfenster
        // für Touch-Latenz und Puffer vor dem Überfahren-Tod. Der Puffer
        // muss größer als das Gnadenfenster sein, sonst überholt der Tod
        // einen noch gültigen späten Tap.
        const val MIN_REACTION_SECONDS = 0.45f
        const val LATE_TAP_FORGIVENESS_SECONDS = 0.07f
        const val PASS_BUFFER_SECONDS = 0.09f

        // Scoring: erster Perfekt +2, jeder weitere in Serie +1 mehr, Deckel +5.
        const val PERFECT_BASE_SCORE = 2
        const val PERFECT_MAX_SCORE = 5

        // Twists
        const val MAX_ACTIVE_TWISTS = 2

        /** Nie zusammen aktive Twist-Paare (siehe conflictsWithActive). */
        val FORBIDDEN_COMBOS = listOf(setOf(Twist.GHOST, Twist.FAKE))
        const val TWIST_PROBABILITY = 0.45f
        const val PULSE_SPEED = 5f
        const val PULSE_MIN_SHARE = 0.62f
        const val DRIFT_SPEED = 0.35f
        const val FAKE_MIN_DISTANCE = 0.55f

        /**
         * Nebel (Twist.GHOST): Die Bank beginnt so viele Sekunden Laufzeit vor
         * der Zonenkante ...
         */
        const val FOG_SECONDS = 0.12f

        /**
         * ... und reicht bis zu diesem Anteil der halben Zonenbreite in die
         * Zone hinein (0,5 = erstes Viertel der Zone). Verdeckt ist der
         * Punkt damit `FOG_SECONDS + FOG_END_SHARE·h/Tempo` Sekunden.
         */
        const val FOG_END_SHARE = 0.5f

        /** BLIND!: Extrapunkte für einen Treffer im Nebel (siehe [blindBonus]). */
        private const val BLIND_BONUS_POINTS = 1
        const val CHAIN_LENGTH = 1
        const val CHAIN_MIN_DISTANCE = 1.0f
        const val CHAIN_MAX_DISTANCE = 1.8f

        const val DEATH_FREEZE_SECONDS = 0.5f

        /**
         * Dauer der Fall-Animation nach dem Freeze (Mario-Hüpfer, siehe
         * drawTimingDot). Muss reichen, damit der Vogel auch von der
         * höchsten Bahnposition aus unten aus dem Bild ist (~0,9s), erst
         * danach erscheint das Game-Over-Overlay.
         */
        const val DEATH_FALL_SECONDS = 1.0f
        const val RESTART_LOCK_SECONDS = 0.55f

        /** Ab welchem Score ein Twist ins Spiel kommt. */
        fun unlockScore(twist: Twist): Int = when (twist) {
            Twist.PULSE -> 5
            Twist.DRIFT -> 10
            Twist.GHOST -> 15
            Twist.FAKE -> 20
            Twist.CHAIN -> 25
        }

        private const val TWO_PI = (2 * PI).toFloat()

        /** Normalisiert auf (-PI, PI]. */
        fun wrapToPi(value: Float): Float {
            var v = value % TWO_PI
            if (v <= -PI) v += TWO_PI
            if (v > PI) v -= TWO_PI
            return v
        }

        /** Normalisiert auf [0, 2*PI). */
        fun wrapTwoPi(value: Float): Float {
            var v = value % TWO_PI
            if (v < 0f) v += TWO_PI
            return v
        }
    }
}
