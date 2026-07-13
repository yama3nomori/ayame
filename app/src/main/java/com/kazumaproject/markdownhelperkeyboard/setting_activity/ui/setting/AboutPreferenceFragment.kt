package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kazumaproject.markdownhelperkeyboard.R

class AboutPreferenceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_about, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val packageInfo = requireContext().packageManager.getPackageInfo(
            requireContext().packageName, 0
        )

        val appVersionPreference = findPreference<Preference>("app_version_preference")
        appVersionPreference?.apply {
            summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.longVersionCode}"
            } else {
                "version name: ${packageInfo.versionName}\nversion code: ${packageInfo.versionCode}"
            }
        }

        val openSourcePreference = findPreference<Preference>("preference_open_source")
        openSourcePreference?.setOnPreferenceClickListener {
            findNavController().navigate(
                R.id.action_aboutPreferenceFragment_to_openSourceFragment
            )
            true
        }
    }

    // リーク対策: RecyclerViewの参照を断ち切る
    override fun onDestroyView() {
        try {
            listView.adapter = null
        } catch (e: Exception) {
            // Viewが生成されていない場合などを考慮して例外は無視
        }
        super.onDestroyView()
    }
}
