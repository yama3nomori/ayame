package com.kazumaproject.custom_keyboard.controller

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.ShapeType
import com.kazumaproject.custom_keyboard.view.FlickCirclePopupView

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.sqrt

enum class PopupPosition {
    CENTER,
    TOP
}

class FlickInputController(context: Context) {

    interface FlickListener {
        fun onFlick(direction: FlickDirection, character: String)
        fun onStateChanged(view: View, newMap: Map<FlickDirection, String>)
        fun onFlickDirectionChanged(newDirection: FlickDirection)
    }

    var listener: FlickListener? = null

    private val popupView = FlickCirclePopupView(context)
    private val popupWindow = PopupWindow(
        popupView,
        popupView.preferredWidth,
        popupView.preferredHeight,
        false
    ).apply {
        isOutsideTouchable = false
    }

    private var anchorView: View? = null
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var flickThreshold = 80f

    private var keyMaps: List<Map<FlickDirection, String>> = emptyList()
    private var currentMapIndex = 0
    private var previousDirection = FlickDirection.TAP
    private var lastValidFlickDirection = FlickDirection.TAP
    private var isDownModeActive = false

    private var isLongPressModeActive = false
    private val controllerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var longPressJob: Job? = null

    private var popupPosition: PopupPosition = PopupPosition.CENTER

    fun setShapeType(shape: ShapeType) {
        popupView.setShapeType(shape)
    }

    fun setPopupPosition(position: PopupPosition) {
        this.popupPosition = position
    }

    fun setPopupColors(theme: FlickPopupColorTheme) {
        popupView.setColors(theme)
    }

    fun setPopupViewSize(center: Float, target: Float, orbit: Float, textSize: Float) {
        popupView.setUiSize(center, target, orbit, textSize)
        this.flickThreshold = center
    }


    /**
     * TalkBackのダブルタップ（performClick）時に、タップ入力をシミュレートします。
     */
    fun performTap() {
        if (keyMaps.isNotEmpty()) {
            val tapMap = keyMaps[currentMapIndex]
            val char = tapMap[FlickDirection.TAP] ?: ""
            if (char.isNotEmpty()) {
                listener?.onFlick(FlickDirection.TAP, char)
            }
        }
    }

    fun cancel() {
        controllerScope.cancel()
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attach(button: View, maps: List<Map<FlickDirection, String>>) {
        if (maps.isEmpty()) {
            Log.e("FlickInputController", "Character maps cannot be empty.")
            return
        }
        this.keyMaps = maps
        button.setOnTouchListener { _, event ->
            handleTouchEvent(button, event)
        }
    }

    private fun handleTouchEvent(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                anchorView = view
                initialTouchX = event.rawX
                initialTouchY = event.rawY

                currentMapIndex = 0
                previousDirection = FlickDirection.TAP
                lastValidFlickDirection = FlickDirection.TAP
                isDownModeActive = false
                isLongPressModeActive = false
                popupView.setFullUIMode(false)

                popupView.setCharacterMap(keyMaps[currentMapIndex])
                popupView.updateFlickDirection(FlickDirection.TAP)

                showPopup()

                longPressJob?.cancel()
                longPressJob = controllerScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    isLongPressModeActive = true
                    popupView.setFullUIMode(true)
                    popupView.invalidate()
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val currentCalculatedDirection = calculateDirection(view, event.rawX, event.rawY)

                if (currentCalculatedDirection != previousDirection) {
                    listener?.onFlickDirectionChanged(currentCalculatedDirection)

                    // Voice Guidance
                    val currentMap = keyMaps[currentMapIndex]
                    val text = currentMap[currentCalculatedDirection]
                    if (!text.isNullOrEmpty()) {
                        view.announceForAccessibility(text)
                    }
                }

                if (currentCalculatedDirection != FlickDirection.TAP) {
                    longPressJob?.cancel()
                    lastValidFlickDirection = currentCalculatedDirection
                    isDownModeActive = true
                }

                if (currentCalculatedDirection == FlickDirection.DOWN) {
                    popupView.setFullUIMode(true)

                    if (previousDirection != FlickDirection.DOWN) {
                        currentMapIndex = (currentMapIndex + 1) % keyMaps.size
                        val newMap = keyMaps[currentMapIndex]
                        popupView.setCharacterMap(newMap)
                        listener?.onStateChanged(view, newMap)
                        
                        // Voice Guidance for map change
                        val textForSpeech = newMap[currentCalculatedDirection]
                        if (!textForSpeech.isNullOrEmpty()) {
                            view.announceForAccessibility(textForSpeech)
                        }
                    }
                }

                popupView.updateFlickDirection(currentCalculatedDirection)
                previousDirection = currentCalculatedDirection
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()

                val finalDirectionToInput = if (isDownModeActive || isLongPressModeActive) {
                    calculateDirection(view, event.rawX, event.rawY)
                } else {
                    FlickDirection.TAP
                }

                if (finalDirectionToInput != FlickDirection.DOWN) {
                    val currentMap = keyMaps[currentMapIndex]
                    val character = currentMap[finalDirectionToInput] ?: ""
                    listener?.onFlick(finalDirectionToInput, character)
                }

                hidePopup()
                return true
            }
        }
        return false
    }

    private fun showPopup() {
        val currentAnchor = anchorView ?: return
        popupWindow.width = popupView.preferredWidth
        popupWindow.height = popupView.preferredHeight

        val location = IntArray(2)
        currentAnchor.getLocationInWindow(location)

        val anchorX = location[0]
        val anchorY = location[1]

        val x = anchorX + (currentAnchor.width / 2) - (popupWindow.width / 2)

        val y = when (popupPosition) {
            PopupPosition.CENTER -> anchorY + (currentAnchor.height / 2) - (popupWindow.height / 2)
            PopupPosition.TOP -> anchorY - (popupWindow.height / 2)
        }

        if (!popupWindow.isShowing) {
            popupWindow.showAtLocation(currentAnchor, Gravity.NO_GRAVITY, x, y)
        }
    }

    private fun hidePopup() {
        if (popupWindow.isShowing) {
            popupWindow.dismiss()
        }
        anchorView = null
    }

    private fun calculateDirection(view: View, currentX: Float, currentY: Float): FlickDirection {
        val dx1 = currentX - initialTouchX
        val dy1 = currentY - initialTouchY

        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val centerX = location[0] + view.width / 2f
        val centerY = location[1] + view.height / 2f
        val dx2 = currentX - centerX
        val dy2 = currentY - centerY

        val dx = if (kotlin.math.abs(dx1) > kotlin.math.abs(dx2)) dx1 else dx2
        val dy = if (kotlin.math.abs(dy1) > kotlin.math.abs(dy2)) dy1 else dy2

        val distance = sqrt(dx * dx + dy * dy)

        if (distance < flickThreshold) {
            return FlickDirection.TAP
        }

        val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360

        return popupView.getDirectionForAngle(angle)
    }
}
