package com.reader343.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.R
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.heroFill
import com.reader343.ui.components.riseIn
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes

@Composable
fun EmptyLibraryContent(
    importing: Boolean,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 40.dp, end = 28.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .heroFill(MaterialTheme.appShapes.emblem),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ph_book_open_text),
                    contentDescription = null,
                    tint = colors.accLt,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = stringResource(R.string.library_first_title),
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 26.sp),
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.library_first_body),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                color = colors.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 280.dp),
            )
            PrimaryButton(
                text = stringResource(if (importing) R.string.library_importing else R.string.library_import_a_pdf),
                onClick = onImport,
                icon = R.drawable.ic_ph_file_plus,
                enabled = !importing,
                compact = true,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .heightIn(min = 48.dp),
            )
        }
        AppCard(
            shape = MaterialTheme.appShapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp)
                .riseIn(delayMs = 60),
        ) {
            OnboardingSteps.forEachIndexed { index, step ->
                if (index > 0) HorizontalDivider(color = colors.line)
                OnboardingRow(step)
            }
        }
    }
}

@Composable
private fun OnboardingRow(step: OnboardingStep) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(colors.surf2, MaterialTheme.appShapes.iconTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(step.icon),
                contentDescription = null,
                tint = colors.accLt,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(step.title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
            )
            Text(
                text = stringResource(step.body),
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = colors.ink3,
            )
        }
    }
}

private class OnboardingStep(@param:DrawableRes val icon: Int, @param:StringRes val title: Int, @param:StringRes val body: Int)

private val OnboardingSteps = listOf(
    OnboardingStep(R.drawable.ic_ph_file_pdf, R.string.onboarding_import_title, R.string.onboarding_import_body),
    OnboardingStep(R.drawable.ic_ph_target, R.string.onboarding_goal_title, R.string.onboarding_goal_body),
    OnboardingStep(R.drawable.ic_ph_highlighter, R.string.onboarding_highlight_title, R.string.onboarding_highlight_body),
)

@Preview(showBackground = true)
@Composable
private fun EmptyLibraryPreview() {
    Reader343Theme {
        EmptyLibraryContent(importing = false, onImport = {})
    }
}
