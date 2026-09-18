package com.reader343.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.pdf.PageSize
import com.reader343.ui.theme.Reader343Theme
import kotlin.math.roundToInt

@Composable
fun ReaderRoute(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()

    ReaderScreen(
        state = state,
        zoom = zoom,
        detail = detail,
        onBack = onBack,
        onPageSettled = viewModel::onPageSettled,
        onTransform = viewModel::onTransform,
        onDoubleTap = viewModel::onDoubleTap,
        onTap = viewModel::onTap,
        loadPage = viewModel::pageBitmap,
    )
}

@Composable
fun ReaderScreen(
    state: ReaderUiState,
    zoom: ZoomState,
    detail: PageDetail?,
    onBack: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, factor: Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: () -> Unit,
    loadPage: suspend (page: Int, viewport: IntSize) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        when (state) {
            ReaderUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            ReaderUiState.Error -> ReaderError(onBack = onBack, modifier = Modifier.align(Alignment.Center))
            is ReaderUiState.Ready -> {
                ReaderPager(
                    state = state,
                    zoom = zoom,
                    detail = detail,
                    onPageSettled = onPageSettled,
                    onTransform = onTransform,
                    onDoubleTap = onDoubleTap,
                    onTap = onTap,
                    loadPage = loadPage,
                )
                ReaderTopBar(
                    visible = state.chromeVisible,
                    title = state.title,
                    onBack = onBack,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                ReaderBottomBar(
                    visible = state.chromeVisible,
                    page = state.currentPage,
                    pageCount = state.pageCount,
                    percent = state.percent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun ReaderPager(
    state: ReaderUiState.Ready,
    zoom: ZoomState,
    detail: PageDetail?,
    onPageSettled: (Int) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: () -> Unit,
    loadPage: suspend (Int, IntSize) -> ImageBitmap?,
) {
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.pageCount }
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnPageSettled(it) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !zoom.isZoomed,
        key = { it },
    ) { index ->
        val active = index == state.currentPage
        PdfPage(
            page = index,
            pageSize = state.pageSizes[index],
            zoom = if (active) zoom else ZoomState(),
            detail = detail?.takeIf { it.page == index },
            active = active,
            onTransform = onTransform,
            onDoubleTap = onDoubleTap,
            onTap = onTap,
            loadPage = loadPage,
        )
    }
}

@Composable
private fun PdfPage(
    page: Int,
    pageSize: PageSize,
    zoom: ZoomState,
    detail: PageDetail?,
    active: Boolean,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: () -> Unit,
    loadPage: suspend (Int, IntSize) -> ImageBitmap?,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(page) { mutableStateOf<ImageBitmap?>(null) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnTap by rememberUpdatedState(onTap)
    val loadingDescription = stringResource(R.string.reader_page_loading, page + 1)

    LaunchedEffect(page, viewport) {
        if (viewport != IntSize.Zero) bitmap = loadPage(page, viewport)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it }
            .pointerInput(active) {
                if (active) detectZoomAndPan({ currentZoom.isZoomed }) { c, p, f -> currentOnTransform(c, p, f) }
            }
            .pointerInput(active) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = if (active) { position -> currentOnDoubleTap(position) } else null,
                )
            }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.scale
                scaleY = zoom.scale
                translationX = zoom.offsetX
                translationY = zoom.offsetY
            }
            .drawBehind {
                val area = PageLayout.fit(viewport, pageSize).page
                drawRect(Color.White, area.topLeft, area.size)
                bitmap?.let {
                    drawImage(
                        image = it,
                        dstOffset = IntOffset(area.left.roundToInt(), area.top.roundToInt()),
                        dstSize = IntSize(area.width.roundToInt(), area.height.roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
                detail?.let {
                    val left = area.left + it.region.left * area.width
                    val top = area.top + it.region.top * area.height
                    val right = area.left + it.region.right * area.width
                    val bottom = area.top + it.region.bottom * area.height
                    drawImage(
                        image = it.bitmap,
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize((right - left).roundToInt(), (bottom - top).roundToInt()),
                        filterQuality = FilterQuality.High,
                    )
                }
            },
    ) {
        if (bitmap == null && viewport != IntSize.Zero) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = loadingDescription },
            )
        }
    }
}

private suspend fun PointerInputScope.detectZoomAndPan(
    isZoomed: () -> Boolean,
    onTransform: (centroid: Offset, pan: Offset, factor: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var pastSlop = false
        var accumulated = Offset.Zero
        do {
            val event = awaitPointerEvent()
            if (event.changes.none { it.isConsumed }) {
                val multiTouch = event.changes.count { it.pressed } > 1
                val factor = event.calculateZoom()
                val pan = event.calculatePan()
                if (!pastSlop) {
                    accumulated += pan
                    pastSlop = multiTouch || accumulated.getDistance() > viewConfiguration.touchSlop
                }
                if (pastSlop && (multiTouch || isZoomed())) {
                    if (factor != 1f || pan != Offset.Zero) {
                        onTransform(event.calculateCentroid(useCurrent = false), pan, factor)
                    }
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun ReaderTopBar(
    visible: Boolean,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
    ) {
        Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun ReaderBottomBar(
    visible: Boolean,
    page: Int,
    pageCount: Int,
    percent: Float,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
    ) {
        Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.reader_page_indicator, page + 1, pageCount),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Text(
                        text = stringResource(R.string.library_percent, (percent * 100).roundToInt()),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ReaderError(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_open_failed),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.action_back))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderReadyPreview() {
    Reader343Theme {
        ReaderScreen(
            state = ReaderUiState.Ready(
                title = "Sample book",
                pageSizes = List(12) { PageSize(612f, 792f) },
                initialPage = 3,
                currentPage = 3,
                chromeVisible = true,
            ),
            zoom = ZoomState(),
            detail = null,
            onBack = {},
            onPageSettled = {},
            onTransform = { _, _, _ -> },
            onDoubleTap = {},
            onTap = {},
            loadPage = { _, _ -> null },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderErrorPreview() {
    Reader343Theme {
        ReaderScreen(
            state = ReaderUiState.Error,
            zoom = ZoomState(),
            detail = null,
            onBack = {},
            onPageSettled = {},
            onTransform = { _, _, _ -> },
            onDoubleTap = {},
            onTap = {},
            loadPage = { _, _ -> null },
        )
    }
}
