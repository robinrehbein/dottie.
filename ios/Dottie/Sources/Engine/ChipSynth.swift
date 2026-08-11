import Foundation

/// Port von core/.../ChipSynth.kt: Chiptune-Soundeffekte im NES-Stil,
/// Rechteckwellen und Rauschen, zur Laufzeit erzeugt — keine Audio-Assets.
///
/// Alle Funktionen liefern Mono-Samples im Bereich [-1, 1] bei
/// `sampleRate` Hz. Anders als auf Android (SoundPool mit Abspielrate)
/// werden die Tonhöhen-Varianten hier direkt beim Rendern erzeugt:
/// Abspielrate r entspricht Frequenz * r und Dauer / r (und Decay * r) —
/// das Ergebnis klingt identisch, braucht aber kein Varispeed-Setup.
enum ChipSynth {

    static let sampleRate: Int = 22050

    /// Attack-Rampe (~1,5 ms) gegen Knackser am Ton-Anfang.
    private static let attackSamples: Int = 32

    /// Lineares Fade-Out (~3 ms) gegen Knackser am Ton-Ende.
    private static let fadeOutSamples: Int = 64

    /// Rechteckwelle mit fester Frequenz. decay = Abklingrate pro Sekunde.
    static func square(
        freqHz: Float,
        seconds: Float,
        volume: Float = 0.4,
        decay: Float = 14,
        duty: Float = 0.5
    ) -> [Float] {
        return render(seconds: seconds, volume: volume, decay: decay) { _ in
            (freqHz, duty)
        }
    }

    /// Rechteckwelle, deren Frequenz linear von fromHz nach toHz gleitet.
    static func sweep(
        fromHz: Float,
        toHz: Float,
        seconds: Float,
        volume: Float = 0.4,
        decay: Float = 5
    ) -> [Float] {
        return render(seconds: seconds, volume: volume, decay: decay) { progress in
            (fromHz + (toHz - fromHz) * progress, 0.5)
        }
    }

    /// Rausch-Burst (deterministischer Seed wie im Original: Random(42)).
    static func noise(seconds: Float, volume: Float = 0.3, decay: Float = 18) -> [Float] {
        let random = KotlinRandom(intSeed: 42)
        let n = Int(seconds * Float(sampleRate))
        var out = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let t = Float(i) / Float(sampleRate)
            out[i] = (random.nextFloat() * 2 - 1) * volume * envelope(index: i, total: n, t: t, decay: decay)
        }
        return out
    }

    /// Hängt mehrere Klänge nahtlos aneinander.
    static func concat(_ parts: [Float]...) -> [Float] {
        var out: [Float] = []
        out.reserveCapacity(parts.reduce(0) { $0 + $1.count })
        for part in parts {
            out.append(contentsOf: part)
        }
        return out
    }

    /// Mischt zwei Klänge übereinander (Summe, hart auf [-1, 1] begrenzt).
    static func mix(_ a: [Float], _ b: [Float]) -> [Float] {
        let n = max(a.count, b.count)
        var out = [Float](repeating: 0, count: n)
        for i in 0..<n {
            let sum = (i < a.count ? a[i] : 0) + (i < b.count ? b[i] : 0)
            out[i] = min(max(sum, -1), 1)
        }
        return out
    }

    /// Abspielrate für den Treffer-Blip: klettert innerhalb jeder 5er-Stufe
    /// eine Pentatonik hinauf (0, 2, 4, 7, 9 Halbtöne). pow wie Kotlins
    /// `Float.pow` in Double gerechnet und auf Float gerundet.
    static func hitRate(score: Int) -> Float {
        let pentatonic: [Int] = [0, 2, 4, 7, 9]
        let semitones = pentatonic[((score % 5) + 5) % 5]
        let exponent = Float(semitones) / 12
        return Float(pow(2.0, Double(exponent)))
    }

    /// Abspielrate für den Perfekt-Sound: Jede Serien-Stufe hebt den
    /// Münz-Sound um zwei Halbtöne.
    static func perfectRate(streak: Int) -> Float {
        let step = min(max(streak - 1, 0), 4) * 2
        let exponent = Float(step) / 12
        return Float(pow(2.0, Double(exponent)))
    }

    // MARK: - Fertige Effekte

    /// Lauf-Start: dezenter, weicher Blip.
    static func startSound() -> [Float] {
        return square(freqHz: 440, seconds: 0.06, volume: 0.22, decay: 20)
    }

    /// Treffer: kurzer satter Blip — `rate` verschiebt die Tonhöhe wie die
    /// SoundPool-Abspielrate auf Android.
    static func hitSound(rate: Float) -> [Float] {
        return square(freqHz: 660 * rate, seconds: 0.07 / rate, volume: 0.38, decay: 18 * rate)
    }

    /// Perfekt: der klassische Münz-Sound — zwei Töne schnell aufwärts.
    static func perfectSound(rate: Float) -> [Float] {
        return concat(
            square(freqHz: 988 * rate, seconds: 0.06 / rate, volume: 0.32, decay: 12 * rate),
            square(freqHz: 1319 * rate, seconds: 0.16 / rate, volume: 0.38, decay: 9 * rate)
        )
    }

    /// Ketten-Zone: zwei flinke hohe Blips.
    static func chainSound() -> [Float] {
        return concat(
            square(freqHz: 880, seconds: 0.05, volume: 0.3, decay: 20),
            square(freqHz: 1175, seconds: 0.07, volume: 0.3, decay: 18)
        )
    }

    /// Twist/Stufe freigeschaltet: kleine Fanfare aufwärts.
    static func unlockSound() -> [Float] {
        return concat(
            square(freqHz: 523, seconds: 0.07, volume: 0.3, decay: 14),
            square(freqHz: 659, seconds: 0.07, volume: 0.3, decay: 14),
            square(freqHz: 784, seconds: 0.07, volume: 0.3, decay: 14),
            square(freqHz: 1046, seconds: 0.2, volume: 0.34, decay: 8)
        )
    }

    /// Neuer Rekord: längerer Jingle mit ausklingendem Schlusston.
    static func recordSound() -> [Float] {
        return concat(
            square(freqHz: 784, seconds: 0.09, volume: 0.32, decay: 10),
            square(freqHz: 1046, seconds: 0.09, volume: 0.32, decay: 10),
            square(freqHz: 1319, seconds: 0.09, volume: 0.32, decay: 10),
            square(freqHz: 1568, seconds: 0.3, volume: 0.36, decay: 6)
        )
    }

    /// Tod: fallender Sweep plus Rausch-Burst — der Rage-Moment.
    static func deathSound() -> [Float] {
        return mix(
            sweep(fromHz: 700, toHz: 90, seconds: 0.35, volume: 0.42, decay: 4),
            noise(seconds: 0.12, volume: 0.32, decay: 22)
        )
    }

    /// Dumpfer Aufschlag, wenn das Ergebnis feststeht.
    static func thudSound() -> [Float] {
        return square(freqHz: 100, seconds: 0.09, volume: 0.5, decay: 14)
    }

    // MARK: - Rendering

    /// Rendert eine Rechteckwelle; `voice` liefert pro Fortschritt Frequenz
    /// und Duty-Cycle.
    private static func render(
        seconds: Float,
        volume: Float,
        decay: Float,
        voice: (Float) -> (Float, Float)
    ) -> [Float] {
        let n = Int(seconds * Float(sampleRate))
        var out = [Float](repeating: 0, count: n)
        var phase: Float = 0
        for i in 0..<n {
            let t = Float(i) / Float(sampleRate)
            let progress = n > 1 ? Float(i) / Float(n - 1) : 0
            let (freq, duty) = voice(progress)
            let wave: Float = phase < duty ? 1 : -1
            out[i] = wave * volume * envelope(index: i, total: n, t: t, decay: decay)
            phase += freq / Float(sampleRate)
            if phase >= 1 {
                phase -= 1
            }
        }
        return out
    }

    /// Attack-Rampe, exponentielles Abklingen und End-Fade in einem.
    /// exp wie Kotlin/JVM in Double gerechnet und auf Float gerundet.
    private static func envelope(index: Int, total: Int, t: Float, decay: Float) -> Float {
        let attack: Float = index < attackSamples
            ? Float(index) / Float(attackSamples)
            : 1
        let remaining = total - index
        let fadeOut: Float = remaining < fadeOutSamples
            ? Float(remaining) / Float(fadeOutSamples)
            : 1
        return attack * fadeOut * Float(exp(Double(-decay * t)))
    }
}
