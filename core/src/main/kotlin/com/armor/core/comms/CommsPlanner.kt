package com.armor.core.comms

/** Which transport carries rider-to-rider voice right now. Safety logic never depends on this. */
enum class VoiceRoute {
    /** Internet available: software group call, Bluetooth earphones act as plain audio endpoints. */
    MOBILE_NETWORK,

    /** Offline with a helmet intercom paired: the intercom's own protocol carries the voice. */
    HELMET_INTERCOM,

    /** Offline and no intercom: no voice link; GPS tracking and safety monitoring still run. */
    UNAVAILABLE,
}

object CommsPlanner {
    fun select(online: Boolean, intercomConnected: Boolean): VoiceRoute = when {
        online -> VoiceRoute.MOBILE_NETWORK
        intercomConnected -> VoiceRoute.HELMET_INTERCOM
        else -> VoiceRoute.UNAVAILABLE
    }
}
