package com.kazumaproject.core.domain.listener

import com.kazumaproject.core.domain.qwerty.QWERTYKey

interface QWERTYKeyListener {
    fun onPressedQWERTYKey(
        qwertyKey: QWERTYKey,
    )

    fun onReleasedQWERTYKey(
        qwertyKey: QWERTYKey,
        tap: Char?,
        variations: List<Char>?
    )

    fun onLongPressQWERTYKey(qwertyKey: QWERTYKey)

    /**
     * QWERTYキーが上フリックされたときに呼び出されます。
     * (setFlickUpDetectionEnabled(true) が設定されている場合のみ)
     *
     * @param qwertyKey フリックジェスチャーが開始されたキー
     */
    fun onFlickUPQWERTYKey(
        qwertyKey: QWERTYKey,
        tap: Char?,
        variations: List<Char>?
    )

    /**
     * QWERTYキーが下フリックされたときに呼び出されます。
     * (setFlickDownDetectionEnabled(true) が設定されている場合のみ)
     *
     * @param qwertyKey フリックジェスチャーが開始されたキー
     * @param character 下フリックで入力される大文字
     */
    fun onFlickDownQWERTYKey(
        qwertyKey: QWERTYKey,
        character: Char
    )

    /**
     * 削除キーが左フリック/ドラッグされたときに呼び出されます。
     * 行頭まで削除する処理を実行します。
     */
    fun onDeleteLeftFlick()
}
