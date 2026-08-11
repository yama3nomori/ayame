package com.kazumaproject.core

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class SafeAccessibilityManager(private val actual: AccessibilityManager) {
    val isEnabled: Boolean
        get() = actual.isEnabled

    val isTouchExplorationEnabled: Boolean
        get() = actual.isTouchExplorationEnabled

    fun getEnabledAccessibilityServiceList(feedbackTypeFlags: Int) =
        actual.getEnabledAccessibilityServiceList(feedbackTypeFlags)

    fun sendAccessibilityEvent(event: AccessibilityEvent) =
        actual.sendAccessibilityEvent(event)

    fun interrupt() {
        try {
            actual.interrupt()
        } catch (e: Exception) {
            // ignore
        }
    }
}
