import UIKit

/// Haptisches Feedback für das Spiel — auf iOS über die Feedback-
/// Generatoren von UIKit statt der Android-Vibrations-Waveforms. Jeder
/// Effekt ist bewusst kurz gehalten, damit er das Spielgefühl unterstützt
/// statt zu nerven.
final class GameHaptics {

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private let notification = UINotificationFeedbackGenerator()

    init() {
        light.prepare()
        medium.prepare()
        heavy.prepare()
    }

    /// Kurzer, satter Blip bei einem Treffer in der Zone.
    func score() {
        light.impactOccurred()
        light.prepare()
    }

    /// Doppel-Tick für einen perfekten Treffer.
    func perfect() {
        medium.impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.06) { [weak self] in
            self?.medium.impactOccurred(intensity: 1.0)
            self?.medium.prepare()
        }
    }

    /// Harter Schlag beim Aufprall — der Rage-Moment.
    func death() {
        heavy.impactOccurred()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.11) { [weak self] in
            self?.heavy.impactOccurred(intensity: 0.7)
            self?.heavy.prepare()
        }
    }

    /// Dumpfer Thud, wenn der Punkt nach dem Aus am Boden aufschlägt.
    func thud() {
        medium.impactOccurred()
        medium.prepare()
    }

    /// Fanfare, wenn ein Twist oder eine neue Stufe freigeschaltet wird.
    func unlock() {
        notification.notificationOccurred(.success)
    }

    /// Feier-Muster für einen neuen Rekord.
    func newRecord() {
        notification.notificationOccurred(.success)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) { [weak self] in
            self?.heavy.impactOccurred()
            self?.heavy.prepare()
        }
    }
}
