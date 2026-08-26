package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme.KeyboardThemeFragment

class SettingPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 8

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CommonPreferenceFragment()
            1 -> KeyboardThemeFragment()
            2 -> ZenzPreferenceFragment()
            3 -> DictionaryPreferenceFragment()
            4 -> KanaPreferenceFragment()
            5 -> QwertyPreferenceFragment()
            6 -> CustomKeyboardPreferenceFragment()
            7 -> TabletPreferenceFragment()
            else -> CommonPreferenceFragment()
        }
    }
}
