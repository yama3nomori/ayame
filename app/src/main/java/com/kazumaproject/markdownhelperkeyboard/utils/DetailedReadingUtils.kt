package com.kazumaproject.markdownhelperkeyboard.utils

import com.kazumaproject.markdownhelperkeyboard.repository.TamachiRepository

object DetailedReadingUtils {

    /**
     * TTSエンジン（DTalker TTS またはその他）に応じた詳細読みテキストを生成する。
     *
     * @param text 詳細読みを行う対象文字列
     * @param prefix 接頭辞（例: "1行目、" など。空文字列の場合は付加しない）
     * @param positionText 位置情報（例: " 1の10" など。変換候補用）
     * @param isDTalkerTTS DTalker TTSが有効かどうか
     * @param tamachiRepository 田町読みリポジトリ
     * @return 読み上げ用テキスト（DTalker TTS有効時はSSML、それ以外は田町読みテキスト）
     */
    fun getDetailedReading(
        text: String,
        prefix: String = "",
        positionText: String = "",
        isDTalkerTTS: Boolean,
        tamachiRepository: TamachiRepository?
    ): String {
        if (text.isEmpty()) {
            return prefix + positionText
        }
        return if (isDTalkerTTS) {
            getSyosaiYomiSSML(word = text, prefix = prefix, positionText = positionText)
        } else {
            val baseReading = tamachiRepository?.getDetailedReading(text) ?: text
            "$prefix$baseReading$positionText"
        }
    }

    /**
     * DTalker TTS 向けの SSML を生成する。
     *
     * - 純カタカナ（長音含む）: 「カタカナの[単語]」
     * - 純ひらがな（長音含む）: 「ひらがなの[単語]」
     * - 純半角カタカナ（長音含む）: 「半角カタカナの[単語]」
     * - それ以外（漢字等含む）: <say-as interpret-as="characters" format="glyphs">[単語]</say-as>
     */
    fun getSyosaiYomiSSML(
        word: String,
        prefix: String = "",
        positionText: String = ""
    ): String {
        val escapedWord = word.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val isPureKatakana = word.isNotEmpty() && word.all { JapaneseCharacterUtils.isKatakana(it) || it == 'ー' }
        val isPureHiragana = word.isNotEmpty() && word.all { JapaneseCharacterUtils.isHiragana(it) || it == 'ー' }
        val isPureHalfKatakana = word.isNotEmpty() && word.all { JapaneseCharacterUtils.isHalfWidthKatakana(it) || it == 'ｰ' }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\"?>")
        sb.append("<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" ")
        sb.append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
        sb.append("xsi:schemaLocation=\"http://www.w3.org/2001/10/synthesis ")
        sb.append("http://www.w3.org/TR/speech-synthesis/synthesis.xsd\" ")
        sb.append("xml:lang=\"ja\">")

        if (prefix.isNotEmpty()) {
            val escapedPrefix = prefix.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            sb.append(escapedPrefix)
        }

        when {
            isPureKatakana -> {
                sb.append("カタカナの")
                sb.append(escapedWord)
            }
            isPureHiragana -> {
                sb.append("ひらがなの")
                sb.append(escapedWord)
            }
            isPureHalfKatakana -> {
                sb.append("半角カタカナの")
                sb.append(escapedWord)
            }
            else -> {
                sb.append("<say-as interpret-as=\"characters\" format=\"glyphs\">")
                sb.append(escapedWord)
                sb.append("</say-as>")
            }
        }
        if (positionText.isNotEmpty()) {
            sb.append(positionText)
        }
        sb.append("</speak>")
        return sb.toString()
    }
}
