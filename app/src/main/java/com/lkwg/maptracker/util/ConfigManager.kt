package com.lkwg.maptracker.util

import android.content.Context
import android.content.SharedPreferences

/**
 * 配置管理器 - 持久化存储小地图裁剪参数和匹配参数
 */
object ConfigManager {

    private const val PREF_NAME = "map_tracker_config"

    private const val KEY_MINIMAP_X = "minimap_x"
    private const val KEY_MINIMAP_Y = "minimap_y"
    private const val KEY_MINIMAP_W = "minimap_w"
    private const val DEFAULT_MINIMAP_X = 0
    private const val DEFAULT_MINIMAP_Y = 0
    private const val DEFAULT_MINIMAP_W = 300
    private const val DEFAULT_MINIMAP_H = 300

    private const val KEY_MATCH_INTERVAL = "match_interval"
    private const val DEFAULT_MATCH_INTERVAL = 200L

    private const val KEY_ORB_FEATURES = "orb_features"
    private const val DEFAULT_ORB_FEATURES = 5000

    private const val KEY_CONFIDENCE_THRESHOLD = "confidence_threshold"
    private const val DEFAULT_CONFIDENCE_THRESHOLD = 0.3f

    private const val KEY_MAP_FILE = "map_file_path"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getMinimapRect(context: Context): IntArray {
        val p = prefs(context)
        return intArrayOf(
            p.getInt(KEY_MINIMAP_X, DEFAULT_MINIMAP_X),
            p.getInt(KEY_MINIMAP_Y, DEFAULT_MINIMAP_Y),
            p.getInt(KEY_MINIMAP_W, DEFAULT_MINIMAP_W),
            p.getInt("minimap_h", DEFAULT_MINIMAP_H)
        )
    }

    fun setMinimapRect(context: Context, x: Int, y: Int, w: Int, h: Int) {
        prefs(context).edit()
            .putInt(KEY_MINIMAP_X, x)
            .putInt(KEY_MINIMAP_Y, y)
            .putInt(KEY_MINIMAP_W, w)
            .putInt("minimap_h", h)
            .apply()
    }

    fun getMatchInterval(context: Context): Long =
        prefs(context).getLong(KEY_MATCH_INTERVAL, DEFAULT_MATCH_INTERVAL)

    fun setMatchInterval(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_MATCH_INTERVAL, ms).apply()
    }

    fun getOrbFeatures(context: Context): Int =
        prefs(context).getInt(KEY_ORB_FEATURES, DEFAULT_ORB_FEATURES)

    fun getConfidenceThreshold(context: Context): Float =
        prefs(context).getFloat(KEY_CONFIDENCE_THRESHOLD, DEFAULT_CONFIDENCE_THRESHOLD)

    fun getMapFilePath(context: Context): String =
        prefs(context).getString(KEY_MAP_FILE, "") ?: ""

    fun setMapFilePath(context: Context, path: String) {
        prefs(context).edit().putString(KEY_MAP_FILE, path).apply()
    }
}
