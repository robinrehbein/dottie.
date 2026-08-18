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
