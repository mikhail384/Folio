import SwiftUI

@main struct FolioApp: App {
    @StateObject private var store = NotebookStore.shared
    @AppStorage("folio.welcomed") private var welcomed = false
    var body: some Scene {
        WindowGroup {
            LibraryView(store: store)
                .tint(Color(uiColor: FolioTheme.accent))
                .preferredColorScheme(.light)
                .fullScreenCover(isPresented: Binding(get: { !welcomed }, set: { welcomed = !$0 })) {
                    WelcomeView { welcomed = true }
                }
                .alert("Folio", isPresented: Binding(get: { store.error != nil }, set: { if !$0 { store.error = nil } })) {
                    Button("Понятно", role: .cancel) { store.error = nil }
                } message: { Text(store.error ?? "") }
                .onOpenURL { url in store.importFile(url) }
        }
    }
}

extension NotebookStore {
    func importFile(_ url: URL) {
        let access = url.startAccessingSecurityScopedResource()
        io.async {
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let _ = try DocumentIO.imported(url, store: self)
                DispatchQueue.main.async { try? self.reload() }
            } catch { DispatchQueue.main.async { self.error = error.localizedDescription } }
        }
    }
}
