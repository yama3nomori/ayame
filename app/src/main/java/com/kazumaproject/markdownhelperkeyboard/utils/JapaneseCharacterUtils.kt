package com.kazumaproject.markdownhelperkeyboard.utils

object JapaneseCharacterUtils {

    fun isHiragana(char: Char): Boolean {
        return char in '\u3041'..'\u3096'
    }

    fun isKatakana(char: Char): Boolean {
        return char in '\u30A1'..'\u30FA'
    }

    fun isHalfWidthKatakana(char: Char): Boolean {
        return char in '\uFF66'..'\uFF9F'
    }

    fun isFullWidthAlphabet(char: Char): Boolean {
        return char in '\uFF21'..'\uFF3A' || char in '\uFF41'..'\uFF5A'
    }

    fun isHalfWidthAlphabet(char: Char): Boolean {
        return char in '\u0041'..'\u005A' || char in '\u0061'..'\u007A'
    }

    fun isFullWidthNumber(char: Char): Boolean {
        return char in '\uFF10'..'\uFF19'
    }

    fun isHalfWidthNumber(char: Char): Boolean {
        return char in '0'..'9'
    }

    fun isFullWidthSymbol(char: Char): Boolean {
        return (char in '\uFF01'..'\uFF0F') ||
                (char in '\uFF1A'..'\uFF1F') ||
                (char in '\uFF3B'..'\uFF40') ||
                (char in '\uFF5B'..'\uFF65') ||
                (char in '\uFFE0'..'\uFFE6')
    }

    fun isHalfWidthSymbol(char: Char): Boolean {
        return (char in '\u0021'..'\u002F') ||
                (char in '\u003A'..'\u003F') ||
                (char in '\u005B'..'\u0060') ||
                (char in '\u007B'..'\u007E')
    }

    fun isEmoji(char: Char): Boolean {
        val type = Character.getType(char).toByte()
        return Character.isSurrogate(char) || type == Character.OTHER_SYMBOL.toByte()
    }

    fun getCharacterTypeDetailed(char: Char): String? {
        return when {
            isHiragana(char) -> "ひらがな"
            isKatakana(char) -> "カタカナ"
            isHalfWidthKatakana(char) -> "半角カタカナ"
            isFullWidthAlphabet(char) -> "全角アルファベット"
            isHalfWidthAlphabet(char) -> "半角アルファベット"
            isFullWidthNumber(char) -> "全角数字"
            isHalfWidthNumber(char) -> "半角数字"
            isFullWidthSymbol(char) -> "全角記号"
            isHalfWidthSymbol(char) -> "半角記号"
            isEmoji(char) -> "絵文字"
            else -> null
        }
    }
}
