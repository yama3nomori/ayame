# ボリュームキーによるカーソル移動の実装仕様

## 概要
Android 12以降の端末においても、ハードウェアボリュームキーをインターセプトしてIMEのカーソル移動（DPAD_LEFT/RIGHT）に割り当てる実装。TalkBack（ユーザー補助）環境下において、文字以外の不要な情報を読み上げさせないための最適化を施している。

## 実装の核心（IMEService.kt）

### 1. MediaSessionによる奪取
Android 12+ では標準の `onKeyDown` だけではボリュームキーを確実に奪えないため、`MediaSession` を使用する。
- **VolumeProvider**: `VOLUME_CONTROL_RELATIVE` モードを使用し、方向（1 or -1）のみを検知する。
- **Metadata**: `METADATA_KEY_TITLE` を必ず空文字 `""` に設定すること。これに値が入っていると、TalkBackがボリューム操作のたびにタイトルを読み上げてしまう。

### 2. イベントの完全消費
システムの音量UI（スライダーHUD）を出さないために、以下の箇所ですべて `true` を返す必要がある。
- `onKeyDown`
- `onKeyUp`
- `onAdjustVolume` (MediaSession側)

### 3. 音量HUDの抑制フラグ
`AudioManager.adjustStreamVolume` を呼び出す際、以下のフラグを組み合わせる。
- `ADJUST_SAME`: 実際の音量は変えない。
- `FLAG_REMOVE_SOUND_AND_VIBRATE`: システムの通知音・バイブ・HUD表示を抑制する。

### 4. TalkBackの「シンプル化」
- `announceForAccessibility` による「左」「右」などの明示的な発話は行わない。
- 独自のアナウンスを排除し、標準の `sendKeyEvent(KEYCODE_DPAD_...)` のみに任せることで、Android標準の「移動先の1文字読み上げ」のみが機能する。

## 注意事項
- `MediaSession.isActive` を頻繁に `true` にリセットすると、TalkBackがそのたびにアナウンスを行う可能性があるため、`false` の場合のみ有効化する設計にしている。
