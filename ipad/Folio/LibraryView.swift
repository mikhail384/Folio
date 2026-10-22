import SwiftUI
import UniformTypeIdentifiers

struct LibraryView: View {
    @ObservedObject var store: NotebookStore
    @State private var opened: Notebook?
    @State private var folder = "Все тетради"
    @State private var search = ""
    @State private var importing = false
    @State private var settings = false
    @State private var naming = false
    @State private var folderName = ""
    @State private var selected = Set<UUID>()
    @AppStorage("folio.folders") private var savedFolders = ""
    private var folders: [String] { Array(Set(savedFolders.components(separatedBy: "\n").filter { !$0.isEmpty } + store.notebooks.map(\.folder).filter { !$0.isEmpty })).sorted() }
    private var visible: [Notebook] {
        store.notebooks.filter { note in
            (folder == "Корзина" ? note.trashed != nil : note.trashed == nil) &&
            (["Все тетради", "Корзина"].contains(folder) || note.folder == folder) &&
            (search.isEmpty || note.title.localizedCaseInsensitiveContains(search))
        }
    }
    var body: some View {
        NavigationSplitView {
            VStack(alignment: .leading, spacing: 28) {
                HStack(spacing: 10) {
                    Image(systemName: "scribble.variable").font(.system(size: 30, weight: .medium)).foregroundStyle(Color(uiColor:FolioTheme.accent))
                    Text("Folio").font(.system(size: 32, weight: .semibold, design: .rounded))
                }.padding(.top, 22)
                VStack(alignment: .leading, spacing: 8) {
                    navItem("Все тетради", symbol: "square.grid.2x2")
                    ForEach(folders, id: \.self) { navItem($0, symbol: "folder") }
                    Button { naming = true } label: { Label("Новая папка", systemImage:"folder.badge.plus").font(.system(size:15,weight:.medium)).padding(14) }
                    navItem("Корзина", symbol: "trash")
                }
                Spacer()
                Button { settings = true } label: { Label("Мастерская", systemImage: "slider.horizontal.3").padding(14) }
                Text("Место для ваших мыслей.").font(.footnote).foregroundStyle(.secondary).padding(.bottom, 20)
            }.padding(.horizontal, 20).frame(maxWidth:.infinity,alignment:.leading).background(Color(uiColor:FolioTheme.paper))
            .navigationTitle("").toolbar(.hidden, for:.navigationBar)
        } detail: {
            ScrollView {
                VStack(alignment:.leading,spacing:24) {
                    HStack {
                        VStack(alignment:.leading,spacing:8) {
                            Text(folder).font(.system(size:34,weight:.semibold,design:.rounded))
                            Text(folder == "Корзина" ? "Выберите тетради для восстановления" : "Пишите. Рисуйте. Думайте свободно.").foregroundStyle(.secondary)
                        }
                        Spacer()
                        if folder == "Корзина" { trashActions }
                        else {
                            Button { importing = true } label: { Image(systemName:"square.and.arrow.down").font(.title3).frame(width:46,height:46).background(.white,in:Circle()) }.accessibilityLabel("Импорт")
                            Button(action:create) { Label("Новая тетрадь",systemImage:"plus").padding(.horizontal,10).padding(.vertical,8) }.buttonStyle(.borderedProminent).buttonBorderShape(.capsule).accessibilityIdentifier("createNotebook")
                        }
                    }
                    if visible.isEmpty {
                        ContentUnavailableView(folder == "Корзина" ? "В корзине пусто" : "Здесь начнётся ваша история", systemImage: folder == "Корзина" ? "trash" : "pencil.and.scribble", description:Text("\(folder == "Корзина" ? "Удалённые тетради появятся здесь." : "Создайте тетрадь или импортируйте PDF.")"))
                            .frame(minHeight:380)
                    }
                    LazyVGrid(columns:[GridItem(.adaptive(minimum:210,maximum:290),spacing:24)],alignment:.leading,spacing:30) {
                        ForEach(visible) { note in card(note) }
                    }
                }.padding(34)
            }.background(Color(uiColor:FolioTheme.paper).opacity(0.5)).searchable(text:$search,prompt:"Найти тетрадь")
            .navigationTitle("").navigationBarTitleDisplayMode(.inline)
        }
        .fullScreenCover(item:$opened) { note in EditorScreen(id:note.id,store:store) }
        .sheet(isPresented:$settings) { WorkshopView(store:store) }
        .fileImporter(isPresented:$importing,allowedContentTypes:[.pdf,.image,.zip,UTType(importedAs:"ru.folio.ipad.document"),UTType(importedAs:"ru.folio.android.document")]) { result in
            do { store.importFile(try result.get()) } catch { store.error = error.localizedDescription }
        }
        .alert("Новая папка",isPresented:$naming) {
            TextField("Название",text:$folderName)
            Button("Создать") { let name = folderName.trimmingCharacters(in:.whitespacesAndNewlines); if !name.isEmpty && !["Все тетради","Корзина"].contains(name) { savedFolders = (folders + [name]).joined(separator:"\n"); folder = name }; folderName = "" }
            Button("Отмена",role:.cancel) { folderName = "" }
        }
        .onChange(of:folder) { _,_ in selected.removeAll() }
    }
    func navItem(_ title:String,symbol:String) -> some View {
        Button { withAnimation(.snappy) { folder = title } } label: {
            HStack { Image(systemName:symbol).frame(width:24); Text(title); Spacer(); if title == folder { Circle().frame(width:6,height:6) } }
                .font(.system(size:16,weight:title == folder ? .semibold:.regular)).padding(14)
                .background(title == folder ? Color(uiColor:FolioTheme.accent).opacity(0.12):.clear,in:RoundedRectangle(cornerRadius:16))
        }.foregroundStyle(Color(uiColor:FolioTheme.ink))
    }
    func card(_ note:Notebook) -> some View {
        Button {
            if folder == "Корзина" { if !selected.insert(note.id).inserted { selected.remove(note.id) } }
            else { opened = note }
        } label: {
            VStack(alignment:.leading,spacing:12) {
                ZStack(alignment:.topTrailing) {
                    RoundedRectangle(cornerRadius:22).fill(Color(uiColor:note.pages.first?.paper.color.ui ?? .white))
                    if let image = UIImage(contentsOfFile:store.directory(note.id).appendingPathComponent("thumbnail.png").path) {
                        Image(uiImage:image).resizable().scaledToFit().padding(12)
                    } else {
                        VStack(alignment:.leading,spacing:16) {
                            Text("f.").font(.system(size:64,weight:.medium,design:.serif)).foregroundStyle(Color(uiColor:FolioTheme.accent).opacity(0.5))
                            ForEach(0..<4) { i in Capsule().fill(Color(uiColor:FolioTheme.ink).opacity(0.07)).frame(width:CGFloat(130-i*18),height:2) }
                        }.frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.topLeading).padding(26)
                    }
                    if selected.contains(note.id) { Image(systemName:"checkmark.circle.fill").font(.title).padding(12) }
                }.frame(height:260).clipShape(RoundedRectangle(cornerRadius:22)).overlay(RoundedRectangle(cornerRadius:22).stroke(.black.opacity(0.04))).shadow(color:.black.opacity(0.05),radius:14,x:0,y:8)
                Text(note.title).font(.system(size:17,weight:.semibold)).lineLimit(1).foregroundStyle(Color(uiColor:FolioTheme.ink))
                Text("\(note.pages.count) стр. · \(note.modified.formatted(date:.abbreviated,time:.omitted))").font(.footnote).foregroundStyle(.secondary)
            }
        }.buttonStyle(.plain).contextMenu {
            if note.trashed == nil {
                Menu("Переместить",systemImage:"folder") {
                    Button("Без папки") { var n=note; n.folder=""; n.title=store.uniqueTitle(n.title,folder:"",excluding:n.id); store.updateMetadata(n) }
                    ForEach(folders,id:\.self) { name in Button(name) { var n=note; n.folder=name; n.title=store.uniqueTitle(n.title,folder:name,excluding:n.id); store.updateMetadata(n) } }
                }
                Button("В корзину",systemImage:"trash",role:.destructive) { var n=note; n.trashed=Date(); store.updateMetadata(n) }
            } else { Button("Восстановить",systemImage:"arrow.uturn.backward") { restore(note) } }
        }
    }
    var trashActions: some View {
        HStack {
            Button("Все") { selected = Set(visible.map(\.id)) }
            Button("Восстановить") { for n in visible where selected.contains(n.id) { restore(n) }; selected.removeAll() }.disabled(selected.isEmpty)
            Button("Удалить",role:.destructive) { store.purge(selected); selected.removeAll() }.disabled(selected.isEmpty)
        }.buttonStyle(.bordered)
    }
    func restore(_ note:Notebook) { var n=note; n.trashed=nil; n.title=store.uniqueTitle(n.title,folder:n.folder,excluding:n.id); store.updateMetadata(n) }
    func create() { do { opened = try store.create(folder:folder == "Все тетради" ? "":folder) } catch { store.error=error.localizedDescription } }
}

struct EditorScreen: UIViewControllerRepresentable {
    let id:UUID
    let store:NotebookStore
    @Environment(\.dismiss) var dismiss
    func makeUIViewController(context:Context) -> EditorController { EditorController(id:id,store:store,onClose:{ dismiss() }) }
    func updateUIViewController(_ uiViewController:EditorController,context:Context) {}
}
