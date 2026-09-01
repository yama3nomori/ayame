package com.kazumaproject.core.domain.braille

import com.kazumaproject.core.domain.braille.BrailleDot.DOT_1
import com.kazumaproject.core.domain.braille.BrailleDot.DOT_2
import com.kazumaproject.core.domain.braille.BrailleDot.DOT_3
import com.kazumaproject.core.domain.braille.BrailleDot.DOT_4
import com.kazumaproject.core.domain.braille.BrailleDot.DOT_5
import com.kazumaproject.core.domain.braille.BrailleDot.DOT_6

/**
 * 点字コード（6ビット）と文字のマッピングおよび合成ロジック
 */
object BrailleTable {

    // 前置符の点字コード
    const val CODE_DAKUTEN = DOT_5                          // 16: ⑤
    const val CODE_HANDAKUTEN = DOT_6                       // 32: ⑥
    const val CODE_YOUON = DOT_4                            // 8:  ④
    const val CODE_YOU_DAKUTEN = DOT_4 or DOT_5             // 24: ④⑤
    const val CODE_YOU_HANDAKUTEN = DOT_4 or DOT_6          // 40: ④⑥
    const val CODE_NUMBER_PREFIX = DOT_3 or DOT_4 or DOT_5 or DOT_6 // 60: ③④⑤⑥
    const val CODE_FOREIGN_PREFIX = DOT_5 or DOT_6          // 48: ⑤⑥

    // 日本語基本仮名点字テーブル (6ビットコード -> かな)
    val JAPANESE_KANA_MAP: Map<Int, String> = mapOf(
        // あ行
        DOT_1 to "あ",
        DOT_1 or DOT_2 to "い",
        DOT_1 or DOT_4 to "う",
        DOT_1 or DOT_2 or DOT_4 to "え",
        DOT_2 or DOT_4 to "お",

        // か行 (+⑥)
        DOT_1 or DOT_6 to "か",
        DOT_1 or DOT_2 or DOT_6 to "き",
        DOT_1 or DOT_4 or DOT_6 to "く",
        DOT_1 or DOT_2 or DOT_4 or DOT_6 to "け",
        DOT_2 or DOT_4 or DOT_6 to "こ",

        // さ行 (+⑤⑥)
        DOT_1 or DOT_5 or DOT_6 to "さ",
        DOT_1 or DOT_2 or DOT_5 or DOT_6 to "し",
        DOT_1 or DOT_4 or DOT_5 or DOT_6 to "す",
        DOT_1 or DOT_2 or DOT_4 or DOT_5 or DOT_6 to "せ",
        DOT_2 or DOT_4 or DOT_5 or DOT_6 to "そ",

        // た行 (+③⑤)
        DOT_1 or DOT_3 or DOT_5 to "た",
        DOT_1 or DOT_2 or DOT_3 or DOT_5 to "ち",
        DOT_1 or DOT_3 or DOT_4 or DOT_5 to "つ",
        DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5 to "て",
        DOT_2 or DOT_3 or DOT_4 or DOT_5 to "と",

        // な行 (+③)
        DOT_1 or DOT_3 to "な",
        DOT_1 or DOT_2 or DOT_3 to "に",
        DOT_1 or DOT_3 or DOT_4 to "ぬ",
        DOT_1 or DOT_2 or DOT_3 or DOT_4 to "ね",
        DOT_2 or DOT_3 or DOT_4 to "の",

        // は行 (+③⑥)
        DOT_1 or DOT_3 or DOT_6 to "は",
        DOT_1 or DOT_2 or DOT_3 or DOT_6 to "ひ",
        DOT_1 or DOT_3 or DOT_4 or DOT_6 to "ふ",
        DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_6 to "へ",
        DOT_2 or DOT_3 or DOT_4 or DOT_6 to "ほ",

        // ま行 (+③⑤⑥)
        DOT_1 or DOT_3 or DOT_5 or DOT_6 to "ま",
        DOT_1 or DOT_2 or DOT_3 or DOT_5 or DOT_6 to "み",
        DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6 to "む",
        DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5 or DOT_6 to "め",
        DOT_2 or DOT_3 or DOT_5 or DOT_6 to "も",

        // ら行 (+⑤)
        DOT_1 or DOT_5 to "ら",
        DOT_1 or DOT_2 or DOT_5 to "り",
        DOT_1 or DOT_4 or DOT_5 to "る",
        DOT_1 or DOT_2 or DOT_4 or DOT_5 to "れ",
        DOT_2 or DOT_4 or DOT_5 to "ろ",

        // や行
        DOT_3 or DOT_4 to "や",
        DOT_3 or DOT_4 or DOT_6 to "ゆ",
        DOT_3 or DOT_4 or DOT_5 to "よ",

        // わ行
        DOT_3 to "わ",
        DOT_3 or DOT_5 to "を",
        DOT_3 or DOT_5 or DOT_6 to "ん",

        // 記号
        DOT_2 or DOT_5 to "ー",                                   // 長音符 (②⑤)
        DOT_2 to "っ",                                            // 促音符 (②)
        DOT_2 or DOT_5 or DOT_6 to "。",                          // 句点 (②⑤⑥)
        DOT_2 or DOT_6 to "？",                                    // 疑問符 (②⑥)
        DOT_2 or DOT_3 or DOT_5 to "！"                            // 感嘆符 (②③⑤)
    )

    // 濁点合成マップ (基本文字 -> 濁音)
    private val DAKUTEN_MAP: Map<String, String> = mapOf(
        "か" to "が", "き" to "ぎ", "く" to "ぐ", "け" to "げ", "こ" to "ご",
        "さ" to "ざ", "し" to "じ", "す" to "ず", "せ" to "ぜ", "そ" to "ぞ",
        "た" to "だ", "ち" to "ぢ", "つ" to "づ", "て" to "で", "と" to "ど",
        "は" to "ば", "ひ" to "び", "ふ" to "ぶ", "へ" to "べ", "ほ" to "ぼ",
        "う" to "ゔ"
    )

    // 半濁点合成マップ (基本文字 -> 半濁音)
    private val HANDAKUTEN_MAP: Map<String, String> = mapOf(
        "は" to "ぱ", "ひ" to "ぴ", "ふ" to "ぷ", "へ" to "ぺ", "ほ" to "ぽ"
    )

    // 拗音合成マップ (基本文字 -> 拗音)
    private val YOUON_MAP: Map<String, String> = mapOf(
        "か" to "きゃ", "き" to "きゃ", "く" to "きゅ", "け" to "きぇ", "こ" to "きょ",
        "さ" to "しゃ", "し" to "しゃ", "す" to "しゅ", "せ" to "しぇ", "そ" to "しょ",
        "た" to "ちゃ", "ち" to "ちゃ", "つ" to "ちゅ", "て" to "ちぇ", "と" to "ちょ",
        "な" to "にゃ", "に" to "にゃ", "ぬ" to "にゅ", "ね" to "にぇ", "の" to "にょ",
        "は" to "ひゃ", "ひ" to "ひゃ", "ふ" to "ひゅ", "へ" to "ひぇ", "ほ" to "ひょ",
        "ま" to "みゃ", "み" to "みゃ", "む" to "みゅ", "め" to "みぇ", "も" to "みょ",
        "ら" to "りゃ", "り" to "りゃ", "る" to "りゅ", "れ" to "りぇ", "ろ" to "りょ"
    )

    // 拗濁音合成マップ
    private val YOU_DAKUTEN_MAP: Map<String, String> = mapOf(
        "か" to "ぎゃ", "き" to "ぎゃ", "く" to "ぎゅ", "け" to "ぎぇ", "こ" to "ぎょ",
        "さ" to "じゃ", "し" to "じゃ", "す" to "じゅ", "せ" to "じぇ", "そ" to "じょ",
        "た" to "ぢゃ", "ち" to "ぢゃ", "つ" to "ぢゅ", "て" to "ぢぇ", "と" to "ぢょ",
        "は" to "びゃ", "ひ" to "びゃ", "ふ" to "びゅ", "へ" to "びぇ", "ほ" to "びょ"
    )

    // 拗半濁音合成マップ
    private val YOU_HANDAKUTEN_MAP: Map<String, String> = mapOf(
        "は" to "ぴゃ", "ひ" to "ぴゃ", "ふ" to "ぴゅ", "へ" to "ぴぇ", "ほ" to "ぴょ"
    )

    // 英語アルファベットテーブル
    val ENGLISH_MAP: Map<Int, String> = mapOf(
        DOT_1 to "a",
        DOT_1 or DOT_2 to "b",
        DOT_1 or DOT_4 to "c",
        DOT_1 or DOT_4 or DOT_5 to "d",
        DOT_1 or DOT_5 to "e",
        DOT_1 or DOT_2 or DOT_4 to "f",
        DOT_1 or DOT_2 or DOT_4 or DOT_5 to "g",
        DOT_1 or DOT_2 or DOT_5 to "h",
        DOT_2 or DOT_4 to "i",
        DOT_2 or DOT_4 or DOT_5 to "j",
        DOT_1 or DOT_3 to "k",
        DOT_1 or DOT_2 or DOT_3 to "l",
        DOT_1 or DOT_3 or DOT_4 to "m",
        DOT_1 or DOT_3 or DOT_4 or DOT_5 to "n",
        DOT_1 or DOT_3 or DOT_5 to "o",
        DOT_1 or DOT_2 or DOT_3 or DOT_4 to "p",
        DOT_1 or DOT_2 or DOT_3 or DOT_4 or DOT_5 to "q",
        DOT_1 or DOT_2 or DOT_3 or DOT_5 to "r",
        DOT_2 or DOT_3 or DOT_4 to "s",
        DOT_2 or DOT_3 or DOT_4 or DOT_5 to "t",
        DOT_1 or DOT_3 or DOT_6 to "u",
        DOT_1 or DOT_2 or DOT_3 or DOT_6 to "v",
        DOT_2 or DOT_4 or DOT_5 or DOT_6 to "w",
        DOT_1 or DOT_3 or DOT_4 or DOT_6 to "x",
        DOT_1 or DOT_3 or DOT_4 or DOT_5 or DOT_6 to "y",
        DOT_1 or DOT_3 or DOT_5 or DOT_6 to "z",
        // 英語記号
        DOT_2 to ",",
        DOT_2 or DOT_5 or DOT_6 to ".",
        DOT_2 or DOT_3 or DOT_6 to "?",
        DOT_2 or DOT_3 or DOT_5 to "!",
        DOT_3 or DOT_6 to "-"
    )

    // 数字テーブル
    val NUMBER_MAP: Map<Int, String> = mapOf(
        DOT_1 to "1",
        DOT_1 or DOT_2 to "2",
        DOT_1 or DOT_4 to "3",
        DOT_1 or DOT_4 or DOT_5 to "4",
        DOT_1 or DOT_5 to "5",
        DOT_1 or DOT_2 or DOT_4 to "6",
        DOT_1 or DOT_2 or DOT_4 or DOT_5 to "7",
        DOT_1 or DOT_2 or DOT_5 to "8",
        DOT_2 or DOT_4 to "9",
        DOT_2 or DOT_4 or DOT_5 to "0",
        DOT_2 to ",",
        DOT_2 or DOT_5 or DOT_6 to ".",
        DOT_3 or DOT_6 to "-"
    )

    /**
     * 前置符と文字コードから合成文字を取得
     */
    fun decodeJapanese(code: Int, prefix: BraillePrefixState): Pair<String?, BraillePrefixState> {
        val baseChar = JAPANESE_KANA_MAP[code]

        if (baseChar == null) {
            // マップにない場合
            return Pair(null, prefix)
        }

        return when (prefix) {
            BraillePrefixState.NONE -> Pair(baseChar, BraillePrefixState.NONE)
            BraillePrefixState.DAKUTEN -> {
                val combined = DAKUTEN_MAP[baseChar] ?: baseChar
                Pair(combined, BraillePrefixState.NONE)
            }
            BraillePrefixState.HANDAKUTEN -> {
                val combined = HANDAKUTEN_MAP[baseChar] ?: baseChar
                Pair(combined, BraillePrefixState.NONE)
            }
            BraillePrefixState.YOUON -> {
                val combined = YOUON_MAP[baseChar] ?: baseChar
                Pair(combined, BraillePrefixState.NONE)
            }
            BraillePrefixState.YOU_DAKUTEN -> {
                val combined = YOU_DAKUTEN_MAP[baseChar] ?: baseChar
                Pair(combined, BraillePrefixState.NONE)
            }
            BraillePrefixState.YOU_HANDAKUTEN -> {
                val combined = YOU_HANDAKUTEN_MAP[baseChar] ?: baseChar
                Pair(combined, BraillePrefixState.NONE)
            }
            else -> Pair(baseChar, BraillePrefixState.NONE)
        }
    }
}