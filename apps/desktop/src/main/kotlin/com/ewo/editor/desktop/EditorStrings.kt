package com.ewo.editor.desktop

import java.util.Locale
import java.util.ResourceBundle

/**
 * Thin wrapper around a [ResourceBundle] for desktop editor UI strings.
 * Falls back to English when the current locale has no translation.
 */
object EditorStrings {

    private val bundle: ResourceBundle
        get() = ResourceBundle.getBundle("strings.EditorStrings", Locale.getDefault())

    val selectMultiple: String get() = bundle.getString("select_multiple")
    val tagFieldLabel: String get() = bundle.getString("tag_field_label")
    val tagFieldErrorCommas: String get() = bundle.getString("tag_field_error_commas")
    val tagFieldErrorAllowedChars: String get() = bundle.getString("tag_field_error_allowed_chars")

    // Tooltip strings
    val tooltipTargetWatts: String get() = bundle.getString("tooltip_target_watts")
    val tooltipTargetFtp: String get() = bundle.getString("tooltip_target_ftp")
    val tooltipTargetHr: String get() = bundle.getString("tooltip_target_hr")
    val tooltipTargetHrRel: String get() = bundle.getString("tooltip_target_hr_rel")
    val tooltipCadenceAdd: String get() = bundle.getString("tooltip_cadence_add")
    val tooltipCadenceClear: String get() = bundle.getString("tooltip_cadence_clear")
    val tooltipAddSteady: String get() = bundle.getString("tooltip_add_steady")
    val tooltipAddRamp: String get() = bundle.getString("tooltip_add_ramp")
    val tooltipAddRampDown: String get() = bundle.getString("tooltip_add_ramp_down")
    val tooltipAddFreeride: String get() = bundle.getString("tooltip_add_freeride")
    val tooltipAddRepeat: String get() = bundle.getString("tooltip_add_repeat")
    val tooltipMoveUp: String get() = bundle.getString("tooltip_move_up")
    val tooltipMoveDown: String get() = bundle.getString("tooltip_move_down")
    val tooltipDuplicate: String get() = bundle.getString("tooltip_duplicate")
    val tooltipCopy: String get() = bundle.getString("tooltip_copy")
    val tooltipUnwrap: String get() = bundle.getString("tooltip_unwrap")
    val tooltipDelete: String get() = bundle.getString("tooltip_delete")
    val tooltipChartClick: String get() = bundle.getString("tooltip_chart_click")
    val tooltipMultiSelect: String get() = bundle.getString("tooltip_multi_select")
    val tooltipAnchorStart: String get() = bundle.getString("tooltip_anchor_start")
    val tooltipAnchorEnd: String get() = bundle.getString("tooltip_anchor_end")
    val tooltipExpand: String get() = bundle.getString("tooltip_expand")
    val tooltipSearch: String get() = bundle.getString("tooltip_search")

    /** Returns the tooltip for a target type button code (W, %FTP, HR, HR%). */
    fun tooltipForTargetType(type: String): String = when (type) {
        "W" -> tooltipTargetWatts
        "%FTP" -> tooltipTargetFtp
        "HR" -> tooltipTargetHr
        "HR%" -> tooltipTargetHrRel
        else -> ""
    }

    fun tagFieldGuidance(maxCount: Int): String =
        interpolate("tag_field_guidance", "maxCount" to maxCount.toString())

    fun tagFieldErrorInvalidTag(tag: String): String =
        interpolate("tag_field_error_invalid_tag", "tag" to tag)

    fun tagFieldErrorTooMany(maxCount: Int): String =
        interpolate("tag_field_error_too_many", "maxCount" to maxCount.toString())

    private fun interpolate(key: String, vararg replacements: Pair<String, String>): String {
        return replacements.fold(bundle.getString(key)) { text, (name, value) ->
            text.replace("{$name}", value)
        }
    }
}
