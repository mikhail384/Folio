import UIKit
import SwiftUI
import PencilKit
import UIKit.UIGestureRecognizerSubclass

final class EditorController:UIViewController, PKCanvasViewDelegate, UIGestureRecognizerDelegate, UIPencilInteractionDelegate, UIColorPickerViewControllerDelegate {
    let id:UUID
    let store:NotebookStore
    let onClose:()->Void
    var note=Notebook(title:"")
    var drawings:[UUID:PKDrawing]=[:]
    var histories:[UUID:DrawingHistory]=[:]
    var history=DrawingHistory()
    var beforeStroke=PKDrawing()
    var tool=FolioTool.pen
    var previousTool=FolioTool.pen
    var eraserReturnTool=FolioTool.pen
    let canvas=FolioCanvas()
    let paper=PaperView()
    let top=UIStackView()
    let strip=UIStackView()
    let stripScroll=UIScrollView()
    let penCase=UIView()
    let colors=UIStackView()
    let bottom=UIStackView()
    let titleButton=UIButton(type:.system)
    let pageButton=UIButton(type:.system)
    let status=UILabel()
    var toolButtons:[FolioTool:UIButton]=[:]
    var toolGlyphs:[FolioTool:ToolGlyph]=[:]
    var colorButtons:[UIButton]=[]
    let hoverLayer=CAShapeLayer()
    let hoverMask=CAShapeLayer()
    var hoveredPoint:CGPoint?
    var floating:UIViewController?
    var radial:RadialPalette?
    var saving:DispatchWorkItem?
    var saveGeneration=0
    var loading=true
    var interacting=false
    var initialLayout=false
    var guide=GuideState()
    let guideLayer=CAShapeLayer()
    let laserLayer=CAShapeLayer()
    var customPoints:[CGPoint]=[]
    var startAngle:CGFloat=0
    var lastAngle:CGFloat=0
    var sweep:CGFloat=0
    var colorIndex=0
    var closed=false
    var page:CanvasPage { get { note.pages[note.current] } set { note.pages[note.current]=newValue } }

    init(id:UUID,store:NotebookStore,onClose:@escaping()->Void) {
        self.id=id; self.store=store; self.onClose=onClose; super.init(nibName:nil,bundle:nil)
    }
    required init?(coder:NSCoder) { fatalError() }
    override var prefersStatusBarHidden:Bool { true }
    override var prefersHomeIndicatorAutoHidden:Bool { true }
    override var preferredScreenEdgesDeferringSystemGestures:UIRectEdge { .all }
    override func viewDidLoad() {
        super.viewDidLoad(); view.backgroundColor=FolioTheme.paper
        do { (note,drawings)=try store.read(id) } catch { store.error=error.localizedDescription; DispatchQueue.main.async { self.onClose() }; return }
        view.addSubview(paper); view.addSubview(canvas)
        canvas.backgroundColor = .clear; canvas.isOpaque=false; canvas.delegate=self
        canvas.minimumZoomScale=0.08; canvas.maximumZoomScale=6
        canvas.contentInsetAdjustmentBehavior = .never
        canvas.showsHorizontalScrollIndicator=false; canvas.showsVerticalScrollIndicator=false
        canvas.alwaysBounceVertical=true; canvas.alwaysBounceHorizontal=true
        canvas.drawingPolicy=store.preferences.fingerDrawing ? .anyInput:.pencilOnly
        canvas.drawing=drawings[page.id] ?? PKDrawing()
        beforeStroke=canvas.drawing
        for layer in [guideLayer,laserLayer,hoverLayer] { view.layer.addSublayer(layer); layer.fillColor=UIColor.clear.cgColor }
        guideLayer.strokeColor=FolioTheme.accent.withAlphaComponent(0.5).cgColor; guideLayer.lineWidth=1
        laserLayer.strokeColor=UIColor.systemRed.cgColor; laserLayer.lineWidth=3; laserLayer.lineCap = .round; laserLayer.lineJoin = .round
        laserLayer.shadowColor=UIColor.systemRed.cgColor; laserLayer.shadowOpacity=0.85; laserLayer.shadowRadius=10
        hoverLayer.strokeColor=FolioTheme.ink.withAlphaComponent(0.65).cgColor; hoverLayer.lineWidth=1
        configureChrome(); rebuildTools(); loadPage()
        let two=UITapGestureRecognizer(target:self,action:#selector(twoFingerUndo)); two.numberOfTouchesRequired=2; two.allowedTouchTypes=[NSNumber(value:UITouch.TouchType.direct.rawValue)]; two.cancelsTouchesInView=false; two.delegate=self; canvas.addGestureRecognizer(two)
        let hover=UIHoverGestureRecognizer(target:self,action:#selector(hovered(_:))); hover.allowedTouchTypes=[NSNumber(value:UITouch.TouchType.pencil.rawValue)]; view.addGestureRecognizer(hover)
        let tracker=InkTracker(); tracker.delegate=self; tracker.cancelsTouchesInView=false
        tracker.acceptsFinger={ [weak self] in self?.store.preferences.fingerDrawing ?? false }
        tracker.changed={ [weak self] point,phase in self?.track(point,phase:phase) }; canvas.addGestureRecognizer(tracker)
        let interaction=UIPencilInteraction(); interaction.delegate=self; view.addInteraction(interaction)
        NotificationCenter.default.addObserver(self,selector:#selector(backgrounded),name:UIApplication.willResignActiveNotification,object:nil)
        NotificationCenter.default.addObserver(self,selector:#selector(foregrounded),name:UIApplication.didBecomeActiveNotification,object:nil)
        applyTool()
    }
    deinit { NotificationCenter.default.removeObserver(self); saving?.cancel() }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews(); paper.frame=view.bounds; canvas.frame=view.bounds
        if !initialLayout && view.bounds.width > 0 {
            initialLayout=true; restoreViewport(); loading=false
            penCase.alpha=0; penCase.transform=CGAffineTransform(translationX:0,y:-18)
            FolioTheme.animate { self.penCase.alpha=1; self.penCase.transform = .identity }
        }
        updatePaper(); drawGuide()
    }
    func loadPage() {
        loading=true; canvas.layer.mask=nil; hoverLayer.path=nil
        canvas.drawing=drawings[page.id] ?? PKDrawing(); beforeStroke=canvas.drawing
        history=histories[page.id] ?? DrawingHistory()
        canvas.contentSize=page.paper.infinite ? CGSize(width:page.canvasWidth,height:page.canvasHeight):CGSize(width:page.paper.width,height:page.paper.height)
        paper.backgroundImage=page.backgroundFile.flatMap { UIImage(contentsOfFile:store.directory(id).appendingPathComponent($0).path) }
        restoreViewport(); loading=false; updatePaper(); refreshChrome()
    }
    func restoreViewport() {
        canvas.zoomScale=CGFloat(min(6,max(0.08,page.zoom)))
        canvas.contentOffset=CGPoint(x:page.viewportX * page.zoom,y:page.viewportY * page.zoom)
    }
    func rememberPage() {
        drawings[page.id]=canvas.drawing; histories[page.id]=history
        var p=page; p.viewportX=Double(canvas.contentOffset.x/canvas.zoomScale); p.viewportY=Double(canvas.contentOffset.y/canvas.zoomScale); p.zoom=Double(canvas.zoomScale); page=p
    }
    func updatePaper() { paper.page=page; paper.offset=canvas.contentOffset; paper.zoom=canvas.zoomScale }
    func scrollViewDidScroll(_ scrollView:UIScrollView) { if isViewLoaded { updatePaper(); if !loading { scheduleSave() } } }
    func scrollViewDidZoom(_ scrollView:UIScrollView) { updatePaper() }
    func scrollViewDidEndDragging(_ scrollView:UIScrollView,willDecelerate decelerate:Bool) { if !decelerate { expandCanvas() } }
    func scrollViewDidEndDecelerating(_ scrollView:UIScrollView) { expandCanvas() }
    func scrollViewDidEndZooming(_ scrollView:UIScrollView,with view:UIView?,atScale scale:CGFloat) { expandCanvas() }
    func expandCanvas() {
        guard !loading,!interacting,page.paper.infinite else { return }
        let visible=visibleRect(),margin:CGFloat=1024,step:CGFloat=8192
        let dx:CGFloat=visible.minX < margin ? step:0, dy:CGFloat=visible.minY < margin ? step:0
        var p=page; var changed=false; loading=true
        if dx>0 || dy>0 {
            let t=CGAffineTransform(translationX:dx,y:dy)
            canvas.drawing=canvas.drawing.transformed(using:t); beforeStroke=canvas.drawing; history.translate(t)
            p.originX+=Double(dx); p.originY+=Double(dy); p.canvasWidth+=Double(dx); p.canvasHeight+=Double(dy); changed=true
        }
        if visible.maxX+dx > CGFloat(p.canvasWidth)-margin { p.canvasWidth+=Double(step); changed=true }
        if visible.maxY+dy > CGFloat(p.canvasHeight)-margin { p.canvasHeight+=Double(step); changed=true }
        let old=canvas.contentOffset; page=p
        canvas.contentSize=CGSize(width:p.canvasWidth,height:p.canvasHeight)
        canvas.contentOffset=CGPoint(x:old.x+dx*canvas.zoomScale,y:old.y+dy*canvas.zoomScale)
        loading=false; updatePaper(); if changed { scheduleSave() }
    }
    func visibleRect()->CGRect { CGRect(x:canvas.contentOffset.x/canvas.zoomScale,y:canvas.contentOffset.y/canvas.zoomScale,width:canvas.bounds.width/canvas.zoomScale,height:canvas.bounds.height/canvas.zoomScale) }
    func world(_ point:CGPoint)->CGPoint { CGPoint(x:(point.x+canvas.contentOffset.x)/canvas.zoomScale,y:(point.y+canvas.contentOffset.y)/canvas.zoomScale) }
    func screen(_ point:CGPoint)->CGPoint { CGPoint(x:point.x*canvas.zoomScale-canvas.contentOffset.x,y:point.y*canvas.zoomScale-canvas.contentOffset.y) }
    func canvasViewDidBeginUsingTool(_ canvasView:PKCanvasView) {
        canvas.layer.mask=nil; beforeStroke=canvas.drawing; interacting=true; dismissFloating()
    }
    func canvasViewDrawingDidChange(_ canvasView:PKCanvasView) { if !loading && !interacting { scheduleSave() } }
    func canvasViewDidEndUsingTool(_ canvasView:PKCanvasView) {
        history.record(beforeStroke); interacting=false; hoverLayer.path=nil
        scheduleSave(); expandCanvas(); refreshChrome()
    }
    @objc func twoFingerUndo() { undoInk() }
    func undoInk() { guard !interacting,let drawing=history.undo(canvas.drawing) else { return }; loading=true; canvas.drawing=drawing; loading=false; scheduleSave(); refreshChrome() }
    func redoInk() { guard !interacting,let drawing=history.redo(canvas.drawing) else { return }; loading=true; canvas.drawing=drawing; loading=false; scheduleSave(); refreshChrome() }
    func scheduleSave() {
        guard !loading,!closed else { return }; saving?.cancel(); status.text="Сохраняем…"
        let work=DispatchWorkItem { [weak self] in self?.flush() }; saving=work
        DispatchQueue.main.asyncAfter(deadline:.now()+0.65,execute:work)
    }
    func flush(completion:((Error?)->Void)?=nil) {
        saving?.cancel(); rememberPage(); note.modified=Date(); saveGeneration+=1; let generation=saveGeneration
        store.save(note,drawings:drawings) { [weak self] error in
            if let self,self.saveGeneration == generation { self.status.text=error == nil ? "Сохранено":"Ошибка сохранения" }
            completion?(error)
        }
    }
    @objc func backgrounded() {
        dismissFloating(); canvas.layer.mask=nil; hoverLayer.path=nil
        var task=UIBackgroundTaskIdentifier.invalid
        task=UIApplication.shared.beginBackgroundTask(withName:"Folio autosave") { if task != .invalid { UIApplication.shared.endBackgroundTask(task); task = .invalid } }
        flush { _ in if task != .invalid { UIApplication.shared.endBackgroundTask(task); task = .invalid } }
    }
    @objc func foregrounded() { applyTool(); setNeedsStatusBarAppearanceUpdate() }
    func closeEditor() {
        dismissFloating(); canvas.layer.mask=nil; closed=true
        flush { [weak self] error in
            guard let self,error == nil else { return }
            let rect=DocumentIO.exportRect(self.page,drawing:self.canvas.drawing)
            let image=DocumentIO.image(page:self.page,drawing:self.canvas.drawing,background:self.paper.backgroundImage,rect:rect,scale:min(0.3,600/max(rect.width,rect.height)))
            self.store.setThumbnail(image,note:self.id); self.onClose()
        }
    }
    func gestureRecognizer(_ gestureRecognizer:UIGestureRecognizer,shouldRecognizeSimultaneouslyWith otherGestureRecognizer:UIGestureRecognizer)->Bool { true }
}

/// History is owned by the document so coordinate expansion can also transform undo/redo snapshots.
final class FolioCanvas:PKCanvasView {
    override var undoManager:UndoManager? { nil }
}

final class InkTracker:UIGestureRecognizer {
    var changed:((CGPoint,UITouch.Phase)->Void)?
    var acceptsFinger:(()->Bool)?
    private var tracked:UITouch?
    override func touchesBegan(_ touches:Set<UITouch>,with event:UIEvent) {
        guard tracked == nil,let touch=touches.first(where:{$0.type == .pencil || (acceptsFinger?() == true && $0.type == .direct)}) else { return }
        tracked=touch; state = .began; deliver(touch,event:event,phase:.began)
    }
    override func touchesMoved(_ touches:Set<UITouch>,with event:UIEvent) { guard let tracked,touches.contains(tracked) else { return }; state = .changed; deliver(tracked,event:event,phase:.moved) }
    override func touchesEnded(_ touches:Set<UITouch>,with event:UIEvent) { guard let tracked,touches.contains(tracked) else { return }; deliver(tracked,event:event,phase:.ended); state = .ended }
    override func touchesCancelled(_ touches:Set<UITouch>,with event:UIEvent) { if let tracked { deliver(tracked,event:event,phase:.cancelled) }; state = .cancelled }
    func deliver(_ touch:UITouch,event:UIEvent,phase:UITouch.Phase) {
        guard let host=view?.superview else { return }
        if phase == .moved { for sample in event.coalescedTouches(for:touch) ?? [touch] { changed?(sample.location(in:host),phase) } }
        else { changed?(touch.location(in:host),phase) }
    }
    override func reset() { tracked=nil; super.reset() }
}
