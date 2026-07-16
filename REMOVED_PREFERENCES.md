# 削除された設定項目の一覧

アプリのリリースに向けて不要と判断され、非表示（削除）にされた設定項目を記録するドキュメントです。後から復元（再表示）する際の参考情報として、削除された定義やファイルをここに残します。

---

## 1. かな入力設定の「IME切替ボタン」

* **削除日**: 2026-06-24
* **削除対象ファイル**: [pref_kana.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_kana.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="tenkey_show_switch_ime_button_preference"
      android:summary="@string/qwerty_show_switch_ime_button_summary_on"
      android:title="@string/qwerty_show_switch_ime_button_title" />
  ```
* **備考**:
  IMEを切り替える地球儀ボタンの表示/非表示をユーザーが切り替えるトグル項目。削除されましたが、IMEService上での動作値としては、デフォルトの `true`（表示）が適用されたままになります。

---

## 2. QWERTY入力設定の「IME切替ボタン」

* **削除日**: 2026-06-24
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="qwerty_show_switch_ime_button_preference"
      android:summaryOff="@string/qwerty_show_switch_ime_button_summary_off"
      android:summaryOn="@string/qwerty_show_switch_ime_button_summary_on"
      android:title="@string/qwerty_show_switch_ime_button_title" />
  ```
* **備考**:
  上記のかな入力と同様に、QWERTY設定画面からも地球儀ボタン表示トグルが削除されました。こちらもデフォルトの `true`（表示）のままになります。

---

## 3. 共通設定の「ライブ変換」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="live_conversion_preference"
      android:summaryOff="@string/live_conversion_summary_off"
      android:summaryOn="@string/live_conversion_summary_on"
      android:title="@string/live_conversion_title" />
  ```
* **備考**:
  入力中のライブ変換機能を有効にするトグル項目。削除されましたが、IMEService上での動作値としては、デフォルトの `false`（無効）が適用されます。

---

## 4. 共通設定の「変換キー長押しでAI変換候補を追加」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="conversion_key_long_press_ai_conversion_preference"
      android:summary="@string/conversion_key_long_press_ai_conversion_preference_summary"
      android:title="@string/conversion_key_long_press_ai_conversion_preference_title" />
  ```
* **備考**:
  変換キーを長押しした際にAI変換候補を追加する機能を有効にするトグル項目。削除されましたが、デフォルトの `false`（無効）が適用されます。

---

## 5. 共通設定の「削除キーの左フリック」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="delete_key_flick_left_preference"
      android:summary="@string/delete_key_left_summary"
      android:title="@string/delete_key_left_flick_title" />
  ```
* **備考**:
  削除キーを左フリックした際に文頭や文末に向かって文字をまとめて削除する機能を有効にするトグル項目。削除されましたが、デフォルトの `true`（有効）が適用されます。

---

## 6. 共通設定の「修飾キー省略入力」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="omission_search_preference"
      android:summaryOff="@string/omission_search_summary_off"
      android:summaryOn="@string/omission_search_summary_on"
      android:title="@string/omission_search_title" />
  ```
* **備考**:
  濁点や小書き文字などの修飾キーの入力を省略して検索候補を出す機能を有効にするトグル項目。削除されましたが、デフォルトの `false`（無効）が適用されます。

---

## 7. 共通設定の「スペースキー長押しでカーソル移動」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="conversion_key_swipe_cursor_move_preference"
      android:summary="@string/pref_summary_space_longpress_cursor"
      android:title="@string/pref_title_space_longpress_cursor" />
  ```
* **備考**:
  スペースキー（変換キー）を長押し・スワイプすることでカーソルを移動する機能を有効にするトグル項目。削除されましたが、デフォルトの `false`（無効）が適用されます。

---

## 8. 設定メイン一覧の「zenzの設定」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [SettingMainFragment.kt](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/setting_activity/ui/setting/SettingMainFragment.kt)
* **削除された定義**:
  ```kotlin
  SettingCategory(
      iconRes = com.kazumaproject.core.R.drawable.baseline_emoji_emotions_24,
      titleRes = R.string.zenz_preference_category_title,
      summaryRes = R.string.setting_category_zenz_summary,
      actionId = R.id.action_navigation_setting_to_zenzPreferenceFragment,
  ),
  ```
* **備考**:
  メイン設定画面のリストから「zenzの設定」カテゴリを非表示にしました。

---

## 9. 設定メイン一覧の「カスタムキーボード設定」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [SettingMainFragment.kt](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/java/com/kazumaproject/markdownhelperkeyboard/setting_activity/ui/setting/SettingMainFragment.kt)
* **削除された定義**:
  ```kotlin
  SettingCategory(
      iconRes = com.kazumaproject.core.R.drawable.ic_custom_icon,
      titleRes = R.string.category_custom_keyboard_title,
      summaryRes = R.string.setting_category_custom_summary,
      actionId = R.id.action_navigation_setting_to_customKeyboardPreferenceFragment,
  ),
  ```
* **備考**:
  メイン設定画面のリストから「カスタムキーボード」カテゴリを非表示にしました。

---

## 10. ボトムナビゲーションの「カスタムキーボード」タブ

* **削除日**: 2026-07-12
* **削除対象ファイル**: [bottom_nav_menu.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/menu/bottom_nav_menu.xml)
* **削除された定義**:
  ```xml
  <item
      android:id="@+id/keyboardListFragment"
      android:icon="@drawable/keyboard_24px"
      android:title="@string/custom_keyboard_fragment_label" />
  ```
* **備考**:
  設定アプリの下部ボトムナビゲーションバーから「カスタムキーボード」切り替えタブを非表示にしました。

---

## 11. 共通設定の「ショートカットツールバーの見出し」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <PreferenceCategory android:title="@string/shortcutsettingfragment">
      ...
  </PreferenceCategory>
  ```
* **備考**:
  「ショートカットツールバーのカスタマイズ」設定項目を「ショートカットツールバー（表示/非表示）」の直後に移動したため、不要になった見出しカテゴリを削除しました。

---

## 12. 共通設定の「キーボードの選択」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <Preference
      android:icon="@drawable/outline_border_color_24"
      android:key="keyboard_selection_preference"
      android:summary="@string/keyboard_selection_summary"
      android:title="@string/keyboard_selection_preference_title" />
  ```
* **備考**:
  「キーボードの選択」設定項目を設定のメイン画面（カテゴリ一覧）の上部に直接表示させるよう変更したため、共通設定画面（`pref_common.xml` および `CommonPreferenceFragment.kt`）から項目を削除・移動しました。

---

## 13. 共通設定の「アプリについて」

* **削除日**: 2026-07-12
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <PreferenceCategory android:title="@string/category_about_app_title">
      <Preference
          android:key="preference_open_source"
          android:title="@string/open_source_libraries_title" />
      <Preference
          android:key="app_version_preference"
          android:title="@string/app_version_title" />
  </PreferenceCategory>
  ```
* **備考**:
  「アプリについて」設定項目をメイン設定画面の末尾へ移動し、タップすることで独立した「アプリについて」設定画面（`pref_about.xml` / `AboutPreferenceFragment.kt`）が開くよう変更しました。これに伴い、共通設定画面から項目を削除・移動しました。

---

## 14. QWERTY入力設定の「上フリック」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="qwerty_enable_flick_up_preference"
      android:summary="@string/pref_qwerty_enable_flick_up_summary"
      android:title="@string/pref_qwerty_enable_flick_up_title" />
  ```
* **備考**:
  QWERTYキーボードで上フリックによる入力を有効にするトグル項目。削除されましたが、デフォルトの `false`（無効）が適用されます。

---

## 15. QWERTY入力設定の「下フリック」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="qwerty_enable_flick_down_preference"
      android:summary="@string/pref_qwerty_enable_flick_down_summary"
      android:title="@string/pref_qwerty_enable_flick_down_title" />
  ```
* **備考**:
  QWERTYキーボードで下フリックによる入力を有効にするトグル項目。削除されましたが、デフォルトの `false`（無効）が適用されます。

---

## 16. QWERTY入力設定の「カーソルキーの表示」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="qwerty_show_cursor_buttons_preference"
      android:summaryOff="@string/qwerty_show_cursor_buttons_summary_off"
      android:summaryOn="@string/qwerty_show_cursor_buttons_summary_on"
      android:title="@string/qwerty_show_cursor_buttons_title" />
  ```
* **備考**:
  QWERTYキーボードで左右カーソル移動キーを表示するトグル項目。削除されましたが、デフォルトの `true`（有効・表示）が適用されます。

---

## 17. QWERTY入力設定の「句読点キーの表示」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="qwerty_show_kutouten_buttons_preference"
      android:summaryOff="@string/qwerty_show_kutouten_buttons_summary_off"
      android:summaryOn="@string/qwerty_show_kutouten_buttons_summary_on"
      android:title="@string/qwerty_show_kutouten_buttons_title" />
  ```
* **備考**:
  QWERTYキーボードで句読点キーを表示するトグル項目。削除されましたが、デフォルトの `false`（無効・非表示）が適用されます。

---

## 18. QWERTY入力設定の「数字キーの表示」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="qwerty_show_number_keys_buttons_preference"
      android:summaryOff="@string/qwerty_number_keys_summary_off"
      android:summaryOn="@string/qwerty_number_keys_summary_on"
      android:title="@string/qwerty_number_keys_title" />
  ```
* **備考**:
  QWERTYキーボードの上部に数字キーを常時表示するトグル項目。削除されましたが、デフォルトの `false`（無効・非表示）が適用されます。

---

## 19. QWERTY入力設定の「記号キーマップの表示」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="false"
      android:key="qwerty_show_keymap_symbols_romaji_preference"
      android:summaryOff="@string/qwerty_keymap_preference_summary_off"
      android:summaryOn="@string/qwerty_keymap_preference_summary_on"
      android:title="@string/qwerty_keymap_symbol_preference" />
  ```
* **備考**:
  ローマ字入力時などに記号のキーマップをオーバーレイ表示するトグル項目。削除されましたが、デフォルトの `false`（無効・非表示）が適用されます。

---

## 20. QWERTY入力設定の「ローマ字と英語を切替るキーの表示」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:key="qwerty_show_switch_romaji_english_preference"
      android:title="@string/qwerty_romaji_english_visibility_title"
      app:defaultValue="false"
      app:summary="@string/qwerty_romaji_english_visibility_summary" />
  ```
* **備考**:
  ローマ字と英語を切り替える専用の物理的なキーの表示/非表示を設定するトグル項目。削除されましたが、以前デフォルト値を変更したため、デフォルトの `false`（非表示）が適用されます。

---

## 21. QWERTY入力設定の「シフト直後の1文字だけを大文字にする」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_qwerty.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_qwerty.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:key="qwerty_romaji_shift_conversion_preference"
      android:title="@string/pref_romaji_shift_title"
      app:defaultValue="false"
      app:summary="@string/pref_romaji_shift_summary" />
  ```
* **備考**:
  シフトキーを押した直後の1文字目だけを自動的に大文字にするオートキャピタライズ機能を有効にするトグル項目。削除されましたが、デフォルトの `false`（無効）が適用されます。

---

## 22. アヤメキーボード入力設定の「入力スタイル」

* **削除日**: 2026-07-13
* **削除対象ファイル**: [pref_sumire.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_sumire.xml)
* **削除された定義**:
  ```xml
  <ListPreference
      android:defaultValue="default"
      android:dialogTitle="@string/sumire_keyboard_style_dialog_title"
      android:entries="@array/sumire_keyboard_style_options"
      android:entryValues="@array/sumire_keyboard_style_values"
      android:key="sumire_keyboard_style_preference"
      android:summary="%s"
      android:title="@string/sumire_keyboard_style_title" />
  ```
* **備考**:
  アヤメ（スミレ）キーボードの入力スタイル（デフォルト/円形など）を選択するリスト項目。削除されましたが、デフォルトの `"default"`（デフォルトスタイル）が適用されます。

---

## 23. 共通設定の「フリック速度判定を有効にする」

* **削除日**: 2026-07-16
* **削除対象ファイル**: [pref_common.xml](file:///c:/Users/nyama/OneDrive/APPS/JapaneseKeyboard/app/src/main/res/xml/pref_common.xml)
* **削除された定義**:
  ```xml
  <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="flick_velocity_filter_preference"
      android:summary="@string/flick_velocity_filter_summary"
      android:title="@string/flick_velocity_filter_title" />
  ```
* **備考**:
  フリック時の速度判定を有効にするトグルスイッチ項目。設定項目は削除されましたが、コード上で常に `true` (判定を有効にする) として動作するように標準化（固定化）されました。
