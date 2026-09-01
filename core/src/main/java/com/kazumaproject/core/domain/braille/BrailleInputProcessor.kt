package com.kazumaproject.core.domain.braille

/**
 * 点字入力処理結果
 */
sealed class BrailleInputResult {
    /** 1打目が完了し、2打目待ちに進んだ */
    data class StrokeAdvance(val leftDots: Int) : BrailleInputResult()
    /** 文字（仮名・英数・記号）が完成した */
    data class Character(val text: String) : BrailleInputResult()
    /** 空白（マスあけ） */
    data class Space(val text: String = " ") : BrailleInputResult()
    /** 前置符（濁点・拗音等）がセットされた */
    data class PrefixSet(val prefix: BraillePrefixState) : BrailleInputResult()
    /** 1打目ストロークがリセットされた */
    object StrokeReset : BrailleInputResult()
    /** 該当なし / 変化なし */
    object None : BrailleInputResult()
}

/**
 * プレビュー表示用状態データ
 */
data class BraillePreviewState(
    val step: BrailleStrokeStep,
    val leftDots: Int,
    val rightDots: Int,
    val prefix: BraillePrefixState,
    val mode: BrailleInputMode
)

/**
 * 点字入力プロセッサ (2ストローク 6点点字ステートマシン)
 */
class BrailleInputProcessor(
    var inputMode: BrailleInputMode = BrailleInputMode.JAPANESE
) {
    var currentStep: BrailleStrokeStep = BrailleStrokeStep.FIRST_STROKE
        private set

    var leftDots: Int = BrailleDot.DOT_NONE
        private set

    var currentPrefix: BraillePrefixState = BraillePrefixState.NONE
        private set

    /**
     * ストローク入力を処理する
     * @param isHalfSpace ハンスペースキーが押されたか
     * @param keyIndices 押下されたキー番号のセット（1, 2, 3）
     */
    fun processStroke(isHalfSpace: Boolean, keyIndices: Set<Int>): BrailleInputResult {
        return if (currentStep == BrailleStrokeStep.FIRST_STROKE) {
            // 第1ストローク（左側列: ①②③）
            leftDots = if (isHalfSpace) {
                BrailleDot.DOT_NONE
            } else {
                var dots = BrailleDot.DOT_NONE
                for (key in keyIndices) {
                    dots = dots or BrailleDot.keyToLeftDot(key)
                }
                dots
            }
            currentStep = BrailleStrokeStep.SECOND_STROKE
            BrailleInputResult.StrokeAdvance(leftDots)
        } else {
            // 第2ストローク（右側列: ④⑤⑥）
            val rightDots = if (isHalfSpace) {
                BrailleDot.DOT_NONE
            } else {
                var dots = BrailleDot.DOT_NONE
                for (key in keyIndices) {
                    dots = dots or BrailleDot.keyToRightDot(key)
                }
                dots
            }

            val fullCode = leftDots or rightDots
            // ストロークをリセット
            currentStep = BrailleStrokeStep.FIRST_STROKE
            leftDots = BrailleDot.DOT_NONE

            // 6点コードから文字・操作を解決
            resolveFullCode(fullCode)
        }
    }

    private fun resolveFullCode(fullCode: Int): BrailleInputResult {
        // 空マス（2連続ハンスペース）の場合 -> スペース
        if (fullCode == BrailleDot.DOT_NONE) {
            currentPrefix = BraillePrefixState.NONE
            return BrailleInputResult.Space()
        }

        return when (inputMode) {
            BrailleInputMode.JAPANESE -> {
                // 前置符の判定（現在前置符が未設定の場合）
                if (currentPrefix == BraillePrefixState.NONE) {
                    when (fullCode) {
                        BrailleTable.CODE_DAKUTEN -> {
                            currentPrefix = BraillePrefixState.DAKUTEN
                            return BrailleInputResult.PrefixSet(currentPrefix)
                        }
                        BrailleTable.CODE_HANDAKUTEN -> {
                            currentPrefix = BraillePrefixState.HANDAKUTEN
                            return BrailleInputResult.PrefixSet(currentPrefix)
                        }
                        BrailleTable.CODE_YOUON -> {
                            currentPrefix = BraillePrefixState.YOUON
                            return BrailleInputResult.PrefixSet(currentPrefix)
                        }
                        BrailleTable.CODE_YOU_DAKUTEN -> {
                            currentPrefix = BraillePrefixState.YOU_DAKUTEN
                            return BrailleInputResult.PrefixSet(currentPrefix)
                        }
                        BrailleTable.CODE_YOU_HANDAKUTEN -> {
                            currentPrefix = BraillePrefixState.YOU_HANDAKUTEN
                            return BrailleInputResult.PrefixSet(currentPrefix)
                        }
                        BrailleTable.CODE_NUMBER_PREFIX -> {
                            inputMode = BrailleInputMode.NUMBER
                            return BrailleInputResult.PrefixSet(BraillePrefixState.NUMBER_MODE)
                        }
                        BrailleTable.CODE_FOREIGN_PREFIX -> {
                            inputMode = BrailleInputMode.ENGLISH
                            return BrailleInputResult.PrefixSet(BraillePrefixState.FOREIGN_MODE)
                        }
                    }
                }

                // 日本語仮名デコード
                val (char, nextPrefix) = BrailleTable.decodeJapanese(fullCode, currentPrefix)
                currentPrefix = nextPrefix
                if (char != null) {
                    BrailleInputResult.Character(char)
                } else {
                    BrailleInputResult.None
                }
            }

            BrailleInputMode.ENGLISH -> {
                val char = BrailleTable.ENGLISH_MAP[fullCode]
                if (char != null) {
                    BrailleInputResult.Character(char)
                } else {
                    BrailleInputResult.None
                }
            }

            BrailleInputMode.NUMBER -> {
                val char = BrailleTable.NUMBER_MAP[fullCode]
                if (char != null) {
                    BrailleInputResult.Character(char)
                } else {
                    BrailleInputResult.None
                }
            }
        }
    }

    /**
     * 1打目ストローク中であればリセットする
     * @return 1打目をリセットした場合は true、すでにリセット状態なら false
     */
    fun resetStroke(): Boolean {
        return if (currentStep == BrailleStrokeStep.SECOND_STROKE) {
            currentStep = BrailleStrokeStep.FIRST_STROKE
            leftDots = BrailleDot.DOT_NONE
            true
        } else if (currentPrefix != BraillePrefixState.NONE) {
            currentPrefix = BraillePrefixState.NONE
            true
        } else {
            false
        }
    }

    /**
     * モードを順番に切り替える (JAPANESE -> ENGLISH -> NUMBER -> JAPANESE)
     */
    fun switchMode(): BrailleInputMode {
        inputMode = when (inputMode) {
            BrailleInputMode.JAPANESE -> BrailleInputMode.ENGLISH
            BrailleInputMode.ENGLISH -> BrailleInputMode.NUMBER
            BrailleInputMode.NUMBER -> BrailleInputMode.JAPANESE
        }
        currentStep = BrailleStrokeStep.FIRST_STROKE
        leftDots = BrailleDot.DOT_NONE
        currentPrefix = BraillePrefixState.NONE
        return inputMode
    }

    /**
     * 現在のプレビュー表示状態を取得
     */
    fun getPreviewState(): BraillePreviewState {
        return BraillePreviewState(
            step = currentStep,
            leftDots = leftDots,
            rightDots = BrailleDot.DOT_NONE,
            prefix = currentPrefix,
            mode = inputMode
        )
    }
}