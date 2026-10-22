import UIKit
import PencilKit
import PDFKit
import ZIPFoundation

enum DocumentIO {
    static let maxBytes: UInt64 = 512 * 1024 * 1024
    static func extract(_ file: URL, to directory: URL) throws {
        let archive = try Archive(url: file, accessMode: .read)
        let fm = FileManager.default
        try fm.createDirectory(at: directory, withIntermediateDirectories: true)
        var total: UInt64 = 0
        var paths = Set<String>()
        for entry in archive {
            let parts = entry.path.split(separator: "/", omittingEmptySubsequences: false)
            guard !entry.path.hasPrefix("/"), !entry.path.contains("\\"), !entry.path.contains(":"), !parts.contains(".."), entry.type != .symlink,
                  paths.insert(entry.path).inserted else { throw FolioError.invalidDocument("небезопасный путь в архиве") }
            let (next,overflow)=total.addingReportingOverflow(UInt64(entry.uncompressedSize)); total=next
            guard !overflow, total <= maxBytes, paths.count < 10000 else { throw FolioError.tooLarge }
            let destination = directory.appendingPathComponent(entry.path)
            guard destination.standardizedFileURL.path.hasPrefix(directory.standardizedFileURL.path + "/") else { throw FolioError.invalidDocument("неверный путь") }
            try fm.createDirectory(at: destination.deletingLastPathComponent(), withIntermediateDirectories: true)
            let checksum = try archive.extract(entry, to: destination)
            guard checksum == entry.checksum else { throw FolioError.invalidDocument("ошибка контрольной суммы архива") }
        }
    }
    static func zip(_ directory: URL, title: String) throws -> URL {
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".folioipad")
        try FileManager.default.zipItem(at: directory, to: dest, shouldKeepParent: false)
        return dest
    }
    static func imported(_ file: URL, store: NotebookStore) throws -> Notebook {
        let fm = FileManager.default
        let temp = fm.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: temp, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: temp) }
        let ext = file.pathExtension.lowercased()
        if ext == "pdf" { return try importPDF(file, store: store) }
        if ["png", "jpg", "jpeg", "heic"].contains(ext) { return try importImage(file, store: store) }
        try extract(file, to: temp)
        let manifest = temp.appendingPathComponent("manifest.json")
        if fm.fileExists(atPath: manifest.path) {
            let data = try Data(contentsOf: manifest)
            guard data.count <= 8 * 1024 * 1024 else { throw FolioError.tooLarge }
            var note = try JSONDecoder().decode(Notebook.self, from: data)
            guard note.version == 1, !note.pages.isEmpty, note.pages.count <= 2000 else { throw FolioError.invalidDocument("неподдерживаемый формат") }
            var drawings: [UUID: PKDrawing] = [:]
            for page in note.pages {
                guard store.safeFile(page.drawingFile), !page.drawingFile.isEmpty,
                      page.paper.spacing.isFinite, (8...160).contains(page.paper.spacing),
                      [page.canvasWidth, page.canvasHeight, page.originX, page.originY, page.viewportX, page.viewportY, page.zoom].allSatisfy({ $0.isFinite && abs($0) <= 10_000_000 }),
                      page.zoom > 0, page.canvasWidth > 0, page.canvasHeight > 0,
                      page.paper.width.isFinite, page.paper.height.isFinite,
                      (100...20000).contains(page.paper.width), (100...20000).contains(page.paper.height) else { throw FolioError.invalidDocument("повреждённая страница") }
                drawings[page.id] = try PKDrawing(data: Data(contentsOf: temp.appendingPathComponent(page.drawingFile)))
                if let background = page.backgroundFile, !store.safeFile(background) { throw FolioError.invalidDocument("путь изображения") }
            }
            note.id = UUID(); note.folder = ""; note.trashed = nil; note.modified = Date()
            note.title = store.uniqueTitle(note.title, folder: "")
            note.current = min(max(note.current, 0), note.pages.count - 1)
            let dir = store.directory(note.id); try fm.createDirectory(at: dir, withIntermediateDirectories: true)
            do {
                for page in note.pages { if let name = page.backgroundFile { try fm.copyItem(at: temp.appendingPathComponent(name), to: dir.appendingPathComponent(name)) } }
                try store.persist(note, drawings: drawings)
            } catch { try? fm.removeItem(at: dir); throw error }
            return note
        }
        return try AndroidImport.read(temp, store: store)
    }
    static func importImage(_ file: URL, store: NotebookStore) throws -> Notebook {
        guard let image = UIImage(contentsOfFile: file.path), image.size.width > 0, image.size.height > 0 else { throw FolioError.invalidDocument("изображение") }
        var note = Notebook(title: store.uniqueTitle(file.deletingPathExtension().lastPathComponent, folder: ""))
        note.pages[0].paper.infinite = false; note.pages[0].paper.width = Double(image.size.width); note.pages[0].paper.height = Double(image.size.height)
        note.pages[0].originX = 0; note.pages[0].originY = 0; note.pages[0].viewportX = 0; note.pages[0].viewportY = 0
        note.pages[0].backgroundFile = "background.png"
        let dir = store.directory(note.id); try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        try image.pngData()?.write(to: dir.appendingPathComponent("background.png"))
        try store.persist(note, drawings: [note.pages[0].id: PKDrawing()]); return note
    }
    static func importPDF(_ file: URL, store: NotebookStore) throws -> Notebook {
        guard let pdf = PDFDocument(url: file), pdf.pageCount > 0, pdf.pageCount <= 500 else { throw FolioError.invalidDocument("PDF или слишком много страниц") }
        var note = Notebook(title: store.uniqueTitle(file.deletingPathExtension().lastPathComponent, folder: "")); note.pages = []
        let dir = store.directory(note.id); try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var drawings: [UUID: PKDrawing] = [:]
        do {
            for i in 0..<pdf.pageCount {
                try autoreleasepool {
                    guard let source = pdf.page(at: i) else { throw FolioError.missingPage }
                    let bounds = source.bounds(for: .mediaBox)
                    var page = CanvasPage(); page.paper.infinite = false; page.paper.pattern = .blank
                    page.paper.width = Double(bounds.width * 2); page.paper.height = Double(bounds.height * 2)
                    page.originX = 0; page.originY = 0; page.viewportX = 0; page.viewportY = 0
                    let scale = min(2, 2400 / max(bounds.width, bounds.height))
                    let size = CGSize(width: bounds.width * scale, height: bounds.height * scale)
                    let image = source.thumbnail(of: size, for: .mediaBox)
                    let name = "background-\(page.id).png"; page.backgroundFile = name
                    try image.pngData()?.write(to: dir.appendingPathComponent(name))
                    note.pages.append(page); drawings[page.id] = PKDrawing()
                }
            }
            try store.persist(note, drawings: drawings); return note
        } catch { try? FileManager.default.removeItem(at: dir); throw error }
    }
    static func image(page: CanvasPage, drawing: PKDrawing, background: UIImage?, rect: CGRect, scale: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = true
        return UIGraphicsImageRenderer(size: rect.size, format: format).image { renderer in
            let c = renderer.cgContext
            c.translateBy(x: -rect.minX, y: -rect.minY)
            page.paper.color.ui.setFill(); c.fill(rect)
            PaperView.paint(c, rect: rect, paper: page.paper, origin: CGPoint(x: page.originX, y: page.originY), unitScale: 1)
            if let background { background.draw(in: CGRect(x: 0, y: 0, width: page.paper.width, height: page.paper.height)) }
            drawing.image(from: rect, scale: scale).draw(in: rect)
        }
    }
    static func exportRect(_ page: CanvasPage, drawing: PKDrawing) -> CGRect {
        if !page.paper.infinite { return CGRect(x: 0, y: 0, width: page.paper.width, height: page.paper.height) }
        let fallback = CGRect(x: page.originX, y: page.originY, width: 1190, height: 1684)
        let bounds = drawing.bounds
        return bounds.isNull || bounds.isEmpty ? fallback : bounds.insetBy(dx: -64, dy: -64)
    }
    static func pdf(note: Notebook, drawings: [UUID: PKDrawing], directory: URL) throws -> URL {
        let dest = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".pdf")
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
        try renderer.writePDF(to: dest) { context in
            for page in note.pages {
                let drawing = drawings[page.id] ?? PKDrawing()
                let rect = exportRect(page, drawing: drawing)
                let factor = min(1, 2400 / max(rect.width, rect.height))
                let bounds = CGRect(origin: .zero, size: CGSize(width: rect.width * factor, height: rect.height * factor))
                context.beginPage(withBounds: bounds, pageInfo: [:])
                let background = page.backgroundFile.flatMap { UIImage(contentsOfFile: directory.appendingPathComponent($0).path) }
                image(page: page, drawing: drawing, background: background, rect: rect, scale: factor).draw(in: bounds)
            }
        }
        return dest
    }
}
