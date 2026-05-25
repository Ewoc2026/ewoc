package com.example.ergometerapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.FitExportPreference
import com.example.ergometerapp.HrProfileSex
import com.example.ergometerapp.R
import com.example.ergometerapp.WebsiteGuideDestination

/**
 * PROFILE step content within the menu setup detail flow.
 *
 * Renders FTP input, HR profile (age/sex), and FIT export preference.
 */
@Composable
internal fun MenuProfileStepContent(
    state: ProfileSectionState,
    onFtpInputChanged: (String) -> Unit,
    onHrProfileAgeInputChanged: (String) -> Unit,
    onHrProfileSexSelected: (HrProfileSex) -> Unit,
    onFitExportPreferenceSelected: (FitExportPreference) -> Unit,
    onOpenBaselineFitnessTest: () -> Unit,
    isTwoColumn: Boolean = false,
    isCompact: Boolean = false,
) {
    if (isTwoColumn) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileFtpCard(state, onFtpInputChanged, isCompact)
                ProfileHrCard(state, onHrProfileAgeInputChanged, onHrProfileSexSelected, isCompact)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ProfileFitExportCard(state, onFitExportPreferenceSelected, isCompact)
                ProfileBaselineCard(state, onOpenBaselineFitnessTest, isCompact)
            }
        }
    } else {
        ProfileFtpCard(state, onFtpInputChanged)
        ProfileHrCard(state, onHrProfileAgeInputChanged, onHrProfileSexSelected)
        ProfileFitExportCard(state, onFitExportPreferenceSelected)
        ProfileBaselineCard(state, onOpenBaselineFitnessTest)
    }
}

@Composable
private fun ProfileFtpCard(
    state: ProfileSectionState,
    onFtpInputChanged: (String) -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(
        title = stringResource(R.string.menu_setup_step_profile_title),
        modifier = Modifier.animateContentSize(),
        compact = isCompact,
    ) {
        Text(
            text = stringResource(R.string.menu_setup_profile_hint),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.ftpInputText,
                onValueChange = onFtpInputChanged,
                modifier = Modifier.widthIn(min = 96.dp, max = 128.dp),
                placeholder = { Text("FTP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.ftpInputError != null,
                colors = menuTextFieldColors(),
            )
            Text(
                text = stringResource(R.string.menu_ftp_hint, state.ftpWatts),
                style = MaterialTheme.typography.bodySmall,
                color = normalTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.menu_ftp_definition_short),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Text(
            text = stringResource(R.string.menu_profile_help_ftp_update_later),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
        )
        if (state.ftpInputError != null) {
            Text(
                text = state.ftpInputError,
                style = MaterialTheme.typography.bodySmall,
                color = errorTextColor,
            )
        }
    }
}

@Composable
private fun ProfileHrCard(
    state: ProfileSectionState,
    onHrProfileAgeInputChanged: (String) -> Unit,
    onHrProfileSexSelected: (HrProfileSex) -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val errorTextColor = menuErrorTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = stringResource(R.string.menu_hr_profile_title), compact = isCompact) {
        Text(
            text = state.hrProfileSummary,
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.hrProfileAgeInput,
                onValueChange = onHrProfileAgeInputChanged,
                modifier = Modifier.weight(0.38f),
                label = { Text(stringResource(R.string.menu_hr_profile_age_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = state.hrProfileAgeError != null,
                colors = menuTextFieldColors(),
            )
            Row(
                modifier = Modifier.weight(0.62f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HrProfileSexButton(
                    label = stringResource(R.string.menu_hr_profile_sex_male),
                    selected = state.hrProfileSex == HrProfileSex.MALE,
                    onClick = { onHrProfileSexSelected(HrProfileSex.MALE) },
                    modifier = Modifier.weight(1f),
                )
                HrProfileSexButton(
                    label = stringResource(R.string.menu_hr_profile_sex_female),
                    selected = state.hrProfileSex == HrProfileSex.FEMALE,
                    onClick = { onHrProfileSexSelected(HrProfileSex.FEMALE) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (state.hrProfileAgeError != null) {
            Text(
                text = state.hrProfileAgeError,
                style = MaterialTheme.typography.bodySmall,
                color = errorTextColor,
            )
        }
        if (state.hrProfileAge == null || state.hrProfileSex == null) {
            Text(
                text = stringResource(R.string.menu_profile_help_hr_optional),
                style = MaterialTheme.typography.bodySmall,
                color = supportingTextColor,
            )
        }
    }
}

@Composable
private fun ProfileFitExportCard(
    state: ProfileSectionState,
    onFitExportPreferenceSelected: (FitExportPreference) -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()

    SectionCard(title = stringResource(R.string.menu_fit_export_preference_title), compact = isCompact) {
        Text(
            text = stringResource(
                R.string.menu_fit_export_preference_summary_value,
                state.fitExportPreferenceLabel,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Text(
            text = stringResource(R.string.menu_fit_export_preference_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    onFitExportPreferenceSelected(FitExportPreference.AUTO_SAVE)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.summary_fit_preference_auto_save),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = {
                    onFitExportPreferenceSelected(FitExportPreference.ASK_EVERY_TIME)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.summary_fit_preference_ask_every_time),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = {
                    onFitExportPreferenceSelected(FitExportPreference.DO_NOT_SAVE)
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.summary_fit_preference_do_not_save),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ProfileBaselineCard(
    state: ProfileSectionState,
    onOpenBaselineFitnessTest: () -> Unit,
    isCompact: Boolean = false,
) {
    val normalTextColor = menuNormalTextColor()
    val supportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard(title = stringResource(R.string.baseline_fitness_test_section_title), compact = isCompact) {
        Text(
            text = stringResource(R.string.baseline_fitness_test_section_hint),
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Text(
            text = stringResource(R.string.menu_profile_help_baseline_intro),
            style = MaterialTheme.typography.bodySmall,
            color = supportingTextColor,
        )
        EnglishOnlyGuideLink(destination = WebsiteGuideDestination.GETTING_STARTED)
        val lastResultText = if (state.baselineLastFtpWatts != null) {
            stringResource(
                R.string.baseline_fitness_test_last_result,
                state.baselineLastFtpWatts,
                state.baselineLastTestedAt ?: "",
            )
        } else {
            stringResource(R.string.baseline_fitness_test_no_prior_result)
        }
        Text(
            text = lastResultText,
            style = MaterialTheme.typography.bodySmall,
            color = normalTextColor,
        )
        Button(
            onClick = onOpenBaselineFitnessTest,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.baseline_fitness_test_run_button))
        }
    }
}
