// Preferencias del onboarding
package com.aural.player.data

import android.content.Context

object OnboardingPrefs {
    private const val PREFS_NAME = "aural_onboarding"
    private const val KEY_SEEN = "onboarding_seen"

    fun isSeen(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SEEN, false)
    }

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SEEN, true)
            .apply()
    }
}
