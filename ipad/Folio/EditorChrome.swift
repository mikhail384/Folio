import UIKit
import SwiftUI
import PencilKit

extension EditorController {
    func configureChrome() {
        top.axis = .horizontal; top.spacing=4; top.alignment = .center
        top.addArrangedSubview(FolioTheme.button("chevron.left",label:"Библиотека") { [weak self] in self?.closeEditor() })
        let names=UIStackView(); names.axis = .vertical; names.spacing=2; names.alignment = .leading
        titleButton.titleLabel?.font = .systemFont(ofSize:18,weight:.semibold); titleButton.setTitleColor(FolioTheme.ink,for:.normal)
        titleButton.accessibilityIdentifier="notebookTitle"
        titleButton.addAction(UIAction { [weak self] _ in self?.rename() },for:.touchUpInside)
        status.font = .systemFont(ofSize:10,weight:.medium); status.textColor = .secondaryLabel; status.text="Сохранено"
        names.addArrangedSubview(titleButton); names.addArrangedSubview(status); top.addArrangedSubview(names)
        let spacer=UIView(); spacer.setContentHuggingPriority(.defaultLow,for:.horizontal); top.addArrangedSubview(spacer)
        top.addArrangedSubview(FolioTheme.button("arrow.uturn.backward",label:"Отменить") { [weak self] in self?.undoInk() })
        top.addArrangedSubview(FolioTheme.button("arrow.uturn.forward",label:"Повторить") { [weak self] in self?.redoInk() })
        top.addArrangedSubview(FolioTheme.button("ruler",label:"Линейка") { [weak self] in self?.toggleRuler() })
        top.addArrangedSubview(FolioTheme.button("circle.and.line.horizontal",label:"Циркуль") { [weak self] in self?.toggleCompass() })
        let share=FolioTheme.button("square.and.arrow.up",label:"Экспорт") { }
        share.showsMenuAsPrimaryAction=true
        share.menu=UIMenu(children:[UIAction(title:"Тетрадь Folio iPad",image:UIImage(systemName:"book.closed")) { [weak self] _ in self?.exportDocument("folioipad",anchor:share) },UIAction(title:"PDF · все страницы",image:UIImage(systemName:"doc.richtext")) { [weak self] _ in self?.exportDocument("pdf",anchor:share) },UIAction(title:"PNG · эта страница",image:UIImage(systemName:"photo")) { [weak self] _ in self?.exportDocument("png",anchor:share) }])
        top.addArrangedSubview(share)
        let more=FolioTheme.button("ellipsis",label:"Настройки холста") { [weak self] in self?.openPageSettings() }; more.accessibilityIdentifier="canvasSettings"; top.addArrangedSubview(more)
        view.addSubview(top); top.translatesAutoresizingMaskIntoConstraints=false
        FolioTheme.glass(penCase); view.addSubview(penCase); penCase.translatesAutoresizingMaskIntoConstraints=false
        penCase.addSubview(stripScroll); stripScroll.translatesAutoresizingMaskIntoConstraints=false; stripScroll.showsHorizontalScrollIndicator=false; stripScroll.alwaysBounceHorizontal=true
        strip.axis = .horizontal; strip.spacing=4; strip.alignment = .center
        stripScroll.addSubview(strip); strip.translatesAutoresizingMaskIntoConstraints=false
        colors.axis = .horizontal; colors.spacing=8; colors.alignment = .center
        penCase.addSubview(colors); colors.translatesAutoresizingMaskIntoConstraints=false
        bottom.axis = .horizontal; bottom.spacing=8; bottom.alignment = .center; FolioTheme.glass(bottom)
        bottom.isLayoutMarginsRelativeArrangement=true; bottom.layoutMargins=UIEdgeInsets(top:4,left:8,bottom:4,right:8)
        bottom.addArrangedSubview(FolioTheme.button("chevron.left",label:"Предыдущая страница") { [weak self] in self?.changePage(-1) })
        pageButton.titleLabel?.font = .monospacedDigitSystemFont(ofSize:14,weight:.semibold)
        pageButton.setTitleColor(FolioTheme.ink,for:.normal); pageButton.showsMenuAsPrimaryAction=true; pageButton.accessibilityLabel="Страницы"
        pageButton.widthAnchor.constraint(greaterThanOrEqualToConstant:65).isActive=true; bottom.addArrangedSubview(pageButton)
        bottom.addArrangedSubview(FolioTheme.button("chevron.right",label:"Следующая страница") { [weak self] in self?.changePage(1) })
        bottom.addArrangedSubview(FolioTheme.button("plus",label:"Добавить страницу") { [weak self] in self?.addPage() })
        view.addSubview(bottom); bottom.translatesAutoresizingMaskIntoConstraints=false
        NSLayoutConstraint.activate([
            top.leadingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.leadingAnchor,constant:18),top.trailingAnchor.constraint(equalTo:view.safeAreaLayoutGuide.trailingAnchor,constant:-18),top.topAnchor.constraint(equalTo:view.safeAreaLayoutGuide.topAnchor,constant:10),top.heightAnchor.constraint(equalToConstant:48),
            penCase.centerXAnchor.constraint(equalTo:view.centerXAnchor),penCase.topAnchor.constraint(equalTo:top.bottomAnchor,constant:16),penCase.widthAnchor.constraint(equalTo:view.safeAreaLayoutGuide.widthAnchor,constant:-64),penCase.heightAnchor.constraint(equalToConstant:70),
            colors.trailingAnchor.constraint(equalTo:penCase.trailingAnchor,constant:-16),colors.centerYAnchor.constraint(equalTo:penCase.centerYAnchor),colors.widthAnchor.constraint(equalToConstant:118),
            stripScroll.leadingAnchor.constraint(equalTo:penCase.leadingAnchor,constant:10),stripScroll.trailingAnchor.constraint(equalTo:colors.leadingAnchor,constant:-12),stripScroll.topAnchor.constraint(equalTo:penCase.topAnchor),stripScroll.bottomAnchor.constraint(equalTo:penCase.bottomAnchor),
            strip.leadingAnchor.constraint(equalTo:stripScroll.contentLayoutGuide.leadingAnchor),strip.trailingAnchor.constraint(equalTo:stripScroll.contentLayoutGuide.trailingAnchor),strip.topAnchor.constraint(equalTo:stripScroll.contentLayoutGuide.topAnchor),strip.bottomAnchor.constraint(equalTo:stripScroll.contentLayoutGuide.bottomAnchor),strip.heightAnchor.constraint(equalTo:stripScroll.frameLayoutGuide.heightAnchor),
            bottom.centerXAnchor.constraint(equalTo:view.centerXAnchor),bottom.bottomAnchor.constraint(equalTo:view.safeAreaLayoutGuide.bottomAnchor,constant:-12)
        ])
        for i in 0..<3 {
            let b=UIButton(type:.custom); b.widthAnchor.constraint(equalToConstant:34).isActive=true; b.heightAnchor.constraint(equalToConstant:34).isActive=true
            b.layer.cornerRadius=17; b.layer.borderWidth=3; b.accessibilityLabel="Цвет \(i+1)"; b.tag=i
            b.addAction(UIAction { [weak self] _ in self?.selectColor(i,anchor:b) },for:.touchUpInside)
            let hold=UILongPressGestureRecognizer(target:self,action:#selector(colorHeld(_:))); hold.minimumPressDuration=0.4; b.addGestureRecognizer(hold)
            colors.addArrangedSubview(b); colorButtons.append(b)
        }
    }
    func rebuildTools() {
        for child in strip.arrangedSubviews { strip.removeArrangedSubview(child); child.removeFromSuperview() }
        toolButtons.removeAll(); toolGlyphs.removeAll()
        for tool in store.preferences.tools {
            let b=UIButton(type:.custom); b.accessibilityLabel=tool.title; b.accessibilityIdentifier="tool-"+tool.rawValue
            b.widthAnchor.constraint(equalToConstant:52).isActive=true; b.heightAnchor.constraint(equalToConstant:52).isActive=true; b.layer.cornerRadius=20; b.layer.cornerCurve = .continuous
            let glyph=ToolGlyph(tool:tool,color:store.preferences.style(for:tool).color); glyph.frame=CGRect(x:2,y:2,width:48,height:48); b.addSubview(glyph)
            b.addAction(UIAction { [weak self] _ in guard let self else { return }; if self.tool == tool { self.openToolSettings(anchor:b) } else { self.select(tool) } },for:.touchUpInside)
            let hold=UILongPressGestureRecognizer(target:self,action:#selector(toolHeld(_:))); hold.minimumPressDuration=0.4; b.addGestureRecognizer(hold)
            strip.addArrangedSubview(b); toolButtons[tool]=b; toolGlyphs[tool]=glyph
        }
        refreshChrome()
    }
    func refreshChrome() {
        titleButton.setTitle(note.title,for:.normal); pageButton.setTitle("\(note.current+1) / \(note.pages.count)",for:.normal); pageMenu()
        for (item,b) in toolButtons {
            b.isSelected=item == tool; b.accessibilityTraits=item == tool ? [.button,.selected]:[.button]
            toolGlyphs[item]?.color=store.preferences.style(for:item).color
            FolioTheme.animate { b.backgroundColor=item == self.tool ? FolioTheme.accent.withAlphaComponent(0.12):.clear; b.transform=item == self.tool ? CGAffineTransform(translationX:0,y:-3):.identity }
        }
        let style=store.preferences.style(for:tool)
        for (i,b) in colorButtons.enumerated() {
            b.backgroundColor=style.colors[i].ui; b.layer.borderColor=(i == style.selected ? FolioTheme.accent:UIColor.white).cgColor
            b.isEnabled=tool.hasColor; b.alpha=tool.hasColor ? 1:0.25
            b.accessibilityTraits=i == style.selected ? [.button,.selected]:[.button]
        }
    }
    func select(_ next:FolioTool) {
        if tool != .eraser && next == .eraser { eraserReturnTool=tool }
        if tool != next { previousTool=tool }
        tool=next; dismissFloating(); applyTool(); refreshChrome(); UISelectionFeedbackGenerator().selectionChanged()
    }
    func applyTool() {
        let style=store.preferences.style(for:tool)
        if tool == .eraser { canvas.tool=PKEraserTool(store.preferences.strokeEraser ? .vector:.bitmap,width:style.width) }
        else if tool == .lasso { canvas.tool=PKLassoTool() }
        else { canvas.tool=PKInkingTool(tool.inkType,color:style.color,width:style.width) }
        canvas.drawingGestureRecognizer.isEnabled=tool != .hand && tool != .laser && !guide.compass
        canvas.drawingPolicy=store.preferences.fingerDrawing ? .anyInput:.pencilOnly
        canvas.layer.mask=nil; hoverLayer.path=nil
    }
    @objc func toolHeld(_ gesture:UILongPressGestureRecognizer) {
        guard gesture.state == .began,let anchor=gesture.view,let found=toolButtons.first(where:{$0.value === anchor})?.key else { return }
        select(found); openToolSettings(anchor:anchor)
    }
    func openToolSettings(anchor:UIView) {
        guard tool != .hand && tool != .lasso else { return }
        let edited=tool,style=store.preferences.style(for:edited)
        let content=ToolSettingsView(tool:edited,width:style.width,strokeEraser:store.preferences.strokeEraser) { [weak self] width,stroke in
            guard let self else { return }; var s=self.store.preferences.style(for:edited); s.width=width
            self.store.preferences.styles[edited.rawValue]=s; self.store.preferences.strokeEraser=stroke; self.store.savePreferences(); self.applyTool()
        }
        presentFloating(UIHostingController(rootView:content),size:CGSize(width:310,height:edited == .eraser ? 170:185),anchor:anchor)
    }
    func selectColor(_ index:Int,anchor:UIView) {
        guard tool.hasColor else { return }; var style=store.preferences.style(for:tool)
        if style.selected == index { openColor(index,anchor:anchor); return }
        style.selected=index; store.preferences.styles[tool.rawValue]=style; store.savePreferences(); applyTool(); refreshChrome()
    }
    @objc func colorHeld(_ gesture:UILongPressGestureRecognizer) { if gesture.state == .began,let anchor=gesture.view { openColor(anchor.tag,anchor:anchor) } }
    func openColor(_ index:Int,anchor:UIView) {
        guard tool.hasColor else { return }; dismissFloating(); colorIndex=index
        var style=store.preferences.style(for:tool); style.selected=index; store.preferences.styles[tool.rawValue]=style; refreshChrome()
        let picker=UIColorPickerViewController(); picker.selectedColor=style.colors[index].ui; picker.supportsAlpha=true; picker.delegate=self
        picker.modalPresentationStyle = .popover; picker.preferredContentSize=CGSize(width:330,height:450)
        picker.popoverPresentationController?.sourceView=anchor; picker.popoverPresentationController?.sourceRect=anchor.bounds; picker.popoverPresentationController?.permittedArrowDirections = .up
        present(picker,animated:true)
    }
    func colorPickerViewControllerDidSelectColor(_ viewController:UIColorPickerViewController) {
        var style=store.preferences.style(for:tool); style.colors[colorIndex]=InkColor(viewController.selectedColor)
        store.preferences.styles[tool.rawValue]=style; store.savePreferences(); applyTool(); refreshChrome()
    }
    func presentFloating(_ controller:UIViewController,size:CGSize,anchor:UIView) {
        dismissFloating(); addChild(controller); view.addSubview(controller.view); controller.didMove(toParent:self)
        let source=anchor.convert(anchor.bounds,to:view)
        let x=min(max(16,source.midX-size.width/2),view.bounds.width-size.width-16)
        let y=min(source.maxY+12,view.bounds.height-size.height-30)
        controller.view.frame=CGRect(x:x,y:y,width:size.width,height:size.height); FolioTheme.glass(controller.view)
        controller.view.alpha=0; controller.view.transform=CGAffineTransform(translationX:source.midX-(x+size.width/2),y:source.midY-(y+size.height/2)).scaledBy(x:0.12,y:0.12)
        floating=controller
        FolioTheme.animate { controller.view.alpha=1; controller.view.transform = .identity }
    }
    func dismissFloating() {
        radial?.removeFromSuperview(); radial=nil
        guard let controller=floating else { return }; floating=nil
        controller.willMove(toParent:nil)
        UIView.animate(withDuration:UIAccessibility.isReduceMotionEnabled ? 0:0.16,animations:{ controller.view.alpha=0; controller.view.transform=CGAffineTransform(scaleX:0.9,y:0.9) }) { _ in controller.view.removeFromSuperview(); controller.removeFromParent() }
    }
    func rename() {
        let alert=UIAlertController(title:"Название тетради",message:nil,preferredStyle:.alert)
        alert.addTextField { $0.text=self.note.title; $0.clearButtonMode = .whileEditing }
        alert.addAction(UIAlertAction(title:"Сохранить",style:.default) { _ in self.note.title=self.store.uniqueTitle(alert.textFields?.first?.text ?? "",folder:self.note.folder,excluding:self.id); self.refreshChrome(); self.scheduleSave() })
        alert.addAction(UIAlertAction(title:"Отмена",style:.cancel)); present(alert,animated:true)
    }
    func addPage() {
        rememberPage(); var new=CanvasPage(); new.paper=store.preferences.defaultPaper
        if !new.paper.infinite { new.originX=0; new.originY=0; new.viewportX=0; new.viewportY=0 }
        note.pages.append(new); note.current=note.pages.count-1; drawings[new.id]=PKDrawing(); guide=GuideState(); canvas.isRulerActive=false
        loadPage(); scheduleSave()
    }
    func changePage(_ delta:Int) { let next=note.current+delta; guard note.pages.indices.contains(next) else { return }; rememberPage(); note.current=next; loadPage(); scheduleSave() }
    func pageMenu() {
        let actions=note.pages.enumerated().map { i,p in UIAction(title:"\(p.bookmark ? "★ " : "")Страница \(i+1)",state:i == note.current ? .on:.off) { _ in self.changePage(i-self.note.current) } }
        let bookmark=UIAction(title:page.bookmark ? "Убрать закладку":"Добавить закладку",image:UIImage(systemName:"bookmark")) { _ in var p=self.page; p.bookmark.toggle(); self.page=p; self.scheduleSave() }
        pageButton.menu=UIMenu(children:[bookmark,UIMenu(title:"",options:.displayInline,children:actions)]); pageButton.showsMenuAsPrimaryAction=true
    }
    func openPageSettings() {
        let content=PageSettingsSheet(paper:page.paper) { [weak self] paper in
            guard let self else { return }; var p=self.page
            if p.paper.infinite != paper.infinite {
                let dx:CGFloat=paper.infinite ? 4096:-CGFloat(p.originX),dy:CGFloat=paper.infinite ? 4096:-CGFloat(p.originY)
                let transform=CGAffineTransform(translationX:dx,y:dy)
                self.loading=true; self.canvas.drawing=self.canvas.drawing.transformed(using:transform); self.beforeStroke=self.canvas.drawing; self.history.translate(transform); self.loading=false
                p.originX+=Double(dx); p.originY+=Double(dy); p.viewportX=max(0,p.viewportX+Double(dx)); p.viewportY=max(0,p.viewportY+Double(dy))
            }
            p.paper=paper; self.page=p
            self.canvas.contentSize=paper.infinite ? CGSize(width:p.canvasWidth,height:p.canvasHeight):CGSize(width:paper.width,height:paper.height)
            self.updatePaper(); self.scheduleSave()
        }
        let host=UIHostingController(rootView:content); host.modalPresentationStyle = .popover; host.preferredContentSize=CGSize(width:360,height:430)
        host.popoverPresentationController?.sourceView=top.arrangedSubviews.last; host.popoverPresentationController?.sourceRect=top.arrangedSubviews.last?.bounds ?? .zero
        present(host,animated:true)
    }
    func exportDocument(_ format:String,anchor:UIView) {
        flush { [weak self] error in
            guard let self,error == nil else { return }; self.rememberPage()
            let note=self.note,drawings=self.drawings,directory=self.store.directory(self.id),page=self.page,background=self.paper.backgroundImage
            self.store.io.async {
                do {
                    let url:URL
                    if format == "folioipad" { url=try DocumentIO.zip(directory,title:note.title) }
                    else if format == "pdf" { url=try DocumentIO.pdf(note:note,drawings:drawings,directory:directory) }
                    else {
                        let drawing=drawings[page.id] ?? PKDrawing(),rect=DocumentIO.exportRect(page,drawing:drawings[page.id] ?? PKDrawing())
                        let image=DocumentIO.image(page:page,drawing:drawing,background:background,rect:rect,scale:min(2,4096/max(rect.width,rect.height)))
                        url=FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString+".png")
                        guard let data=image.pngData() else { throw FolioError.invalidDocument("экспорт PNG") }; try data.write(to:url)
                    }
                    DispatchQueue.main.async {
                        let share=UIActivityViewController(activityItems:[url],applicationActivities:nil); share.popoverPresentationController?.sourceView=anchor; share.popoverPresentationController?.sourceRect=anchor.bounds
                        share.completionWithItemsHandler={ _,_,_,_ in try? FileManager.default.removeItem(at:url) }; self.present(share,animated:true)
                    }
                } catch { DispatchQueue.main.async { self.store.error=error.localizedDescription } }
            }
        }
    }
}

struct PageSettingsSheet:View {
    @State var paper:PaperStyle
    let change:(PaperStyle)->Void
    var body:some View { Form { PaperSettingsView(paper:$paper) }.onChange(of:paper) { _,v in change(v) } }
}
