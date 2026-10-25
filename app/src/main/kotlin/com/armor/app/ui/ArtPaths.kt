package com.armor.app.ui

/**
 * ARMOR's mark, as SVG path data in a 24 x 28 box: a wheel set inside a location pin. The wheel has a tyre ring and thin
 * spokes; two of its spokes and a rim chord are lit up to spell the letter A. Kept as path data so the docs previews and
 * the app draw the same shape.
 */
object Art {
    const val LOGO_PIN = "M12 1 C6.2 1 2 5.3 2 10.8 C2 17.5 12 27 12 27 C12 27 22 17.5 22 10.8 C22 5.3 17.8 1 12 1 Z"
    const val LOGO_CX = 12f
    const val LOGO_CY = 10.8f
    const val LOGO_FACE_R = 7.8f
    const val LOGO_TYRE_R = 7.0f
    const val LOGO_SPOKES = "M12 10.8 L18.18 12.46 M12 10.8 L13.66 16.98 M12 10.8 L7.47 15.33 M12 10.8 L5.82 9.14 M12 10.8 L10.34 4.62 M12 10.8 L16.53 6.27"
    const val LOGO_A = "M8.6 15.9 L12 4.6 L15.4 15.9 M9.6 12.6 L14.4 12.6"
}
