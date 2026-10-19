package com.oloomyar.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Same foreground-visit policy as Grade 9; internal navigation never restarts it. */
internal object LaunchSplashController {
    var visible by mutableStateOf(true)
        private set
    var epoch by mutableStateOf(0)
        private set
    private var started = 0
    private var configurationChange = false

    fun started() {
        if (started == 0 && !configurationChange) { epoch++; visible = true }
        configurationChange = false
        started++
    }
    fun stopped(changingConfiguration: Boolean) {
        started = (started - 1).coerceAtLeast(0)
        if (started == 0) configurationChange = changingConfiguration
    }
    fun finish(ticket: Int) { if (ticket == epoch) visible = false }
}

class OloomYarApplication : Application(), Application.ActivityLifecycleCallbacks {
    override fun onCreate() { super.onCreate(); registerActivityLifecycleCallbacks(this) }
    override fun onActivityStarted(activity: Activity) = LaunchSplashController.started()
    override fun onActivityStopped(activity: Activity) = LaunchSplashController.stopped(activity.isChangingConfigurations)
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
