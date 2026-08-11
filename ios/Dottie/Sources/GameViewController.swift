import SpriteKit
import UIKit

/// Minimaler UIKit-Host für die SpriteKit-Szene — kein Storyboard.
/// Portrait-only, Statusbar aus, Home-Indicator gedimmt.
final class GameViewController: UIViewController {

    private var scenePresented = false

    override func loadView() {
        view = SKView(frame: UIScreen.main.bounds)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Erst hier stimmen Bounds und Safe-Area — die Szene wird genau
        // einmal mit der finalen Größe aufgebaut (portrait-only).
        guard !scenePresented, let skView = view as? SKView,
              skView.bounds.width > 0, skView.bounds.height > 0 else {
            return
        }
        scenePresented = true
        let scene = GameScene(size: skView.bounds.size)
        scene.scaleMode = .resizeFill
        // Sibling-Reihenfolge respektieren: Die Overlays verlassen sich
        // bei gleicher zPosition auf die Einfüge-Reihenfolge.
        skView.ignoresSiblingOrder = false
        skView.presentScene(scene)
    }

    override var prefersStatusBarHidden: Bool {
        return true
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        return .portrait
    }

    override var prefersHomeIndicatorAutoHidden: Bool {
        return true
    }
}
