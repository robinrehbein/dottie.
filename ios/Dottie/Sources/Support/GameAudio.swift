import AVFoundation
import Foundation

/// Chiptune-Soundeffekte für das Spiel — zur Laufzeit aus Rechteckwellen
/// synthetisiert (ChipSynth), keine Audio-Assets. Statt Android-SoundPool:
/// AVAudioEngine mit vorbereiteten AVAudioPCMBuffern, damit die Latenz
/// für ein Timing-Spiel niedrig genug ist. Die Tonhöhen-Varianten (Treffer-
/// Pentatonik, Perfekt-Serie) sind vorgerendert.
final class GameAudio {

    private let engine = AVAudioEngine()
    private var players: [AVAudioPlayerNode] = []
    private var nextPlayer: Int = 0
    private var buffers: [String: AVAudioPCMBuffer] = [:]

    /// Stumm geschaltet? Die Scene hält das mit dem ScoreStore synchron.
    var muted: Bool = false

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

        buffers["start"] = GameAudio.makeBuffer(ChipSynth.startSound(), format: format)
        buffers["chain"] = GameAudio.makeBuffer(ChipSynth.chainSound(), format: format)
        buffers["unlock"] = GameAudio.makeBuffer(ChipSynth.unlockSound(), format: format)
        buffers["record"] = GameAudio.makeBuffer(ChipSynth.recordSound(), format: format)
        buffers["death"] = GameAudio.makeBuffer(ChipSynth.deathSound(), format: format)
        buffers["thud"] = GameAudio.makeBuffer(ChipSynth.thudSound(), format: format)
        // Treffer-Blip: fünf Pentatonik-Stufen (score % 5).
        for step in 0..<5 {
            let rate = ChipSynth.hitRate(score: step)
            buffers["hit\(step)"] = GameAudio.makeBuffer(
                ChipSynth.hitSound(rate: rate), format: format
            )
        }
        // Münz-Sound: fünf Serien-Stufen (streak 1...5, Deckel bei 5).
        for streak in 1...5 {
            let rate = ChipSynth.perfectRate(streak: streak)
            buffers["perfect\(streak)"] = GameAudio.makeBuffer(
                ChipSynth.perfectSound(rate: rate), format: format
            )
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

    func release() {
        engine.stop()
    }

    private func play(_ name: String) {
        if muted {
            return
        }
        guard let buffer = buffers[name] else {
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
