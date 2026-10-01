package com.example.dimanow.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/** A node whose click action is announced with [label] ("double-tap to <label>"). */
internal fun hasClickLabel(label: String) = SemanticsMatcher("OnClick label = $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}

internal fun hasStateDescription(value: String) =
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)
