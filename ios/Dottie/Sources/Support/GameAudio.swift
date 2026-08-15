import AVFoundation
import DottieCore
import Foundation

/// Chiptune-Soundeffekte für das Spiel — zur Laufzeit aus Rechteckwellen
/// synthetisiert (ChipSynth nach der Tabelle in SoundBank), keine
/// Audio-Assets. Statt Android-SoundPool:
/// AVAudioEngine mit vorbereiteten AVAudioPCMBuffern, damit die Latenz
/// für ein Timing-Spiel niedrig genug ist. Die Tonhöhen-Varianten (Treffer-
/// Pentatonik, Perfekt-Serie) sind vorgerendert.
final class GameAudio {

    private let engine = AVAudioEngine()
    private var players: [AVAudioPlayerNode] = []
    private var nextPlayer: Int = 0

    /// Ton-Set → Klangname → Puffer. Es liegen ALLE Sets bereit: Drei
    /// Sets sind zusammen kein Speicherproblem, und die Hörprobe in der
    /// Auswahl muss sofort kommen.
    private var buffers: [SoundSetId: [String: AVAudioPCMBuffer]] = [:]

    /// Stumm geschaltet? Die Scene hält das mit dem ScoreStore synchron.
    var muted: Bool = false

    /// Das gewählte Ton-Set; die Scene hält es mit dem ScoreStore synchron.
    var soundSet: SoundSetId = .klassik

    init() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.ambient, options: [.mixWithOthers])
        try? session.setActive(true)

        guard let format = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: Double(ChipSynth.sampleRate),
            channels: 1,
            interleaved: false
        ) else {
            return
        }

        for _ in 0..<4 {
            let player = AVAudioPlayerNode()
            engine.attach(player)
            engine.connect(player, to: engine.mainMixerNode, format: format)
            players.append(player)
        }

        for set in SoundSetId.allCases {
            var proSet: [String: AVAudioPCMBuffer] = [:]
            for event in SoundEvent.allCases where event != .hit && event != .perfect {
                proSet[event.rawValue] = GameAudio.makeBuffer(
                    ChipSynth.render(SoundBank.voice(set, event)), format: format
                )
            }
            // Treffer-Blip: fünf Pentatonik-Stufen (score % 5).
            for step in 0..<5 {
                let rate = ChipSynth.hitRate(score: step)
                proSet["hit\(step)"] = GameAudio.makeBuffer(
                    ChipSynth.render(SoundBank.voice(set, .hit), rate: rate), format: format
                )
            }
            // Münz-Sound: fünf Serien-Stufen (streak 1...5, Deckel bei 5).
            for streak in 1...5 {
                let rate = ChipSynth.perfectRate(streak: streak)
                proSet["perfect\(streak)"] = GameAudio.makeBuffer(
                    ChipSynth.render(SoundBank.voice(set, .perfect), rate: rate), format: format
                )
            }
            buffers[set] = proSet
        }

        engine.prepare()
    }

    func start() {
        play("start")
    }

    /// Treffer-Blip; die Tonhöhe klettert pro 5er-Stufe eine Pentatonik hoch.
    func hit(score: Int) {
        play("hit\(((score % 5) + 5) % 5)")
    }

    /// Münz-Sound; jede Serien-Stufe klingt zwei Halbtöne höher.
    func perfect(streak: Int) {
        play("perfect\(min(max(streak, 1), 5))")
    }

    func chain() { play("chain") }
    func unlock() { play("unlock") }
    func death() { play("death") }
    func thud() { play("thud") }
    func newRecord() { play("record") }

    /// Hörprobe für die Auswahl: die Fanfare des angetippten Sets, auch
    /// wenn es gerade nicht das gewählte ist. Ohne Probe wählt man einen
    /// Klang nach seinem Namen.
    func preview(_ set: SoundSetId) { play("unlock", set: set) }

    func release() {
        engine.stop()
    }

    private func play(_ name: String, set: SoundSetId? = nil) {
        if muted {
            return
        }
        guard let buffer = buffers[set ?? soundSet]?[name] else {
            return
        }
        if !engine.isRunning {
            do {
                try engine.start()
            } catch {
                return
            }
        }
        guard !players.isEmpty else {
            return
        }
        let player = players[nextPlayer]
        nextPlayer = (nextPlayer + 1) % players.count
        player.stop()
        player.scheduleBuffer(buffer, at: nil, options: [], completionHandler: nil)
        player.play()
    }

    private static func makeBuffer(_ samples: [Float], format: AVAudioFormat) -> AVAudioPCMBuffer? {
        let frameCount = AVAudioFrameCount(samples.count)
        guard frameCount > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount),
              let channel = buffer.floatChannelData else {
            return nil
        }
        buffer.frameLength = frameCount
        for i in 0..<samples.count {
            channel[0][i] = samples[i]
        }
        return buffer
    }
}
