package com.armor.app.ui

/** ARMOR's drawn artwork as SVG path data. Generated from one source so the docs and the app show the same drawing. */
object Art {
    const val LOGO_SHIELD = "M12 1 L22 5 L22 14 C22 20 17.5 24.5 12 27 C6.5 24.5 2 20 2 14 L2 5 Z"
    val LOGO_CHEVRONS = listOf("M7.5 14.5 L12 10 L16.5 14.5", "M7.5 19.5 L12 15 L16.5 19.5")

    class Part(val d: String, val fill: Boolean, val w: Float = 0f)

    /** viewBox 240 x 130. */
    val BIKE_PARTS = listOf(
        Part("M50 88 L104 90", false, 5f),
        Part("M22 73 C30 64 52 61 76 62 C86 63 92 65 98 67 L96 73 C78 73 50 77 30 79 Z", true),
        Part("M92 63 C100 44 126 40 146 50 C151 52 153 56 150 61 C132 67 108 69 94 69 Z", true),
        Part("M146 50 C158 41 171 43 178 52 L172 63 C164 63 155 63 148 62 Z", true),
        Part("M92 69 L136 67 C141 75 141 86 135 95 L100 99 C94 91 92 79 92 69 Z", true),
        Part("M100 94 C80 92 56 90 34 88 C30 88 30 94 34 95 C58 98 82 100 104 102 Z", true),
        Part("M168 58 L192 88", false, 6f),
        Part("M172 68 C186 61 202 67 210 80", false, 5f),
        Part("M66 61 C74 46 92 33 112 28 L124 36 C112 44 100 54 92 65 Z", true),
        Part("M126 27 m-10.5 0 a10.5 10.5 0 1 0 21 0 a10.5 10.5 0 1 0 -21 0", true),
        Part("M118 37 C132 41 145 43 160 47", false, 7f),
        Part("M70 63 C84 63 98 67 107 73 L101 81 C91 77 79 75 68 73 Z", true),
        Part("M104 77 C102 87 98 96 94 104", false, 7f),
        Part("M86 105 L103 105", false, 6f),
    )

    /** (centre x, centre y, radius) in the same viewBox. */
    val BIKE_WHEELS = listOf(Triple(50f, 88f, 26f), Triple(192f, 88f, 26f))
}
