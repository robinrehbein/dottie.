import Foundation

/// 1:1-Port von Kotlins `XorWowRandom` (kotlin.random.Random(seed)).
///
/// Die Daily Challenge verlangt, dass iOS aus demselben Tages-Seed exakt
/// dieselbe Zonen- und Twist-Abfolge erzeugt wie die Android-App. Kotlins
/// Default-Random ist ein XorWow-Generator mit 64 Warmup-Runden — der wird
/// hier Bit für Bit nachgebaut (Int32-Arithmetik mit Überlauf-Wrapping),
/// statt Swifts `SystemRandomNumberGenerator` zu verwenden.
final class KotlinRandom {

    private var x: Int32
    private var y: Int32
    private var z: Int32
    private var w: Int32
    private var v: Int32
    private var addend: Int32

    /// Entspricht `XorWowRandom(seed1, seed2)` in Kotlin.
    init(seed1: Int32, seed2: Int32) {
        self.x = seed1
        self.y = seed2
        self.z = 0
        self.w = 0
        self.v = ~seed1
        self.addend = (seed1 << 10) ^ Int32(bitPattern: UInt32(bitPattern: seed2) >> 4)
        // Kotlin verlangt mindestens ein Nicht-Null-Element; durch v = ~seed1
        // ist (x | v) immer != 0. Danach 64 Warmup-Runden wie im Original.
        for _ in 0..<64 {
            _ = nextInt()
        }
    }

    /// Entspricht `Random(seed: Long)` in Kotlin.
    convenience init(seed: Int64) {
        self.init(
            seed1: Int32(truncatingIfNeeded: seed),
            seed2: Int32(truncatingIfNeeded: seed >> 32)
        )
    }

    /// Entspricht `Random(seed: Int)` in Kotlin.
    convenience init(intSeed: Int32) {
        self.init(seed1: intSeed, seed2: intSeed >> 31)
    }

    /// Ersatz für `Random.Default`: echter Zufall, Sequenz-Identität egal.
    static func systemSeeded() -> KotlinRandom {
        var generator = SystemRandomNumberGenerator()
        let seed = Int64(bitPattern: generator.next())
        return KotlinRandom(seed: seed)
    }

    /// Kern des XorWow-Generators, exakt wie `XorWowRandom.nextInt()`.
    func nextInt() -> Int32 {
        var t = x
        t ^= Int32(bitPattern: UInt32(bitPattern: t) >> 2)
        x = y
        y = z
        z = w
        let v0 = v
        w = v0
        t = (t ^ (t << 1)) ^ v0 ^ (v0 << 4)
        v = t
        addend = addend &+ 362437
        return t &+ addend
    }

    /// Entspricht `nextInt().takeUpperBits(bitCount)` in Kotlin.
    func nextBits(_ bitCount: Int) -> Int32 {
        if bitCount <= 0 {
            return 0
        }
        let raw = UInt32(bitPattern: nextInt())
        let shifted: UInt32 = bitCount >= 32 ? raw : raw >> UInt32(32 - bitCount)
        return Int32(bitPattern: shifted)
    }

    /// Entspricht `nextFloat()`: obere 24 Bit auf [0, 1).
    func nextFloat() -> Float {
        return Float(nextBits(24)) / Float(1 << 24)
    }

    /// Entspricht `nextBoolean()`.
    func nextBoolean() -> Bool {
        return nextBits(1) != 0
    }

    /// Entspricht `nextInt(until)` für `until > 0` (Kotlin: from = 0).
    func nextInt(bound: Int32) -> Int32 {
        let n = bound
        if (n & (0 &- n)) == n {
            // Zweierpotenz: obere Bits reichen (fastLog2).
            let bitCount = 31 - Int(n.leadingZeroBitCount)
            return nextBits(bitCount)
        }
        var value: Int32 = 0
        while true {
            let bits = Int32(bitPattern: UInt32(bitPattern: nextInt()) >> 1)
            value = bits % n
            if bits &- value &+ (n &- 1) >= 0 {
                break
            }
        }
        return value
    }

    /// Fisher-Yates wie Kotlins `MutableList.shuffle(random)` —
    /// die Grundlage von `shuffled(random)` in TimingGame.chooseTwists().
    func shuffle<T>(_ array: inout [T]) {
        guard array.count > 1 else { return }
        var i = array.count - 1
        while i >= 1 {
            let j = Int(nextInt(bound: Int32(i + 1)))
            array.swapAt(i, j)
            i -= 1
        }
    }
}
