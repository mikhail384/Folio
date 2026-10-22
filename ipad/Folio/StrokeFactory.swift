import PencilKit
import UIKit

enum StrokeFactory {
    static func stroke(points: [CGPoint], tool: FolioTool, color: UIColor, width: CGFloat) -> PKStroke {
        let controls = points.enumerated().map { i, p in PKStrokePoint(location: p, timeOffset: Double(i) / 120, size: CGSize(width: width, height: width), opacity: 1, force: 1, azimuth: 0, altitude: .pi / 2) }
        return PKStroke(ink: PKInk(tool.inkType, color: color), path: PKStrokePath(controlPoints: controls, creationDate: Date()))
    }
    static func arc(center: CGPoint, radius: CGFloat, start: CGFloat, sweep: CGFloat) -> [CGPoint] {
        let count = max(2, min(8192, Int(abs(sweep) * max(radius, 1) / 2)))
        return (0...count).map { i in let a = start + sweep * CGFloat(i) / CGFloat(count); return CGPoint(x: center.x + radius * cos(a), y: center.y + radius * sin(a)) }
    }
    static func snappedAngle(_ radians: CGFloat) -> CGFloat {
        let degrees = radians * 180 / .pi
        let candidates = stride(from: -360, through: 360, by: 15).filter { d in let a = abs(d) % 90; return a == 0 || a == 30 || a == 45 || a == 60 }
        let closest = candidates.min { abs(CGFloat($0) - degrees) < abs(CGFloat($1) - degrees) } ?? 0
        return abs(CGFloat(closest) - degrees) < 3 ? CGFloat(closest) * .pi / 180 : radians
    }
}
