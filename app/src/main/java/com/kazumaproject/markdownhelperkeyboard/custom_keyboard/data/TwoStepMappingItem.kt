package com.kazumaproject.markdownhelperkeyboard.custom_keyboard.data

import com.kazumaproject.custom_keyboard.controller.TfbiFlickDirection
import java.util.UUID

data class TwoStepMappingItem(
    val id: String = UUID.randomUUID().toString(),
    val first: TfbiFlickDirection,
    val second: TfbiFlickDirection,
    val output: String
)
