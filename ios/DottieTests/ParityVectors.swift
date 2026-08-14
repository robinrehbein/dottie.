import Foundation

/// Liest `parity/golden-vectors.txt` — die von `:core` erzeugten
/// Soll-Werte, gegen die sich dieser Port prüft. Siehe parity/README.md.
///
/// Das Format ist absichtlich primitiv (Schlüssel, dann Werte, durch
/// Leerzeichen getrennt, `#` ist ein Kommentar), damit Kotlin, Swift und
/// JavaScript es ohne Bibliothek lesen können.
struct ParityVectors {

    enum LoadError: Error, CustomStringConvertible {
        case missingFile
        case missingKey(String)

        var description: String {
            switch self {
            case .missingFile:
                return "golden-vectors.txt fehlt im Test-Bundle — "
                    + "liegt die Datei in project.yml unter sources?"
            case .missingKey(let key):
                return "Schlüssel fehlt in golden-vectors.txt: \(key)"
            }
        }
    }

    private let values: [String: [String]]

    /// Reihenfolge der Schlüssel, für Abschnitte mit fortlaufendem Index.
    let keys: [String]

    init() throws {
        guard let url = Bundle(for: BundleToken.self)
            .url(forResource: "golden-vectors", withExtension: "txt"),
            let text = try? String(contentsOf: url, encoding: .utf8) else {
            throw LoadError.missingFile
        }

        var values: [String: [String]] = [:]
        var keys: [String] = []
        for rawLine in text.split(separator: "\n", omittingEmptySubsequences: false) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.isEmpty || line.hasPrefix("#") { continue }
            let parts = line.split(separator: " ").map(String.init)
            guard let key = parts.first else { continue }
            values[key] = Array(parts.dropFirst())
            keys.append(key)
        }
        self.values = values
        self.keys = keys
    }

    func has(_ key: String) -> Bool { values[key] != nil }

    func strings(_ key: String) throws -> [String] {
        guard let value = values[key] else { throw LoadError.missingKey(key) }
        return value
    }

    func string(_ key: String) throws -> String {
        guard let first = try strings(key).first else {
            throw LoadError.missingKey(key)
        }
        return first
    }

    func int(_ key: String) throws -> Int {
        return Int(try string(key)) ?? 0
    }

    func int64(_ key: String) throws -> Int64 {
        return Int64(try string(key)) ?? 0
    }

    func float(_ key: String) throws -> Float {
        return Float(try string(key)) ?? 0
    }

    /// ARGB-Farbe im Format `0xAARRGGBB`.
    static func color(_ token: String) -> UInt32 {
        let hex = token.hasPrefix("0x") ? String(token.dropFirst(2)) : token
        return UInt32(hex, radix: 16) ?? 0
    }
}

/// Nur da, um das Test-Bundle zu finden (Bundle(for:) braucht eine Klasse).
private final class BundleToken {}
