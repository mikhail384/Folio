import Foundation
import Combine
import PencilKit
import UIKit

final class NotebookStore: ObservableObject {
    @Published private(set) var notebooks: [Notebook] = []
    @Published var error: String?
    @Published var preferences: FolioPreferences
    let root: URL
    let io = DispatchQueue(label: "ru.folio.ipad.documents", qos: .userInitiated)
    private let fm = FileManager.default
    static let shared = NotebookStore()

    init(root: URL? = nil) {
        self.root = root ?? FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent("Notebooks", isDirectory: true)
        preferences = (UserDefaults.standard.data(forKey: "folio.preferences").flatMap { try? JSONDecoder().decode(FolioPreferences.self, from: $0) }) ?? FolioPreferences()
        do { try fm.createDirectory(at: self.root, withIntermediateDirectories: true); try reload() }
        catch { self.error = error.localizedDescription }
    }

    func directory(_ id: UUID) -> URL { root.appendingPathComponent(id.uuidString, isDirectory: true) }
    func reload() throws {
        let urls = try fm.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)
        notebooks = urls.compactMap { url in
            guard let data = try? Data(contentsOf: url.appendingPathComponent("manifest.json")) else { return nil }
            return try? JSONDecoder().decode(Notebook.self, from: data)
        }.sorted { $0.modified > $1.modified }
    }
    func savePreferences() {
        if let data = try? JSONEncoder().encode(preferences) { UserDefaults.standard.set(data, forKey: "folio.preferences") }
        objectWillChange.send()
    }
    func uniqueTitle(_ proposed: String, folder: String, excluding: UUID? = nil) -> String {
        NotebookNames.unique(proposed, existing: notebooks.filter { $0.id != excluding && $0.folder == folder && $0.trashed == nil }.map(\.title))
    }
    @discardableResult func create(title: String = "", folder: String = "") throws -> Notebook {
        var note = Notebook(title: uniqueTitle(title, folder: folder)); note.folder = folder
        note.pages[0].paper = preferences.defaultPaper
        if !note.pages[0].paper.infinite { note.pages[0].originX=0; note.pages[0].originY=0; note.pages[0].viewportX=0; note.pages[0].viewportY=0 }
        try persist(note, drawings: [note.pages[0].id: PKDrawing()])
        try reload(); return notebooks.first { $0.id == note.id } ?? note
    }
    func read(_ id: UUID) throws -> (Notebook, [UUID: PKDrawing]) {
        let dir = directory(id)
        let note = try JSONDecoder().decode(Notebook.self, from: Data(contentsOf: dir.appendingPathComponent("manifest.json")))
        guard note.version == 1, !note.pages.isEmpty, note.pages.count <= 2000 else { throw FolioError.invalidDocument("неподдерживаемая версия") }
        var drawings: [UUID: PKDrawing] = [:]
        for page in note.pages {
            guard safeFile(page.drawingFile), !page.drawingFile.isEmpty else { throw FolioError.missingPage }
            drawings[page.id] = try PKDrawing(data: Data(contentsOf: dir.appendingPathComponent(page.drawingFile)))
        }
        return (note, drawings)
    }
    func save(_ note: Notebook, drawings: [UUID: PKDrawing], completion: ((Error?) -> Void)? = nil) {
        io.async {
            do {
                try self.persist(note, drawings: drawings)
                DispatchQueue.main.async { do { try self.reload(); completion?(nil) } catch { self.error = error.localizedDescription; completion?(error) } }
            } catch {
                DispatchQueue.main.async { self.error = error.localizedDescription; completion?(error) }
            }
        }
    }
    /// Each new manifest points only at completed files. A crash before replacement retains the old revision.
    func persist(_ note: Notebook, drawings: [UUID: PKDrawing]) throws {
        var committed = note
        let dir = directory(note.id); try fm.createDirectory(at: dir, withIntermediateDirectories: true)
        let revision = UUID().uuidString
        var keep = Set<String>(["manifest.json", "thumbnail.png"])
        for i in committed.pages.indices {
            let id = committed.pages[i].id
            guard let drawing = drawings[id] else { throw FolioError.missingPage }
            let name = "\(id.uuidString)-\(revision).drawing"
            try drawing.dataRepresentation().write(to: dir.appendingPathComponent(name), options: .atomic)
            committed.pages[i].drawingFile = name; keep.insert(name)
            if let background = committed.pages[i].backgroundFile { keep.insert(background) }
        }
        let data = try JSONEncoder().encode(committed)
        try data.write(to: dir.appendingPathComponent("manifest.json"), options: .atomic)
        for file in try fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) where file.pathExtension == "drawing" && !keep.contains(file.lastPathComponent) {
            try? fm.removeItem(at: file)
        }
    }
    func updateMetadata(_ note: Notebook) {
        io.async {
            do {
                let (saved, drawings) = try self.read(note.id)
                var result = saved; result.title = note.title; result.folder = note.folder; result.trashed = note.trashed
                try self.persist(result, drawings: drawings)
                DispatchQueue.main.async { try? self.reload() }
            } catch { DispatchQueue.main.async { self.error = error.localizedDescription } }
        }
    }
    func purge(_ ids: Set<UUID>) {
        io.async {
            do {
                for id in ids { try self.fm.removeItem(at: self.directory(id)) }
                DispatchQueue.main.async { try? self.reload() }
            } catch { DispatchQueue.main.async { self.error = error.localizedDescription } }
        }
    }
    func setThumbnail(_ image: UIImage, note: UUID) {
        io.async { if let data = image.pngData() { try? data.write(to: self.directory(note).appendingPathComponent("thumbnail.png"), options: .atomic) } }
    }
    func safeFile(_ file: String) -> Bool { !file.contains("/") && !file.contains("\\") && file != "." && file != ".." }
}
