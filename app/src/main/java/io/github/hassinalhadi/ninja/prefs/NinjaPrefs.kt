package io.github.hassinalhadi.ninja.prefs

import android.content.Context
import android.content.SharedPreferences

object NinjaPrefs {
    private const val PREF_NAME = "ninja_open_source_prefs"

    const val KEY_AUTO_CLICKER = "auto_clicker"
    const val KEY_CLICK_SPEED_MS = "click_speed_ms"
    const val KEY_SHOW_SUPPORT = "show_support"
    const val KEY_CUSTOMER_LOCATION = "customer_location"
    const val KEY_FAKE_GPS = "fake_gps"
    const val KEY_FAKE_LAT = "fake_lat"
    const val KEY_FAKE_LNG = "fake_lng"
    const val KEY_AUTO_REFRESH = "auto_refresh"
    const val KEY_REFRESH_SPEED_MS = "refresh_speed_ms"
    const val KEY_DISPLAY_ORDER_NUMBER = "display_order_number"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isAutoClickerEnabled(context: Context) = prefs(context).getBoolean(KEY_AUTO_CLICKER, false)
    fun getClickSpeedMs(context: Context) = prefs(context).getLong(KEY_CLICK_SPEED_MS, 500L).coerceAtLeast(150L)

    fun isShowSupportEnabled(context: Context) = prefs(context).getBoolean(KEY_SHOW_SUPPORT, true)
    fun isCustomerLocationEnabled(context: Context) = prefs(context).getBoolean(KEY_CUSTOMER_LOCATION, true)

    fun isFakeGpsEnabled(context: Context) = prefs(context).getBoolean(KEY_FAKE_GPS, false)
    fun getFakeLat(context: Context) = prefs(context).getString(KEY_FAKE_LAT, "24.7136")?.toDoubleOrNull() ?: 24.7136
    fun getFakeLng(context: Context) = prefs(context).getString(KEY_FAKE_LNG, "46.6753")?.toDoubleOrNull() ?: 46.6753

    fun isAutoRefreshEnabled(context: Context) = prefs(context).getBoolean(KEY_AUTO_REFRESH, false)
    fun getRefreshSpeedMs(context: Context) = prefs(context).getLong(KEY_REFRESH_SPEED_MS, 2000L).coerceAtLeast(500L)

    fun isDisplayOrderNumberEnabled(context: Context) = prefs(context).getBoolean(KEY_DISPLAY_ORDER_NUMBER, true)

    fun saveAll(
        context: Context,
        autoClicker: Boolean,
        clickSpeedMs: Long,
        showSupport: Boolean,
        customerLocation: Boolean,
        fakeGps: Boolean,
        fakeLat: String,
        fakeLng: String,
        autoRefresh: Boolean,
        refreshSpeedMs: Long,
        displayOrderNumber: Boolean
    ) {
        prefs(context).edit()
            .putBoolean(KEY_AUTO_CLICKER, autoClicker)
            .putLong(KEY_CLICK_SPEED_MS, clickSpeedMs.coerceAtLeast(150L))
            .putBoolean(KEY_SHOW_SUPPORT, showSupport)
            .putBoolean(KEY_CUSTOMER_LOCATION, customerLocation)
            .putBoolean(KEY_FAKE_GPS, fakeGps)
            .putString(KEY_FAKE_LAT, fakeLat)
            .putString(KEY_FAKE_LNG, fakeLng)
            .putBoolean(KEY_AUTO_REFRESH, autoRefresh)
            .putLong(KEY_REFRESH_SPEED_MS, refreshSpeedMs.coerceAtLeast(500L))
            .putBoolean(KEY_DISPLAY_ORDER_NUMBER, displayOrderNumber)
            .apply()
    }
}
