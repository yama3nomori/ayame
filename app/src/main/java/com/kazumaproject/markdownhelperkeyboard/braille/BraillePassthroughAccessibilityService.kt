package com.kazumaproject.markdownhelperkeyboard.braille

import android.accessibilityservice.AccessibilityService
import android.graphics.Region
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * キーボードの指定タッチエリア限定でTalkBackのタッチ探索およびジェスチャー検出をパススルーし、
 * キーボード自身のマルチタッチ・フリック・スワイプジェスチャーをダイレクトに動作させるためのユーザー補助サービス。
 */
class BraillePassthroughAccessibilityService : AccessibilityService() {

    companion object {
        const val OWNER_BRAILLE = "braille"
        const val OWNER_TENKEY_BETA = "tenkey_beta"

        @Volatile
        private var instance: BraillePassthroughAccessibilityService? = null

        @Volatile
        private var currentPassthroughRegion: Region = Region()

        @Volatile
        private var currentOwner: String? = null

        @Volatile
        var onConnectionChanged: ((Boolean) -> Unit)? = null

        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * パススルー対応のユーザー補助サービスが接続済みかつOSサポート対象(API 30+)かを返す
         */
        fun isServiceConnected(): Boolean {
            return instance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        }

        /**
         * パススルー対象エリアの画面上領域を更新する。
         * 空の Region() を渡すとパススルーを解除し、画面全体でTalkBackジェスチャーが通常動作する。
         */
        fun updatePassthroughRegion(region: Region, owner: String = OWNER_BRAILLE) {
            currentOwner = if (region.isEmpty) null else owner
            currentPassthroughRegion = Region(region)
            instance?.applyRegion(currentPassthroughRegion)
        }

        /**
         * パススルー領域を即座にクリア（解除）する。
         * owner を指定した場合は、その owner が現在保持中の領域である場合のみクリアする。
         * owner が null の場合は無条件にクリアする。
         */
        fun clearPassthroughRegion(owner: String? = null) {
            if (owner != null && currentOwner != null && currentOwner != owner) {
                return
            }
            currentOwner = null
            currentPassthroughRegion = Region()
            instance?.applyRegion(Region())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.d("BraillePassthroughAccessibilityService connected")
        applyRegion(currentPassthroughRegion)
        mainHandler.post { onConnectionChanged?.invoke(isServiceConnected()) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 必要に応じてウィンドウ状態変化時にも現在のRegionを再適用
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onDestroy() {
        applyRegion(Region())
        if (instance === this) {
            instance = null
        }
        mainHandler.post { onConnectionChanged?.invoke(false) }
        super.onDestroy()
    }

    private fun applyRegion(region: Region) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY, region)
                setGestureDetectionPassthroughRegion(Display.DEFAULT_DISPLAY, region)
                Timber.d("Applied passthrough region: ${region.bounds}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to set passthrough region")
            }
        }
    }
}
