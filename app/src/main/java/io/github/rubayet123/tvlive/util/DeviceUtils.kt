package io.github.rubayet123.tvlive.util

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration

object DeviceUtils {

    /**
     * Returns true if the device is running on an Android TV / Google TV.
     */
    fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    /**
     * Returns true if the device is a mobile phone or tablet (touchscreen device).
     */
    fun isMobileDevice(context: Context): Boolean {
        return !isTvDevice(context)
    }

    /**
     * Returns true if the device is a tablet (smallest width >= 600dp).
     */
    fun isTablet(context: Context): Boolean {
        val smallestWidth = context.resources.configuration.smallestScreenWidthDp
        return smallestWidth >= 600
    }

    /**
     * Locks orientation to landscape on Android TV, while allowing dynamic sensor rotation
     * (portrait & landscape) on Mobile phones and Tablets.
     */
    fun setupOrientationForDevice(activity: Activity) {
        if (isTvDevice(activity)) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
