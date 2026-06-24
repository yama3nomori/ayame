package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_selection

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentKeyboardSelectionBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.state.KeyboardType
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSelectionFragment : Fragment() {

    private var _binding: FragmentKeyboardSelectionBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreferences: AppPreference

    private val viewModel: KeyboardSelectionViewModel by viewModels()

    private lateinit var keyboardSelectionAdapter: KeyboardSelectionAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKeyboardSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMenu()
        setupRecyclerView()
        setupAddButton()
        setupNumericKeyboardSpinner()
        observeUiState()

        if (savedInstanceState == null) {
            viewModel.setInitialKeyboards(appPreferences.keyboard_order)
        }
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.keyboard_selection_menu, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                val editItem = menu.findItem(R.id.action_edit)
                val isEditing = viewModel.uiState.value.isEditing
                editItem.title =
                    if (isEditing) getString(com.kazumaproject.core.R.string.done_text) else getString(
                        R.string.edit_text
                    )
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_edit -> {
                        viewModel.toggleEditMode()
                        // If we are finishing editing, save the latest state
                        if (!viewModel.uiState.value.isEditing) {
                            appPreferences.keyboard_order = viewModel.uiState.value.keyboards
                        }
                        true
                    }

                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun setupRecyclerView() {
        keyboardSelectionAdapter = KeyboardSelectionAdapter(
            onStartDrag = { viewHolder ->
                if (viewModel.uiState.value.isEditing) {
                    itemTouchHelper.startDrag(viewHolder)
                }
            },
            onDeleteClick = { position ->
                val currentList = viewModel.uiState.value.keyboards.toMutableList()
                if (currentList.size > 1) {
                    currentList.removeAt(position)
                    viewModel.updateKeyboardOrder(currentList)
                }
            },
            onKeyboardClick = { position, keyboardType ->
                if (viewModel.uiState.value.isEditing) {
                    val options = arrayOf("上に移動", "下に移動", "先頭に移動", "最後に移動")
                    AlertDialog.Builder(requireContext())
                        .setTitle(getKeyboardDisplayName(keyboardType))
                        .setItems(options) { dialog, which ->
                            val list = viewModel.uiState.value.keyboards.toMutableList()
                            when (which) {
                                0 -> { // 上に移動
                                    if (position > 0) {
                                        Collections.swap(list, position, position - 1)
                                        viewModel.updateKeyboardOrder(list)
                                    }
                                }
                                1 -> { // 下に移動
                                    if (position < list.size - 1) {
                                        Collections.swap(list, position, position + 1)
                                        viewModel.updateKeyboardOrder(list)
                                    }
                                }
                                2 -> { // 先頭に移動
                                    if (position != 0) {
                                        val item = list.removeAt(position)
                                        list.add(0, item)
                                        viewModel.updateKeyboardOrder(list)
                                    }
                                }
                                3 -> { // 最後に移動
                                    if (position != list.size - 1) {
                                        val item = list.removeAt(position)
                                        list.add(item)
                                        viewModel.updateKeyboardOrder(list)
                                    }
                                }
                            }
                            dialog.dismiss()
                        }
                        .show()
                }
            },
            onAccessibilityAction = { position, action ->
                val list = viewModel.uiState.value.keyboards.toMutableList()
                when (action) {
                    KeyboardSelectionAdapter.AccessibilityAction.MOVE_UP -> {
                        if (position > 0) {
                            Collections.swap(list, position, position - 1)
                        }
                    }
                    KeyboardSelectionAdapter.AccessibilityAction.MOVE_DOWN -> {
                        if (position < list.size - 1) {
                            Collections.swap(list, position, position + 1)
                        }
                    }
                    KeyboardSelectionAdapter.AccessibilityAction.MOVE_TOP -> {
                        if (position != 0) {
                            val item = list.removeAt(position)
                            list.add(0, item)
                        }
                    }
                    KeyboardSelectionAdapter.AccessibilityAction.MOVE_BOTTOM -> {
                        if (position != list.size - 1) {
                            val item = list.removeAt(position)
                            list.add(item)
                        }
                    }
                }
                viewModel.updateKeyboardOrder(list)
            }
        )

        binding.keyboardSelectionView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = keyboardSelectionAdapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                val currentList = viewModel.uiState.value.keyboards.toMutableList()
                Collections.swap(currentList, fromPosition, toPosition)
                viewModel.updateKeyboardOrder(currentList)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false // Disable default drag
            override fun isItemViewSwipeEnabled(): Boolean = false // Disable swipe
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.keyboardSelectionView)
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    keyboardSelectionAdapter.submitList(state.keyboards)
                    keyboardSelectionAdapter.setEditMode(state.isEditing)
                    
                    val visibility = if (state.isEditing) View.GONE else View.VISIBLE
                    binding.addNewKeyboardDialogTrigger.visibility = visibility
                    binding.numericKeyboardSettingTitle.visibility = visibility
                    binding.numericKeyboardSettingSpinner.visibility = visibility

                    // Make the menu redraw itself to update the text
                    requireActivity().invalidateOptionsMenu()
                }
            }
        }
    }

    private fun setupAddButton() {
        binding.addNewKeyboardDialogTrigger.setOnClickListener {
            showAddKeyboardDialog()
        }
    }

    private fun showAddKeyboardDialog() {
        val allKeyboardTypes = KeyboardType.entries.filter { it != KeyboardType.SUMIRE && it != KeyboardType.NUMERIC && it != KeyboardType.AYAME_NUMERIC }.toTypedArray()
        val currentKeyboards = viewModel.uiState.value.keyboards
        val availableKeyboards = allKeyboardTypes.filter { it !in currentKeyboards }

        if (availableKeyboards.isEmpty()) return

        val dialogItems = availableKeyboards.map { getKeyboardDisplayName(it) }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(com.kazumaproject.core.R.string.add_keyboard_dialog_title)
            .setItems(dialogItems) { dialog, which ->
                val selectedKeyboard = availableKeyboards[which]
                val newList = currentKeyboards.toMutableList().apply { add(selectedKeyboard) }
                viewModel.updateKeyboardOrder(newList)
                dialog.dismiss()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Save the latest state when the user leaves the screen
        appPreferences.keyboard_order = viewModel.uiState.value.keyboards
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupNumericKeyboardSpinner() {
        val options = listOf(
            getString(R.string.numeric_keyboard_type_standard),
            getString(R.string.numeric_keyboard_type_ayame)
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            options
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.numericKeyboardSettingSpinner.adapter = adapter

        // 初期選択状態の設定
        val currentType = appPreferences.numeric_keyboard_type
        val initialPosition = if (currentType == "ayame_numeric") 1 else 0
        binding.numericKeyboardSettingSpinner.setSelection(initialPosition)

        // 選択変更イベントリスナーの登録
        binding.numericKeyboardSettingSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedType = if (position == 1) "ayame_numeric" else "numeric"
                appPreferences.numeric_keyboard_type = selectedType
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}

fun getKeyboardDisplayName(keyboardType: KeyboardType): String {
    return when (keyboardType) {
        KeyboardType.TENKEY -> "日本語 - かな"
        KeyboardType.QWERTY -> "英語(QWERTY)"
        KeyboardType.ROMAJI -> "日本語 - ローマ字"
        KeyboardType.SUMIRE -> "日本語 - スミレ入力 β"
        KeyboardType.CUSTOM -> "カスタム - ユーザー定義"
        KeyboardType.AYAME_TENKEY -> "アヤメテンキー"
        KeyboardType.AYAME_QWERTY -> "アヤメ英語(QWERTY)"
        KeyboardType.AYAME_ROMAJI -> "アヤメ日本語 - ローマ字"
        KeyboardType.NUMERIC -> "数字専用キーボード"
        KeyboardType.AYAME_NUMERIC -> "アヤメ数字専用キーボード"
    }
}
