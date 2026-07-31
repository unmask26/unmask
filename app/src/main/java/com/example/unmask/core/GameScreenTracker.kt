package com.example.unmask.core

object GameScreenTracker {
    @Volatile
    var isAppInForeground: Boolean = false

    @Volatile
    var isGameTabSelected: Boolean = false

    val isGameScreenActive: Boolean
        get() = isAppInForeground && isGameTabSelected
}
