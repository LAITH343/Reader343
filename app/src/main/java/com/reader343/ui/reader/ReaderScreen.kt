package com.reader343.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.reader343.domain.Highlight
import com.reader343.domain.NormRect
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
    val markup by viewModel.markup.collectAsStateWithLifecycle()

    ReaderScreen(
        state = state,
        zoom = zoom,
        detail = detail,
        markup = markup,
        markupActions = viewModel,
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
    markup: MarkupState,
    markupActions: MarkupActions,
    onBack: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onTransform: (centroid: Offset, pan: Offset, factor: Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: (Offset) -> Unit,
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
                    markup = markup,
                    markupActions = markupActions,
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
    markup: MarkupState,
    markupActions: MarkupActions,
    onPageSettled: (Int) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: (Offset) -> Unit,
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
        userScrollEnabled = !zoom.isZoomed && markup.selection == null,
        key = { it },
    ) { index ->
        val active = index == state.currentPage
        PdfPage(
            page = index,
            pageSize = state.pageSizes[index],
            zoom = if (active) zoom else ZoomState(),
            detail = detail?.takeIf { it.page == index },
            active = active,
            highlights = markup.highlights[index].orEmpty(),
            selection = markup.selection?.takeIf { active && it.page == index },
            activeHighlight = markup.activeHighlight?.takeIf { active && it.page == index },
            markupActions = markupActions,
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
    highlights: List<Highlight>,
    selection: SelectionUi?,
    activeHighlight: Highlight?,
    markupActions: MarkupActions,
    onTransform: (Offset, Offset, Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: (Offset) -> Unit,
    loadPage: suspend (Int, IntSize) -> ImageBitmap?,
) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var bitmap by remember(page) { mutableStateOf<ImageBitmap?>(null) }
    val layout = remember(viewport, pageSize) { PageLayout.fit(viewport, pageSize) }
    val density = LocalDensity.current
    val handleRadius = with(density) { HandleRadius.toPx() }
    val handleTouchRadius = with(density) { HandleTouchRadius.toPx() }
    val markStroke = with(density) { MarkStroke.toPx() }
    val floatGap = with(density) { FloatGap.toPx() }
    val floatMargin = with(density) { FloatMargin.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    val currentZoom by rememberUpdatedState(zoom)
    val currentLayout by rememberUpdatedState(layout)
    val currentSelection by rememberUpdatedState(selection)
    val currentActions by rememberUpdatedState(markupActions)
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
            .onSizeChanged { viewport = it },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(active) {
                    if (active) detectZoomAndPan({ currentZoom.isZoomed }) { c, p, f -> currentOnTransform(c, p, f) }
                }
                .pointerInput(active) {
                    detectTapGestures(
                        onTap = { position -> currentOnTap(position) },
                        onDoubleTap = if (active) { position -> currentOnDoubleTap(position) } else null,
                    )
                }
                .pointerInput(active, handleRadius, handleTouchRadius) {
                    if (active) {
                        detectSelectionGestures(
                            handleAt = { position ->
                                currentSelection?.let {
                                    handleAt(it, currentLayout, currentZoom, position, handleRadius, handleTouchRadius)
                                }
                            },
                            actions = { currentActions },
                        )
                    }
                }
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = zoom.scale
                    scaleY = zoom.scale
                    translationX = zoom.offsetX
                    translationY = zoom.offsetY
                }
                .drawBehind {
                    val area = layout.page
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
                    highlights.forEach { drawMarks(it.rects, Color(it.color), layout) }
                    activeHighlight?.let { drawOutlines(it.rects, accent, layout, markStroke / zoom.scale) }
                    selection?.let { drawSelection(it, layout, accent, handleRadius / zoom.scale, markStroke / zoom.scale) }
                },
        )
        if (selection != null) {
            SelectionPalette(
                selectedColor = selection.color,
                canConfirm = selection.canConfirm,
                onColorSelected = markupActions::onColorSelected,
                onConfirm = markupActions::onConfirmHighlight,
                modifier = Modifier.floatNear(
                    anchor = screenBounds(selection.bounds, layout, zoom, if (selection.region) 0f else handleRadius * 2f),
                    gap = floatGap,
                    margin = floatMargin,
                ),
            )
        } else if (activeHighlight != null) {
            val bounds = activeHighlight.rects.reduceOrNull(NormRect::union)
            if (bounds != null) {
                HighlightMenu(
                    onDelete = markupActions::onDeleteHighlight,
                    modifier = Modifier.floatNear(
                        anchor = screenBounds(bounds, layout, zoom, 0f),
                        gap = floatGap,
                        margin = floatMargin,
                    ),
                )
            }
        }
        if (bitmap == null && viewport != IntSize.Zero) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .semantics { contentDescription = loadingDescription },
            )
        }
    }
}

private fun DrawScope.drawMarks(rects: List<NormRect>, color: Color, layout: PageLayout) {
    rects.forEach { rect ->
        val area = layout.toContent(rect)
        drawRect(color, area.topLeft, area.size, blendMode = BlendMode.Multiply)
    }
}

private fun DrawScope.drawOutlines(rects: List<NormRect>, color: Color, layout: PageLayout, width: Float) {
    rects.forEach { rect ->
        val area = layout.toContent(rect)
        drawRect(color, area.topLeft, area.size, style = Stroke(width))
    }
}

private fun DrawScope.drawSelection(
    selection: SelectionUi,
    layout: PageLayout,
    accent: Color,
    radius: Float,
    stroke: Float,
) {
    val fill = Color(selection.color).copy(alpha = SELECTION_ALPHA)
    selection.rects.forEach { rect ->
        val area = layout.toContent(rect)
        drawRect(fill, area.topLeft, area.size, blendMode = BlendMode.Multiply)
    }
    if (selection.region) {
        val box = layout.toContent(selection.bounds)
        drawRect(accent, box.topLeft, box.size, style = Stroke(stroke))
    }
    listOf(selection.start, selection.end).forEach { mark ->
        val top = layout.toContent(mark.x, mark.top)
        val bottom = layout.toContent(mark.x, mark.bottom)
        if (selection.region) {
            drawCircle(accent, radius, bottom)
        } else {
            drawLine(accent, top, bottom, stroke)
            drawCircle(accent, radius, bottom + Offset(0f, radius))
        }
    }
}

private fun handleAt(
    selection: SelectionUi,
    layout: PageLayout,
    zoom: ZoomState,
    position: Offset,
    radius: Float,
    touchRadius: Float,
): Pair<SelectionHandle, Offset>? {
    val candidates = listOf(SelectionHandle.End to selection.end, SelectionHandle.Start to selection.start)
    var best: Pair<SelectionHandle, HandleMark>? = null
    var bestDistance = touchRadius
    candidates.forEach { (handle, mark) ->
        val bottom = layout.toScreen(zoom, mark.x, mark.bottom)
        val center = if (selection.region) bottom else bottom + Offset(0f, radius)
        val distance = (center - position).getDistance()
        if (distance <= bestDistance) {
            bestDistance = distance
            best = handle to mark
        }
    }
    val (handle, mark) = best ?: return null
    val anchor = if (selection.region) {
        layout.toScreen(zoom, mark.x, mark.bottom)
    } else {
        val inward = if (handle == SelectionHandle.Start) HANDLE_INSET_PX else -HANDLE_INSET_PX
        layout.toScreen(zoom, mark.x, (mark.top + mark.bottom) / 2f) + Offset(inward, 0f)
    }
    return handle to (anchor - position)
}

private fun screenBounds(bounds: NormRect, layout: PageLayout, zoom: ZoomState, extraBottom: Float): Rect {
    val topLeft = layout.toScreen(zoom, bounds.left, bounds.top)
    val bottomRight = layout.toScreen(zoom, bounds.right, bounds.bottom)
    return Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y + extraBottom)
}

private fun Modifier.floatNear(anchor: Rect, gap: Float, margin: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            val maxX = (width - placeable.width - margin).coerceAtLeast(margin)
            val maxY = (height - placeable.height - margin).coerceAtLeast(margin)
            val above = anchor.top - gap - placeable.height
            val below = anchor.bottom + gap
            val y = when {
                above >= margin -> above
                below <= maxY -> below
                else -> anchor.center.y - placeable.height / 2f
            }
            val x = anchor.center.x - placeable.width / 2f
            placeable.place(x.coerceIn(margin, maxX).roundToInt(), y.coerceIn(margin, maxY).roundToInt())
        }
    }

private suspend fun PointerInputScope.detectSelectionGestures(
    handleAt: (Offset) -> Pair<SelectionHandle, Offset>?,
    actions: () -> MarkupActions,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val grabbed = handleAt(down.position)
        if (grabbed != null) {
            down.consume()
            actions().onHandleGrab(grabbed.first, grabbed.second)
        } else {
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            press.consume()
            actions().onLongPress(press.position)
        }
        trackSelectionDrag(down.id, actions())
    }
}

private suspend fun AwaitPointerEventScope.trackSelectionDrag(pointer: PointerId, actions: MarkupActions) {
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointer } ?: break
            if (change.pressed && change.positionChanged()) actions.onSelectionDrag(change.position)
            event.changes.forEach { it.consume() }
            if (!change.pressed) break
        }
    } finally {
        actions.onSelectionDragEnd()
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
private fun SelectionPalette(
    selectedColor: Int,
    canConfirm: Boolean,
    onColorSelected: (Int) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HighlightColor.entries.forEach { color ->
                val isSelected = color.argb == selectedColor
                val label = stringResource(color.label)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(color.argb))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape,
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onColorSelected(color.argb) },
                        )
                        .semantics { contentDescription = label },
                )
            }
            IconButton(onClick = onConfirm, enabled = canConfirm) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.highlight_confirm),
                )
            }
        }
    }
}

@Composable
private fun HighlightMenu(onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        TextButton(onClick = onDelete, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.highlight_delete))
        }
    }
}

private val HandleRadius = 9.dp
private val HandleTouchRadius = 28.dp
private val MarkStroke = 2.dp
private val FloatGap = 12.dp
private val FloatMargin = 8.dp
private const val SELECTION_ALPHA = 0.6f
private const val HANDLE_INSET_PX = 1f

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
            markup = MarkupState(),
            markupActions = MarkupActions.None,
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
            markup = MarkupState(),
            markupActions = MarkupActions.None,
            onBack = {},
            onPageSettled = {},
            onTransform = { _, _, _ -> },
            onDoubleTap = {},
            onTap = {},
            loadPage = { _, _ -> null },
        )
    }
}
