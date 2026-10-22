import UIKit
import PencilKit

struct GuideState {
    var compass=false
    var center:CGPoint?
    var radius:CGFloat=100
    var choosingCenter=false
}

extension EditorController {
    func pencilInteractionDidTap(_ interaction:UIPencilInteraction) { perform(store.preferences.doubleTap,isSqueeze:false) }
    @available(iOS 17.5, *)
    func pencilInteraction(_ interaction:UIPencilInteraction,didReceiveTap tap:UIPencilInteraction.Tap) {
        if let pose=tap.hoverPose { hoveredPoint=pose.location }; perform(store.preferences.doubleTap,isSqueeze:false)
    }
    @available(iOS 17.5, *)
    func pencilInteraction(_ interaction:UIPencilInteraction,didReceiveSqueeze squeeze:UIPencilInteraction.Squeeze) {
        if squeeze.phase == .ended { if let pose=squeeze.hoverPose { hoveredPoint=pose.location }; perform(store.preferences.squeeze,isSqueeze:true) }
    }
    func perform(_ command:PencilCommand,isSqueeze:Bool) {
        guard presentedViewController == nil,!interacting else { return }
        var effective=command
        if command == .system {
            var preferred=UIPencilInteraction.preferredTapAction
            if isSqueeze { if #available(iOS 17.5, *) { preferred=UIPencilInteraction.preferredSqueezeAction } }
            switch preferred {
            case .switchEraser: effective = .eraser
            case .switchPrevious: select(previousTool); return
            case .showColorPalette: effective = .palette
            default:
                if #available(iOS 17.5, *),preferred == .showContextualPalette { effective = .palette }
                else { return }
            }
        }
        switch effective {
        case .eraser: select(tool == .eraser ? eraserReturnTool:.eraser)
        case .nextTool:
            let tools=store.preferences.tools.filter { $0 != .hand && $0 != .lasso }
            if !tools.isEmpty { select(tools[((tools.firstIndex(of:tool) ?? -1)+1)%tools.count]) }
        case .palette: showRadial()
        case .nextColor:
            guard tool.hasColor else { return }; var style=store.preferences.style(for:tool); style.selected=(style.selected+1)%3
            store.preferences.styles[tool.rawValue]=style; store.savePreferences(); applyTool(); refreshChrome()
        case .undo: undoInk()
        case .none,.system: break
        }
    }
    func showRadial() {
        if radial != nil { dismissFloating(); return }
        dismissFloating(); canvas.layer.mask=nil
        let point=hoveredPoint ?? CGPoint(x:view.bounds.midX,y:view.bounds.midY)
        let menu=RadialPalette(tools:store.preferences.tools,selected:tool,preferences:store.preferences)
        menu.frame=view.bounds; menu.autoresizingMask=[.flexibleWidth,.flexibleHeight]
        menu.centerPoint=CGPoint(x:min(max(168,point.x),view.bounds.width-168),y:min(max(168,point.y),view.bounds.height-168))
        menu.selectTool={ [weak self] tool in self?.select(tool) }
        menu.selectColor={ [weak self] index in
            guard let self,self.tool.hasColor else { return }; var s=self.store.preferences.style(for:self.tool); s.selected=index
            self.store.preferences.styles[self.tool.rawValue]=s; self.store.savePreferences(); self.applyTool(); self.refreshChrome(); self.dismissFloating()
        }
        menu.close={ [weak self] in self?.dismissFloating() }
        view.addSubview(menu); radial=menu; menu.reveal()
    }
    @objc func hovered(_ recognizer:UIHoverGestureRecognizer) {
        guard store.preferences.showHover,radial == nil,floating == nil,presentedViewController == nil else { return }
        if recognizer.state == .ended || recognizer.state == .cancelled { hoverLayer.path=nil; canvas.layer.mask=nil; hoveredPoint=nil; return }
        let point=recognizer.location(in:view); hoveredPoint=point
        guard !penCase.frame.contains(point),!top.frame.contains(point),!bottom.frame.contains(point),tool != .hand,tool != .lasso else { hoverLayer.path=nil; canvas.layer.mask=nil; return }
        var roll:CGFloat=0
        if #available(iOS 17.5, *) { roll=recognizer.rollAngle }
        updateCursor(point,roll:roll,preview:true)
    }
    func updateCursor(_ point:CGPoint,roll:CGFloat=0,preview:Bool) {
        let diameter=max(2,CGFloat(store.preferences.style(for:tool).width)*canvas.zoomScale)
        let path:UIBezierPath
        if tool == .marker { path=UIBezierPath(roundedRect:CGRect(x:-diameter/2,y:-diameter*0.2,width:diameter,height:diameter*0.4),cornerRadius:1) }
        else if tool == .brush { path=UIBezierPath(ovalIn:CGRect(x:-diameter/2,y:-diameter*0.18,width:diameter,height:diameter*0.36)) }
        else { path=UIBezierPath(ovalIn:CGRect(x:-diameter/2,y:-diameter/2,width:diameter,height:diameter)) }
        path.apply(CGAffineTransform(rotationAngle:roll)); path.apply(CGAffineTransform(translationX:point.x,y:point.y))
        CATransaction.begin(); CATransaction.setDisableActions(true)
        hoverLayer.path=path.cgPath
        if tool == .eraser && preview && !store.preferences.strokeEraser {
            // A viewport mask previews exactly the circular area. Stored PKDrawing is never touched.
            let position=canvas.convert(point,from:view)
            let mask=UIBezierPath(rect:canvas.bounds)
            mask.append(UIBezierPath(ovalIn:CGRect(x:position.x-diameter/2,y:position.y-diameter/2,width:diameter,height:diameter)))
            hoverMask.path=mask.cgPath; hoverMask.fillRule = .evenOdd; canvas.layer.mask=hoverMask
        } else { canvas.layer.mask=nil }
        CATransaction.commit()
    }
    func toggleRuler() {
        guide=GuideState(); canvas.isRulerActive.toggle(); applyTool(); drawGuide()
    }
    func toggleCompass() {
        canvas.isRulerActive=false; guide.compass.toggle(); guide.center=nil; guide.choosingCenter=false
        applyTool(); drawGuide()
    }
    func drawGuide() {
        guard guide.compass else { guideLayer.path=nil; return }
        let center=guide.center ?? CGPoint(x:view.bounds.midX,y:view.bounds.midY)
        let path=UIBezierPath(ovalIn:CGRect(x:center.x-guide.radius,y:center.y-guide.radius,width:guide.radius*2,height:guide.radius*2))
        path.move(to:CGPoint(x:center.x-8,y:center.y)); path.addLine(to:CGPoint(x:center.x+8,y:center.y))
        path.move(to:CGPoint(x:center.x,y:center.y-8)); path.addLine(to:CGPoint(x:center.x,y:center.y+8))
        guideLayer.path=path.cgPath; guideLayer.lineDashPattern=[4,5]
        status.text=guide.center == nil ? "Циркуль: коснитесь центра":"Циркуль: ведите перо по нужной дуге"
    }
    func track(_ point:CGPoint,phase:UITouch.Phase) {
        if phase == .began { canvas.layer.mask=nil; dismissFloating() }
        if tool == .eraser { updateCursor(point,preview:false); if phase == .ended || phase == .cancelled { hoverLayer.path=nil }; return }
        guard tool == .laser || guide.compass else { return }
        if phase == .began {
            beforeStroke=canvas.drawing; interacting=true; customPoints=[]; sweep=0
            if guide.compass {
                if guide.center == nil { guide.center=point; guide.choosingCenter=true; drawGuide(); return }
                guide.choosingCenter=false
                let center=guide.center!; guide.radius=max(8,hypot(point.x-center.x,point.y-center.y)); startAngle=atan2(point.y-center.y,point.x-center.x); lastAngle=startAngle
                drawGuide()
            }
            laserLayer.removeAllAnimations(); laserLayer.opacity=1
        }
        if phase == .cancelled { laserLayer.path=nil; customPoints=[]; interacting=false; return }
        if guide.choosingCenter { if phase == .ended { interacting=false }; return }
        if guide.compass,let center=guide.center {
            let angle=atan2(point.y-center.y,point.x-center.x)
            var delta=angle-lastAngle; if delta > .pi { delta-=2 * .pi }; if delta < -.pi { delta+=2 * .pi }
            sweep+=delta; lastAngle=angle
            customPoints=StrokeFactory.arc(center:center,radius:guide.radius,start:startAngle,sweep:sweep)
        } else { customPoints.append(point) }
        let path=UIBezierPath(); if let first=customPoints.first { path.move(to:first); for p in customPoints.dropFirst() { path.addLine(to:p) } }
        CATransaction.begin(); CATransaction.setDisableActions(true); laserLayer.path=path.cgPath
        laserLayer.strokeColor=(tool == .laser ? UIColor.systemRed:store.preferences.style(for:tool).color).cgColor
        laserLayer.lineWidth=tool == .laser ? 3:CGFloat(store.preferences.style(for:tool).width)*canvas.zoomScale
        laserLayer.shadowOpacity=tool == .laser ? 0.9:0; CATransaction.commit()
        if phase == .ended {
            interacting=false
            if tool == .laser {
                let fade=CABasicAnimation(keyPath:"opacity"); fade.fromValue=1; fade.toValue=0; fade.duration=0.65; laserLayer.opacity=0; laserLayer.add(fade,forKey:"fade")
            } else if guide.compass && customPoints.count>1 && tool.hasColor {
                let style=store.preferences.style(for:tool)
                let stroke=StrokeFactory.stroke(points:customPoints.map(world),tool:tool,color:style.color,width:style.width)
                history.record(beforeStroke); loading=true; canvas.drawing=PKDrawing(strokes:canvas.drawing.strokes+[stroke]); loading=false
                laserLayer.path=nil; scheduleSave(); refreshChrome()
            }
        }
    }
}

final class RadialPalette:UIView {
    let tools:[FolioTool]
    let selected:FolioTool
    let preferences:FolioPreferences
    var centerPoint=CGPoint.zero
    var selectTool:((FolioTool)->Void)?
    var selectColor:((Int)->Void)?
    var close:(()->Void)?
    let disk=UIView()
    var page=0
    init(tools:[FolioTool],selected:FolioTool,preferences:FolioPreferences) {
        self.tools=tools; self.selected=selected; self.preferences=preferences; super.init(frame:.zero)
        backgroundColor=UIColor.black.withAlphaComponent(0.035)
        addSubview(disk); FolioTheme.glass(disk); disk.layer.cornerRadius=155
        let tap=UITapGestureRecognizer(target:self,action:#selector(outside(_:))); tap.cancelsTouchesInView=false; addGestureRecognizer(tap)
    }
    required init?(coder:NSCoder) { fatalError() }
    override func layoutSubviews() { super.layoutSubviews(); disk.frame=CGRect(x:centerPoint.x-155,y:centerPoint.y-155,width:310,height:310); layoutItems() }
    func layoutItems() {
        disk.subviews.forEach { $0.removeFromSuperview() }
        let from=page*8,items=Array(tools.dropFirst(from).prefix(8))
        for (i,tool) in items.enumerated() {
            let angle=CGFloat(i)*2 * .pi/CGFloat(max(items.count,1)) - .pi/2
            let button=UIButton(type:.custom); button.frame=CGRect(x:128+cos(angle)*111,y:128+sin(angle)*111,width:54,height:54)
            button.layer.cornerRadius=27; button.layer.borderWidth=tool == selected ? 2:0; button.layer.borderColor=FolioTheme.accent.cgColor
            button.backgroundColor=tool == selected ? FolioTheme.accent.withAlphaComponent(0.1):.clear
            let glyph=ToolGlyph(tool:tool,color:preferences.style(for:tool).color); glyph.frame=button.bounds.offsetBy(dx:0,dy:tool == selected ? -3:0); button.addSubview(glyph)
            button.accessibilityLabel=tool.title; button.addAction(UIAction { _ in self.selectTool?(tool) },for:.touchUpInside)
            let hover=UIHoverGestureRecognizer(target:self,action:#selector(highlight(_:))); button.addGestureRecognizer(hover); disk.addSubview(button)
        }
        let style=preferences.style(for:selected)
        for i in 0..<3 {
            let button=UIButton(type:.custom); button.frame=CGRect(x:98+CGFloat(i)*42,y:126,width:30,height:30); button.layer.cornerRadius=15
            button.backgroundColor=style.colors[i].ui; button.layer.borderWidth=i == style.selected ? 3:1; button.layer.borderColor=(i == style.selected ? FolioTheme.accent:UIColor.white).cgColor
            button.alpha=selected.hasColor ? 1:0.25; button.isEnabled=selected.hasColor; button.accessibilityLabel="Цвет \(i+1)"
            button.addAction(UIAction { _ in self.selectColor?(i) },for:.touchUpInside)
            let hover=UIHoverGestureRecognizer(target:self,action:#selector(highlight(_:))); button.addGestureRecognizer(hover); disk.addSubview(button)
        }
        let next=UIButton(type:.system); next.frame=CGRect(x:110,y:174,width:90,height:30); next.setTitle(tools.count>8 ? "\(page+1) / \((tools.count+7)/8)  ›":"Пенал",for:.normal); next.tintColor=FolioTheme.accent
        next.addAction(UIAction { _ in self.page=(self.page+1)%max(1,(self.tools.count+7)/8); self.layoutItems() },for:.touchUpInside); disk.addSubview(next)
    }
    func reveal() { disk.transform=CGAffineTransform(scaleX:0.2,y:0.2); disk.alpha=0; FolioTheme.animate { self.disk.transform = .identity; self.disk.alpha=1 } }
    @objc func outside(_ tap:UITapGestureRecognizer) { if !disk.frame.contains(tap.location(in:self)) { close?() } }
    @objc func highlight(_ hover:UIHoverGestureRecognizer) {
        guard let button=hover.view else { return }; let active=hover.state != .ended && hover.state != .cancelled
        FolioTheme.animate { button.transform=active ? CGAffineTransform(translationX:0,y:-4).scaledBy(x:1.08,y:1.08):.identity; button.layer.borderWidth=active ? 2:0; button.layer.borderColor=FolioTheme.accent.cgColor }
    }
}
