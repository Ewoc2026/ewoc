package com.example.ergometerapp.ui

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ergometerapp.LocalizedWebLinkLauncher
import com.example.ergometerapp.R
import com.example.ergometerapp.WebsiteGuideDestination
import com.example.ergometerapp.WebsiteGuideLinks

/**
 * Low-emphasis website-help link used beside localized in-app helper copy.
 *
 * The label intentionally keeps the `English only` marker visible before the browser opens.
 */
@Composable
internal fun EnglishOnlyGuideLink(
    destination: WebsiteGuideDestination,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val openLinkFailed = stringResource(R.string.menu_about_open_link_failed)

    TextButton(
        onClick = {
            val opened = LocalizedWebLinkLauncher.open(
                context = context,
                url = WebsiteGuideLinks.resolve(destination),
            )
            if (!opened) {
                Toast.makeText(context, openLinkFailed, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(
            text = stringResource(R.string.menu_help_learn_more_english_only),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Start,
        )
    }
}
