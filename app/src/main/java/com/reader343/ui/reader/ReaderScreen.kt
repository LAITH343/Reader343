package com.reader343.ui.reader

import android.os.Build
import androidx.compose.foundation.magnifier
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontStyle
import com.reader343.domain.Note
import kotlinx.coroutines.launch
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
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    ReaderScreen(
        state = state,
        zoom = zoom,
        detail = detail,
        markup = markup,
        markupActions = viewModel,
        notes = notes,
        noteActions = viewModel,
        onBack = onBack,
        onPageSettled = viewModel::onPageSettled,
        onPageRequestHandled = viewModel::onPageRequestHandled,
        onZoomGestureStart = viewModel::onZoomGestureStart,
        onTransform = viewModel::onTransform,
        onZoomGestureEnd = viewModel::onZoomGestureEnd,
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
    notes: NotesUiState,
    noteActions: NoteActions,
    onBack: () -> Unit,
    onPageSettled: (Int) -> Unit,
    onPageRequestHandled: () -> Unit,
    onZoomGestureStart: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, factor: Float) -> Unit,
    onZoomGestureEnd: (centroid: Offset, velocity: Velocity) -> Unit,
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
                    notes = notes,
                    noteActions = noteActions,
                    onPageSettled = onPageSettled,
                    onPageRequestHandled = onPageRequestHandled,
                    onZoomGestureStart = onZoomGestureStart,
                    onTransform = onTransform,
                    onZoomGestureEnd = onZoomGestureEnd,
                    onDoubleTap = onDoubleTap,
                    onTap = onTap,
                    loadPage = loadPage,
                )
                ReaderTopBar(
                    visible = state.chromeVisible,
                    title = state.title,
                    onBack = onBack,
                    onShowNotes = noteActions::onShowNotes,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                ReaderBottomBar(
                    visible = state.chromeVisible,
                    page = state.currentPage,
                    pageCount = state.pageCount,
                    percent = state.percent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                notes.editor?.let { NoteSheet(editor = it, actions = noteActions) }
                if (notes.listVisible) NotesListSheet(notes = notes.all, actions = noteActions)
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
    notes: NotesUiState,
    noteActions: NoteActions,
    onPageSettled: (Int) -> Unit,
    onPageRequestHandled: () -> Unit,
    onZoomGestureStart: () -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onZoomGestureEnd: (Offset, Velocity) -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onTap: (Offset) -> Unit,
    loadPage: suspend (Int, IntSize) -> ImageBitmap?,
) {
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.pageCount }
    val currentOnPageSettled by rememberUpdatedState(onPageSettled)
    val currentOnPageRequestHandled by rememberUpdatedState(onPageRequestHandled)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnPageSettled(it) }
    }

    LaunchedEffect(state.pageRequest) {
        val request = state.pageRequest ?: return@LaunchedEffect
        pagerState.scrollToPage(request)
        currentOnPageRequestHandled()
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
            loupe = markup.loupe?.takeIf { active },
            markupActions = markupActions,
            notes = notes.byPage[index].orEmpty(),
            noteAnchor = notes.editor?.takeIf { active && it.page == index }?.anchor?.rect,
            activeHighlightHasNote = markup.activeHighlight?.let { notes.forHighlight(it.id) } != null,
            noteActions = noteActions,
            onZoomGestureStart = onZoomGestureStart,
            onTransform = onTransform,
            onZoomGestureEnd = onZoomGestureEnd,
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
    loupe: Loupe?,
    markupActions: MarkupActions,
    notes: List<Note>,
    noteAnchor: NormRect?,
    activeHighlightHasNote: Boolean,
    noteActions: NoteActions,
    onZoomGestureStart: () -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
    onZoomGestureEnd: (Offset, Velocity) -> Unit,
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
    val markerSize = with(density) { NoteMarkerSize.toPx() }
    val markerInset = with(density) { NoteMarkerInset.toPx() }
    val markerTouch = with(density) { NoteMarkerTouchRadius.toPx() }
    val accent = MaterialTheme.colorScheme.primary
    val markerColor = MaterialTheme.colorScheme.tertiary
    val markerContent = MaterialTheme.colorScheme.onTertiary
    val notePainter = painterResource(R.drawable.ic_note)
    val currentNotes by rememberUpdatedState(notes)
    val currentNoteActions by rememberUpdatedState(noteActions)
    val currentZoom by rememberUpdatedState(zoom)
    val currentLayout by rememberUpdatedState(layout)
    val currentSelection by rememberUpdatedState(selection)
    val currentActions by rememberUpdatedState(markupActions)
    val currentOnZoomGestureStart by rememberUpdatedState(onZoomGestureStart)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnZoomGestureEnd by rememberUpdatedState(onZoomGestureEnd)
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
            .selectionLoupe(loupe),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(active) {
                    if (active) {
                        detectZoomAndPan(
                            isZoomed = { currentZoom.isZoomed },
                            onStart = { currentOnZoomGestureStart() },
                            onTransform = { c, p, f -> currentOnTransform(c, p, f) },
                            onEnd = { c, v -> currentOnZoomGestureEnd(c, v) },
                        )
                    }
                }
                .pointerInput(active) {
                    detectTapGestures(
                        onTap = { position ->
                            val hit = noteAt(
                                notes = currentNotes,
                                layout = currentLayout,
                                zoom = currentZoom,
                                position = position,
                                size = markerSize,
                                inset = markerInset,
                                touchRadius = markerTouch,
                            )
                            if (hit != null) currentNoteActions.onOpenNote(hit.id) else currentOnTap(position)
                        },
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
                    noteAnchor?.let { drawOutlines(listOf(it), markerColor, layout, markStroke / zoom.scale) }
                    drawNoteMarkers(
                        markers = noteMarkers(notes, layout, markerSize / zoom.scale, markerInset / zoom.scale),
                        size = markerSize / zoom.scale,
                        painter = notePainter,
                        background = markerColor,
                        content = markerContent,
                    )
                    selection?.let { drawSelection(it, layout, accent, handleRadius / zoom.scale, markStroke / zoom.scale) }
                },
        )
        if (selection != null && loupe == null) {
            SelectionPalette(
                selectedColor = selection.color,
                canConfirm = selection.canConfirm,
                onColorSelected = markupActions::onColorSelected,
                onConfirm = markupActions::onConfirmHighlight,
                onAddNote = noteActions::onAddNoteFromSelection,
                modifier = Modifier.floatNear(
                    anchor = screenBounds(selection.bounds, layout, zoom, if (selection.region) 0f else handleRadius * 2f),
                    gap = floatGap,
                    margin = floatMargin,
                ),
            )
        } else if (selection == null && activeHighlight != null) {
            val bounds = activeHighlight.rects.reduceOrNull(NormRect::union)
            if (bounds != null) {
                HighlightMenu(
                    hasNote = activeHighlightHasNote,
                    onNote = noteActions::onAddNoteFromHighlight,
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

private class NoteMarker(val note: Note, val center: Offset)

private fun noteMarkers(notes: List<Note>, layout: PageLayout, size: Float, inset: Float): List<NoteMarker> {
    if (notes.isEmpty()) return emptyList()
    val area = layout.page
    val x = area.right - inset - size / 2f
    val maxTop = (area.bottom - inset - size).coerceAtLeast(area.top)
    var nextTop = area.top + inset
    return notes.sortedBy { it.anchor.rect.top }.map { note ->
        val anchorTop = area.top + note.anchor.rect.top * area.height
        val top = maxOf(anchorTop, nextTop).coerceAtMost(maxTop)
        nextTop = top + size + inset
        NoteMarker(note, Offset(x, top + size / 2f))
    }
}

private fun noteAt(
    notes: List<Note>,
    layout: PageLayout,
    zoom: ZoomState,
    position: Offset,
    size: Float,
    inset: Float,
    touchRadius: Float,
): Note? {
    val content = Offset((position.x - zoom.offsetX) / zoom.scale, (position.y - zoom.offsetY) / zoom.scale)
    val limit = touchRadius / zoom.scale
    return noteMarkers(notes, layout, size / zoom.scale, inset / zoom.scale)
        .map { it to (it.center - content).getDistance() }
        .filter { it.second <= limit }
        .minByOrNull { it.second }
        ?.first
        ?.note
}

private fun DrawScope.drawNoteMarkers(
    markers: List<NoteMarker>,
    size: Float,
    painter: Painter,
    background: Color,
    content: Color,
) {
    val icon = size * NOTE_ICON_RATIO
    markers.forEach { marker ->
        drawCircle(background, size / 2f, marker.center)
        translate(marker.center.x - icon / 2f, marker.center.y - icon / 2f) {
            with(painter) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(content)) }
        }
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

private fun Modifier.selectionLoupe(loupe: Loupe?): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return this
    return magnifier(
        sourceCenter = { loupe?.source ?: Offset.Unspecified },
        magnifierCenter = {
            loupe?.let { Offset(it.source.x, minOf(it.source.y, it.touch.y) - LoupeLift.toPx()) }
                ?: Offset.Unspecified
        },
        zoom = LOUPE_ZOOM,
        size = LoupeSize,
        cornerRadius = LoupeCornerRadius,
    )
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
            actions().onHandleGrab(grabbed.first, down.position, grabbed.second)
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
    onStart: () -> Unit,
    onTransform: (centroid: Offset, pan: Offset, factor: Float) -> Unit,
    onEnd: (centroid: Offset, velocity: Velocity) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onStart()
        val tracker = VelocityTracker()
        var pastSlop = false
        var transformed = false
        var accumulated = Offset.Zero
        var travelled = Offset.Zero
        var centroid = Offset.Zero
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
                        centroid = event.calculateCentroid(useCurrent = false)
                        onTransform(centroid, pan, factor)
                        transformed = true
                    }
                    travelled += pan
                    tracker.addPosition(event.changes.first().uptimeMillis, travelled)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
        if (transformed) onEnd(centroid, tracker.calculateVelocity())
    }
}

@Composable
private fun SelectionPalette(
    selectedColor: Int,
    canConfirm: Boolean,
    onColorSelected: (Int) -> Unit,
    onConfirm: () -> Unit,
    onAddNote: () -> Unit,
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
            IconButton(onClick = onAddNote, enabled = canConfirm) {
                Icon(
                    painter = painterResource(R.drawable.ic_note_add),
                    contentDescription = stringResource(R.string.note_add),
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
private fun HighlightMenu(
    hasNote: Boolean,
    onNote: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(modifier = Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = onNote) {
                Icon(
                    painter = painterResource(if (hasNote) R.drawable.ic_note else R.drawable.ic_note_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (hasNote) R.string.note_view else R.string.note_add))
            }
            TextButton(onClick = onDelete) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(editor: NoteEditor, actions: NoteActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    var body by rememberSaveable(editor.key) { mutableStateOf(editor.body) }
    val isNew = editor.noteId == null
    val canSave = body.isNotBlank() && body.trim() != editor.body

    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }

    LaunchedEffect(editor.key) {
        if (isNew) focusRequester.requestFocus()
    }

    ModalBottomSheet(onDismissRequest = actions::onDismissNote, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(if (isNew) R.string.note_new else R.string.note_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(R.string.note_page, editor.page + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            editor.anchor.snippet?.let { snippet ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                placeholder = { Text(stringResource(R.string.note_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .focusRequester(focusRequester),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isNew) {
                    TextButton(onClick = { hideThen(actions::onDeleteNote) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.note_delete))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { hideThen(actions::onDismissNote) }) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = { hideThen { actions.onSaveNote(body) } }, enabled = canSave) {
                    Text(stringResource(R.string.note_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotesListSheet(notes: List<Note>, actions: NoteActions) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = actions::onHideNotes, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.notes_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (notes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.notes_empty),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.notes_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(notes, key = { it.id }) { note ->
                    ListItem(
                        overlineContent = { Text(stringResource(R.string.note_page, note.page + 1)) },
                        headlineContent = {
                            Text(text = note.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = note.anchor.snippet?.let { snippet ->
                            {
                                Text(
                                    text = snippet,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            scope.launch { sheetState.hide() }.invokeOnCompletion { actions.onJumpToNote(note.id) }
                        },
                    )
                }
            }
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
private val NoteMarkerSize = 22.dp
private val NoteMarkerInset = 6.dp
private val NoteMarkerTouchRadius = 24.dp
private const val NOTE_ICON_RATIO = 0.6f
private val LoupeSize = DpSize(160.dp, 64.dp)
private val LoupeCornerRadius = 32.dp
private val LoupeLift = 80.dp
private const val LOUPE_ZOOM = 2f

@Composable
private fun ReaderTopBar(
    visible: Boolean,
    title: String,
    onBack: () -> Unit,
    onShowNotes: () -> Unit,
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
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                IconButton(onClick = onShowNotes) {
                    Icon(
                        painter = painterResource(R.drawable.ic_notes),
                        contentDescription = stringResource(R.string.notes_title),
                    )
                }
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
            notes = NotesUiState(),
            noteActions = NoteActions.None,
            onBack = {},
            onPageSettled = {},
            onPageRequestHandled = {},
            onZoomGestureStart = {},
            onTransform = { _, _, _ -> },
            onZoomGestureEnd = { _, _ -> },
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
            notes = NotesUiState(),
            noteActions = NoteActions.None,
            onBack = {},
            onPageSettled = {},
            onPageRequestHandled = {},
            onZoomGestureStart = {},
            onTransform = { _, _, _ -> },
            onZoomGestureEnd = { _, _ -> },
            onDoubleTap = {},
            onTap = {},
            loadPage = { _, _ -> null },
        )
    }
}
