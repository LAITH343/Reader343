package com.reader343.ui.update

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.data.repo.UpdateStatus
import com.reader343.ui.components.AppTopBar
import com.reader343.ui.components.GhostButton
import com.reader343.ui.components.HeroCard
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.ProgressBar
import com.reader343.ui.components.SecondaryButton
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.formatDate
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatPercent
import com.reader343.ui.components.formatRelative
import com.reader343.ui.components.riseIn
import com.reader343.ui.settings.rememberNotificationAccess
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import com.reader343.update.AndroidVersions
import com.reader343.update.ApkAsset
import com.reader343.update.ApkInstaller
import com.reader343.update.ChangeEntry
import com.reader343.update.ChangeKind
import com.reader343.update.DownloadError
import com.reader343.update.DownloadState
import com.reader343.update.InstalledApp
import com.reader343.update.Release
import com.reader343.update.UpdateStage
import com.reader343.update.etaSeconds
import com.reader343.update.stage
import java.io.File
import java.time.Instant
import java.time.ZoneId

@Composable
fun UpdateRoute(
    onBack: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifications = rememberNotificationAccess()
    var pendingInstall by remember { mutableStateOf<File?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val file = pendingInstall
        pendingInstall = null
        if (file != null && ApkInstaller.canInstall(context)) {
            context.startActivity(ApkInstaller.installIntent(context, file))
        }
    }

    UpdateScreen(
        state = state,
        actions = UpdateActions(
            onBack = onBack,
            onCheck = viewModel::check,
            onDownload = { notifications.request { viewModel.download() } },
            onDownloadMetered = { notifications.request { viewModel.download(allowMetered = true) } },
            onPause = viewModel::pause,
            onCancel = viewModel::cancel,
            onInstall = { file ->
                if (ApkInstaller.canInstall(context)) {
                    context.startActivity(ApkInstaller.installIntent(context, file))
                } else {
                    pendingInstall = file
                    permissionLauncher.launch(ApkInstaller.permissionIntent(context))
                }
            },
        ),
    )
}

@Immutable
class UpdateActions(
    val onBack: () -> Unit,
    val onCheck: () -> Unit,
    val onDownload: () -> Unit,
    val onDownloadMetered: () -> Unit,
    val onPause: () -> Unit,
    val onCancel: () -> Unit,
    val onInstall: (File) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    state: UpdateStatus?,
    actions: UpdateActions,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
        topBar = { AppTopBar(title = stringResource(R.string.update_title), onBack = actions.onBack) },
    ) { padding ->
        if (state == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        val release = state.release
        val changelog = release?.changelog.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item(key = "hero") {
                StatusCard(
                    state = state,
                    actions = actions,
                    modifier = Modifier.padding(start = ScreenPadding, top = 12.dp, end = ScreenPadding),
                )
            }
            if (release != null && changelog.isNotEmpty()) {
                item(key = "whats_new") { WhatsNewHeader(release) }
                itemsIndexed(changelog) { index, entry ->
                    ChangeCard(
                        entry = entry,
                        modifier = Modifier
                            .padding(start = ScreenPadding, top = 10.dp, end = ScreenPadding)
                            .riseIn(delayMs = index * 40),
                    )
                }
            }
            item(key = "installed") {
                InstalledCard(
                    installed = state.installed,
                    previous = state.available,
                    modifier = Modifier.padding(start = ScreenPadding, top = 14.dp, end = ScreenPadding),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: UpdateStatus,
    actions: UpdateActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val stage = state.stage()
    val copy = stageCopy(state, stage)
    HeroCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.appShapes.hero,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(colors.accLtTint16, MaterialTheme.appShapes.badgeTile)
                    .border(1.dp, colors.heroLine, MaterialTheme.appShapes.badgeTile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(copy.icon),
                    contentDescription = null,
                    tint = colors.accTx,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = copy.kicker.uppercase(currentLocale()),
                    style = MaterialTheme.appType.kicker.copy(fontSize = 12.sp),
                    color = colors.accLt,
                )
                Text(
                    text = copy.headline,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 21.sp),
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text = copy.sub, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
            }
        }

        TransferBlock(state = state, stage = stage)

        FactsGrid(facts = facts(state))

        StatusButtons(state = state, stage = stage, actions = actions)

        if (stage == UpdateStage.Failed && (state.download as? DownloadState.Failed)?.error == DownloadError.NeedsWifi) {
            GhostButton(
                text = stringResource(R.string.update_action_mobile_data),
                onClick = actions.onDownloadMetered,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_wifi_high),
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(14.dp),
            )
            Text(text = stringResource(R.string.update_note), style = MaterialTheme.appType.caption, color = colors.ink2)
        }
    }
}

@Immutable
private data class StageCopy(
    @param:DrawableRes val icon: Int,
    val kicker: String,
    val headline: String,
    val sub: String,
)

@Composable
private fun stageCopy(state: UpdateStatus, stage: UpdateStage): StageCopy {
    val release = state.release
    val latestName = release?.versionName ?: state.installed.versionName
    val version = stringResource(R.string.update_headline_version, latestName)
    return when (stage) {
        UpdateStage.Checking -> StageCopy(
            R.drawable.ic_ph_circle_notch,
            stringResource(R.string.update_kicker_checking),
            stringResource(R.string.update_headline_checking),
            stringResource(R.string.update_sub_checking),
        )
        UpdateStage.CheckFailed -> StageCopy(
            R.drawable.ic_ph_warning_circle,
            stringResource(R.string.update_kicker_check_failed),
            stringResource(R.string.update_headline_current, state.installed.versionName),
            stringResource(R.string.update_sub_check_failed),
        )
        UpdateStage.UpToDate -> StageCopy(
            R.drawable.ic_ph_check_circle,
            stringResource(R.string.update_kicker_current),
            stringResource(R.string.update_headline_current, state.installed.versionName),
            state.checkedAt?.let {
                stringResource(R.string.update_sub_current) + " " +
                    stringResource(R.string.update_sub_checked, formatRelative(it.toEpochMilli()))
            } ?: stringResource(R.string.update_sub_current),
        )
        UpdateStage.Available -> {
            val count = release?.changelog?.size ?: 0
            StageCopy(
                R.drawable.ic_ph_download_simple,
                stringResource(R.string.update_kicker_available),
                version,
                if (count > 0) {
                    pluralStringResource(R.plurals.update_sub_available_changes, count, formatNumber(count))
                } else {
                    stringResource(R.string.update_sub_available)
                },
            )
        }
        UpdateStage.Downloading -> StageCopy(
            R.drawable.ic_ph_cloud_arrow_down,
            stringResource(R.string.update_kicker_downloading),
            version,
            stringResource(R.string.update_sub_downloading),
        )
        UpdateStage.Paused -> StageCopy(
            R.drawable.ic_ph_pause_circle,
            stringResource(R.string.update_kicker_paused),
            version,
            stringResource(R.string.update_sub_paused),
        )
        UpdateStage.Verifying -> StageCopy(
            R.drawable.ic_ph_gear,
            stringResource(R.string.update_kicker_installing),
            version,
            stringResource(R.string.update_sub_verifying),
        )
        UpdateStage.Ready -> StageCopy(
            R.drawable.ic_ph_shield_check,
            stringResource(R.string.update_kicker_ready),
            version,
            stringResource(R.string.update_sub_ready),
        )
        UpdateStage.Failed -> StageCopy(
            R.drawable.ic_ph_warning_circle,
            stringResource(R.string.update_kicker_failed),
            version,
            when ((state.download as? DownloadState.Failed)?.error) {
                DownloadError.NeedsWifi -> stringResource(R.string.update_sub_failed_wifi)
                DownloadError.Verification -> stringResource(R.string.update_sub_failed_verification)
                DownloadError.Storage -> stringResource(R.string.update_sub_failed_storage)
                else -> stringResource(R.string.update_sub_failed_network)
            },
        )
    }
}

@Composable
private fun TransferBlock(state: UpdateStatus, stage: UpdateStage) {
    val colors = MaterialTheme.appColors
    val context = LocalContext.current
    val (bytes, total) = when (val download = state.download) {
        is DownloadState.Downloading -> download.bytes to download.total
        is DownloadState.Paused -> download.bytes to download.total
        else -> (state.release?.apk?.size ?: 0L).let { it to it }
    }
    val label = when (stage) {
        UpdateStage.Downloading -> stringResource(R.string.update_stage_downloading)
        UpdateStage.Paused -> stringResource(R.string.update_stage_paused)
        UpdateStage.Verifying -> stringResource(R.string.update_stage_installing)
        else -> return
    }
    val fraction = if (total > 0L) bytes.toFloat() / total else 0f
    val eta = when (val download = state.download) {
        is DownloadState.Downloading -> etaSeconds(download.bytes, download.total, download.bytesPerSecond)
            ?.let { formatEta(it) } ?: stringResource(R.string.update_eta_almost)
        is DownloadState.Paused -> stringResource(R.string.update_stage_paused)
        else -> stringResource(R.string.update_eta_almost)
    }
    Column(
        modifier = Modifier.riseIn(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = colors.ink)
            Text(
                text = if (stage == UpdateStage.Verifying) label else formatPercent(fraction),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.accTx,
            )
        }
        ProgressBar(
            progress = fraction,
            height = 8.dp,
            color = colors.accLt,
            trackColor = colors.accLine,
            animate = false,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(
                    R.string.update_transferred,
                    Formatter.formatShortFileSize(context, bytes),
                    Formatter.formatShortFileSize(context, total),
                ),
                style = MaterialTheme.appType.caption,
                color = colors.ink2,
            )
            Text(text = eta, style = MaterialTheme.appType.caption, color = colors.ink2)
        }
    }
}

@Composable
private fun formatEta(seconds: Long): String =
    if (seconds < 60L) {
        pluralStringResource(R.plurals.update_eta_seconds, seconds.toInt(), formatNumber(seconds))
    } else {
        val minutes = (seconds + 59L) / 60L
        pluralStringResource(R.plurals.update_eta_minutes, minutes.toInt(), formatNumber(minutes))
    }

@Immutable
private data class Fact(val label: String, val value: String)

@Composable
private fun facts(state: UpdateStatus): List<Fact> {
    val context = LocalContext.current
    val release = state.release
    val shown = release?.takeIf { state.available }
    val versionName = shown?.versionName ?: state.installed.versionName
    val versionCode = shown?.versionCode ?: state.installed.versionCode
    val requires = AndroidVersions.nameOf(state.installed.minSdk) ?: state.installed.minSdk.toString()
    return buildList {
        add(
            Fact(
                stringResource(R.string.update_fact_version),
                stringResource(R.string.update_fact_version_value, versionName, formatNumber(versionCode)),
            ),
        )
        shown?.apk?.size?.takeIf { it > 0L }?.let {
            add(Fact(stringResource(R.string.update_fact_download_size), Formatter.formatShortFileSize(context, it)))
        }
        (shown ?: release)?.publishedAt?.let {
            add(Fact(stringResource(R.string.update_fact_released), formatDate(it.localDate())))
        }
        add(Fact(stringResource(R.string.update_fact_requires), stringResource(R.string.update_fact_requires_value, requires)))
        add(Fact(stringResource(R.string.update_fact_channel), stringResource(R.string.update_channel_stable)))
    }
}

@Composable
private fun FactsGrid(facts: List<Fact>) {
    val colors = MaterialTheme.appColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.heroLine),
        )
        facts.chunked(FACT_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { fact ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) {},
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(text = fact.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), color = colors.ink2)
                        Text(
                            text = fact.value,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(FACT_COLUMNS - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun StatusButtons(state: UpdateStatus, stage: UpdateStage, actions: UpdateActions) {
    val cancelable = stage == UpdateStage.Downloading || stage == UpdateStage.Paused ||
        (stage == UpdateStage.Failed && (state.download as? DownloadState.Failed)?.error.let {
            it == DownloadError.Network || it == DownloadError.NeedsWifi
        })
    val ready = state.download as? DownloadState.Ready
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cancelable) {
            SecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = actions.onCancel,
                modifier = Modifier.weight(1f),
            )
        }
        val (label, icon, onClick) = when (stage) {
            UpdateStage.Checking -> Triple(R.string.update_action_checking, R.drawable.ic_ph_circle_notch, null)
            UpdateStage.CheckFailed -> Triple(R.string.action_retry, R.drawable.ic_ph_arrow_clockwise, actions.onCheck)
            UpdateStage.UpToDate -> Triple(R.string.update_action_check, R.drawable.ic_ph_arrow_clockwise, actions.onCheck)
            UpdateStage.Available -> Triple(R.string.update_action_update, R.drawable.ic_ph_download_simple, actions.onDownload)
            UpdateStage.Downloading -> Triple(R.string.update_action_pause, R.drawable.ic_ph_pause, actions.onPause)
            UpdateStage.Paused -> Triple(R.string.update_action_resume, R.drawable.ic_ph_play, actions.onDownload)
            UpdateStage.Verifying -> Triple(R.string.update_action_verifying, R.drawable.ic_ph_circle_notch, null)
            UpdateStage.Ready -> Triple(
                R.string.update_action_install,
                R.drawable.ic_ph_shield_check,
                ready?.let { { actions.onInstall(it.file) } },
            )
            UpdateStage.Failed -> Triple(R.string.action_retry, R.drawable.ic_ph_arrow_clockwise, actions.onDownload)
        }
        PrimaryButton(
            text = stringResource(label),
            icon = icon,
            onClick = onClick ?: {},
            enabled = onClick != null,
            modifier = Modifier.weight(if (cancelable) PRIMARY_WEIGHT else 1f),
        )
    }
}

@Composable
private fun WhatsNewHeader(release: Release) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 18.dp, end = ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(R.string.update_whats_new, release.versionName),
            style = MaterialTheme.typography.titleSmall,
            color = colors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        release.publishedAt?.let {
            Text(
                text = stringResource(R.string.update_released_on, formatDate(it.localDate())),
                style = MaterialTheme.appType.caption,
                color = colors.ink3,
            )
        }
    }
}

@Immutable
private data class KindStyle(@param:DrawableRes val icon: Int, val tint: Color, val content: Color, val label: String?)

@Composable
private fun kindStyle(kind: ChangeKind?): KindStyle {
    val colors = MaterialTheme.appColors
    return when (kind) {
        ChangeKind.New -> KindStyle(R.drawable.ic_ph_sparkle, colors.accTint18, colors.accTx, stringResource(R.string.update_kind_new))
        ChangeKind.Improved -> KindStyle(R.drawable.ic_ph_lightning, colors.neutralTint14, colors.neutralTx, stringResource(R.string.update_kind_improved))
        ChangeKind.Fixed -> KindStyle(R.drawable.ic_ph_bug, colors.amber.fill, colors.amber.text, stringResource(R.string.update_kind_fixed))
        null -> KindStyle(R.drawable.ic_ph_list_dashes, colors.neutralTint14, colors.neutralTx, null)
    }
}

@Composable
private fun ChangeCard(entry: ChangeEntry, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val style = kindStyle(entry.kind)
    val shape = MaterialTheme.appShapes.stepper
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surf, shape)
            .border(1.dp, colors.line, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 15.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(style.tint, MaterialTheme.appShapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(style.icon),
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (entry.title != null || style.label != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.title != null) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (style.label != null) {
                        Box(
                            modifier = Modifier
                                .height(20.dp)
                                .background(style.tint, MaterialTheme.appShapes.pill)
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = style.label.uppercase(currentLocale()),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.06.em,
                                ),
                                color = style.content,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (entry.body.isNotEmpty()) {
                Text(
                    text = entry.body,
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                    color = colors.ink3,
                )
            }
        }
    }
}

@Composable
private fun InstalledCard(installed: InstalledApp, previous: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.stepper
    val code = formatNumber(installed.versionCode)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surf0, shape)
            .border(1.dp, colors.line, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(
                if (previous) R.string.update_previous else R.string.update_installed,
                installed.versionName,
                code,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = colors.ink2,
        )
        installed.updatedAt?.let {
            val date = formatDate(it.localDate())
            Text(
                text = stringResource(if (previous) R.string.update_previous_body else R.string.update_installed_body, date),
                style = MaterialTheme.appType.caption.copy(lineHeight = 17.sp),
                color = colors.ink3,
            )
        }
    }
}

private fun Instant.localDate() = atZone(ZoneId.systemDefault()).toLocalDate()

private val ScreenPadding = 20.dp
private const val FACT_COLUMNS = 3
private const val PRIMARY_WEIGHT = 1.6f

private val PreviewInstalled = InstalledApp(
    versionName = "1.0",
    versionCode = 104,
    minSdk = 26,
    updatedAt = Instant.parse("2026-09-02T10:00:00Z"),
)

private val PreviewRelease = Release(
    tag = "v1.1",
    apk = ApkAsset(
        name = "reader343-1.1-118.apk",
        versionName = "1.1",
        versionCode = 118,
        url = "",
        size = 24_600_000L,
    ),
    publishedAt = Instant.parse("2026-09-16T09:00:00Z"),
    htmlUrl = null,
    changelog = listOf(
        ChangeEntry(ChangeKind.New, "Weekly goals", "Set a weekly target alongside the daily one."),
        ChangeEntry(ChangeKind.Improved, "Faster page rendering", "Large PDFs open about 40% quicker."),
        ChangeEntry(ChangeKind.Fixed, "Streak rollover at midnight", "Sessions crossing midnight no longer count twice."),
        ChangeEntry(null, null, "Smaller download size."),
    ),
)

private fun previewStatus(download: DownloadState, release: Release? = PreviewRelease) = UpdateStatus(
    installed = PreviewInstalled,
    release = release,
    checkedAt = Instant.parse("2026-09-17T09:00:00Z"),
    checking = false,
    checkFailed = false,
    download = download,
)

private val PreviewActions = UpdateActions({}, {}, {}, {}, {}, {}, {})

@Preview(name = "Available", showBackground = true, heightDp = 1100)
@Composable
private fun UpdateAvailablePreview() {
    Reader343Theme(darkTheme = true) {
        UpdateScreen(state = previewStatus(DownloadState.Idle), actions = PreviewActions)
    }
}

@Preview(name = "Downloading light", showBackground = true, heightDp = 1100)
@Composable
private fun UpdateDownloadingPreview() {
    Reader343Theme(darkTheme = false) {
        UpdateScreen(
            state = previewStatus(DownloadState.Downloading(9_800_000L, 24_600_000L, 1_200_000L)),
            actions = PreviewActions,
        )
    }
}

@Preview(name = "Paused RTL", showBackground = true, heightDp = 1100, locale = "ar")
@Composable
private fun UpdatePausedRtlPreview() {
    Reader343Theme(darkTheme = true) {
        UpdateScreen(state = previewStatus(DownloadState.Paused(9_800_000L, 24_600_000L)), actions = PreviewActions)
    }
}

@Preview(name = "Up to date", showBackground = true, heightDp = 700)
@Composable
private fun UpdateCurrentPreview() {
    Reader343Theme(darkTheme = true) {
        UpdateScreen(
            state = previewStatus(DownloadState.Idle, release = PreviewRelease.copy(apk = PreviewRelease.apk.copy(versionCode = 104, versionName = "1.0"))),
            actions = PreviewActions,
        )
    }
}
