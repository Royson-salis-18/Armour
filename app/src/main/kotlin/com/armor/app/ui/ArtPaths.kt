package com.armor.app.ui

/**
 * ARMOR's mark, as SVG path data in a 24 x 28 box: a location pin holding a wheel rim, with the letter A set inside
 * the rim so its legs read as spokes. Kept as path data so the docs previews and the app draw the same shape.
 */
object Art {
    const val LOGO_PIN = "M12 1 C6.2 1 2 5.3 2 10.8 C2 17.5 12 27 12 27 C12 27 22 17.5 22 10.8 C22 5.3 17.8 1 12 1 Z"
    const val LOGO_RIM_CX = 12f
    const val LOGO_RIM_CY = 10.8f
    const val LOGO_RIM_R = 7.2f
    const val LOGO_A = "M8.2 15.4 L12 6.2 L15.8 15.4 M9.9 12.2 L14.1 12.2"
}
