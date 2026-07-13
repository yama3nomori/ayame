package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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

        val tamachiPreference = findPreference<Preference>("preference_tamachi_reading")
        tamachiPreference?.setOnPreferenceClickListener {
            val dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.tamachi_reading_title)
                .setMessage(R.string.tamachi_reading_dialog_message)
                .setPositiveButton(android.R.string.ok, null)
                .create()
            dialog.show()
            val messageView = dialog.findViewById<TextView>(android.R.id.message)
            if (messageView != null) {
                messageView.movementMethod = LinkMovementMethod.getInstance()
                Linkify.addLinks(messageView, Linkify.WEB_URLS)
            }
            true
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
