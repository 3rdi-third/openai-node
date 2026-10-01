import SwiftUI

struct Knob: View {
    @Binding var value: Double
    let range: ClosedRange<Double>
    let label: String
    var size: CGFloat = 58
    var color: Color = .orange
    @State private var start: Double?

    private var normalized: Double {
        (value - range.lowerBound) / (range.upperBound - range.lowerBound)
    }

    var body: some View {
        VStack(spacing: 4) {
            ZStack {
                Circle().fill(color)
                Circle().stroke(.black.opacity(0.45), lineWidth: 2)
                Capsule()
                    .fill(.black)
                    .frame(width: 3, height: size * 0.34)
                    .offset(y: -size * 0.19)
                    .rotationEffect(.degrees(-135 + normalized * 270))
            }
            .frame(width: size, height: size)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        if start == nil { start = value }
                        let delta = -Double(g.translation.height) / 140 * (range.upperBound - range.lowerBound)
                        value = min(range.upperBound, max(range.lowerBound, (start ?? value) + delta))
                    }
                    .onEnded { _ in start = nil }
            )
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(.black)
                .lineLimit(1)
        }
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var color: Color = .orange
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.bold())
            .foregroundStyle(.black)
            .frame(maxWidth: .infinity, minHeight: 46)
            .background(color.opacity(configuration.isPressed ? 0.7 : 1), in: RoundedRectangle(cornerRadius: 10))
    }
}
