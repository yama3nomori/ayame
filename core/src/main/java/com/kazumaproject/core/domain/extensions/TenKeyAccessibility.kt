package com.kazumaproject.core.domain.extensions

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.InputMode

/**
 * テンキー（スマートフォン・タブレット）のキー用アクセシビリティ読み上げ文字列を生成する
 */
fun buildTenKeyAccessibilityDescription(
    description: CharSequence?,
    key: Key,
    inputMode: InputMode
): String {
    if (description.isNullOrEmpty()) return ""

    val numberKeys = listOf(
        Key.KeyA, Key.KeyKA, Key.KeySA,
        Key.KeyTA, Key.KeyNA, Key.KeyHA,
        Key.KeyMA, Key.KeyYA, Key.KeyRA,
        Key.KeyWA
    )

    val targetKeys = numberKeys + Key.KeyKutouten

    if ((inputMode == InputMode.ModeEnglish || inputMode == InputMode.ModeNumber) && key in targetKeys) {
        val nonWhitespaceChars = description.filter { !it.isWhitespace() }
        if (nonWhitespaceChars.isEmpty()) return description.toString()

        // 数字モードの数字キー（1〜0）の場合、数字と記号の間に1文字分の間（読点）を挿入する
        if (inputMode == InputMode.ModeNumber && key in numberKeys) {
            val firstChar = nonWhitespaceChars.first()
            val remainingChars = nonWhitespaceChars.drop(1)
            return if (remainingChars.isNotEmpty()) {
                val firstNumberName = firstChar.toAccessibilityName()
                val symbolNames = remainingChars.map { it.toAccessibilityName() }.joinToString(" ")
                "$firstNumberName、 $symbolNames"
            } else {
                firstChar.toAccessibilityName()
            }
        }

        return nonWhitespaceChars
            .map { it.toAccessibilityName() }
            .joinToString(" ")
    }

    return description.toString()
}
