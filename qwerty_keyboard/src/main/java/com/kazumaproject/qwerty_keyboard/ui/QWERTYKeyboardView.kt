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
import android.view.VelocityTracker
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
import com.kazumaproject.core.domain.extensions.toAccessibilityName
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
    private var capsLockState = CapsLockState()

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

    // Drag tracking variables for QWERTYKeyCursorRight
    private var isDraggingRightCursor = false
    private var rightCursorDragStartX = 0f
    private var rightCursorDragEndX = 0f
    private var rightCursorDragStartY = 0f
    private var rightCursorDragEndY = 0f
    private var rightCursorDragTopY = 0f
    private var isLineStartAnnounced = false
    private var isLineEndAnnounced = false
    private var isLineUpAnnounced = false
    private var isLineDownAnnounced = false
    private var touchSlideInEntryTime = 0L
    private var touchSlideInEntryX = 0f
    private var touchSlideInEntryY = 0f

    // Drag tracking variables for QWERTYKeyCursorLeft
    private var isDraggingLeftCursor = false
    private var leftCursorDragStartX = 0f
    private var leftCursorDragEndX = 0f
    private var leftCursorDragStartY = 0f
    private var leftCursorDragEndY = 0f
    private var leftCursorDragTopY = 0f
    private var isLeftLineStartAnnounced = false
    private var isLeftLineEndAnnounced = false
    private var isLeftLineUpAnnounced = false
    private var isLeftLineDownAnnounced = false
    private var leftTouchSlideInEntryTime = 0L
    private var leftTouchSlideInEntryX = 0f
    private var leftTouchSlideInEntryY = 0f

    // Drag tracking variables for QWERTYKeyDelete
    private var isDraggingDeleteKey = false
    private var deleteKeyDragStartX = 0f
    private var deleteKeyDragEndX = 0f
    private var deleteKeyDragStartY = 0f
    private var deleteKeyDragEndY = 0f
    private var deleteKeyDragTopY = 0f
    private var isDeleteLeftAnnounced = false
    private var isDeleteRightAnnounced = false
    private var deleteTouchSlideInEntryTime = 0L
    private var deleteTouchSlideInEntryX = 0f
    private var deleteTouchSlideInEntryY = 0f

    // Drag tracking variables for QWERTYKeySpace
    private var isDraggingSpaceKey = false
    private var spaceKeyDragStartX = 0f
    private var spaceKeyDragEndX = 0f
    private var spaceKeyDragStartY = 0f
    private var spaceKeyDragEndY = 0f
    private var isSpaceDownAnnounced = false
    private var isSpaceUpAnnounced = false
    private var isSpaceRightAnnounced = false
    private var spaceTouchSlideInEntryTime = 0L
    private var spaceTouchSlideInEntryX = 0f
    private var spaceTouchSlideInEntryY = 0f

    // Drag tracking variables for QWERTYKeyReadAloud
    private var isDraggingReadAloudKey = false
    private var readAloudKeyDragStartX = 0f
    private var readAloudKeyDragEndX = 0f
    private var readAloudKeyDragStartY = 0f
    private var readAloudKeyDragEndY = 0f
    private var readAloudKeyDragTopY = 0f
    private var isReadAloudLeftAnnounced = false
    private var isReadAloudUpAnnounced = false
    private var isReadAloudRightAnnounced = false
    private var readAloudTouchSlideInEntryTime = 0L
    private var readAloudTouchSlideInEntryX = 0f
    private var readAloudTouchSlideInEntryY = 0f

    private var pressedKeyInitialX = 0f
    private var pressedKeyInitialY = 0f

    private val _romajiModeState = MutableStateFlow(true)
    private val romajiModeState = _romajiModeState.asStateFlow()

    private var isRomajiKeyboard = false

    private val _qwertyMode = MutableStateFlow<QWERTYMode>(QWERTYMode.Default)
    val qwertyMode: StateFlow<QWERTYMode> = _qwertyMode.asStateFlow()

    private var qwertyKeyListener: QWERTYKeyListener? = null
    private var onDeleteLeftFlickListener: (() -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastClickedKey: QWERTYKey? = null
    private var lastClickedTime: Long = 0L

    private var isVelocityFilterEnabled: Boolean = true
    private var velocityTracker: VelocityTracker? = null

    fun setFlickVelocityFilter(enabled: Boolean) {
        isVelocityFilterEnabled = enabled
    }

    var isAyameMode: Boolean = false
        set(value) {
            field = value
            lastClickedKey = null
            lastClickedTime = 0L
            setupAccessibilityDelegates(this)
        }

    private val accessibilityManager: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private var isCalledFromHoverEvent = false
    private var currentTargetView: View? = null
    private var pendingInputJob: Job? = null

    private var cachedKeyRects: List<Pair<Rect, View>>? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

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
            put(binding.keyApostropheDash, QWERTYKey.QWERTYKeyApostropheDash)
            put(binding.keyAtMark, QWERTYKey.QWERTYKeyAtMark)
            put(binding.keyDelete, QWERTYKey.QWERTYKeyDelete)
            put(binding.keyShift, QWERTYKey.QWERTYKeyShift)
            put(binding.keySpace, QWERTYKey.QWERTYKeySpace)
            put(binding.keyTouten, QWERTYKey.QWERTYKeyTouten)
            put(binding.keyKuten, QWERTYKey.QWERTYKeyKuten)
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
                updateShiftKeyAppearance()
            }
        }

        // 全てのボタン部品を検索してアクセシビリティ設定を強制適用（漏れを防止）
        setupAccessibilityDelegates(this)

        // TalkBack support for Double Tap
        qwertyButtonMap.forEach { (view, key) ->
            view.isClickable = true
            view.isFocusable = true
            view.setOnClickListener {
                if (isAyameMode) {
                    if (accessibilityManager.isTouchExplorationEnabled) {
                        performKeyInput(view, key)
                    } else {
                        val currentTime = SystemClock.uptimeMillis()
                        if (key == lastClickedKey && currentTime - lastClickedTime < 500) {
                            performKeyInput(view, key)
                            lastClickedKey = null
                            lastClickedTime = 0L
                        } else {
                            lastClickedKey = key
                            lastClickedTime = currentTime
                        }
                    }
                } else if (accessibilityManager.isTouchExplorationEnabled) {
                    performKeyInput(view, key)
                }
            }
        }
        updateShiftKeyAppearance()
        setEmojiKeyMode(false)
    }

    private fun setupAccessibilityDelegates(view: View) {
        if (view is android.widget.Button || view is android.widget.ImageButton || view is QWERTYButton) {
            val key = qwertyButtonMap[view]
            if (key != null) {
                view.isClickable = true
                view.isFocusable = true
                view.setOnClickListener {
                    if (isAyameMode) {
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            performKeyInput(view, key)
                        } else {
                            val currentTime = SystemClock.uptimeMillis()
                            if (key == lastClickedKey && currentTime - lastClickedTime < 500) {
                                performKeyInput(view, key)
                                lastClickedKey = null
                                lastClickedTime = 0L
                            } else {
                                lastClickedKey = key
                                lastClickedTime = currentTime
                            }
                        }
                    } else if (accessibilityManager.isTouchExplorationEnabled) {
                        performKeyInput(view, key)
                    }
                }
            }

            androidx.core.view.ViewCompat.setAccessibilityDelegate(view, object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    
                    val description = host.contentDescription ?: (host as? TextView)?.text
                    if (!description.isNullOrEmpty()) {
                        info.text = description
                        info.contentDescription = description
                    }

                    if (isAyameMode) {
                        info.className = "android.widget.Button"
                        info.isClickable = true
                        info.isLongClickable = true

                        if (key != null) {
                            when (key) {
                                QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeyCursorLeft -> {
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "行頭移動 (左フリック)"))
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "行末移動 (右フリック)"))
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_top, "前行移動 (上フリック)"))
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_bottom, "次行移動 (下フリック)"))
                                }
                                QWERTYKey.QWERTYKeyDelete -> {
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "一括削除 (左フリック)"))
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "行末まで削除 (右フリック)"))
                                }
                                QWERTYKey.QWERTYKeySpace -> {
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_bottom, "予測変換 (下フリック)"))
                                }
                                QWERTYKey.QWERTYKeyReadAloud -> {
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "詳細読み上げ (左フリック)"))
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_top, "文頭から読み上げ (上フリック)"))
                                    info.addAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "文末まで読み上げ (右フリック)"))
                                }
                                else -> {
                                    val romajiMode = romajiModeState.value
                                    val qMode = qwertyMode.value
                                    val qKeyInfo = when (qMode) {
                                        is QWERTYMode.Default -> if (romajiMode) qwertyKeyMap.getKeyInfoDefaultJP(key) else qwertyKeyMap.getKeyInfoDefault(key)
                                        is QWERTYMode.Number -> if (romajiMode) qwertyKeyMap.getKeyInfoNumberJP(key) else qwertyKeyMap.getKeyInfoNumber(key)
                                        is QWERTYMode.Symbol -> if (romajiMode) qwertyKeyMap.getKeyInfoSymbolJP(key) else qwertyKeyMap.getKeyInfoSymbol(key)
                                    }

                                    if (qKeyInfo is QWERTYKeyInfo.QWERTYVariation) {
                                        val isUpper = (host as? TextView)?.text?.toString()?.firstOrNull()?.isUpperCase() == true
                                        val variationsList = (if (isUpper) qKeyInfo.capVariations ?: qKeyInfo.variations else qKeyInfo.variations) ?: emptyList()
                                        
                                        val variationActionIds = listOf(
                                            com.kazumaproject.core.R.id.action_qwerty_variation_0,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_1,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_2,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_3,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_4,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_5,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_6,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_7,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_8,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_9,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_10,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_11,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_12,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_13,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_14,
                                            com.kazumaproject.core.R.id.action_qwerty_variation_15
                                        )

                                        variationsList.forEachIndexed { idx, vChar ->
                                            if (idx < variationActionIds.size) {
                                                info.addAction(
                                                    androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                                        variationActionIds[idx],
                                                        "$vChar (選択)"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (key == QWERTYKey.QWERTYKeyEmoji) {
                            info.className = "android.widget.Button"
                            info.isClickable = true
                            info.isLongClickable = true
                        } else {
                            info.className = ""
                            info.setRoleDescription("\u200B")
                            info.isClickable = false
                            info.removeAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
                            info.removeAction(androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK)
                        }
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    args: android.os.Bundle?
                ): Boolean {
                    if (isAyameMode && key != null) {
                        val variationActionIds = listOf(
                            com.kazumaproject.core.R.id.action_qwerty_variation_0,
                            com.kazumaproject.core.R.id.action_qwerty_variation_1,
                            com.kazumaproject.core.R.id.action_qwerty_variation_2,
                            com.kazumaproject.core.R.id.action_qwerty_variation_3,
                            com.kazumaproject.core.R.id.action_qwerty_variation_4,
                            com.kazumaproject.core.R.id.action_qwerty_variation_5,
                            com.kazumaproject.core.R.id.action_qwerty_variation_6,
                            com.kazumaproject.core.R.id.action_qwerty_variation_7,
                            com.kazumaproject.core.R.id.action_qwerty_variation_8,
                            com.kazumaproject.core.R.id.action_qwerty_variation_9,
                            com.kazumaproject.core.R.id.action_qwerty_variation_10,
                            com.kazumaproject.core.R.id.action_qwerty_variation_11,
                            com.kazumaproject.core.R.id.action_qwerty_variation_12,
                            com.kazumaproject.core.R.id.action_qwerty_variation_13,
                            com.kazumaproject.core.R.id.action_qwerty_variation_14,
                            com.kazumaproject.core.R.id.action_qwerty_variation_15
                        )

                        val varIndex = variationActionIds.indexOf(action)
                        if (varIndex >= 0) {
                            val romajiMode = romajiModeState.value
                            val qMode = qwertyMode.value
                            val qKeyInfo = when (qMode) {
                                is QWERTYMode.Default -> if (romajiMode) qwertyKeyMap.getKeyInfoDefaultJP(key) else qwertyKeyMap.getKeyInfoDefault(key)
                                is QWERTYMode.Number -> if (romajiMode) qwertyKeyMap.getKeyInfoNumberJP(key) else qwertyKeyMap.getKeyInfoNumber(key)
                                is QWERTYMode.Symbol -> if (romajiMode) qwertyKeyMap.getKeyInfoSymbolJP(key) else qwertyKeyMap.getKeyInfoSymbol(key)
                            }

                            if (qKeyInfo is QWERTYKeyInfo.QWERTYVariation) {
                                val isUpper = (host as? TextView)?.text?.toString()?.firstOrNull()?.isUpperCase() == true
                                val variationsList = (if (isUpper) qKeyInfo.capVariations ?: qKeyInfo.variations else qKeyInfo.variations) ?: emptyList()
                                if (varIndex < variationsList.size) {
                                    val targetChar = variationsList[varIndex]
                                    qwertyKeyListener?.onReleasedQWERTYKey(key, targetChar, null)
                                    return true
                                }
                            }
                        }

                        val gesture = when (action) {
                            com.kazumaproject.core.R.id.action_flick_left -> com.kazumaproject.core.domain.state.GestureType.FlickLeft
                            com.kazumaproject.core.R.id.action_flick_top -> com.kazumaproject.core.domain.state.GestureType.FlickTop
                            com.kazumaproject.core.R.id.action_flick_right -> com.kazumaproject.core.domain.state.GestureType.FlickRight
                            com.kazumaproject.core.R.id.action_flick_bottom -> com.kazumaproject.core.domain.state.GestureType.FlickBottom
                            else -> null
                        }
                        if (gesture != null) {
                            triggerAyameFlickAction(key, gesture)
                            return true
                        }
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            })
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupAccessibilityDelegates(view.getChildAt(i))
            }
        }
    }

    private fun triggerAyameFlickAction(key: QWERTYKey, gesture: com.kazumaproject.core.domain.state.GestureType) {
        when (key) {
            QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeyCursorLeft -> {
                val charCode = when (gesture) {
                    com.kazumaproject.core.domain.state.GestureType.FlickLeft -> '\u0001'
                    com.kazumaproject.core.domain.state.GestureType.FlickRight -> '\u0002'
                    com.kazumaproject.core.domain.state.GestureType.FlickTop -> '\u0003'
                    com.kazumaproject.core.domain.state.GestureType.FlickBottom -> '\u0004'
                    else -> null
                }
                if (charCode != null) {
                    qwertyKeyListener?.onReleasedQWERTYKey(key, charCode, null)
                }
            }
            QWERTYKey.QWERTYKeyDelete -> {
                val charCode = when (gesture) {
                    com.kazumaproject.core.domain.state.GestureType.FlickLeft -> '\u0005'
                    com.kazumaproject.core.domain.state.GestureType.FlickRight -> '\u0007'
                    else -> null
                }
                if (charCode != null) {
                    qwertyKeyListener?.onReleasedQWERTYKey(key, charCode, null)
                }
            }
            QWERTYKey.QWERTYKeySpace -> {
                if (gesture == com.kazumaproject.core.domain.state.GestureType.FlickBottom) {
                    qwertyKeyListener?.onReleasedQWERTYKey(key, '\u0014', null)
                }
            }
            QWERTYKey.QWERTYKeyReadAloud -> {
                val charCode = when (gesture) {
                    com.kazumaproject.core.domain.state.GestureType.FlickLeft -> '\u0011'
                    com.kazumaproject.core.domain.state.GestureType.FlickTop -> '\u0012'
                    com.kazumaproject.core.domain.state.GestureType.FlickRight -> '\u0013'
                    else -> null
                }
                if (charCode != null) {
                    qwertyKeyListener?.onReleasedQWERTYKey(key, charCode, null)
                }
            }
            else -> {}
        }
    }

    private fun performKeyInput(view: View, key: QWERTYKey) {
        pendingInputJob?.cancel()

        if (key == QWERTYKey.QWERTYKeyShift) {
            handleShiftClick()
            return
        }

        if (accessibilityManager.isTouchExplorationEnabled && isCalledFromHoverEvent) {
            // TalkBackでの「指を離して確定」の場合は、わずかに遅延させてから確定する。
            pendingInputJob = scope.launch {
                delay(100)
                val text = (view as? TextView)?.text?.toString() ?: ""
                val char = text.firstOrNull()
                qwertyKeyListener?.onReleasedQWERTYKey(key, char, null)
                announceKey(view)

                if (capsLockState.shiftOn) {
                    capsLockState = CapsLockState(shiftOn = false, capsLockOn = false)
                    updateShiftKeyAppearance()
                    applyContentForMode(qwertyMode.value)
                }
            }
        } else {
            // ダブルタップやTalkBackオフ時は即座に確定
            val text = (view as? TextView)?.text?.toString() ?: ""
            val char = text.firstOrNull()
            qwertyKeyListener?.onReleasedQWERTYKey(key, char, null)
            if (accessibilityManager.isTouchExplorationEnabled) {
                announceKey(view)
            }

            if (capsLockState.shiftOn) {
                capsLockState = CapsLockState(shiftOn = false, capsLockOn = false)
                updateShiftKeyAppearance()
                applyContentForMode(qwertyMode.value)
            }
        }
    }

    private fun handleShiftClick() {
        val currentMode = qwertyMode.value
        if (currentMode is QWERTYMode.Number) {
            setQwertyMode(QWERTYMode.Symbol)
            announceForAccessibility("他の記号を表示")
            return
        } else if (currentMode is QWERTYMode.Symbol) {
            setQwertyMode(QWERTYMode.Number)
            announceForAccessibility("数字と記号を表示")
            return
        }

        capsLockState = when {
            capsLockState.shiftOn -> CapsLockState(shiftOn = false, capsLockOn = true)
            capsLockState.capsLockOn -> CapsLockState(shiftOn = false, capsLockOn = false)
            else -> CapsLockState(shiftOn = true, capsLockOn = false)
        }
        updateShiftKeyAppearance()
        applyContentForMode(qwertyMode.value)

        val announceText = when {
            capsLockState.capsLockOn -> "キャプスロックオン"
            capsLockState.shiftOn -> "シフトオン"
            else -> "シフトオフ"
        }
        if (accessibilityManager.isTouchExplorationEnabled) {
            accessibilityManager.interrupt()
        }
        announceForAccessibility(announceText)
    }

    private fun updateShiftKeyAppearance() {
        val mode = qwertyMode.value
        if (mode is QWERTYMode.Number || mode is QWERTYMode.Symbol) {
            val iconRes = if (mode is QWERTYMode.Symbol) {
                com.kazumaproject.core.R.drawable.shift_fill_24px
            } else {
                com.kazumaproject.core.R.drawable.shift_24px
            }
            binding.keyShift.setImageResource(iconRes)
            val tintColor = context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
            binding.keyShift.setColorFilter(tintColor)

            val description = if (mode is QWERTYMode.Symbol) "数字と記号" else "他の記号"
            binding.keyShift.contentDescription = description
            return
        }

        val iconRes = when {
            capsLockState.capsLockOn -> com.kazumaproject.core.R.drawable.caps_lock
            capsLockState.shiftOn -> com.kazumaproject.core.R.drawable.shift_fill_24px
            else -> com.kazumaproject.core.R.drawable.shift_24px
        }
        binding.keyShift.setImageResource(iconRes)
        val tintColor = context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
        binding.keyShift.setColorFilter(tintColor)

        val description = when {
            capsLockState.capsLockOn -> "キャプスロックオン"
            capsLockState.shiftOn -> "シフトオン"
            else -> "シフトキー"
        }
        binding.keyShift.contentDescription = description
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
            binding.cursorLeft, binding.cursorRight,
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
                    // 「数字」ボタン（key1）はモードに応じて表示を変える
                    if (key == QWERTYKey.QWERTYKeySwitchMode) {
                        view.text = when (mode) {
                            is QWERTYMode.Number, is QWERTYMode.Symbol -> "文字"
                            else -> "数字"
                        }
                    } else {
                        info.tap?.let {
                            val isShift = capsLockState.shiftOn || capsLockState.capsLockOn
                            val text = if (isShift && mode is QWERTYMode.Default) {
                                info.capChar?.toString() ?: it.uppercaseChar().toString()
                            } else {
                                it.toString()
                            }
                            view.text = text
                            view.contentDescription = if (text.length == 1) {
                                text.first().toAccessibilityName()
                            } else {
                                text
                            }
                        }
                    }
                }
            }
        }

        if (romajiMode) {
            binding.keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_japanese)
            binding.switchNumberLayout?.text = "あa1"
            binding.key0.text = "エンター"
            if (mode is QWERTYMode.Default) {
                binding.keyKuten.text = "。"
                binding.keyTouten.text = "、"
                val isShift = capsLockState.shiftOn || capsLockState.capsLockOn
                binding.key2.text = if (isShift) "：" else "～"
                binding.key3.text = if (isShift) "＆" else "…"
                binding.key4.text = if (isShift) "「" else "（"
                binding.key5.text = if (isShift) "」" else "）"
                binding.key6.text = if (isShift) "※" else "！"
                binding.key7.text = if (isShift) "＜" else "、"
                binding.key8.text = if (isShift) "＞" else "。"
                binding.key9.text = if (isShift) "・" else "？"
            } else {
                binding.keyKuten.text = "."
                binding.keyTouten.text = ","
            }
        } else {
            binding.keySpace.text = resources.getString(com.kazumaproject.core.R.string.space_english)
            binding.switchNumberLayout?.text = "123"
            binding.keyKuten.text = "."
            binding.keyTouten.text = ","
            binding.key0.text = "Enter"
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
        setRomajiMode(false)
    }

    fun setSpecialKeyVisibility(showCursors: Boolean, showSwitchKey: Boolean, showKutouten: Boolean) {
        binding.cursorLeft?.isVisible = showCursors
        binding.cursorRight?.isVisible = showCursors
        binding.keyEmoji?.isVisible = showSwitchKey
        binding.keyKuten?.isVisible = showKutouten
        binding.keyTouten?.isVisible = showKutouten
    }

    fun setEmojiKeyMode(isInputting: Boolean) {
        val btn = binding.keyEmoji ?: return
        if (isInputting) {
            btn.text = "スペース"
            btn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
            btn.contentDescription = "スペース"
        } else {
            btn.text = ""
            val drawable = ContextCompat.getDrawable(context, com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24)
            drawable?.setTint(ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color))
            btn.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
            btn.contentDescription = context.getString(com.kazumaproject.core.R.string.string_symbol)
        }
        btn.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
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
        setRomajiMode(true)
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
        setEmojiKeyMode(false)
    }
    fun getRomajiMode(): Boolean = romajiModeState.value
    fun setQwertyMode(mode: QWERTYMode) { _qwertyMode.update { mode } }
    fun setSwitchNumberLayoutKeyVisibility(state: Boolean) { binding.switchNumberLayout?.isVisible = state }
    fun setRomajiEnglishSwitchKeyVisibility(state: Boolean) { binding.switchRomajiEnglish?.isVisible = state }
    fun setReturnKeyText(text: String) {}
    fun setMaterialYouTheme(isNight: Boolean, isDynamic: Boolean) {}

    override fun onInterceptHoverEvent(event: MotionEvent): Boolean {
        if (isAyameMode) {
            return false
        }
        if (accessibilityManager.isTouchExplorationEnabled) {
            return true
        }
        return super.onInterceptHoverEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isAyameMode) {
            return super.onHoverEvent(event)
        }
        if (accessibilityManager.isTouchExplorationEnabled && event.pointerCount == 1) {
            val action = when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> MotionEvent.ACTION_DOWN
                MotionEvent.ACTION_HOVER_MOVE -> MotionEvent.ACTION_MOVE
                MotionEvent.ACTION_HOVER_EXIT -> {
                    val buffer = 2f
                    val isSlideOff = event.x <= buffer || 
                                   event.x >= (width.toFloat() - buffer) || 
                                   event.y <= buffer || 
                                   event.y >= (height.toFloat() - buffer)
                    if (isSlideOff) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP
                }
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
        if (isAyameMode) {
            return false
        }
        // If TalkBack is enabled, we only process touches that came from onHoverEvent conversion.
        // This implements "Confirm on Lift" (Slide to type).
        if (accessibilityManager.isTouchExplorationEnabled && !isCalledFromHoverEvent) {
            return true
        }

        if (event.action == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        val action = event.action and MotionEvent.ACTION_MASK
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_POINTER_UP && action != MotionEvent.ACTION_CANCEL) {
            velocityTracker?.addMovement(event)
        }

        try {
            val x = event.x.toInt()
            val y = event.y.toInt()
            val target = findChildViewAt(x, y)

            val screenX = try { event.rawX } catch (e: Exception) { event.x }
            val screenY = try { event.rawY } catch (e: Exception) { event.y }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                pendingInputJob?.cancel()
                if (target != currentTargetView) {
                    currentTargetView = target
                    target?.let { view ->
                        qwertyButtonMap[view]?.let { key ->
                            qwertyKeyListener?.onPressedQWERTYKey(key)

                            pressedKeyInitialX = screenX
                            pressedKeyInitialY = screenY

                            when (key) {
                                QWERTYKey.QWERTYKeyCursorRight -> {
                                    isDraggingRightCursor = true
                                    isLineStartAnnounced = false
                                    isLineEndAnnounced = false
                                    isLineUpAnnounced = false
                                    isLineDownAnnounced = false
                                    rightCursorDragStartX = screenX
                                    rightCursorDragEndX = screenX
                                    rightCursorDragStartY = screenY
                                    rightCursorDragEndY = screenY
                                    rightCursorDragTopY = screenY

                                    isDraggingLeftCursor = false
                                    isDraggingDeleteKey = false
                                    isDraggingSpaceKey = false
                                    isSpaceDownAnnounced = false
                                    isDraggingReadAloudKey = false
                                }
                                QWERTYKey.QWERTYKeyCursorLeft -> {
                                    isDraggingLeftCursor = true
                                    isLeftLineStartAnnounced = false
                                    isLeftLineEndAnnounced = false
                                    isLeftLineUpAnnounced = false
                                    isLeftLineDownAnnounced = false
                                    leftCursorDragStartX = screenX
                                    leftCursorDragEndX = screenX
                                    leftCursorDragStartY = screenY
                                    leftCursorDragEndY = screenY
                                    leftCursorDragTopY = screenY

                                    isDraggingRightCursor = false
                                    isDraggingDeleteKey = false
                                    isDraggingSpaceKey = false
                                    isSpaceDownAnnounced = false
                                    isDraggingReadAloudKey = false
                                }
                                QWERTYKey.QWERTYKeyDelete -> {
                                    isDraggingDeleteKey = true
                                    isDeleteLeftAnnounced = false
                                    isDeleteRightAnnounced = false
                                    deleteKeyDragStartX = screenX
                                    deleteKeyDragEndX = screenX
                                    deleteKeyDragStartY = screenY
                                    deleteKeyDragEndY = screenY
                                    deleteKeyDragTopY = screenY

                                    isDraggingRightCursor = false
                                    isDraggingLeftCursor = false
                                    isDraggingSpaceKey = false
                                    isSpaceDownAnnounced = false
                                    isDraggingReadAloudKey = false
                                }
                                QWERTYKey.QWERTYKeySpace -> {
                                    isDraggingSpaceKey = true
                                    isSpaceDownAnnounced = false
                                    isSpaceUpAnnounced = false
                                    isSpaceRightAnnounced = false
                                    spaceKeyDragStartX = screenX
                                    spaceKeyDragEndX = screenX
                                    spaceKeyDragStartY = screenY
                                    spaceKeyDragEndY = screenY

                                    isDraggingRightCursor = false
                                    isDraggingLeftCursor = false
                                    isDraggingDeleteKey = false
                                    isDraggingReadAloudKey = false
                                }
                                QWERTYKey.QWERTYKeyReadAloud -> {
                                    isDraggingReadAloudKey = true
                                    isReadAloudLeftAnnounced = false
                                    isReadAloudUpAnnounced = false
                                    isReadAloudRightAnnounced = false
                                    readAloudKeyDragStartX = screenX
                                    readAloudKeyDragEndX = screenX
                                    readAloudKeyDragStartY = screenY
                                    readAloudKeyDragEndY = screenY
                                    readAloudKeyDragTopY = screenY

                                    isDraggingRightCursor = false
                                    isDraggingLeftCursor = false
                                    isDraggingDeleteKey = false
                                    isDraggingSpaceKey = false
                                    isSpaceDownAnnounced = false
                                }
                                else -> {
                                    resetDragVariables()
                                }
                            }
                        }
                        announceKey(view)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val currentKey = target?.let { qwertyButtonMap[it] }

                // --- Slide-in / Slide-out transitions ---
                if (currentKey == QWERTYKey.QWERTYKeyCursorRight) {
                    if (!isDraggingRightCursor) {
                        if (touchSlideInEntryTime == 0L) {
                            touchSlideInEntryTime = System.currentTimeMillis()
                            touchSlideInEntryX = screenX
                            touchSlideInEntryY = screenY
                        } else {
                            val movementThreshold = 10f
                            val dx = screenX - touchSlideInEntryX
                            val dy = screenY - touchSlideInEntryY
                            if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                touchSlideInEntryTime = System.currentTimeMillis()
                                touchSlideInEntryX = screenX
                                touchSlideInEntryY = screenY
                            } else {
                                val elapsed = System.currentTimeMillis() - touchSlideInEntryTime
                                if (elapsed >= 500L) {
                                    isDraggingRightCursor = true
                                    rightCursorDragStartX = screenX
                                    rightCursorDragEndX = screenX
                                    rightCursorDragStartY = screenY
                                    rightCursorDragEndY = screenY
                                    rightCursorDragTopY = screenY
                                    isLineStartAnnounced = false
                                    isLineEndAnnounced = false
                                    isLineUpAnnounced = false
                                    isLineDownAnnounced = false
                                    touchSlideInEntryTime = 0L
                                }
                            }
                        }
                    }
                } else {
                    touchSlideInEntryTime = 0L
                    if (isDraggingRightCursor && currentKey != null) {
                        isDraggingRightCursor = false
                        isLineStartAnnounced = false
                        isLineEndAnnounced = false
                        isLineUpAnnounced = false
                        isLineDownAnnounced = false
                    }
                }

                if (currentKey == QWERTYKey.QWERTYKeyCursorLeft) {
                    if (!isDraggingLeftCursor) {
                        if (leftTouchSlideInEntryTime == 0L) {
                            leftTouchSlideInEntryTime = System.currentTimeMillis()
                            leftTouchSlideInEntryX = screenX
                            leftTouchSlideInEntryY = screenY
                        } else {
                            val movementThreshold = 10f
                            val dx = screenX - leftTouchSlideInEntryX
                            val dy = screenY - leftTouchSlideInEntryY
                            if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                leftTouchSlideInEntryTime = System.currentTimeMillis()
                                leftTouchSlideInEntryX = screenX
                                leftTouchSlideInEntryY = screenY
                            } else {
                                val elapsed = System.currentTimeMillis() - leftTouchSlideInEntryTime
                                if (elapsed >= 500L) {
                                    isDraggingLeftCursor = true
                                    leftCursorDragStartX = screenX
                                    leftCursorDragEndX = screenX
                                    leftCursorDragStartY = screenY
                                    leftCursorDragEndY = screenY
                                    leftCursorDragTopY = screenY
                                    isLeftLineStartAnnounced = false
                                    isLeftLineEndAnnounced = false
                                    isLeftLineUpAnnounced = false
                                    isLeftLineDownAnnounced = false
                                    leftTouchSlideInEntryTime = 0L
                                }
                            }
                        }
                    }
                } else {
                    leftTouchSlideInEntryTime = 0L
                    if (isDraggingLeftCursor && currentKey != null) {
                        isDraggingLeftCursor = false
                        isLeftLineStartAnnounced = false
                        isLeftLineEndAnnounced = false
                        isLeftLineUpAnnounced = false
                        isLeftLineDownAnnounced = false
                    }
                }

                if (currentKey == QWERTYKey.QWERTYKeyDelete) {
                    if (!isDraggingDeleteKey) {
                        if (deleteTouchSlideInEntryTime == 0L) {
                            deleteTouchSlideInEntryTime = System.currentTimeMillis()
                            deleteTouchSlideInEntryX = screenX
                            deleteTouchSlideInEntryY = screenY
                        } else {
                            val movementThreshold = 10f
                            val dx = screenX - deleteTouchSlideInEntryX
                            val dy = screenY - deleteTouchSlideInEntryY
                            if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                deleteTouchSlideInEntryTime = System.currentTimeMillis()
                                deleteTouchSlideInEntryX = screenX
                                deleteTouchSlideInEntryY = screenY
                            } else {
                                val elapsed = System.currentTimeMillis() - deleteTouchSlideInEntryTime
                                if (elapsed >= 500L) {
                                    isDraggingDeleteKey = true
                                    deleteKeyDragStartX = screenX
                                    deleteKeyDragEndX = screenX
                                    deleteKeyDragStartY = screenY
                                    deleteKeyDragEndY = screenY
                                    deleteKeyDragTopY = screenY
                                    isDeleteLeftAnnounced = false
                                    isDeleteRightAnnounced = false
                                    deleteTouchSlideInEntryTime = 0L
                                }
                            }
                        }
                    }
                } else {
                    deleteTouchSlideInEntryTime = 0L
                    if (isDraggingDeleteKey && currentKey != null) {
                        isDraggingDeleteKey = false
                        isDeleteLeftAnnounced = false
                        isDeleteRightAnnounced = false
                    }
                }

                if (currentKey == QWERTYKey.QWERTYKeySpace) {
                    if (!isDraggingSpaceKey) {
                        if (spaceTouchSlideInEntryTime == 0L) {
                            spaceTouchSlideInEntryTime = System.currentTimeMillis()
                            spaceTouchSlideInEntryX = screenX
                            spaceTouchSlideInEntryY = screenY
                        } else {
                            val movementThreshold = 10f
                            val dx = screenX - spaceTouchSlideInEntryX
                            val dy = screenY - spaceTouchSlideInEntryY
                            if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                spaceTouchSlideInEntryTime = System.currentTimeMillis()
                                spaceTouchSlideInEntryX = screenX
                                spaceTouchSlideInEntryY = screenY
                            } else {
                                val elapsed = System.currentTimeMillis() - spaceTouchSlideInEntryTime
                                if (elapsed >= 500L) {
                                    isDraggingSpaceKey = true
                                    spaceKeyDragStartX = screenX
                                    spaceKeyDragEndX = screenX
                                    spaceKeyDragStartY = screenY
                                    spaceKeyDragEndY = screenY
                                    isSpaceDownAnnounced = false
                                    isSpaceUpAnnounced = false
                                    isSpaceRightAnnounced = false
                                    spaceTouchSlideInEntryTime = 0L
                                }
                            }
                        }
                    }
                } else {
                    spaceTouchSlideInEntryTime = 0L
                    if (isDraggingSpaceKey && currentKey != null) {
                        isDraggingSpaceKey = false
                        isSpaceDownAnnounced = false
                        isSpaceUpAnnounced = false
                        isSpaceRightAnnounced = false
                    }
                }

                if (currentKey == QWERTYKey.QWERTYKeyReadAloud) {
                    if (!isDraggingReadAloudKey) {
                        if (readAloudTouchSlideInEntryTime == 0L) {
                            readAloudTouchSlideInEntryTime = System.currentTimeMillis()
                            readAloudTouchSlideInEntryX = screenX
                            readAloudTouchSlideInEntryY = screenY
                        } else {
                            val movementThreshold = 10f
                            val dx = screenX - readAloudTouchSlideInEntryX
                            val dy = screenY - readAloudTouchSlideInEntryY
                            if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                readAloudTouchSlideInEntryTime = System.currentTimeMillis()
                                readAloudTouchSlideInEntryX = screenX
                                readAloudTouchSlideInEntryY = screenY
                            } else {
                                val elapsed = System.currentTimeMillis() - readAloudTouchSlideInEntryTime
                                if (elapsed >= 500L) {
                                    isDraggingReadAloudKey = true
                                    readAloudKeyDragStartX = screenX
                                    readAloudKeyDragEndX = screenX
                                    readAloudKeyDragStartY = screenY
                                    readAloudKeyDragEndY = screenY
                                    readAloudKeyDragTopY = screenY
                                    isReadAloudLeftAnnounced = false
                                    isReadAloudUpAnnounced = false
                                    isReadAloudRightAnnounced = false
                                    readAloudTouchSlideInEntryTime = 0L
                                }
                            }
                        }
                    }
                } else {
                    readAloudTouchSlideInEntryTime = 0L
                    if (isDraggingReadAloudKey && currentKey != null) {
                        isDraggingReadAloudKey = false
                        isReadAloudLeftAnnounced = false
                        isReadAloudUpAnnounced = false
                        isReadAloudRightAnnounced = false
                    }
                }

                // --- Drag and Hold thresholds ---
                val threshold = 35f
                val cancelThreshold = 150f
                val cancelXThreshold = 60f
                val cancelYThreshold = 60f

                if (isDraggingRightCursor) {
                    if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                        if (screenX > rightCursorDragStartX) rightCursorDragStartX = screenX
                        if (screenX < rightCursorDragEndX) rightCursorDragEndX = screenX
                        if (screenY > rightCursorDragEndY) rightCursorDragEndY = screenY
                        if (screenY < rightCursorDragTopY) rightCursorDragTopY = screenY
                    }
                    val dxStart = screenX - rightCursorDragStartX
                    val dxEnd = screenX - rightCursorDragEndX
                    val dyUp = screenY - rightCursorDragEndY
                    val dyDown = screenY - rightCursorDragTopY

                    if (dxStart < -threshold && dxStart >= -cancelThreshold && abs(screenY - rightCursorDragStartY) <= cancelYThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineStartAnnounced = true
                            announceForAccessibility("行頭")
                            android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxEnd > threshold && dxEnd <= cancelThreshold && abs(screenY - rightCursorDragStartY) <= cancelYThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineEndAnnounced = true
                            announceForAccessibility("行末")
                            android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyUp < -threshold && dyUp >= -cancelThreshold && abs(screenX - rightCursorDragStartX) <= cancelXThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineUpAnnounced = true
                            announceForAccessibility("上カーソル")
                            android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyDown > threshold && dyDown <= cancelThreshold && abs(screenX - rightCursorDragStartX) <= cancelXThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineDownAnnounced = true
                            announceForAccessibility("下カーソル")
                            android.widget.Toast.makeText(context, "下カーソル", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else {
                        val returnedToCenter = if (isLineStartAnnounced) {
                            dxStart >= -threshold
                        } else if (isLineEndAnnounced) {
                            dxEnd <= threshold
                        } else if (isLineUpAnnounced) {
                            dyUp >= -threshold
                        } else if (isLineDownAnnounced) {
                            dyDown <= threshold
                        } else {
                            false
                        }

                        if (returnedToCenter) {
                            isLineStartAnnounced = false
                            isLineEndAnnounced = false
                            isLineUpAnnounced = false
                            isLineDownAnnounced = false

                            rightCursorDragStartX = screenX
                            rightCursorDragEndX = screenX
                            rightCursorDragStartY = screenY
                            rightCursorDragEndY = screenY
                            rightCursorDragTopY = screenY

                            val button = qwertyButtonMap.filterValues { it == QWERTYKey.QWERTYKeyCursorRight }.keys.firstOrNull()
                            button?.let { view ->
                                val textStr = (view as? TextView)?.text?.toString() ?: view.contentDescription?.toString() ?: "右移動"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isLineStartAnnounced) {
                                (dxStart < -cancelThreshold) || (abs(screenY - rightCursorDragStartY) > cancelYThreshold)
                            } else if (isLineEndAnnounced) {
                                (dxEnd > cancelThreshold) || (abs(screenY - rightCursorDragStartY) > cancelYThreshold)
                            } else if (isLineUpAnnounced) {
                                (dyUp < -cancelThreshold) || (abs(screenX - rightCursorDragStartX) > cancelXThreshold)
                            } else if (isLineDownAnnounced) {
                                (dyDown > cancelThreshold) || (abs(screenX - rightCursorDragStartX) > cancelXThreshold)
                            } else {
                                (dxStart < -cancelThreshold) || (dxEnd > cancelThreshold) || (dyUp < -cancelThreshold) || (dyDown > cancelThreshold) ||
                                (abs(screenY - rightCursorDragStartY) > cancelYThreshold && abs(screenX - rightCursorDragStartX) > cancelXThreshold)
                            }
                            if (shouldCancel) {
                                isLineStartAnnounced = false
                                isLineEndAnnounced = false
                                isLineUpAnnounced = false
                                isLineDownAnnounced = false
                                isDraggingRightCursor = false
                            }
                        }
                    }
                }

                if (isDraggingLeftCursor) {
                    if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                        if (screenX > leftCursorDragStartX) leftCursorDragStartX = screenX
                        if (screenX < leftCursorDragEndX) leftCursorDragEndX = screenX
                        if (screenY > leftCursorDragEndY) leftCursorDragEndY = screenY
                        if (screenY < leftCursorDragTopY) leftCursorDragTopY = screenY
                    }
                    val dxStart = screenX - leftCursorDragStartX
                    val dxEnd = screenX - leftCursorDragEndX
                    val dyUp = screenY - leftCursorDragEndY
                    val dyDown = screenY - leftCursorDragTopY

                    if (dxStart < -threshold && dxStart >= -cancelThreshold && abs(screenY - leftCursorDragStartY) <= cancelYThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineStartAnnounced = true
                            announceForAccessibility("行頭")
                            android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxEnd > threshold && dxEnd <= cancelThreshold && abs(screenY - leftCursorDragStartY) <= cancelYThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineEndAnnounced = true
                            announceForAccessibility("行末")
                            android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyUp < -threshold && dyUp >= -cancelThreshold && abs(screenX - leftCursorDragStartX) <= cancelXThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineUpAnnounced = true
                            announceForAccessibility("上カーソル")
                            android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyDown > threshold && dyDown <= cancelThreshold && abs(screenX - leftCursorDragStartX) <= cancelXThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineDownAnnounced = true
                            announceForAccessibility("下カーソル")
                            android.widget.Toast.makeText(context, "下カーソル", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else {
                                                val returnedToCenter = if (isLeftLineStartAnnounced) {
                            dxStart >= -threshold
                        } else if (isLeftLineEndAnnounced) {
                            dxEnd <= threshold
                        } else if (isLeftLineUpAnnounced) {
                            dyUp >= -threshold
                        } else if (isLeftLineDownAnnounced) {
                            dyDown <= threshold
                        } else {
                            false
                        }

                        if (returnedToCenter) {
                            isLeftLineStartAnnounced = false
                            isLeftLineEndAnnounced = false
                            isLeftLineUpAnnounced = false
                            isLeftLineDownAnnounced = false

                            leftCursorDragStartX = screenX
                            leftCursorDragEndX = screenX
                            leftCursorDragStartY = screenY
                            leftCursorDragEndY = screenY
                            leftCursorDragTopY = screenY

                            val button = qwertyButtonMap.filterValues { it == QWERTYKey.QWERTYKeyCursorLeft }.keys.firstOrNull()
                            button?.let { view ->
                                val textStr = (view as? TextView)?.text?.toString() ?: view.contentDescription?.toString() ?: "左移動"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isLeftLineStartAnnounced) {
                                (dxStart < -cancelThreshold) || (abs(screenY - leftCursorDragStartY) > cancelYThreshold)
                            } else if (isLeftLineEndAnnounced) {
                                (dxEnd > cancelThreshold) || (abs(screenY - leftCursorDragStartY) > cancelYThreshold)
                            } else if (isLeftLineUpAnnounced) {
                                (dyUp < -cancelThreshold) || (abs(screenX - leftCursorDragStartX) > cancelXThreshold)
                            } else if (isLeftLineDownAnnounced) {
                                (dyDown > cancelThreshold) || (abs(screenX - leftCursorDragStartX) > cancelXThreshold)
                            } else {
                                (dxStart < -cancelThreshold) || (dxEnd > cancelThreshold) || (dyUp < -cancelThreshold) || (dyDown > cancelThreshold) ||
                                (abs(screenY - leftCursorDragStartY) > cancelYThreshold && abs(screenX - leftCursorDragStartX) > cancelXThreshold)
                            }
                            if (shouldCancel) {
                                isLeftLineStartAnnounced = false
                                isLeftLineEndAnnounced = false
                                isLeftLineUpAnnounced = false
                                isLeftLineDownAnnounced = false
                                isDraggingLeftCursor = false
                            }
                        }
                    }
                }

                if (isDraggingDeleteKey) {
                    if (!isDeleteLeftAnnounced && !isDeleteRightAnnounced) {
                        if (screenX < deleteKeyDragEndX) deleteKeyDragEndX = screenX
                    }
                    val dxStart = screenX - deleteKeyDragStartX

                    if (dxStart < -threshold && dxStart >= -cancelThreshold && abs(screenY - deleteKeyDragStartY) <= cancelYThreshold) {
                        if (!isDeleteLeftAnnounced && !isDeleteRightAnnounced) {
                            isDeleteLeftAnnounced = true
                            val annText = "一括削除"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxStart > threshold && dxStart <= cancelThreshold && abs(screenY - deleteKeyDragStartY) <= cancelYThreshold) {
                        if (!isDeleteRightAnnounced && !isDeleteLeftAnnounced) {
                            isDeleteRightAnnounced = true
                            val annText = "行末まで削除"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else {
                        val returnedToCenter = if (isDeleteLeftAnnounced) {
                            dxStart >= -threshold
                        } else if (isDeleteRightAnnounced) {
                            dxStart <= threshold
                        } else {
                            false
                        }

                        if (returnedToCenter) {
                            isDeleteLeftAnnounced = false
                            isDeleteRightAnnounced = false

                            deleteKeyDragStartX = screenX
                            deleteKeyDragEndX = screenX
                            deleteKeyDragStartY = screenY
                            deleteKeyDragEndY = screenY
                            deleteKeyDragTopY = screenY

                            val button = qwertyButtonMap.filterValues { it == QWERTYKey.QWERTYKeyDelete }.keys.firstOrNull()
                            button?.let { view ->
                                val textStr = (view as? TextView)?.text?.toString() ?: view.contentDescription?.toString() ?: "削除"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isDeleteLeftAnnounced) {
                                (dxStart < -cancelThreshold) || (abs(screenY - deleteKeyDragStartY) > cancelYThreshold)
                            } else if (isDeleteRightAnnounced) {
                                (dxStart > cancelThreshold) || (abs(screenY - deleteKeyDragStartY) > cancelYThreshold)
                            } else {
                                (dxStart < -cancelThreshold) || (dxStart > cancelThreshold) || (abs(screenY - deleteKeyDragStartY) > cancelYThreshold)
                            }
                            if (shouldCancel) {
                                isDeleteLeftAnnounced = false
                                isDeleteRightAnnounced = false
                                isDraggingDeleteKey = false
                            }
                        }
                    }
                }

                if (isDraggingSpaceKey) {
                    val dyStart = screenY - spaceKeyDragStartY
                    val dxStart = screenX - spaceKeyDragStartX
                    val dragUpThreshold = -35f
                    val dragRightThreshold = 35f

                    if (dyStart > threshold && dyStart <= cancelThreshold && abs(screenX - spaceKeyDragStartX) <= cancelXThreshold) {
                        if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                            isSpaceDownAnnounced = true
                            val annText = "予測変換"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyStart < dragUpThreshold && dyStart >= -cancelThreshold && abs(screenX - spaceKeyDragStartX) <= cancelXThreshold) {
                        if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                            isSpaceUpAnnounced = true
                            val annText = "カタカナ変換"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxStart > dragRightThreshold && dxStart <= cancelThreshold && abs(screenY - spaceKeyDragStartY) <= cancelXThreshold) {
                        if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                            isSpaceRightAnnounced = true
                            val annText = "半角カタカナ"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else {
                        val returnedToCenter = when {
                            isSpaceDownAnnounced -> dyStart <= threshold
                            isSpaceUpAnnounced -> dyStart >= dragUpThreshold
                            isSpaceRightAnnounced -> dxStart <= dragRightThreshold
                            else -> false
                        }

                        if (returnedToCenter) {
                            isSpaceDownAnnounced = false
                            isSpaceUpAnnounced = false
                            isSpaceRightAnnounced = false

                            spaceKeyDragStartX = screenX
                            spaceKeyDragEndX = screenX
                            spaceKeyDragStartY = screenY
                            spaceKeyDragEndY = screenY

                            val button = qwertyButtonMap.filterValues { it == QWERTYKey.QWERTYKeySpace }.keys.firstOrNull()
                            button?.let { view ->
                                val textStr = (view as? TextView)?.text?.toString() ?: view.contentDescription?.toString() ?: "スペース"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = when {
                                isSpaceDownAnnounced -> (dyStart > cancelThreshold) || (abs(screenX - spaceKeyDragStartX) > cancelXThreshold)
                                isSpaceUpAnnounced -> (dyStart < -cancelThreshold) || (abs(screenX - spaceKeyDragStartX) > cancelXThreshold)
                                isSpaceRightAnnounced -> (dxStart > cancelThreshold) || (abs(screenY - spaceKeyDragStartY) > cancelXThreshold)
                                else -> {
                                    (dyStart > cancelThreshold) || (dyStart < -cancelThreshold) || (dxStart > cancelThreshold) || (abs(screenX - spaceKeyDragStartX) > cancelXThreshold && dyStart > threshold) || (abs(screenX - spaceKeyDragStartX) > cancelXThreshold && dyStart < dragUpThreshold) || (abs(screenY - spaceKeyDragStartY) > cancelXThreshold && dxStart > dragRightThreshold)
                                }
                            }
                            if (shouldCancel) {
                                isSpaceDownAnnounced = false
                                isSpaceUpAnnounced = false
                                isSpaceRightAnnounced = false
                                isDraggingSpaceKey = false
                            }
                        }
                    }
                }

                if (isDraggingReadAloudKey) {
                    if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                        if (screenX > readAloudKeyDragStartX) readAloudKeyDragStartX = screenX
                        if (screenX < readAloudKeyDragEndX) readAloudKeyDragEndX = screenX
                        if (screenY > readAloudKeyDragEndY) readAloudKeyDragEndY = screenY
                        if (screenY < readAloudKeyDragTopY) readAloudKeyDragTopY = screenY
                    }
                    val dxStart = screenX - readAloudKeyDragStartX
                    val dxEnd = screenX - readAloudKeyDragEndX
                    val dyUp = screenY - readAloudKeyDragEndY

                    if (dxStart < -threshold && dxStart >= -cancelThreshold && abs(screenY - readAloudKeyDragStartY) <= cancelYThreshold) {
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            isReadAloudLeftAnnounced = true
                            val annText = "詳細読み上げ"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxEnd > threshold && dxEnd <= cancelThreshold && abs(screenY - readAloudKeyDragStartY) <= cancelYThreshold) {
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            isReadAloudRightAnnounced = true
                            val annText = "文末まで読み上げ"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyUp < -threshold && dyUp >= -cancelThreshold && abs(screenX - readAloudKeyDragStartX) <= cancelXThreshold) {
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            isReadAloudUpAnnounced = true
                            val annText = "文頭から読み上げ"
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else {
                        val returnedToCenter = if (isReadAloudLeftAnnounced) {
                            dxStart >= -threshold
                        } else if (isReadAloudRightAnnounced) {
                            dxEnd <= threshold
                        } else if (isReadAloudUpAnnounced) {
                            dyUp >= -threshold
                        } else {
                            false
                        }

                        if (returnedToCenter) {
                            isReadAloudLeftAnnounced = false
                            isReadAloudUpAnnounced = false
                            isReadAloudRightAnnounced = false

                            readAloudKeyDragStartX = screenX
                            readAloudKeyDragEndX = screenX
                            readAloudKeyDragStartY = screenY
                            readAloudKeyDragEndY = screenY
                            readAloudKeyDragTopY = screenY

                            val button = qwertyButtonMap.filterValues { it == QWERTYKey.QWERTYKeyReadAloud }.keys.firstOrNull()
                            button?.let { view ->
                                val textStr = (view as? TextView)?.text?.toString() ?: view.contentDescription?.toString() ?: "読み上げ"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isReadAloudLeftAnnounced) {
                                (dxStart < -cancelThreshold) || (abs(screenY - readAloudKeyDragStartY) > cancelYThreshold)
                            } else if (isReadAloudRightAnnounced) {
                                (dxEnd > cancelThreshold) || (abs(screenY - readAloudKeyDragStartY) > cancelYThreshold)
                            } else if (isReadAloudUpAnnounced) {
                                (dyUp < -cancelThreshold) || (abs(screenX - readAloudKeyDragStartX) > cancelXThreshold)
                            } else {
                                (dxStart < -cancelThreshold) || (dxEnd > cancelThreshold) || (dyUp < -cancelThreshold) ||
                                (abs(screenY - readAloudKeyDragStartY) > cancelYThreshold && abs(screenX - readAloudKeyDragStartX) > cancelXThreshold)
                            }
                            if (shouldCancel) {
                                isReadAloudLeftAnnounced = false
                                isReadAloudUpAnnounced = false
                                isReadAloudRightAnnounced = false
                                isDraggingReadAloudKey = false
                            }
                        }
                    }
                }

                if (target != currentTargetView) {
                    currentTargetView = target
                    if (target != null) {
                        target.let { view ->
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
            }

            MotionEvent.ACTION_UP -> {
                var handledGesture = false
                var gestureChar: Char? = null

                val activeKey = currentTargetView?.let { qwertyButtonMap[it] }

                if (activeKey != null) {
                    // 1) Check Drag and Hold confirmation
                    when (activeKey) {
                        QWERTYKey.QWERTYKeyCursorRight -> {
                            if (isDraggingRightCursor) {
                                isDraggingRightCursor = false
                                if (isLineStartAnnounced) {
                                    gestureChar = '\u0001'
                                    handledGesture = true
                                } else if (isLineEndAnnounced) {
                                    gestureChar = '\u0002'
                                    handledGesture = true
                                } else if (isLineUpAnnounced) {
                                    gestureChar = '\u0003'
                                    handledGesture = true
                                } else if (isLineDownAnnounced) {
                                    gestureChar = '\u0004'
                                    handledGesture = true
                                }
                            }
                        }
                        QWERTYKey.QWERTYKeyCursorLeft -> {
                            if (isDraggingLeftCursor) {
                                isDraggingLeftCursor = false
                                if (isLeftLineStartAnnounced) {
                                    gestureChar = '\u0001'
                                    handledGesture = true
                                } else if (isLeftLineEndAnnounced) {
                                    gestureChar = '\u0002'
                                    handledGesture = true
                                } else if (isLeftLineUpAnnounced) {
                                    gestureChar = '\u0003'
                                    handledGesture = true
                                } else if (isLeftLineDownAnnounced) {
                                    gestureChar = '\u0004'
                                    handledGesture = true
                                }
                            }
                        }
                        QWERTYKey.QWERTYKeyDelete -> {
                            if (isDraggingDeleteKey) {
                                isDraggingDeleteKey = false
                                if (isDeleteLeftAnnounced) {
                                    gestureChar = '\u0005'
                                    handledGesture = true
                                } else if (isDeleteRightAnnounced) {
                                    gestureChar = '\u0007'
                                    handledGesture = true
                                }
                            }
                        }
                        QWERTYKey.QWERTYKeySpace -> {
                            if (isDraggingSpaceKey) {
                                isDraggingSpaceKey = false
                                when {
                                    isSpaceDownAnnounced -> {
                                        gestureChar = '\u0014'
                                        handledGesture = true
                                    }
                                    isSpaceUpAnnounced -> {
                                        gestureChar = '\u0015'
                                        handledGesture = true
                                    }
                                    isSpaceRightAnnounced -> {
                                        gestureChar = '\u0016'
                                        handledGesture = true
                                    }
                                }
                                isSpaceDownAnnounced = false
                                isSpaceUpAnnounced = false
                                isSpaceRightAnnounced = false
                            }
                        }
                        QWERTYKey.QWERTYKeyReadAloud -> {
                            if (isDraggingReadAloudKey) {
                                isDraggingReadAloudKey = false
                                if (isReadAloudLeftAnnounced) {
                                    gestureChar = '\u0011'
                                    handledGesture = true
                                } else if (isReadAloudUpAnnounced) {
                                    gestureChar = '\u0012'
                                    handledGesture = true
                                } else if (isReadAloudRightAnnounced) {
                                    gestureChar = '\u0013'
                                    handledGesture = true
                                }
                            }
                        }
                        else -> {}
                    }

                    // 2) Fast flick fallback if drag gesture wasn't already confirmed
                    if (!handledGesture) {
                        val fastFlickChar = getFastFlickChar(event, activeKey)
                        if (fastFlickChar != null) {
                            gestureChar = fastFlickChar
                            handledGesture = true
                        }
                    }
                }

                // Reset drag states
                resetDragVariables()

                if (accessibilityManager.isTouchExplorationEnabled) {
                    currentTargetView?.let { view ->
                        qwertyButtonMap[view]?.let { key ->
                            if (handledGesture) {
                                qwertyKeyListener?.onReleasedQWERTYKey(key, gestureChar, null)
                            } else {
                                performKeyInput(view, key)
                            }
                        }
                    }
                } else {
                    target?.let { view ->
                        qwertyButtonMap[view]?.let { key ->
                            if (handledGesture) {
                                qwertyKeyListener?.onReleasedQWERTYKey(key, gestureChar, null)
                            } else {
                                performKeyInput(view, key)
                            }
                        }
                    }
                }
                isCalledFromHoverEvent = false
                currentTargetView = null
            }

            MotionEvent.ACTION_CANCEL -> {
                resetDragVariables()
                isCalledFromHoverEvent = false
                currentTargetView = null
            }
        }
        } finally {
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        return true
    }

    private fun announceKey(view: View) {
        // アヤメローマ字入力モード時は、IMEService側のannounceChar（変換後文字の読み上げ）に
        // 一本化するため、ここではTYPE_VIEW_HOVER_ENTERを送らない。
        // 送ってしまうとTalkBackが"a"を読み上げ、"あ"との衝突が発生する。
        if (isAyameMode && romajiModeState.value) return

        val textStr = (view as? TextView)?.text?.toString()
        val announcement = if (!textStr.isNullOrEmpty()) {
            textStr
        } else {
            view.contentDescription?.toString()
        } ?: return
        
        if (announcement.isNotEmpty()) {
            if (accessibilityManager.isTouchExplorationEnabled) {
                // 強制的にこれまでの読み上げを中断する
                accessibilityManager.interrupt()
            }
            // TalkBackのフォーカス移動を維持
            view.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        }
    }

    private fun resetDragVariables() {
        isDraggingRightCursor = false
        isLineStartAnnounced = false
        isLineEndAnnounced = false
        isLineUpAnnounced = false
        isLineDownAnnounced = false
        touchSlideInEntryTime = 0L

        isDraggingLeftCursor = false
        isLeftLineStartAnnounced = false
        isLeftLineEndAnnounced = false
        isLeftLineUpAnnounced = false
        isLeftLineDownAnnounced = false
        leftTouchSlideInEntryTime = 0L

        isDraggingDeleteKey = false
        isDeleteLeftAnnounced = false
        isDeleteRightAnnounced = false
        deleteTouchSlideInEntryTime = 0L

        isDraggingSpaceKey = false
        isSpaceDownAnnounced = false
        isSpaceUpAnnounced = false
        isSpaceRightAnnounced = false
        spaceTouchSlideInEntryTime = 0L

        isDraggingReadAloudKey = false
        isReadAloudLeftAnnounced = false
        isReadAloudUpAnnounced = false
        isReadAloudRightAnnounced = false
        readAloudTouchSlideInEntryTime = 0L
    }

    private fun getKeyCenter(key: QWERTYKey, useRaw: Boolean): Pair<Float, Float>? {
        val button = qwertyButtonMap.filterValues { it == key }.keys.firstOrNull() ?: return null
        return if (useRaw) {
            val location = IntArray(2)
            button.getLocationOnScreen(location)
            val cx = location[0] + button.width / 2f
            val cy = location[1] + button.height / 2f
            cx to cy
        } else {
            val cx = button.x + button.width / 2f
            val cy = button.y + button.height / 2f
            cx to cy
        }
    }

    private fun getFastFlickChar(event: MotionEvent, key: QWERTYKey): Char? {
        var useRaw = true
        val screenX = try {
            event.rawX
        } catch (e: Exception) {
            useRaw = false
            event.x
        }
        val screenY = try {
            event.rawY
        } catch (e: Exception) {
            useRaw = false
            event.y
        }

        val dX1 = screenX - pressedKeyInitialX
        val dY1 = screenY - pressedKeyInitialY

        val keyCenter = getKeyCenter(key, useRaw)
        val dX2 = if (keyCenter != null) screenX - keyCenter.first else dX1
        val dY2 = if (keyCenter != null) screenY - keyCenter.second else dY1

        val distanceX = if (abs(dX1) > abs(dX2)) dX1 else dX2
        val distanceY = if (abs(dY1) > abs(dY2)) dY1 else dY2

        val threshold = 35f
        val cancelThreshold = 60f

        var isFastX = true
        var isFastY = true
        if (isVelocityFilterEnabled) {
            velocityTracker?.computeCurrentVelocity(1000)
            val xVel = velocityTracker?.xVelocity ?: 0f
            val yVel = velocityTracker?.yVelocity ?: 0f
            val density = context.resources.displayMetrics.density
            val swipeThreshold = 500f * density
            isFastX = abs(xVel) > swipeThreshold
            isFastY = abs(yVel) > swipeThreshold
        }

        return when (key) {
            QWERTYKey.QWERTYKeyCursorRight, QWERTYKey.QWERTYKeyCursorLeft -> {
                if (abs(distanceY) <= cancelThreshold && isFastX) {
                    if (distanceX < -threshold) '\u0001'
                    else if (distanceX > threshold) '\u0002'
                    else null
                } else if (abs(distanceX) <= cancelThreshold && isFastY) {
                    if (distanceY < -threshold) '\u0003'
                    else if (distanceY > threshold && abs(distanceX) < abs(distanceY) / 2f) '\u0004'
                    else null
                } else null
            }
            QWERTYKey.QWERTYKeyDelete -> {
                if (abs(distanceY) <= cancelThreshold && isFastX) {
                    if (distanceX < -threshold) '\u0005'
                    else if (distanceX > threshold) '\u0007'
                    else null
                } else null
            }
            QWERTYKey.QWERTYKeySpace -> {
                if (abs(distanceX) <= cancelThreshold && distanceY > threshold && isFastY && abs(distanceX) < abs(distanceY) / 2f) {
                    '\u0014'
                } else null
            }
            QWERTYKey.QWERTYKeyReadAloud -> {
                if (abs(distanceY) <= cancelThreshold && isFastX) {
                    if (distanceX < -threshold) '\u0011'
                    else if (distanceX > threshold) '\u0013'
                    else null
                } else if (abs(distanceX) <= cancelThreshold && distanceY < -threshold && isFastY) {
                    '\u0012'
                } else null
            }
            else -> null
        }
    }

    private fun findChildViewAt(x: Int, y: Int): View? {
        if (cachedKeyRects == null || width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            cachedKeyRects = qwertyButtonMap.keys.filter { it.isVisible }.map { child ->
                val rect = Rect()
                child.getHitRect(rect)
                rect to child
            }
        }

        // 1) 最初に、完全に矩形内に指があるかチェック（従来通りの厳格判定）
        cachedKeyRects?.forEach { (rect, child) ->
            if (rect.contains(x, y)) {
                return child
            }
        }

        // 2) TalkBackでのなぞり操作中の場合のみ、最も近いキーを探索（テンキー同等の近接アルゴリズム）
        if (accessibilityManager.isTouchExplorationEnabled && isCalledFromHoverEvent) {
            val nearest = cachedKeyRects?.minByOrNull { (rect, _) ->
                val cx = (rect.left + rect.right) / 2
                val cy = (rect.top + rect.bottom) / 2
                val dx = x - cx
                val dy = y - cy
                (dx * dx + dy * dy)
            }?.second
            return nearest
        }

        return null
    }
}
