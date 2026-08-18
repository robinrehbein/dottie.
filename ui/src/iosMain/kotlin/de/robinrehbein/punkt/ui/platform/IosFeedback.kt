package de.robinrehbein.punkt.ui.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/**
 * [GameFeedback] ueber die Taptic Engine.
 *
 * Android baut seine Muster aus Zeiten und Staerken selbst
 * (`VibrationEffect.createWaveform`); iOS gibt stattdessen fertige
 * Staerken vor. Deshalb ist das hier keine Uebersetzung derselben Zahlen,
 * sondern dieselbe Absicht in der Sprache der Plattform: Ein Treffer ist
 * leicht, ein Perfekt-Treffer mittel, ein Tod schwer.
 */
class IosFeedback : GameFeedback {

    private val light = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notice = UINotificationFeedbackGenerator()

    override fun score() = light.impactOccurred()

    override fun perfect() = medium.impactOccurred()

    override fun unlock() =
        notice.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)

    override fun death() = heavy.impactOccurred()

    override fun thud() = medium.impactOccurred()

    override fun newRecord() =
        notice.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
}
