package com.kazumaproject.tenkey

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.PopupWindow
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.setPadding
import android.widget.ImageView
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.kazumaproject.core.domain.extensions.hide
import com.kazumaproject.core.domain.extensions.toAccessibilityName
import com.kazumaproject.core.domain.extensions.layoutXPosition
import com.kazumaproject.core.domain.extensions.layoutYPosition
import com.kazumaproject.core.domain.extensions.setBorder
import com.kazumaproject.core.domain.extensions.setDrawableAlpha
import com.kazumaproject.core.domain.extensions.setDrawableSolidColor
import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.key.KeyInfo
import com.kazumaproject.core.domain.key.KeyMap
import com.kazumaproject.core.domain.key.KeyRect
import com.kazumaproject.core.domain.listener.FlickListener
import com.kazumaproject.core.domain.listener.LongPressListener
import com.kazumaproject.core.domain.state.GestureType
import com.kazumaproject.core.domain.state.InputMode
import com.kazumaproject.core.domain.state.InputMode.ModeEnglish.next
import com.kazumaproject.core.domain.state.PressedKey
import com.kazumaproject.core.ui.effect.Blur
import com.kazumaproject.core.ui.input_mode_witch.InputModeSwitch
import com.kazumaproject.core.ui.key_window.KeyWindowLayout
import com.kazumaproject.tenkey.databinding.KeyboardLayoutBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveMaterialBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutActiveMaterialLightBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutMaterialBinding
import com.kazumaproject.tenkey.databinding.PopupLayoutMaterialLightBinding
import com.kazumaproject.tenkey.extensions.setPopUpWindowBottom
import com.kazumaproject.tenkey.extensions.setPopUpWindowCenter
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickBottom
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickLeft
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickRight
import com.kazumaproject.tenkey.extensions.setPopUpWindowFlickTop
import com.kazumaproject.tenkey.extensions.setPopUpWindowLeft
import com.kazumaproject.tenkey.extensions.setPopUpWindowRight
import com.kazumaproject.tenkey.extensions.setPopUpWindowTop
import com.kazumaproject.tenkey.extensions.setTenKeyTextEnglish
import com.kazumaproject.tenkey.extensions.setTenKeyTextJapanese
import com.kazumaproject.tenkey.extensions.setTenKeyTextJapaneseWithFlickGuide
import com.kazumaproject.tenkey.extensions.setTenKeyTextNumber
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapEnglish
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapJapanese
import com.kazumaproject.tenkey.extensions.setTenKeyTextWhenTapNumber
import com.kazumaproject.tenkey.extensions.setTextFlickBottomEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickBottomJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickBottomNumber
import com.kazumaproject.tenkey.extensions.setTextFlickLeftEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickLeftJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickLeftNumber
import com.kazumaproject.tenkey.extensions.setTextFlickRightEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickRightJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickRightNumber
import com.kazumaproject.tenkey.extensions.setTextFlickTopEnglish
import com.kazumaproject.tenkey.extensions.setTextFlickTopJapanese
import com.kazumaproject.tenkey.extensions.setTextFlickTopNumber
import com.kazumaproject.tenkey.extensions.setTextTapEnglish
import com.kazumaproject.tenkey.extensions.setTextTapJapanese
import com.kazumaproject.tenkey.extensions.setTextTapNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility")
class TenKey(context: Context, attributeSet: AttributeSet) :
    ConstraintLayout(context, attributeSet), View.OnTouchListener {

    // ViewBinding for the main keyboard layout
    private val binding: KeyboardLayoutBinding

    // KeyMap to decide which character to send on tap/flick
    private var keyMap: KeyMap

    // For handling long-press detection
    private var longPressJob: Job? = null
    private var isLongPressed = false

    // Track which key is currently pressed
    private lateinit var pressedKey: PressedKey

    // External listeners
    private var flickListener: FlickListener? = null
    private var longPressListener: LongPressListener? = null

    private var flickSensitivity: Int = 100
    private var isVelocityFilterEnabled: Boolean = false
    private var velocityTracker: VelocityTracker? = null
    private var hoverVelocityTracker: VelocityTracker? = null

    private var keySizeDelta = 0

    private var isLanguageIconEnabled = true

    /** ← REPLACED AtomicReference with StateFlow **/
    private val _currentInputMode = MutableStateFlow<InputMode>(InputMode.ModeJapanese)
    val currentInputMode: StateFlow<InputMode> = _currentInputMode

    // Popups: active (center) and directional
    private lateinit var popupWindowActive: PopupWindow
    private lateinit var bubbleViewActive: KeyWindowLayout
    private lateinit var popTextActive: MaterialTextView

    private lateinit var popupWindowLeft: PopupWindow
    private lateinit var bubbleViewLeft: KeyWindowLayout
    private lateinit var popTextLeft: MaterialTextView

    private lateinit var popupWindowTop: PopupWindow
    private lateinit var bubbleViewTop: KeyWindowLayout
    private lateinit var popTextTop: MaterialTextView

    private lateinit var popupWindowRight: PopupWindow
    private lateinit var bubbleViewRight: KeyWindowLayout
    private lateinit var popTextRight: MaterialTextView

    private lateinit var popupWindowBottom: PopupWindow
    private lateinit var bubbleViewBottom: KeyWindowLayout
    private lateinit var popTextBottom: MaterialTextView

    private lateinit var popupWindowCenter: PopupWindow
    private lateinit var bubbleViewCenter: KeyWindowLayout
    private lateinit var popTextCenter: MaterialTextView

    private var isFlickGuideEnabled: Boolean = false

    private val accessibilityManager: AccessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private var isCalledFromHoverEvent = false
    // TalkBack hover tracking: which Key is currently under the user's finger
    private var currentHoverKey: Key = Key.NotSelected

    // Cache for key coordinates to avoid expensive lookups during hover
    private var cachedKeyRects: List<KeyRect>? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    private val cachedArrowRightDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_arrow_right_24
        )
    }

    private val cachedArrowLeftDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_arrow_left_24
        )
    }

    private val cachedSymbolDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.symbol
        )
    }

    private val cachedUndoDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.undo_24px
        )
    }

    private val cachedBackSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_backspace_24
        )
    }

    private val cachedSpaceDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_space_bar_24
        )
    }

    private val cachedNumberSmallDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.number_small
        )
    }

    private val cachedKanaDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(context, com.kazumaproject.core.R.drawable.kana_small)
    }

    private val cachedOpenBracketDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.open_bracket
        )
    }

    private val cachedLanguageDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.language_24dp
        )
    }

    private val cachedContentCopyDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.content_copy_24dp
        )
    }

    private val cachedContentCutDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.content_cut_24dp
        )
    }

    private val cachedContentShareDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.baseline_share_24
        )
    }

    private val cachedContentSelectDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context,
            com.kazumaproject.core.R.drawable.text_select_start_24dp
        )
    }

    private val cachedEnglishDrawable: Drawable? by lazy {
        ContextCompat.getDrawable(
            context, com.kazumaproject.core.R.drawable.english_small
        )
    }


    // Map each Key enum to its corresponding View (Button/ImageButton/Switch)
    private var listKeys: Map<Key, Any>

    private var isCursorMode = false

    // Drag tracking variables for Key.SideKeyCursorRight
    private var isDraggingRightCursor = false
    private var isHoverDraggingRightCursor = false
    private var isLineStartAnnounced = false
    private var isLineEndAnnounced = false
    private var isLineUpAnnounced = false
    private var isLineDownAnnounced = false
    private var rightCursorDragStartX = 0f
    private var rightCursorDragEndX = 0f
    private var rightCursorDragStartY = 0f
    private var rightCursorDragEndY = 0f
    private var rightCursorDragTopY = 0f
    private var hoverRightCursorDragStartX = 0f
    private var hoverRightCursorDragEndX = 0f
    private var hoverRightCursorDragStartY = 0f
    private var hoverRightCursorDragEndY = 0f
    private var hoverRightCursorDragTopY = 0f

    // Stationary tracking for slide-in gesture start on Right Cursor key
    private var touchSlideInEntryTime = 0L
    private var touchSlideInEntryX = 0f
    private var touchSlideInEntryY = 0f
    private var hoverSlideInEntryTime = 0L
    private var hoverSlideInEntryX = 0f
    private var hoverSlideInEntryY = 0f

    // Drag tracking variables for Key.SideKeyCursorLeft
    private var isDraggingLeftCursor = false
    private var isHoverDraggingLeftCursor = false
    private var isLeftLineStartAnnounced = false
    private var isLeftLineEndAnnounced = false
    private var isLeftLineUpAnnounced = false
    private var isLeftLineDownAnnounced = false
    private var leftCursorDragStartX = 0f
    private var leftCursorDragEndX = 0f
    private var leftCursorDragStartY = 0f
    private var leftCursorDragEndY = 0f
    private var leftCursorDragTopY = 0f
    private var hoverLeftCursorDragStartX = 0f
    private var hoverLeftCursorDragEndX = 0f
    private var hoverLeftCursorDragStartY = 0f
    private var hoverLeftCursorDragEndY = 0f
    private var hoverLeftCursorDragTopY = 0f

    // Stationary tracking for slide-in gesture start on Left Cursor key
    private var leftTouchSlideInEntryTime = 0L
    private var leftTouchSlideInEntryX = 0f
    private var leftTouchSlideInEntryY = 0f
    private var leftHoverSlideInEntryTime = 0L
    private var leftHoverSlideInEntryX = 0f
    private var leftHoverSlideInEntryY = 0f

    // Drag tracking variables for Key.SideKeyDelete
    var isInputComposing = false
        set(value) {
            val changed = field != value
            field = value
            updateSideKeySymbolLabel()
            if (changed && isAyameMode) {
                setupAccessibility()
            }
        }
    private var isDraggingDeleteKey = false
    private var isHoverDraggingDeleteKey = false
    private var isDeleteLeftAnnounced = false
    private var isDeleteRightAnnounced = false
    private var isDeleteUpAnnounced = false
    private var deleteKeyDragStartX = 0f
    private var deleteKeyDragEndX = 0f
    private var deleteKeyDragStartY = 0f
    private var deleteKeyDragEndY = 0f
    private var deleteKeyDragTopY = 0f
    private var hoverDeleteKeyDragStartX = 0f
    private var hoverDeleteKeyDragEndX = 0f
    private var hoverDeleteKeyDragStartY = 0f
    private var hoverDeleteKeyDragEndY = 0f
    private var hoverDeleteKeyDragTopY = 0f

    // Stationary tracking for slide-in gesture start on Delete key
    private var deleteTouchSlideInEntryTime = 0L
    private var deleteTouchSlideInEntryX = 0f
    private var deleteTouchSlideInEntryY = 0f
    private var deleteHoverSlideInEntryTime = 0L
    private var deleteHoverSlideInEntryX = 0f
    private var deleteHoverSlideInEntryY = 0f

    // Drag tracking variables for Key.SideKeySpace
    private var isDraggingSpaceKey = false
    private var isHoverDraggingSpaceKey = false
    private var isSpaceDownAnnounced = false
    private var isSpaceUpAnnounced = false
    private var isSpaceRightAnnounced = false
    private var spaceKeyDragStartX = 0f
    private var spaceKeyDragEndX = 0f
    private var spaceKeyDragStartY = 0f
    private var spaceKeyDragEndY = 0f
    private var hoverSpaceKeyDragStartX = 0f
    private var hoverSpaceKeyDragEndX = 0f
    private var hoverSpaceKeyDragStartY = 0f
    private var hoverSpaceKeyDragEndY = 0f

    // Stationary tracking for slide-in gesture start on Space key
    private var spaceTouchSlideInEntryTime = 0L
    private var spaceTouchSlideInEntryX = 0f
    private var spaceTouchSlideInEntryY = 0f
    private var spaceHoverSlideInEntryTime = 0L
    private var spaceHoverSlideInEntryX = 0f
    private var spaceHoverSlideInEntryY = 0f

    // Drag tracking variables for Key.SideKeyReadAloud
    private var isDraggingReadAloudKey = false
    private var isHoverDraggingReadAloudKey = false
    private var isReadAloudLeftAnnounced = false
    private var isReadAloudUpAnnounced = false
    private var isReadAloudRightAnnounced = false
    private var readAloudKeyDragStartX = 0f
    private var readAloudKeyDragEndX = 0f
    private var readAloudKeyDragStartY = 0f
    private var readAloudKeyDragEndY = 0f
    private var readAloudKeyDragTopY = 0f
    private var hoverReadAloudKeyDragStartX = 0f
    private var hoverReadAloudKeyDragEndX = 0f
    private var hoverReadAloudKeyDragStartY = 0f
    private var hoverReadAloudKeyDragEndY = 0f
    private var hoverReadAloudKeyDragTopY = 0f

    // Stationary tracking for slide-in gesture start on Read Aloud key
    private var readAloudTouchSlideInEntryTime = 0L
    private var readAloudTouchSlideInEntryX = 0f
    private var readAloudTouchSlideInEntryY = 0f
    private var readAloudHoverSlideInEntryTime = 0L
    private var readAloudHoverSlideInEntryX = 0f
    private var readAloudHoverSlideInEntryY = 0f

    // Hover drag tracking variables for character keys
    private var isHoverDraggingCharKey = false
    private var hoverCharKey: Key = Key.NotSelected
    private var hoverCharKeyDragStartX = 0f
    private var hoverCharKeyDragStartY = 0f
    private var hoverActiveGesture: GestureType = GestureType.Tap

    // DTalker IME-style Hover Hold activation variables
    private var hoverCurrentKey: Key = Key.NotSelected
    private var hoverCurrentKeyEntryTime: Long = 0L
    private var hoverCurrentKeyEntryX: Float = 0f
    private var hoverCurrentKeyEntryY: Float = 0f
    private var isHoverDragActive: Boolean = false
    private var hoverLastAnnouncedChar: String? = null
    private var charHoverSlideInEntryTime = 0L
    private var charHoverSlideInEntryX = 0f
    private var charHoverSlideInEntryY = 0f

    // Theme Variables (Initialized with defaults)
    private var themeMode: String = "default"
    private var isNightMode: Boolean = false
    private var isDynamicColorEnabled: Boolean = false
    private var customBgColor: Int = Color.WHITE
    private var customKeyColor: Int = Color.LTGRAY
    private var customSpecialKeyColor: Int = Color.GRAY
    private var customKeyTextColor: Int = Color.BLACK
    private var customSpecialKeyTextColor: Int = Color.BLACK
    private var liquidGlassEnable: Boolean = false
    private var liquidGlassKeyAlphaEnable: Int = 255
    private var customBorderEnable: Boolean = false
    private var customBorderColor: Int = Color.BLACK

    /** ← NEW: scope tied to this view; cancel it on detach **/
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Inflate the keyboard layout with ViewBinding (root is <merge>, so attachToParent = true)
        val inflater = LayoutInflater.from(context)
        binding = KeyboardLayoutBinding.inflate(inflater, this)
        // Initialize keyMap
        keyMap = KeyMap()

        // Build the map from Key enum to actual View references
        listKeys = mapOf(
            Key.KeyA to binding.key1,
            Key.KeyKA to binding.key2,
            Key.KeySA to binding.key3,
            Key.KeyTA to binding.key4,
            Key.KeyNA to binding.key5,
            Key.KeyHA to binding.key6,
            Key.KeyMA to binding.key7,
            Key.KeyYA to binding.key8,
            Key.KeyRA to binding.key9,
            Key.KeyWA to binding.key11,
            Key.KeyKutouten to binding.key12,
            Key.KeyDakutenSmall to binding.keySmallLetter,
            Key.SideKeyReadAloud to binding.sideKeyReadAloud,
            Key.SideKeyCursorLeft to binding.keySoftLeft,
            Key.SideKeyCursorRight to binding.keyMoveCursorRight,
            Key.SideKeySymbol to binding.sideKeySymbol,
            Key.SideKeyInputMode to binding.keySwitchKeyMode,
            Key.SideKeyDelete to binding.keyDelete,
            Key.SideKeySpace to binding.keySpace,
            Key.SideKeyEnter to binding.keyEnter
        )

        // TalkBack support: setup focus and click listeners
        setupAccessibility()

        // Initially display Japanese text on main keys
        setJapaneseTextFor(binding.key12)

        // Set default drawable for small/dakuten key
        setBackgroundSmallLetterKey()

        // Attach this view as its own touch listener
        this.setOnTouchListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.focusable = View.NOT_FOCUSABLE
        } else {
            this.isFocusable = false
        }

        scope.launch {
            currentInputMode.collect { inputMode ->
                Log.d("TenKey", "currentInputMode: $inputMode")
                // Whenever inputMode changes, update all keys and switch UI
                handleCurrentInputModeSwitch(inputMode)
                binding.keySwitchKeyMode.setInputMode(inputMode, false)
            }
        }
    }

    private fun setPopupViewTheme(
        isDynamicColorsEnable: Boolean,
        isDarkMode: Boolean,
        inflater: LayoutInflater
    ) {
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val activeBinding =
                    PopupLayoutActiveMaterialBinding.inflate(inflater, null, false)
                popupWindowActive = PopupWindow(
                    activeBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewActive = activeBinding.bubbleLayoutActive
                popTextActive = activeBinding.popupTextActive
                val leftBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowLeft = PopupWindow(
                    leftBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewLeft = leftBinding.bubbleLayout
                popTextLeft = leftBinding.popupText
            } else {
                val activeBinding =
                    PopupLayoutActiveMaterialLightBinding.inflate(inflater, null, false)
                popupWindowActive = PopupWindow(
                    activeBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewActive = activeBinding.bubbleLayoutActive
                popTextActive = activeBinding.popupTextActive
                val leftBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowLeft = PopupWindow(
                    leftBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewLeft = leftBinding.bubbleLayout
                popTextLeft = leftBinding.popupText
            }
        } else {
            val activeBinding = PopupLayoutActiveBinding.inflate(inflater, null, false)
            popupWindowActive = PopupWindow(
                activeBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewActive = activeBinding.bubbleLayoutActive
            popTextActive = activeBinding.popupTextActive

            val leftBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowLeft = PopupWindow(
                leftBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewLeft = leftBinding.bubbleLayout
            popTextLeft = leftBinding.popupText
        }

        // --- Top popup ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val topBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowTop = PopupWindow(
                    topBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewTop = topBinding.bubbleLayout
                popTextTop = topBinding.popupText
            } else {
                val topBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowTop = PopupWindow(
                    topBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewTop = topBinding.bubbleLayout
                popTextTop = topBinding.popupText
            }
        } else {
            val topBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowTop = PopupWindow(
                topBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
            )
            bubbleViewTop = topBinding.bubbleLayout
            popTextTop = topBinding.popupText
        }

        // --- Right popup ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val rightBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowRight = PopupWindow(
                    rightBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewRight = rightBinding.bubbleLayout
                popTextRight = rightBinding.popupText
            } else {
                val rightBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowRight = PopupWindow(
                    rightBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewRight = rightBinding.bubbleLayout
                popTextRight = rightBinding.popupText
            }
        } else {
            val rightBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowRight = PopupWindow(
                rightBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewRight = rightBinding.bubbleLayout
            popTextRight = rightBinding.popupText
        }

        // --- Bottom popup ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val bottomBinding =
                    PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowBottom = PopupWindow(
                    bottomBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewBottom = bottomBinding.bubbleLayout
                popTextBottom = bottomBinding.popupText
            } else {
                val bottomBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowBottom = PopupWindow(
                    bottomBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewBottom = bottomBinding.bubbleLayout
                popTextBottom = bottomBinding.popupText
            }
        } else {
            val bottomBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowBottom = PopupWindow(
                bottomBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewBottom = bottomBinding.bubbleLayout
            popTextBottom = bottomBinding.popupText
        }

        // --- Center popup (for long‐press + flick previews) ---
        if (isDynamicColorsEnable) {
            if (isDarkMode) {
                val centerBinding =
                    PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowCenter = PopupWindow(
                    centerBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewCenter = centerBinding.bubbleLayout
                popTextCenter = centerBinding.popupText
            } else {
                val centerBinding =
                    PopupLayoutMaterialLightBinding.inflate(inflater, null, false)
                popupWindowCenter = PopupWindow(
                    centerBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewCenter = centerBinding.bubbleLayout
                popTextCenter = centerBinding.popupText
            }
        } else {
            val centerBinding = PopupLayoutBinding.inflate(inflater, null, false)
            popupWindowCenter = PopupWindow(
                centerBinding.root,
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                false
            )
            bubbleViewCenter = centerBinding.bubbleLayout
            popTextCenter = centerBinding.popupText
        }
    }

    private var lastClickedKey: Key? = null
    private var lastClickedTime: Long = 0L

    var isAyameMode: Boolean = false
        set(value) {
            field = value
            lastClickedKey = null
            lastClickedTime = 0L
            setupAccessibility()
        }

    fun setLanguageEnableKeyState(state: Boolean) {
        this.isLanguageIconEnabled = state
    }

    /**
     * Sets the text size for the main keys (key1 to key12).
     * @param size The new text size in sp.
     */
    fun setKeyLetterSize(size: Float) {
        binding.apply {
            val keyButtons = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, key11, key12
            )
            keyButtons.forEach { button ->
                button.textSize = size
            }
        }
    }

    /**
     * Sets the padding delta to keySize Delta.
     * @param delta The delta value from preference.
     */
    fun setKeyLetterSizeDelta(delta: Int) {
        this.keySizeDelta = delta
    }

    private fun setMaterialYouTheme(
        isDarkMode: Boolean,
        isDynamicColorEnable: Boolean
    ) {
        if (!isDynamicColorEnable) {
            val tint = ColorStateList.valueOf(
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.black)
            )
            ImageViewCompat.setImageTintList(binding.keyEnter, tint)
            return
        }
        binding.apply {
            val centerRes = if (isDarkMode)
                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material
            else
                com.kazumaproject.core.R.drawable.ten_keys_center_bg_material_light

            val sideRes = if (isDarkMode)
                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material
            else
                com.kazumaproject.core.R.drawable.ten_keys_side_bg_material_light

            val roundRes = if (isDarkMode)
                com.kazumaproject.core.R.drawable.round_key_bg_material
            else
                com.kazumaproject.core.R.drawable.round_key_bg_material_light
            // 中央キー
            listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, keySmallLetter, key11, key12
            ).forEach { btn ->
                // getDrawable→mutate でインスタンスを複製
                btn.background = ContextCompat
                    .getDrawable(context, centerRes)
                    ?.mutate()
                if (liquidGlassEnable) {
                    btn.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }

            // サイドキー
            listOf(
                sideKeyReadAloud, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace,

                ).forEach { btn ->
                btn.background = ContextCompat
                    .getDrawable(context, sideRes)
                    ?.mutate()
                if (liquidGlassEnable) {
                    btn.setDrawableAlpha(liquidGlassKeyAlphaEnable)
                }
            }

            keyEnter.background = ContextCompat
                .getDrawable(context, roundRes)

            keyEnter.setDrawableAlpha(liquidGlassKeyAlphaEnable)

            keySwitchKeyMode.background = ContextCompat
                .getDrawable(context, roundRes)

            keySwitchKeyMode.setDrawableAlpha(liquidGlassKeyAlphaEnable)
        }
    }

    /**
     * 動的な色指定によるニューモーフィズムテーマの適用
     * @param targetColor 適用したいメインカラー (例: Color.parseColor("#E0E5EC"))
     */
    fun setDynamicNeumorphismTheme(targetColor: Int) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)

        // 文字色を背景の明るさに応じて自動決定（白 または 黒）
        val textColor =
            if (androidx.core.graphics.ColorUtils.calculateLuminance(targetColor) > 0.5) {
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.black)
            } else {
                ContextCompat.getColor(context, com.kazumaproject.core.R.color.white)
            }
        val textTint = ColorStateList.valueOf(textColor)

        // 背景色をセット（キーと同化させるため）
        this.setBackgroundColor(targetColor)

        binding.apply {
            // 中央キー、サイドキー、Enterキーなど全てに適用
            // （サイドキーだけ色を変えたい場合は別の引数を渡して getDynamicNeumorphDrawable を呼ぶ）
            val commonDrawable = getDynamicNeumorphDrawable(targetColor, radius)

            // 各ボタンに適用 (Drawableはmutateしないと状態が共有されてバグる可能性があるが、
            // 今回は都度生成関数を呼ぶか、定数ならmutateする。
            // ここではループ内で「同じ設定」でいいなら共通インスタンスの `constantState.newDrawable()` を使うとメモリ効率が良い)

            val keys = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, keySmallLetter, key11, key12,
                sideKeyReadAloud, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace, keyEnter, keySwitchKeyMode
            )

            keys.forEach { view ->
                // 新しいDrawableインスタンスを生成してセット
                // (全て同じ色なら同じDrawableインスタンスを使い回しても良いが、サイズが違うとstretchされるため注意)
                view.background = getDynamicNeumorphDrawable(targetColor, radius)

                if (view is MaterialTextView || view is AppCompatButton) {
                    (view as? MaterialTextView)?.setTextColor(textColor)
                    (view as? AppCompatButton)?.setTextColor(textColor)
                }
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, textTint)
                }
            }
        }
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

        val inflater = LayoutInflater.from(context)

        when (this.themeMode) {
            "default" -> {
                setPopupViewTheme(
                    isDynamicColorsEnable = isDynamicColorEnabled,
                    isDarkMode = isNightMode,
                    inflater = inflater
                )
                setBackgroundColor(Color.TRANSPARENT)
                setMaterialYouTheme(this.isNightMode, true)
            }


            "custom" -> {
                val activeBinding = PopupLayoutActiveBinding.inflate(inflater, null, false)
                popupWindowActive = PopupWindow(
                    activeBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewActive = activeBinding.bubbleLayoutActive
                popTextActive = activeBinding.popupTextActive
                val activeColor = manipulateColor(customSpecialKeyColor, 1.2f)
                bubbleViewActive.setBubbleColor(activeColor)
                popTextActive.setTextColor(customSpecialKeyTextColor)

                val leftBinding = PopupLayoutBinding.inflate(inflater, null, false)
                popupWindowLeft = PopupWindow(
                    leftBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewLeft = leftBinding.bubbleLayout
                popTextLeft = leftBinding.popupText
                bubbleViewLeft.setBubbleColor(customSpecialKeyColor)
                popTextLeft.setTextColor(customSpecialKeyTextColor)

                // --- Top popup ---
                val topBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowTop = PopupWindow(
                    topBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewTop = topBinding.bubbleLayout
                popTextTop = topBinding.popupText
                bubbleViewTop.setBubbleColor(customSpecialKeyColor)
                popTextTop.setTextColor(customSpecialKeyTextColor)

                // --- Right popup ---
                val rightBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowRight = PopupWindow(
                    rightBinding.root,
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT,
                    false
                )
                bubbleViewRight = rightBinding.bubbleLayout
                popTextRight = rightBinding.popupText
                bubbleViewRight.setBubbleColor(customSpecialKeyColor)
                popTextRight.setTextColor(customSpecialKeyTextColor)

                // --- Bottom popup ---
                val bottomBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowBottom = PopupWindow(
                    bottomBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
                )
                bubbleViewBottom = bottomBinding.bubbleLayout
                popTextBottom = bottomBinding.popupText
                bubbleViewBottom.setBubbleColor(customSpecialKeyColor)
                popTextBottom.setTextColor(customSpecialKeyTextColor)

                // --- Center popup (for long‐press + flick previews) ---
                val centerBinding = PopupLayoutMaterialBinding.inflate(inflater, null, false)
                popupWindowCenter = PopupWindow(
                    centerBinding.root, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, false
                )
                bubbleViewCenter = centerBinding.bubbleLayout
                popTextCenter = centerBinding.popupText
                bubbleViewCenter.setBubbleColor(customSpecialKeyColor)
                popTextCenter.setTextColor(customSpecialKeyTextColor)

                setFullCustomNeumorphismTheme(
                    backgroundColor = customBgColor,
                    normalKeyColor = customKeyColor,
                    specialKeyColor = customSpecialKeyColor,
                    normalKeyTextColor = customKeyTextColor,
                    specialKeyTextColor = customSpecialKeyTextColor,
                    borderWidth = borderWidth
                )
            }

            else -> {
                setPopupViewTheme(
                    isDynamicColorsEnable = isDynamicColorEnabled,
                    isDarkMode = isNightMode,
                    inflater = inflater
                )
                setBackgroundColor(Color.TRANSPARENT)
                setMaterialYouTheme(this.isNightMode, true)
            }
        }
    }

    /**
     * 詳細な色指定によるニューモーフィズムテーマの適用（拡張版）
     *
     * @param backgroundColor View全体の背景色
     * @param normalKeyColor 「通常キー」の背景色 (追加)
     * @param specialKeyColor 「特殊キー（Enter, Deleteなど）」の背景色
     * @param normalKeyTextColor 通常キーの文字・アイコン色
     * @param specialKeyTextColor 特殊キーの文字・アイコン色
     */
    fun setFullCustomNeumorphismTheme(
        backgroundColor: Int,
        normalKeyColor: Int, // 引数を追加
        specialKeyColor: Int,
        normalKeyTextColor: Int,
        specialKeyTextColor: Int,
        borderWidth: Int
    ) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)

        // 1. 全体の背景色を設定
        if (liquidGlassEnable) {
            this.setBackgroundColor(ColorUtils.setAlphaComponent(backgroundColor, 0))
        } else {
            this.setBackgroundColor(backgroundColor)
        }

        binding.apply {
            // --- キーの分類リスト定義 ---
            val normalKeys = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, key11, key12, keySmallLetter
            )

            val specialKeys = listOf(
                sideKeyReadAloud, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace, keyEnter,
                keySwitchKeyMode
            )

            // --- 色の適用処理 ---

            // 2. 通常キーへの適用 (normalKeyColorを使用)
            val normalDrawableState =
                getDynamicNeumorphDrawable(normalKeyColor, radius).constantState

            val normalColorStateList = ColorStateList.valueOf(normalKeyTextColor)

            normalKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(customKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = normalDrawableState?.newDrawable()?.mutate()
                }

                if (view is MaterialTextView) view.setTextColor(normalColorStateList)
                if (view is AppCompatButton) view.setTextColor(normalColorStateList)

                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, normalColorStateList)
                }

                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }

            // 3. 特殊キーへの適用 (specialKeyColorを使用)
            val specialDrawableState =
                getDynamicNeumorphDrawable(specialKeyColor, radius).constantState

            val specialColorStateList = ColorStateList.valueOf(specialKeyTextColor)

            specialKeys.forEach { view ->
                if (customBorderEnable) {
                    view.setDrawableSolidColor(customSpecialKeyColor)
                    view.setBorder(customBorderColor, borderWidth)
                } else {
                    view.background = specialDrawableState?.newDrawable()?.mutate()
                }
                if (view is ImageView) {
                    ImageViewCompat.setImageTintList(view, specialColorStateList)
                } else if (view is TextView) {
                    view.setTextColor(specialColorStateList)
                    TextViewCompat.setCompoundDrawableTintList(view, specialColorStateList)
                }
                view.setDrawableAlpha(liquidGlassKeyAlphaEnable)
            }
        }
    }

    /**
     * 指定された色(baseColor)を元に、ニューモーフィズムのDrawableを動的に生成する
     * @param baseColor キーのメインカラー
     * @param radius キーの角丸の半径 (px)
     */
    private fun getDynamicNeumorphDrawable(baseColor: Int, radius: Float): Drawable {
        // 1. 色の計算
        // ハイライト色: ベース色に白(#FFFFFF)を50%混ぜる（または明るくする）
        val highlightColor = manipulateColor(baseColor, 1.2f) // 輝度を上げる簡易版
        // シャドウ色: ベース色に黒(#000000)を混ぜて暗くする
        val shadowColor = manipulateColor(baseColor, 0.8f)    // 輝度を下げる簡易版

        // 2. ピクセル単位のオフセット量（4dpなどをpxに変換）
        val density = context.resources.displayMetrics.density
        val offset = (4 * density).toInt() // 影のずれ幅
        val padding = (2 * density).toInt() // メイン面の縮小幅

        // --- A. 通常状態 (Idle) の作成 ---

        // レイヤー0: 暗い影 (右下に配置)
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(shadowColor)
        }

        // レイヤー1: 明るいハイライト (左上に配置)
        val highlightDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(highlightColor)
        }

        // レイヤー2: メインの面
        val surfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(baseColor)
        }

        // LayerDrawableで重ねる (下から順に描画される)
        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))

        // インセット（余白）を設定して位置をずらす
        // setLayerInset(index, left, top, right, bottom)

        // 影: 左と上を空けて、右下に表示させる
        idleLayer.setLayerInset(0, offset, offset, 0, 0)

        // ハイライト: 右と下を空けて、左上に表示させる
        idleLayer.setLayerInset(1, 0, 0, offset, offset)

        // メイン面: 全体に少し余白を入れて中央に配置（影が見えるようにする）
        idleLayer.setLayerInset(2, padding, padding, padding, padding)


        // --- B. 押下状態 (Pressed) の作成 ---

        // 押したときは凹む表現（影を消して少し暗くする、あるいは内側の影を擬似的に表現）
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            // ベース色より少し暗くすることで「押し込まれた」感を出す
            setColor(manipulateColor(baseColor, 0.95f))
        }
        // Pressed状態はサイズを変えないため、IdleのSurfaceと同じ位置に合わせるためのInsetが必要ならLayerDrawableにする
        val pressedLayer = LayerDrawable(arrayOf(pressedDrawable))
        pressedLayer.setLayerInset(0, padding, padding, padding, padding)


        // --- C. StateListDrawable (Selector) にまとめる ---
        val stateListDrawable = android.graphics.drawable.StateListDrawable()

        // 押された時
        stateListDrawable.addState(
            intArrayOf(android.R.attr.state_pressed),
            pressedLayer
        )
        // 無効な時 (必要であれば)
        stateListDrawable.addState(
            intArrayOf(-android.R.attr.state_enabled),
            pressedLayer // 簡易的にPressedと同じ、あるいは透明度を下げるなど
        )
        // 通常時
        stateListDrawable.addState(
            intArrayOf(),
            idleLayer
        )

        return stateListDrawable
    }

    /**
     * 詳細な色指定によるニューモーフィズムテーマの適用
     *
     * @param backgroundColor 全体の背景色および「通常キー」の背景色
     * @param specialKeyColor 「特殊キー（Enter, Deleteなど）」の背景色
     * @param normalKeyTextColor 通常キーの文字・アイコン色
     * @param specialKeyTextColor 特殊キーの文字・アイコン色
     */
    fun setCustomNeumorphismTheme(
        backgroundColor: Int,
        specialKeyColor: Int,
        normalKeyTextColor: Int,
        specialKeyTextColor: Int
    ) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density // 角丸の半径 (8dp)

        // 1. 全体の背景色を設定
        this.setBackgroundColor(backgroundColor)

        binding.apply {
            // --- キーの分類リスト定義 ---
            val normalKeys = listOf(
                key1, key2, key3, key4, key5, key6,
                key7, key8, key9, key11, key12, keySmallLetter
            )

            val specialKeys = listOf(
                sideKeyReadAloud, keySoftLeft, sideKeySymbol,
                keyDelete, keyMoveCursorRight, keySpace, keyEnter,
                keySwitchKeyMode
            )

            // --- 色の適用処理 ---

            // 2. 通常キーへの適用
            val normalDrawableState =
                getDynamicNeumorphDrawable(backgroundColor, radius).constantState

            // ★修正: 単色のColorStateListを作成して強制適用する
            val normalColorStateList = ColorStateList.valueOf(normalKeyTextColor)

            normalKeys.forEach { view ->
                view.background = normalDrawableState?.newDrawable()?.mutate()

                // テキストカラーの適用 (ColorStateListを使う)
                if (view is MaterialTextView) view.setTextColor(normalColorStateList)
                if (view is AppCompatButton) view.setTextColor(normalColorStateList)

                // アイコンTintの適用
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, normalColorStateList)
                }
            }

            // 3. 特殊キーへの適用
            val specialDrawableState =
                getDynamicNeumorphDrawable(specialKeyColor, radius).constantState

            // ★修正: 単色のColorStateListを作成して強制適用する
            val specialColorStateList = ColorStateList.valueOf(specialKeyTextColor)

            specialKeys.forEach { view ->
                view.background = specialDrawableState?.newDrawable()?.mutate()

                // テキストカラーの適用 (ColorStateListを使う)
                if (view is MaterialTextView) view.setTextColor(specialColorStateList)
                if (view is AppCompatButton) view.setTextColor(specialColorStateList)

                // アイコンTintの適用
                if (view is AppCompatImageButton) {
                    ImageViewCompat.setImageTintList(view, specialColorStateList)
                }
            }
        }
    }

    /**
     * 色の明るさを調整するヘルパー関数
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

    fun setCurrentMode(inputMode: InputMode) {
        Log.d("setCurrentMode", "$inputMode")
        _currentInputMode.update { inputMode }
    }

    /** Allow setting an external FlickListener **/
    fun setOnFlickListener(flickListener: FlickListener) {
        this.flickListener = flickListener
    }

    /** Allow setting an external LongPressListener **/
    fun setOnLongPressListener(longPressListener: LongPressListener) {
        this.longPressListener = longPressListener
    }

    /** Padding setters for side keys (symbol, cursors, delete, enter, previous char) **/
    fun setPaddingToSideKeySymbol(paddingSize: Int) {
        binding.sideKeySymbol.setPadding(paddingSize)
    }

    fun setTextToMoveCursorMode(cursorMode: Boolean) {
        if (cursorMode) {
            setKeysCursorMoveMode()
        } else {
            handleCurrentInputModeSwitch(currentInputMode.value)
        }
        this.isCursorMode = cursorMode
    }

    fun setTextToAllButtonsFromSelectMode(isSelecMode: Boolean) {
        if (isSelecMode) {
            setKeysTextsInSelectMode()
        } else {
            handleCurrentInputModeSwitch(currentInputMode.value)
        }
    }

    /** Clean up references when view is detached **/
    private fun release() {
        flickListener = null
        longPressListener = null
        longPressJob?.cancel()
        longPressJob = null
        isCursorMode = false
        // ← CANCEL the observing coroutine when the view is detached
        //scope.coroutineContext.cancelChildren()
    }

    fun cancelTenKeyScope() {
        scope.coroutineContext.cancelChildren()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d("TenKey: onDetachedFromWindow", "called")
        release()
    }

    /** Intercept all touch events so we can handle them manually in onTouch **/
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean {
        if (isAyameMode) {
            return false
        }
        if (accessibilityManager.isTouchExplorationEnabled) {
            return true
        }
        return true
    }

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
        if (!accessibilityManager.isTouchExplorationEnabled || event.pointerCount != 1) {
            return super.onHoverEvent(event)
        }

        // Hover event x/y are view-relative; convert to screen-absolute for key detection
        if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
            hoverVelocityTracker?.recycle()
            hoverVelocityTracker = VelocityTracker.obtain()
        }
        val tempEvent = MotionEvent.obtain(event)
        if (event.action == MotionEvent.ACTION_HOVER_MOVE) {
            tempEvent.action = MotionEvent.ACTION_MOVE
        } else if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
            tempEvent.action = MotionEvent.ACTION_DOWN
        }
        hoverVelocityTracker?.addMovement(tempEvent)
        tempEvent.recycle()
        val location = IntArray(2)
        this.getLocationOnScreen(location)
        val screenX = event.x + location[0]
        val screenY = event.y + location[1]
        val key = pressedKeyByScreenCoordinates(screenX, screenY)

        when (event.action) {
            MotionEvent.ACTION_HOVER_ENTER -> {
                if (accessibilityManager.isTouchExplorationEnabled) {
                    accessibilityManager.interrupt()
                }
                currentHoverKey = key
                hoverCurrentKey = key
                hoverCurrentKeyEntryTime = System.currentTimeMillis()
                hoverCurrentKeyEntryX = screenX
                hoverCurrentKeyEntryY = screenY
                isHoverDragActive = false

                val targetView = getButtonFromKey(key) as? View
                if (targetView is View) {
                    targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                }
            }
            MotionEvent.ACTION_HOVER_MOVE -> {
                val density = context.resources.displayMetrics.density
                val swipeThreshold = 500f * density
                hoverVelocityTracker?.computeCurrentVelocity(1000)
                val xVel = hoverVelocityTracker?.getXVelocity(0) ?: 0f
                val yVel = hoverVelocityTracker?.getYVelocity(0) ?: 0f
                val speed = kotlin.math.sqrt(xVel * xVel + yVel * yVel)
                val isFlicking = speed > swipeThreshold

                val shouldSwitchKey = if (isHoverDragActive) {
                    key != hoverCurrentKey && !isFlicking
                } else {
                    key != hoverCurrentKey
                }

                if (shouldSwitchKey) {
                    hoverCurrentKey = key
                    hoverCurrentKeyEntryTime = System.currentTimeMillis()
                    hoverCurrentKeyEntryX = screenX
                    hoverCurrentKeyEntryY = screenY
                    isHoverDragActive = false
                    resetHoverDragStates()

                    val targetView = getButtonFromKey(key) as? View
                    if (targetView is View) {
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            accessibilityManager.interrupt()
                        }
                        targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                    }
                } else {
                    if (!isHoverDragActive) {
                        val dx = screenX - hoverCurrentKeyEntryX
                        val dy = screenY - hoverCurrentKeyEntryY
                        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                        if (dist > 10f * density) {
                            hoverCurrentKeyEntryTime = System.currentTimeMillis()
                            hoverCurrentKeyEntryX = screenX
                            hoverCurrentKeyEntryY = screenY
                        } else {
                            val elapsed = System.currentTimeMillis() - hoverCurrentKeyEntryTime
                            if (elapsed >= 500L) {
                                isHoverDragActive = true
                                initHoverDragState(key, screenX, screenY)
                            }
                        }
                    }
                }



                if (key != currentHoverKey) {
                    currentHoverKey = key

                    // Slide-in instant read: announce the character immediately when sliding onto a new key
                    val keyInfo = currentInputMode.value.next(keyMap = keyMap, key = key, isTablet = false)
                    val charToAnnounce = if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                        keyInfo.tap?.toAccessibilityName()
                    } else {
                        val targetView = getButtonFromKey(key) as? View
                        targetView?.contentDescription?.toString()
                    }

                    if (charToAnnounce != null) {
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            accessibilityManager.interrupt()
                        }
                        announceForAccessibility(charToAnnounce)
                        android.widget.Toast.makeText(context, charToAnnounce, android.widget.Toast.LENGTH_SHORT).show()
                        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    // Only send hover enter event if we are not actively dragging to keep focus frame synced
                    if (!isHoverDraggingRightCursor && !isHoverDraggingLeftCursor && !isHoverDraggingDeleteKey && !isHoverDraggingSpaceKey && !isHoverDraggingCharKey && !isHoverDraggingReadAloudKey) {
                        val targetView = getButtonFromKey(key) as? View
                        if (targetView is View) {
                            targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                        }
                    }
                }

                if (isHoverDraggingRightCursor) {
                    // Update peak coordinates before any trigger
                    if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                        if (screenX > hoverRightCursorDragStartX) {
                            hoverRightCursorDragStartX = screenX
                        }
                        if (screenX < hoverRightCursorDragEndX) {
                            hoverRightCursorDragEndX = screenX
                        }
                        if (screenY > hoverRightCursorDragEndY) {
                            hoverRightCursorDragEndY = screenY
                        }
                        if (screenY < hoverRightCursorDragTopY) {
                            hoverRightCursorDragTopY = screenY
                        }
                    }

                    val dxStart = screenX - hoverRightCursorDragStartX // negative when sliding left
                    val dxEnd = screenX - hoverRightCursorDragEndX     // positive when sliding right
                    val dyUp = screenY - hoverRightCursorDragEndY       // negative when sliding up
                    val dyDown = screenY - hoverRightCursorDragTopY     // positive when sliding down
                    
                    val threshold = 35f // Highly sensitive and responsive
                    val cancelLeftThreshold = -150f
                    val cancelRightThreshold = 150f
                    val cancelUpThreshold = -150f
                    val cancelDownThreshold = 150f
                    val cancelXThreshold = 60f
                    val cancelYThreshold = 60f
                    
                    Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: isHoverDraggingRightCursor=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart, dxEnd=$dxEnd, dyUp=$dyUp, dyDown=$dyDown")
                    
                    if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverRightCursorDragStartY) <= cancelYThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineStartAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Left threshold reached! Announcing '行頭'")
                            announceForAccessibility("行頭")
                            android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - hoverRightCursorDragStartY) <= cancelYThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineEndAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Right threshold reached! Announcing '行末'")
                            announceForAccessibility("行末")
                            android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - hoverRightCursorDragStartX) <= cancelXThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineUpAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Up threshold reached! Announcing '上カーソル'")
                            announceForAccessibility("上カーソル")
                            android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyDown > threshold && dyDown <= cancelDownThreshold && abs(screenX - hoverRightCursorDragStartX) <= cancelXThreshold) {
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            isLineDownAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Down threshold reached! Announcing '下カーソル'")
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

                            hoverRightCursorDragStartX = screenX
                            hoverRightCursorDragEndX = screenX
                            hoverRightCursorDragStartY = screenY
                            hoverRightCursorDragEndY = screenY
                            hoverRightCursorDragTopY = screenY

                            val targetView = getButtonFromKey(key) as? View
                            targetView?.let { view ->
                                val textStr = view.contentDescription?.toString() ?: "右移動"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isLineStartAnnounced) {
                                (dxStart < cancelLeftThreshold) || (abs(screenY - hoverRightCursorDragStartY) > cancelYThreshold)
                            } else if (isLineEndAnnounced) {
                                (dxEnd > cancelRightThreshold) || (abs(screenY - hoverRightCursorDragStartY) > cancelYThreshold)
                            } else if (isLineUpAnnounced) {
                                (dyUp < cancelUpThreshold) || (abs(screenX - hoverRightCursorDragStartX) > cancelXThreshold)
                            } else if (isLineDownAnnounced) {
                                (dyDown > cancelDownThreshold) || (abs(screenX - hoverRightCursorDragStartX) > cancelXThreshold)
                            } else {
                                (dxStart < cancelLeftThreshold) || (dxEnd > cancelRightThreshold) || (dyUp < cancelUpThreshold) || (dyDown > cancelDownThreshold) ||
                                (abs(screenY - hoverRightCursorDragStartY) > cancelYThreshold && abs(screenX - hoverRightCursorDragStartX) > cancelXThreshold)
                            }
                            if (shouldCancel && !isFlicking) {
                                isLineStartAnnounced = false
                                isLineEndAnnounced = false
                                isLineUpAnnounced = false
                                isLineDownAnnounced = false
                                isHoverDraggingRightCursor = false
                                isHoverDragActive = false
                            }
                        }
                    }
                }

                if (isHoverDraggingLeftCursor) {
                    // Update peak coordinates before any trigger
                    if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                        if (screenX > hoverLeftCursorDragStartX) {
                            hoverLeftCursorDragStartX = screenX
                        }
                        if (screenX < hoverLeftCursorDragEndX) {
                            hoverLeftCursorDragEndX = screenX
                        }
                        if (screenY > hoverLeftCursorDragEndY) {
                            hoverLeftCursorDragEndY = screenY
                        }
                        if (screenY < hoverLeftCursorDragTopY) {
                            hoverLeftCursorDragTopY = screenY
                        }
                    }

                    val dxStart = screenX - hoverLeftCursorDragStartX // negative when sliding left
                    val dxEnd = screenX - hoverLeftCursorDragEndX     // positive when sliding right
                    val dyUp = screenY - hoverLeftCursorDragEndY       // negative when sliding up
                    val dyDown = screenY - hoverLeftCursorDragTopY     // positive when sliding down
                    
                    val threshold = 35f // Highly sensitive and responsive
                    val cancelLeftThreshold = -150f
                    val cancelRightThreshold = 150f
                    val cancelUpThreshold = -150f
                    val cancelDownThreshold = 150f
                    val cancelXThreshold = 60f
                    val cancelYThreshold = 60f
                    
                    Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: isHoverDraggingLeftCursor=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart, dxEnd=$dxEnd, dyUp=$dyUp, dyDown=$dyDown")
                    
                    if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverLeftCursorDragStartY) <= cancelYThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineStartAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Left threshold reached! Announcing '行頭'")
                            announceForAccessibility("行頭")
                            android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - hoverLeftCursorDragStartY) <= cancelYThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineEndAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Right threshold reached! Announcing '行末'")
                            announceForAccessibility("行末")
                            android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - hoverLeftCursorDragStartX) <= cancelXThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineUpAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Up threshold reached! Announcing '上カーソル'")
                            announceForAccessibility("上カーソル")
                            android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyDown > threshold && dyDown <= cancelDownThreshold && abs(screenX - hoverLeftCursorDragStartX) <= cancelXThreshold) {
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            isLeftLineDownAnnounced = true
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Down threshold reached! Announcing '下カーソル'")
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

                            hoverLeftCursorDragStartX = screenX
                            hoverLeftCursorDragEndX = screenX
                            hoverLeftCursorDragStartY = screenY
                            hoverLeftCursorDragEndY = screenY
                            hoverLeftCursorDragTopY = screenY

                            val targetView = getButtonFromKey(key) as? View
                            targetView?.let { view ->
                                val textStr = view.contentDescription?.toString() ?: "左移動"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isLeftLineStartAnnounced) {
                                (dxStart < cancelLeftThreshold) || (abs(screenY - hoverLeftCursorDragStartY) > cancelYThreshold)
                            } else if (isLeftLineEndAnnounced) {
                                (dxEnd > cancelRightThreshold) || (abs(screenY - hoverLeftCursorDragStartY) > cancelYThreshold)
                            } else if (isLeftLineUpAnnounced) {
                                (dyUp < cancelUpThreshold) || (abs(screenX - hoverLeftCursorDragStartX) > cancelXThreshold)
                            } else if (isLeftLineDownAnnounced) {
                                (dyDown > cancelDownThreshold) || (abs(screenX - hoverLeftCursorDragStartX) > cancelXThreshold)
                            } else {
                                (dxStart < cancelLeftThreshold) || (dxEnd > cancelRightThreshold) || (dyUp < cancelUpThreshold) || (dyDown > cancelDownThreshold) ||
                                (abs(screenY - hoverLeftCursorDragStartY) > cancelYThreshold && abs(screenX - hoverLeftCursorDragStartX) > cancelXThreshold)
                            }
                            if (shouldCancel && !isFlicking) {
                                isLeftLineStartAnnounced = false
                                isLeftLineEndAnnounced = false
                                isLeftLineUpAnnounced = false
                                isLeftLineDownAnnounced = false
                                isHoverDraggingLeftCursor = false
                                isHoverDragActive = false
                            }
                        }
                    }
                }

                if (isHoverDraggingDeleteKey) {
                    // Update peak coordinates before any trigger
                    if (!isDeleteLeftAnnounced) {
                        if (screenX < hoverDeleteKeyDragEndX) {
                            hoverDeleteKeyDragEndX = screenX
                        }
                    }

                    val dxStart = screenX - hoverDeleteKeyDragStartX // negative when sliding left
                    
                    val threshold = 35f // Highly sensitive and responsive
                    val cancelLeftThreshold = -150f
                    val cancelRightThreshold = 150f
                    val cancelYThreshold = 60f
                    
                    Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: isHoverDraggingDeleteKey=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart")
                    
                    if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverDeleteKeyDragStartY) <= cancelYThreshold) {
                        if (!isDeleteLeftAnnounced && !isDeleteRightAnnounced) {
                            isDeleteLeftAnnounced = true
                            val annText = if (isInputComposing) "一括削除" else "行頭まで削除"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Delete Left threshold reached! Announcing '$annText'")
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxStart > threshold && dxStart <= cancelRightThreshold && abs(screenY - hoverDeleteKeyDragStartY) <= cancelYThreshold) {
                        if (!isDeleteRightAnnounced && !isDeleteLeftAnnounced) {
                            isDeleteRightAnnounced = true
                            val annText = "行末まで削除"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Delete Right threshold reached! Announcing '$annText'")
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

                            hoverDeleteKeyDragStartX = screenX
                            hoverDeleteKeyDragEndX = screenX
                            hoverDeleteKeyDragStartY = screenY
                            hoverDeleteKeyDragEndY = screenY
                            hoverDeleteKeyDragTopY = screenY

                            val targetView = getButtonFromKey(key) as? View
                            targetView?.let { view ->
                                val textStr = view.contentDescription?.toString() ?: "削除"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isDeleteLeftAnnounced) {
                                (dxStart < cancelLeftThreshold) || (abs(screenY - hoverDeleteKeyDragStartY) > cancelYThreshold)
                            } else if (isDeleteRightAnnounced) {
                                (dxStart > cancelRightThreshold) || (abs(screenY - hoverDeleteKeyDragStartY) > cancelYThreshold)
                            } else {
                                (dxStart < cancelLeftThreshold) || (dxStart > cancelRightThreshold) || (abs(screenY - hoverDeleteKeyDragStartY) > cancelYThreshold)
                            }
                            if (shouldCancel && !isFlicking) {
                                isDeleteLeftAnnounced = false
                                isDeleteRightAnnounced = false
                                isHoverDraggingDeleteKey = false
                                isHoverDragActive = false
                            }
                        }
                    }
                }

                if (isHoverDraggingSpaceKey) {
                    val dyStart = screenY - hoverSpaceKeyDragStartY // positive when sliding down
                    val dxStart = screenX - hoverSpaceKeyDragStartX // positive when sliding right
                    
                    val threshold = 35f
                    val dragUpThreshold = -35f
                    val dragRightThreshold = 35f
                    val cancelDownThreshold = 150f
                    val cancelXThreshold = 60f
                    
                    Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: isHoverDraggingSpaceKey=true, screenX=$screenX, screenY=$screenY, dyStart=$dyStart, dxStart=$dxStart")
                    
                    if (dyStart > threshold && dyStart <= cancelDownThreshold && abs(dxStart) <= cancelXThreshold) {
                        if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                            isSpaceDownAnnounced = true
                            val annText = "予測変換"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Space Down threshold reached! Announcing '$annText'")
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (currentInputMode.value != InputMode.ModeNumber && dyStart < dragUpThreshold && dyStart >= -cancelDownThreshold && abs(dxStart) <= cancelXThreshold) {
                        if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                            isSpaceUpAnnounced = true
                            val annText = if (currentInputMode.value == InputMode.ModeEnglish) "全角英語変換" else "カタカナ変換"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Space Up threshold reached! Announcing '$annText'")
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (currentInputMode.value == InputMode.ModeJapanese && dxStart > dragRightThreshold && dxStart <= cancelDownThreshold && abs(dyStart) <= cancelXThreshold) {
                        if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                            isSpaceRightAnnounced = true
                            val annText = "半角カタカナ"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Space Right threshold reached! Announcing '$annText'")
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

                            hoverSpaceKeyDragStartX = screenX
                            hoverSpaceKeyDragEndX = screenX
                            hoverSpaceKeyDragStartY = screenY
                            hoverSpaceKeyDragEndY = screenY

                            val targetView = getButtonFromKey(key) as? View
                            targetView?.let { view ->
                                val textStr = view.contentDescription?.toString() ?: "スペース"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = when {
                                isSpaceDownAnnounced -> (dyStart > cancelDownThreshold) || (abs(dxStart) > cancelXThreshold)
                                isSpaceUpAnnounced -> (dyStart < -cancelDownThreshold) || (abs(dxStart) > cancelXThreshold)
                                isSpaceRightAnnounced -> (dxStart > cancelDownThreshold) || (abs(dyStart) > cancelXThreshold)
                                else -> (dyStart > cancelDownThreshold) || (abs(dxStart) > cancelXThreshold)
                            }
                            if (shouldCancel && !isFlicking) {
                                isSpaceDownAnnounced = false
                                isSpaceUpAnnounced = false
                                isSpaceRightAnnounced = false
                                isHoverDraggingSpaceKey = false
                                isHoverDragActive = false
                            }
                        }
                    }
                }

                if (isHoverDraggingReadAloudKey) {
                    // Update peak coordinates before any trigger
                    if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                        if (screenX > hoverReadAloudKeyDragStartX) {
                            hoverReadAloudKeyDragStartX = screenX
                        }
                        if (screenX < hoverReadAloudKeyDragEndX) {
                            hoverReadAloudKeyDragEndX = screenX
                        }
                        if (screenY > hoverReadAloudKeyDragEndY) {
                            hoverReadAloudKeyDragEndY = screenY
                        }
                        if (screenY < hoverReadAloudKeyDragTopY) {
                            hoverReadAloudKeyDragTopY = screenY
                        }
                    }

                    val dxStart = screenX - hoverReadAloudKeyDragStartX // negative when sliding left
                    val dxEnd = screenX - hoverReadAloudKeyDragEndX     // positive when sliding right
                    val dyUp = screenY - hoverReadAloudKeyDragEndY       // negative when sliding up
                    
                    val threshold = 35f // Highly sensitive and responsive
                    val cancelLeftThreshold = -150f
                    val cancelRightThreshold = 150f
                    val cancelUpThreshold = -150f
                    val cancelXThreshold = 60f
                    val cancelYThreshold = 60f
                    
                    Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: isHoverDraggingReadAloudKey=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart, dxEnd=$dxEnd, dyUp=$dyUp")
                    
                    if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - hoverReadAloudKeyDragStartY) <= cancelYThreshold) {
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            isReadAloudLeftAnnounced = true
                            val annText = "詳細読み上げ"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Read Aloud Left threshold reached! Announcing '$annText'")
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - hoverReadAloudKeyDragStartY) <= cancelYThreshold) {
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            isReadAloudRightAnnounced = true
                            val annText = "文末まで読み上げ"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Read Aloud Right threshold reached! Announcing '$annText'")
                            announceForAccessibility(annText)
                            android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                    } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - hoverReadAloudKeyDragStartX) <= cancelXThreshold) {
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            isReadAloudUpAnnounced = true
                            val annText = "文頭から読み上げ"
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Read Aloud Up threshold reached! Announcing '$annText'")
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

                            hoverReadAloudKeyDragStartX = screenX
                            hoverReadAloudKeyDragEndX = screenX
                            hoverReadAloudKeyDragStartY = screenY
                            hoverReadAloudKeyDragEndY = screenY
                            hoverReadAloudKeyDragTopY = screenY

                            val targetView = getButtonFromKey(key) as? View
                            targetView?.let { view ->
                                val textStr = view.contentDescription?.toString() ?: "読み上げ"
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                announceForAccessibility(textStr)
                                android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else {
                            val shouldCancel = if (isReadAloudLeftAnnounced) {
                                (dxStart < cancelLeftThreshold) || (abs(screenY - hoverReadAloudKeyDragStartY) > cancelYThreshold)
                            } else if (isReadAloudRightAnnounced) {
                                (dxEnd > cancelRightThreshold) || (abs(screenY - hoverReadAloudKeyDragStartY) > cancelYThreshold)
                            } else if (isReadAloudUpAnnounced) {
                                (dyUp < cancelUpThreshold) || (abs(screenX - hoverReadAloudKeyDragStartX) > cancelXThreshold)
                            } else {
                                (dxStart < cancelLeftThreshold) || (dxEnd > cancelRightThreshold) || (dyUp < cancelUpThreshold) || 
                                (abs(screenY - hoverReadAloudKeyDragStartY) > cancelYThreshold && abs(screenX - hoverReadAloudKeyDragStartX) > cancelXThreshold)
                            }
                            if (shouldCancel && !isFlicking) {
                                isReadAloudLeftAnnounced = false
                                isReadAloudUpAnnounced = false
                                isReadAloudRightAnnounced = false
                                isHoverDraggingReadAloudKey = false
                                isHoverDragActive = false
                            }
                        }
                    }
                }

                if (isHoverDraggingCharKey && hoverCharKey != Key.NotSelected) {
                    val activeKeyInfo = currentInputMode.value.next(keyMap = keyMap, key = hoverCharKey, isTablet = false)
                    if (activeKeyInfo is KeyInfo.KeyTapFlickInfo) {
                        val dx = screenX - hoverCharKeyDragStartX
                        val dy = screenY - hoverCharKeyDragStartY
                        
                        val button = getButtonFromKey(hoverCharKey) as? View
                        val threshold = if (button != null) {
                            val keyWidth = button.width.toFloat()
                            val keyHeight = button.height.toFloat()
                            if (keyWidth > 0f && keyHeight > 0f) {
                                kotlin.math.min(keyWidth / 6f, keyHeight / 6f)
                            } else {
                                35f
                            }
                        } else {
                            35f
                        }
                        
                        val nextGesture = when {
                            abs(dx) < threshold && abs(dy) < threshold -> GestureType.Tap
                            abs(dx) > abs(dy) && dx < -threshold -> GestureType.FlickLeft
                            abs(dx) <= abs(dy) && dy < -threshold -> GestureType.FlickTop
                            abs(dx) > abs(dy) && dx > threshold -> GestureType.FlickRight
                            abs(dx) <= abs(dy) && dy > threshold -> GestureType.FlickBottom
                            else -> GestureType.Null
                        }
                        
                        if (nextGesture == GestureType.Null && !isFlicking) {
                            Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Drag out of bounds on Char Key $hoverCharKey. Drag cancelled.")
                            isHoverDraggingCharKey = false
                            hoverCharKey = Key.NotSelected
                            isHoverDragActive = false
                            
                            // Immediately announce the currently hovered key
                            val targetView = getButtonFromKey(key)
                            if (targetView is View) {
                                if (accessibilityManager.isTouchExplorationEnabled) {
                                    accessibilityManager.interrupt()
                                }
                                targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                            }
                        } else {
                            val charToAnnounce = when (nextGesture) {
                                GestureType.Tap -> activeKeyInfo.tap?.toAccessibilityName()
                                GestureType.FlickLeft -> activeKeyInfo.flickLeft?.toAccessibilityName()
                                GestureType.FlickTop -> activeKeyInfo.flickTop?.toAccessibilityName()
                                GestureType.FlickRight -> activeKeyInfo.flickRight?.toAccessibilityName()
                                GestureType.FlickBottom -> activeKeyInfo.flickBottom?.toAccessibilityName()
                                else -> null
                            }
                            
                            if (charToAnnounce != null && charToAnnounce != hoverLastAnnouncedChar) {
                                val previousGesture = hoverActiveGesture
                                hoverLastAnnouncedChar = charToAnnounce
                                hoverActiveGesture = nextGesture
                                Log.d("TenKeyDrag", "ACTION_HOVER_MOVE: Char Key gesture changed to $nextGesture. Announcing '$charToAnnounce'")
                                if (nextGesture != GestureType.Tap || previousGesture != GestureType.Tap) {
                                    announceForAccessibility(charToAnnounce)
                                    android.widget.Toast.makeText(context, charToAnnounce, android.widget.Toast.LENGTH_SHORT).show()
                                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_HOVER_EXIT -> {
                hoverVelocityTracker?.recycle()
                hoverVelocityTracker = null
                // Prevent accidental input when sliding off the keyboard edge.
                val buffer = 2f // Tolerance pixels for the view boundary
                val viewWidth = width.toFloat()
                val viewHeight = height.toFloat()
                
                val isSlideOff = event.x <= buffer || 
                               event.x >= (viewWidth - buffer) || 
                               event.y <= buffer || 
                               event.y >= (viewHeight - buffer)

                if (isHoverDraggingRightCursor) {
                    isHoverDraggingRightCursor = false
                    Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: hover right cursor drag finished. isLineStartAnnounced=$isLineStartAnnounced, isLineEndAnnounced=$isLineEndAnnounced, isLineUpAnnounced=$isLineUpAnnounced, isLineDownAnnounced=$isLineDownAnnounced")
                    
                    var triggerLineStart = isLineStartAnnounced
                    var triggerLineEnd = isLineEndAnnounced
                    var triggerLineUp = isLineUpAnnounced
                    var triggerLineDown = isLineDownAnnounced

                    // Fallback for fast flick in Hover Mode: if not already announced, check final delta
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

                    if (triggerLineStart) {
                        isLineStartAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0001'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerLineEnd) {
                        isLineEndAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0002'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerLineUp) {
                        isLineUpAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0003'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerLineDown) {
                        isLineDownAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0004'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    }
                }

                if (isHoverDraggingLeftCursor) {
                    isHoverDraggingLeftCursor = false
                    Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: hover left cursor drag finished. isLeftLineStartAnnounced=$isLeftLineStartAnnounced, isLeftLineEndAnnounced=$isLeftLineEndAnnounced, isLeftLineUpAnnounced=$isLeftLineUpAnnounced, isLeftLineDownAnnounced=$isLeftLineDownAnnounced")
                    
                    var triggerLineStart = isLeftLineStartAnnounced
                    var triggerLineEnd = isLeftLineEndAnnounced
                    var triggerLineUp = isLeftLineUpAnnounced
                    var triggerLineDown = isLeftLineDownAnnounced

                    // Fallback for fast flick in Hover Mode: if not already announced, check final delta
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

                    if (triggerLineStart) {
                        isLeftLineStartAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0001'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerLineEnd) {
                        isLeftLineEndAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0002'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerLineUp) {
                        isLeftLineUpAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0003'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerLineDown) {
                        isLeftLineDownAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0004'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    }
                }

                if (isHoverDraggingDeleteKey) {
                    isHoverDraggingDeleteKey = false
                    Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: hover Delete drag finished. isDeleteLeftAnnounced=$isDeleteLeftAnnounced, isDeleteRightAnnounced=$isDeleteRightAnnounced, isDeleteUpAnnounced=$isDeleteUpAnnounced")
                    
                    var triggerDeleteLeft = isDeleteLeftAnnounced
                    var triggerDeleteRight = isDeleteRightAnnounced

                    // Fallback for fast flick in Hover Mode: if not already announced, check final delta
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

                    if (triggerDeleteLeft) {
                        isDeleteLeftAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyDelete,
                                char = '\u0005'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerDeleteRight) {
                        isDeleteRightAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyDelete,
                                char = '\u0007'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    }
                }

                if (isHoverDraggingReadAloudKey) {
                    isHoverDraggingReadAloudKey = false
                    Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: hover Read Aloud drag finished. Left=$isReadAloudLeftAnnounced, Up=$isReadAloudUpAnnounced, Right=$isReadAloudRightAnnounced")
                    
                    var triggerLeft = isReadAloudLeftAnnounced
                    var triggerUp = isReadAloudUpAnnounced
                    var triggerRight = isReadAloudRightAnnounced

                    // Fallback for fast flick in Hover Mode: if not already announced, check final delta
                    if (!triggerLeft && !triggerUp && !triggerRight) {
                        val dx = screenX - hoverReadAloudKeyDragStartX
                        val dy = screenY - hoverReadAloudKeyDragStartY
                        val threshold = 35f
                        val cancelXThreshold = 60f
                        val cancelYThreshold = 60f
                        
                        if (abs(dy) <= cancelYThreshold && dx < -threshold) {
                            triggerLeft = true
                        } else if (abs(dy) <= cancelYThreshold && dx > threshold) {
                            triggerRight = true
                        } else if (abs(dx) <= cancelXThreshold && dy < -threshold) {
                            triggerUp = true
                        }
                    }

                    if (triggerLeft) {
                        isReadAloudLeftAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyReadAloud,
                                char = '\u0011'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerUp) {
                        isReadAloudUpAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyReadAloud,
                                char = '\u0012'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    } else if (triggerRight) {
                        isReadAloudRightAnnounced = false
                        if (!isSlideOff) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyReadAloud,
                                char = '\u0013'
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    }
                }

                if (isHoverDraggingSpaceKey) {
                    isHoverDraggingSpaceKey = false
                    Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: hover Space drag finished. isSpaceDownAnnounced=$isSpaceDownAnnounced, isSpaceUpAnnounced=$isSpaceUpAnnounced, isSpaceRightAnnounced=$isSpaceRightAnnounced")
                    
                    var triggerSpaceDown = isSpaceDownAnnounced
                    var triggerSpaceUp = isSpaceUpAnnounced
                    var triggerSpaceRight = isSpaceRightAnnounced

                    // Fallback for fast flick in Hover Mode: if not already announced, check final delta
                    if (!triggerSpaceDown && !triggerSpaceUp && !triggerSpaceRight) {
                        val dx = screenX - hoverSpaceKeyDragStartX
                        val dy = screenY - hoverSpaceKeyDragStartY
                        val threshold = 35f
                        val cancelXThreshold = 60f
                        
                        if (abs(dx) <= cancelXThreshold && dy > threshold) {
                            triggerSpaceDown = true
                        } else if (currentInputMode.value != InputMode.ModeNumber && abs(dx) <= cancelXThreshold && dy < -threshold) {
                            triggerSpaceUp = true
                        } else if (currentInputMode.value == InputMode.ModeJapanese && abs(dy) <= cancelXThreshold && dx > threshold) {
                            triggerSpaceRight = true
                        }
                    }

                    val gestureType = when {
                        triggerSpaceDown -> GestureType.FlickBottom
                        triggerSpaceUp -> GestureType.FlickTop
                        triggerSpaceRight -> GestureType.FlickRight
                        else -> GestureType.Tap
                    }
                    isSpaceDownAnnounced = false
                    isSpaceUpAnnounced = false
                    isSpaceRightAnnounced = false

                    if (gestureType != GestureType.Null && !isSlideOff) {
                        flickListener?.onFlick(
                            gestureType = gestureType,
                            key = Key.SideKeySpace,
                            char = null
                        )
                    }
                    currentHoverKey = Key.NotSelected
                    return true
                }

                if (isHoverDraggingCharKey && hoverCharKey != Key.NotSelected) {
                    isHoverDraggingCharKey = false
                    val activeKey = hoverCharKey
                    hoverCharKey = Key.NotSelected
                    Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: hover Char drag finished on $activeKey. hoverActiveGesture=$hoverActiveGesture")
                    
                    val activeKeyInfo = currentInputMode.value.next(keyMap = keyMap, key = activeKey, isTablet = false)
                    if (activeKeyInfo is KeyInfo.KeyTapFlickInfo) {
                        val finalChar = when (hoverActiveGesture) {
                            GestureType.Tap -> activeKeyInfo.tap
                            GestureType.FlickLeft -> activeKeyInfo.flickLeft
                            GestureType.FlickTop -> activeKeyInfo.flickTop
                            GestureType.FlickRight -> activeKeyInfo.flickRight
                            GestureType.FlickBottom -> activeKeyInfo.flickBottom
                            else -> null
                        }
                        
                        if (finalChar != null && !isSlideOff) {
                            Log.d("TenKeyDrag", "ACTION_HOVER_EXIT: Dispatching flick event for $activeKey: $hoverActiveGesture -> $finalChar")
                            flickListener?.onFlick(
                                gestureType = hoverActiveGesture,
                                key = activeKey,
                                char = finalChar
                            )
                        }
                        currentHoverKey = Key.NotSelected
                        return true
                    }
                }

                if (!isSlideOff) {
                    if (currentHoverKey != Key.NotSelected) {
                        performKeyInput(currentHoverKey)
                    }
                }
                currentHoverKey = Key.NotSelected
            }
        }
        return true
    }

    /** Find which Key is at the given screen-absolute coordinates **/
    private fun pressedKeyByScreenCoordinates(x: Float, y: Float): Key {
        // キャッシュがない、またはサイズが変わった場合にのみリフレッシュする
        if (cachedKeyRects == null || width != lastWidth || height != lastHeight) {
            lastWidth = width
            lastHeight = height
            refreshKeyRects()
        }

        val keyRects = cachedKeyRects ?: return Key.NotSelected

        keyRects.forEach { rect ->
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) return rect.key
        }
        return keyRects.minByOrNull { rect ->
            val cx = (rect.left + rect.right) / 2
            val cy = (rect.top + rect.bottom) / 2
            (x - cx) * (x - cx) + (y - cy) * (y - cy)
        }?.key ?: Key.NotSelected
    }

    private fun refreshKeyRects() {
        cachedKeyRects = listOf(
            KeyRect(Key.SideKeyReadAloud, binding.sideKeyReadAloud.layoutXPosition(), binding.sideKeyReadAloud.layoutYPosition(), binding.sideKeyReadAloud.layoutXPosition() + binding.sideKeyReadAloud.width, binding.sideKeyReadAloud.layoutYPosition() + binding.sideKeyReadAloud.height),
            KeyRect(Key.KeyA, binding.key1.layoutXPosition(), binding.key1.layoutYPosition(), binding.key1.layoutXPosition() + binding.key1.width, binding.key1.layoutYPosition() + binding.key1.height),
            KeyRect(Key.KeyKA, binding.key2.layoutXPosition(), binding.key2.layoutYPosition(), binding.key2.layoutXPosition() + binding.key2.width, binding.key2.layoutYPosition() + binding.key2.height),
            KeyRect(Key.KeySA, binding.key3.layoutXPosition(), binding.key3.layoutYPosition(), binding.key3.layoutXPosition() + binding.key3.width, binding.key3.layoutYPosition() + binding.key3.height),
            KeyRect(Key.SideKeyDelete, binding.keyDelete.layoutXPosition(), binding.keyDelete.layoutYPosition(), binding.keyDelete.layoutXPosition() + binding.keyDelete.width, binding.keyDelete.layoutYPosition() + binding.keyDelete.height),
            KeyRect(Key.SideKeyCursorLeft, binding.keySoftLeft.layoutXPosition(), binding.keySoftLeft.layoutYPosition(), binding.keySoftLeft.layoutXPosition() + binding.keySoftLeft.width, binding.keySoftLeft.layoutYPosition() + binding.keySoftLeft.height),
            KeyRect(Key.KeyTA, binding.key4.layoutXPosition(), binding.key4.layoutYPosition(), binding.key4.layoutXPosition() + binding.key4.width, binding.key4.layoutYPosition() + binding.key4.height),
            KeyRect(Key.KeyNA, binding.key5.layoutXPosition(), binding.key5.layoutYPosition(), binding.key5.layoutXPosition() + binding.key5.width, binding.key5.layoutYPosition() + binding.key5.height),
            KeyRect(Key.KeyHA, binding.key6.layoutXPosition(), binding.key6.layoutYPosition(), binding.key6.layoutXPosition() + binding.key6.width, binding.key6.layoutYPosition() + binding.key6.height),
            KeyRect(Key.SideKeyCursorRight, binding.keyMoveCursorRight.layoutXPosition(), binding.keyMoveCursorRight.layoutYPosition(), binding.keyMoveCursorRight.layoutXPosition() + binding.keyMoveCursorRight.width, binding.keyMoveCursorRight.layoutYPosition() + binding.keyMoveCursorRight.height),
            KeyRect(Key.SideKeySymbol, binding.sideKeySymbol.layoutXPosition(), binding.sideKeySymbol.layoutYPosition(), binding.sideKeySymbol.layoutXPosition() + binding.sideKeySymbol.width, binding.sideKeySymbol.layoutYPosition() + binding.sideKeySymbol.height),
            KeyRect(Key.KeyMA, binding.key7.layoutXPosition(), binding.key7.layoutYPosition(), binding.key7.layoutXPosition() + binding.key7.width, binding.key7.layoutYPosition() + binding.key7.height),
            KeyRect(Key.KeyYA, binding.key8.layoutXPosition(), binding.key8.layoutYPosition(), binding.key8.layoutXPosition() + binding.key8.width, binding.key8.layoutYPosition() + binding.key8.height),
            KeyRect(Key.KeyRA, binding.key9.layoutXPosition(), binding.key9.layoutYPosition(), binding.key9.layoutXPosition() + binding.key9.width, binding.key9.layoutYPosition() + binding.key9.height),
            KeyRect(Key.SideKeySpace, binding.keySpace.layoutXPosition(), binding.keySpace.layoutYPosition(), binding.keySpace.layoutXPosition() + binding.keySpace.width, binding.keySpace.layoutYPosition() + binding.keySpace.height),
            KeyRect(Key.SideKeyInputMode, binding.keySwitchKeyMode.layoutXPosition(), binding.keySwitchKeyMode.layoutYPosition(), binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width, binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height),
            KeyRect(Key.KeyDakutenSmall, binding.keySmallLetter.layoutXPosition(), binding.keySmallLetter.layoutYPosition(), binding.keySmallLetter.layoutXPosition() + binding.keySmallLetter.width, binding.keySmallLetter.layoutYPosition() + binding.keySmallLetter.height),
            KeyRect(Key.KeyWA, binding.key11.layoutXPosition(), binding.key11.layoutYPosition(), binding.key11.layoutXPosition() + binding.key11.width, binding.key11.layoutYPosition() + binding.key11.height),
            KeyRect(Key.KeyKutouten, binding.key12.layoutXPosition(), binding.key12.layoutYPosition(), binding.key12.layoutXPosition() + binding.key12.width, binding.key12.layoutYPosition() + binding.key12.height),
            KeyRect(Key.SideKeyEnter, binding.keyEnter.layoutXPosition(), binding.keyEnter.layoutYPosition(), binding.keyEnter.layoutXPosition() + binding.keyEnter.width, binding.keyEnter.layoutYPosition() + binding.keyEnter.height)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View?, event: MotionEvent?): Boolean {
        if (isAyameMode) {
            return false
        }
        if (view != null && event != null) {
            if (accessibilityManager.isTouchExplorationEnabled && !isCalledFromHoverEvent) {
                return true
            }
            if (view.visibility != View.VISIBLE) {
                return false
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
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        val key = pressedKeyByMotionEvent(event, 0)
                    flickListener?.onFlick(GestureType.Down, key, null)

                    pressedKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        PressedKey(
                            key = key,
                            pointer = 0,
                            initialX = event.getRawX(event.actionIndex),
                            initialY = event.getRawY(event.actionIndex),
                        )
                    } else {
                        PressedKey(
                            key = key,
                            pointer = 0,
                            initialX = event.getX(event.actionIndex),
                            initialY = event.getY(event.actionIndex),
                        )
                    }

                    // Drag tracking for Key.SideKeyCursorRight / Key.SideKeyCursorLeft
                    Log.d("TenKeyDrag", "ACTION_DOWN: key=$key")
                    if (key == Key.SideKeyCursorRight) {
                        isDraggingRightCursor = true
                        isLineStartAnnounced = false
                        isLineEndAnnounced = false
                        isLineUpAnnounced = false
                        isLineDownAnnounced = false
                        val currentX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawX(0)
                        } else {
                            event.getX(0)
                        }
                        val currentY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawY(0)
                        } else {
                            event.getY(0)
                        }
                        rightCursorDragStartX = currentX
                        rightCursorDragEndX = currentX
                        rightCursorDragStartY = currentY
                        rightCursorDragEndY = currentY
                        rightCursorDragTopY = currentY
                        isDraggingLeftCursor = false
                        isDraggingDeleteKey = false
                        isDraggingSpaceKey = false
                        isSpaceDownAnnounced = false
                        Log.d("TenKeyDrag", "ACTION_DOWN: Right Cursor drag initialized. StartX=$rightCursorDragStartX")
                    } else if (key == Key.SideKeyCursorLeft) {
                        isDraggingLeftCursor = true
                        isLeftLineStartAnnounced = false
                        isLeftLineEndAnnounced = false
                        isLeftLineUpAnnounced = false
                        isLeftLineDownAnnounced = false
                        val currentX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawX(0)
                        } else {
                            event.getX(0)
                        }
                        val currentY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawY(0)
                        } else {
                            event.getY(0)
                        }
                        leftCursorDragStartX = currentX
                        leftCursorDragEndX = currentX
                        leftCursorDragStartY = currentY
                        leftCursorDragEndY = currentY
                        leftCursorDragTopY = currentY
                        isDraggingRightCursor = false
                        isDraggingDeleteKey = false
                        isDraggingSpaceKey = false
                        isSpaceDownAnnounced = false
                        Log.d("TenKeyDrag", "ACTION_DOWN: Left Cursor drag initialized. StartX=$leftCursorDragStartX")
                    } else if (key == Key.SideKeyDelete) {
                        isDraggingDeleteKey = true
                        isDeleteLeftAnnounced = false
                        isDeleteRightAnnounced = false
                        isDeleteUpAnnounced = false
                        val currentX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawX(0)
                        } else {
                            event.getX(0)
                        }
                        val currentY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawY(0)
                        } else {
                            event.getY(0)
                        }
                        deleteKeyDragStartX = currentX
                        deleteKeyDragEndX = currentX
                        deleteKeyDragStartY = currentY
                        deleteKeyDragEndY = currentY
                        deleteKeyDragTopY = currentY
                        isDraggingRightCursor = false
                        isDraggingLeftCursor = false
                        isDraggingSpaceKey = false
                        isSpaceDownAnnounced = false
                        Log.d("TenKeyDrag", "ACTION_DOWN: Delete key drag initialized. StartX=$deleteKeyDragStartX")
                    } else if (key == Key.SideKeySpace) {
                        isDraggingSpaceKey = true
                        isSpaceDownAnnounced = false
                        isSpaceUpAnnounced = false
                        isSpaceRightAnnounced = false
                        val currentX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawX(0)
                        } else {
                            event.getX(0)
                        }
                        val currentY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawY(0)
                        } else {
                            event.getY(0)
                        }
                        spaceKeyDragStartX = currentX
                        spaceKeyDragEndX = currentX
                        spaceKeyDragStartY = currentY
                        spaceKeyDragEndY = currentY
                        isDraggingRightCursor = false
                        isDraggingLeftCursor = false
                        isDraggingDeleteKey = false
                        Log.d("TenKeyDrag", "ACTION_DOWN: Space key drag initialized. StartX=$spaceKeyDragStartX")
                    } else if (key == Key.SideKeyReadAloud) {
                        isDraggingReadAloudKey = true
                        isReadAloudLeftAnnounced = false
                        isReadAloudUpAnnounced = false
                        isReadAloudRightAnnounced = false
                        val currentX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawX(0)
                        } else {
                            event.getX(0)
                        }
                        val currentY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawY(0)
                        } else {
                            event.getY(0)
                        }
                        readAloudKeyDragStartX = currentX
                        readAloudKeyDragEndX = currentX
                        readAloudKeyDragStartY = currentY
                        readAloudKeyDragEndY = currentY
                        readAloudKeyDragTopY = currentY
                        isDraggingRightCursor = false
                        isDraggingLeftCursor = false
                        isDraggingDeleteKey = false
                        isDraggingSpaceKey = false
                        isSpaceDownAnnounced = false
                        Log.d("TenKeyDrag", "ACTION_DOWN: Read Aloud key drag initialized. StartX=$readAloudKeyDragStartX")
                    } else {
                        isDraggingRightCursor = false
                        isLineStartAnnounced = false
                        isLineEndAnnounced = false
                        isLineUpAnnounced = false
                        isLineDownAnnounced = false
                        isDraggingLeftCursor = false
                        isLeftLineStartAnnounced = false
                        isLeftLineEndAnnounced = false
                        isLeftLineUpAnnounced = false
                        isLeftLineDownAnnounced = false
                        isDraggingDeleteKey = false
                        isDeleteLeftAnnounced = false
                        isDeleteRightAnnounced = false
                        isDeleteUpAnnounced = false
                        isDraggingSpaceKey = false
                        isSpaceDownAnnounced = false
                    }

                    Log.d("TenKey: ACTION_DOWN", "called ${pressedKey.key}")

                    if (isCursorMode) {
                        return true
                    }

                    setKeyPressed()
                    longPressJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                        if (pressedKey.key != Key.NotSelected) {
                            longPressListener?.onLongPress(pressedKey.key)
                            isLongPressed = true
                            onLongPressed()
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_UP -> {
                    resetLongPressAction()
                    
                    // 1) Drag/Hold line move confirmation
                    if (isDraggingRightCursor) {
                        isDraggingRightCursor = false
                        Log.d("TenKeyDrag", "ACTION_UP: Right Cursor drag finished. isLineStartAnnounced=$isLineStartAnnounced, isLineEndAnnounced=$isLineEndAnnounced, isLineUpAnnounced=$isLineUpAnnounced, isLineDownAnnounced=$isLineDownAnnounced")
                        if (isLineStartAnnounced) {
                            isLineStartAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0001'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isLineEndAnnounced) {
                            isLineEndAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0002'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isLineUpAnnounced) {
                            isLineUpAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0003'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isLineDownAnnounced) {
                            isLineDownAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0004'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }

                    if (isDraggingLeftCursor) {
                        isDraggingLeftCursor = false
                        Log.d("TenKeyDrag", "ACTION_UP: Left Cursor drag finished. isLeftLineStartAnnounced=$isLeftLineStartAnnounced, isLeftLineEndAnnounced=$isLeftLineEndAnnounced, isLeftLineUpAnnounced=$isLeftLineUpAnnounced, isLeftLineDownAnnounced=$isLeftLineDownAnnounced")
                        if (isLeftLineStartAnnounced) {
                            isLeftLineStartAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0001'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isLeftLineEndAnnounced) {
                            isLeftLineEndAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0002'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isLeftLineUpAnnounced) {
                            isLeftLineUpAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0003'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isLeftLineDownAnnounced) {
                            isLeftLineDownAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0004'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }

                    if (isDraggingDeleteKey) {
                        isDraggingDeleteKey = false
                        Log.d("TenKeyDrag", "ACTION_UP: Delete key drag finished. isDeleteLeftAnnounced=$isDeleteLeftAnnounced, isDeleteRightAnnounced=$isDeleteRightAnnounced")
                        if (isDeleteLeftAnnounced) {
                            isDeleteLeftAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyDelete,
                                char = '\u0005'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (isDeleteRightAnnounced) {
                            isDeleteRightAnnounced = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyDelete,
                                char = '\u0007'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }

                    if (isDraggingSpaceKey) {
                        isDraggingSpaceKey = false
                        Log.d("TenKeyDrag", "ACTION_UP: Space key drag finished. isSpaceDownAnnounced=$isSpaceDownAnnounced, isSpaceUpAnnounced=$isSpaceUpAnnounced, isSpaceRightAnnounced=$isSpaceRightAnnounced")
                        if (isSpaceDownAnnounced || isSpaceUpAnnounced || isSpaceRightAnnounced) {
                            val gestureType = when {
                                isSpaceDownAnnounced -> GestureType.FlickBottom
                                isSpaceUpAnnounced -> GestureType.FlickTop
                                isSpaceRightAnnounced -> GestureType.FlickRight
                                else -> GestureType.Null
                            }
                            isSpaceDownAnnounced = false
                            isSpaceUpAnnounced = false
                            isSpaceRightAnnounced = false
                            if (gestureType != GestureType.Null) {
                                flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = Key.SideKeySpace,
                                    char = null
                                )
                            }
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }

                    if (isDraggingReadAloudKey) {
                        isDraggingReadAloudKey = false
                        Log.d("TenKeyDrag", "ACTION_UP: Read Aloud key drag finished. Left=$isReadAloudLeftAnnounced, Up=$isReadAloudUpAnnounced, Right=$isReadAloudRightAnnounced")
                        val targetChar = when {
                            isReadAloudLeftAnnounced -> '\u0011'
                            isReadAloudUpAnnounced -> '\u0012'
                            isReadAloudRightAnnounced -> '\u0013'
                            else -> null
                        }
                        isReadAloudLeftAnnounced = false
                        isReadAloudUpAnnounced = false
                        isReadAloudRightAnnounced = false
                        
                        flickListener?.onFlick(
                            gestureType = GestureType.Tap,
                            key = Key.SideKeyReadAloud,
                            char = targetChar
                        )
                        resetAllKeys()
                        popupWindowActive.hide()
                        return false
                    }

                    // 2) Fast flick gesture fallback (if drag didn't confirm because of quick swipe & release)
                    if (pressedKey.key == Key.SideKeyCursorRight) {
                        val gestureType = getGestureType(event)
                        Log.d("TenKeyDrag", "ACTION_UP: Right Cursor fast flick gesture detected: $gestureType")
                        if (gestureType == GestureType.FlickLeft) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0001'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickRight) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0002'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickTop) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0003'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickBottom) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorRight,
                                char = '\u0004'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }

                    if (pressedKey.key == Key.SideKeyCursorLeft) {
                        val gestureType = getGestureType(event)
                        Log.d("TenKeyDrag", "ACTION_UP: Left Cursor fast flick gesture detected: $gestureType")
                        if (gestureType == GestureType.FlickLeft) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0001'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickRight) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0002'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickTop) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0003'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickBottom) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyCursorLeft,
                                char = '\u0004'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }

                    if (pressedKey.key == Key.SideKeyDelete) {
                        val gestureType = getGestureType(event)
                        Log.d("TenKeyDrag", "ACTION_UP: Delete key fast flick gesture detected: $gestureType")
                        if (gestureType == GestureType.FlickLeft) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyDelete,
                                char = '\u0005'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickRight) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyDelete,
                                char = '\u0007'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }
                    if (pressedKey.key == Key.SideKeyReadAloud) {
                        val gestureType = getGestureType(event)
                        Log.d("TenKeyDrag", "ACTION_UP: Read Aloud key fast flick gesture detected: $gestureType")
                        if (gestureType == GestureType.FlickLeft) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyReadAloud,
                                char = '\u0011'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickTop) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyReadAloud,
                                char = '\u0012'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        } else if (gestureType == GestureType.FlickRight) {
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = Key.SideKeyReadAloud,
                                char = '\u0013'
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }
                    if (pressedKey.key == Key.SideKeySpace) {
                        val gestureType = getGestureType(event)
                        Log.d("TenKeyDrag", "ACTION_UP: Space key fast flick gesture detected: $gestureType")
                        if (gestureType == GestureType.FlickBottom || gestureType == GestureType.FlickTop || gestureType == GestureType.FlickRight) {
                            flickListener?.onFlick(
                                gestureType = gestureType,
                                key = Key.SideKeySpace,
                                char = null
                            )
                            resetAllKeys()
                            popupWindowActive.hide()
                            return false
                        }
                    }
                    if (isCursorMode) {
                        val viewToRelease: View? = when (pressedKey.key) {
                            Key.SideKeySpace -> binding.keySpace
                            else -> null
                        }
                        viewToRelease?.let { key ->
                            key.isPressed = false
                            flickListener?.onFlick(
                                gestureType = GestureType.Tap,
                                key = pressedKey.key,
                                char = null
                            )
                        }
                        handleCurrentInputModeSwitch(currentInputMode.value)
                        isCursorMode = false
                        return false
                    }

                    if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                        val gestureType = getGestureType(event)
                        // ← READING the state flow's current value:
                        val keyInfo = currentInputMode.value
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)

                        Log.d("TenKey: ACTION_UP in pointer", "called $keyInfo $pressedKey")

                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType, key = pressedKey.key, char = null
                            )
                            if (pressedKey.key == Key.SideKeyInputMode) {
                                handleClickInputModeSwitch()
                            }
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.tap
                                )

                                GestureType.FlickLeft -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickLeft
                                )

                                GestureType.FlickTop -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickTop
                                )

                                GestureType.FlickRight -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickRight
                                )

                                GestureType.FlickBottom -> flickListener?.onFlick(
                                    gestureType = gestureType,
                                    key = pressedKey.key,
                                    char = keyInfo.flickBottom
                                )
                            }
                        }
                    }
                    Log.d("TenKey: ACTION_UP out", "called $pressedKey")
                    resetAllKeys()
                    popupWindowActive.hide()
                    val button = getButtonFromKey(pressedKey.key)
                    button?.let {
                        if (it is AppCompatButton) {
                            if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return false
                            if (it.id == R.id.key_switch_key_mode) return false
                            // ← UPDATE: use state flow's value to set text after finger-up
                            when (currentInputMode.value) {

                                InputMode.ModeJapanese -> setJapaneseTextFor(
                                    it
                                )

                                InputMode.ModeEnglish -> it.setTenKeyTextEnglish(
                                    it.id,
                                    delta = keySizeDelta,
                                    modeTheme = themeMode,
                                    colorTextInt = customKeyTextColor
                                )

                                InputMode.ModeNumber -> it.setTenKeyTextNumber(
                                    it.id,
                                    delta = keySizeDelta,
                                    modeTheme = themeMode,
                                    colorTextInt = customKeyTextColor
                                )
                            }
                        }
                        if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                            it.setImageDrawable(
                                cachedNumberSmallDrawable
                            )
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isCursorMode) {
                        // sensitivity threshold in pixels
                        val threshold = 16f

                        // 1) get the tracked pointer index
                        val pointer = pressedKey.pointer

                        // 2) read its current raw X–Y
                        val (currentX, currentY) = getRawCoordinates(event, pointer)

                        // 3) compute delta since last origin
                        val dx = currentX - pressedKey.initialX
                        val dy = currentY - pressedKey.initialY

                        // 4) only handle if movement exceeds threshold and one axis dominates
                        if (abs(dx) > abs(dy) && abs(dx) > threshold) {
                            // horizontal move
                            if (dx < 0f) {
                                flickListener?.onFlick(GestureType.Tap, Key.SideKeyCursorLeft, null)
                            } else {
                                flickListener?.onFlick(
                                    GestureType.Tap,
                                    Key.SideKeyCursorRight,
                                    null
                                )
                            }
                            // reset origin to avoid repeated triggers
                            pressedKey = pressedKey.copy(initialX = currentX, initialY = currentY)
                        } else if (abs(dy) > abs(dx) && abs(dy) > threshold) {
                            // vertical move
                            if (dy < 0f) {
                                flickListener?.onFlick(
                                    GestureType.FlickTop,
                                    Key.SideKeyCursorLeft,
                                    null
                                )
                            } else {
                                flickListener?.onFlick(
                                    GestureType.FlickBottom,
                                    Key.SideKeyCursorRight,
                                    null
                                )
                            }
                            // reset origin
                            pressedKey = pressedKey.copy(initialX = currentX, initialY = currentY)
                        }

                        return true
                    }

                    val currentX = if (event.pointerCount > 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawX(0)
                        } else {
                            event.getX(0)
                        }
                    } else 0f
                    val currentY = if (event.pointerCount > 0) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.getRawY(0)
                        } else {
                            event.getY(0)
                        }
                    } else 0f

                    val currentKey = if (event.pointerCount > 0) {
                        pressedKeyByMotionEvent(event, 0)
                    } else {
                        Key.NotSelected
                    }

                    val (screenX, screenY) = if (event.pointerCount > 0) {
                        getRawCoordinates(event, 0)
                    } else {
                        0f to 0f
                    }

                    val density = context.resources.displayMetrics.density
                    val swipeThreshold = 500f * density
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVel = velocityTracker?.getXVelocity(0) ?: 0f
                    val yVel = velocityTracker?.getYVelocity(0) ?: 0f
                    val speed = kotlin.math.sqrt(xVel * xVel + yVel * yVel)
                    val elapsed = event.eventTime - event.downTime
                    val isFlicking = speed > swipeThreshold || elapsed < 250L

                    // Handle slide-in / slide-out state transition for SideKeyCursorRight
                    if (currentKey == Key.SideKeyCursorRight) {
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
                                    if (elapsed >= 150L) {
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
                                        Log.d("TenKeyDrag", "ACTION_MOVE: Slid onto Right Cursor and remained stationary for 150ms. Starting drag tracking.")
                                    }
                                }
                            }
                        }
                    } else {
                        touchSlideInEntryTime = 0L
                        if (isDraggingRightCursor) {
                            Log.d("TenKeyDrag", "ACTION_MOVE: Slid off Right Cursor to $currentKey. Drag cancelled.")
                            isDraggingRightCursor = false
                            isLineStartAnnounced = false
                            isLineEndAnnounced = false
                            isLineUpAnnounced = false
                            isLineDownAnnounced = false
                        }
                    }

                    // Handle slide-in / slide-out state transition for SideKeyCursorLeft
                    if (currentKey == Key.SideKeyCursorLeft) {
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
                                    if (elapsed >= 150L) {
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
                                        Log.d("TenKeyDrag", "ACTION_MOVE: Slid onto Left Cursor and remained stationary for 150ms. Starting drag tracking.")
                                    }
                                }
                            }
                        }
                    } else {
                        leftTouchSlideInEntryTime = 0L
                        if (isDraggingLeftCursor) {
                            Log.d("TenKeyDrag", "ACTION_MOVE: Slid off Left Cursor to $currentKey. Drag cancelled.")
                            isDraggingLeftCursor = false
                            isLeftLineStartAnnounced = false
                            isLeftLineEndAnnounced = false
                            isLeftLineUpAnnounced = false
                            isLeftLineDownAnnounced = false
                        }
                    }

                    // Handle slide-in / slide-out state transition for SideKeyDelete
                    if (currentKey == Key.SideKeyDelete) {
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
                                    if (elapsed >= 150L) {
                                        isDraggingDeleteKey = true
                                        deleteKeyDragStartX = screenX
                                        deleteKeyDragEndX = screenX
                                        deleteKeyDragStartY = screenY
                                        deleteKeyDragEndY = screenY
                                        deleteKeyDragTopY = screenY
                                        isDeleteLeftAnnounced = false
                                        isDeleteUpAnnounced = false
                                        deleteTouchSlideInEntryTime = 0L
                                        Log.d("TenKeyDrag", "ACTION_MOVE: Slid onto Delete key and remained stationary for 150ms. Starting drag tracking.")
                                    }
                                }
                            }
                        }
                    } else {
                        deleteTouchSlideInEntryTime = 0L
                        if (isDraggingDeleteKey) {
                            Log.d("TenKeyDrag", "ACTION_MOVE: Slid off Delete key to $currentKey. Drag cancelled.")
                            isDraggingDeleteKey = false
                            isDeleteLeftAnnounced = false
                            isDeleteUpAnnounced = false
                        }
                    }

                    // Handle slide-in / slide-out state transition for SideKeySpace
                    if (currentKey == Key.SideKeySpace) {
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
                                    if (elapsed >= 150L) {
                                        isDraggingSpaceKey = true
                                        spaceKeyDragStartX = screenX
                                        spaceKeyDragEndX = screenX
                                        spaceKeyDragStartY = screenY
                                        spaceKeyDragEndY = screenY
                                        isSpaceDownAnnounced = false
                                        isSpaceUpAnnounced = false
                                        isSpaceRightAnnounced = false
                                        spaceTouchSlideInEntryTime = 0L
                                        Log.d("TenKeyDrag", "ACTION_MOVE: Slid onto Space key and remained stationary for 150ms. Starting drag tracking.")
                                    }
                                }
                            }
                        }
                    } else {
                        spaceTouchSlideInEntryTime = 0L
                        if (isDraggingSpaceKey) {
                            Log.d("TenKeyDrag", "ACTION_MOVE: Slid off Space key to $currentKey. Drag cancelled.")
                            isDraggingSpaceKey = false
                            isSpaceDownAnnounced = false
                            isSpaceUpAnnounced = false
                            isSpaceRightAnnounced = false
                        }
                    }

                    // Handle slide-in / slide-out state transition for SideKeyReadAloud
                    if (currentKey == Key.SideKeyReadAloud) {
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
                                    if (elapsed >= 150L) {
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
                                        Log.d("TenKeyDrag", "ACTION_MOVE: Slid onto Read Aloud key and remained stationary for 150ms. Starting drag tracking.")
                                    }
                                }
                            }
                        }
                    } else {
                        readAloudTouchSlideInEntryTime = 0L
                        if (isDraggingReadAloudKey) {
                            Log.d("TenKeyDrag", "ACTION_MOVE: Slid off Read Aloud key to $currentKey. Drag cancelled.")
                            isDraggingReadAloudKey = false
                            isReadAloudLeftAnnounced = false
                            isReadAloudUpAnnounced = false
                            isReadAloudRightAnnounced = false
                        }
                    }

                    if (isDraggingRightCursor) {
                        // Update peak coordinates before any trigger
                        if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                            if (screenX > rightCursorDragStartX) {
                                rightCursorDragStartX = screenX
                            }
                            if (screenX < rightCursorDragEndX) {
                                rightCursorDragEndX = screenX
                            }
                            if (screenY > rightCursorDragEndY) {
                                rightCursorDragEndY = screenY
                            }
                            if (screenY < rightCursorDragTopY) {
                                rightCursorDragTopY = screenY
                            }
                        }

                        val dxStart = screenX - rightCursorDragStartX // negative when sliding left
                        val dxEnd = screenX - rightCursorDragEndX     // positive when sliding right
                        val dyUp = screenY - rightCursorDragEndY       // negative when sliding up
                        val dyDown = screenY - rightCursorDragTopY     // positive when sliding down
                        
                        val button = binding.keyMoveCursorRight
                        val threshold = if (button != null) {
                            val w = button.width.toFloat()
                            val h = button.height.toFloat()
                            if (w > 0f && h > 0f) kotlin.math.min(w / 6f, h / 6f) else 35f
                        } else {
                            35f
                        }
                        val cancelLeftThreshold = -threshold * 4.3f
                        val cancelRightThreshold = threshold * 4.3f
                        val cancelUpThreshold = -threshold * 4.3f
                        val cancelDownThreshold = threshold * 4.3f
                        val cancelXThreshold = threshold * 1.7f
                        val cancelYThreshold = threshold * 1.7f
                        
                        Log.d("TenKeyDrag", "ACTION_MOVE: isDraggingRightCursor=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart, dxEnd=$dxEnd, dyUp=$dyUp, dyDown=$dyDown")
                        
                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - rightCursorDragStartY) <= cancelYThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineStartAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Left threshold reached! Announcing '行頭'")
                                announceForAccessibility("行頭")
                                android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - rightCursorDragStartY) <= cancelYThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineEndAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Right threshold reached! Announcing '行末'")
                                announceForAccessibility("行末")
                                android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - rightCursorDragStartX) <= cancelXThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineUpAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Up threshold reached! Announcing '上カーソル'")
                                announceForAccessibility("上カーソル")
                                android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyDown > threshold && dyDown <= cancelDownThreshold && abs(screenX - rightCursorDragStartX) <= cancelXThreshold) {
                            if (!isLineStartAnnounced && !isLineEndAnnounced && !isLineUpAnnounced && !isLineDownAnnounced) {
                                isLineDownAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Down threshold reached! Announcing '下カーソル'")
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

                                val targetView = getButtonFromKey(Key.SideKeyCursorRight) as? View
                                targetView?.let { view ->
                                    val textStr = view.contentDescription?.toString() ?: "右移動"
                                    if (accessibilityManager.isTouchExplorationEnabled) {
                                        accessibilityManager.interrupt()
                                    }
                                    announceForAccessibility(textStr)
                                    android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } else {
                                val shouldCancel = if (isLineStartAnnounced) {
                                    (dxStart < cancelLeftThreshold) || (abs(screenY - rightCursorDragStartY) > cancelYThreshold)
                                } else if (isLineEndAnnounced) {
                                    (dxEnd > cancelRightThreshold) || (abs(screenY - rightCursorDragStartY) > cancelYThreshold)
                                } else if (isLineUpAnnounced) {
                                    (dyUp < cancelUpThreshold) || (abs(screenX - rightCursorDragStartX) > cancelXThreshold)
                                } else if (isLineDownAnnounced) {
                                    (dyDown > cancelDownThreshold) || (abs(screenX - rightCursorDragStartX) > cancelXThreshold)
                                } else {
                                    (dxStart < cancelLeftThreshold) || (dxEnd > cancelRightThreshold) || (dyUp < cancelUpThreshold) || (dyDown > cancelDownThreshold) || 
                                    (abs(screenY - rightCursorDragStartY) > cancelYThreshold && abs(screenX - rightCursorDragStartX) > cancelXThreshold)
                                }
                                if (shouldCancel && !isFlicking) {
                                    isLineStartAnnounced = false
                                    isLineEndAnnounced = false
                                    isLineUpAnnounced = false
                                    isLineDownAnnounced = false
                                    isDraggingRightCursor = false
                                }
                            }
                        }
                        return true // Consume this event to bypass popups and other move gesture handlers!
                    }

                    if (isDraggingLeftCursor) {
                        // Update peak coordinates before any trigger
                        if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                            if (screenX > leftCursorDragStartX) {
                                leftCursorDragStartX = screenX
                            }
                            if (screenX < leftCursorDragEndX) {
                                leftCursorDragEndX = screenX
                            }
                            if (screenY > leftCursorDragEndY) {
                                leftCursorDragEndY = screenY
                            }
                            if (screenY < leftCursorDragTopY) {
                                leftCursorDragTopY = screenY
                            }
                        }

                        val dxStart = screenX - leftCursorDragStartX // negative when sliding left
                        val dxEnd = screenX - leftCursorDragEndX     // positive when sliding right
                        val dyUp = screenY - leftCursorDragEndY       // negative when sliding up
                        val dyDown = screenY - leftCursorDragTopY     // positive when sliding down
                        
                        val button = binding.keySoftLeft
                        val threshold = if (button != null) {
                            val w = button.width.toFloat()
                            val h = button.height.toFloat()
                            if (w > 0f && h > 0f) kotlin.math.min(w / 6f, h / 6f) else 35f
                        } else {
                            35f
                        }
                        val cancelLeftThreshold = -threshold * 4.3f
                        val cancelRightThreshold = threshold * 4.3f
                        val cancelUpThreshold = -threshold * 4.3f
                        val cancelDownThreshold = threshold * 4.3f
                        val cancelXThreshold = threshold * 1.7f
                        val cancelYThreshold = threshold * 1.7f
                        
                        Log.d("TenKeyDrag", "ACTION_MOVE: isDraggingLeftCursor=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart, dxEnd=$dxEnd, dyUp=$dyUp, dyDown=$dyDown")
                        
                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - leftCursorDragStartY) <= cancelYThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineStartAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Left threshold reached! Announcing '行頭'")
                                announceForAccessibility("行頭")
                                android.widget.Toast.makeText(context, "行頭", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - leftCursorDragStartY) <= cancelYThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineEndAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Right threshold reached! Announcing '行末'")
                                announceForAccessibility("行末")
                                android.widget.Toast.makeText(context, "行末", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - leftCursorDragStartX) <= cancelXThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineUpAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Up threshold reached! Announcing '上カーソル'")
                                announceForAccessibility("上カーソル")
                                android.widget.Toast.makeText(context, "上カーソル", android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyDown > threshold && dyDown <= cancelDownThreshold && abs(screenX - leftCursorDragStartX) <= cancelXThreshold) {
                            if (!isLeftLineStartAnnounced && !isLeftLineEndAnnounced && !isLeftLineUpAnnounced && !isLeftLineDownAnnounced) {
                                isLeftLineDownAnnounced = true
                                Log.d("TenKeyDrag", "ACTION_MOVE: Down threshold reached! Announcing '下カーソル'")
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

                                val targetView = getButtonFromKey(Key.SideKeyCursorLeft) as? View
                                targetView?.let { view ->
                                    val textStr = view.contentDescription?.toString() ?: "左移動"
                                    if (accessibilityManager.isTouchExplorationEnabled) {
                                        accessibilityManager.interrupt()
                                    }
                                    announceForAccessibility(textStr)
                                    android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } else {
                                val shouldCancel = if (isLeftLineStartAnnounced) {
                                    (dxStart < cancelLeftThreshold) || (abs(screenY - leftCursorDragStartY) > cancelYThreshold)
                                } else if (isLeftLineEndAnnounced) {
                                    (dxEnd > cancelRightThreshold) || (abs(screenY - leftCursorDragStartY) > cancelYThreshold)
                                } else if (isLeftLineUpAnnounced) {
                                    (dyUp < cancelUpThreshold) || (abs(screenX - leftCursorDragStartX) > cancelXThreshold)
                                } else if (isLeftLineDownAnnounced) {
                                    (dyDown > cancelDownThreshold) || (abs(screenX - leftCursorDragStartX) > cancelXThreshold)
                                } else {
                                    (dxStart < cancelLeftThreshold) || (dxEnd > cancelRightThreshold) || (dyUp < cancelUpThreshold) || (dyDown > cancelDownThreshold) || 
                                    (abs(screenY - leftCursorDragStartY) > cancelYThreshold && abs(screenX - leftCursorDragStartX) > cancelXThreshold)
                                }
                                if (shouldCancel && !isFlicking) {
                                    isLeftLineStartAnnounced = false
                                    isLeftLineEndAnnounced = false
                                    isLeftLineUpAnnounced = false
                                    isLeftLineDownAnnounced = false
                                    isDraggingLeftCursor = false
                                }
                            }
                        }
                        return true // Consume this event to bypass popups and other move gesture handlers!
                    }

                    if (isDraggingDeleteKey) {
                        // Update peak coordinates before any trigger
                        if (!isDeleteLeftAnnounced) {
                            if (screenX < deleteKeyDragEndX) {
                                deleteKeyDragEndX = screenX
                            }
                        }

                        val dxStart = screenX - deleteKeyDragStartX // negative when sliding left
                        
                        val button = binding.keyDelete
                        val threshold = if (button != null) {
                            val w = button.width.toFloat()
                            val h = button.height.toFloat()
                            if (w > 0f && h > 0f) kotlin.math.min(w / 6f, h / 6f) else 35f
                        } else {
                            35f
                        }
                        val cancelLeftThreshold = -threshold * 4.3f
                        val cancelRightThreshold = threshold * 4.3f
                        val cancelYThreshold = threshold * 1.7f
                        
                        Log.d("TenKeyDrag", "ACTION_MOVE: isDraggingDeleteKey=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart")
                        
                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - deleteKeyDragStartY) <= cancelYThreshold) {
                            if (!isDeleteLeftAnnounced && !isDeleteRightAnnounced) {
                                isDeleteLeftAnnounced = true
                                val annText = if (isInputComposing) "一括削除" else "行頭まで削除"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Delete Left threshold reached! Announcing '$annText'")
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxStart > threshold && dxStart <= cancelRightThreshold && abs(screenY - deleteKeyDragStartY) <= cancelYThreshold) {
                            if (!isDeleteRightAnnounced && !isDeleteLeftAnnounced) {
                                isDeleteRightAnnounced = true
                                val annText = "行末まで削除"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Delete Right threshold reached! Announcing '$annText'")
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

                                val targetView = getButtonFromKey(Key.SideKeyDelete) as? View
                                targetView?.let { view ->
                                    val textStr = view.contentDescription?.toString() ?: "削除"
                                    if (accessibilityManager.isTouchExplorationEnabled) {
                                        accessibilityManager.interrupt()
                                    }
                                    announceForAccessibility(textStr)
                                    android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } else {
                                val shouldCancel = if (isDeleteLeftAnnounced) {
                                    (dxStart < cancelLeftThreshold) || (abs(screenY - deleteKeyDragStartY) > cancelYThreshold)
                                } else if (isDeleteRightAnnounced) {
                                    (dxStart > cancelRightThreshold) || (abs(screenY - deleteKeyDragStartY) > cancelYThreshold)
                                } else {
                                    (dxStart < cancelLeftThreshold) || (dxStart > cancelRightThreshold) || (abs(screenY - deleteKeyDragStartY) > cancelYThreshold)
                                }
                                if (shouldCancel && !isFlicking) {
                                    isDeleteLeftAnnounced = false
                                    isDeleteRightAnnounced = false
                                    isDraggingDeleteKey = false
                                }
                            }
                        }
                        return true // Consume this event to bypass popups and other move gesture handlers!
                    }

                    if (isDraggingSpaceKey) {
                        val dyStart = screenY - spaceKeyDragStartY
                        val dxStart = screenX - spaceKeyDragStartX
                        
                        val spaceButton = binding.keySpace
                        val threshold = if (spaceButton != null) {
                            val keyWidth = spaceButton.width.toFloat()
                            val keyHeight = spaceButton.height.toFloat()
                            if (keyWidth > 0f && keyHeight > 0f) {
                                kotlin.math.min(keyWidth / 6f, keyHeight / 6f)
                            } else {
                                35f
                            }
                        } else {
                            35f
                        }
                        val dragUpThreshold = -threshold
                        val dragRightThreshold = threshold
                        val cancelDownThreshold = threshold * 4.3f
                        val cancelXThreshold = threshold * 1.7f
                        
                        Log.d("TenKeyDrag", "ACTION_MOVE: isDraggingSpaceKey=true, screenX=$screenX, screenY=$screenY, dyStart=$dyStart, dxStart=$dxStart")
                        
                        if (dyStart > threshold && dyStart <= cancelDownThreshold && abs(dxStart) <= cancelXThreshold) {
                            if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                                isSpaceDownAnnounced = true
                                val annText = "予測変換"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Space Down threshold reached! Announcing '$annText'")
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (currentInputMode.value != InputMode.ModeNumber && dyStart < dragUpThreshold && dyStart >= -cancelDownThreshold && abs(dxStart) <= cancelXThreshold) {
                            if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                                isSpaceUpAnnounced = true
                                val annText = if (currentInputMode.value == InputMode.ModeEnglish) "全角英語変換" else "カタカナ変換"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Space Up threshold reached! Announcing '$annText'")
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (currentInputMode.value == InputMode.ModeJapanese && dxStart > dragRightThreshold && dxStart <= cancelDownThreshold && abs(dyStart) <= cancelXThreshold) {
                            if (!isSpaceDownAnnounced && !isSpaceUpAnnounced && !isSpaceRightAnnounced) {
                                isSpaceRightAnnounced = true
                                val annText = "半角カタカナ"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Space Right threshold reached! Announcing '$annText'")
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

                                val targetView = getButtonFromKey(Key.SideKeySpace) as? View
                                targetView?.let { view ->
                                    val textStr = view.contentDescription?.toString() ?: "スペース"
                                    if (accessibilityManager.isTouchExplorationEnabled) {
                                        accessibilityManager.interrupt()
                                    }
                                    announceForAccessibility(textStr)
                                    android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } else {
                                val shouldCancel = when {
                                    isSpaceDownAnnounced -> (dyStart > cancelDownThreshold) || (abs(dxStart) > cancelXThreshold)
                                    isSpaceUpAnnounced -> (dyStart < -cancelDownThreshold) || (abs(dxStart) > cancelXThreshold)
                                    isSpaceRightAnnounced -> (dxStart > cancelDownThreshold) || (abs(dyStart) > cancelXThreshold)
                                    else -> {
                                        (dyStart > cancelDownThreshold) || (dyStart < -cancelDownThreshold) || (dxStart > cancelDownThreshold) || (abs(dxStart) > cancelXThreshold && dyStart > threshold) || (abs(dxStart) > cancelXThreshold && dyStart < dragUpThreshold) || (abs(dyStart) > cancelXThreshold && dxStart > dragRightThreshold)
                                    }
                                }
                                if (shouldCancel && !isFlicking) {
                                    isSpaceDownAnnounced = false
                                    isSpaceUpAnnounced = false
                                    isSpaceRightAnnounced = false
                                    isDraggingSpaceKey = false
                                }
                            }
                        }
                        return true // Consume this event to bypass popups and other move gesture handlers!
                    }

                    if (isDraggingReadAloudKey) {
                        // Update peak coordinates before any trigger
                        if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                            if (screenX > readAloudKeyDragStartX) {
                                readAloudKeyDragStartX = screenX
                            }
                            if (screenX < readAloudKeyDragEndX) {
                                readAloudKeyDragEndX = screenX
                            }
                            if (screenY > readAloudKeyDragEndY) {
                                readAloudKeyDragEndY = screenY
                            }
                            if (screenY < readAloudKeyDragTopY) {
                                readAloudKeyDragTopY = screenY
                            }
                        }

                        val dxStart = screenX - readAloudKeyDragStartX // negative when sliding left
                        val dxEnd = screenX - readAloudKeyDragEndX     // positive when sliding right
                        val dyUp = screenY - readAloudKeyDragEndY       // negative when sliding up
                        
                        val button = binding.sideKeyReadAloud
                        val threshold = if (button != null) {
                            val w = button.width.toFloat()
                            val h = button.height.toFloat()
                            if (w > 0f && h > 0f) kotlin.math.min(w / 6f, h / 6f) else 35f
                        } else {
                            35f
                        }
                        val cancelLeftThreshold = -threshold * 4.3f
                        val cancelRightThreshold = threshold * 4.3f
                        val cancelUpThreshold = -threshold * 4.3f
                        val cancelXThreshold = threshold * 1.7f
                        val cancelYThreshold = threshold * 1.7f
                        
                        Log.d("TenKeyDrag", "ACTION_MOVE: isDraggingReadAloudKey=true, screenX=$screenX, screenY=$screenY, dxStart=$dxStart, dxEnd=$dxEnd, dyUp=$dyUp")
                        
                        if (dxStart < -threshold && dxStart >= cancelLeftThreshold && abs(screenY - readAloudKeyDragStartY) <= cancelYThreshold) {
                            if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                                isReadAloudLeftAnnounced = true
                                val annText = "詳細読み上げ"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Read Aloud Left threshold reached! Announcing '$annText'")
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dxEnd > threshold && dxEnd <= cancelRightThreshold && abs(screenY - readAloudKeyDragStartY) <= cancelYThreshold) {
                            if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                                isReadAloudRightAnnounced = true
                                val annText = "文末まで読み上げ"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Read Aloud Right threshold reached! Announcing '$annText'")
                                announceForAccessibility(annText)
                                android.widget.Toast.makeText(context, annText, android.widget.Toast.LENGTH_SHORT).show()
                                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                            }
                        } else if (dyUp < -threshold && dyUp >= cancelUpThreshold && abs(screenX - readAloudKeyDragStartX) <= cancelXThreshold) {
                            if (!isReadAloudLeftAnnounced && !isReadAloudUpAnnounced && !isReadAloudRightAnnounced) {
                                isReadAloudUpAnnounced = true
                                val annText = "文頭から読み上げ"
                                Log.d("TenKeyDrag", "ACTION_MOVE: Read Aloud Up threshold reached! Announcing '$annText'")
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

                                val targetView = getButtonFromKey(Key.SideKeyReadAloud) as? View
                                targetView?.let { view ->
                                    val textStr = view.contentDescription?.toString() ?: "読み上げ"
                                    if (accessibilityManager.isTouchExplorationEnabled) {
                                        accessibilityManager.interrupt()
                                    }
                                    announceForAccessibility(textStr)
                                    android.widget.Toast.makeText(context, textStr, android.widget.Toast.LENGTH_SHORT).show()
                                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            } else {
                                val shouldCancel = if (isReadAloudLeftAnnounced) {
                                    (dxStart < cancelLeftThreshold) || (abs(screenY - readAloudKeyDragStartY) > cancelYThreshold)
                                } else if (isReadAloudRightAnnounced) {
                                    (dxEnd > cancelRightThreshold) || (abs(screenY - readAloudKeyDragStartY) > cancelYThreshold)
                                } else if (isReadAloudUpAnnounced) {
                                    (dyUp < cancelUpThreshold) || (abs(screenX - readAloudKeyDragStartX) > cancelXThreshold)
                                } else {
                                    (dxStart < cancelLeftThreshold) || (dxEnd > cancelRightThreshold) || (dyUp < cancelUpThreshold) || 
                                    (abs(screenY - readAloudKeyDragStartY) > cancelYThreshold && abs(screenX - readAloudKeyDragStartX) > cancelXThreshold)
                                }
                                if (shouldCancel && !isFlicking) {
                                    isReadAloudLeftAnnounced = false
                                    isReadAloudUpAnnounced = false
                                    isReadAloudRightAnnounced = false
                                    isDraggingReadAloudKey = false
                                }
                            }
                        }
                        return true // Consume this event to bypass popups and other move gesture handlers!
                    }

                    val gestureType = if (event.pointerCount == 1) {
                        getGestureType(event, 0)
                    } else {
                        getGestureType(event, pressedKey.pointer)
                    }
                    when (gestureType) {
                        GestureType.Null -> {}
                        GestureType.Down -> {}
                        GestureType.Tap -> setTapInActionMove()
                        GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> setFlickInActionMove(
                            gestureType
                        )
                    }
                    return false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (isLongPressed) {
                        hideAllPopWindow()
                        Blur.removeBlurEffect(this)
                    }
                    popupWindowActive.hide()
                    longPressJob?.cancel()
                    isDraggingRightCursor = false
                    isLineStartAnnounced = false
                    isDraggingLeftCursor = false
                    isLeftLineStartAnnounced = false
                    isLeftLineEndAnnounced = false
                    isLeftLineUpAnnounced = false
                    isLeftLineDownAnnounced = false
                    isDraggingDeleteKey = false
                    isDeleteLeftAnnounced = false
                    isDeleteUpAnnounced = false
                    isDraggingSpaceKey = false
                    isSpaceDownAnnounced = false
                    isSpaceUpAnnounced = false
                    isSpaceRightAnnounced = false
                    isDraggingReadAloudKey = false
                    isHoverDraggingReadAloudKey = false
                    isReadAloudLeftAnnounced = false
                    isReadAloudUpAnnounced = false
                    isReadAloudRightAnnounced = false
                    isHoverDraggingCharKey = false
                    hoverCharKey = Key.NotSelected
                    if (isCursorMode) {
                        return true
                    }
                    Log.d(
                        "TenKey: ACTION_POINTER_DOWN",
                        "called $pressedKey ${binding.keySmallLetter.drawable == cachedLanguageDrawable}"
                    )
                    if (pressedKey.key == Key.SideKeySymbol ||
                        pressedKey.key == Key.SideKeyInputMode ||
                        (pressedKey.key == Key.KeyDakutenSmall && binding.keySmallLetter.drawable == cachedLanguageDrawable)
                    ) {
                        return true
                    }
                    if (event.pointerCount == 2) {
                        isLongPressed = false
                        val pointer = event.getPointerId(event.actionIndex)
                        val key = pressedKeyByMotionEvent(event, pointer)
                        val gestureType2 = getGestureType(
                            event, if (pointer == 0) 1 else 0
                        )
                        if (pressedKey.key == Key.KeyDakutenSmall && currentInputMode.value == InputMode.ModeNumber) {
                            binding.keySmallLetter.setImageDrawable(
                                cachedNumberSmallDrawable
                            )
                        }
                        val keyInfo = currentInputMode.value
                            .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)
                        if (keyInfo == KeyInfo.Null) {
                            flickListener?.onFlick(
                                gestureType = gestureType2, key = pressedKey.key, char = null
                            )
                        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                            when (gestureType2) {
                                GestureType.Null -> {}
                                GestureType.Down -> {}
                                GestureType.Tap -> {
                                    flickListener?.onFlick(
                                        gestureType = gestureType2,
                                        key = pressedKey.key,
                                        char = keyInfo.tap
                                    )
                                    val button = getButtonFromKey(pressedKey.key)
                                    button?.let {
                                        if (it is AppCompatButton) {
                                            if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return false
                                            when (currentInputMode.value) {
                                                InputMode.ModeJapanese -> setJapaneseTextFor(
                                                    it
                                                )

                                                InputMode.ModeEnglish -> it.setTenKeyTextEnglish(
                                                    it.id,
                                                    delta = keySizeDelta,
                                                    modeTheme = themeMode,
                                                    colorTextInt = customKeyTextColor
                                                )

                                                InputMode.ModeNumber -> it.setTenKeyTextNumber(
                                                    it.id,
                                                    delta = keySizeDelta,
                                                    modeTheme = themeMode,
                                                    colorTextInt = customKeyTextColor
                                                )
                                            }
                                        }
                                        if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                                            it.setImageDrawable(
                                                cachedNumberSmallDrawable
                                            )
                                        }
                                    }
                                }

                                GestureType.FlickLeft, GestureType.FlickTop, GestureType.FlickRight, GestureType.FlickBottom -> {
                                    setFlickActionPointerDown(keyInfo, gestureType2)
                                }
                            }
                        }
                        pressedKey = pressedKey.copy(
                            key = key, pointer = pointer, initialX = if (pointer == 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawX(0)
                                } else {
                                    event.getX(0)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawX(1)
                                } else {
                                    event.getX(1)
                                }
                            }, initialY = if (pointer == 0) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawY(0)
                                } else {
                                    event.getY(0)
                                }
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    event.getRawY(1)
                                } else {
                                    event.getY(1)
                                }
                            }
                        )
                        setKeyPressed()
                        longPressJob = CoroutineScope(Dispatchers.Main).launch {
                            delay(ViewConfiguration.getLongPressTimeout().toLong())
                            if (pressedKey.key != Key.NotSelected) {
                                longPressListener?.onLongPress(pressedKey.key)
                                isLongPressed = true
                                onLongPressed()
                            }
                        }
                    }
                    return false
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (event.pointerCount == 2) {
                        if (pressedKey.pointer == event.getPointerId(event.actionIndex)) {
                            resetLongPressAction()
                            if (isCursorMode) return true
                            val gestureType = getGestureType(
                                event, event.getPointerId(event.actionIndex)
                            )
                            val keyInfo = currentInputMode.value
                                .next(keyMap = keyMap, key = pressedKey.key, isTablet = false)

                            Log.d("TenKey: ACTION_POINTER_UP", "called [${pressedKey.key}]")
                            if (keyInfo == KeyInfo.Null) {
                                flickListener?.onFlick(
                                    gestureType = gestureType, key = pressedKey.key, char = null
                                )
                                if (pressedKey.key == Key.SideKeyInputMode) {
                                    handleClickInputModeSwitch()
                                }
                            } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                                when (gestureType) {
                                    GestureType.Null -> {}
                                    GestureType.Down -> {}
                                    GestureType.Tap -> {
                                        flickListener?.onFlick(
                                            gestureType = gestureType,
                                            key = pressedKey.key,
                                            char = keyInfo.tap
                                        )
                                    }

                                    GestureType.FlickLeft -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickLeft
                                    )

                                    GestureType.FlickTop -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickTop
                                    )

                                    GestureType.FlickRight -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickRight
                                    )

                                    GestureType.FlickBottom -> flickListener?.onFlick(
                                        gestureType = gestureType,
                                        key = pressedKey.key,
                                        char = keyInfo.flickBottom
                                    )
                                }
                            }
                            val button = getButtonFromKey(pressedKey.key)
                            button?.let {
                                if (it is AppCompatButton) {
                                    if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return false
                                    it.isPressed = false
                                    when (currentInputMode.value) {
                                        InputMode.ModeJapanese -> setJapaneseTextFor(
                                            it
                                        )

                                        InputMode.ModeEnglish -> it.setTenKeyTextEnglish(
                                            it.id,
                                            delta = keySizeDelta,
                                            modeTheme = themeMode,
                                            colorTextInt = customKeyTextColor
                                        )

                                        InputMode.ModeNumber -> it.setTenKeyTextNumber(
                                            it.id,
                                            delta = keySizeDelta,
                                            modeTheme = themeMode,
                                            colorTextInt = customKeyTextColor
                                        )
                                    }
                                }
                            }
                            pressedKey = pressedKey.copy(key = Key.NotSelected)
                            popupWindowActive.hide()
                        }
                        return false
                    }
                    return false
                }

                else -> return false
            }
            } finally {
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
            }
        }
        return false
    }

    /** Handle orientation changes by re‐applying text on all keys **/
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        newConfig?.apply {
            if (orientation == Configuration.ORIENTATION_PORTRAIT || orientation == Configuration.ORIENTATION_LANDSCAPE) {
                setTextToAllButtons()
            }
        }
    }

    fun setFlickSensitivityValue(sensitivity: Int) {
        flickSensitivity = sensitivity
    }

    fun setFlickVelocityFilter(enabled: Boolean) {
        isVelocityFilterEnabled = enabled
    }

    private fun getKeyCenter(key: Key, useRaw: Boolean): Pair<Float, Float>? {
        val button = getButtonFromKey(key) as? View ?: return null
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

    private fun setTextToAllButtons() {
        setJapaneseTextFor(binding.key1)
        setJapaneseTextFor(binding.key2)
        setJapaneseTextFor(binding.key3)
        setJapaneseTextFor(binding.key4)
        setJapaneseTextFor(binding.key5)
        setJapaneseTextFor(binding.key6)
        setJapaneseTextFor(binding.key7)
        setJapaneseTextFor(binding.key8)
        setJapaneseTextFor(binding.key9)
        setJapaneseTextFor(binding.key11)
    }

    /** Determine which Key enum corresponds to the touch coordinates **/
    private fun pressedKeyByMotionEvent(event: MotionEvent, pointer: Int): Key {
        val (x, y) = getRawCoordinates(event, pointer)

        val keyRects = listOf(
            KeyRect(
                Key.SideKeyReadAloud,
                binding.sideKeyReadAloud.layoutXPosition(),
                binding.sideKeyReadAloud.layoutYPosition(),
                binding.sideKeyReadAloud.layoutXPosition() + binding.sideKeyReadAloud.width,
                binding.sideKeyReadAloud.layoutYPosition() + binding.sideKeyReadAloud.height
            ), KeyRect(
                Key.KeyA,
                binding.key1.layoutXPosition(),
                binding.key1.layoutYPosition(),
                binding.key1.layoutXPosition() + binding.key1.width,
                binding.key1.layoutYPosition() + binding.key1.height
            ), KeyRect(
                Key.KeyKA,
                binding.key2.layoutXPosition(),
                binding.key2.layoutYPosition(),
                binding.key2.layoutXPosition() + binding.key2.width,
                binding.key2.layoutYPosition() + binding.key2.height
            ), KeyRect(
                Key.KeySA,
                binding.key3.layoutXPosition(),
                binding.key3.layoutYPosition(),
                binding.key3.layoutXPosition() + binding.key3.width,
                binding.key3.layoutYPosition() + binding.key3.height
            ), KeyRect(
                Key.SideKeyDelete,
                binding.keyDelete.layoutXPosition(),
                binding.keyDelete.layoutYPosition(),
                binding.keyDelete.layoutXPosition() + binding.keyDelete.width,
                binding.keyDelete.layoutYPosition() + binding.keyDelete.height
            ), KeyRect(
                Key.SideKeyCursorLeft,
                binding.keySoftLeft.layoutXPosition(),
                binding.keySoftLeft.layoutYPosition(),
                binding.keySoftLeft.layoutXPosition() + binding.keySoftLeft.width,
                binding.keySoftLeft.layoutYPosition() + binding.keySoftLeft.height
            ), KeyRect(
                Key.KeyTA,
                binding.key4.layoutXPosition(),
                binding.key4.layoutYPosition(),
                binding.key4.layoutXPosition() + binding.key4.width,
                binding.key4.layoutYPosition() + binding.key4.height
            ), KeyRect(
                Key.KeyNA,
                binding.key5.layoutXPosition(),
                binding.key5.layoutYPosition(),
                binding.key5.layoutXPosition() + binding.key5.width,
                binding.key5.layoutYPosition() + binding.key5.height
            ), KeyRect(
                Key.KeyHA,
                binding.key6.layoutXPosition(),
                binding.key6.layoutYPosition(),
                binding.key6.layoutXPosition() + binding.key6.width,
                binding.key6.layoutYPosition() + binding.key6.height
            ), KeyRect(
                Key.SideKeyCursorRight,
                binding.keyMoveCursorRight.layoutXPosition(),
                binding.keyMoveCursorRight.layoutYPosition(),
                binding.keyMoveCursorRight.layoutXPosition() + binding.keyMoveCursorRight.width,
                binding.keyMoveCursorRight.layoutYPosition() + binding.keyMoveCursorRight.height
            ), KeyRect(
                Key.SideKeySymbol,
                binding.sideKeySymbol.layoutXPosition(),
                binding.sideKeySymbol.layoutYPosition(),
                binding.sideKeySymbol.layoutXPosition() + binding.sideKeySymbol.width,
                binding.sideKeySymbol.layoutYPosition() + binding.sideKeySymbol.height
            ), KeyRect(
                Key.KeyMA,
                binding.key7.layoutXPosition(),
                binding.key7.layoutYPosition(),
                binding.key7.layoutXPosition() + binding.key7.width,
                binding.key7.layoutYPosition() + binding.key7.height
            ), KeyRect(
                Key.KeyYA,
                binding.key8.layoutXPosition(),
                binding.key8.layoutYPosition(),
                binding.key8.layoutXPosition() + binding.key8.width,
                binding.key8.layoutYPosition() + binding.key8.height
            ), KeyRect(
                Key.KeyRA,
                binding.key9.layoutXPosition(),
                binding.key9.layoutYPosition(),
                binding.key9.layoutXPosition() + binding.key9.width,
                binding.key9.layoutYPosition() + binding.key9.height
            ), KeyRect(
                Key.SideKeySpace,
                binding.keySpace.layoutXPosition(),
                binding.keySpace.layoutYPosition(),
                binding.keySpace.layoutXPosition() + binding.keySpace.width,
                binding.keySpace.layoutYPosition() + binding.keySpace.height
            ), KeyRect(
                Key.SideKeyInputMode,
                binding.keySwitchKeyMode.layoutXPosition(),
                binding.keySwitchKeyMode.layoutYPosition(),
                binding.keySwitchKeyMode.layoutXPosition() + binding.keySwitchKeyMode.width,
                binding.keySwitchKeyMode.layoutYPosition() + binding.keySwitchKeyMode.height
            ), KeyRect(
                Key.KeyDakutenSmall,
                binding.keySmallLetter.layoutXPosition(),
                binding.keySmallLetter.layoutYPosition(),
                binding.keySmallLetter.layoutXPosition() + binding.keySmallLetter.width,
                binding.keySmallLetter.layoutYPosition() + binding.keySmallLetter.height
            ), KeyRect(
                Key.KeyWA,
                binding.key11.layoutXPosition(),
                binding.key11.layoutYPosition(),
                binding.key11.layoutXPosition() + binding.key11.width,
                binding.key11.layoutYPosition() + binding.key11.height
            ), KeyRect(
                Key.KeyKutouten,
                binding.key12.layoutXPosition(),
                binding.key12.layoutYPosition(),
                binding.key12.layoutXPosition() + binding.key12.width,
                binding.key12.layoutYPosition() + binding.key12.height
            ), KeyRect(
                Key.SideKeyEnter,
                binding.keyEnter.layoutXPosition(),
                binding.keyEnter.layoutYPosition(),
                binding.keyEnter.layoutXPosition() + binding.keyEnter.width,
                binding.keyEnter.layoutYPosition() + binding.keyEnter.height
            )
        )

        // If the touch falls inside any key's rectangle, return that enum
        keyRects.forEach { rect ->
            if (x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom) {
                return rect.key
            }
        }

        // Otherwise return the nearest key by Euclidean distance
        val nearest = keyRects.minByOrNull { rect ->
            val centerX = (rect.left + rect.right) / 2
            val centerY = (rect.top + rect.bottom) / 2
            val dx = x - centerX
            val dy = y - centerY
            dx * dx + dy * dy
        }
        return nearest?.key ?: Key.NotSelected
    }

    /**
     * フリックガイド表示のオン/オフを切り替える
     */
    fun setFlickGuideEnabled(enabled: Boolean) {
        isFlickGuideEnabled = enabled
        // 現在のモードに合わせてキー表示を再描画
        handleCurrentInputModeSwitch(currentInputMode.value)
    }

    private fun setJapaneseTextFor(button: AppCompatButton) {
        if (isFlickGuideEnabled) {
            button.setTenKeyTextJapaneseWithFlickGuide(
                button.id,
                delta = keySizeDelta,
                modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
        } else {
            button.setTenKeyTextJapanese(
                button.id,
                delta = keySizeDelta,
                modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
        }
        // Set proper contentDescription for key12 to align with TalkBack behavior
        if (button.id == R.id.key_12) {
            button.contentDescription = "読点"
        }
    }

    private fun isNearCenter(key: Key, x: Float, y: Float): Boolean {
        if (cachedKeyRects == null) {
            refreshKeyRects()
        }
        val rect = cachedKeyRects?.find { it.key == key } ?: return true
        val kw = rect.right - rect.left
        val kh = rect.bottom - rect.top
        val cx = rect.left + kw / 2f
        val cy = rect.top + kh / 2f
        
        // Define center region: within 30% of width/height from the center
        return abs(x - cx) < kw * 0.30f && abs(y - cy) < kh * 0.30f
    }

    /** Get absolute coordinates for the given pointer **/
    private fun getRawCoordinates(event: MotionEvent, pointer: Int): Pair<Float, Float> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer) to event.getRawY(pointer)
        } else {
            val location = IntArray(2)
            this.getLocationOnScreen(location)
            (event.getX(pointer) + location[0]) to (event.getY(pointer) + location[1])
        }
    }

    /** Determine whether the movement is a tap or a flick in a direction **/
    private fun getGestureType(event: MotionEvent, pointer: Int = 0): GestureType {
        val finalX = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawX(pointer)
        } else {
            event.getX(pointer)
        }
        val finalY = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            event.getRawY(pointer)
        } else {
            event.getY(pointer)
        }
        val useRaw = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        // 1. Dual-origin logic: compute delta from both touch start and key center,
        // and select the one with the larger absolute value.
        val dX1 = finalX - pressedKey.initialX
        val dY1 = finalY - pressedKey.initialY

        val keyCenter = getKeyCenter(pressedKey.key, useRaw)
        val dX2 = if (keyCenter != null) finalX - keyCenter.first else dX1
        val dY2 = if (keyCenter != null) finalY - keyCenter.second else dY1

        val distanceX = if (abs(dX1) > abs(dX2)) dX1 else dX2
        val distanceY = if (abs(dY1) > abs(dY2)) dY1 else dY2

        val absX = abs(distanceX)
        val absY = abs(distanceY)

        // 2. Velocity-based filtering (applied if filter is enabled AND long press has NOT triggered yet)
        var isFastX = true
        var isFastY = true
        if (isVelocityFilterEnabled && !isLongPressed) {
            velocityTracker?.computeCurrentVelocity(1000)
            val xVel = velocityTracker?.getXVelocity(pointer) ?: 0f
            val yVel = velocityTracker?.getYVelocity(pointer) ?: 0f
            val density = context.resources.displayMetrics.density
            val swipeThreshold = 500f * density
            isFastX = abs(xVel) > swipeThreshold
            isFastY = abs(yVel) > swipeThreshold
        }

        val button = getButtonFromKey(pressedKey.key) as? View
        val threshold = if (button != null) {
            val keyWidth = button.width.toFloat()
            val keyHeight = button.height.toFloat()
            if (keyWidth > 0f && keyHeight > 0f) {
                kotlin.math.min(keyWidth / 6f, keyHeight / 6f)
            } else {
                flickSensitivity.toFloat()
            }
        } else {
            flickSensitivity.toFloat()
        }

        return when {
            absX < threshold && absY < threshold -> GestureType.Tap
            absX > absY && distanceX <= 0f && isFastX -> GestureType.FlickLeft
            absX <= absY && distanceY <= 0f && isFastY -> GestureType.FlickTop
            absX > absY && distanceX > 0f && isFastX -> GestureType.FlickRight
            // 3. Stricter downward flick angle limit: absX < absY / 2
            absX < absY / 2f && distanceY > 0f && isFastY -> GestureType.FlickBottom
            else -> GestureType.Null
        }
    }

    /** Visually indicate which key is pressed **/
    private fun setKeyPressed() {
        listKeys.forEach { (keyEnum, viewObj) ->
            when (viewObj) {
                is InputModeSwitch -> viewObj.isPressed = (keyEnum == pressedKey.key)
                is AppCompatButton -> viewObj.isPressed = (keyEnum == pressedKey.key)
                is AppCompatImageButton -> viewObj.isPressed = (keyEnum == pressedKey.key)
            }
        }
    }

    /** Cancel ongoing long‐press visuals and job **/
    private fun resetLongPressAction() {
        if (isLongPressed) {
            hideAllPopWindow()
            Blur.removeBlurEffect(this)
        }
        longPressJob?.cancel()
        isLongPressed = false
    }

    /** Un–highlight all keys **/
    private fun resetAllKeys() {
        listKeys.values.forEach { viewObj ->
            when (viewObj) {
                is InputModeSwitch -> viewObj.isPressed = false
                is AppCompatButton -> viewObj.isPressed = false
                is AppCompatImageButton -> viewObj.isPressed = false
            }
        }
    }

    /** Return the underlying view object (Button/ImageButton/Switch) for a given Key **/
    private fun getButtonFromKey(key: Key): Any? {
        return listKeys[key]
    }

    /** Called when a long‐press is detected; show all related popups **/
    private fun onLongPressed() {
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return

                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        popTextTop.setTextFlickTopJapanese(it.id)
                        popTextLeft.setTextFlickLeftJapanese(it.id)
                        popTextBottom.setTextFlickBottomJapanese(it.id)
                        popTextRight.setTextFlickRightJapanese(it.id)
                        popTextActive.setTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        popTextTop.setTextFlickTopEnglish(it.id)
                        popTextLeft.setTextFlickLeftEnglish(it.id)
                        popTextBottom.setTextFlickBottomEnglish(it.id)
                        popTextRight.setTextFlickRightEnglish(it.id)
                        popTextActive.setTextTapEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        popTextTop.setTextFlickTopNumber(it.id)
                        popTextLeft.setTextFlickLeftNumber(it.id)
                        popTextBottom.setTextFlickBottomNumber(it.id)
                        popTextRight.setTextFlickRightNumber(it.id)
                        popTextActive.setTextTapNumber(it.id)
                    }
                }
                popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it)
                popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it)
                if (popTextBottom.text.isNotEmpty()) {
                    popupWindowBottom.setPopUpWindowBottom(context, bubbleViewBottom, it)
                }
                if (popTextRight.text.isNotEmpty()) {
                    popupWindowRight.setPopUpWindowRight(context, bubbleViewRight, it)
                }
                popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                Blur.applyBlurEffect(this, 8f)
            }

            if (it is AppCompatImageButton) {
                if (currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                    popTextTop.setTextFlickTopNumber(it.id)
                    popTextLeft.setTextFlickLeftNumber(it.id)
                    popTextBottom.setTextFlickBottomNumber(it.id)
                    popTextRight.setTextFlickRightNumber(it.id)
                    popupWindowTop.setPopUpWindowTop(context, bubbleViewTop, it)
                    popupWindowLeft.setPopUpWindowLeft(context, bubbleViewLeft, it)
                    popupWindowBottom.setPopUpWindowBottom(context, bubbleViewBottom, it)
                    popupWindowRight.setPopUpWindowRight(context, bubbleViewRight, it)
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                    Blur.applyBlurEffect(this, 8f)
                }
            }
        }
    }

    /** Hide every popup bubble **/
    private fun hideAllPopWindow() {
        popupWindowActive.hide()
        popupWindowLeft.hide()
        popupWindowTop.hide()
        popupWindowRight.hide()
        popupWindowBottom.hide()
        popupWindowCenter.hide()
    }

    /** Called during a “tap” gesture in an ongoing move event **/
    private fun setTapInActionMove() {
        if (!isLongPressed) popupWindowActive.hide()
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return
                it.isPressed = true
                when (currentInputMode.value) {
                    InputMode.ModeJapanese -> {
                        it.setTenKeyTextWhenTapJapanese(it.id)
                        if (isLongPressed) popTextActive.setTextTapJapanese(it.id)
                    }

                    InputMode.ModeEnglish -> {
                        it.setTenKeyTextWhenTapEnglish(it.id)
                        if (isLongPressed) popTextActive.setTextTapEnglish(it.id)
                    }

                    InputMode.ModeNumber -> {
                        it.setTenKeyTextWhenTapNumber(it.id)
                        if (isLongPressed) popTextActive.setTextTapNumber(it.id)
                    }
                }

                if (isLongPressed) {
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                }
            }
            if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                it.isPressed = true
                it.setImageDrawable(
                    cachedOpenBracketDrawable
                )
                if (isLongPressed) popTextActive.setTextTapNumber(it.id)
                if (isLongPressed) {
                    popupWindowActive.setPopUpWindowCenter(context, bubbleViewActive, it)
                }
            }
        }
    }

    /** Called during a “flick” gesture in an ongoing move event **/
    private fun setFlickInActionMove(gestureType: GestureType) {
        longPressJob?.cancel()
        val button = getButtonFromKey(pressedKey.key)
        button?.let {
            if (it is AppCompatButton) {
                if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return
                it.isPressed = true
                if (!isLongPressed) it.text = ""
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickLeftJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickLeftEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickLeftNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowLeft(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickTop -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickTopJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickTopEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickTopNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickRight -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickRightJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickRightEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickRightNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowRight(context, bubbleViewActive, it)
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickRight(
                                    context, bubbleViewActive, it
                                )
                            }
                        }
                    }

                    GestureType.FlickBottom -> {
                        when (currentInputMode.value) {
                            InputMode.ModeJapanese -> {
                                popTextActive.setTextFlickBottomJapanese(it.id)
                                if (isLongPressed) popTextCenter.setTextTapJapanese(it.id)
                            }

                            InputMode.ModeEnglish -> {
                                popTextActive.setTextFlickBottomEnglish(it.id)
                                if (isLongPressed) popTextCenter.setTextTapEnglish(it.id)
                            }

                            InputMode.ModeNumber -> {
                                popTextActive.setTextFlickBottomNumber(it.id)
                                if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                            }
                        }
                        if (isLongPressed) {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowBottom(
                                    context, bubbleViewActive, it
                                )
                                popupWindowCenter.setPopUpWindowCenter(
                                    context, bubbleViewCenter, it
                                )
                            }
                        } else {
                            if (popTextActive.text.isNotEmpty()) {
                                popupWindowActive.setPopUpWindowFlickBottom(
                                    context, bubbleViewActive, it
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
            if (it is AppCompatImageButton && currentInputMode.value == InputMode.ModeNumber && it == binding.keySmallLetter) {
                it.isPressed = true
                if (!isLongPressed) it.setImageDrawable(null)
                when (gestureType) {
                    GestureType.FlickLeft -> {
                        popTextActive.setTextFlickLeftNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowLeft(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickLeft(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickTop -> {
                        popTextActive.setTextFlickTopNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowTop(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickTop(context, bubbleViewActive, it)
                        }
                    }

                    GestureType.FlickRight -> {
                        popTextActive.setTextFlickRightNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowRight(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickRight(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    GestureType.FlickBottom -> {
                        popTextActive.setTextFlickBottomNumber(it.id)
                        if (isLongPressed) popTextCenter.setTextTapNumber(it.id)
                        if (isLongPressed) {
                            popupWindowCenter.setPopUpWindowCenter(context, bubbleViewCenter, it)
                            popupWindowActive.setPopUpWindowBottom(context, bubbleViewActive, it)
                        } else {
                            popupWindowActive.setPopUpWindowFlickBottom(
                                context, bubbleViewActive, it
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    /** Handle flick action when second finger goes down **/
    private fun setFlickActionPointerDown(keyInfo: KeyInfo, gestureType: GestureType) {
        if (keyInfo is KeyInfo.KeyTapFlickInfo) {
            val charToSend = when (gestureType) {
                GestureType.Tap -> keyInfo.tap
                GestureType.FlickLeft -> keyInfo.flickLeft
                GestureType.FlickTop -> keyInfo.flickTop
                GestureType.FlickRight -> keyInfo.flickRight
                GestureType.FlickBottom -> keyInfo.flickBottom
                else -> null
            }
            flickListener?.onFlick(
                gestureType = gestureType, key = pressedKey.key, char = charToSend
            )
            val button = getButtonFromKey(pressedKey.key)
            button?.let {
                if (it is AppCompatButton) {
                    if (it == binding.sideKeySymbol || it == binding.sideKeyReadAloud) return
                    when (currentInputMode.value) {
                        InputMode.ModeJapanese -> setJapaneseTextFor(
                            it
                        )

                        InputMode.ModeEnglish -> it.setTenKeyTextEnglish(
                            it.id,
                            delta = keySizeDelta,
                            modeTheme = themeMode,
                            colorTextInt = customKeyTextColor
                        )

                        InputMode.ModeNumber -> it.setTenKeyTextNumber(
                            it.id,
                            delta = keySizeDelta,
                            modeTheme = themeMode,
                            colorTextInt = customKeyTextColor
                        )
                    }
                }
            }
        }
    }

    /** Set default drawable for the small/dakuten key **/
    fun setBackgroundSmallLetterKey(
        drawable: Drawable? = cachedLanguageDrawable
    ) {
        binding.keySmallLetter.setImageDrawable(drawable)
    }

    /** Set default drawable for the small/dakuten key **/
    fun setBackgroundSmallLetterKey(
        isLanguageEnable: Boolean,
        isEnglish: Boolean
    ) {
        if (isLanguageEnable) {
            binding.keySmallLetter.setImageDrawable(cachedLanguageDrawable)
        } else {
            if (isEnglish) {
                binding.keySmallLetter.setImageDrawable(cachedEnglishDrawable)
            } else {
                binding.keySmallLetter.setImageDrawable(cachedKanaDrawable)
            }
        }
    }

    /** Set custom drawable on the Enter key **/
    fun setSideKeyEnterDrawable(drawable: Drawable?) {
        binding.keyEnter.setImageDrawable(drawable)
    }

    /** Retrieve current Enter key drawable **/
    fun getCurrentEnterKeyDrawable(): Drawable? {
        return binding.keyEnter.drawable
    }

    /** Set custom drawable on the Space key **/
    fun setSideKeySpaceDrawable(drawable: Drawable?) {
        binding.keySpace.setImageDrawable(drawable)
    }

    /** Enable/disable the “previous character” key **/
    fun setSideKeyPreviousState(state: Boolean) {
        binding.sideKeyReadAloud.isEnabled = state
    }

    /** Enable/disable the “previous character” key **/
    fun setSideKeyPreviousDrawable(drawable: Drawable?) {
        binding.sideKeyReadAloud.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
    }

    /** Cycle through input modes when the switch key is clicked **/
    private fun handleClickInputModeSwitch() {
        // ← READ from StateFlow.value:
        val newInputMode = when (currentInputMode.value) {
            InputMode.ModeJapanese -> InputMode.ModeEnglish
            InputMode.ModeEnglish -> InputMode.ModeNumber
            InputMode.ModeNumber -> InputMode.ModeJapanese
        }
        // ← WRITE to MutableStateFlow:
        _currentInputMode.update { newInputMode }
        // We don’t need to manually call setKeysInXXX or setInputMode(...) here,
        // because our collector in init { … } already calls `handleCurrentInputModeSwitch(...)`
        // and `binding.keySwitchKeyMode.setInputMode(...)`.
    }

    /** Sync UI to a specified input mode (called from collector) **/
    private fun handleCurrentInputModeSwitch(inputMode: InputMode) {
        Log.d("TenKeyAccessibility", "handleCurrentInputModeSwitch: inputMode=$inputMode")
        when (inputMode) {
            InputMode.ModeJapanese -> {
                setKeysInJapaneseText()
                binding.key12.contentDescription = "読点"
                binding.keySmallLetter.contentDescription = context.getString(com.kazumaproject.core.R.string.small_key)
                announceForAccessibility(context.getString(com.kazumaproject.core.R.string.tenkey_hiragana))
            }
            InputMode.ModeEnglish -> {
                setKeysInEnglishText()
                binding.key12.contentDescription = null
                binding.keySmallLetter.contentDescription = context.getString(com.kazumaproject.core.R.string.small_key)
                announceForAccessibility(context.getString(com.kazumaproject.core.R.string.tenkey_alphabet))
            }
            InputMode.ModeNumber -> {
                setKeysInNumberText()
                binding.key12.contentDescription = null
                binding.keySmallLetter.contentDescription = "かっこ とじかっこ かくかっこ とじかくかっこ"
                announceForAccessibility(context.getString(com.kazumaproject.core.R.string.tenkey_number))
            }
        }
    }

    private fun setKeysCursorMoveMode() {
        binding.apply {
            key1.text = ""
            key2.text = ""
            key3.text = ""
            key4.text = ""
            key5.text = ""
            key6.text = ""
            key7.text = ""
            key8.text = ""
            key9.text = ""
            key11.text = ""
            key12.text = ""
            keySmallLetter.setImageDrawable(null)
            sideKeyReadAloud.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            sideKeySymbol.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            keySpace.setImageDrawable(null)
            keyMoveCursorRight.setImageDrawable(null)
            keySoftLeft.setImageDrawable(null)
            keyDelete.setImageDrawable(null)
        }
    }

    /** Populate all main keys with Japanese labels **/
    private fun setKeysTextsInSelectMode() {
        val copyIcon = cachedContentCopyDrawable
        copyIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val cutIcon = cachedContentCutDrawable

        cutIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val shareIcon = cachedContentShareDrawable
        shareIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        val selectAllIcon = cachedContentSelectDrawable
        selectAllIcon?.apply {
            setBounds(
                0,
                0,
                intrinsicWidth,
                intrinsicHeight
            )
        }

        binding.apply {
            key1.apply {
                text = "コピー"
                textSize = 12f
                setCompoundDrawables(copyIcon, null, null, null)
            }
            key2.text = ""
            key3.apply {
                text = "切り取り"
                textSize = 12f
                setCompoundDrawables(cutIcon, null, null, null)
            }
            key4.text = ""
            key5.text = ""
            key6.text = ""
            key7.apply {
                text = "全て選択"
                textSize = 12f
                setCompoundDrawables(selectAllIcon, null, null, null)
            }
            key8.text = ""
            key9.apply {
                text = "共有"
                textSize = 12f
                setCompoundDrawables(shareIcon, null, null, null)
            }

            keyEnter.visibility = View.INVISIBLE
            keySwitchKeyMode.visibility = View.INVISIBLE
            key11.visibility = View.INVISIBLE
            key12.visibility = View.INVISIBLE
            keySmallLetter.visibility = View.INVISIBLE

            sideKeyReadAloud.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            sideKeySymbol.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
            keySpace.setImageDrawable(
                cachedUndoDrawable
            )
            keyMoveCursorRight.setImageDrawable(
                cachedArrowRightDrawable
            )
            keySoftLeft.setImageDrawable(
                cachedArrowLeftDrawable
            )
            keyDelete.setImageDrawable(
                cachedBackSpaceDrawable
            )
        }
    }

    /** Populate all main keys with Japanese labels **/
    private fun setKeysInJapaneseText() {
        binding.apply {
            key1.apply {
                setJapaneseTextFor(key1)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key2)
            key3.apply {
                setJapaneseTextFor(key3)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key4)
            setJapaneseTextFor(key5)
            setJapaneseTextFor(key6)
            key7.apply {
                setJapaneseTextFor(key7)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key8)
            key9.apply {
                setJapaneseTextFor(key9)
                setCompoundDrawables(null, null, null, null)
            }
            setJapaneseTextFor(key11)
            key12.apply {
                setJapaneseTextFor(key12)
                setCompoundDrawables(null, null, null, null)
            }
            if (isLanguageIconEnabled) {
                keySmallLetter.setImageDrawable(cachedLanguageDrawable)
            } else {
                keySmallLetter.setImageDrawable(cachedKanaDrawable)
            }
            resetFromSelectMode(binding)
            keyMoveCursorRight.setImageDrawable(
                cachedArrowRightDrawable
            )
            keySoftLeft.setImageDrawable(
                cachedArrowLeftDrawable
            )
            keyDelete.setImageDrawable(cachedBackSpaceDrawable)
        }
    }

    /** Populate all main keys with English labels **/
    private fun setKeysInEnglishText() {
        binding.apply {
            key1.apply {
                setTenKeyTextEnglish(
                    key1.id,
                    delta = keySizeDelta,
                    modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key2.apply {
                setTenKeyTextEnglish(
                    key2.id,
                    delta = keySizeDelta,
                    modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key3.setTenKeyTextEnglish(
                key3.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key4.setTenKeyTextEnglish(
                key4.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key5.setTenKeyTextEnglish(
                key5.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key6.setTenKeyTextEnglish(
                key6.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key7.apply {
                setTenKeyTextEnglish(
                    key7.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextEnglish(
                key8.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key9.apply {
                setTenKeyTextEnglish(
                    key9.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextEnglish(
                key11.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key12.apply {
                setTenKeyTextEnglish(
                    key12.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            resetFromSelectMode(binding)
            keyMoveCursorRight.setImageDrawable(
                cachedArrowRightDrawable
            )
            keySoftLeft.setImageDrawable(
                cachedArrowLeftDrawable
            )
            if (isLanguageIconEnabled) {
                keySmallLetter.setImageDrawable(cachedLanguageDrawable)
            } else {
                keySmallLetter.setImageDrawable(cachedEnglishDrawable)
            }
            keyDelete.setImageDrawable(cachedBackSpaceDrawable)
        }
    }

    /** Populate all main keys with Number labels **/
    private fun setKeysInNumberText() {
        binding.apply {
            key1.apply {
                setTenKeyTextNumber(
                    key1.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key2.setTenKeyTextNumber(
                key2.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key3.apply {
                setTenKeyTextNumber(
                    key3.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key4.setTenKeyTextNumber(
                key4.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key5.setTenKeyTextNumber(
                key5.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key6.setTenKeyTextNumber(
                key6.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key7.apply {
                setTenKeyTextNumber(
                    key7.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key8.setTenKeyTextNumber(
                key8.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key9.apply {
                setTenKeyTextNumber(
                    key9.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }
            key11.setTenKeyTextNumber(
                key11.id, delta = keySizeDelta, modeTheme = themeMode,
                colorTextInt = customKeyTextColor
            )
            key12.apply {
                setTenKeyTextNumber(
                    key12.id, delta = keySizeDelta, modeTheme = themeMode,
                    colorTextInt = customKeyTextColor
                )
                setCompoundDrawables(null, null, null, null)
            }

            resetFromSelectMode(binding)
            keyMoveCursorRight.setImageDrawable(
                cachedArrowRightDrawable
            )
            keySoftLeft.setImageDrawable(
                cachedArrowLeftDrawable
            )
            keySmallLetter.setImageDrawable(cachedNumberSmallDrawable)
            keySmallLetter.contentDescription = "かっこ とじかっこ かくかっこ とじかくかっこ"
            keyDelete.setImageDrawable(cachedBackSpaceDrawable)
        }
    }

    private fun resetFromSelectMode(binding: KeyboardLayoutBinding) {
        binding.apply {
            sideKeyReadAloud.apply {
                visibility = View.VISIBLE
                setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    cachedUndoDrawable,
                    null,
                    null
                )
            }
            sideKeySymbol.apply {
                visibility = View.VISIBLE
                updateSideKeySymbolLabel()
            }
            keySpace.apply {
                visibility = View.VISIBLE
                setImageDrawable(
                    cachedSpaceDrawable
                )
            }
            keyEnter.visibility = View.VISIBLE
            keySwitchKeyMode.visibility = View.VISIBLE
            key11.visibility = View.VISIBLE
            key12.visibility = View.VISIBLE
            keySmallLetter.visibility = View.VISIBLE
        }
    }

    /** Mark all key Views as non‐focusable so touches go directly to onTouch **/
    private fun setupAccessibility() {
        listKeys.forEach { (key, view) ->
            if (view is View) {
                view.isClickable = true
                view.isFocusable = true
                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                view.setOnClickListener {
                    if (isAyameMode) {
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            performKeyInput(key)
                        } else {
                            val currentTime = android.os.SystemClock.uptimeMillis()
                            if (key == lastClickedKey && currentTime - lastClickedTime < 500) {
                                performKeyInput(key)
                                lastClickedKey = null
                                lastClickedTime = 0L
                            } else {
                                lastClickedKey = key
                                lastClickedTime = currentTime
                            }
                        }
                    } else {
                        if (accessibilityManager.isTouchExplorationEnabled) {
                            performKeyInput(key)
                        }
                    }
                }
                ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        var description = host.contentDescription ?: (host as? TextView)?.text

                        if (key == Key.KeyDakutenSmall && currentInputMode.value == InputMode.ModeNumber) {
                            description = "かっこ とじかっこ かくかっこ とじかくかっこ"
                        }

                        Log.d("TenKeyAccessibility", "onInitializeAccessibilityNodeInfo: key=$key, mode=${currentInputMode.value}, desc=$description")

                        if (!description.isNullOrEmpty()) {
                            val mappedDescription = if ((currentInputMode.value == InputMode.ModeEnglish || currentInputMode.value == InputMode.ModeNumber)
                                && key in listOf(
                                    Key.KeyA, Key.KeyKA, Key.KeySA,
                                    Key.KeyTA, Key.KeyNA, Key.KeyHA,
                                    Key.KeyMA, Key.KeyYA, Key.KeyRA,
                                    Key.KeyWA, Key.KeyKutouten
                                )
                            ) {
                                description.filter { !it.isWhitespace() }
                                    .map { it.toAccessibilityName() }
                                    .joinToString(" ")
                            } else {
                                description.toString()
                            }
                            info.text = mappedDescription
                            info.contentDescription = mappedDescription
                        } else {
                            // Fallback for Read Aloud if somehow cleared
                            if (host == binding.sideKeyReadAloud) {
                                val fallback = host.context.getString(com.kazumaproject.core.R.string.read_aloud)
                                info.text = fallback
                                info.contentDescription = fallback
                            } else if (host == binding.sideKeySymbol) {
                                val fallback = host.context.getString(com.kazumaproject.core.R.string.symbol)
                                info.text = fallback
                                info.contentDescription = fallback
                            }
                        }

                        if (isAyameMode) {
                            info.className = "android.widget.Button"
                            info.isClickable = true
                            info.isLongClickable = true

                            when (key) {
                                Key.SideKeyCursorRight, Key.SideKeyCursorLeft -> {
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "行頭移動 (左フリック)"))
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "行末移動 (右フリック)"))
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_top, "上カーソル (上フリック)"))
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_bottom, "下カーソル (下フリック)"))
                                }
                                Key.SideKeyDelete -> {
                                    val leftLabel = if (isInputComposing) "一括削除 (左フリック)" else "行頭まで削除 (左フリック)"
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, leftLabel))
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "行末まで削除 (右フリック)"))
                                }
                                Key.SideKeySpace -> {
                                    if (isInputComposing) {
                                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_bottom, "予測変換 (下フリック)"))
                                        if (currentInputMode.value != InputMode.ModeNumber) {
                                            val label = if (currentInputMode.value == InputMode.ModeEnglish) "全角英語変換 (上フリック)" else "全角カタカナ変換 (上フリック)"
                                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_top, label))
                                        }
                                        if (currentInputMode.value == InputMode.ModeJapanese) {
                                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "半角カタカナ変換 (右フリック)"))
                                        }
                                    } else {
                                        info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "全半スペース切替 (左フリック)"))
                                    }
                                }
                                Key.SideKeyReadAloud -> {
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "詳細読み上げ (左フリック)"))
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_top, "文頭から読み上げ (上フリック)"))
                                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "文末まで読み上げ (右フリック)"))
                                }
                                else -> {
                                    val keyInfo = currentInputMode.value.next(keyMap = keyMap, key = key, isTablet = false)
                                    if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                                        keyInfo.flickLeft?.let {
                                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_left, "${it.toAccessibilityName()} (左フリック)"))
                                        }
                                        keyInfo.flickTop?.let {
                                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_top, "${it.toAccessibilityName()} (上フリック)"))
                                        }
                                        keyInfo.flickRight?.let {
                                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_right, "${it.toAccessibilityName()} (右フリック)"))
                                        }
                                        keyInfo.flickBottom?.let {
                                            info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(com.kazumaproject.core.R.id.action_flick_bottom, "${it.toAccessibilityName()} (下フリック)"))
                                        }
                                    }
                                }
                            }
                        } else {
                            // クラス名を空にし、役割記述をゼロ幅スペースにすることで「ボタン」の読み込みを完全に阻止する
                            info.className = ""
                            info.roleDescription = "\u200B"
                            // OS側で「ボタン」としての挙動を認識させない（QWERTYと同様）
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
                        if (isAyameMode) {
                            val gesture = when (action) {
                                com.kazumaproject.core.R.id.action_flick_left -> GestureType.FlickLeft
                                com.kazumaproject.core.R.id.action_flick_top -> GestureType.FlickTop
                                com.kazumaproject.core.R.id.action_flick_right -> GestureType.FlickRight
                                com.kazumaproject.core.R.id.action_flick_bottom -> GestureType.FlickBottom
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
        }
    }

    private fun triggerAyameFlickAction(key: Key, gesture: GestureType) {
        when (key) {
            Key.SideKeyCursorRight, Key.SideKeyCursorLeft -> {
                val charCode = when (gesture) {
                    GestureType.FlickLeft -> '\u0001'
                    GestureType.FlickRight -> '\u0002'
                    GestureType.FlickTop -> '\u0003'
                    GestureType.FlickBottom -> '\u0004'
                    else -> null
                }
                if (charCode != null) {
                    flickListener?.onFlick(GestureType.Tap, key, charCode)
                }
            }
            Key.SideKeyDelete -> {
                val charCode = when (gesture) {
                    GestureType.FlickLeft -> '\u0005'
                    GestureType.FlickRight -> '\u0007'
                    else -> null
                }
                if (charCode != null) {
                    flickListener?.onFlick(GestureType.Tap, key, charCode)
                }
            }
            Key.SideKeySpace -> {
                if (gesture == GestureType.FlickBottom || gesture == GestureType.FlickTop || gesture == GestureType.FlickRight || gesture == GestureType.FlickLeft) {
                    flickListener?.onFlick(gesture, key, null)
                }
            }
            Key.SideKeyReadAloud -> {
                val charCode = when (gesture) {
                    GestureType.FlickLeft -> '\u0011'
                    GestureType.FlickTop -> '\u0012'
                    GestureType.FlickRight -> '\u0013'
                    else -> null
                }
                if (charCode != null) {
                    flickListener?.onFlick(GestureType.Tap, key, charCode)
                }
            }
            else -> {
                val keyInfo = currentInputMode.value.next(keyMap = keyMap, key = key, isTablet = false)
                if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                    val targetChar = when (gesture) {
                        GestureType.FlickLeft -> keyInfo.flickLeft
                        GestureType.FlickTop -> keyInfo.flickTop
                        GestureType.FlickRight -> keyInfo.flickRight
                        GestureType.FlickBottom -> keyInfo.flickBottom
                        else -> null
                    }
                    if (targetChar != null) {
                        flickListener?.onFlick(gesture, key, targetChar)
                    }
                }
            }
        }
    }

    private fun performKeyInput(key: Key) {
        val keyInfo = currentInputMode.value
            .next(keyMap = keyMap, key = key, isTablet = false)

        if (keyInfo == KeyInfo.Null) {
            flickListener?.onFlick(
                gestureType = GestureType.Tap, key = key, char = null
            )
            if (key == Key.SideKeyInputMode) {
                handleClickInputModeSwitch()
            }
        } else if (keyInfo is KeyInfo.KeyTapFlickInfo) {
            flickListener?.onFlick(
                gestureType = GestureType.Tap,
                key = key,
                char = keyInfo.tap
            )
        }
        // Reset hover state after input
        currentHoverKey = Key.NotSelected
        isCalledFromHoverEvent = false
    }

    private fun announceKey(key: Key) {
        val targetView = getButtonFromKey(key) as? View ?: return
        targetView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
    }

    private fun setViewsNotFocusable() {
        // No-op or removed in favor of setupAccessibility
    }

    private fun updateSideKeySymbolLabel() {
        binding.apply {
            if (isInputComposing) {
                sideKeySymbol.apply {
                    text = "スペース"
                    contentDescription = "スペース"
                    setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        cachedSpaceDrawable,
                        null,
                        null
                    )
                }
            } else {
                sideKeySymbol.apply {
                    text = context.getString(com.kazumaproject.core.R.string.symbol)
                    contentDescription = context.getString(com.kazumaproject.core.R.string.symbol)
                    setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        cachedSymbolDrawable,
                        null,
                        null
                    )
                }
            }
        }
    }

    override fun announceForAccessibility(text: CharSequence?) {
        if (text == null) return
        if (accessibilityManager.isEnabled) {
            try {
                accessibilityManager.interrupt()
            } catch (e: Exception) {
                Log.e("TenKey", "Failed to interrupt TalkBack", e)
            }
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            event.text.add(text)
            event.packageName = context.packageName
            event.className = javaClass.name
            event.isEnabled = true
            postDelayed({
                try {
                    sendAccessibilityEventUnchecked(event)
                } catch (e: Exception) {
                    Log.e("TenKey", "Failed to send accessibility announcement", e)
                }
            }, 10)
        }
    }

    private fun resetHoverDragStates() {
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

        isHoverDraggingSpaceKey = false
        isSpaceDownAnnounced = false
        isSpaceUpAnnounced = false
        isSpaceRightAnnounced = false

        isHoverDraggingReadAloudKey = false
        isReadAloudLeftAnnounced = false
        isReadAloudUpAnnounced = false
        isReadAloudRightAnnounced = false

        isHoverDraggingCharKey = false
        hoverCharKey = Key.NotSelected
    }

    private fun initHoverDragState(key: Key, screenX: Float, screenY: Float) {
        resetHoverDragStates()
        if (key == Key.SideKeyCursorRight) {
            isHoverDraggingRightCursor = true
            hoverRightCursorDragStartX = screenX
            hoverRightCursorDragEndX = screenX
            hoverRightCursorDragStartY = screenY
            hoverRightCursorDragEndY = screenY
            hoverRightCursorDragTopY = screenY
        } else if (key == Key.SideKeyCursorLeft) {
            isHoverDraggingLeftCursor = true
            hoverLeftCursorDragStartX = screenX
            hoverLeftCursorDragEndX = screenX
            hoverLeftCursorDragStartY = screenY
            hoverLeftCursorDragEndY = screenY
            hoverLeftCursorDragTopY = screenY
        } else if (key == Key.SideKeyDelete) {
            isHoverDraggingDeleteKey = true
            hoverDeleteKeyDragStartX = screenX
            hoverDeleteKeyDragEndX = screenX
            hoverDeleteKeyDragStartY = screenY
            hoverDeleteKeyDragEndY = screenY
            hoverDeleteKeyDragTopY = screenY
        } else if (key == Key.SideKeyReadAloud) {
            isHoverDraggingReadAloudKey = true
            hoverReadAloudKeyDragStartX = screenX
            hoverReadAloudKeyDragEndX = screenX
            hoverReadAloudKeyDragStartY = screenY
            hoverReadAloudKeyDragEndY = screenY
            hoverReadAloudKeyDragTopY = screenY
        } else if (key == Key.SideKeySpace) {
            isHoverDraggingSpaceKey = true
            hoverSpaceKeyDragStartX = screenX
            hoverSpaceKeyDragEndX = screenX
            hoverSpaceKeyDragStartY = screenY
            hoverSpaceKeyDragEndY = screenY
        } else {
            val keyInfo = currentInputMode.value.next(keyMap = keyMap, key = key, isTablet = false)
            if (keyInfo is KeyInfo.KeyTapFlickInfo) {
                isHoverDraggingCharKey = true
                hoverCharKey = key
                hoverCharKeyDragStartX = screenX
                hoverCharKeyDragStartY = screenY
                hoverActiveGesture = GestureType.Tap
                
                val charToAnnounce = keyInfo.tap?.toAccessibilityName()
                if (charToAnnounce != null) {
                    hoverLastAnnouncedChar = charToAnnounce
                    // Do not call announceForAccessibility to prevent redundant reading of the base key name at 500ms mark.
                    android.widget.Toast.makeText(context, charToAnnounce, android.widget.Toast.LENGTH_SHORT).show()
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }
    }
}
