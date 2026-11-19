package io.github.ewoc2026.ewoc.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ewoc2026.ewoc.BuildConfig
import io.github.ewoc2026.ewoc.LocalizedWebLinkLauncher
import io.github.ewoc2026.ewoc.R

/** Keeps feedback usable without a GitHub account or an installed email app. */
@Composable
internal fun MenuAboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val address = stringResource(R.string.about_feedback_email)
    val feedbackLabel = stringResource(R.string.menu_about_feedback)
    val openLinkFailed = stringResource(R.string.menu_about_open_link_failed)
    var emailOpenFailed by rememberSaveable { mutableStateOf(false) }

    fun openUrl(url: String) {
        if (!LocalizedWebLinkLauncher.open(context = context, url = url)) {
            Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT).show()
        }
    }

    val supportUrl = stringResource(R.string.about_url_support)
    val changelogUrl = stringResource(R.string.about_url_changelog)
    val userGuideUrl = stringResource(R.string.about_url_user_guide)
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Let the title scroll too so large text cannot consume a short window.
                Text(
                    text = stringResource(R.string.menu_about_title) + " " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
                )
                Text(feedbackLabel, style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("aboutEmailAddress"),
                    )
                }
                Button(
                    onClick = {
                        // Launch directly: package-visibility filtering must not hide an email app.
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address, null))
                        emailOpenFailed = try {
                            context.startActivity(intent)
                            false
                        } catch (_: ActivityNotFoundException) {
                            true
                        } catch (_: SecurityException) {
                            true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("aboutEmailButton"),
                ) {
                    Text(stringResource(R.string.menu_about_email))
                }
                OutlinedButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText(feedbackLabel, address))
                        // The supported Android 13+ versions provide clipboard confirmation.
                    },
                    modifier = Modifier.fillMaxWidth().testTag("aboutCopyEmailButton"),
                ) {
                    Text(stringResource(R.string.menu_about_copy_email))
                }
                if (emailOpenFailed) {
                    Text(
                        text = stringResource(R.string.menu_about_email_open_failed),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .testTag("aboutEmailError")
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                TextButton(
                    onClick = { openUrl(supportUrl) },
                    modifier = Modifier.testTag("aboutGitHubButton"),
                ) {
                    Text(
                        text = stringResource(R.string.menu_about_support),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                TextButton(onClick = { openUrl(changelogUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_changelog),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { openUrl(userGuideUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_user_guide),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { openUrl(privacyPolicyUrl) }) {
                    Text(
                        text = stringResource(R.string.menu_about_privacy_policy),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        // Dismissal stays outside scrolling content, including in short landscape windows.
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("aboutCloseButton")) {
                Text(stringResource(R.string.menu_dialog_ok))
            }
        },
    )
}
