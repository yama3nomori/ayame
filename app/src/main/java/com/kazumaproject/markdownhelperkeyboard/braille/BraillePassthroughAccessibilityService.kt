package com.kazumaproject.markdownhelperkeyboard.braille

import android.accessibilityservice.AccessibilityService
import android.graphics.Region
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import timber.log.Timber

/**
 * 点字キーボードの4点タッチエリア限定でTalkBackのタッチ探索およびジェスチャー検出をパススルーし、
 * キーボード自身のマルチタッチ・スワイプジェスチャーをダイレクトに動作させるためのユーザー補助サービス。
 */
class BraillePassthroughAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: BraillePassthroughAccessibilityService? = null

        @Volatile
        private var currentPassthroughRegion: Region = Region()

        /**
         * 点字キーボードの4点タッチエリアの画面上領域を更新する。
         * 空の Region() を渡すとパススルーを解除し、画面全体でTalkBackジェスチャーが通常動作する。
         */
        fun updatePassthroughRegion(region: Region) {
            currentPassthroughRegion = Region(region)
            instance?.applyRegion(currentPassthroughRegion)
        }

        /**
         * パススルー領域を即座にクリア（解除）する
         */
        fun clearPassthroughRegion() {
            currentPassthroughRegion = Region()
            instance?.applyRegion(Region())
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.d("BraillePassthroughAccessibilityService connected")
        applyRegion(currentPassthroughRegion)
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
