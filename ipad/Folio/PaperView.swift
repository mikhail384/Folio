import UIKit

final class PaperView: UIView {
    var page = CanvasPage() { didSet { setNeedsDisplay() } }
    var offset = CGPoint.zero { didSet { setNeedsDisplay() } }
    var zoom: CGFloat = 1 { didSet { setNeedsDisplay() } }
    var backgroundImage: UIImage? { didSet { setNeedsDisplay() } }
    override init(frame: CGRect) { super.init(frame: frame); isOpaque = true; isUserInteractionEnabled = false; contentMode = .redraw }
    required init?(coder: NSCoder) { fatalError() }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        page.paper.color.ui.setFill(); c.fill(bounds)
        c.saveGState(); c.translateBy(x: -offset.x, y: -offset.y); c.scaleBy(x: zoom, y: zoom)
        let visible = CGRect(x: offset.x / zoom, y: offset.y / zoom, width: bounds.width / zoom, height: bounds.height / zoom)
        Self.paint(c, rect: visible, paper: page.paper, origin: CGPoint(x: page.originX, y: page.originY), unitScale: zoom)
        if let image = backgroundImage { image.draw(in: CGRect(x: 0, y: 0, width: page.paper.width, height: page.paper.height)) }
        c.restoreGState()
    }
    static func paint(_ c: CGContext, rect: CGRect, paper: PaperStyle, origin: CGPoint, unitScale: CGFloat) {
        guard paper.pattern != .blank else { return }
        var spacing = CGFloat(paper.spacing)
        while spacing * unitScale < 12 { spacing *= 2 }
        guard spacing.isFinite && spacing > 0 && rect.width.isFinite && rect.height.isFinite else { return }
        let x0 = floor((rect.minX - origin.x) / spacing) * spacing + origin.x
        let y0 = floor((rect.minY - origin.y) / spacing) * spacing + origin.y
        c.setStrokeColor(paper.lines.ui.cgColor); c.setFillColor(paper.lines.ui.cgColor)
        c.setLineWidth(0.65 / max(unitScale, 0.01))
        let nx = min(600, Int(ceil(rect.width / spacing)) + 2), ny = min(600, Int(ceil(rect.height / spacing)) + 2)
        if paper.pattern == .dots {
            let radius: CGFloat = 1.05 / max(unitScale, 0.2)
            for x in 0..<nx { for y in 0..<ny { c.fillEllipse(in: CGRect(x: x0 + CGFloat(x) * spacing - radius, y: y0 + CGFloat(y) * spacing - radius, width: radius * 2, height: radius * 2)) } }
        } else {
            if paper.pattern == .grid { for i in 0..<nx { let x = x0 + CGFloat(i) * spacing; c.move(to: CGPoint(x: x, y: rect.minY)); c.addLine(to: CGPoint(x: x, y: rect.maxY)) } }
            for i in 0..<ny { let y = y0 + CGFloat(i) * spacing; c.move(to: CGPoint(x: rect.minX, y: y)); c.addLine(to: CGPoint(x: rect.maxX, y: y)) }
            c.strokePath()
        }
    }
}
