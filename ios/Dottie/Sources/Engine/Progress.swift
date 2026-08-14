import Foundation

/// Port von core/.../Progress.kt: Welche Freischaltung als Nächstes fällt
/// und wie weit es noch ist.
///
/// Seit v2.20 laufen vier Ausdauer-Achsen mit (Läufe, Punkte insgesamt,
/// gespielte Tage, verschiedene Monate), und sichtbar war davon nichts:
/// `isUnlocked` beantwortet nur "offen oder nicht", nie "wie weit noch".
/// Genau diese Lücke schließt diese Datei — mit derselben Reihenfolge wie
/// Android und die PWA.
///
/// Zwei Regeln stecken in der Auswahl, nicht in der Anzeige: Gönner-Skins
/// tauchen nie auf (die kauft man, die erreicht man nicht), Saison-Skins
/// nur in ihrem Monat — "noch 5 Tage im Oktober" wäre im März gelogen.
enum GoalAxis {
    case bestScore
    case perfectStreak
    case dailyStreak
    case runCount
    case totalScore
    case daysPlayed
    case monthsPlayed

    /// Tage mit Lauf im laufenden Saison-Fenster (siehe `Season`).
    case seasonDays

    /// Gesammelte Skins — die Bedingung des REGENBOGEN.
    case skinCollection

    /// Gesammelte Kulissen — die Bedingung des WELTRAUM.
    case sceneCollection
}

/// Ein noch offenes Ziel: was es freischaltet, woran es hängt, wo man
/// steht und wo es fällt. Genau eines von `skin` und `scene` ist gesetzt.
struct Goal {
    let skin: DotSkin?
    let scene: SceneId?
    let axis: GoalAxis
    /// Aktueller Stand auf der Achse, nie größer als `target`.
    let current: Int
    let target: Int

    /// Was noch fehlt — die Zahl, die im Game-Over die Motivation trägt.
    var remaining: Int { return max(0, target - current) }

    /// Anteil 0..1 für den Balken.
    var fraction: CGFloat {
        guard target > 0 else { return 1 }
        return min(1, max(0, CGFloat(current) / CGFloat(target)))
    }

    /// Der Beschriftungs-Schlüssel der Belohnung.
    var titleKey: String { return skin?.titleKey ?? scene?.titleKey ?? "" }
}

enum Progress {

    /// Wie viele Ziele die Statistik-Seite zeigt. Drei sind genug: Die
    /// Liste soll den nächsten Schritt zeigen, nicht die ganze Sammlung
    /// ein zweites Mal — dafür gibt es das Skin-Menü.
    static let pageGoals = 3

    /// Aus wie vielen Blöcken der Fortschrittsbalken besteht. Er rastet
    /// auf ganze Blöcke ein — ein weicher Balken wäre der einzige
    /// stufenlose Verlauf im ganzen Spiel.
    static let barBlocks = 24

    /// Wie viele Blöcke bei diesem Anteil leuchten.
    static func filledBlocks(_ fraction: CGFloat) -> Int {
        return Int(floor(min(1, max(0, fraction)) * CGFloat(barBlocks)))
    }

    /// Die Schwellen, gespiegelt aus `DotSkin.isUnlocked` — in der
    /// Reihenfolge der Sammlung, damit die Liste bei Gleichstand
    /// vorhersagbar bleibt.
    private static let skinThresholds: [(DotSkin, GoalAxis, Int)] = [
        (.minze, .bestScore, 10),
        (.lava, .bestScore, 20),
        (.gold, .bestScore, 30),
        (.frost, .bestScore, 40),
        (.schatten, .perfectStreak, 4),
        (.prisma, .dailyStreak, 3),

        (.biene, .perfectStreak, 6),
        (.melone, .bestScore, 25),
        (.pilz, .bestScore, 35),
        (.koi, .dailyStreak, 7),
        (.galaxie, .bestScore, 50),
        (.karo, .perfectStreak, 10),
        (.ei, .runCount, 25),
        (.tiger, .runCount, 100),
        (.pinguin, .bestScore, 65),
        (.fussball, .runCount, 300),
        (.donut, .totalScore, 1_000),

        (.aurora, .dailyStreak, 14),
        (.magma, .bestScore, 60),
        (.neon, .perfectStreak, 12),
        (.chrom, .bestScore, 45),
        (.welle, .bestScore, 70),
        (.gewitter, .perfectStreak, 15),
        (.konfetti, .totalScore, 5_000),
        (.disco, .dailyStreak, 21),
        (.holo, .bestScore, 80),

        (.chamaeleon, .bestScore, 30),
        (.kombo, .perfectStreak, 8),
        (.tinte, .bestScore, 55),
        (.thermo, .bestScore, 75),
        (.medaille, .runCount, 200),
        (.tageszeit, .daysPlayed, 7),
        (.jahreszeit, .monthsPlayed, 3)
    ]

    /// Dieselbe Tabelle für die Kulissen (siehe `ScenePaint.isUnlocked`).
    private static let sceneThresholds: [(SceneId, GoalAxis, Int)] = [
        (.wueste, .runCount, 500),
        (.meer, .totalScore, 10_000),
        (.berg, .dailyStreak, 30),
        (.stadt, .bestScore, 85)
    ]

    /// Alle noch offenen Ziele, das nächstliegende zuerst. `month` ist der
    /// Kalendermonat 1-12 (0 = kein Kalender, dann keine Saison-Ziele),
    /// `seasonDays` der Tageszähler des laufenden Saison-Fensters.
    static func goals(_ stats: DotSkin.Stats, month: Int = 0, seasonDays: Int = 0) -> [Goal] {
        var open: [Goal] = []

        for (skin, axis, target) in skinThresholds where !skin.isUnlocked(stats) {
            open.append(Goal(
                skin: skin,
                scene: nil,
                axis: axis,
                // Ein Balken zeigt nie mehr als voll: Der Rohwert kann die
                // Schwelle nur überholen, wenn das Ziel längst offen ist.
                current: min(value(axis, stats, seasonDays: seasonDays), target),
                target: target
            ))
        }

        // Saison: nur im eigenen Monat, und nur solange das Bit fehlt.
        if let season = Season.forMonth(month), !season.skin.isUnlocked(stats) {
            open.append(Goal(
                skin: season.skin,
                scene: nil,
                axis: .seasonDays,
                current: min(max(0, seasonDays), season.requiredDays),
                target: season.requiredDays
            ))
        }

        // Der REGENBOGEN ist der Abschluss der Sammlung: Er zählt selbst
        // mit, also fehlt zum Ziel genau er — daher collectableCount - 1.
        if !DotSkin.regenbogen.isUnlocked(stats) {
            open.append(Goal(
                skin: .regenbogen,
                scene: nil,
                axis: .skinCollection,
                current: DotSkin.unlockedCount(stats),
                target: DotSkin.collectableCount() - 1
            ))
        }

        for (scene, axis, target) in sceneThresholds where !scene.isUnlocked(stats) {
            open.append(Goal(
                skin: nil,
                scene: scene,
                axis: axis,
                current: min(value(axis, stats, seasonDays: seasonDays), target),
                target: target
            ))
        }

        // Der WELTRAUM steht zu den Kulissen wie der REGENBOGEN zu den Skins.
        if !SceneId.weltraum.isUnlocked(stats) {
            open.append(Goal(
                skin: nil,
                scene: .weltraum,
                axis: .sceneCollection,
                current: ScenePaint.unlockedCount(stats),
                target: SceneId.allCases.count - 1
            ))
        }

        return open.sorted(by: nearestFirst)
    }

    /// Die vordersten `limit` Ziele — das Kurzformat für Seite und Game-Over.
    static func nextGoals(
        _ stats: DotSkin.Stats,
        month: Int = 0,
        seasonDays: Int = 0,
        limit: Int = pageGoals
    ) -> [Goal] {
        let list = goals(stats, month: month, seasonDays: seasonDays)
        return Array(list.prefix(max(0, limit)))
    }

    /// Das eine Ziel für die Zeile im Game-Over — nil, wenn alles offen ist.
    static func nextGoal(_ stats: DotSkin.Stats, month: Int = 0, seasonDays: Int = 0) -> Goal? {
        return goals(stats, month: month, seasonDays: seasonDays).first
    }

    /// Nähe zum Ziel zuerst. Der Anteil entscheidet und nicht der Restweg,
    /// weil "5 von 7 Tagen" näher dran ist als "4.800 von 5.000 Punkten".
    /// Bei Gleichstand erst der kleinere Rest, dann die Reihenfolge der
    /// Sammlung — sonst springt die Liste zwischen zwei Aufrufen.
    private static func nearestFirst(_ a: Goal, _ b: Goal) -> Bool {
        if a.fraction != b.fraction { return a.fraction > b.fraction }
        if a.remaining != b.remaining { return a.remaining < b.remaining }
        return order(a) < order(b)
    }

    private static func order(_ goal: Goal) -> Int {
        if let skin = goal.skin {
            return DotSkin.allCases.firstIndex(of: skin) ?? DotSkin.allCases.count
        }
        let scene = goal.scene ?? .wiese
        return DotSkin.allCases.count + (SceneId.allCases.firstIndex(of: scene) ?? 0)
    }

    /// Der aktuelle Stand auf einer Achse.
    private static func value(_ axis: GoalAxis, _ stats: DotSkin.Stats, seasonDays: Int) -> Int {
        switch axis {
        case .bestScore: return stats.bestScore
        case .perfectStreak: return stats.bestPerfectStreak
        case .dailyStreak: return stats.bestDailyStreak
        case .runCount: return stats.runCount
        case .totalScore: return stats.totalScore
        case .daysPlayed: return stats.daysPlayed
        case .monthsPlayed: return stats.monthsPlayed
        case .seasonDays: return seasonDays
        case .skinCollection: return DotSkin.unlockedCount(stats)
        case .sceneCollection: return ScenePaint.unlockedCount(stats)
        }
    }
}
