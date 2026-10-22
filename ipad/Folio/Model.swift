import Foundation
import UIKit
import PencilKit

enum FolioTool: String, CaseIterable, Codable, Identifiable {
    case pen, fountain, pencil, brush, marker, eraser, lasso, laser, hand
    var id: String { rawValue }
    var title: String {
        switch self {
        case .pen: return "Шариковая"
        case .fountain: return "Перьевая"
        case .pencil: return "Карандаш"
        case .brush: return "Кисть"
        case .marker: return "Маркер"
        case .eraser: return "Ластик"
        case .lasso: return "Лассо"
        case .laser: return "Лазер"
        case .hand: return "Навигация"
        }
    }
    var symbol: String {
        switch self {
        case .pen: return "pencil.tip"
        case .fountain: return "pencil.tip.crop.circle"
        case .pencil: return "pencil"
        case .brush: return "paintbrush.pointed.fill"
        case .marker: return "highlighter"
        case .eraser: return "eraser.fill"
        case .lasso: return "lasso"
        case .laser: return "sparkle"
        case .hand: return "hand.draw"
        }
    }
    var hasColor: Bool { ![.eraser, .lasso, .hand].contains(self) }
    var initialWidth: Double {
        switch self {
        case .brush: return 12
        case .marker: return 22
        case .eraser: return 28
        default: return 3
        }
    }
    var inkType: PKInkingTool.InkType {
        switch self {
        case .pen: return .monoline
        case .pencil: return .pencil
        case .brush: return .watercolor
        case .marker: return .marker
        default: return .pen
        }
    }
}

struct InkColor: Codable, Equatable {
    var argb: UInt32
    init(_ argb: UInt32) { self.argb = argb }
    init(_ color: UIColor) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        argb = UInt32((a * 255).rounded()) << 24 | UInt32((r * 255).rounded()) << 16 |
            UInt32((g * 255).rounded()) << 8 | UInt32((b * 255).rounded())
    }
    var ui: UIColor {
        UIColor(red: CGFloat((argb >> 16) & 255) / 255,
                green: CGFloat((argb >> 8) & 255) / 255,
                blue: CGFloat(argb & 255) / 255, alpha: CGFloat(argb >> 24) / 255)
    }
}

enum PencilCommand: String, Codable, CaseIterable, Identifiable {
    case system, eraser, nextTool, palette, nextColor, undo, none
    var id: String { rawValue }
    var title: String {
        switch self {
        case .system: return "Как в настройках iPad"
        case .eraser: return "Ластик / предыдущий инструмент"
        case .nextTool: return "Следующий инструмент"
        case .palette: return "Круговое меню"
        case .nextColor: return "Следующий цвет"
        case .undo: return "Отменить действие"
        case .none: return "Без действия"
        }
    }
}

struct ToolStyle: Codable {
    var width: Double
    var colors: [InkColor] = [.init(0xff293449), .init(0xff8470c7), .init(0xffdc9c86)]
    var selected = 0
    var color: UIColor { colors[min(max(selected, 0), colors.count - 1)].ui }
}

struct FolioPreferences: Codable {
    var tools: [FolioTool] = [.pen, .fountain, .pencil, .brush, .marker, .eraser, .lasso, .laser, .hand]
    var styles: [String: ToolStyle] = [:]
    var doubleTap: PencilCommand = .system
    var squeeze: PencilCommand = .palette
    var fingerDrawing = false
    var strokeEraser = false
    var showHover = true
    var defaultPaper = PaperStyle()
    func style(for tool: FolioTool) -> ToolStyle { styles[tool.rawValue] ?? ToolStyle(width: tool.initialWidth) }
}

struct PaperStyle: Codable, Equatable {
    enum Pattern: String, Codable, CaseIterable, Identifiable {
        case blank, grid, lined, dots
        var id: String { rawValue }
        var title: String {
            switch self { case .blank: return "Чистый"; case .grid: return "Клетка"; case .lined: return "Линии"; case .dots: return "Точки" }
        }
    }
    var color = InkColor(0xfffffcf6)
    var lines = InkColor(0x253e4659)
    var pattern: Pattern = .grid
    var spacing: Double = 32
    var infinite = true
    var width: Double = 1190
    var height: Double = 1684
}

struct CanvasPage: Codable, Identifiable {
    var id = UUID()
    var paper = PaperStyle()
    var drawingFile = ""
    var backgroundFile: String?
    var originX: Double = 4096
    var originY: Double = 4096
    var canvasWidth: Double = 16384
    var canvasHeight: Double = 16384
    var viewportX: Double = 4096
    var viewportY: Double = 4096
    var zoom: Double = 1
    var bookmark = false
}

struct Notebook: Codable, Identifiable {
    var version = 1
    var id = UUID()
    var title: String
    var folder = ""
    var modified = Date()
    var trashed: Date?
    var current = 0
    var pages = [CanvasPage()]
}

enum FolioError: LocalizedError {
    case invalidDocument(String), tooLarge, missingPage
    var errorDescription: String? {
        switch self {
        case .invalidDocument(let message): return "Не удалось открыть тетрадь: \(message)"
        case .tooLarge: return "Этот файл слишком большой для безопасного импорта."
        case .missingPage: return "Не удалось прочитать страницу. Исходная тетрадь сохранена."
        }
    }
}

enum NotebookNames {
    static func unique(_ title: String, existing: [String], date: Date = Date()) -> String {
        var base = title.trimmingCharacters(in: .whitespacesAndNewlines)
        if base.isEmpty {
            let f = DateFormatter(); f.locale = Locale(identifier: "ru_RU"); f.dateFormat = "dd.MM.yyyy"
            base = f.string(from: date)
        }
        let names = Set(existing.map { $0.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "ru_RU")) })
        func exists(_ name: String) -> Bool { names.contains(name.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "ru_RU"))) }
        if !exists(base) { return base }
        var i = 1
        while exists("\(base) (\(i))") { i += 1 }
        return "\(base) (\(i))"
    }
}

/// Bounded CoW drawing snapshots; no PNGs or serialization on each Pencil sample.
struct DrawingHistory {
    private(set) var past: [PKDrawing] = []
    private(set) var future: [PKDrawing] = []
    mutating func record(_ previous: PKDrawing) {
        past.append(previous); if past.count > 40 { past.removeFirst() }; future.removeAll()
    }
    mutating func undo(_ current: PKDrawing) -> PKDrawing? {
        guard let result = past.popLast() else { return nil }; future.append(current); return result
    }
    mutating func redo(_ current: PKDrawing) -> PKDrawing? {
        guard let result = future.popLast() else { return nil }; past.append(current); return result
    }
    mutating func translate(_ transform: CGAffineTransform) {
        past = past.map { $0.transformed(using: transform) }
        future = future.map { $0.transformed(using: transform) }
    }
}
