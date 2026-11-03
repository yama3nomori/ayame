package com.kazumaproject.markdownhelperkeyboard.braille

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kazumaproject.core.domain.braille.BrailleDot
import com.kazumaproject.core.domain.braille.BrailleInputMode
import com.kazumaproject.core.domain.braille.BrailleInputProcessor
import com.kazumaproject.core.domain.braille.BrailleInputResult
import com.kazumaproject.core.domain.braille.BraillePrefixState
import com.kazumaproject.core.domain.braille.BrailleStrokeStep
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.BrailleKeyboardLayoutBinding
import kotlin.math.abs
import kotlin.math.max

interface OnBrailleInputListener {
    fun onInputText(text: String)
    fun onDelete()
    fun onDeleteLeftGesture()
    fun onDeleteRightGesture()
    fun onSpace()
    fun onEnter()
    fun onNextCandidate()
    fun onPrevCandidate()
    fun onSwitchMode(mode: BrailleInputMode)
    fun onSwitchKeyboard()
    fun onCursorLeft()
    fun onCursorRight()
    fun onCursorUp()
    fun onCursorDown()
    fun onReadAloudTap()
    fun onReadAloudLeft()
    fun onReadAloudUp()
    fun onReadAloudRight()
    fun onReadAloudDown()
}

class BrailleKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: BrailleKeyboardLayoutBinding =
        BrailleKeyboardLayoutBinding.inflate(LayoutInflater.from(context), this, true)

    val inputProcessor = BrailleInputProcessor()
    var listener: OnBrailleInputListener? = null

    var isAyameMode: Boolean = false
        set(value) {
            field = value
            applyTheme()
        }

    private var customKeyBgColor: Int? = null
    private var customTextColor: Int? = null
    private var customBgColor: Int? = null

    // タッチトラッキング
    private data class TouchPointer(
        val pointerId: Int,
        val startX: Float,
        val startY: Float,
        val startTime: Long,
        var lastX: Float,
        var lastY: Float
    )

    private val activePointers = mutableMapOf<Int, TouchPointer>()
    private val strokeKeySet = mutableSetOf<Int>() // 1, 2, 3, 4
    private var maxPointerCount = 0
    private var isGestureRecognized = false
    private var isHoldSwipeExecuted = false
    private var isLongPressExecuted = false
    private var isSwipeHoldExecuted = false
    private var swipeHoldAnchorX = 0f
    private var swipeHoldAnchorY = 0f
    private var isSwipeLeftHoldPending = false
    private var isSwipeRightHoldPending = false
    private var lastNonAnchorSwipeDx = 0f
    private var lastNonAnchorSwipeDy = 0f
    private var hadAnchorAtPointerUp = false

    private val swipeThresholdPx: Float = 40f * context.resources.displayMetrics.density
    private val swipeHoldMoveSlopPx: Float = 12f * context.resources.displayMetrics.density
    private val longPressTimeoutMs = 450L
    private val swipeHoldTimeoutMs = 400L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val threeFingerLongPressRunnable = Runnable {
        if (activePointers.size == 3 && !isGestureRecognized && !isHoldSwipeExecuted && !isLongPressExecuted) {
            isLongPressExecuted = true
            strokeKeySet.clear()
            clearAllHighlights()
            vibrate(50)
            listener?.onReadAloudTap()
        }
    }

    private val oneFingerLeftSwipeHoldRunnable = Runnable {
        if (activePointers.size == 1 && maxPointerCount == 1 && !isHoldSwipeExecuted && !isLongPressExecuted && !isSwipeHoldExecuted) {
            val tp = activePointers.values.firstOrNull()
            if (tp != null) {
                val dx = tp.lastX - tp.startX
                val dy = tp.lastY - tp.startY
                if (dx < -swipeThresholdPx && abs(dx) > abs(dy)) {
                    isSwipeHoldExecuted = true
                    isSwipeLeftHoldPending = false
                    strokeKeySet.clear()
                    clearAllHighlights()
                    inputProcessor.resetStroke()
                    updatePreview()
                    vibrate(50)
                    listener?.onDeleteLeftGesture()
                }
            }
        }
    }

    private val oneFingerRightSwipeHoldRunnable = Runnable {
        if (activePointers.size == 1 && maxPointerCount == 1 && !isHoldSwipeExecuted && !isLongPressExecuted && !isSwipeHoldExecuted) {
            val tp = activePointers.values.firstOrNull()
            if (tp != null) {
                val dx = tp.lastX - tp.startX
                val dy = tp.lastY - tp.startY
                if (dx > swipeThresholdPx && abs(dx) > abs(dy)) {
                    isSwipeHoldExecuted = true
                    isSwipeRightHoldPending = false
                    strokeKeySet.clear()
                    clearAllHighlights()
                    inputProcessor.resetStroke()
                    updatePreview()
                    vibrate(50)
                    listener?.onDeleteRightGesture()
                }
            }
        }
    }

    private fun cancelThreeFingerLongPress() {
        longPressHandler.removeCallbacks(threeFingerLongPressRunnable)
    }

    private fun cancelOneFingerSwipeHold() {
        isSwipeLeftHoldPending = false
        isSwipeRightHoldPending = false
        longPressHandler.removeCallbacks(oneFingerLeftSwipeHoldRunnable)
        longPressHandler.removeCallbacks(oneFingerRightSwipeHoldRunnable)
    }

    // バイブレーター
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    init {
        binding.brailleKey1Title.text = "1"
        binding.brailleKey1Sub.text = "① / ④"
        binding.brailleKey2Title.text = "2"
        binding.brailleKey2Sub.text = "② / ⑤"
        binding.brailleKey3Title.text = "3"
        binding.brailleKey3Sub.text = "③ / ⑥"
        binding.brailleKey4Title.text = "半"
        binding.brailleKey4Sub.text = "ハンスペース"

        // 上部プレビューバー: TalkBack探索で状態を読み上げ可能にする
        binding.braillePreviewBar.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        binding.braillePreviewBar.isFocusable = true

        // 下部キーエリア: 内部個別ボタンやコンテナ自体へのTalkBackフォーカスを抑止し、
        // 上部の変換候補やショートカットツールバーで1本指スワイプした際にキーボードへフォーカスが吸われないようにする
        binding.brailleKeysContainer.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.brailleKeysContainer.isFocusable = false
        binding.brailleKeysContainer.isClickable = false

        binding.brailleKey1.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.brailleKey2.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.brailleKey3.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        binding.brailleKey4.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS

        // 親ViewGroup自体もフォーカスを奪わず、上部の候補エリアやツールバーでのTalkBackスワイプナビゲーションを阻害しない
        this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        this.isFocusable = false
        this.isClickable = false

        applyTheme()
        updatePreview()
    }

    /**
     * 4点タッチエリアの表示状態・座標に応じて、TalkBackパススルー領域を更新または解除する
     */
    fun syncPassthroughRegion() {
        if (!isShown || visibility != View.VISIBLE) {
            BraillePassthroughAccessibilityService.clearPassthroughRegion()
            return
        }
        val container = binding.brailleKeysContainer
        if (container.width <= 0 || container.height <= 0) {
            BraillePassthroughAccessibilityService.clearPassthroughRegion()
            return
        }
        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        val rect = Rect(
            loc[0],
            loc[1],
            loc[0] + container.width,
            loc[1] + container.height
        )
        BraillePassthroughAccessibilityService.updatePassthroughRegion(Region(rect))
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == View.VISIBLE && isShown) {
            post { syncPassthroughRegion() }
        } else {
            BraillePassthroughAccessibilityService.clearPassthroughRegion()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && isShown) {
            post { syncPassthroughRegion() }
        } else {
            BraillePassthroughAccessibilityService.clearPassthroughRegion()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        this.isFocusable = false
        this.isClickable = false
        post { syncPassthroughRegion() }
    }

    override fun onDetachedFromWindow() {
        BraillePassthroughAccessibilityService.clearPassthroughRegion()
        super.onDetachedFromWindow()
    }

    /**
     * テーマ設定
     */
    fun setKeyboardTheme(
        backgroundColor: Int,
        keyBackgroundColor: Int,
        textColor: Int
    ) {
        customBgColor = backgroundColor
        customKeyBgColor = keyBackgroundColor
        customTextColor = textColor
        applyTheme()
    }

    private fun applyTheme() {
        val keyBgColor = customKeyBgColor ?: if (isAyameMode) {
            Color.parseColor("#2D3133")
        } else {
            Color.parseColor("#3C4043")
        }

        val textColor = customTextColor ?: Color.WHITE
        val rootBgColor = customBgColor ?: if (isAyameMode) {
            Color.parseColor("#1B1B1F")
        } else {
            Color.parseColor("#202124")
        }

        binding.brailleRootLayout.setBackgroundColor(rootBgColor)

        // 各キーの背景を描画
        val keyViews = listOf(
            binding.brailleKey1,
            binding.brailleKey2,
            binding.brailleKey3,
            binding.brailleKey4
        )

        for (keyView in keyViews) {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(keyBgColor)
            }
            keyView.background = shape
        }

        // テキスト色の適用
        val textViews = listOf(
            binding.brailleKey1Title, binding.brailleKey1Sub,
            binding.brailleKey2Title, binding.brailleKey2Sub,
            binding.brailleKey3Title, binding.brailleKey3Sub,
            binding.brailleKey4Title, binding.brailleKey4Sub,
            binding.brailleModeBadge, binding.brailleStatusBadge
        )
        for (tv in textViews) {
            tv.setTextColor(textColor)
        }
    }

    private fun setKeyHighlight(keyIndex: Int, isPressed: Boolean) {
        val keyView = when (keyIndex) {
            1 -> binding.brailleKey1
            2 -> binding.brailleKey2
            3 -> binding.brailleKey3
            4 -> binding.brailleKey4
            else -> return
        }

        val baseColor = customKeyBgColor ?: if (isAyameMode) {
            Color.parseColor("#2D3133")
        } else {
            Color.parseColor("#3C4043")
        }

        val highlightColor = if (isPressed) {
            Color.parseColor("#5C6B73")
        } else {
            baseColor
        }

        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 16f * resources.displayMetrics.density
            setColor(highlightColor)
        }
        keyView.background = shape
    }

    private fun clearAllHighlights() {
        for (i in 1..4) {
            setKeyHighlight(i, false)
        }
    }

    /**
     * 座標からキー番号 (1〜4) を判定
     */
    private fun getKeyIndexAtPosition(x: Float, y: Float): Int {
        val container = binding.brailleKeysContainer
        val location = IntArray(2)
        container.getLocationOnScreen(location)

        val keys = listOf(
            binding.brailleKey1,
            binding.brailleKey2,
            binding.brailleKey3,
            binding.brailleKey4
        )

        for (i in keys.indices) {
            val keyLoc = IntArray(2)
            keys[i].getLocationOnScreen(keyLoc)
            val keyX = keyLoc[0]
            val keyY = keyLoc[1]
            val keyW = keys[i].width
            val keyH = keys[i].height

            val viewLoc = IntArray(2)
            getLocationOnScreen(viewLoc)
            val screenX = viewLoc[0] + x
            val screenY = viewLoc[1] + y

            if (screenX >= keyX && screenX <= keyX + keyW &&
                screenY >= keyY && screenY <= keyY + keyH
            ) {
                return i + 1
            }
        }
        return 0
    }

    /**
     * 座標 (x, y) が 4点キーコンテナの領域内にあるかを判定
     */
    private fun isTouchInKeysArea(x: Float, y: Float): Boolean {
        val container = binding.brailleKeysContainer
        val viewLoc = IntArray(2)
        getLocationOnScreen(viewLoc)
        val screenX = viewLoc[0] + x
        val screenY = viewLoc[1] + y

        val containerLoc = IntArray(2)
        container.getLocationOnScreen(containerLoc)
        val cX = containerLoc[0]
        val cY = containerLoc[1]
        val cW = container.width
        val cH = container.height

        return screenX >= cX && screenX <= cX + cW && screenY >= cY && screenY <= cY + cH
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isTouchInKeysArea(ev.x, ev.y)) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (isTouchInKeysArea(event.x, event.y)) {
            return onHoverEvent(event)
        }
        return super.dispatchHoverEvent(event)
    }

    private var hoverStartX = 0f
    private var hoverStartY = 0f
    private var hoverLastX = 0f
    private var hoverLastY = 0f
    private var hoverKey = 0
    private var isHoverGestureRecognized = false

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isTouchInKeysArea(event.x, event.y)) {
            clearAllHighlights()
            return super.onHoverEvent(event)
        }

        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                hoverStartX = event.x
                hoverStartY = event.y
                hoverLastX = event.x
                hoverLastY = event.y
                isHoverGestureRecognized = false
                strokeKeySet.clear()

                hoverKey = getKeyIndexAtPosition(event.x, event.y)
                if (hoverKey > 0) {
                    strokeKeySet.add(hoverKey)
                    setKeyHighlight(hoverKey, true)
                    announceKey(hoverKey)
                    vibrate(15)
                }
                return true
            }

            MotionEvent.ACTION_HOVER_MOVE -> {
                hoverLastX = event.x
                hoverLastY = event.y
                val dx = hoverLastX - hoverStartX
                val dy = hoverLastY - hoverStartY

                if (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) {
                    isHoverGestureRecognized = true
                }

                val currentKey = getKeyIndexAtPosition(event.x, event.y)
                if (currentKey > 0 && currentKey != hoverKey && !isHoverGestureRecognized) {
                    clearAllHighlights()
                    hoverKey = currentKey
                    strokeKeySet.clear()
                    strokeKeySet.add(currentKey)
                    setKeyHighlight(currentKey, true)
                    announceKey(currentKey)
                    vibrate(10)
                }
                return true
            }

            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverLastX = event.x
                hoverLastY = event.y
                val dx = hoverLastX - hoverStartX
                val dy = hoverLastY - hoverStartY

                clearAllHighlights()

                if (isHoverGestureRecognized || abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) {
                    handleGesture(dx, dy, 1)
                } else if (strokeKeySet.isNotEmpty()) {
                    handleTap()
                }

                strokeKeySet.clear()
                hoverKey = 0
                return true
            }
        }
        return true
    }

    private fun announceKey(keyIndex: Int) {
        val name = when (keyIndex) {
            1 -> if (inputProcessor.currentStep == BrailleStrokeStep.FIRST_STROKE) "1の点" else "4の点"
            2 -> if (inputProcessor.currentStep == BrailleStrokeStep.FIRST_STROKE) "2の点" else "5の点"
            3 -> if (inputProcessor.currentStep == BrailleStrokeStep.FIRST_STROKE) "3の点" else "6の点"
            4 -> "ハンスペース"
            else -> return
        }
        announceForAccessibility(name)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val container = binding.brailleKeysContainer
        val rect = Rect(container.left, container.top, container.right, container.bottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            systemGestureExclusionRects = listOf(rect)
        }
        post { syncPassthroughRegion() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN && !isTouchInKeysArea(event.getX(0), event.getY(0))) {
            return false
        }
        val actionIndex = event.actionIndex

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                cancelThreeFingerLongPress()
                cancelOneFingerSwipeHold()
                activePointers.clear()
                strokeKeySet.clear()
                maxPointerCount = 1
                isGestureRecognized = false
                isHoldSwipeExecuted = false
                isLongPressExecuted = false
                isSwipeHoldExecuted = false
                lastNonAnchorSwipeDx = 0f
                lastNonAnchorSwipeDy = 0f
                hadAnchorAtPointerUp = false

                val pointerId = event.getPointerId(0)
                val x = event.getX(0)
                val y = event.getY(0)
                activePointers[pointerId] = TouchPointer(
                    pointerId = pointerId,
                    startX = x,
                    startY = y,
                    startTime = System.currentTimeMillis(),
                    lastX = x,
                    lastY = y
                )

                val key = getKeyIndexAtPosition(x, y)
                if (key > 0) {
                    strokeKeySet.add(key)
                    setKeyHighlight(key, true)
                }
                vibrate(15)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                cancelOneFingerSwipeHold()
                val pointerId = event.getPointerId(actionIndex)
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                activePointers[pointerId] = TouchPointer(
                    pointerId = pointerId,
                    startX = x,
                    startY = y,
                    startTime = System.currentTimeMillis(),
                    lastX = x,
                    lastY = y
                )
                maxPointerCount = max(maxPointerCount, event.pointerCount)

                val key = getKeyIndexAtPosition(x, y)
                if (key > 0 && !isHoldSwipeExecuted && !isLongPressExecuted && !isSwipeHoldExecuted) {
                    strokeKeySet.add(key)
                    setKeyHighlight(key, true)
                }
                vibrate(15)

                cancelThreeFingerLongPress()
                if (activePointers.size == 3 && !isGestureRecognized && !isHoldSwipeExecuted && !isLongPressExecuted && !isSwipeHoldExecuted) {
                    longPressHandler.postDelayed(threeFingerLongPressRunnable, longPressTimeoutMs)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val tp = activePointers[pId]
                    if (tp != null) {
                        tp.lastX = event.getX(i)
                        tp.lastY = event.getY(i)
                        val dx = tp.lastX - tp.startX
                        val dy = tp.lastY - tp.startY
                        if (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) {
                            isGestureRecognized = true
                            cancelThreeFingerLongPress()
                        }

                        // 1本指の左右スワイプ後の長押し判定
                        if (activePointers.size == 1 && maxPointerCount == 1 &&
                            !isHoldSwipeExecuted && !isLongPressExecuted && !isSwipeHoldExecuted
                        ) {
                            if (dx < -swipeThresholdPx && abs(dx) > abs(dy)) {
                                if (isSwipeRightHoldPending) {
                                    isSwipeRightHoldPending = false
                                    longPressHandler.removeCallbacks(oneFingerRightSwipeHoldRunnable)
                                }
                                if (!isSwipeLeftHoldPending) {
                                    isSwipeLeftHoldPending = true
                                    swipeHoldAnchorX = tp.lastX
                                    swipeHoldAnchorY = tp.lastY
                                    longPressHandler.postDelayed(
                                        oneFingerLeftSwipeHoldRunnable,
                                        swipeHoldTimeoutMs
                                    )
                                } else if (abs(tp.lastX - swipeHoldAnchorX) > swipeHoldMoveSlopPx ||
                                    abs(tp.lastY - swipeHoldAnchorY) > swipeHoldMoveSlopPx
                                ) {
                                    // 指がまだ動いている間は基準座標を更新しタイマーをリセット
                                    swipeHoldAnchorX = tp.lastX
                                    swipeHoldAnchorY = tp.lastY
                                    longPressHandler.removeCallbacks(oneFingerLeftSwipeHoldRunnable)
                                    longPressHandler.postDelayed(
                                        oneFingerLeftSwipeHoldRunnable,
                                        swipeHoldTimeoutMs
                                    )
                                }
                            } else if (dx > swipeThresholdPx && abs(dx) > abs(dy)) {
                                if (isSwipeLeftHoldPending) {
                                    isSwipeLeftHoldPending = false
                                    longPressHandler.removeCallbacks(oneFingerLeftSwipeHoldRunnable)
                                }
                                if (!isSwipeRightHoldPending) {
                                    isSwipeRightHoldPending = true
                                    swipeHoldAnchorX = tp.lastX
                                    swipeHoldAnchorY = tp.lastY
                                    longPressHandler.postDelayed(
                                        oneFingerRightSwipeHoldRunnable,
                                        swipeHoldTimeoutMs
                                    )
                                } else if (abs(tp.lastX - swipeHoldAnchorX) > swipeHoldMoveSlopPx ||
                                    abs(tp.lastY - swipeHoldAnchorY) > swipeHoldMoveSlopPx
                                ) {
                                    // 指がまだ動いている間は基準座標を更新しタイマーをリセット
                                    swipeHoldAnchorX = tp.lastX
                                    swipeHoldAnchorY = tp.lastY
                                    longPressHandler.removeCallbacks(oneFingerRightSwipeHoldRunnable)
                                    longPressHandler.postDelayed(
                                        oneFingerRightSwipeHoldRunnable,
                                        swipeHoldTimeoutMs
                                    )
                                }
                            } else {
                                cancelOneFingerSwipeHold()
                            }
                        } else {
                            cancelOneFingerSwipeHold()
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                cancelThreeFingerLongPress()
                cancelOneFingerSwipeHold()
                val pointerId = event.getPointerId(actionIndex)
                val upPointer = activePointers.remove(pointerId)
                if (isLongPressExecuted || isSwipeHoldExecuted) {
                    return true
                }
                if (upPointer != null) {
                    upPointer.lastX = event.getX(actionIndex)
                    upPointer.lastY = event.getY(actionIndex)
                    val dx = upPointer.lastX - upPointer.startX
                    val dy = upPointer.lastY - upPointer.startY

                    // 残っているポインタの中に静止しているホールド指があるか判定
                    val hasStationaryAnchor = activePointers.values.any { p ->
                        abs(p.lastX - p.startX) <= swipeThresholdPx &&
                            abs(p.lastY - p.startY) <= swipeThresholdPx
                    }

                    if (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) {
                        if (hasStationaryAnchor) {
                            // 1本指ホールド + もう1本指スワイプが確定
                            isHoldSwipeExecuted = true
                            strokeKeySet.clear()
                            handleHoldAndSwipe(dx, dy)
                        } else {
                            // 2本指同時スワイプ等のため記録しておく
                            lastNonAnchorSwipeDx = dx
                            lastNonAnchorSwipeDy = dy
                            hadAnchorAtPointerUp = false
                        }
                    } else {
                        // 離れた指自体が静止（ホールド指）だった場合、
                        // 残っているスワイプ指が直後に ACTION_UP されるケースに備えて記録
                        hadAnchorAtPointerUp = true
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelThreeFingerLongPress()
                cancelOneFingerSwipeHold()
                val pointerId = event.getPointerId(0)
                val tp = activePointers[pointerId]
                if (tp != null) {
                    tp.lastX = event.getX(0)
                    tp.lastY = event.getY(0)
                }

                val dx = (tp?.lastX ?: 0f) - (tp?.startX ?: 0f)
                val dy = (tp?.lastY ?: 0f) - (tp?.startY ?: 0f)
                val pointerCount = maxPointerCount

                clearAllHighlights()

                if (isLongPressExecuted || isSwipeHoldExecuted) {
                    // ロングタップまたはスワイプ後長押しを既に実行済みの場合は何もしない
                } else if (isHoldSwipeExecuted) {
                    // 既に ACTION_POINTER_UP でホールド＋スワイプを実行済み。
                    // ただし最後の指自体もスワイプしていて、かつ直前に離れた指が静止していた場合は追加判定
                    if (hadAnchorAtPointerUp && (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx)) {
                        handleHoldAndSwipe(dx, dy)
                    }
                } else if (pointerCount >= 2 && hadAnchorAtPointerUp &&
                    (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx)
                ) {
                    // ホールド指がわずかに先に離れ、直後にスワイプ指が離れたケース
                    handleHoldAndSwipe(dx, dy)
                } else if (isGestureRecognized || abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) {
                    // 通常のジェスチャー処理（2本指同時スワイプで最後の指の移動量が小さい場合は先に離れた指の移動量を使用）
                    val gestureDx = if (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) dx else lastNonAnchorSwipeDx
                    val gestureDy = if (abs(dx) > swipeThresholdPx || abs(dy) > swipeThresholdPx) dy else lastNonAnchorSwipeDy
                    handleGesture(gestureDx, gestureDy, pointerCount)
                } else {
                    // タップ処理
                    handleTap()
                }

                activePointers.clear()
                strokeKeySet.clear()
                isHoldSwipeExecuted = false
                isLongPressExecuted = false
                isSwipeHoldExecuted = false
                hadAnchorAtPointerUp = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelThreeFingerLongPress()
                cancelOneFingerSwipeHold()
                clearAllHighlights()
                activePointers.clear()
                strokeKeySet.clear()
                isHoldSwipeExecuted = false
                isLongPressExecuted = false
                isSwipeHoldExecuted = false
                hadAnchorAtPointerUp = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 1本指ホールド + もう1本指スワイプ（カーソル移動）の判定と実行
     */
    private fun handleHoldAndSwipe(dx: Float, dy: Float) {
        if (abs(dx) > abs(dy)) {
            if (dx < -swipeThresholdPx) {
                // 左スワイプ: 左カーソル
                vibrate(20)
                listener?.onCursorLeft()
            } else if (dx > swipeThresholdPx) {
                // 右スワイプ: 右カーソル
                vibrate(20)
                listener?.onCursorRight()
            }
        } else {
            if (dy > swipeThresholdPx) {
                // 下スワイプ: 下カーソル
                vibrate(20)
                listener?.onCursorDown()
            } else if (dy < -swipeThresholdPx) {
                // 上スワイプ: 上カーソル
                vibrate(20)
                listener?.onCursorUp()
            }
        }
    }

    /**
     * ジェスチャーの判定と実行
     */
    private fun handleGesture(dx: Float, dy: Float, pointerCount: Int) {
        if (abs(dx) > abs(dy)) {
            // 水平スワイプ
            if (dx < -swipeThresholdPx) {
                // 左スワイプ (←)
                if (pointerCount >= 3) {
                    // 3本指以上: 読み上げ左ジェスチャー（現在行の詳細読み上げ）
                    vibrate(40)
                    listener?.onReadAloudLeft()
                } else {
                    // 1〜2本指: 削除
                    vibrate(30)
                    val wasReset = inputProcessor.resetStroke()
                    updatePreview()
                    if (!wasReset) {
                        listener?.onDelete()
                    }
                }
            } else if (dx > swipeThresholdPx) {
                // 右スワイプ (→)
                when (pointerCount) {
                    1 -> {
                        // 1本指: スペース / 変換
                        vibrate(25)
                        listener?.onSpace()
                    }
                    2 -> {
                        // 2本指: エンター (確定 / 改行)
                        vibrate(40)
                        listener?.onEnter()
                    }
                    else -> {
                        // 3本指以上: 読み上げ右ジェスチャー（カーソル位置から末尾まで読み上げ）
                        vibrate(40)
                        listener?.onReadAloudRight()
                    }
                }
            }
        } else {
            // 垂直スワイプ
            if (dy > swipeThresholdPx) {
                // 下スワイプ (↓)
                when {
                    pointerCount >= 3 -> {
                        // 3本指以上: 読み上げ下ジェスチャー（カーソル位置の読み上げ）
                        vibrate(40)
                        listener?.onReadAloudDown()
                    }
                    pointerCount == 2 -> {
                        // 2本指: 入力モード切替
                        vibrate(40)
                        val newMode = inputProcessor.switchMode()
                        updatePreview()
                        listener?.onSwitchMode(newMode)
                        val modeName = when (newMode) {
                            BrailleInputMode.JAPANESE -> "日本語モード"
                            BrailleInputMode.ENGLISH -> "英語モード"
                            BrailleInputMode.NUMBER -> "数字モード"
                        }
                        announceForAccessibility(modeName)
                    }
                    else -> {
                        // 1本指: 変換候補を進める
                        vibrate(20)
                        listener?.onNextCandidate()
                    }
                }
            } else if (dy < -swipeThresholdPx) {
                // 上スワイプ (↑)
                when {
                    pointerCount >= 3 -> {
                        // 3本指以上: 読み上げ上ジェスチャー（全文読み上げ）
                        vibrate(40)
                        listener?.onReadAloudUp()
                    }
                    pointerCount == 2 -> {
                        // 2本指: キーボード切替
                        vibrate(40)
                        listener?.onSwitchKeyboard()
                    }
                    else -> {
                        // 1本指: 変換候補を戻す
                        vibrate(20)
                        listener?.onPrevCandidate()
                    }
                }
            }
        }
    }

    /**
     * タップ入力（1ストローク）の処理
     */
    private fun handleTap() {
        val isHalfSpace = strokeKeySet.contains(4)
        val keyIndices = strokeKeySet.filter { it in 1..3 }.toSet()

        val result = inputProcessor.processStroke(isHalfSpace, keyIndices)
        updatePreview()

        when (result) {
            is BrailleInputResult.StrokeAdvance -> {
                vibrate(15)
            }
            is BrailleInputResult.Character -> {
                vibrate(35)
                listener?.onInputText(result.text)
                announceForAccessibility(result.text)
            }
            is BrailleInputResult.Space -> {
                vibrate(25)
                listener?.onSpace()
                announceForAccessibility("スペース")
            }
            is BrailleInputResult.PrefixSet -> {
                vibrate(20)
                val prefixName = when (result.prefix) {
                    BraillePrefixState.DAKUTEN -> "濁点"
                    BraillePrefixState.HANDAKUTEN -> "半濁点"
                    BraillePrefixState.YOUON -> "拗音"
                    BraillePrefixState.YOU_DAKUTEN -> "拗濁音"
                    BraillePrefixState.YOU_HANDAKUTEN -> "拗半濁音"
                    BraillePrefixState.NUMBER_MODE -> "数字モード"
                    BraillePrefixState.FOREIGN_MODE -> "外国語モード"
                    else -> ""
                }
                if (prefixName.isNotEmpty()) {
                    announceForAccessibility(prefixName)
                }
            }
            else -> {
                vibrate(10)
            }
        }
    }

    /**
     * プレビューバーの表示更新
     */
    fun updatePreview() {
        val state = inputProcessor.getPreviewState()

        // モードバッジ
        binding.brailleModeBadge.text = when (state.mode) {
            BrailleInputMode.JAPANESE -> "かな"
            BrailleInputMode.ENGLISH -> "英語"
            BrailleInputMode.NUMBER -> "数字"
        }

        // ステータスバッジ
        val statusText = when {
            state.prefix != BraillePrefixState.NONE -> {
                when (state.prefix) {
                    BraillePrefixState.DAKUTEN -> "濁点"
                    BraillePrefixState.HANDAKUTEN -> "半濁点"
                    BraillePrefixState.YOUON -> "拗音"
                    BraillePrefixState.YOU_DAKUTEN -> "拗濁音"
                    BraillePrefixState.YOU_HANDAKUTEN -> "拗半濁音"
                    BraillePrefixState.NUMBER_MODE -> "数符"
                    BraillePrefixState.FOREIGN_MODE -> "外字符"
                    else -> ""
                }
            }
            state.step == BrailleStrokeStep.FIRST_STROKE -> "1打目 (左列)"
            state.step == BrailleStrokeStep.SECOND_STROKE -> "2打目 (右列)"
            else -> ""
        }
        binding.brailleStatusBadge.text = statusText

        // 6点ドットの更新
        val dot1On = (state.leftDots and BrailleDot.DOT_1) != 0
        val dot2On = (state.leftDots and BrailleDot.DOT_2) != 0
        val dot3On = (state.leftDots and BrailleDot.DOT_3) != 0

        binding.dot1.setBackgroundResource(if (dot1On) R.drawable.braille_dot_on else R.drawable.braille_dot_off)
        binding.dot2.setBackgroundResource(if (dot2On) R.drawable.braille_dot_on else R.drawable.braille_dot_off)
        binding.dot3.setBackgroundResource(if (dot3On) R.drawable.braille_dot_on else R.drawable.braille_dot_off)
        binding.dot4.setBackgroundResource(R.drawable.braille_dot_off)
        binding.dot5.setBackgroundResource(R.drawable.braille_dot_off)
        binding.dot6.setBackgroundResource(R.drawable.braille_dot_off)

        binding.braillePreviewBar.contentDescription = "点字入力 ${binding.brailleModeBadge.text} $statusText"
    }

    private fun vibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (_: Exception) {}
    }
}