import XCTest
import DottieCore

/// Was von den Paritäts-Tests übrig ist.
///
/// Bis v2.23 rechnete hier ein Swift-Nachbau der Engine gegen
/// `parity/golden-vectors.txt` — 42 Skins, sechs Kulissen, der
/// XorWow-Generator, zwei komplette Läufe. Diese Tests sind ersatzlos
/// entfallen, und zwar aus dem besten Grund: Es gibt keinen zweiten
/// Nachbau mehr, der abweichen könnte. iOS rechnet mit demselben
/// `:core`, das auch die Android-App benutzt.
///
/// Übrig bleibt genau das, was `CoreBridge.swift` von Hand macht und was
/// deshalb still auseinanderlaufen kann:
///
/// 1. **Abgeleitete Textschlüssel.** Der Renderer bildet sie aus dem
///    Kotlin-Namen (`SkinId.LAVA` → `skin_lava`). Ein neuer Skin in
///    `:core` bringt seine Übersetzung nicht mit — ohne diesen Test fiele
///    das erst als leeres Label im Menü auf.
/// 2. **Die Familien-Liste.** Sie steht in `:core` als Zuordnung und hier
///    als Reihenfolge; jeder Skin muss in genau einer Familie landen.
/// 3. **Die Formen-Zuordnung.** `PropKind` ist ein Swift-`enum` neben
///    Kotlins `PropShape` — eine neue Form dort dürfte hier nicht still
///    als BAUM durchrutschen.
final class CoreBridgeTests: XCTestCase {

    /// Die englischen Texte, direkt aus der Ressource des Test-Bundles
    /// gelesen: `NSLocalizedString` würde im Test das Bundle des Runners
    /// befragen, nicht das der App.
    private lazy var strings: [String: String] = {
        guard let url = Bundle(for: CoreBridgeTests.self)
            .url(forResource: "Localizable", withExtension: "strings"),
            let table = NSDictionary(contentsOf: url) as? [String: String] else {
            return [:]
        }
        return table
    }()

    func testTextTabelleGeladen() {
        XCTAssertFalse(strings.isEmpty, "Localizable.strings nicht im Test-Bundle")
    }

    func testJederSkinHatNamenUndHinweis() {
        for skin in DotSkin.allCases {
            XCTAssertNotNil(strings[skin.titleKey], "kein Name für \(skin.name)")
            if let hint = skin.unlockHintKey {
                XCTAssertNotNil(strings[hint], "kein Hinweis für \(skin.name)")
            }
        }
        // KLASSIK ist von Anfang an offen und braucht deshalb als
        // einziger Skin keinen Hinweis.
        XCTAssertNil(DotSkin.klassik.unlockHintKey)
    }

    func testJedeKulisseUndJedesTonSetHatNamenUndHinweis() {
        for scene in SceneId.allCases {
            XCTAssertNotNil(strings[scene.titleKey], "kein Name für \(scene.name)")
            if let hint = scene.unlockHintKey {
                XCTAssertNotNil(strings[hint], "kein Hinweis für \(scene.name)")
            }
        }
        for sound in SoundSetId.allCases {
            XCTAssertNotNil(strings[sound.titleKey], "kein Name für \(sound.name)")
            if let hint = sound.unlockHintKey {
                XCTAssertNotNil(strings[hint], "kein Hinweis für \(sound.name)")
            }
        }
        XCTAssertNil(SceneId.wiese.unlockHintKey)
        XCTAssertNil(SoundSetId.klassik.unlockHintKey)
    }

    func testJedeMedailleUndJederTwistHatText() {
        for medal in MedalPaint.shared.ORDER {
            XCTAssertNotNil(strings[medal.nameKey], "kein Name für \(medal.name)")
        }
        for twist in [Twist.pulse, .drift, .ghost, .fake, .chain] {
            XCTAssertNotNil(strings[twist.bannerKey], "kein Banner für \(twist.name)")
        }
    }

    func testJedeFamilieHatEinenTitelUndJederSkinGenauEineFamilie() {
        var gesehen: [DotSkin] = []
        for family in SkinFamily.allCases {
            XCTAssertNotNil(strings[family.titleKey], "kein Titel für \(family.name)")
            gesehen += DotSkin.allCases.filter { $0.family == family }
        }
        XCTAssertEqual(
            gesehen.count, DotSkin.allCases.count,
            "Familien decken nicht alle Skins ab"
        )
        // Die Familien sind in Menü-Reihenfolge aufgezählt, und die ist
        // dieselbe wie die der Sammlung — sonst spränge das Menü.
        XCTAssertEqual(gesehen, DotSkin.allCases)
    }

    func testJedeRequisitenFormWirdErkannt() {
        // `PropKind` fällt für Unbekanntes auf BAUM zurück. Der Test
        // deckt jede Form ab, die tatsächlich in einer Kulisse steht:
        // Fällt eine davon fälschlich auf BAUM, ist die Zuordnung
        // unvollständig.
        for id in SceneId.allCases {
            for (index, prop) in ScenePaint.shared.props(id: id).enumerated() {
                let kind = ScenePaint.of(id).props[index].shape
                let istBaum = prop.shape == PropShape.baum
                XCTAssertEqual(
                    kind == .baum, istBaum,
                    "\(id.name)/\(index): \(prop.shape.name) landet als \(kind)"
                )
            }
        }
    }

    func testFarbenVerlierenNurDieDeckkraft() {
        // :core führt Farben als ARGB in einem Long, die Renderer als
        // 0xRRGGBB. Alles außer dem Alpha-Byte muss erhalten bleiben.
        XCTAssertEqual(coreRGB(0xFF4E_C0CA), 0x4E_C0CA)
        XCTAssertEqual(coreRGB(0xFF00_0000), 0x00_0000)
        XCTAssertEqual(DotSkin.klassik.body, 0xFFD847)
    }

    func testEngineLaeuftUeberDieBruecke() {
        // Kein Nachrechnen mehr, nur der Beweis, dass das Framework
        // wirklich verlinkt ist und ein Lauf startet.
        let game = TimingGame()
        XCTAssertEqual(game.phase, GamePhase.ready)
        game.reseed(seed: DailyChallenge.seedFor(epochDay: 20_000))
        XCTAssertTrue(game.tap() is GameEventStarted)
        XCTAssertEqual(game.phase, GamePhase.running)
    }
}
