package com.kazumaproject.core.domain.braille

/**
 * 6点点字の各点のビットマスクおよび関連定数
 */
object BrailleDot {
    // 6点点字の点ビットマスク
    const val DOT_NONE = 0
    const val DOT_1 = 1 shl 0 // 1  (左上)
    const val DOT_2 = 1 shl 1 // 2  (左中)
    const val DOT_3 = 1 shl 2 // 4  (左下)
    const val DOT_4 = 1 shl 3 // 8  (右上)
    const val DOT_5 = 1 shl 4 // 16 (右中)
    const val DOT_6 = 1 shl 5 // 32 (右下)

    // 列マスク
    const val LEFT_COLUMN_MASK = DOT_1 or DOT_2 or DOT_3    // 1 | 2 | 4 = 7
    const val RIGHT_COLUMN_MASK = DOT_4 or DOT_5 or DOT_6   // 8 | 16 | 32 = 56

    /**
     * キー番号（1〜3）から第1ストローク（左列: ①②③）のビットを計算
     */
    fun keyToLeftDot(keyIndex: Int): Int {
        return when (keyIndex) {
            1 -> DOT_1
            2 -> DOT_2
            3 -> DOT_3
            else -> DOT_NONE
        }
    }

    /**
     * キー番号（1〜3）から第2ストローク（右列: ④⑤⑥）のビットを計算
     */
    fun keyToRightDot(keyIndex: Int): Int {
        return when (keyIndex) {
            1 -> DOT_4
            2 -> DOT_5
            3 -> DOT_6
            else -> DOT_NONE
        }
    }
}

/**
 * 点字入力モード
 */
enum class BrailleInputMode {
    JAPANESE,   // 日本語（仮名点字）
    ENGLISH,    // 英語（英字点字）
    NUMBER      // 数字
}

/**
 * 前置符の状態
 */
enum class BraillePrefixState {
    NONE,               // 通常
    DAKUTEN,            // 濁点符 (点5)
    HANDAKUTEN,         // 半濁点符 (点6)
    YOUON,              // 拗音符 (点4)
    YOU_DAKUTEN,        // 拗濁音符 (点4, 5)
    YOU_HANDAKUTEN,     // 拗半濁音符 (点4, 6)
    SPECIAL_YOUON,      // 特殊拗音符 (点3, 4)
    NUMBER_MODE,        // 数符 (点3, 4, 5, 6)
    FOREIGN_MODE        // 外字符 (点5, 6)
}

/**
 * 2ストロークの入力ステップ
 */
enum class BrailleStrokeStep {
    FIRST_STROKE,   // 1打目: 左側列 (①②③)
    SECOND_STROKE   // 2打目: 右側列 (④⑤⑥)
}