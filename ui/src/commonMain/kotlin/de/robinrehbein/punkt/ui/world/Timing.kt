package de.robinrehbein.punkt.ui.world

// Zeitkonstanten der Effekte. Sie gehoeren zur Darstellung, nicht zur
// Spielregel — deshalb hier und nicht in :core.

/** Dauer der Freischalt-Zelebration (goldener Ring + Schimmer). */
const val CELEBRATE_SECONDS = 1.1f

/**
 * Mario-Tod: Nach dem Todes-Freeze hüpft der Vogel mit dieser
 * Anfangsgeschwindigkeit nach oben und fällt dann mit der Gravitation
 * unten aus dem Bild — beides in Bildhöhen pro Sekunde(²).
 */
const val DEATH_HOP_SPEED = 1.6f
const val DEATH_GRAVITY = 6f

/**
 * Während des Hüpfers dreht sich der Vogel um 180° auf den Rücken und
 * fällt kopfüber — die Drehung ist am Scheitelpunkt (~0,27s) fertig.
 */
const val DEATH_FLIP_SECONDS = 0.3f

/**
 * Tod durch die Bomben: Der Vogel platzt in [BURST_OUTER] äußere und
 * [BURST_INNER] innere Stücke (Grenze [BURST_INNER_RADIUS] Sprite-Pixel
 * von der Mitte) plus Kern. Sie fliegen mit [BURST_SPEED] aus der Mitte, bekommen [BURST_HOP]
 * nach oben (beides Bildhöhen pro Sekunde) und blassen ab
 * [BURST_FADE_START] bis [BURST_SECONDS] aus.
 */
const val BURST_OUTER = 12
const val BURST_INNER = 6
const val BURST_INNER_RADIUS = 3.8f
const val BURST_SPEED = 0.35f
const val BURST_HOP = 0.8f
const val BURST_FADE_START = 0.6f
const val BURST_SECONDS = 1.2f
