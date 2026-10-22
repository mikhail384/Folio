import UIKit
import SwiftUI

enum FolioTheme {
    static let ink = UIColor(red: 0.16, green: 0.20, blue: 0.28, alpha: 1)
    static let accent = UIColor(red: 0.49, green: 0.40, blue: 0.72, alpha: 1)
    static let paper = UIColor(red: 0.97, green: 0.96, blue: 0.94, alpha: 1)
    static func button(_ symbol: String, label: String, action: @escaping () -> Void) -> UIButton {
        let b = UIButton(type: .system)
        b.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .medium)), for: .normal)
        b.tintColor = ink; b.accessibilityLabel = label
        b.addAction(UIAction { _ in action() }, for: .touchUpInside)
        b.widthAnchor.constraint(equalToConstant: 44).isActive = true
        b.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return b
    }
    static func glass(_ view: UIView) {
        view.backgroundColor = UIColor.white.withAlphaComponent(0.94)
        view.layer.cornerRadius = 24; view.layer.cornerCurve = .continuous
        view.layer.shadowColor = ink.cgColor; view.layer.shadowOpacity = 0.10
        view.layer.shadowRadius = 22; view.layer.shadowOffset = CGSize(width: 0, height: 8)
        view.layer.borderColor = UIColor.white.withAlphaComponent(0.8).cgColor; view.layer.borderWidth = 1
    }
    static func animate(_ changes: @escaping () -> Void, completion: ((Bool) -> Void)? = nil) {
        if UIAccessibility.isReduceMotionEnabled { changes(); completion?(true); return }
        UIView.animate(withDuration: 0.42, delay: 0, usingSpringWithDamping: 0.86,
                       initialSpringVelocity: 0.2, options: [.beginFromCurrentState, .allowUserInteraction], animations: changes, completion: completion)
    }
}

/// Original vector miniatures, drawn at display scale and tinted per tool.
final class ToolGlyph: UIView {
    let tool: FolioTool
    var color: UIColor { didSet { setNeedsDisplay() } }
    init(tool: FolioTool, color: UIColor) { self.tool = tool; self.color = color; super.init(frame: .zero); isOpaque = false; isUserInteractionEnabled = false }
    required init?(coder: NSCoder) { fatalError() }
    override func draw(_ rect: CGRect) {
        guard let c = UIGraphicsGetCurrentContext() else { return }
        if ![.pen, .fountain, .pencil, .brush, .marker, .eraser].contains(tool) {
            UIImage(systemName: tool.symbol)?.withTintColor(color).draw(in: bounds.insetBy(dx: 10, dy: 10)); return
        }
        c.saveGState(); c.translateBy(x: bounds.midX, y: bounds.maxY - 2)
        c.rotate(by: -.pi / 12)
        let w: CGFloat = tool == .marker || tool == .eraser ? 15 : 10
        let body = UIBezierPath(roundedRect: CGRect(x: -w / 2, y: -37, width: w, height: 29), cornerRadius: 3)
        color.withAlphaComponent(0.85).setFill(); body.fill()
        UIColor.white.withAlphaComponent(0.35).setFill()
        UIBezierPath(roundedRect: CGRect(x: -w / 2 + 2, y: -34, width: 2, height: 23), cornerRadius: 1).fill()
        let tip = UIBezierPath()
        if tool == .marker || tool == .eraser {
            tip.move(to: CGPoint(x: -w / 2, y: -9)); tip.addLine(to: CGPoint(x: w / 2, y: -9))
            tip.addLine(to: CGPoint(x: w / 2 - 2, y: -2)); tip.addLine(to: CGPoint(x: -w / 2 + 1, y: -4))
        } else {
            tip.move(to: CGPoint(x: -w / 2, y: -8)); tip.addLine(to: CGPoint(x: w / 2, y: -8)); tip.addLine(to: .zero)
        }
        tip.close(); (tool == .eraser ? UIColor.systemPink.withAlphaComponent(0.7) : FolioTheme.ink).setFill(); tip.fill()
        c.restoreGState()
    }
}
