package com.ravi.freedium.utils.log

import android.util.Log
import com.ravi.freedium.BuildConfig

/**
 * Logging that goes silent in release builds.
 *
 * This app deliberately logs a lot - other apps' notification titles, article URLs, whole
 * flattened Intents - because that visibility is the point of the experiment. All of it is
 * personal data belonging to whoever the notifications are about, and logcat is readable by
 * adb and by anything holding READ_LOGS. So the verbosity stays in debug builds, where it
 * is useful, and is compiled out of release ones.
 *
 * Because [BuildConfig.DEBUG] is a compile-time constant, R8 removes these calls and the
 * string concatenation that feeds them from release builds entirely - the sensitive text
 * never even reaches the binary.
 */
object FreediumLog {

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    /**
     * Errors survive into release, but only as a bare tag and message the caller controls -
     * never a payload. Pass a description, not the data that caused it.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
