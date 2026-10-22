import UIKit
import PencilKit

/// Imports a copy; unsupported Android semantics are rejected explicitly instead of silently discarded.
enum AndroidImport {
    static func read(_ folder: URL, store: NotebookStore) throws -> Notebook {
        let data = try Data(contentsOf: folder.appendingPathComponent("document.json"))
        guard data.count < 180 * 1024 * 1024 else { throw FolioError.tooLarge }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any], json["format"] as? String == "folio",
              let version = json["version"] as? Int, (1...3).contains(version),
              let pages = json["pages"] as? [[String: Any]], !pages.isEmpty, pages.count <= 2000 else { throw FolioError.invalidDocument("ожидается тетрадь Folio версии 1–3") }
        if let audio = json["audio"] as? [Any], !audio.isEmpty { throw FolioError.invalidDocument("аудио Android пока не переносится. Используйте PDF-копию") }
        var note = Notebook(title: store.uniqueTitle(json["title"] as? String ?? "", folder: "")); note.pages = []
        let dir = store.directory(note.id); try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        var drawings: [UUID: PKDrawing] = [:]
        var pointBudget = 0
        do {
            for source in pages {
                if let layers = source["layers"] as? [[String: Any]], layers.count > 1 || layers.contains(where: { $0["visible"] as? Bool == false || $0["locked"] as? Bool == true }) {
                    throw FolioError.invalidDocument("слои Android пока переносятся через PDF")
                }
                var page = CanvasPage()
                page.paper.infinite = source["infinite"] as? Bool ?? false
                page.paper.width = try number(source["width"], default: 1200, range: 200...10000)
                page.paper.height = try number(source["height"], default: 1640, range: 200...10000)
                page.paper.spacing = try number(source["spacing"], default: 32, range: 8...160)
                page.paper.pattern = PaperStyle.Pattern(rawValue: source["pattern"] as? String ?? "grid") ?? .grid
                page.paper.color = InkColor(UInt32(truncatingIfNeeded: (source["paper"] as? NSNumber)?.int64Value ?? -1))
                page.paper.lines = InkColor(UInt32(truncatingIfNeeded: (source["lines"] as? NSNumber)?.int64Value ?? 0x253e4659))
                page.originX = 0; page.originY = 0; page.viewportX = 0; page.viewportY = 0
                if let background = source["background"] as? String, !background.isEmpty {
                    guard store.safeFile(background) else { throw FolioError.invalidDocument("путь фона") }
                    let asset = folder.appendingPathComponent("assets").appendingPathComponent(background)
                    guard let image = UIImage(contentsOfFile: asset.path), let png = image.pngData() else { throw FolioError.invalidDocument("неподдерживаемый фон") }
                    let name = "background-\(page.id).png"; try png.write(to: dir.appendingPathComponent(name)); page.backgroundFile = name
                }
                var strokes: [PKStroke] = []
                for raw in source["strokes"] as? [[String: Any]] ?? [] {
                    guard let toolName = raw["tool"] as? String, let samples = raw["points"] as? [Double], samples.count % 5 == 0,
                          samples.count <= 1_500_000, samples.allSatisfy({ $0.isFinite && abs($0) <= 10_000_000 }) else { throw FolioError.invalidDocument("координаты штриха") }
                    pointBudget += samples.count / 5; guard pointBudget <= 3_000_000 else { throw FolioError.tooLarge }
                    guard !["text", "tape", "rainbow"].contains(toolName) else { throw FolioError.invalidDocument("инструмент «\(toolName)» пока переносится через PDF") }
                    let tool = FolioTool(rawValue: toolName) ?? .pen
                    let width = CGFloat(try number(raw["width"], default: 3, range: 0.2...200))
                    let color = InkColor(UInt32(truncatingIfNeeded: (raw["color"] as? NSNumber)?.int64Value ?? 0xff293449)).ui
                    var controls: [PKStrokePoint] = []
                    for i in stride(from: 0, to: samples.count, by: 5) {
                        let pressure = min(2, max(0.02, samples[i + 2]))
                        let diameter = tool == .pen || tool == .marker ? width : width * CGFloat(0.25 + pressure * 0.75)
                        controls.append(PKStrokePoint(location: CGPoint(x: samples[i], y: samples[i + 1]), timeOffset: Double(i / 5) / 120,
                            size: CGSize(width: diameter, height: tool == .marker ? diameter * 0.4 : diameter), opacity: 1, force: CGFloat(pressure),
                            azimuth: CGFloat(samples[i + 4]), altitude: .pi / 2 - CGFloat(min(1.57, max(0, samples[i + 3])))))
                    }
                    guard !controls.isEmpty else { continue }
                    if ["rect", "ellipse"].contains(toolName), let a = controls.first?.location, let b = controls.last?.location {
                        let rect = CGRect(x:min(a.x,b.x), y:min(a.y,b.y), width:abs(b.x-a.x), height:abs(b.y-a.y))
                        let points: [CGPoint]
                        if toolName == "ellipse" { points = (0...180).map { i in let t = CGFloat(i) * .pi / 90; return CGPoint(x:rect.midX + rect.width/2*cos(t), y:rect.midY + rect.height/2*sin(t)) } }
                        else { points = [CGPoint(x:rect.minX,y:rect.minY),CGPoint(x:rect.maxX,y:rect.minY),CGPoint(x:rect.maxX,y:rect.maxY),CGPoint(x:rect.minX,y:rect.maxY),CGPoint(x:rect.minX,y:rect.minY)] }
                        controls = points.enumerated().map { i,p in PKStrokePoint(location:p,timeOffset:Double(i)/120,size:CGSize(width:width,height:width),opacity:1,force:1,azimuth:0,altitude:.pi/2) }
                    }
                    var stroke = PKStroke(ink:PKInk(tool.inkType,color:color),path:PKStrokePath(controlPoints:controls,creationDate:Date()))
                    if let cuts = raw["cuts"] as? [Double], !cuts.isEmpty {
                        guard cuts.count % 3 == 0, cuts.count <= 600000, cuts.allSatisfy({ $0.isFinite && abs($0) <= 10_000_000 }) else { throw FolioError.invalidDocument("маска ластика") }
                        let ends = raw["cutEnds"] as? [Double]
                        if let ends, ends.count != cuts.count / 3 * 2 || !ends.allSatisfy({ $0.isFinite && abs($0) <= 10_000_000 }) { throw FolioError.invalidDocument("концы маски") }
                        var holes: CGPath?
                        for i in stride(from:0,to:cuts.count,by:3) {
                            let r = CGFloat(cuts[i+2]); guard r > 0 && r <= 1000 else { throw FolioError.invalidDocument("радиус ластика") }
                            let a = CGPoint(x:cuts[i],y:cuts[i+1]), j = i / 3 * 2
                            let b = ends.map { CGPoint(x:$0[j],y:$0[j+1]) } ?? a
                            let path = CGMutablePath(); path.move(to:a); path.addLine(to:b == a ? CGPoint(x:a.x+0.001,y:a.y) : b)
                            let capsule = path.copy(strokingWithWidth:r*2,lineCap:.round,lineJoin:.round,miterLimit:1)
                            holes = holes.map { $0.union(capsule,using:.winding) } ?? capsule
                        }
                        if let holes {
                            let outside = CGPath(rect:stroke.renderBounds.insetBy(dx:-2,dy:-2),transform:nil)
                            stroke = PKStroke(ink:stroke.ink,path:stroke.path,transform:stroke.transform,mask:UIBezierPath(cgPath:outside.subtracting(holes,using:.winding)))
                        }
                    }
                    strokes.append(stroke)
                }
                var drawing = PKDrawing(strokes:strokes)
                if page.paper.infinite {
                    let bounds = drawing.bounds
                    let dx = bounds.isNull ? 4096 : max(4096,4096-bounds.minX), dy = bounds.isNull ? 4096 : max(4096,4096-bounds.minY)
                    drawing = drawing.transformed(using:CGAffineTransform(translationX:dx,y:dy))
                    page.originX = Double(dx); page.originY = Double(dy); page.viewportX = Double(dx); page.viewportY = Double(dy)
                    page.canvasWidth = Double(max(16384,drawing.bounds.isNull ? 16384 : drawing.bounds.maxX+4096))
                    page.canvasHeight = Double(max(16384,drawing.bounds.isNull ? 16384 : drawing.bounds.maxY+4096))
                }
                note.pages.append(page); drawings[page.id] = drawing
            }
            try store.persist(note,drawings:drawings); return note
        } catch { try? FileManager.default.removeItem(at:dir); throw error }
    }
    static func number(_ value: Any?, default fallback: Double, range: ClosedRange<Double>) throws -> Double {
        let result = (value as? NSNumber)?.doubleValue ?? fallback
        guard result.isFinite, range.contains(result) else { throw FolioError.invalidDocument("число вне допустимого диапазона") }
        return result
    }
}
