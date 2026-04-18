package com.kazumaproject.qwerty_keyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.DynamicColors
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.data.qwerty.CapsLockState
import com.kazumaproject.core.data.qwerty.QWERTYKeys
import com.kazumaproject.core.data.qwerty.VariationInfo
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.extensions.setMarginEnd
import com.kazumaproject.core.domain.extensions.setMarginStart
import com.kazumaproject.core.domain.extensions.toZenkaku
import com.kazumaproject.core.domain.listener.QWERTYKeyListener
import com.kazumaproject.core.domain.qwerty.QWERTYKey
import com.kazumaproject.core.domain.qwerty.QWERTYKeyInfo
import com.kazumaproject.core.domain.qwerty.QWERTYKeyMap
import com.kazumaproject.core.domain.state.QWERTYMode
import com.kazumaproject.qwerty_keyboard.R
import com.kazumaproject.qwerty_keyboard.databinding.QwertyLayoutBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class QWERTYKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private lateinit var binding: QwertyLayoutBinding
    private val qwertyKeyMap = QWERTYKeyMap()

    private var themeMode: String = "default"
    private var isNightMode: Boolean = false
    private var isDynamicColorEnabled: Boolean = true
    private var customBgColor: Int = 0
    private var customKeyColor: Int = 0
    private var customSpecialKeyColor: Int = 0
    private var customKeyTextColor: Int = 0
    private var customSpecialKeyTextColor: Int = 0
    private var liquidGlassEnable: Boolean = false
    private var customBorderEnable: Boolean = false
    private var customBorderColor: Int = 0
    private var liquidGlassKeyAlphaEnable: Int = 0
    private var borderWidth: Int = 0

    private var keyVerticalMarginDp: Float = 0f
    private var keyHorizontalGapDp: Float = 0f
    private var keyIndentLargeDp: Float = 23f
    private var keyIndentSmallDp: Float = 9f
    private var keySideMarginDp: Float = 4f
    private var keyTextSizeSp: Float = 20f

    private var isNumberKeysShow = false
    private var isSymbolKeymapShow = false
    private var showPopupView = true
    private var enableFlickUpDetection = true
    private var enableFlickDownDetection = true
    private var enableDeleteLeftFlick = true
    private var isCursorMode = false

    private val _romajiModeState = MutableStateFlow(true)
    private val romajiModeState = _romajiModeState.asStateFlow()

    private var isRomajiKeyboard = false

    private val _qwertyMode = MutableStateFlow<QWERTYMode>(QWERTYMode.Default)
    val qwertyMode: StateFlow<QWERTYMode> = _qwertyMode.asStateFlow()

    private var qwertyKeyListener: QWERTYKeyListener? = null
    private var onDeleteLeftFlickListener: (() -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val accessibilityManager: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private var isCalledFromHoverEvent = false
    private var currentTargetView: View? = null
    private var pendingInputJob: Job? = null

    private val qwertyButtonMap: Map<View, QWERTYKey> by lazy {
        mutableMapOf<View, QWERTYKey>().apply {
            put(binding.key1, QWERTYKey.QWERTYKeySwitchMode)
            put(binding.key2, QWERTYKey.QWERTYKey2)
            put(binding.key3, QWERTYKey.QWERTYKey3)
            put(binding.key4, QWERTYKey.QWERTYKey4)
            put(binding.key5, QWERTYKey.QWERTYKey5)
            put(binding.key6, QWERTYKey.QWERTYKey6)
            put(binding.key7, QWERTYKey.QWERTYKey7)
            put(binding.key8, QWERTYKey.QWERTYKey8)
            put(binding.key9, QWERTYKey.QWERTYKey9)
            put(binding.key0, QWERTYKey.QWERTYKeyReturn)
            put(binding.keyQ, QWERTYKey.QWERTYKeyQ)
            put(binding.keyW, QWERTYKey.QWERTYKeyW)
            put(binding.keyE, QWERTYKey.QWERTYKeyE)
            put(binding.keyR, QWERTYKey.QWERTYKeyR)
            put(binding.keyT, QWERTYKey.QWERTYKeyT)
            put(binding.keyY, QWERTYKey.QWERTYKeyY)
            put(binding.keyU, QWERTYKey.QWERTYKeyU)
            put(binding.keyI, QWERTYKey.QWERTYKeyI)
            put(binding.keyO, QWERTYKey.QWERTYKeyO)
            put(binding.keyP, QWERTYKey.QWERTYKeyP)
            put(binding.keyA, QWERTYKey.QWERTYKeyA)
            put(binding.keyS, QWERTYKey.QWERTYKeyS)
            put(binding.keyD, QWERTYKey.QWERTYKeyD)
            put(binding.keyF, QWERTYKey.QWERTYKeyF)
            put(binding.keyG, QWERTYKey.QWERTYKeyG)
            put(binding.keyH, QWERTYKey.QWERTYKeyH)
            put(binding.keyJ, QWERTYKey.QWERTYKeyJ)
            put(binding.keyK, QWERTYKey.QWERTYKeyK)
            put(binding.keyL, QWERTYKey.QWERTYKeyL)
            put(binding.keyZ, QWERTYKey.QWERTYKeyZ)
            put(binding.keyX, QWERTYKey.QWERTYKeyX)
            put(binding.keyC, QWERTYKey.QWERTYKeyC)
            put(binding.keyV, QWERTYKey.QWERTYKeyV)
            put(binding.keyB, QWERTYKey.QWERTYKeyB)
            put(binding.keyN, QWERTYKey.QWERTYKeyN)
            put(binding.keyM, QWERTYKey.QWERTYKeyM)
            put(binding.keyAtMark, QWERTYKey.QWERTYKeyAtMark)
            put(binding.keyDelete, QWERTYKey.QWERTYKeyDelete)
            put(binding.keyShift, QWERTYKey.QWERTYKeyShift)
            put(binding.keySpace, QWERTYKey.QWERTYKeySpace)
            put(binding.keyTouten, QWERTYKey.QWERTYKeyTouten)
            put(binding.keyKuten, QWERTYKey.QWERTYKeyKuten)
            binding.keySelect?.let { put(it, QWERTYKey.QWERTYKeySelect) }
            binding.cursorLeft?.let { put(it, QWERTYKey.QWERTYKeyCursorLeft) }
            binding.cursorRight?.let { put(it, QWERTYKey.QWERTYKeyCursorRight) }
            binding.switchRomajiEnglish?.let { put(it, QWERTYKey.QWERTYKeySwitchRomajiEnglish) }
            binding.switchNumberLayout?.let { put(it, QWERTYKey.QWERTYKeySwitchNumberKey) }
            binding.keyReadAloud?.let { put(it, QWERTYKey.QWERTYKeyReadAloud) }
            binding.keyEmoji?.let { put(it, QWERTYKey.QWERTYKeyEmoji) }
        }
    }

    init {
        val inflater = LayoutInflater.from(context)
        binding = QwertyLayoutBinding.inflate(inflater, this)
        setPadding(0, 0, 0, 0)
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        this.isNightMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)
        scope.launch {
            qwertyMode.collectLatest { state ->
                applyLayoutForMode(state)
                applyContentForMode(state)
            }
        }

        // 全てのボタン部品を検索してアクセシビリティ設定を強制適用（漏れを防止）
        setupAccessibilityDelegates(this)

        // TalkBack support for Double Tap
        qwertyButtonMap.forEach { (view, key) ->
            view.isClickable = true
            view.isFocusable = true
            view.setOnClickListener {
                if (accessibilityManager.isTouchExplorationEnabled) {
                    performKeyInput(view, key)
                }
            }
        }
    }

    private fun setupAccessibilityDelegates(view: View) {
        if (view is android.widget.Button || view is android.widget.ImageButton || view is QWERTYButton) {
            androidx.core.view.ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    // クラス名を空にし、役割記述をゼロ幅スペースにすることで「ボタン」の読み込みを完全に阻止する
                    info.className = ""
                    info.setRoleDescription("\u200B")
                    // OS側で「ボタン」としての挙動を認識させない
                    info.isClickable = false
                }
            })
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupAccessibilityDelegates(view.getChildAt(i))
            }
        }
    }

    private fun performKeyInput(view: View, key: QWERTYKey) {
        pendingInputJob?.cancel()

        if (accessibilityManager.isTouchExplorationEnabled && isCalledFromHoverEvent) {
            // TalkBackでの「指を離して確定」の場合は、わずかに遅延させてから確定する。
            pendingInputJob = scope.launch {
                delay(100)
                val text = (view as? TextView)?.text?.toString() ?: ""
                val char = text.firstOrNull()
                qwertyKeyListener?.onReleasedQWERTYKey(key, char, null)
                announceKey(view)
            }
        } else {
            // ダブルタップやTalkBackオフ時は即座に確定
            val text = (view as? TextView)?.text?.toString() ?: ""
            val char = text.firstOrNull()
            qwertyKeyListener?.onReleasedQWERTYKey(key, char, null)
            if (accessibilityManager.isTouchExplorationEnabled) {
                announceKey(view)
            }
        }
    }

    private fun applyLayoutForMode(mode: QWERTYMode) {
        val constraintSet = ConstraintSet()
        constraintSet.clone(this)
        updateGlobalMargins()
        when (mode) {
            is QWERTYMode.Symbol -> {
                displayOrHideNumberKeys(false)
                isSymbolKeymapShow = true
            }
            else -> {
                isSymbolKeymapShow = false
                displayOrHideNumberKeys(isNumberKeysShow)
            }
        }
        constraintSet.applyTo(this)
    }

    private fun updateGlobalMargins() {
        val vMargin = context.dpToPx(keyVerticalMarginDp).toInt()
        val hGap = context.dpToPx(keyHorizontalGapDp).toInt()
        val allButtons = listOf(
            binding.keyQ, binding.keyW, binding.keyE, binding.keyR, binding.keyT,
            binding.keyY, binding.keyU, binding.keyI, binding.keyO, binding.keyP,
            binding.keyA, binding.keyS, binding.keyD, binding.keyF, binding.keyG,
            binding.keyH, binding.keyJ, binding.keyK, binding.keyL,
            binding.keyShift, binding.keyZ, binding.keyX, binding.keyC,
            binding.keyV, binding.keyB, binding.keyN, binding.keyM, binding.keyDelete,
            binding.key1, binding.key2, binding.key3, binding.key4, binding.key5,
            binding.key6, binding.key7, binding.key8, binding.key9, binding.key0,
            binding.keyAtMark, binding.keySpace, binding.keyTouten, binding.keyKuten
        ) + listOfNotNull(
            binding.keySelect, binding.cursorLeft, binding.cursorRight,
            binding.switchRomajiEnglish, binding.switchNumberLayout,
            binding.keyReadAloud, binding.keyEmoji
        )
        allButtons.forEach { view ->
            val lp = view.layoutParams as LayoutParams
            lp.setMargins(hGap / 2, vMargin, hGap / 2, vMargin)
            view.layoutParams = lp
        }
    }
    internal fun applyContentForMode(mode: QWERTYMode) {
        val romajiMode = romajiModeState.value

        // 全てのキーラベルを更新
        qwertyButtonMap.forEach { (view, key) ->
            if (key == QWERTYKey.QWERTYKeyReadAloud) return@forEach
            if (view is TextView) {
                val info = when (mode) {
                    is QWERTYMode.Default -> if (romajiMode) qwertyKeyMap.getKeyInfoDefaultJP(key) else qwertyKeyMap.getKeyInfoDefault(key)
                    is QWERTYMode.Number -> if (romajiMode) qwertyKeyMap.getKeyInfoNumberJP(key) else qwertyKeyMap.getKeyInfoNumber(key)
                    is QWERTYMode.Symbol -> if (romajiMode) qwertyKeyMap.getKeyInfoSymbolJP(key) else qwertyKeyMap.getKeyInfoSymbol(key)
                }

                if (info is QWERTYKeyInfo.QWERTYVariation) {
                    // 「記号切替」ボタン（key1）はモードに応じて表示を変える
                    if (key == QWERTYKey.QWERTYKeySwitchMode) {
                        view.text = when (mode) {
                            is QWERTYMode.Number -> if (romajiMode) "あa" else "ABC"
                            else -> "記号切替"
                        }
                    } else {
                        info.tap?.let {
                            view.text = it.toString()
                        }
                    }
                }
            }
        }

        if (mode !is QWERTYMode.Symbol) {
            if (romajiMode) {
                binding.keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_japanese)
                binding.switchNumberLayout?.text = "あa1"
                binding.keyKuten.text = "。"
                binding.keyTouten.text = "、"
                // 4段目の記号（日本語モード）
                binding.key2.text = "@"
                binding.key3.text = ":"
                binding.key4.text = "("
                binding.key5.text = ")"
                binding.key6.text = "-"
                binding.key7.text = ","
                binding.key8.text = "."
                binding.key9.text = "/"
                binding.key0.text = "エンター"
            } else {
                binding.keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
                binding.switchNumberLayout?.text = "123"
                binding.keyKuten.text = "."
                binding.keyTouten.text = ","
                // 4段目の記号（アルファベットモード）: @ : ( ) - , . /
                binding.key2.text = "@"
                binding.key3.text = ":"
                binding.key4.text = "("
                binding.key5.text = ")"
                binding.key6.text = "-"
                binding.key7.text = ","
                binding.key8.text = "."
                binding.key9.text = "/"
                binding.key0.text = "Enter"
            }
        }
        setRomajiEnglishSwitchKeyTextWithStyle(romajiMode)
        setNumberSwitchKeyTextStyle(!isNumberKeysShow)
    }

    fun setRomajiEnglishSwitchKeyTextWithStyle(showRomajiEnglishKey: Boolean) {
        val text = "あa"
        val spannableString = SpannableString(text)
        if (showRomajiEnglishKey) {
            spannableString.setSpan(StyleSpan(Typeface.BOLD), 0, 1, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            spannableString.setSpan(StyleSpan(Typeface.NORMAL), 1, 2, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        } else {
            spannableString.setSpan(StyleSpan(Typeface.NORMAL), 0, 1, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
            spannableString.setSpan(StyleSpan(Typeface.BOLD), 1, 2, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        }
        binding.switchRomajiEnglish?.text = spannableString
    }

    fun setNumberSwitchKeyTextStyle(excludeNumber: Boolean) {
        val text = if (excludeNumber) "あa" else "あa1"
        val spannableString = SpannableString(text)
        spannableString.setSpan(StyleSpan(Typeface.NORMAL), 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(StyleSpan(Typeface.BOLD), 1, 2, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(RelativeSizeSpan(1.5f), 1, 2, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        if (!excludeNumber) {
            spannableString.setSpan(StyleSpan(Typeface.NORMAL), 2, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        binding.switchNumberLayout?.text = spannableString
    }

    private fun displayOrHideNumberKeys(state: Boolean) {
        listOf(
            binding.key1, binding.key2, binding.key3, binding.key4, binding.key5,
            binding.key6, binding.key7, binding.key8, binding.key9, binding.key0
        ).forEach { it.isVisible = state }

        val constraintSet = ConstraintSet()
        constraintSet.clone(this)
        
        // 最上段の数字行の領域を切り替え (0.20f で表示、0.0f で非表示)
        val numberRowPercent = if (state) 0.20f else 0.0f
        constraintSet.setGuidelinePercent(R.id.guideline_number_row, numberRowPercent)
        
        // Z行より下の高さ調整
        val percent = if (state) 0.80f else 0.60f
        constraintSet.setGuidelinePercent(R.id.guideline_z_row, percent)
        
        constraintSet.applyTo(this)
    }

    fun setOnQWERTYKeyListener(listener: QWERTYKeyListener) {
        this.qwertyKeyListener = listener
    }

    fun resetQWERTYKeyboard(enterText: String = "") {
        isRomajiKeyboard = false
        _qwertyMode.update { QWERTYMode.Default }
    }

    fun setSpecialKeyVisibility(showCursors: Boolean, showSwitchKey: Boolean, showKutouten: Boolean) {
        binding.cursorLeft?.isVisible = showCursors
        binding.cursorRight?.isVisible = showCursors
        binding.keyEmoji?.isVisible = showSwitchKey
        binding.keyKuten?.isVisible = showKutouten
        binding.keyTouten?.isVisible = showKutouten
    }

    fun updateSymbolKeymapState(state: Boolean) { this.isSymbolKeymapShow = state }
    fun updateNumberKeyState(state: Boolean) {
        this.isNumberKeysShow = state
        displayOrHideNumberKeys(state)
    }
    fun setPopUpViewState(state: Boolean) { this.showPopupView = state }
    fun setFlickUpDetectionEnabled(enabled: Boolean) { this.enableFlickUpDetection = enabled }
    fun setFlickDownDetectionEnabled(enabled: Boolean) { this.enableFlickDownDetection = enabled }
    fun setDeleteLeftFlickEnabled(enabled: Boolean) { this.enableDeleteLeftFlick = enabled }

    fun setKeyMargins(verticalDp: Float, horizontalGapDp: Float, indentLargeDp: Float, indentSmallDp: Float, sideMarginDp: Float, textSizeSp: Float) {
        this.keyVerticalMarginDp = verticalDp
        this.keyHorizontalGapDp = horizontalGapDp
        this.keyIndentLargeDp = indentLargeDp
        this.keyIndentSmallDp = indentSmallDp
        this.keySideMarginDp = sideMarginDp
        this.keyTextSizeSp = textSizeSp
        applyLayoutForMode(qwertyMode.value)
    }

    fun setRomajiKeyboard(enterKeyText: String) {
        isRomajiKeyboard = true
        _qwertyMode.update { QWERTYMode.Default } 
    }
    fun setSpaceKeyText(text: String) { binding.keySpace.text = text }

    fun applyKeyboardTheme(
        themeMode: String, currentNightMode: Int, isDynamicColorEnabled: Boolean, customBgColor: Int, customKeyColor: Int, customSpecialKeyColor: Int,
        customKeyTextColor: Int, customSpecialKeyTextColor: Int, liquidGlassEnable: Boolean, customBorderEnable: Boolean,
        customBorderColor: Int, liquidGlassKeyAlphaEnable: Int, borderWidth: Int
    ) {
        this.themeMode = themeMode
        this.isNightMode = (currentNightMode == Configuration.UI_MODE_NIGHT_YES)
        this.isDynamicColorEnabled = isDynamicColorEnabled
        this.customBgColor = customBgColor
        this.customKeyColor = customKeyColor
        this.customSpecialKeyColor = customSpecialKeyColor
        this.customKeyTextColor = customKeyTextColor
        this.customSpecialKeyTextColor = customSpecialKeyTextColor
        this.liquidGlassEnable = liquidGlassEnable
        this.customBorderEnable = customBorderEnable
        this.customBorderColor = customBorderColor
        this.liquidGlassKeyAlphaEnable = liquidGlassKeyAlphaEnable
        this.borderWidth = borderWidth
        setMaterialYouTheme(this.isNightMode, true)
    }

    fun setCursorMode(enabled: Boolean) { this.isCursorMode = enabled }

    fun setOnDeleteLeftFlickListener(listener: (() -> Unit)?) {
        this.onDeleteLeftFlickListener = listener
    }

    fun setRomajiMode(state: Boolean) {
        _romajiModeState.update { state }
        applyContentForMode(qwertyMode.value)
    }
    fun getRomajiMode(): Boolean = romajiModeState.value
    fun setQwertyMode(mode: QWERTYMode) { _qwertyMode.update { mode } }
    fun setSwitchNumberLayoutKeyVisibility(state: Boolean) { binding.switchNumberLayout?.isVisible = state }
    fun setRomajiEnglishSwitchKeyVisibility(state: Boolean) { binding.switchRomajiEnglish?.isVisible = state }
    fun setReturnKeyText(text: String) {}
    fun setMaterialYouTheme(isNight: Boolean, isDynamic: Boolean) {}

    override fun onInterceptHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityManager.isTouchExplorationEnabled) {
            return true
        }
        return super.onInterceptHoverEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (accessibilityManager.isTouchExplorationEnabled && event.pointerCount == 1) {
            val action = when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> MotionEvent.ACTION_DOWN
                MotionEvent.ACTION_HOVER_MOVE -> MotionEvent.ACTION_MOVE
                MotionEvent.ACTION_HOVER_EXIT -> MotionEvent.ACTION_UP
                else -> return super.onHoverEvent(event)
            }
            
            val touchEvent = MotionEvent.obtain(
                event.downTime,
                event.eventTime,
                action,
                event.x,
                event.y,
                event.metaState
            )
            
            isCalledFromHoverEvent = true
            val result = onTouchEvent(touchEvent)
            isCalledFromHoverEvent = false
            touchEvent.recycle()
            return result
        }
        return super.onHoverEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // If TalkBack is enabled, we only process touches that came from onHoverEvent conversion.
        // This implements "Confirm on Lift" (Slide to type).
        if (accessibilityManager.isTouchExplorationEnabled && !isCalledFromHoverEvent) {
            return true
        }

        val x = event.x.toInt()
        val y = event.y.toInt()
        val target = findChildViewAt(x, y)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pendingInputJob?.cancel()
                if (target != currentTargetView) {
                    currentTargetView = target
                    target?.let { view ->
                        qwertyButtonMap[view]?.let { key ->
                            qwertyKeyListener?.onPressedQWERTYKey(key)
                        }
                        announceKey(view)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (target != null && target != currentTargetView) {
                    currentTargetView = target
                    target.let { view ->
                        // 新しいキーに入った瞬間にクリック感をフィードバック (DTalker風)
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        qwertyButtonMap[view]?.let { key ->
                            qwertyKeyListener?.onPressedQWERTYKey(key)
                        }
                        announceKey(view)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (accessibilityManager.isTouchExplorationEnabled) {
                    // TalkBack exploration use currentTargetView for robust release recognition
                    currentTargetView?.let { view ->
                        qwertyButtonMap[view]?.let { key ->
                            performKeyInput(view, key)
                        }
                    }
                } else {
                    target?.let { view ->
                        qwertyButtonMap[view]?.let { key ->
                            performKeyInput(view, key)
                        }
                    }
                }
                isCalledFromHoverEvent = false
                currentTargetView = null
            }

            MotionEvent.ACTION_CANCEL -> {
                isCalledFromHoverEvent = false
                currentTargetView = null
            }
        }
        return true
    }

    private fun announceKey(view: View) {
        // ビューがテキストまたは説明文を持っていることを確認
        val announcement = (view as? TextView)?.text?.toString()
            ?: view.contentDescription?.toString()
            ?: return
        
        if (announcement.isNotEmpty()) {
            // TalkBackに「このビューが探索された」ことを通知します。
            // 役割（roleDescription）を空に設定しているため、TalkBackはキーの名前のみを読み上げます。
            // ここで announceForAccessibility を重ねて呼ばないことで、二重読み上げを防ぎます。
            view.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        }
    }

    private fun findChildViewAt(x: Int, y: Int): View? {
        val rect = Rect()
        for (child in qwertyButtonMap.keys) {
            child.getHitRect(rect)
            if (rect.contains(x, y) && child.isVisible) {
                return child
            }
        }
        return null
    }
}
