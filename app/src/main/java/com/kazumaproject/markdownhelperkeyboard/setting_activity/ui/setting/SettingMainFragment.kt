package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingMainFragment : Fragment() {

    private var _binding: FragmentSettingMainBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val romajiMapUpdated = appPreference.romaji_map_data_version
        lifecycleScope.launch(Dispatchers.IO) {
            if (romajiMapUpdated == 0) {
                romajiMapRepository.updateDefaultMap()

                userDictionaryRepository.apply {
                    if (searchByReadingExactMatchSuspend("びゃんびゃんめん").isEmpty()) {
                        insert(
                            UserWord(
                                reading = "びゃんびゃんめん",
                                word = "\uD883\uDEDE\uD883\uDEDE麺",
                                posIndex = 0,
                                posScore = 4000
                            )
                        )
                    }
                    if (searchByReadingExactMatchSuspend("びゃん").isEmpty()) {
                        insert(
                            UserWord(
                                reading = "びゃん", word = "\uD883\uDEDE", posIndex = 0, posScore = 3000
                            )
                        )
                    }
                }

                appPreference.romaji_map_data_version = 1
            }
        }

        // 設定カテゴリ一覧を定義
        val categories = listOf(
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.keyboard_24px,
                titleRes = R.string.keyboard_selection_preference_title,
                summaryRes = R.string.keyboard_selection_summary,
                actionId = R.id.action_navigation_setting_to_keyboardSelectionFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.baseline_settings_24,
                titleRes = R.string.category_common,
                summaryRes = R.string.setting_category_common_summary,
                actionId = R.id.action_navigation_setting_to_commonPreferenceFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.keyboard_24px,
                titleRes = R.string.keyboardthemefragment,
                summaryRes = R.string.setting_category_theme_summary,
                actionId = R.id.action_navigation_setting_to_keyboardThemeFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.dictionary_24px,
                titleRes = R.string.category_dictionary,
                summaryRes = R.string.setting_category_dictionary_summary,
                actionId = R.id.action_navigation_setting_to_dictionaryPreferenceFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.language_japanese_kana_24px,
                titleRes = R.string.category_kana,
                summaryRes = R.string.setting_category_kana_summary,
                actionId = R.id.action_navigation_setting_to_kanaPreferenceFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.keyboard_24px,
                titleRes = R.string.qwertymarginsettingfragment,
                summaryRes = R.string.setting_category_qwerty_summary,
                actionId = R.id.action_navigation_setting_to_qwertyPreferenceFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.kana_small,
                titleRes = R.string.category_sumire_input_keyboard_title,
                summaryRes = R.string.setting_category_sumire_summary,
                actionId = R.id.action_navigation_setting_to_sumirePreferenceFragment,
            ),
            SettingCategory(
                iconRes = com.kazumaproject.core.R.drawable.question_mark_24dp,
                titleRes = R.string.category_about_app_title,
                summaryRes = R.string.category_about_app_title,
                actionId = R.id.action_navigation_setting_to_aboutPreferenceFragment,
            ),
        )

        val adapter = SettingCategoryAdapter(categories) { category ->
            findNavController().navigate(category.actionId)
        }

        binding.settingCategoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // バックスタックに戻れる先があればNavControllerで戻る
                    // なければ（この画面がルート）アクティビティを終了する
                    val navController = findNavController()
                    if (!navController.popBackStack()) {
                        requireActivity().finish()
                    }
                }
            })
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            binding.settingProgressBar.isVisible = true
            val enabled = withContext(Dispatchers.IO) {
                isKeyboardBoardEnabled()
            }
            binding.settingProgressBar.isVisible = false
            if (enabled == false) {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_enableKeyboardFragment
                )
            }
        }
    }

    override fun onDestroyView() {
        // リーク対策: RecyclerViewのアダプター参照を断つ
        binding.settingCategoryRecyclerView.adapter = null

        super.onDestroyView()
        _binding = null
    }

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
    }
}
