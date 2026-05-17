# DTalker IME 入力メカニズム詳細調査レポート

本ドキュメントは、DTalker IME（`KeyboardViewEx.java`）における**「フリック入力（Flick Input）」**と**「ドラッグ入力（Drag Input）」**のそれぞれの設計思想、判定アルゴリズム、および具体的な実装ソースコードの調査結果をまとめたものです。

---

## 1. 入力方式の概要と設計思想

DTalker IME では、一般的なスマートフォンユーザー向けの高速入力と、視覚障害者（主に TalkBack 利用者）向けのアクセシビリティ入力を両立させるため、以下の2つのジェスチャー入力方式が実装されています。

| 入力方式 | 主な対象ユーザー | 入力操作の特徴 | 設計思想 |
| :--- | :--- | :--- | :--- |
| **フリック入力** | 一般ユーザー | キーを押した直後に素早く指を弾く（Flick）。 | 速度と直感的な入力を重視。 |
| **ドラッグ入力** | 視覚障害者<br>（TalkBack併用者） | キーに指を置いて位置を確認（ホールド）した後、指を数ミリずらして離す。 | 探り打ち（探索）のしやすさと、誤入力の防止を極限まで両立。 |

---

## 2. フリック入力（はじく操作）の実装詳細

### A. 判定トリガーとイベントフロー
フリック入力は、Android 標準のジェスチャー検出クラス `GestureDetector` の `onFling` イベントをトリガーとして実行されます。

1. **タッチ開始 (`ACTION_DOWN`)**:
   - `mStartX`, `mStartY` に初期タッチ座標を保存。
   - `mSwipeStartX`, `mSwipeStartY`（キーのタッチ座標）および `mSwipeCenterX`, `mSwipeCenterY`（キーの中心座標）を算出。
2. **フリック検知 (`onFling`)**:
   - 指が離れる瞬間の速度と移動距離からフリック方向を判定。

### B. 判定パラメータと閾値
* **フリック速度閾値 (`mSwipeThreshold`)**:
  - `500 * density` (dp/sec 相当) 以上の速さで指を動かした時にフリックと判定されます。
* **移動距離閾値 (`travelX`, `travelY`)**:
  - ユーザー設定の `mFlickLevel`（フリック感度）に基づいて可変します。
    - **Level 0 (敏感)**: 20px
    - **Level 1 (標準)**: 50px
    - **Level 2 (鈍感)**: 100px
  - 判定時には `travelX` と `travelY` の小さい方に統一されます。

### C. 独自の「基準点」計算ロジック
DTalker IME では、フリックの始点として**「キーのタッチ開始点からの移動量」**と**「キーの中心点からの移動量」**の2種類を並行して計算し、**絶対値が大きい方を採用**する堅牢な設計になっています。
これにより、キーの端をタッチしてフリックを開始した場合でも正確に判定できます。

```java
float dX1 = me2.getX() - mSwipeStartX; // タッチ開始点からの移動量
float dX2 = me2.getX() - mSwipeCenterX; // キー中心点からの移動量
float deltaX = (Math.abs(dX1) > Math.abs(dX2))? dX1 : dX2;
```

### D. 方向判定とキー確定
`onFling` 内で方向が確定すると、即座に該当文字を読み上げ（`sendAccessibilityEventForUnicodeCharacter`）、各フリック方向のイベントを発火します。

* **左 (Left)**: `codes[1]` を読み上げ ➔ `swipeLeft()`
* **上 (Up)**: `codes[2]` を読み上げ ➔ `swipeUp()`
* **右 (Right)**: `codes[3]` を読み上げ ➔ `swipeRight()`
* **下 (Down)**: `codes[4]` を読み上げ ➔ `swipeDown()`

---

## 3. ドラッグ入力（数ミリずらす操作）の実装詳細

### A. 判定トリガーとイベントフロー
ドラッグ入力は、`ACTION_MOVE` イベントの中で「滞留時間」と「微小な移動量」を監視することで、キーから指を離さずに方向選択を行う独自の実装です。

1. **タッチ開始 (`ACTION_DOWN`)**:
   - 通常のタッチ処理を開始。この時点ではまだドラッグモードではありません。
2. **滞留時間の監視 (`ACTION_MOVE` 時)**:
   - 同じキーの上で指がとどまっている時間（`mCurrentKeyTime`）を累積計算します。
   - **500ms** を超えた時点でドラッグ選択モード（`mDragSelectMeasure = true`）に切り替わります。
3. **基準点の再固定（極めて重要な処理）**:
   - ドラッグモードに移行した瞬間の指の座標を、新たなドラッグ基準点（`mDragKeyX`, `mDragKeyY`）として保存します。
   - **これにより、500msの間に指が動いてしまっていてもリセットされ、ここから「数ミリずらす操作」を正確に開始できるようになります。**

```java
if (mCurrentKeyTime > 500){
    if (mDragSelectMeasure == false){
        // 500ms経過時の座標をドラッグ起点としてリセット固定
        resetMultiTap();
        mDragKeyX = me.getX();
        mDragKeyY = me.getY();
        mDragSelectMeasure = true;
    }
    ...
}
```

### B. 判定パラメータと閾値（数ミリの判定）
* **移動閾値 (`maxMove`)**:
  - ドラッグ起点から、キーのサイズ（幅・高さの小さい方）の **1/6** (`Math.min(width/6, height/6)`) 以上の移動があった場合に方向を検知します。
  - ※ 過去のバージョンでは `1/4` でしたが、感度向上のため `1/6` に修正されました。
* **方向の割り当て**:
  - 起点からの `deltaX`, `deltaY` を比較し、方向を確定します。
  - 確定した方向に対応するインデックス（左=1, 上=2, 右=3, 下=4）を一時的にマルチタップカウンタ `mTapCount` に代入します。

### C. リアルタイム・フィードバック（音声と表示）
指をずらしている最中、方向が切り替わるたびに以下の処理がリアルタイムに実行されます。
* `mDragSelected = true` に設定。
* 指の位置にあるキーコード（`getPreviewKeycode`）を取得。
* 選択中の文字プレビューをポップアップ表示（`showKey(keyIndex, true)`）。
* 選択中の文字を即座に音声読み上げ（`sendAccessibilityEventForUnicodeCharacter`）。

### D. 指を離した瞬間の確定 (`ACTION_UP`)
指が離された際、通常のタップ入力ではなく「ドラッグ選択」が行われていた（`mDragSelected == true`）場合は、その時点の `mTapCount` に応じた確定処理を行います。

```java
if (mDragSelected && keyIndex >= 0){
    if (mTapCount == 1)      swipeLeft();
    else if (mTapCount == 2) swipeUp();
    else if (mTapCount == 3) swipeRight();
    else if (mTapCount == 4) swipeDown();
    else if (mTapCount <= 0) {
        detectAndSendKey(mCurrentKey, touchX, touchY, eventTime); // 移動なし（中央）
    }
    mDragSelected = false;
}
```

---

## 4. 2つの入力方式の技術的対比

| 比較項目 | フリック入力 (Flick) | ドラッグ入力 (Drag) |
| :--- | :--- | :--- |
| **イベント契機** | `onFling()` (GestureDetector) | `ACTION_MOVE` ➔ `ACTION_UP` |
| **起動条件** | 素早いスワイプ動作（速度重視） | 同じキー上で **500ms** 以上の滞留（ホールド） |
| **移動量の基準点** | タッチ開始点、またはキーの中心点 | **500ms経過した時点の指の座標** |
| **判定距離 (閾値)** | `mFlickLevel` に依存 (20px ~ 100px) | キーの幅/高さの **1/6** (数ミリの極小距離) |
| **動作の特徴** | 指を弾いて瞬時に文字を入力・確定する。 | 指を滑らせて文字を探り、読み上げを聞きながら指を離して確定する。 |

---

## 5. 実装コード（`KeyboardViewEx.java` より抜粋）

### 【フリック判定部】
```java
// initGestureDetector() より一部抜粋
switch(mFlickLevel){
    case 0: travelX = 20; travelY = 20; break;
    case 1: travelX = 50; travelY = 50; break;
    case 2: travelX = 100; travelY = 100; break;
}
...
if (velocityX > mSwipeThreshold && absY < absX && deltaX > travelX) {
    ...
    swipeRight();
    return true;
} else if (velocityX < -mSwipeThreshold && absY < absX && deltaX < -travelX) {
    ...
    swipeLeft();
    return true;
}
```

### 【ドラッグ判定部】
```java
// onModifiedTouchEvent() - ACTION_MOVE より一部抜粋
if (mDragSelectionMode){
    if (mCurrentKeyTime > 500){
        if (mDragSelectMeasure==false){
            resetMultiTap();
            mDragKeyX = me.getX();
            mDragKeyY = me.getY();
            mDragSelectMeasure = true;
            showKey(keyIndex, false);
        }
        Key key = mKeys[keyIndex];
        int len = key.codes.length;
        float deltaX = me.getX() - mDragKeyX;
        float deltaY = me.getY() - mDragKeyY;
        int maxMove = Math.min( mSwipedKey.width/6,  mSwipedKey.height/6);
        boolean move=false;
        
        if (deltaX > maxMove){      // 右ドラッグ
            if (mTapCount!=3) {
                if (mTapCount==1) mTapCount = 0;
                else if (len>3) mTapCount = 3;
                move=true;
            }
        }
        // ... (左・上・下の判定も同様) ...
        
        if(move){
            mDragKeyX = me.getX();
            mDragKeyY = me.getY();
            int keycode = getPreviewKeycode(key, mTapCount);
            if (keycode > 0){
                mDragSelected = true;
                showKey(keyIndex, true);
                if (mShowPreviewSpeakEnable) sendAccessibilityEventForUnicodeCharacter( keycode, "", mPhoneticMode);
            }
        }
    }
}
```

---
**作成日:** 2026年5月17日  
**対象ファイル:** [KeyboardViewEx.java](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/DTalkerIME/dtalkerime/src/main/java/jp/co/createsystem/dtalkeropenwnn/KeyboardViewEx.java)
