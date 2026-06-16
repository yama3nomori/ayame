package com.kazumaproject.custom_keyboard.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.Gravity
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.GridLayout
import androidx.annotation.AttrRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.material.R
import com.kazumaproject.core.domain.extensions.isDarkThemeOn
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.custom_keyboard.controller.CrossFlickInputController
import com.kazumaproject.custom_keyboard.controller.CustomAngleFlickController
import com.kazumaproject.custom_keyboard.controller.GridFlickInputController
import com.kazumaproject.custom_keyboard.controller.StandardFlickInputController
import com.kazumaproject.custom_keyboard.controller.TfbiHierarchicalFlickController
import com.kazumaproject.custom_keyboard.controller.TfbiInputController
import com.kazumaproject.custom_keyboard.controller.TfbiStickyFlickController
import com.kazumaproject.custom_keyboard.controller.FlickInputController
import com.kazumaproject.custom_keyboard.controller.TfbiFlickDirection
import com.kazumaproject.custom_keyboard.data.FlickAction
import com.kazumaproject.custom_keyboard.data.FlickDirection
import com.kazumaproject.custom_keyboard.data.FlickPopupColorTheme
import com.kazumaproject.custom_keyboard.data.KeyAction
import com.kazumaproject.custom_keyboard.data.KeyData
import com.kazumaproject.custom_keyboard.data.KeyType
import com.kazumaproject.custom_keyboard.data.KeyboardLayout
import com.kazumaproject.custom_keyboard.layout.SegmentedBackgroundDrawable
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class FlickKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : GridLayout(context, attrs, defStyleAttr) {

    interface OnKeyboardActionListener {
        fun onKey(text: String, isFlick: Boolean)
        fun onAction(action: KeyAction, view: View, isFlick: Boolean)
        fun onActionLongPress(action: KeyAction)
        fun onActionUpAfterLongPress(action: KeyAction)
        fun onFlickDirectionChanged(direction: FlickDirection)
        fun onFlickActionLongPress(action: KeyAction)
        fun onFlickActionUpAfterLongPress(action: KeyAction, isFlick: Boolean)
    }

    private var listener: OnKeyboardActionListener? = null
    private val flickControllers = mutableListOf<CustomAngleFlickController>()
    private val crossFlickControllers = mutableListOf<CrossFlickInputController>()
    private val standardFlickControllers = mutableListOf<StandardFlickInputController>()
    private val petalFlickControllers = mutableListOf<GridFlickInputController>()
    private val tfbiControllers = mutableListOf<TfbiInputController>()
    private val stickyTfbiControllers = mutableListOf<TfbiStickyFlickController>()
    private val hierarchicalTfbiControllers = mutableListOf<TfbiHierarchicalFlickController>()

    private val hitRect = Rect()
    private var flickSensitivity: Int = 100
    private var defaultTextSize = 14f
    private var isCursorMode: Boolean = false
    private var cursorInitialX = 0f
    private var cursorInitialY = 0f

    private var liquidGlassEnable: Boolean = false

    /**
     * 動的キー（keyIdを持つキー）の情報を保持するためのマップ
     * keyId: String -> KeyInfo
     */
    private val dynamicKeyMap = mutableMapOf<String, KeyInfo>()

    /**
     * flickKeyMaps などにアクセスするために、現在設定されているレイアウトを保持
     */
    private var currentLayout: KeyboardLayout? = null

    /**
     * 動的キーのViewと最新のKeyData、コントローラー、インデックスを保持する
     */
    private data class KeyInfo(
        var view: View,
        var keyData: KeyData,
        var controller: Any? = null,
        val index: Int
    )

    // Theme Variables (Initialized with defaults)
    private var themeMode: String = "default"
    private var isNightMode: Boolean = false
    private var isDynamicColorEnabled: Boolean = false
    private var customBgColor: Int = Color.WHITE
    private var customKeyColor: Int = Color.LTGRAY
    private var customSpecialKeyColor: Int = Color.GRAY
    private var customKeyTextColor: Int = Color.BLACK
    private var customSpecialKeyTextColor: Int = Color.BLACK

    private var liquidGlassKeyAlphaEnable: Int = 255
    private var customBorderEnable: Boolean = false
    private var customBorderColor: Int = Color.BLACK
    private var customAngleAndRange: Map<FlickDirection, Pair<Float, Float>> = emptyMap()
    private var circularViewScale: Float = 1.0f
    private var borderWidth: Int = 1

    private val accessibilityManager: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    
    // TalkBack対応: onHoverEventから呼ばれたかどうかを示すフラグ
    private var isCalledFromHoverEvent = false

    // 入力中フラグ（IMEServiceからセットされる）
    var isInputComposing = false

    // TalkBack時のホバードラッグ追跡変数
    // カーソル左
    private var isHoverDraggingLeftCursor = false
    private var hoverLeftCursorDragStartX = 0f
    private var hoverLeftCursorDragEndX = 0f
    private var hoverLeftCursorDragStartY = 0f
    private var hoverLeftCursorDragEndY = 0f
    private var hoverLeftCursorDragTopY = 0f
    private var isLeftLineStartAnnounced = false
    private var isLeftLineEndAnnounced = false
    private var isLeftLineUpAnnounced = false
    private var isLeftLineDownAnnounced = false
    private var leftHoverSlideInEntryTime = 0L
    private var leftHoverSlideInEntryX = 0f
    private var leftHoverSlideInEntryY = 0f

    // カーソル右
    private var isHoverDraggingRightCursor = false
    private var hoverRightCursorDragStartX = 0f
    private var hoverRightCursorDragEndX = 0f
    private var hoverRightCursorDragStartY = 0f
    private var hoverRightCursorDragEndY = 0f
    private var hoverRightCursorDragTopY = 0f
    private var isLineStartAnnounced = false
    private var isLineEndAnnounced = false
    private var isLineUpAnnounced = false
    private var isLineDownAnnounced = false
    private var hoverSlideInEntryTime = 0L
    private var hoverSlideInEntryX = 0f
    private var hoverSlideInEntryY = 0f

    // 削除キー
    private var isHoverDraggingDeleteKey = false
    private var hoverDeleteKeyDragStartX = 0f
    private var hoverDeleteKeyDragEndX = 0f
    private var hoverDeleteKeyDragStartY = 0f
    private var hoverDeleteKeyDragEndY = 0f
    private var hoverDeleteKeyDragTopY = 0f
    private var isDeleteLeftAnnounced = false
    private var isDeleteRightAnnounced = false
    private var isDeleteUpAnnounced = false
    private var deleteHoverSlideInEntryTime = 0L
    private var deleteHoverSlideInEntryX = 0f
    private var deleteHoverSlideInEntryY = 0f
    
    // デバッグ用: 最初の1回だけTalkBackの状態を通知
    // private var hasAnnouncedTalkBackStatus = false
    
    /** Mark key Views as important for accessibility so TalkBack can find them via swiping. **/
    private fun setupAccessibility() {
        // 親ビュー自体の設定: TalkBackの対象にするが、背景自体のフォーカスは避ける
        this.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        this.isFocusable = false
        this.isClickable = false
        
        // 子要素の基本設定は updateKeyVisuals 内で行われる
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val info = findKeyInfoForView(child)
            if (info != null) {
                updateKeyVisuals(child, info.keyData)
            }
        }
    }

    /**
     * TalkBackのダブルタップ（performClick）時の処理
     */
    private fun handleKeyClick(view: View) {
        val keyInfo = findKeyInfoForView(view) ?: return
        val keyData = keyInfo.keyData
        
        // 特殊アクションがある場合はそれを優先
        if (keyData.action != null) {
            listener?.onAction(keyData.action, view, false)
            return
        }

        // コントローラーの種類に応じてタップ処理を振り分け
        when (val controller = keyInfo.controller) {
            is TfbiHierarchicalFlickController -> {
                controller.performTap()
            }
            is TfbiStickyFlickController -> {
                controller.performTap()
            }
            is FlickInputController -> {
                controller.performTap()
            }
            else -> {
                // デフォルトのタップ処理（ラベルの最初の文字を入力）
                if (keyData.label.isNotEmpty()) {
                    val char = keyData.label.split("\n").firstOrNull() ?: keyData.label
                    listener?.onKey(char, false)
                }
            }
        }
    }

    /**
     * TalkBackが有効かどうかをチェックするヘルパーメソッド
     */
    private fun isTouchExplorationEnabled(): Boolean {
        return accessibilityManager.isEnabled && accessibilityManager.isTouchExplorationEnabled
    }

    private var lastHoverTarget: View? = null

    private var cachedKeyRects: List<Pair<Rect, View>>? = null
    private var lastViewWidth: Int = 0
    private var lastViewHeight: Int = 0

    fun setOnKeyboardActionListener(listener: OnKeyboardActionListener) {
        this.listener = listener
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    fun setDefaultTextSize(textSize: Float) {
        this.defaultTextSize = textSize
    }

    fun setCursorMode(enabled: Boolean) {
        isCursorMode = enabled
    }

    fun setAngleAndRange(
        range: Map<FlickDirection,
                Pair<Float, Float>>,
        circularPopViewScale: Float
    ) {
        this.customAngleAndRange = range
        this.circularViewScale = circularPopViewScale
    }

    /**
     * テーマ設定を一括で適用するメイン関数
     * メンバ変数に値を保存してからテーマを適用します。
     * @param currentNightMode res.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK の値
     */
    fun applyKeyboardTheme(
        themeMode: String,
        currentNightMode: Int,
        isDynamicColorEnabled: Boolean,
        customBgColor: Int,
        customKeyColor: Int,
        customSpecialKeyColor: Int,
        customKeyTextColor: Int,
        customSpecialKeyTextColor: Int,
        liquidGlassEnable: Boolean,
        customBorderEnable: Boolean,
        customBorderColor: Int,
        liquidGlassKeyAlphaEnable: Int,
        borderWidth: Int
    ) {
        // メンバ変数に代入
        this.themeMode = themeMode

        // Int型の currentNightMode から Boolean型の isNightMode を判定
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

        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(customBgColor, 0))
        }
    }

    /**
     * 色の明るさを調整するヘルパー関数 (QWERTYKeyboardViewと統一)
     * @param color 元の色
     * @param factor 1.0より大＝明るく、1.0より小＝暗く
     */
    private fun manipulateColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setKeyboard(layout: KeyboardLayout) {
        Log.d("FlickKeyboardView", "setKeyboard (Full Rebuild)")

        // 1. 既存のリソースをすべてクリア
        this.removeAllViews()
        flickControllers.forEach { it.cancel() }
        flickControllers.clear()
        crossFlickControllers.forEach { it.cancel() }
        crossFlickControllers.clear()
        standardFlickControllers.forEach { it.cancel() }
        standardFlickControllers.clear()
        petalFlickControllers.forEach { it.cancel() }
        petalFlickControllers.clear()
        tfbiControllers.forEach { it.cancel() }
        tfbiControllers.clear()
        stickyTfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.clear()
        hierarchicalTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.clear()

        dynamicKeyMap.clear()
        currentLayout = layout

        this.columnCount = layout.columnCount
        this.rowCount = layout.rowCount
        this.isFocusable = false

        // 2. キーを順に生成してアタッチ
        layout.keys.forEach { keyData ->
            val index = this.childCount // addViewする前の現在のView数をインデックスとして使用

            // 3. ヘルパー関数でViewを生成
            val keyView = createKeyView(keyData)

            // 4. ヘルパー関数でビヘイビア（リスナーやコントローラー）をアタッチ
            val controller = attachKeyBehavior(keyView, keyData)

            // 5. 動的キーならマップに保存
            keyData.keyId?.let { id ->
                dynamicKeyMap[id] = KeyInfo(keyView, keyData, controller, index)
            }

            this.addView(keyView)
        }
        setupAccessibility()
    }

    /**
     * 指定されたkeyIdを持つキーの表示と動作を、新しいstateIndexに基づいて更新します。
     * このメソッドは、必要に応じてViewの再生成とコントローラーの再アタッチを行います。
     *
     * @param keyId 更新するキーのID (e.g., "enter_key")
     * @param stateIndex 適用する新しい状態のインデックス
     */
    fun updateDynamicKey(keyId: String, stateIndex: Int) {
        // 1. 更新対象のキー情報をマップから取得
        val info = dynamicKeyMap[keyId] ?: return
        val states = info.keyData.dynamicStates ?: return
        val newState = states.getOrNull(stateIndex) ?: states.firstOrNull() ?: return

        // 2. 新しいKeyDataをメモリ上で作成
        val newKeyData = info.keyData.copy(
            label = newState.label ?: "",
            action = newState.action,
            drawableResId = newState.drawableResId
        )

        // 3. Viewタイプの変更チェック
        val oldView = info.view
        val newViewIsIcon = newKeyData.isSpecialKey && newKeyData.drawableResId != null
        val newViewIsText = !newViewIsIcon

        val oldViewIsIcon = oldView is AppCompatImageButton
        val oldViewIsText = !oldViewIsIcon

        val needsNewView = (oldViewIsIcon && newViewIsText) || (oldViewIsText && newViewIsIcon)

        // 4. 古いビヘイビアをデタッチ
        detachKeyBehavior(info.controller)

        val newView: View
        if (needsNewView) {
            // Viewタイプが異なる場合：Viewを再生成して差し替える
            newView = createKeyView(newKeyData) // 新しいViewを生成
            newView.layoutParams = oldView.layoutParams // レイアウトパラメータは引き継ぐ

            this.removeViewAt(info.index) // 古いViewをGridから削除
            this.addView(newView, info.index) // 新しいViewを同じ位置に追加
        } else {
            // Viewタイプが同じ場合：Viewの表示内容だけ更新
            Log.d("FlickKeyboardView", "updateDynamicKey: Updating View for $keyId")
            newView = oldView
            updateKeyVisuals(newView, newKeyData) // 表示だけ更新
        }

        // 5. 新しいビヘイビアをアタッチ
        val newController = attachKeyBehavior(newView, newKeyData)

        // 6. 管理マップの情報を更新
        info.view = newView
        info.keyData = newKeyData
        info.controller = newController
        
        setupAccessibility()
    }

    /** keyDataに基づいてViewを生成し、基本的な設定（背景、テキスト、パディング等）を行います */
    private fun createKeyView(keyData: KeyData): View {
        val (leftInset, topInset, rightInset, bottomInset) = if (keyData.isSpecialKey) {
            listOf(6, 12, 6, 6)
        } else {
            listOf(6, 9, 6, 9)
        }

        val isDarkTheme = context.isDarkThemeOn()
        // 角丸サイズを統一
        val commonCornerRadius = dpToPx(8).toFloat()

        val keyView: View = if (keyData.isSpecialKey && keyData.drawableResId != null) {
            // ■■■ 1. 画像ボタン (AppCompatImageButton) ■■■
            AppCompatImageButton(context).apply {
                isFocusable = false
                elevation = 0f
                setImageResource(keyData.drawableResId)
                contentDescription = keyData.label
                scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE

                if (themeMode == "custom") {
                    // 影の分だけ中身を小さく見せる必要があるためパディングを設定
                    setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
                }

                val originalBg = ContextCompat.getDrawable(
                    context,
                    if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                )
                val insetBg = android.graphics.drawable.InsetDrawable(
                    originalBg, leftInset, topInset, rightInset, bottomInset
                )
                background = insetBg

                if (keyData.isHiLighted) {
                    isPressed = true
                }

                // ★ テーマ適用
                when (themeMode) {
                    "custom" -> {
                        if (customBorderEnable) {
                            setDrawableSolidColor(customSpecialKeyColor)
                            setColorFilter(customSpecialKeyTextColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            // 1. ベース（ニューモーフィズム）- QWERTYと同じロジック
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = customSpecialKeyColor,
                                radius = commonCornerRadius
                            )

                            // 2. 上層（透明なSegmentedDrawable）
                            // STANDARD_FLICKと見た目を合わせるため、アイコンキーにもダミーのSegmentedDrawableを重ねる
                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = customSpecialKeyColor,
                                textColor = customSpecialKeyTextColor,
                                cornerRadius = commonCornerRadius
                            )

                            // 3. レイヤー化とインセット設定
                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2) // QWERTYに合わせるため小さく
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                            background = layerDrawable
                            setColorFilter(customSpecialKeyTextColor)
                        }
                    }
                }

                if (liquidGlassEnable) {
                    this.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        } else {
            // ■■■ 2. テキストボタン (AutoSizeButton) ■■■
            AutoSizeButton(context).apply {
                isFocusable = false
                isAllCaps = false
                elevation = 0f

                if (!keyData.isSpecialKey) {
                    setDefaultTextSize(defaultTextSize)
                }

                if (keyData.label.contains("\n")) {
                    val parts = keyData.label.split("\n", limit = 2)
                    val primaryText = parts[0]
                    val secondaryText = parts.getOrNull(1) ?: ""
                    val spannable = SpannableString(keyData.label)
                    spannable.setSpan(
                        AbsoluteSizeSpan(spToPx(16f)),
                        0,
                        primaryText.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    if (secondaryText.isNotEmpty()) {
                        spannable.setSpan(
                            AbsoluteSizeSpan(spToPx(10f)),
                            primaryText.length + 1,
                            keyData.label.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                    }
                    this.maxLines = 2
                    this.setLineSpacing(0f, 0.9f)
                    this.setPadding(0, dpToPx(4), 0, dpToPx(4))
                    this.gravity = Gravity.CENTER
                    this.text = spannable
                } else {
                    text = keyData.label
                    gravity = Gravity.CENTER
                }

                val originalBg: Drawable? =
                    if (keyData.isSpecialKey) {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_side_bg_material else com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light
                        )
                    } else if (keyData.keyType != KeyType.STANDARD_FLICK) {
                        ContextCompat.getDrawable(
                            context,
                            if (isDarkTheme) com.kazumaproject.core.R.drawable.ten_keys_center_bg_material else com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light
                        )
                    } else {
                        null
                    }

                originalBg?.let {
                    val insetBg = android.graphics.drawable.InsetDrawable(
                        it, leftInset, topInset, rightInset, bottomInset
                    )
                    background = insetBg
                }

                // ★ テーマ適用
                when (themeMode) {
                    "custom" -> {
                        if (customBorderEnable) {
                            setDrawableSolidColor(customKeyColor)
                            setTextColor(customKeyTextColor)
                            setBorder(customBorderColor, borderWidth)
                        } else {
                            val targetBaseColor =
                                if (keyData.isSpecialKey) customSpecialKeyColor else customKeyColor
                            val targetTextColor =
                                if (keyData.isSpecialKey) customSpecialKeyTextColor else customKeyTextColor
                            val targetHighlightColor = if (keyData.isSpecialKey) manipulateColor(
                                customSpecialKeyColor,
                                1.2f
                            ) else customSpecialKeyColor

                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = targetBaseColor,
                                radius = commonCornerRadius
                            )

                            val segmentedDrawable = SegmentedBackgroundDrawable(
                                label = "",
                                baseColor = Color.TRANSPARENT,
                                highlightColor = targetHighlightColor,
                                textColor = targetTextColor,
                                cornerRadius = commonCornerRadius
                            )

                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2) // QWERTYに合わせる
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)

                            background = layerDrawable
                            setTextColor(targetTextColor)
                        }
                    }
                }

                if (liquidGlassEnable) {
                    setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }
        }

        // LayoutParamsの設定
        val params = LayoutParams().apply {
            rowSpec = spec(keyData.row, keyData.rowSpan, FILL, 1f)
            columnSpec = spec(keyData.column, keyData.colSpan, FILL, 1f)
            width = 0
            height = 0

            if (themeMode == "custom" && !customBorderEnable) {
                setMargins(3, 6, 3, 6)
            } else {
                if (keyData.keyType == KeyType.STANDARD_FLICK) {
                    setMargins(6, 9, 6, 9)
                }
            }
        }
        keyView.layoutParams = params
        updateKeyVisuals(keyView, keyData)
        return keyView
    }

    /**
     * 指定された色(baseColor)を元に、ニューモーフィズムのDrawableを動的に生成する
     * QWERTYKeyboardViewと同じロジックを使用
     */
    private fun getDynamicNeumorphDrawable(baseColor: Int, radius: Float): Drawable {
        // 1. 色の計算 (manipulateColorを使用)
        val highlightColor = manipulateColor(baseColor, 1.2f)
        val shadowColor = manipulateColor(baseColor, 0.8f)

        // 2. ピクセル単位のオフセット量
        val offset = dpToPx(4)
        val padding = dpToPx(2)

        // --- A. 通常状態 (Idle) ---
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(shadowColor)
        }
        val highlightDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(highlightColor)
        }
        val surfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(baseColor)
        }

        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))

        // Shadow: 左と上を空けて右下にずらす
        idleLayer.setLayerInset(0, offset, offset, 0, 0)
        // Highlight: 右と下を空けて左上にずらす
        idleLayer.setLayerInset(1, 0, 0, offset, offset)
        // Surface: 四方を少し空けて中央に配置
        idleLayer.setLayerInset(2, padding, padding, padding, padding)


        // --- B. 押下状態 (Pressed) ---
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            // ベース色より少し暗くすることで「押し込まれた」感を出す
            setColor(manipulateColor(baseColor, 0.95f))
        }

        val pressedLayer = LayerDrawable(arrayOf(pressedDrawable))
        // サイズを変えないため、IdleのSurfaceと同じ位置に合わせるためのInset
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)


        // --- C. StateListDrawable ---
        val stateList = android.graphics.drawable.StateListDrawable()
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
        stateList.addState(intArrayOf(), idleLayer)

        return stateList
    }

    /** Viewにリスナーやフリックコントローラーをアタッチします */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachKeyBehavior(keyView: View, keyData: KeyData): Any? {
        val layout = currentLayout ?: return null // currentLayoutが必須

        when (keyData.keyType) {
            KeyType.CIRCULAR_FLICK -> {
                val flickKeyMapsList = layout.flickKeyMaps[keyData.label]
                Log.d(
                    "FlickKeyboardView KeyType.CIRCULAR_FLICK",
                    "$flickKeyMapsList"
                )
                if (!flickKeyMapsList.isNullOrEmpty()) {
                    val controller = CustomAngleFlickController(context, flickSensitivity).apply {
                        // ( ... Controllerの各種設定 ... )
                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)
                        val dynamicColorTheme = when (themeMode) {
                            "default" -> {
                                FlickPopupColorTheme(
                                    segmentColor = surfaceContainerLow,
                                    segmentHighlightGradientStartColor = secondaryColor,
                                    segmentHighlightGradientEndColor = secondaryColor,
                                    centerGradientStartColor = surfaceContainerHighest,
                                    centerGradientEndColor = surfaceContainerLow,
                                    centerHighlightGradientStartColor = secondaryColor,
                                    centerHighlightGradientEndColor = secondaryColor,
                                    separatorColor = textColor,
                                    textColor = textColor
                                )
                            }

                            "custom" -> {
                                FlickPopupColorTheme(
                                    segmentColor = customSpecialKeyColor,
                                    segmentHighlightGradientStartColor = customSpecialKeyColor,
                                    segmentHighlightGradientEndColor = customSpecialKeyColor,
                                    centerGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        0.8f
                                    ),
                                    centerHighlightGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerHighlightGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        0.8f
                                    ),
                                    separatorColor = customSpecialKeyTextColor,
                                    textColor = customSpecialKeyTextColor
                                )
                            }

                            else -> {
                                FlickPopupColorTheme(
                                    segmentColor = surfaceContainerLow,
                                    segmentHighlightGradientStartColor = secondaryColor,
                                    segmentHighlightGradientEndColor = secondaryColor,
                                    centerGradientStartColor = surfaceContainerHighest,
                                    centerGradientEndColor = surfaceContainerLow,
                                    centerHighlightGradientStartColor = secondaryColor,
                                    centerHighlightGradientEndColor = secondaryColor,
                                    separatorColor = textColor,
                                    textColor = textColor
                                )
                            }
                        }
                        setPopupColors(dynamicColorTheme)
                        this.listener = object : CustomAngleFlickController.FlickListener {
                            override fun onFlick(direction: FlickDirection, character: String) {
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
                                        isFlick = direction != FlickDirection.TAP
                                    )
                                }
                            }

                            override fun onStateChanged(
                                view: View,
                                newMap: Map<FlickDirection, String>
                            ) {

                            }

                            override fun onFlickDirectionChanged(newDirection: FlickDirection) {
                                this@FlickKeyboardView.listener?.onFlickDirectionChanged(
                                    newDirection
                                )
                            }
                        }
                        val stringMaps = flickKeyMapsList.map { actionMap ->
                            actionMap.mapValues { (_, flickAction) ->
                                (flickAction as? FlickAction.Input)?.char ?: ""
                            }
                        }
                        attach(keyView, stringMaps)
                        val newCenter = 64f * circularViewScale
                        val newOrbit = 170f * circularViewScale
                        val newTextSize = 55f * circularViewScale
                        setPopupViewSize(
                            orbit = newOrbit,
                            centerRadius = newCenter,
                            textSize = newTextSize
                        )
                    }
                    val ranges = customAngleAndRange.ifEmpty {
                        mapOf(
                            // UP (上): 270度を中心に ±45度
                            // 開始: 225度, 範囲: 90度 (225° 〜 315°)
                            FlickDirection.UP to Pair(225f, 90f),

                            // UP_RIGHT_FAR (右): 0度(360度)を中心に ±45度
                            // 開始: 315度, 範囲: 90度 (315° 〜 45°) ※0度をまたぐ設定
                            FlickDirection.UP_RIGHT_FAR to Pair(315f, 90f),

                            // DOWN (下): 90度を中心に ±45度
                            // 開始: 45度, 範囲: 90度 (45° 〜 135°)
                            FlickDirection.DOWN to Pair(45f, 90f),

                            // UP_LEFT_FAR (左): 180度を中心に ±45度
                            // 開始: 135度, 範囲: 90度 (135° 〜 225°)
                            FlickDirection.UP_LEFT_FAR to Pair(135f, 90f)
                        )
                    }
                    controller.setFlickRanges(ranges)
                    flickControllers.add(controller)
                    return controller
                }
            }

            KeyType.CROSS_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                Log.d(
                    "FlickKeyboardView KeyType.CROSS_FLICK",
                    "$flickActionMap"
                )
                if (flickActionMap != null) {
                    val controller = CrossFlickInputController(context).apply {
                        this.listener = object : CrossFlickInputController.CrossFlickListener {
                            override fun onFlick(flickAction: FlickAction, isFlick: Boolean) {
                                when (flickAction) {
                                    is FlickAction.Input -> this@FlickKeyboardView.listener?.onKey(
                                        flickAction.char, isFlick = true
                                    )

                                    is FlickAction.Action -> this@FlickKeyboardView.listener?.onAction(
                                        flickAction.action, view = keyView, isFlick = isFlick
                                    )
                                }
                            }

                            override fun onFlickLongPress(flickAction: FlickAction) {
                                // TalkBack有効時は長押しを無効化
                                if (isTouchExplorationEnabled()) return
                                
                                when (flickAction) {
                                    is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionLongPress(
                                        flickAction.action
                                    )

                                    is FlickAction.Input -> {}
                                }
                            }

                            override fun onFlickUpAfterLongPress(
                                flickAction: FlickAction,
                                isFlick: Boolean
                            ) {
                                when (flickAction) {
                                    is FlickAction.Action -> this@FlickKeyboardView.listener?.onFlickActionUpAfterLongPress(
                                        flickAction.action, isFlick = isFlick
                                    )

                                    is FlickAction.Input -> {}
                                }
                            }
                        }
                        attach(keyView, flickActionMap)
                    }
                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }
                    crossFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.STANDARD_FLICK -> {
                var flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                // isFlickableがfalseの場合はTAP以外のフリックマップを削除してフリック入力を無効化する
                if (!keyData.isFlickable && flickActionMap != null) {
                    flickActionMap = flickActionMap.filterKeys { it == FlickDirection.TAP }
                }
                if (flickActionMap != null && keyView is Button) {

                    val label = keyData.label
                    val isDarkTheme = context.isDarkThemeOn()

                    val segmentedDrawable: SegmentedBackgroundDrawable

                    if (themeMode == "custom") {

                        if (customBorderEnable) {
                            // 重要: AppCompatButton などの tint が枠線/塗りを壊すことがあるので無効化
                            keyView.backgroundTintList = null

                            // 下層: 枠線付きベース
                            val baseCorner = dpToPx(8).toFloat()
                            val baseWithBorder = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = baseCorner
                                setColor(customKeyColor)
                                setStroke(borderWidth, customBorderColor)
                            }

                            // 上層: ガイド（透明ベースで下層を見せる）
                            segmentedDrawable = SegmentedBackgroundDrawable(
                                label = label,
                                baseColor = Color.TRANSPARENT,
                                highlightColor = manipulateColor(customKeyColor, 1.2f),
                                textColor = customKeyTextColor,
                                cornerRadius = baseCorner
                            )

                            val layer = LayerDrawable(arrayOf(baseWithBorder, segmentedDrawable))
                            val inset = dpToPx(2)
                            layer.setLayerInset(1, inset, inset, inset, inset)

                            keyView.background = layer
                            keyView.setTextColor(Color.TRANSPARENT) // 既存仕様（ガイド描画に任せる）
                        } else {
                            // --- 既存のニューモーフィズムモードをそのまま ---
                            val neumorphDrawable = getDynamicNeumorphDrawable(
                                baseColor = customKeyColor,
                                radius = dpToPx(8).toFloat()
                            )

                            segmentedDrawable = SegmentedBackgroundDrawable(
                                label = label,
                                baseColor = Color.TRANSPARENT,
                                highlightColor = manipulateColor(customKeyColor, 1.2f),
                                textColor = customKeyTextColor,
                                cornerRadius = dpToPx(8).toFloat()
                            )

                            val layerDrawable =
                                LayerDrawable(arrayOf(neumorphDrawable, segmentedDrawable))
                            val inset = dpToPx(2)
                            layerDrawable.setLayerInset(1, inset, inset, inset, inset)
                            keyView.background = layerDrawable
                            keyView.setTextColor(Color.TRANSPARENT)
                        }

                    } else {
                        // --- 既存のデフォルトモードをそのまま ---
                        val keyBaseColor =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest)
                            else context.getColorFromAttr(R.attr.colorSurface)
                        val keyHighlightColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val keyTextColor = context.getColorFromAttr(R.attr.colorOnSurface)

                        segmentedDrawable = SegmentedBackgroundDrawable(
                            label = label,
                            baseColor = keyBaseColor,
                            highlightColor = keyHighlightColor,
                            textColor = keyTextColor,
                            cornerRadius = 20f
                        )
                        keyView.background = segmentedDrawable
                        keyView.setTextColor(Color.TRANSPARENT)
                    }


                    val controller = StandardFlickInputController(context).apply {
                        this.listener =
                            object : StandardFlickInputController.StandardFlickListener {
                                override fun onFlick(character: String) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        character,
                                        isFlick = true
                                    )
                                }
                            }

                        val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                            (flickAction as? FlickAction.Input)?.char ?: ""
                        }

                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                R.attr.colorSurface
                            )
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)


                        val dynamicColorTheme = when (themeMode) {
                            "default" -> {
                                FlickPopupColorTheme(
                                    segmentColor = surfaceContainerHighest,
                                    segmentHighlightGradientStartColor = secondaryColor,
                                    segmentHighlightGradientEndColor = secondaryColor,
                                    centerGradientStartColor = surfaceContainerHighest,
                                    centerGradientEndColor = surfaceContainerLow,
                                    centerHighlightGradientStartColor = secondaryColor,
                                    centerHighlightGradientEndColor = secondaryColor,
                                    separatorColor = textColor,
                                    textColor = textColor
                                )
                            }

                            "custom" -> {
                                FlickPopupColorTheme(
                                    segmentColor = customSpecialKeyColor,
                                    segmentHighlightGradientStartColor = customSpecialKeyColor,
                                    segmentHighlightGradientEndColor = customSpecialKeyColor,
                                    centerGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        0.8f
                                    ),
                                    centerHighlightGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerHighlightGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        0.8f
                                    ),
                                    separatorColor = customSpecialKeyTextColor,
                                    textColor = customSpecialKeyTextColor
                                )
                            }

                            else -> {
                                FlickPopupColorTheme(
                                    segmentColor = surfaceContainerHighest,
                                    segmentHighlightGradientStartColor = secondaryColor,
                                    segmentHighlightGradientEndColor = secondaryColor,
                                    centerGradientStartColor = surfaceContainerHighest,
                                    centerGradientEndColor = surfaceContainerLow,
                                    centerHighlightGradientStartColor = secondaryColor,
                                    centerHighlightGradientEndColor = secondaryColor,
                                    separatorColor = textColor,
                                    textColor = textColor
                                )
                            }
                        }
                        setPopupColors(dynamicColorTheme)
                        attach(keyView, stringMap, segmentedDrawable)
                    }

                    standardFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.PETAL_FLICK -> {
                val flickActionMap = layout.flickKeyMaps[keyData.label]?.firstOrNull()
                Log.d(
                    "FlickKeyboardView KeyType.PETAL_FLICK",
                    "$flickActionMap"
                )
                if (flickActionMap != null) {
                    val controller = GridFlickInputController(
                        context, flickSensitivity
                    ).apply {
                        // ( ... Controllerの各種設定 ... )
                        val isDarkTheme = context.isDarkThemeOn()
                        val secondaryColor =
                            context.getColorFromAttr(R.attr.colorSecondaryContainer)
                        val surfaceContainerLow =
                            context.getColorFromAttr(R.attr.colorSurfaceContainerLow)
                        val surfaceContainerHighest =
                            if (isDarkTheme) context.getColorFromAttr(R.attr.colorSurfaceContainerHighest) else context.getColorFromAttr(
                                R.attr.colorSurface
                            )
                        val textColor =
                            context.getColor(com.kazumaproject.core.R.color.keyboard_icon_color)

                        val dynamicColorTheme = when (themeMode) {
                            "default" -> {
                                FlickPopupColorTheme(
                                    segmentColor = surfaceContainerHighest,
                                    segmentHighlightGradientStartColor = secondaryColor,
                                    segmentHighlightGradientEndColor = secondaryColor,
                                    centerGradientStartColor = surfaceContainerHighest,
                                    centerGradientEndColor = surfaceContainerLow,
                                    centerHighlightGradientStartColor = secondaryColor,
                                    centerHighlightGradientEndColor = secondaryColor,
                                    separatorColor = textColor,
                                    textColor = textColor
                                )
                            }

                            "custom" -> {
                                FlickPopupColorTheme(
                                    segmentColor = customSpecialKeyColor,
                                    segmentHighlightGradientStartColor = customSpecialKeyColor,
                                    segmentHighlightGradientEndColor = customSpecialKeyColor,
                                    centerGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        0.8f
                                    ),
                                    centerHighlightGradientStartColor = manipulateColor(
                                        customSpecialKeyColor,
                                        1.2f
                                    ),
                                    centerHighlightGradientEndColor = manipulateColor(
                                        customSpecialKeyColor,
                                        0.8f
                                    ),
                                    separatorColor = customSpecialKeyTextColor,
                                    textColor = customSpecialKeyTextColor
                                )
                            }

                            else -> {
                                FlickPopupColorTheme(
                                    segmentColor = surfaceContainerHighest,
                                    segmentHighlightGradientStartColor = secondaryColor,
                                    segmentHighlightGradientEndColor = secondaryColor,
                                    centerGradientStartColor = surfaceContainerHighest,
                                    centerGradientEndColor = surfaceContainerLow,
                                    centerHighlightGradientStartColor = secondaryColor,
                                    centerHighlightGradientEndColor = secondaryColor,
                                    separatorColor = textColor,
                                    textColor = textColor
                                )
                            }
                        }
                        setPopupColors(dynamicColorTheme)
                        elevation = 1f
                        this.listener = object : GridFlickInputController.GridFlickListener {
                            override fun onFlick(character: String, isFlick: Boolean) {
                                this@FlickKeyboardView.listener?.onKey(
                                    character, isFlick = isFlick
                                )
                            }
                        }
                        val stringMap = flickActionMap.mapValues { (_, flickAction) ->
                            (flickAction as? FlickAction.Input)?.char ?: ""
                        }
                        attach(keyView, stringMap)
                    }
                    petalFlickControllers.add(controller)
                    return controller
                }
            }

            KeyType.NORMAL -> {
                // ▼▼▼ 修正: newKeyData.action を参照する ▼▼▼
                keyData.action?.let { action ->
                    Log.d(
                        "FlickKeyboardView KeyType.NORMAL",
                        "key data: $keyData"
                    )
                    var isLongPressTriggered = false
                    keyView.setOnClickListener {
                        // ▼▼▼ 修正: info.keyData.action を参照して最新のアクションを実行する ▼▼▼
                        val currentAction = dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                        Log.d(
                            "FlickKeyboardView KeyType.NORMAL",
                            "currentAction: $currentAction"
                        )
                        this@FlickKeyboardView.listener?.onAction(
                            currentAction, view = keyView, isFlick = false
                        )
                    }
                    keyView.setOnLongClickListener {
                        // TalkBack有効時は長押しを無効化
                        if (isTouchExplorationEnabled()) {
                            false
                        } else {
                            val currentAction = dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                            isLongPressTriggered =
                                true; this@FlickKeyboardView.listener?.onActionLongPress(currentAction); true
                        }
                    }
                    keyView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                            if (isLongPressTriggered) {
                                if (event.action == MotionEvent.ACTION_UP) {
                                    val currentAction =
                                        dynamicKeyMap[keyData.keyId]?.keyData?.action ?: action
                                    this@FlickKeyboardView.listener?.onActionUpAfterLongPress(
                                        currentAction
                                    )
                                }
                                isLongPressTriggered = false
                            }
                        }
                        false
                    }
                }
                return null // コントローラーなし
            }

            KeyType.TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.keyId]
                    ?: layout.twoStepFlickKeyMaps[keyData.label]
                if (twoStepMap != null) {
                    val controller = TfbiInputController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {
                        this.listener = object : TfbiInputController.TfbiListener {
                            override fun onFlick(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                val character = twoStepMap[first]?.get(second) ?: ""
                                Log.d(
                                    "FlickKeyboardView KeyType.TWO_STEP_FLICK",
                                    "$character $first $second"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
                                        isFlick = !(first == TfbiFlickDirection.TAP && second == TfbiFlickDirection.TAP)
                                    )
                                }
                            }
                        }
                        attach(
                            view = keyView,
                            provider = { first, second ->
                                twoStepMap[first]?.get(second) ?: ""
                            }
                        )
                    }
                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }
                    tfbiControllers.add(controller)
                    return controller
                }
            }

            KeyType.STICKY_TWO_STEP_FLICK -> {
                val twoStepMap = layout.twoStepFlickKeyMaps[keyData.label]
                if (twoStepMap != null) {
                    val controller = TfbiStickyFlickController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {
                        this.listener = object : TfbiStickyFlickController.TfbiListener {
                            override fun onFlick(
                                first: TfbiFlickDirection,
                                second: TfbiFlickDirection
                            ) {
                                val character = twoStepMap[first]?.get(second) ?: ""
                                Log.d(
                                    "FlickKeyboardView KeyType.STICKY_TWO_STEP_FLICK",
                                    "$character $first $second"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
                                        isFlick = !(first == TfbiFlickDirection.TAP && second == TfbiFlickDirection.TAP)
                                    )
                                }
                            }
                        }
                        attach(
                            view = keyView,
                            provider = { first, second ->
                                twoStepMap[first]?.get(second) ?: ""
                            }
                        )
                    }
                    stickyTfbiControllers.add(controller)
                    return controller
                }
            }

            KeyType.HIERARCHICAL_FLICK -> {
                val statefulNode = layout.hierarchicalFlickMaps[keyData.label]

                if (statefulNode != null) {
                    Log.d(
                        "AttachBehavior",
                        "-> Attaching TfbiHierarchicalFlickController for ${keyData.label}"
                    )
                    val controller = TfbiHierarchicalFlickController(
                        context,
                        flickSensitivity = flickSensitivity.toFloat()
                    ).apply {

                        this.listener = object : TfbiHierarchicalFlickController.TfbiListener {
                            override fun onFlick(character: String) {
                                Log.d(
                                    "FlickKeyboardView KeyType.HIERARCHICAL_FLICK",
                                    "Char: $character"
                                )
                                if (character.isNotEmpty()) {
                                    this@FlickKeyboardView.listener?.onKey(
                                        text = character,
                                        isFlick = true // 階層フリックは常true
                                    )
                                }
                            }

                            override fun onModeChanged(newLabel: String) {
                                Log.d(
                                    "FlickKeyboardView",
                                    "onModeChanged: keyId=${keyData.keyId}, newLabel=$newLabel"
                                )

                                // 1. dynamicKeyMap のキャッシュを更新 (存在する場合)
                                keyData.keyId?.let { id ->
                                    dynamicKeyMap[id]?.let { info ->
                                        info.keyData = info.keyData.copy(label = newLabel)
                                    }
                                }

                                // 2. 実際のViewの表示を更新
                                val newVisualKeyData = keyData.copy(label = newLabel)
                                updateKeyVisuals(keyView, newVisualKeyData)
                            }
                        }

                        attach(keyView, statefulNode)
                    }
                    when (themeMode) {
                        "custom" -> {
                            controller.setPopupColors(
                                backgroundColor = customSpecialKeyColor,
                                highlightedColor = manipulateColor(customSpecialKeyColor, 1.2f),
                                textColor = customSpecialKeyTextColor
                            )
                        }
                    }
                    hierarchicalTfbiControllers.add(controller)
                    return controller
                } else {
                    Log.e(
                        "AttachBehavior",
                        "-> FAILED HIERARCHICAL_FLICK: statefulNode is NULL for key '${keyData.label}'"
                    )
                }
            }
        }
        return null
    }

    /** アタッチされたビヘイビア（コントローラー）を解除します */
    private fun detachKeyBehavior(controller: Any?) {
        // コントローラーを解除
        when (controller) {
            is CustomAngleFlickController -> {
                controller.cancel()
                flickControllers.remove(controller)
            }

            is CrossFlickInputController -> {
                controller.cancel()
                crossFlickControllers.remove(controller)
            }

            is StandardFlickInputController -> {
                controller.cancel()
                standardFlickControllers.remove(controller)
            }

            is GridFlickInputController -> {
                controller.cancel()
                petalFlickControllers.remove(controller)
            }

            is TfbiInputController -> {
                controller.cancel()
                tfbiControllers.remove(controller)
            }

            is TfbiStickyFlickController -> {
                controller.cancel()
                stickyTfbiControllers.remove(controller)
            }

            is TfbiHierarchicalFlickController -> {
                controller.cancel()
                hierarchicalTfbiControllers.remove(controller)
            }
        }
    }

    /** 既存Viewのビジュアル（テキスト/アイコン）のみを更新します */
    private fun updateKeyVisuals(view: View, keyData: KeyData) {
        val announcement = buildKeyAnnouncement(keyData)
        view.contentDescription = announcement

        // TalkBack対応: 各キーをアクセシビリティ対象にする
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        view.isFocusable = true
        
        // TalkBackのダブルタップを有効にするために常にクリック可能にしてリスナーを設定する
        view.isClickable = true
        view.setOnClickListener {
            if (isTouchExplorationEnabled()) {
                handleKeyClick(view)
            }
        }

        // TenKey.kt と同様のアクセシビリティデリゲートを設定
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                if (announcement.isNotEmpty()) {
                    info.text = announcement
                    info.contentDescription = announcement
                }
                
                // クラス名を空にし、役割記述をゼロ幅スペースにすることで「ボタン」の読み込みを完全に阻止する
                info.className = ""
                info.roleDescription = "\u200B"

                if (isTouchExplorationEnabled()) {
                    // クラス名をButtonにし、Clickable, LongClickableを有効化（TenKey.ktと同様）
                    info.className = "android.widget.Button"
                    info.isClickable = true
                    info.isLongClickable = true

                    val flickActionMap = currentLayout?.flickKeyMaps?.get(keyData.label)?.firstOrNull()
                    if (flickActionMap != null) {
                        flickActionMap.forEach { (direction, flickAction) ->
                            if (direction != FlickDirection.TAP) {
                                val actionId = getAccessibilityActionId(direction)
                                val actionLabel = getAccessibilityActionLabel(direction, flickAction)
                                if (actionId != null && actionLabel != null) {
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(actionId, actionLabel))
                                }
                            }
                        }
                    }
                } else {
                    info.isClickable = false
                    info.isLongClickable = false
                    info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
                    info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK)
                }
            }

            override fun performAccessibilityAction(
                host: View,
                action: Int,
                args: android.os.Bundle?
            ): Boolean {
                if (isTouchExplorationEnabled()) {
                    val direction = getFlickDirectionFromActionId(action)
                    if (direction != null) {
                        val flickActionMap = currentLayout?.flickKeyMaps?.get(keyData.label)?.firstOrNull()
                        val flickAction = flickActionMap?.get(direction)
                        if (flickAction != null) {
                            triggerFlickAction(flickAction, view)
                            return true
                        }
                    }
                }
                return super.performAccessibilityAction(host, action, args)
            }
        })

        when (view) {
            is AppCompatImageButton -> {
                keyData.drawableResId?.let { view.setImageResource(it) }
                view.isPressed = keyData.isHiLighted
            }

            is AutoSizeButton -> {
                if (keyData.label.contains("\n")) {
                    val parts = keyData.label.split("\n", limit = 2)
                    val primaryText = parts[0]
                    val secondaryText = parts.getOrNull(1) ?: ""
                    val spannable = SpannableString(keyData.label)
                    spannable.setSpan(
                        AbsoluteSizeSpan(spToPx(16f)),
                        0,
                        primaryText.length,
                        Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    )
                    if (secondaryText.isNotEmpty()) {
                        spannable.setSpan(
                            AbsoluteSizeSpan(spToPx(10f)),
                            primaryText.length + 1,
                            keyData.label.length,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                        )
                    }
                    view.text = spannable
                } else {
                    view.text = keyData.label
                }
                view.isPressed = keyData.isHiLighted
            }
        }
    }

    private val motionTargets = mutableMapOf<Int, View>()
    private val pointerDownTime = mutableMapOf<Int, Long>()
    private val TAG = "FlickKeyboardViewTouch"

    private fun findTargetView(x: Float, y: Float): View? {
        // まず、キーの矩形内に直接ヒットしたかチェック
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.getHitRect(hitRect)
            if (hitRect.contains(x.toInt(), y.toInt())) {
                return child
            }
        }

        // TalkBack有効時は、厳密なタッチ判定を行う
        // 背景（隙間）をタッチした場合に最寄りのキー（スペースなど）が返されないようにする
        if (isTouchExplorationEnabled()) {
            return null
        }

        // 直接ヒットしなかった場合（マージンなどをタッチした場合）、最も近いキーを探す
        var nearestChild: View? = null
        var minDistance = Double.MAX_VALUE

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childCenterX = child.left + child.width / 2f
            val childCenterY = child.top + child.height / 2f
            val distance = sqrt((x - childCenterX).pow(2) + (y - childCenterY).pow(2))

            if (distance < minDistance) {
                minDistance = distance.toDouble()
                nearestChild = child
            }
        }
        return nearestChild
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.actionMasked

        // TalkBack有効時かつホバー経由でない（＝スワイプ中のダブルタップなどの実タッチ）場合は、
        // インターセプトせずに子ビューにイベントを渡す。
        if (isTouchExplorationEnabled() && !isCalledFromHoverEvent) {
            return false
        }

        // 最初の指が触れた瞬間に true を返すことで、
        // この後のすべてのタッチイベント(MOVE, UP, POINTER_DOWNなど)を
        // このビューの onTouchEvent で処理することを決定する。
        if (action == MotionEvent.ACTION_DOWN) {
            return true
        }

        // すでにインターセプトしている場合は、子には渡さない
        if (motionTargets.isNotEmpty()) {
            return true
        }

        return super.onInterceptTouchEvent(ev)
    }

    override fun onInterceptHoverEvent(event: MotionEvent): Boolean {
        // DTalker IME方式: 全てのホバーイベントを親（このView）でインターセプトし、
        // 子要素への自動的な配信を阻止する。これにより、onHoverEvent で集中管理が可能になる。
        if (isTouchExplorationEnabled()) {
            return true
        }
        return super.onInterceptHoverEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        // DTalker IMEのテンキー入力方式の実装: ホバーイベントを直接制御して「スライド入力」を実現する
        if (accessibilityManager.isTouchExplorationEnabled && event.pointerCount == 1) {
            val action = event.action
            val x = event.x
            val y = event.y
            val targetView = findTargetView(x, y)
            val keyInfo = targetView?.let { findKeyInfoForView(it) }
            val keyLabel = keyInfo?.keyData?.label

            // screenX, screenY の計算（画面上の絶対座標、TenKeyと同じ）
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            val screenX = x + location[0]
            val screenY = y + location[1]

            when (action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    if (targetView != lastHoverTarget) {
                        lastHoverTarget = targetView
                        targetView?.let { view ->
                            // 強制的にこれまでの読み上げを中断し、新しいキーのフォーカスイベントを飛ばす
                            if (accessibilityManager.isTouchExplorationEnabled) {
                                accessibilityManager.interrupt()
                            }
                            // そのキーに対してホバーイベントを送信することで、TalkBackに読み上げを促す
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                        }
                    }

                    // ドラッグ状態の初期化
                    if (keyLabel == "CursorMoveRight") {
                        isHoverDraggingRightCursor = true
                        hoverRightCursorDragStartX = screenX
                        hoverRightCursorDragEndX = screenX
                        hoverRightCursorDragStartY = screenY
                        hoverRightCursorDragEndY = screenY
                        hoverRightCursorDragTopY = screenY
                        isLineStartAnnounced = false
                        isLineEndAnnounced = false
                        isLineUpAnnounced = false
                        isLineDownAnnounced = false
                        isHoverDraggingLeftCursor = false
                        isHoverDraggingDeleteKey = false
                    } else if (keyLabel == "CursorMoveLeft") {
                        isHoverDraggingLeftCursor = true
                        hoverLeftCursorDragStartX = screenX
                        hoverLeftCursorDragEndX = screenX
                        hoverLeftCursorDragStartY = screenY
                        hoverLeftCursorDragEndY = screenY
                        hoverLeftCursorDragTopY = screenY
                        isLeftLineStartAnnounced = false
                        isLeftLineEndAnnounced = false
                        isLeftLineUpAnnounced = false
                        isLeftLineDownAnnounced = false
                        isHoverDraggingRightCursor = false
                        isHoverDraggingDeleteKey = false
                    } else if (keyLabel == "Del") {
                        isHoverDraggingDeleteKey = true
                        hoverDeleteKeyDragStartX = screenX
                        hoverDeleteKeyDragEndX = screenX
                        hoverDeleteKeyDragStartY = screenY
                        hoverDeleteKeyDragEndY = screenY
                        hoverDeleteKeyDragTopY = screenY
                        isDeleteLeftAnnounced = false
                        isDeleteRightAnnounced = false
                        isDeleteUpAnnounced = false
                        isHoverDraggingRightCursor = false
                        isHoverDraggingLeftCursor = false
                    } else {
                        isHoverDraggingRightCursor = false
                        isLineStartAnnounced = false
                        isLineEndAnnounced = false
                        isLineUpAnnounced = false
                        isLineDownAnnounced = false
                        isHoverDraggingLeftCursor = false
                        isLeftLineStartAnnounced = false
                        isLeftLineEndAnnounced = false
                        isLeftLineUpAnnounced = false
                        isLeftLineDownAnnounced = false
                        isHoverDraggingDeleteKey = false
                        isDeleteLeftAnnounced = false
                        isDeleteRightAnnounced = false
                        isDeleteUpAnnounced = false
                    }
                }

                MotionEvent.ACTION_HOVER_MOVE -> {
                    // スライドイン / 静止状態からのドラッグ開始処理 (TenKey.kt準拠)
                    if (keyLabel == "CursorMoveRight") {
                        if (!isHoverDraggingRightCursor) {
                            if (hoverSlideInEntryTime == 0L) {
                                hoverSlideInEntryTime = System.currentTimeMillis()
                                hoverSlideInEntryX = screenX
                                hoverSlideInEntryY = screenY
                            } else {
                                val movementThreshold = 10f
                                val dx = screenX - hoverSlideInEntryX
                                val dy = screenY - hoverSlideInEntryY
                                if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                    hoverSlideInEntryTime = System.currentTimeMillis()
                                    hoverSlideInEntryX = screenX
                                    hoverSlideInEntryY = screenY
                                } else {
                                    val elapsed = System.currentTimeMillis() - hoverSlideInEntryTime
                                    if (elapsed >= 150L) {
                                        isHoverDraggingRightCursor = true
                                        hoverRightCursorDragStartX = screenX
                                        hoverRightCursorDragEndX = screenX
                                        hoverRightCursorDragStartY = screenY
                                        hoverRightCursorDragEndY = screenY
                                        hoverRightCursorDragTopY = screenY
                                        isLineStartAnnounced = false
                                        isLineEndAnnounced = false
                                        isLineUpAnnounced = false
                                        isLineDownAnnounced = false
                                        hoverSlideInEntryTime = 0L
                                        Log.d("FlickKeyboardViewDrag", "ACTION_HOVER_MOVE: Slid onto CursorMoveRight (Hover) and remained stationary. Starting drag.")
                                    }
                                }
                            }
                        }
                    } else {
                        hoverSlideInEntryTime = 0L
                        if (isHoverDraggingRightCursor) {
                            isHoverDraggingRightCursor = false
                            isLineStartAnnounced = false
                            isLineEndAnnounced = false
                            isLineUpAnnounced = false
                            isLineDownAnnounced = false
                        }
                    }

                    if (keyLabel == "CursorMoveLeft") {
                        if (!isHoverDraggingLeftCursor) {
                            if (leftHoverSlideInEntryTime == 0L) {
                                leftHoverSlideInEntryTime = System.currentTimeMillis()
                                leftHoverSlideInEntryX = screenX
                                leftHoverSlideInEntryY = screenY
                            } else {
                                val movementThreshold = 10f
                                val dx = screenX - leftHoverSlideInEntryX
                                val dy = screenY - leftHoverSlideInEntryY
                                if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                    leftHoverSlideInEntryTime = System.currentTimeMillis()
                                    leftHoverSlideInEntryX = screenX
                                    leftHoverSlideInEntryY = screenY
                                } else {
                                    val elapsed = System.currentTimeMillis() - leftHoverSlideInEntryTime
                                    if (elapsed >= 150L) {
                                        isHoverDraggingLeftCursor = true
                                        hoverLeftCursorDragStartX = screenX
                                        hoverLeftCursorDragEndX = screenX
                                        hoverLeftCursorDragStartY = screenY
                                        hoverLeftCursorDragEndY = screenY
                                        hoverLeftCursorDragTopY = screenY
                                        isLeftLineStartAnnounced = false
                                        isLeftLineEndAnnounced = false
                                        isLeftLineUpAnnounced = false
                                        isLeftLineDownAnnounced = false
                                        leftHoverSlideInEntryTime = 0L
                                        Log.d("FlickKeyboardViewDrag", "ACTION_HOVER_MOVE: Slid onto CursorMoveLeft (Hover) and remained stationary. Starting drag.")
                                    }
                                }
                            }
                        }
                    } else {
                        leftHoverSlideInEntryTime = 0L
                        if (isHoverDraggingLeftCursor) {
                            isHoverDraggingLeftCursor = false
                            isLeftLineStartAnnounced = false
                            isLeftLineEndAnnounced = false
                            isLeftLineUpAnnounced = false
                            isLeftLineDownAnnounced = false
                        }
                    }

                    if (keyLabel == "Del") {
                        if (!isHoverDraggingDeleteKey) {
                            if (deleteHoverSlideInEntryTime == 0L) {
                                deleteHoverSlideInEntryTime = System.currentTimeMillis()
                                deleteHoverSlideInEntryX = screenX
                                deleteHoverSlideInEntryY = screenY
                            } else {
                                val movementThreshold = 10f
                                val dx = screenX - deleteHoverSlideInEntryX
                                val dy = screenY - deleteHoverSlideInEntryY
                                if (abs(dx) > movementThreshold || abs(dy) > movementThreshold) {
                                    deleteHoverSlideInEntryTime = System.currentTimeMillis()
                                    deleteHoverSlideInEntryX = screenX
                                    deleteHoverSlideInEntryY = screenY
                                } else {
                                    val elapsed = System.currentTimeMillis() - deleteHoverSlideInEntryTime
                                    if (elapsed >= 150L) {
                                        isHoverDraggingDeleteKey = true
                                        hoverDeleteKeyDragStartX = screenX
                                        hoverDeleteKeyDragEndX = screenX
                                        hoverDeleteKeyDragStartY = screenY
                                        hoverDeleteKeyDragEndY = screenY
                                        hoverDeleteKeyDragTopY = screenY
                                        isDeleteLeftAnnounced = false
                                        isDeleteRightAnnounced = false
                                        isDeleteUpAnnounced = false
                                        deleteHoverSlideInEntryTime = 0L
                                        Log.d("FlickKeyboardViewDrag", "ACTION_HOVER_MOVE: Slid onto Del (Hover) and remained stationary. Starting drag.")
                                    }
                                }
                            }
                        }
                    } else {
                        deleteHoverSlideInEntryTime = 0L
                        if (isHoverDraggingDeleteKey) {
                            isHoverDraggingDeleteKey = false
                            isDeleteLeftAnnounced = false
                            isDeleteRightAnnounced = false
                            isDeleteUpAnnounced = false
                        }
                    }

                    // ピーク座標とドラッグ方向のアナウンス処理
                    if (isHoverDraggingRightCursor) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            if (screenX > hoverRightCursorDragStartX) hoverRightCursorDragStartX = screenX
                            if (screenX < hoverRightCursorDragEndX) hoverRightCursorDragEndX = screenX
                            if (screenY > hoverRightCursorDragEndY) hoverRightCursorDragEndY = screenY
                            if (screenY < hoverRightCursorDragTopY) hoverRightCursorDragTopY = screenY
                        }

                        val dxStart = screenX - hoverRightCursorDragStartX
                        val dxEnd = screenX - hoverRightCursorDragEndX
                        val dyUp = screenY - hoverRightCursorDragEndY
                        val dyDown = screenY - hoverRightCursorDragTopY

                        val threshold = 35f
                        val cancelLeftThreshold = -150f
                        val cancelRightThreshold = 150f
                        val cancelUpThreshold = -150f
                        val cancelDownThreshold = 150f
                        val cancelXThreshold = 60f
                        val cancelYThreshold = 60f

                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverRightCursorDragStartY) <= cancelYThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineStartAnnounced = true
                                announceForAccessibility("行頭")
                                android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - hoverRightCursorDragStartY) <= cancelYThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineEndAnnounced = true
                                announceForAccessibility("行末")
                                android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - hoverRightCursorDragStartX) <= cancelXThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineUpAnnounced = true
                                announceForAccessibility("上カーソル")
                                android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyDown > threshold && dyDown <= cancelDownThreshold && abs(screenX - hoverRightCursorDragStartX) <= cancelXThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineDownAnnounced = true
                                announceForAccessibility("下カーソル")
                                android.widget.Toast.makeText(context, "下カーソル", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isLineStartAnnounced) {
                                (dxStart >= -threshold / 2f) || (dxStart < cancelLeftThreshold) || (abs(screenY - hoverRightCursorDragStartY) > cancelYThreshold)
                            } else if (isLineEndAnnounced) {
                                (dxEnd <= threshold / 2f) || (dxEnd > cancelRightThreshold) || (abs(screenY - hoverRightCursorDragStartY) > cancelYThreshold)
                            } else if (isLineUpAnnounced) {
                                (dyUp >= -threshold / 2f) || (dyUp < cancelUpThreshold) || (abs(screenX - hoverRightCursorDragStartX) > cancelXThreshold)
                            } else if (isLineDownAnnounced) {
                                (dyDown <= threshold / 2f) || (dyDown > cancelDownThreshold) || (abs(screenX - hoverRightCursorDragStartX) > cancelXThreshold)
                            } else false

                            if (shouldCancel) {
                                isLineStartAnnounced = false
                                isLineEndAnnounced = false
                                isLineUpAnnounced = false
                                isLineDownAnnounced = false
                                isHoverDraggingRightCursor = false
                            }
                        }
                    }

                    if (isHoverDraggingLeftCursor) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            if (screenX > hoverLeftCursorDragStartX) hoverLeftCursorDragStartX = screenX
                            if (screenX < hoverLeftCursorDragEndX) hoverLeftCursorDragEndX = screenX
                            if (screenY > hoverLeftCursorDragEndY) hoverLeftCursorDragEndY = screenY
                            if (screenY < hoverLeftCursorDragTopY) hoverLeftCursorDragTopY = screenY
                        }

                        val dxStart = screenX - hoverLeftCursorDragStartX
                        val dxEnd = screenX - hoverLeftCursorDragEndX
                        val dyUp = screenY - hoverLeftCursorDragEndY
                        val dyDown = screenY - hoverLeftCursorDragTopY

                        val threshold = 35f
                        val cancelLeftThreshold = -150f
                        val cancelRightThreshold = 150f
                        val cancelUpThreshold = -150f
                        val cancelDownThreshold = 150f
                        val cancelXThreshold = 60f
                        val cancelYThreshold = 60f

                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverLeftCursorDragStartY) <= cancelYThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineStartAnnounced = true
                                announceForAccessibility("行頭")
                                android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - hoverLeftCursorDragStartY) <= cancelYThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineEndAnnounced = true
                                announceForAccessibility("行末")
                                android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - hoverLeftCursorDragStartX) <= cancelXThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineUpAnnounced = true
                                announceForAccessibility("上カーソル")
                                android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyDown > threshold && dyDown <= cancelDownThreshold && abs(screenX - hoverLeftCursorDragStartX) <= cancelXThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineDownAnnounced = true
                                announceForAccessibility("下カーソル")
                                android.widget.Toast.makeText(context, "下カーソル", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isLeftLineStartAnnounced) {
                                (dxStart >= -threshold / 2f) || (dxStart < cancelLeftThreshold) || (abs(screenY - hoverLeftCursorDragStartY) > cancelYThreshold)
                            } else if (isLeftLineEndAnnounced) {
                                (dxEnd <= threshold / 2f) || (dxEnd > cancelRightThreshold) || (abs(screenY - hoverLeftCursorDragStartY) > cancelYThreshold)
                            } else if (isLeftLineUpAnnounced) {
                                (dyUp >= -threshold / 2f) || (dyUp < cancelUpThreshold) || (abs(screenX - hoverLeftCursorDragStartX) > cancelXThreshold)
                            } else if (isLeftLineDownAnnounced) {
                                (dyDown <= threshold / 2f) || (dyDown > cancelDownThreshold) || (abs(screenX - hoverLeftCursorDragStartX) > cancelXThreshold)
                            } else false

                            if (shouldCancel) {
                                isLeftLineStartAnnounced = false
                                isLeftLineEndAnnounced = false
                                isLeftLineUpAnnounced = false
                                isLeftLineDownAnnounced = false
                                isHoverDraggingLeftCursor = false
                            }
                        }
                    }

                    if (isHoverDraggingDeleteKey) {
                        if (!isDeleteLeftAnnounced) {
                            if (screenX < hoverDeleteKeyDragEndX) hoverDeleteKeyDragEndX = screenX
                        }

                        val dxStart = screenX - hoverDeleteKeyDragStartX
                        val threshold = 35f
                        val cancelLeftThreshold = -150f
                        val cancelRightThreshold = 150f
                        val cancelYThreshold = 60f

                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverDeleteKeyDragStartY) <= cancelYThreshold) {
                            if (!isDeleteLeftAnnounced && !isDeleteRightAnnounced) {
                                isDeleteLeftAnnounced = true
                                val annText = if (isInputComposing) "一括削除" else "行頭まで削除"
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxStart > threshold && dxStart <= cancelRightThreshold && abs(screenY - hoverDeleteKeyDragStartY) <= cancelYThreshold) {
                            if (!isDeleteRightAnnounced && !isDeleteLeftAnnounced) {
                                isDeleteRightAnnounced = true
                                val annText = "行末まで削除"
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isDeleteLeftAnnounced) {
                                (dxStart >= -threshold / 2f) || (dxStart < cancelLeftThreshold) || (abs(screenY - hoverDeleteKeyDragStartY) > cancelYThreshold)
                            } else if (isDeleteRightAnnounced) {
                                (dxStart <= threshold / 2f) || (dxStart > cancelRightThreshold) || (abs(screenY - hoverDeleteKeyDragStartY) > cancelYThreshold)
                            } else false

                            if (shouldCancel) {
                                isDeleteLeftAnnounced = false
                                isDeleteRightAnnounced = false
                                isDeleteUpAnnounced = false
                                isHoverDraggingDeleteKey = false
                            }
                        }
                    }

                    // 通常の読み上げ位置の更新
                    if (targetView != lastHoverTarget) {
                        lastHoverTarget = targetView
                        targetView?.let { view ->
                            if (accessibilityManager.isTouchExplorationEnabled) {
                                accessibilityManager.interrupt()
                            }
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                        }
                    }
                }

                MotionEvent.ACTION_HOVER_EXIT -> {
                    // 指を離した際の処理
                    val buffer = 2f
                    val isSlideOff = x <= buffer || 
                                   x >= (width.toFloat() - buffer) || 
                                   y <= buffer || 
                                   y >= (height.toFloat() - buffer)

                    if (isHoverDraggingRightCursor) {
                        isHoverDraggingRightCursor = false
                        var triggerLineStart = isLineStartAnnounced
                        var triggerLineEnd = isLineEndAnnounced
                        var triggerLineUp = isLineUpAnnounced
                        var triggerLineDown = isLineDownAnnounced

                        // 素早いフリックのフォールバック
                        if (!triggerLineStart && !triggerLineEnd && !triggerLineUp && !triggerLineDown) {
                            val dx = screenX - hoverRightCursorDragStartX
                            val dy = screenY - hoverRightCursorDragStartY
                            val threshold = 35f
                            val cancelXThreshold = 60f
                            val cancelYThreshold = 60f

                            if (abs(dy) <= cancelYThreshold && dx < -threshold) {
                                triggerLineStart = true
                            } else if (abs(dy) <= cancelYThreshold && dx > threshold) {
                                triggerLineEnd = true
                            } else if (abs(dx) <= cancelXThreshold && dy < -threshold) {
                                triggerLineUp = true
                            } else if (abs(dx) <= cancelXThreshold && dy > threshold) {
                                triggerLineDown = true
                            }
                        }

                        if (!isSlideOff) {
                            if (triggerLineStart) {
                                listener?.onAction(KeyAction.MoveCursorToStartOfLine, this, true)
                            } else if (triggerLineEnd) {
                                listener?.onAction(KeyAction.MoveCursorToEndOfLine, this, true)
                            } else if (triggerLineUp) {
                                listener?.onAction(KeyAction.MoveCursorToPrevLine, this, true)
                            } else if (triggerLineDown) {
                                listener?.onAction(KeyAction.MoveCursorToNextLine, this, true)
                            }
                        }
                        isLineStartAnnounced = false
                        isLineEndAnnounced = false
                        isLineUpAnnounced = false
                        isLineDownAnnounced = false
                        
                        if (triggerLineStart || triggerLineEnd || triggerLineUp || triggerLineDown) {
                            lastHoverTarget = null
                            return true
                        }
                    }

                    if (isHoverDraggingLeftCursor) {
                        isHoverDraggingLeftCursor = false
                        var triggerLineStart = isLeftLineStartAnnounced
                        var triggerLineEnd = isLeftLineEndAnnounced
                        var triggerLineUp = isLeftLineUpAnnounced
                        var triggerLineDown = isLeftLineDownAnnounced

                        // 素早いフリックのフォールバック
                        if (!triggerLineStart && !triggerLineEnd && !triggerLineUp && !triggerLineDown) {
                            val dx = screenX - hoverLeftCursorDragStartX
                            val dy = screenY - hoverLeftCursorDragStartY
                            val threshold = 35f
                            val cancelXThreshold = 60f
                            val cancelYThreshold = 60f

                            if (abs(dy) <= cancelYThreshold && dx < -threshold) {
                                triggerLineStart = true
                            } else if (abs(dy) <= cancelYThreshold && dx > threshold) {
                                triggerLineEnd = true
                            } else if (abs(dx) <= cancelXThreshold && dy < -threshold) {
                                triggerLineUp = true
                            } else if (abs(dx) <= cancelXThreshold && dy > threshold) {
                                triggerLineDown = true
                            }
                        }

                        if (!isSlideOff) {
                            if (triggerLineStart) {
                                listener?.onAction(KeyAction.MoveCursorToStartOfLine, this, true)
                            } else if (triggerLineEnd) {
                                listener?.onAction(KeyAction.MoveCursorToEndOfLine, this, true)
                            } else if (triggerLineUp) {
                                listener?.onAction(KeyAction.MoveCursorToPrevLine, this, true)
                            } else if (triggerLineDown) {
                                listener?.onAction(KeyAction.MoveCursorToNextLine, this, true)
                            }
                        }
                        isLeftLineStartAnnounced = false
                        isLeftLineEndAnnounced = false
                        isLeftLineUpAnnounced = false
                        isLeftLineDownAnnounced = false
                        
                        if (triggerLineStart || triggerLineEnd || triggerLineUp || triggerLineDown) {
                            lastHoverTarget = null
                            return true
                        }
                    }

                    if (isHoverDraggingDeleteKey) {
                        isHoverDraggingDeleteKey = false
                        var triggerDeleteLeft = isDeleteLeftAnnounced
                        var triggerDeleteRight = isDeleteRightAnnounced

                        // 素早いフリックのフォールバック
                        if (!triggerDeleteLeft && !triggerDeleteRight) {
                            val dx = screenX - hoverDeleteKeyDragStartX
                            val dy = screenY - hoverDeleteKeyDragStartY
                            val threshold = 35f
                            val cancelYThreshold = 60f

                            if (abs(dy) <= cancelYThreshold) {
                                if (dx < -threshold) {
                                    triggerDeleteLeft = true
                                } else if (dx > threshold) {
                                    triggerDeleteRight = true
                                }
                            }
                        }

                        if (!isSlideOff) {
                            if (triggerDeleteLeft) {
                                listener?.onAction(KeyAction.DeleteLeftWordOrSymbols, this, true)
                            } else if (triggerDeleteRight) {
                                listener?.onAction(KeyAction.DeleteForward, this, true)
                            }
                        }
                        isDeleteLeftAnnounced = false
                        isDeleteRightAnnounced = false
                        isDeleteUpAnnounced = false
                        
                        if (triggerDeleteLeft || triggerDeleteRight) {
                            lastHoverTarget = null
                            return true
                        }
                    }

                    if (!isSlideOff) {
                        // スライド/ドラッグを行わずに単純リフトした場合はタップ処理を行う
                        lastHoverTarget?.let { view ->
                            handleKeyClick(view)
                        }
                    }
                    lastHoverTarget = null
                }
            }
            // DTalker IME方式: システムのデフォルト処理をバイパスするために true を返す
            return true
        }
        return super.onHoverEvent(event)
    }
    
    /**
     * デバッグ用: アクション名を取得
     */
    private fun getActionName(action: Int): String {
        return when (action) {
            MotionEvent.ACTION_HOVER_ENTER -> "HOVER_ENTER"
            MotionEvent.ACTION_HOVER_MOVE -> "HOVER_MOVE"
            MotionEvent.ACTION_HOVER_EXIT -> "HOVER_EXIT"
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            else -> "UNKNOWN($action)"
        }
    }

    
    /**
     * ホバー中のキーを音声で読み上げる
     */
    private fun announceHoveredKey(view: View) {
        val keyInfo = findKeyInfoForView(view)
        keyInfo?.let { info ->
            val announcement = buildKeyAnnouncement(info.keyData)
            if (announcement.isNotEmpty() && accessibilityManager.isEnabled) {
                if (accessibilityManager.isTouchExplorationEnabled) {
                    // 強制的にこれまでの読み上げを中断する
                    accessibilityManager.interrupt()
                }
                this.announceForAccessibility(announcement)
            }
        }
    }
    
    /**
     * Viewに対応するKeyInfoを検索
     */
    private fun findKeyInfoForView(view: View): KeyInfo? {
        // 動的キーマップから検索
        dynamicKeyMap.values.find { it.view == view }?.let { return it }
        
        // 現在のレイアウトから検索
        currentLayout?.keys?.forEachIndexed { index, keyData ->
            if (getChildAt(index) == view) {
                return KeyInfo(view, keyData, null, index)
            }
        }
        
        return null
    }
    
    private fun buildKeyAnnouncement(keyData: KeyData): String {
        return when {
            // NewLine または Enter アクションがある場合は、ラベルがあってもアクションの読み上げ（エンター）を優先する
            keyData.action == KeyAction.NewLine || keyData.action == KeyAction.Enter -> {
                getActionDescription(keyData.action)
            }
            else -> {
                val baseLabel = if (keyData.label.isNotEmpty()) {
                    keyData.label.split("\n").firstOrNull()?.trim() ?: keyData.label.trim()
                } else ""

                val announcement = when (baseLabel) {
                    "CursorMoveLeft" -> "左カーソル"
                    "CursorMoveRight" -> "右カーソル"
                    "Del" -> "削除"
                    "#", "＃" -> "シャープ"
                    "-", "－" -> "ハイフン"
                    else -> {
                        if (baseLabel.isNotEmpty()) {
                            baseLabel
                        } else if (keyData.action != null) {
                            getActionDescription(keyData.action)
                        } else {
                            ""
                        }
                    }
                }
                Log.d("FlickKeyAnnouncement", "label='${keyData.label}', baseLabel='$baseLabel', announcement='$announcement'")
                announcement
            }
        }
    }
    
    /**
     * アクションキーの説明を取得
     */
    private fun getActionDescription(action: KeyAction): String {
        return when (action) {
            is KeyAction.Delete, is KeyAction.Backspace -> "削除"
            is KeyAction.Enter, is KeyAction.NewLine -> context.getString(com.kazumaproject.core.R.string.enter_key)
            is KeyAction.Space -> "スペース"
            is KeyAction.ShiftKey -> "シフト"
            is KeyAction.SwitchToNextIme -> "言語切替"
            is KeyAction.ShowEmojiKeyboard -> context.getString(com.kazumaproject.core.R.string.symbol)
            is KeyAction.VoiceInput -> context.getString(com.kazumaproject.core.R.string.read_aloud)
            is KeyAction.ReadAloudCurrent,
            is KeyAction.ReadAloudLine,
            is KeyAction.ReadAloudAll,
            is KeyAction.ReadAloudFromCursor -> context.getString(com.kazumaproject.core.R.string.read_aloud)
            is KeyAction.MoveCursorLeft -> context.getString(com.kazumaproject.core.R.string.left_key)
            is KeyAction.MoveCursorRight -> context.getString(com.kazumaproject.core.R.string.key_right)
            is KeyAction.MoveCursorToStartOfLine -> "行頭"
            is KeyAction.MoveCursorToEndOfLine -> "行末"
            is KeyAction.MoveCursorToPrevLine -> "上カーソル"
            is KeyAction.MoveCursorToNextLine -> "下カーソル"
            is KeyAction.DeleteLeftWordOrSymbols -> "一括削除"
            is KeyAction.DeleteForward -> "行末まで削除"
            is KeyAction.ChangeInputMode -> "入力モード切替"
            is KeyAction.Convert -> "変換"
            is KeyAction.Confirm -> "確定"
            else -> ""
        }
    }

    private fun getAccessibilityActionId(direction: FlickDirection): Int? {
        return when (direction) {
            FlickDirection.UP_LEFT, FlickDirection.UP_LEFT_FAR -> com.kazumaproject.core.R.id.action_flick_left
            FlickDirection.UP -> com.kazumaproject.core.R.id.action_flick_top
            FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR -> com.kazumaproject.core.R.id.action_flick_right
            FlickDirection.DOWN -> com.kazumaproject.core.R.id.action_flick_bottom
            else -> null
        }
    }

    private fun getFlickDirectionFromActionId(actionId: Int): FlickDirection? {
        return when (actionId) {
            com.kazumaproject.core.R.id.action_flick_left -> FlickDirection.UP_LEFT
            com.kazumaproject.core.R.id.action_flick_top -> FlickDirection.UP
            com.kazumaproject.core.R.id.action_flick_right -> FlickDirection.UP_RIGHT
            com.kazumaproject.core.R.id.action_flick_bottom -> FlickDirection.DOWN
            else -> null
        }
    }

    private fun getAccessibilityActionLabel(direction: FlickDirection, flickAction: FlickAction): String? {
        val directionStr = when (direction) {
            FlickDirection.UP_LEFT, FlickDirection.UP_LEFT_FAR -> "左フリック"
            FlickDirection.UP -> "上フリック"
            FlickDirection.UP_RIGHT, FlickDirection.UP_RIGHT_FAR -> "右フリック"
            FlickDirection.DOWN -> "下フリック"
            else -> return null
        }
        
        val actionName = when (flickAction) {
            is FlickAction.Input -> {
                if (flickAction.char.isNotEmpty()) {
                    flickAction.char
                } else {
                    return null
                }
            }
            is FlickAction.Action -> {
                when (flickAction.action) {
                    KeyAction.MoveCursorToStartOfLine -> "行頭移動"
                    KeyAction.MoveCursorToEndOfLine -> "行末移動"
                    KeyAction.MoveCursorToPrevLine -> "前行移動"
                    KeyAction.MoveCursorToNextLine -> "次行移動"
                    KeyAction.DeleteLeftWordOrSymbols -> "一括削除"
                    KeyAction.DeleteForward -> "行末まで削除"
                    else -> getActionDescription(flickAction.action)
                }
            }
        }
        
        return if (actionName.isNotEmpty()) {
            "$actionName ($directionStr)"
        } else {
            null
        }
    }

    private fun triggerFlickAction(flickAction: FlickAction, view: View) {
        when (flickAction) {
            is FlickAction.Input -> {
                this@FlickKeyboardView.listener?.onKey(flickAction.char, isFlick = true)
            }
            is FlickAction.Action -> {
                this@FlickKeyboardView.listener?.onAction(flickAction.action, view = view, isFlick = true)
            }
        }
    }



    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // TalkBack対応: スワイプ中のダブルタップなどの実タッチイベントはブロックせずに
        // システムの標準的なディスパッチ（子ビューへの伝達）に任せる。
        if (!isCalledFromHoverEvent && isTouchExplorationEnabled()) {
            return super.onTouchEvent(event)
        }

        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        
        // TalkBack有効時の音声フィードバック
        if (accessibilityManager.isTouchExplorationEnabled && 
            (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            val target = findTargetView(event.x, event.y)
            if (target != lastHoverTarget) {
                lastHoverTarget = target
                target?.let { view ->
                    announceHoveredKey(view)
                }
            }
        }

        if (isCursorMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Store the initial touch position for cursor movement
                    cursorInitialX = event.x
                    cursorInitialY = event.y
                    return true // Consume the event
                }

                MotionEvent.ACTION_MOVE -> {
                    val threshold = 30f // Movement detection threshold in pixels
                    val currentX = event.x
                    val currentY = event.y

                    val dx = currentX - cursorInitialX
                    val dy = currentY - cursorInitialY

                    // Horizontal movement
                    if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                        val action2 =
                            if (dx < 0f) KeyAction.MoveCursorLeft else KeyAction.MoveCursorRight
                        listener?.onAction(action2, this, false)
                        cursorInitialX = currentX // Reset the origin for continuous swiping
                        cursorInitialY = currentY
                    }
                    // Vertical movement
                    else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                        // Assuming you have CURSOR_UP and CURSOR_DOWN in your KeyAction enum
                        val action2 =
                            if (dy < 0f) KeyAction.MoveCursorUp else KeyAction.MoveCursorDown
                        listener?.onAction(action2, this, false)
                        cursorInitialX = currentX // Reset the origin
                        cursorInitialY = currentY
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Exit cursor mode when the finger is lifted
                    setCursorMode(false)
                    crossFlickControllers.forEach { it.dismissAllPopups() }

                    clearSpaceKeyPressedState()
                    motionTargets.clear()
                    pointerDownTime.clear()
                    lastHoverTarget = null
                    return true
                }
            }
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                // 最初の指が触れた。すべての状態をクリアして開始。
                motionTargets.clear()
                pointerDownTime.clear()

                // この指の情報を保存
                pointerDownTime[pointerId] = event.downTime
                val x = event.x
                val y = event.y
                val targetView = findTargetView(x, y)

                targetView?.let {
                    motionTargets[pointerId] = it

                    // 元のeventから複製することで、rawX/rawY座標等の属性を正しく保持する
                    val newEvent = MotionEvent.obtain(event).apply {
                        setAction(MotionEvent.ACTION_DOWN)
                    }

                    Log.d("FlickKeyboardView MotionEvent.ACTION_DOWN", "$newEvent")

                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {

                if (this.visibility != View.VISIBLE) {
                    return false
                }
                motionTargets.keys.toList().forEach { existingPointerId ->
                    val target = motionTargets[existingPointerId]
                    val downTime = pointerDownTime[existingPointerId]

                    Log.d(
                        "FlickKeyboardView",
                        "MotionEvent.ACTION_POINTER_DOWN called ${event.metaState} $target $downTime"
                    )

                    if (target != null && downTime != null) {
                        // 1本目の指の現在の座標を取得
                        val existingPointerIndex = event.findPointerIndex(existingPointerId)
                        if (existingPointerIndex != -1) {
                            val x = event.getX(existingPointerIndex)
                            val y = event.getY(existingPointerIndex)

                            // 1本目の指に対して「ACTION_UP」イベントを自作して送る
                            val upEvent = MotionEvent.obtain(
                                downTime,
                                event.eventTime,
                                MotionEvent.ACTION_UP, // ジェスチャー終了としてUPイベントを偽装
                                x,
                                y,
                                event.metaState
                            )
                            upEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                            target.dispatchTouchEvent(upEvent) // ターゲットにUPイベントをディスパッチ
                            upEvent.recycle()
                        }
                    }

                    val matchingEntry = dynamicKeyMap.entries.find { it.value.view == target }
                    if (matchingEntry != null) {
                        val keyId = matchingEntry.key
                        val keyInfo = matchingEntry.value
                        Log.d(
                            TAG,
                            "ACTION_POINTER_DOWN: First finger (ID: $existingPointerId) is on a dynamic key. KeyId: $keyId, KeyInfo: $keyInfo"
                        )
                        if (keyInfo.keyData.action == KeyAction.InputText(text = "^_^") ||
                            keyInfo.keyData.keyId == "switch_next_ime"
                        ) {
                            return true
                        }
                    } else {
                        Log.d(
                            TAG,
                            "ACTION_POINTER_DOWN: First finger (ID: $existingPointerId) is on a non-dynamic key."
                        )
                    }
                }

                // 既存のポインター情報をすべてクリア
                motionTargets.clear()
                pointerDownTime.clear()

                // 2. 新しい指（2本目）のジェスチャーを新しく開始する
                val newPointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // 新しい指の情報を保存
                pointerDownTime[newPointerId] = event.eventTime
                val targetView = findTargetView(x, y)


                targetView?.let {
                    motionTargets[newPointerId] = it
                    // この指専用の「ACTION_DOWN」イベントを自作する
                    val newEvent = MotionEvent.obtain(
                        event.eventTime, // 新しいジェスチャーなのでdownTimeは現在のeventTime
                        event.eventTime,
                        MotionEvent.ACTION_DOWN,
                        x,
                        y,
                        event.metaState
                    )
                    // 自作したきれいなDOWNイベントをターゲットにディスパッチ

                    Log.d(
                        "FlickKeyboardView",
                        "MotionEvent.ACTION_POINTER_DOWN called new $newPointerId $newEvent"
                    )

                    newEvent.offsetLocation(-it.left.toFloat(), -it.top.toFloat())
                    it.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // 指が動いた。追跡中のすべての指に対して、それぞれ専用のMOVEイベントを作成する
                for (i in 0 until event.pointerCount) {
                    val pId = event.getPointerId(i)
                    val oldTarget = motionTargets[pId]
                    val x = event.getX(i)
                    val y = event.getY(i)
                    val newTarget = findTargetView(x, y)

                    if (oldTarget != newTarget) {
                        // ターゲットが変わった場合

                        // 1. 古いターゲットにCANCELを送る
                        if (oldTarget != null) {
                            val downTime = pointerDownTime[pId] ?: event.downTime
                            val cancelEvent = MotionEvent.obtain(
                                downTime, event.eventTime, MotionEvent.ACTION_CANCEL,
                                x, y, event.metaState
                            )
                            cancelEvent.offsetLocation(
                                -oldTarget.left.toFloat(),
                                -oldTarget.top.toFloat()
                            )
                            oldTarget.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                            motionTargets.remove(pId)
                            pointerDownTime.remove(pId)
                        }

                        // 2. 新しいターゲットにDOWNを送る（新たなタップとして開始）
                        if (newTarget != null) {
                            motionTargets[pId] = newTarget
                            pointerDownTime[pId] = event.eventTime
                            val downEvent = MotionEvent.obtain(
                                event.eventTime, event.eventTime, MotionEvent.ACTION_DOWN,
                                x, y, event.metaState
                            )
                            downEvent.offsetLocation(
                                -newTarget.left.toFloat(),
                                -newTarget.top.toFloat()
                            )
                            newTarget.dispatchTouchEvent(downEvent)
                            downEvent.recycle()
                        }
                    } else if (newTarget != null) {
                        // ターゲットが変わっていない場合、MOVEを送る
                        val moveEvent = MotionEvent.obtain(event).apply {
                            setAction(MotionEvent.ACTION_MOVE)
                        }
                        moveEvent.offsetLocation(
                            -newTarget.left.toFloat(),
                            -newTarget.top.toFloat()
                        )
                        newTarget.dispatchTouchEvent(moveEvent)
                        moveEvent.recycle()
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (this.visibility != View.VISIBLE) {
                    return false
                }
                // 離された指の情報を取得
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)

                // ▼▼▼ ログ追加 ▼▼▼
                Log.d(
                    "FlickKeyboardView",
                    "ACTION_POINTER_UP: pointerId=$pointerId, index=$pointerIndex"
                )
                // ▲▲▲ ログ追加 ▲▲▲

                motionTargets[pointerId]?.let { target ->
                    val downTime = pointerDownTime[pointerId]!!

                    // ▼▼▼ ログ追加 ▼▼▼
                    Log.d(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: Found target! $target"
                    )
                    // ▲▲▲ ログ追加 ▲▲▲

                    // この指専用の「ACTION_UP」イベントを自作
                    val newEvent = MotionEvent.obtain(
                        downTime, event.eventTime, MotionEvent.ACTION_UP, // ジェスチャーの終了として偽装
                        x, y, event.metaState
                    )

                    // ▼▼▼ ログ追加 ▼▼▼
                    Log.d(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: Dispatching fake ACTION_UP to target. Event: $newEvent"
                    )
                    // ▲▲▲ ログ追加 ▲▲▲

                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()

                } ?: run {
                    // ▼▼▼ ログ追加（ターゲットが見つからなかった場合）▼▼▼
                    Log.e(
                        "FlickKeyboardView",
                        "ACTION_POINTER_UP: No target found for pointerId=$pointerId"
                    )
                    // ▲▲▲ ログ追加 ▲▲▲
                }

                // 離された指の情報を削除
                motionTargets.remove(pointerId)
                pointerDownTime.remove(pointerId)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 最後の指が離された、またはジェスチャーがキャンセルされた
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val actionToDispatch = if (action == MotionEvent.ACTION_UP) MotionEvent.ACTION_UP else MotionEvent.ACTION_CANCEL

                motionTargets[pointerId]?.let { target ->
                    // 元のeventから複製することで、rawX/rawY座標等の属性を正しく保持する
                    val newEvent = MotionEvent.obtain(event).apply {
                        setAction(actionToDispatch)
                    }

                    Log.d("FlickKeyboardView MotionEvent.ACTION_UP", "$newEvent")
                    newEvent.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
                    target.dispatchTouchEvent(newEvent)
                    newEvent.recycle()
                }

                // すべての状態をクリア
                motionTargets.clear()
                pointerDownTime.clear()
                lastHoverTarget = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        flickControllers.forEach { it.cancel() }
        crossFlickControllers.forEach { it.cancel() }
        standardFlickControllers.forEach { it.cancel() }
        petalFlickControllers.forEach { it.cancel() }
        tfbiControllers.forEach { it.cancel() }
        stickyTfbiControllers.forEach { it.cancel() }
        hierarchicalTfbiControllers.forEach { it.cancel() }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    private fun Context.getColorFromAttr(@AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(
            attrRes, typedValue, true
        )
        return ContextCompat.getColor(this, typedValue.resourceId)
    }

    private fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
            .toInt()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun clearSpaceKeyPressedState() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)

            // ラベルが "空白" のキーだけ解除する
            val isKuhakuKey = when (child) {
                is AutoSizeButton -> child.text?.toString() == "空白"
                is AppCompatImageButton -> child.contentDescription?.toString() == "空白"
                else -> false
            }

            if (isKuhakuKey) {
                child.isPressed = false
                child.isSelected = false
                child.refreshDrawableState()
            }
        }
    }

    /**
     * TalkBack対応: OnHoverListenerを設定して音声フィードバックを有効化
     * DTalker IMEの実装に基づく
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        
        // TalkBackが有効な場合、OnHoverListenerを設定
        // falseを返すことでTalkBackの音声読み上げが機能する
        // DTalker IMEでは無条件にfalseを返すことで、Explorer by touchを無効化し
        // ホバーイベントをタッチイベントに変換する仕組みを有効にしている
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH && 
            accessibilityManager.isEnabled) {
            setOnHoverListener { _, _ -> false }
        }
    }

}
