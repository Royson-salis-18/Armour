package com.armor.app

import android.content.Context

/**
 * `Context.getSystemService(Class)` is declared nullable by recent SDKs. Every manager used here exists on any
 * phone that can run the app, so a null is a broken device and failing loudly is right.
 */
inline fun <reified T : Any> Context.requireService(): T =
    getSystemService(T::class.java) ?: error("${T::class.java.simpleName} is not available on this device")
