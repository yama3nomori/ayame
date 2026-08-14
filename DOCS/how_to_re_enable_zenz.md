# Zenz (AI予測変換エンジン) 再有効化手順書

本ドキュメントは、現在無効化されているオンデバイスAI予測変換エンジン **Zenz** を再度有効にする際に必要な作業手順をまとめたものです。

---

## 1. 概要
Zenz は Llama.cpp を用いたオンデバイスのニューラル言語モデルによる予測変換機能です。
現在は以下の理由により、**起動時の不要なリソース消費を防ぐため、初期化処理が完全にコメントアウト（無効化）**されています。
* キーボードのサービス起動時に、毎回約21MBのAIモデルファイル (`ggml-model-Q5_K_M.gguf`) をメモリにロードする処理が走り、メモリ負荷および起動速度にオーバーヘッドが生じるため。
* 32ビット端末（`armeabi-v7a`）でネイティブコードのビルドがサポートされておらず、コンパイルエラーやインストール失敗が発生するため。

---

## 2. 再有効化手順

### ステップ 0: ビルド構成の復元（モジュール除外の解除）

Zenz モジュールは、32ビット端末（`armeabi-v7a`）へのインストール互換性確保およびビルド高速化のため、Gradleのビルド構成から完全に除外されています。
再有効化する前に、以下のビルド設定を復元し、ダミーのクラスを削除してください。

#### ① `settings.gradle` の復元
`settings.gradle` 内の `// include ':zenz'` のコメントアウトを解除します。
```groovy
include ':zenz'
```

#### ② `app/build.gradle` の復元
`app/build.gradle` 内の `// implementation project(':zenz')` のコメントアウトを解除します。
```groovy
implementation project(':zenz')
```

#### ③ ダミーの `ZenzEngine.kt` の削除
`app` モジュール内に配置されているダミーの `ZenzEngine` クラスファイルを削除します。
* 削除対象ファイル: [ZenzEngine.kt](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/java/com/kazumaproject/zenz/ZenzEngine.kt)

---

### ステップ 1: モデル初期化処理の復元
[IMEService.kt](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/ime_service/IMEService.kt) 内の `providesZenzEngine` メソッドのコメントアウトを解除し、元の初期化処理を復元します。

#### 【修正前】
```kotlin
    private fun providesZenzEngine(context: Context): ZenzEngine? {
        /*
        val defaultAssetFileName = "ggml-model-Q5_K_M.gguf"
        ... (中略) ...
        return ZenzEngine
        */
        return null
    }
```

#### 【修正後】
戻り値の型を `ZenzEngine`（Non-null）に戻し、コメントアウト（`/*` と `*/`）を外して本来のコピー＆ロード処理を復帰させます。
```kotlin
    private fun providesZenzEngine(context: Context): ZenzEngine {
        val defaultAssetFileName = "ggml-model-Q5_K_M.gguf"
        val defaultDestFile = File(context.filesDir, defaultAssetFileName)

        fun ensureDefaultModelCopied(): File {
            if (!defaultDestFile.exists()) {
                context.assets.open(defaultAssetFileName).use { input ->
                    FileOutputStream(defaultDestFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return defaultDestFile
        }

        fun copyUriToInternalFile(uriString: String): File {
            val uri = uriString.toUri()
            val dest = File(context.filesDir, "zenz_custom_model.gguf")

            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "openInputStream returned null for uri=$uri" }
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            return dest
        }

        val customUri = AppPreference.zenz_model_uri_preference

        // 1) まずユーザー指定があれば試す
        if (customUri.isNotBlank()) {
            try {
                val customFile = copyUriToInternalFile(customUri)
                ZenzEngine.initModel(customFile.absolutePath)
                Timber.d("Zenz model initialized with custom file: ${customFile.absolutePath}")
                return ZenzEngine
            } catch (e: Exception) {
                Timber.e(e, "Zenz Failed to init Zenz with custom model. Fallback to default.")
            }
        }

        // 2) デフォルトで初期化
        try {
            val defaultFile = ensureDefaultModelCopied()
            ZenzEngine.initModel(defaultFile.absolutePath)
            Timber.d("Zenz model initialized with default asset file: ${defaultFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Zenz Failed to init Zenz with default model as well.")
        }

        return ZenzEngine
    }
```

---

### ステップ 2: メイン設定画面への「zenzの設定」カテゴリ復元
Zenzの動作設定やオン/オフのトグルスイッチを表示するための設定項目カテゴリを、メイン設定画面に復活させます。

詳細な定義変更履歴は [REMOVED_PREFERENCES.md](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/REMOVED_PREFERENCES.md)（項目 8）に記載されています。

#### ① 設定リストへの再追加
[SettingMainFragment.kt](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/setting_activity/ui/setting/SettingMainFragment.kt) 内の `categories` 定義リストの任意の場所（通常は「辞書・学習」と「テンキー」の間など）に、以下の項目を再挿入します。

```kotlin
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.bolt_24dp,
                titleRes = R.string.zenz_preference_category_title,
                summaryRes = R.string.setting_category_zenz_summary,
                actionId = R.id.action_navigation_setting_to_zenzPreferenceFragment,
            ),
```

---

### ステップ 3: 共通設定の Zenz 関連項目の復元（必要に応じて）
共通設定（`pref_common.xml`）から削除した Zenz 関連の項目を元に戻します。
（詳細は [REMOVED_PREFERENCES.md](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/REMOVED_PREFERENCES.md) の項目 11 を参照してください）

#### ① 変換キー長押しでAI変換候補を追加する項目の復活
`app/src/main/res/xml/pref_common.xml` 内の「キーボード」カテゴリ等の任意の場所に以下を戻します。
```xml
        <SwitchPreferenceCompat
            android:defaultValue="false"
            android:key="conversion_key_long_press_ai_conversion_preference"
            android:summary="@string/pref_long_press_conversion_summary"
            android:title="@string/pref_long_press_conversion_title" />
```

---

## 3. 注意点・制限事項

### ① サポート対象アーキテクチャ (ABI) 制限
Zenz のネイティブ C++ ライブラリ (`llama.cpp`等) は、現在 **64ビットアーキテクチャ (`arm64-v8a`, `x86_64`) 専用** にコンパイルされるよう指定されています。
* **32ビット ARM (`armeabi-v7a`) 端末ではビルドが通りません**（FP16 関連の NEON 命令コンパイルエラー等が発生します）。
* Zenz 再有効化後は、テスト端末が 64ビット対応（`arm64-v8a`）であることを必ず確認してください。32ビット端末にインストールしようとすると、`INSTALL_FAILED_NO_MATCHING_ABIS` エラーでインストールに失敗します。

### ② デフォルトモデルアセットのサイズ
Zenz 再有効化時、`zenz` モジュールのアセットディレクトリ (`zenz/src/main/assets/`) に `ggml-model-Q5_K_M.gguf`（約21MB）が正しく配置されていることを確認してください。このモデルファイルが欠けていると、起動時に例外が発生してキーボードが正常に起動しない原因になります。
