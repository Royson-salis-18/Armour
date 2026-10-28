package com.armor.app.ui

/**
 * ARMOR's mark, as SVG path data in a 24 x 28 box: a wheel set inside a location pin. The wheel has a tyre ring and thin
 * spokes; two of its spokes and a rim chord are lit up to spell the letter A. Kept as path data so the docs previews and
 * the app draw the same shape.
 */
object Art {
    /** The mark lives in a 68 x 91 box whose top-left is (16, 6): a pin, an open rim, and an A whose legs run out of the rim. */
    const val LOGO_PIN = "M50 97 C50 97 16 64 16 40 A34 34 0 1 1 84 40 C84 64 50 97 50 97 Z"
    const val LOGO_RIM = "M59.4 60.2 A22 22 0 1 0 40.6 60.2"
    const val LOGO_A = "M59.4 60.2 L50 25 L40.6 60.2 M44.4 47 L55.6 47"
    const val LOGO_STROKE = 5f
}
