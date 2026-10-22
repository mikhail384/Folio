import XCTest
import PencilKit
import UIKit
import ZIPFoundation
@testable import Folio

final class FolioTests:XCTestCase {
    var root:URL!
    var store:NotebookStore!
    override func setUpWithError() throws {
        root=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        store=NotebookStore(root:root)
    }
    override func tearDownWithError() throws { try? FileManager.default.removeItem(at:root) }
    func testNamesUseOnlyExistingSiblings() {
        XCTAssertEqual(NotebookNames.unique("Идея",existing:[]),"Идея")
        XCTAssertEqual(NotebookNames.unique("Идея",existing:["Идея","Идея (2)"]),"Идея (1)")
        XCTAssertEqual(NotebookNames.unique("Идея",existing:["Идея (1)"]),"Идея")
        XCTAssertEqual(NotebookNames.unique("идея",existing:["Идея"]),"идея (1)")
        let date=Date(timeIntervalSince1970:0)
        XCTAssertFalse(NotebookNames.unique("  ",existing:[],date:date).trimmingCharacters(in:.whitespaces).isEmpty)
    }
    func drawing(_ offset:CGFloat=0)->PKDrawing {
        PKDrawing(strokes:[StrokeFactory.stroke(points:[CGPoint(x:10+offset,y:20),CGPoint(x:80+offset,y:90)],tool:.pen,color:.black,width:3)])
    }
    func testAtomicPersistenceAndReopen() throws {
        let note=try store.create(title:"Тест")
        let ink=drawing(); try store.persist(note,drawings:[note.pages[0].id:ink])
        let (loaded,drawings)=try store.read(note.id)
        XCTAssertEqual(loaded.title,"Тест"); XCTAssertEqual(drawings[note.pages[0].id]?.strokes.count,1)
        XCTAssertFalse(loaded.pages[0].drawingFile.isEmpty)
        let reopened=NotebookStore(root:root); XCTAssertEqual(reopened.notebooks.count,1)
        let files=try FileManager.default.contentsOfDirectory(at:store.directory(note.id),includingPropertiesForKeys:nil)
        XCTAssertEqual(files.filter{$0.pathExtension == "drawing"}.count,1)
    }
    func testIncompleteSnapshotDoesNotReplaceManifest() throws {
        let note=try store.create(title:"Исходная")
        let old=try Data(contentsOf:store.directory(note.id).appendingPathComponent("manifest.json"))
        var broken=note; broken.title="Неполная"; broken.pages.append(CanvasPage())
        XCTAssertThrowsError(try store.persist(broken,drawings:[note.pages[0].id:drawing()]))
        XCTAssertEqual(try Data(contentsOf:store.directory(note.id).appendingPathComponent("manifest.json")),old)
        XCTAssertNoThrow(try store.read(note.id))
    }
    func testUndoRedoRebaseAndNewBranch() {
        var history=DrawingHistory(); let a=drawing(),b=drawing(10),c=drawing(20)
        history.record(a)
        XCTAssertEqual(history.undo(b)?.strokes.first?.path.first?.location,a.strokes.first?.path.first?.location)
        XCTAssertEqual(history.redo(a)?.strokes.first?.path.first?.location,b.strokes.first?.path.first?.location)
        history.translate(CGAffineTransform(translationX:8192,y:0))
        let result=history.undo(c)
        XCTAssertNotNil(result)
        XCTAssertEqual(result!.bounds.minX,a.bounds.minX+8192,accuracy:0.01)
        history.record(c); XCTAssertTrue(history.future.isEmpty)
        for _ in 0..<60 { history.record(a) }; XCTAssertEqual(history.past.count,40)
    }
    func testIndependentToolColorsAndSettingsRoundtrip() throws {
        var p=FolioPreferences(); var pen=p.style(for:.pen); pen.colors[0]=InkColor(0xff123456); p.styles["pen"]=pen
        XCTAssertNotEqual(p.style(for:.pen).colors[0],p.style(for:.marker).colors[0])
        p.squeeze = .undo
        let decoded=try JSONDecoder().decode(FolioPreferences.self,from:JSONEncoder().encode(p))
        XCTAssertEqual(decoded.squeeze,.undo); XCTAssertEqual(decoded.style(for:.pen).colors[0].argb,0xff123456)
        XCTAssertFalse(FolioTool.eraser.hasColor); XCTAssertEqual(FolioTool.pen.inkType,.monoline)
    }
    @MainActor func testEditorUndoRedoSurvivesEmptyContact() throws {
        let note=try store.create(title:"История")
        let editor=EditorController(id:note.id,store:store,onClose:{})
        editor.loadViewIfNeeded()
        editor.canvasViewDidBeginUsingTool(editor.canvas)
        editor.canvas.drawing=drawing()
        editor.canvasViewDidEndUsingTool(editor.canvas)
        XCTAssertEqual(editor.history.past.count,1)
        editor.undoInk(); XCTAssertTrue(editor.canvas.drawing.strokes.isEmpty)
        XCTAssertEqual(editor.history.future.count,1)
        editor.canvasViewDidBeginUsingTool(editor.canvas)
        editor.canvasViewDidEndUsingTool(editor.canvas)
        XCTAssertEqual(editor.history.future.count,1)
        editor.redoInk(); XCTAssertEqual(editor.canvas.drawing.strokes.count,1)
        editor.saving?.cancel()
    }
    func testNativeArchiveRoundTrip() throws {
        let note=try store.create(title:"Обмен")
        try store.persist(note,drawings:[note.pages[0].id:drawing()])
        let archive=try DocumentIO.zip(store.directory(note.id),title:note.title); defer { try? FileManager.default.removeItem(at:archive) }
        let imported=try DocumentIO.imported(archive,store:store)
        XCTAssertNotEqual(imported.id,note.id); XCTAssertEqual(imported.title,"Обмен (1)")
        let (_,ink)=try store.read(imported.id); XCTAssertEqual(ink[imported.pages[0].id]?.strokes.count,1)
    }
    func testZipTraversalRejectedBeforeWritingOutsideDestination() throws {
        let zip=root.appendingPathComponent("bad.zip"),outside=root.appendingPathComponent("outside")
        let archive=try Archive(url:zip,accessMode:.create)
        try archive.addEntry(with:"../outside",type:.file,uncompressedSize:Int64(1),provider:{ _,_ in Data([65]) })
        XCTAssertThrowsError(try DocumentIO.extract(zip,to:root.appendingPathComponent("extracted")))
        XCTAssertFalse(FileManager.default.fileExists(atPath:outside.path))
    }
    func testSmallImageNotebookArchiveCanReopen() throws {
        let url=root.appendingPathComponent("small.png")
        let image=UIGraphicsImageRenderer(size:CGSize(width:32,height:24)).image { c in UIColor.red.setFill(); c.cgContext.fill(CGRect(x:0,y:0,width:32,height:24)) }
        try XCTUnwrap(image.pngData()).write(to:url)
        let note=try DocumentIO.imported(url,store:store)
        let zip=try DocumentIO.zip(store.directory(note.id),title:note.title)
        defer { try? FileManager.default.removeItem(at:zip) }
        let copy=try DocumentIO.imported(zip,store:store)
        XCTAssertEqual(copy.pages[0].paper.width,32)
        XCTAssertEqual(copy.pages[0].paper.height,24)
        XCTAssertNotNil(copy.pages[0].backgroundFile)
        XCTAssertNoThrow(try store.read(copy.id))
    }
    func testAndroidPressureGeometryAndOverlappingCuts() throws {
        let temp=root.appendingPathComponent("android"); try FileManager.default.createDirectory(at:temp,withIntermediateDirectories:true)
        let json:[String:Any] = ["format":"folio","version":3,"title":"Android","pages":[["infinite":false,"strokes":[["tool":"pen","width":8,"color":-16777216,"points":[0,50,1,0,0,100,50,1,0,0],"cuts":[40,50,10,45,50,10],"cutEnds":[40,50,45,50]]]]]]
        try JSONSerialization.data(withJSONObject:json).write(to:temp.appendingPathComponent("document.json"))
        let note=try AndroidImport.read(temp,store:store),(_,drawings)=try store.read(note.id)
        let stroke=try XCTUnwrap(drawings[note.pages[0].id]?.strokes.first)
        XCTAssertNotNil(stroke.mask)
        XCTAssertFalse(stroke.mask!.contains(CGPoint(x:43,y:50)))
        XCTAssertTrue(stroke.mask!.contains(CGPoint(x:80,y:50)))
    }
    func testAndroidLayerImportFailsWithoutCreatingBrokenNotebook() throws {
        let temp=root.appendingPathComponent("layers"); try FileManager.default.createDirectory(at:temp,withIntermediateDirectories:true)
        let json:[String:Any] = ["format":"folio","version":3,"title":"Layers","pages":[["layers":[["id":"base"],["id":"two"]],"strokes":[]]]]
        try JSONSerialization.data(withJSONObject:json).write(to:temp.appendingPathComponent("document.json"))
        XCTAssertThrowsError(try AndroidImport.read(temp,store:store)); try store.reload(); XCTAssertTrue(store.notebooks.isEmpty)
    }
    func testCompassArcAndAngleSnaps() {
        let arc=StrokeFactory.arc(center:.zero,radius:100,start:0,sweep:.pi)
        XCTAssertEqual(arc.first!.x,100,accuracy:0.01); XCTAssertEqual(arc.last!.x,-100,accuracy:0.01)
        XCTAssertEqual(arc.last!.y,0,accuracy:0.01)
        for d in [30,45,60,90,120,135,150,180,210,225,240,270,300,315,330] {
            XCTAssertEqual(StrokeFactory.snappedAngle(CGFloat(d+1) * .pi / 180),CGFloat(d) * .pi / 180,accuracy:0.001)
        }
    }
}
