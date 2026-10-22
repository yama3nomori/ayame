package com.kazumaproject.core.domain.extensions

import com.kazumaproject.core.domain.key.Key
import com.kazumaproject.core.domain.state.InputMode
import org.junit.Assert.assertEquals
import org.junit.Test

class TenKeyAccessibilityTest {

    @Test
    fun testNumberModeDigitsHavePauseAfterNumber() {
        // キー1: "1\n☆♪→" -> "1、 ほし おんぷ みぎやじるし"
        val descKey1 = buildTenKeyAccessibilityDescription("1\n☆♪→", Key.KeyA, InputMode.ModeNumber)
        assertEquals("1、 ほし おんぷ みぎやじるし", descKey1)

        // キー2: "2\n￥$€" -> "2、 えん どる ユーロ"
        val descKey2 = buildTenKeyAccessibilityDescription("2\n￥$€", Key.KeyKA, InputMode.ModeNumber)
        assertEquals("2、 えん どる ユーロ", descKey2)

        // キー3: "3\n%°#" -> "3、 パーセント ど シャープ"
        val descKey3 = buildTenKeyAccessibilityDescription("3\n%°#", Key.KeySA, InputMode.ModeNumber)
        assertEquals("3、 パーセント ど シャープ", descKey3)

        // キー4: "4\n○ *・" -> "4、 まる アスタリスク なかてん"
        val descKey4 = buildTenKeyAccessibilityDescription("4\n○ *・", Key.KeyTA, InputMode.ModeNumber)
        assertEquals("4、 まる アスタリスク なかてん", descKey4)

        // キー5: "5\n+×÷" -> "5、 プラス かける わる"
        val descKey5 = buildTenKeyAccessibilityDescription("5\n+×÷", Key.KeyNA, InputMode.ModeNumber)
        assertEquals("5、 プラス かける わる", descKey5)

        // キー6: "6\n<=>" -> "6、 しょうなり イコール だいなり"
        val descKey6 = buildTenKeyAccessibilityDescription("6\n<=>", Key.KeyHA, InputMode.ModeNumber)
        assertEquals("6、 しょうなり イコール だいなり", descKey6)

        // キー7: "7\n「」:" -> "7、 かぎかっこ とじかぎかっこ コロン"
        val descKey7 = buildTenKeyAccessibilityDescription("7\n「」:", Key.KeyMA, InputMode.ModeNumber)
        assertEquals("7、 かぎかっこ とじかぎかっこ コロン", descKey7)

        // キー8: "8\n〒々〆" -> "8、 ゆうびん くりかえし しめ"
        val descKey8 = buildTenKeyAccessibilityDescription("8\n〒々〆", Key.KeyYA, InputMode.ModeNumber)
        assertEquals("8、 ゆうびん くりかえし しめ", descKey8)

        // キー9: "9\n^ | \\" -> "9、 ハット たてせん バックスラッシュ"
        val descKey9 = buildTenKeyAccessibilityDescription("9\n^ | \\", Key.KeyRA, InputMode.ModeNumber)
        assertEquals("9、 ハット たてせん バックスラッシュ", descKey9)

        // キー10: "0\n~…@" -> "0、 波線 てんてんてん アットマーク"
        val descKey10 = buildTenKeyAccessibilityDescription("0\n~…@", Key.KeyWA, InputMode.ModeNumber)
        assertEquals("0、 波線 てんてんてん アットマーク", descKey10)
    }

    @Test
    fun testNumberModeKey12DoesNotHavePause() {
        // キー12 (.,-/) は数字キーではないため間（読点）は入らない
        val descKey12 = buildTenKeyAccessibilityDescription("   .,-/   ", Key.KeyKutouten, InputMode.ModeNumber)
        assertEquals("ピリオド カンマ ハイフン スラッシュ", descKey12)
    }

    @Test
    fun testEnglishModeDoesNotHavePause() {
        // 英語モードでは間（読点）は入らない
        val descEnglishKey1 = buildTenKeyAccessibilityDescription("@#&_1", Key.KeyA, InputMode.ModeEnglish)
        assertEquals("アットマーク シャープ アンド アンダーバー 1", descEnglishKey1)

        val descEnglishKey2 = buildTenKeyAccessibilityDescription("abcABC2", Key.KeyKA, InputMode.ModeEnglish)
        assertEquals("a b c A B C 2", descEnglishKey2)
    }

    @Test
    fun testJapaneseModeUnchanged() {
        // 日本語モードではそのままのテキストが返る
        val descJp = buildTenKeyAccessibilityDescription("あ\nい\nう\nえ\nお", Key.KeyA, InputMode.ModeJapanese)
        assertEquals("あ\nい\nう\nえ\nお", descJp)
    }

    @Test
    fun testNullOrEmptyDescription() {
        assertEquals("", buildTenKeyAccessibilityDescription(null, Key.KeyA, InputMode.ModeNumber))
        assertEquals("", buildTenKeyAccessibilityDescription("", Key.KeyA, InputMode.ModeNumber))
    }

    @Test
    fun testSingleDigitWithoutSymbols() {
        // 記号を含まない単一の数字の場合、読点は付かない
        val descSingle = buildTenKeyAccessibilityDescription("1", Key.KeyA, InputMode.ModeNumber)
        assertEquals("1", descSingle)
    }
}
