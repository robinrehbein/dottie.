import DottieUi
import UIKit

/// Haengt die geteilte Compose-Oberflaeche in die App.
///
/// Bis v2.24 stand hier eine SKView mit einer eigenen SpriteKit-Szene —
/// 2 900 Zeilen Swift, die dieselbe Oberflaeche bauten, die die
/// Android-App in Compose zeichnet. Jetzt kommt sie aus `:ui`, und diese
/// Datei ist nur noch die Klammer darum.
final class GameViewController: UIViewController {

    private lazy var compose: UIViewController = MainViewControllerKt.MainViewController()

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)
    }

    override var prefersStatusBarHidden: Bool { true }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }
}
