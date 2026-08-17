package com.ravi.freedium.utils.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The interception switches, shared between the listener service and the UI.
 *
 * Both toggles are deliberately exposed rather than hard-coded: the interesting part of
 * this experiment is watching the shade behave differently as you flip them.
 */
object FreediumPrefs {

    private const val FILE = "freedium_prefs"
    private const val KEY_SHADOW = "shadow_notification_enabled"
    private const val KEY_CANCEL_ORIGINAL = "cancel_original_enabled"
    private const val KEY_AUTO_PROBE = "auto_probe_enabled"
    private const val KEY_CAPTURE_ALL = "capture_all_packages"
    private const val KEY_SECURE_SCREEN = "secure_screen"

    private var prefs: SharedPreferences? = null

    private val _shadowEnabled = MutableStateFlow(true)
    private val _cancelOriginalEnabled = MutableStateFlow(false)
    private val _autoProbeEnabled = MutableStateFlow(true)
    private val _captureAllPackages = MutableStateFlow(false)
    private val _secureScreen = MutableStateFlow(false)

    /** Mirror captured Medium notifications with a Freedium-owned one. */
    val shadowEnabled: StateFlow<Boolean> = _shadowEnabled.asStateFlow()

    /** Also dismiss Medium's original notification, leaving only ours in the shade. */
    val cancelOriginalEnabled: StateFlow<Boolean> = _cancelOriginalEnabled.asStateFlow()

    /**
     * Probe the contentIntent automatically when a Medium notification arrives with no
     * URL in it, instead of waiting to be asked.
     *
     * On by default, and it has to be: a PendingIntent is held in memory only, so it dies
     * with the app process. Waiting for the user to open the app and press a button means
     * that by then there is usually nothing left to probe. The only reliable moment to
     * recover the link is the instant the notification arrives. The listener only
     * honours it where the resulting activity launch can be suppressed - otherwise every
     * incoming notification would yank the Medium app to the foreground.
     */
    val autoProbeEnabled: StateFlow<Boolean> = _autoProbeEnabled.asStateFlow()

    /**
     * Capture every app's notifications instead of only Medium's. Off by default - the
     * first iteration only cares about Medium, and capturing everything makes the list
     * unusable. Kept as an escape hatch for poking at other apps' notifications.
     */
    val captureAllPackages: StateFlow<Boolean> = _captureAllPackages.asStateFlow()

    /**
     * Sets FLAG_SECURE, which blocks screenshots, screen recording and the recents
     * thumbnail. This screen lists other apps' notification content, so it is worth
     * hiding from the app switcher - but it is off by default because it also blocks the
     * screenshots that make this app useful to poke at.
     */
    val secureScreen: StateFlow<Boolean> = _secureScreen.asStateFlow()


    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        _shadowEnabled.value = store.getBoolean(KEY_SHADOW, true)
        _cancelOriginalEnabled.value = store.getBoolean(KEY_CANCEL_ORIGINAL, false)
        _autoProbeEnabled.value = store.getBoolean(KEY_AUTO_PROBE, true)
        _captureAllPackages.value = store.getBoolean(KEY_CAPTURE_ALL, false)
        _secureScreen.value = store.getBoolean(KEY_SECURE_SCREEN, false)
    }

    fun setShadowEnabled(enabled: Boolean) {
        _shadowEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_SHADOW, enabled)?.apply()
    }

    fun setCancelOriginalEnabled(enabled: Boolean) {
        _cancelOriginalEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_CANCEL_ORIGINAL, enabled)?.apply()
    }

    fun setAutoProbeEnabled(enabled: Boolean) {
        _autoProbeEnabled.value = enabled
        prefs?.edit()?.putBoolean(KEY_AUTO_PROBE, enabled)?.apply()
    }

    fun setSecureScreen(enabled: Boolean) {
        _secureScreen.value = enabled
        prefs?.edit()?.putBoolean(KEY_SECURE_SCREEN, enabled)?.apply()
    }

    fun setCaptureAllPackages(enabled: Boolean) {
        _captureAllPackages.value = enabled
        prefs?.edit()?.putBoolean(KEY_CAPTURE_ALL, enabled)?.apply()
    }
}
