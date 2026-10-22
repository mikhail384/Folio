import SwiftUI

struct PaperSettingsView: View {
    @Binding var paper: PaperStyle
    var body: some View {
        Section("Бумага") {
            Picker("Разметка",selection:$paper.pattern) { ForEach(PaperStyle.Pattern.allCases) { Text($0.title).tag($0) } }.pickerStyle(.segmented)
            ColorPicker("Цвет бумаги",selection:Binding(get:{ Color(uiColor:paper.color.ui) },set:{ paper.color=InkColor(UIColor($0)) }),supportsOpacity:false)
            ColorPicker("Цвет линий",selection:Binding(get:{ Color(uiColor:paper.lines.ui) },set:{ paper.lines=InkColor(UIColor($0)) }))
            HStack { Text("Шаг"); Slider(value:$paper.spacing,in:8...96,step:4); Text("\(Int(paper.spacing))").monospacedDigit().frame(width:28) }
            Toggle("Бесконечный холст",isOn:$paper.infinite)
            if !paper.infinite {
                HStack {
                    Button("A4") { paper.width=1190; paper.height=1684 }
                    Button("A5") { paper.width=842; paper.height=1190 }
                    Button("Квадрат") { paper.width=1200; paper.height=1200 }
                    Button { let width=paper.width; paper.width=paper.height; paper.height=width } label: { Image(systemName:"rotate.right") }
                }.buttonStyle(.bordered)
            }
        }
    }
}

struct WorkshopView: View {
    @ObservedObject var store: NotebookStore
    @Environment(\.dismiss) var dismiss
    @State private var preferences: FolioPreferences
    init(store:NotebookStore) { self.store=store; _preferences=State(initialValue:store.preferences) }
    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing:18) {
                        Image(systemName:"applepencil.and.scribble").font(.system(size:38)).foregroundStyle(Color(uiColor:FolioTheme.accent))
                        VStack(alignment:.leading,spacing:6) { Text("Ваш почерк. Ваши правила.").font(.title3.weight(.semibold)); Text("Настройте Folio под свою руку.").foregroundStyle(.secondary) }
                    }.padding(.vertical,14)
                }
                Section("Apple Pencil") {
                    Picker("Двойной тап",selection:$preferences.doubleTap) { ForEach(PencilCommand.allCases) { Text($0.title).tag($0) } }
                    Picker("Сжатие Pencil Pro",selection:$preferences.squeeze) { ForEach(PencilCommand.allCases) { Text($0.title).tag($0) } }
                    Toggle("Показывать наконечник при наведении",isOn:$preferences.showHover)
                    Toggle("Рисовать также пальцем",isOn:$preferences.fingerDrawing)
                    Text("Одинарный тап по корпусу Apple не передаёт приложениям. Двойной тап, сжатие и наведение доступны на поддерживаемых моделях. Если сжатию назначена системная быстрая команда, iPad выполняет её самостоятельно.").font(.footnote).foregroundStyle(.secondary)
                }
                Section("Пенал · удерживайте справа для перестановки") {
                    ForEach(preferences.tools) { tool in Label(tool.title,systemImage:tool.symbol) }
                        .onMove { preferences.tools.move(fromOffsets:$0,toOffset:$1) }
                        .onDelete { offsets in if preferences.tools.count-offsets.count >= 1 { preferences.tools.remove(atOffsets:offsets) } }
                    Menu("Добавить инструмент",systemImage:"plus") {
                        ForEach(FolioTool.allCases.filter { !preferences.tools.contains($0) }) { tool in
                            Button(tool.title,systemImage:tool.symbol) { preferences.tools.append(tool) }
                        }
                    }
                }
                PaperSettingsView(paper:$preferences.defaultPaper)
                Section {
                    Text("Folio для iPad · 0.1.0\nТетради хранятся на этом iPad. Экспортируйте .folioipad для резервной копии в Файлы или iCloud Drive.").font(.footnote).foregroundStyle(.secondary)
                }
            }.scrollContentBackground(.hidden).background(Color(uiColor:FolioTheme.paper))
            .navigationTitle("Мастерская").toolbar { ToolbarItem(placement:.confirmationAction) { Button("Готово") { dismiss() } } }
            .environment(\.editMode,.constant(.active))
            .onChange(of:preferencesData) { _,_ in store.preferences=preferences; store.savePreferences() }
        }.presentationDetents([.large]).presentationCornerRadius(28)
    }
    var preferencesData: Data { let encoder=JSONEncoder(); encoder.outputFormatting = .sortedKeys; return (try? encoder.encode(preferences)) ?? Data() }
}

struct ToolSettingsView: View {
    let tool:FolioTool
    @State var width:Double
    @State var strokeEraser:Bool
    let change:(Double,Bool)->Void
    var body: some View {
        VStack(alignment:.leading,spacing:18) {
            HStack { Image(systemName:tool.symbol).foregroundStyle(Color(uiColor:FolioTheme.accent)); Text(tool.title).font(.headline); Spacer(); Text(String(format:"%.1f",width)).monospacedDigit().foregroundStyle(.secondary) }
            Slider(value:$width,in:tool == .eraser ? 4...100 : 0.5...60).tint(Color(uiColor:FolioTheme.accent))
            if tool == .eraser {
                Picker("Режим",selection:$strokeEraser) { Text("По кругу").tag(false); Text("Штрих целиком").tag(true) }.pickerStyle(.segmented)
            } else if tool.hasColor {
                StrokePreview(tool:tool,width:width).frame(height:48)
            }
        }.padding(22).background(Color.white)
            .onChange(of:width) { _,v in change(v,strokeEraser) }
            .onChange(of:strokeEraser) { _,v in change(width,v) }
    }
}

struct StrokePreview: UIViewRepresentable {
    let tool:FolioTool
    let width:Double
    func makeUIView(context:Context) -> PreviewInkView { PreviewInkView() }
    func updateUIView(_ view:PreviewInkView,context:Context) { view.tool=tool; view.width=width; view.setNeedsDisplay() }
}

final class PreviewInkView:UIView {
    var tool=FolioTool.pen
    var width=3.0
    override init(frame:CGRect) { super.init(frame:frame); isOpaque=false }
    required init?(coder:NSCoder) { fatalError() }
    override func draw(_ rect:CGRect) {
        let points = (0...100).map { i in CGPoint(x:CGFloat(i)/100*bounds.width,y:bounds.midY+sin(CGFloat(i)/100 * .pi*2)*10) }
        let stroke=StrokeFactory.stroke(points:points,tool:tool,color:FolioTheme.accent,width:min(30,width))
        let drawing=PencilKit.PKDrawing(strokes:[stroke])
        drawing.image(from:bounds,scale:UIScreen.main.scale).draw(in:bounds)
    }
}

import PencilKit
