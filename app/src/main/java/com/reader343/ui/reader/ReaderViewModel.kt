package com.reader343.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.HighlightRepository
import com.reader343.data.repo.ReaderRepository
import com.reader343.di.ApplicationScope
import com.reader343.domain.Highlight
import com.reader343.domain.NewHighlight
import com.reader343.domain.NormRect
import com.reader343.pdf.PageBitmapCache
import com.reader343.pdf.PageSize
import com.reader343.pdf.PageText
import com.reader343.pdf.PdfEngine
import com.reader343.pdf.TextLayer
import com.reader343.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data object Error : ReaderUiState
    data class Ready(
        val title: String,
        val pageSizes: List<PageSize>,
        val initialPage: Int,
        val currentPage: Int,
        val chromeVisible: Boolean,
    ) : ReaderUiState {
        val pageCount: Int get() = pageSizes.size
        val percent: Float get() = if (pageCount == 0) 0f else (currentPage + 1).toFloat() / pageCount
    }
}

class PageDetail(val page: Int, val region: Rect, val bitmap: ImageBitmap)

@OptIn(FlowPreview::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ReaderRepository,
    private val highlightRepository: HighlightRepository,
    private val engine: PdfEngine,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel(), MarkupActions {

    private val bookId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _zoom = MutableStateFlow(ZoomState())
    val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()

    private val _detail = MutableStateFlow<PageDetail?>(null)
    val detail: StateFlow<PageDetail?> = _detail.asStateFlow()

    private val _markup = MutableStateFlow(MarkupState())
    val markup: StateFlow<MarkupState> = _markup.asStateFlow()

    private val cache = PageBitmapCache()
    private val textLayer = TextLayer(engine)
    private var draft: SelectionDraft? = null
    private var activeHandle = SelectionHandle.End
    private var grabOffset = Offset.Zero
    private var pendingDrag: Offset? = null
    private var selectionColor = HighlightColor.entries.first().argb
    private val inFlight = mutableMapOf<PageBitmapCache.Key, Deferred<Bitmap?>>()
    private val currentPage = MutableStateFlow(0)
    private var viewport = IntSize.Zero
    private var pageCount = 0
    private var savedPage = -1
    private var chromeJob: Job? = null
    private var selectionJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val book = repository.getBook(bookId)
        if (book == null) {
            _uiState.value = ReaderUiState.Error
            return
        }
        try {
            engine.open(book.filePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            _uiState.value = ReaderUiState.Error
            return
        }
        pageCount = engine.pageCount
        if (pageCount == 0) {
            _uiState.value = ReaderUiState.Error
            return
        }
        val initial = book.lastPage.coerceIn(0, pageCount - 1)
        savedPage = initial
        currentPage.value = initial
        _uiState.value = ReaderUiState.Ready(
            title = book.title,
            pageSizes = engine.pageSizes(),
            initialPage = initial,
            currentPage = initial,
            chromeVisible = true,
        )
        scheduleChromeHide()
        viewModelScope.launch {
            currentPage.drop(1).debounce(SAVE_DEBOUNCE_MS).collect { saveProgress(it) }
        }
        viewModelScope.launch {
            _zoom.debounce(DETAIL_DEBOUNCE_MS).collectLatest { renderDetail(it) }
        }
        viewModelScope.launch {
            highlightRepository.observeByPage(bookId).collect { highlights ->
                _markup.update { state ->
                    state.copy(
                        highlights = highlights,
                        activeHighlight = state.activeHighlight?.let { active ->
                            highlights[active.page]?.firstOrNull { it.id == active.id }
                        },
                    )
                }
            }
        }
        preloadText(initial)
    }

    fun onViewportChanged(size: IntSize) {
        if (size == viewport || size.width <= 0 || size.height <= 0) return
        viewport = size
        cache.clear()
        _detail.value = null
        _zoom.value = ZoomState()
    }

    fun onPageSettled(page: Int) {
        if (page == currentPage.value) return
        currentPage.value = page
        _zoom.value = ZoomState()
        _detail.value = null
        clearSelection()
        updateReady { it.copy(currentPage = page) }
        preloadText(page)
    }

    fun onTransform(centroid: Offset, pan: Offset, factor: Float) {
        val layout = layoutFor(currentPage.value) ?: return
        _zoom.update { layout.transform(it, centroid, pan, factor) }
    }

    fun onDoubleTap(position: Offset) {
        val layout = layoutFor(currentPage.value) ?: return
        _zoom.update { current ->
            if (current.isZoomed) {
                ZoomState()
            } else {
                layout.transform(current, position, Offset.Zero, ZoomState.DOUBLE_TAP_SCALE / current.scale)
            }
        }
    }

    fun onTap(position: Offset) {
        if (draft != null || _markup.value.activeHighlight != null) {
            clearSelection()
            return
        }
        val hit = highlightAt(position)
        if (hit != null) {
            _markup.update { it.copy(activeHighlight = hit) }
            return
        }
        toggleChrome()
    }

    override fun onLongPress(position: Offset) {
        val page = currentPage.value
        val layout = layoutFor(page) ?: return
        val point = layout.toNormalized(_zoom.value, position)
        if (point.x !in 0f..1f || point.y !in 0f..1f) return
        clearSelection()
        val cached = textLayer.peek(page)
        if (cached != null) {
            startSelection(page, point, cached)
        } else {
            selectionJob = viewModelScope.launch {
                val text = loadText(page)
                if (page == currentPage.value) startSelection(page, point, text)
            }
        }
    }

    override fun onHandleGrab(handle: SelectionHandle, grabOffset: Offset) {
        activeHandle = handle
        this.grabOffset = grabOffset
    }

    override fun onSelectionDrag(position: Offset) {
        val current = draft
        if (current == null) {
            pendingDrag = position
            return
        }
        val layout = layoutFor(current.page) ?: return
        val point = layout.toNormalized(_zoom.value, position + grabOffset)
        draft = when (current) {
            is SelectionDraft.Text -> dragText(current, point, layout) ?: return
            is SelectionDraft.Region -> {
                val clamped = Offset(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
                if (activeHandle == SelectionHandle.End) current.copy(end = clamped) else current.copy(start = clamped)
            }
        }
        publishSelection()
    }

    override fun onSelectionDragEnd() {
        grabOffset = Offset.Zero
        pendingDrag = null
    }

    override fun onColorSelected(color: Int) {
        selectionColor = color
        _markup.update { state -> state.copy(selection = state.selection?.copy(color = color)) }
    }

    override fun onConfirmHighlight() {
        val current = draft ?: return
        val highlight = when (current) {
            is SelectionDraft.Text -> {
                val text = textLayer.peek(current.page)
                val rects = text?.rectsFor(current.start, current.end).orEmpty()
                if (text == null || rects.isEmpty()) {
                    null
                } else {
                    NewHighlight(
                        page = current.page,
                        rects = rects,
                        color = selectionColor,
                        charStart = current.start,
                        charEnd = current.end + 1,
                        snippet = text.textFor(current.start, current.end).ifEmpty { null },
                    )
                }
            }
            is SelectionDraft.Region -> current.rect()?.let { rect ->
                NewHighlight(
                    page = current.page,
                    rects = listOf(rect),
                    color = selectionColor,
                    charStart = null,
                    charEnd = null,
                    snippet = null,
                )
            }
        }
        clearSelection()
        if (highlight != null) viewModelScope.launch { highlightRepository.add(bookId, highlight) }
    }

    override fun onDeleteHighlight() {
        val active = _markup.value.activeHighlight ?: return
        clearSelection()
        viewModelScope.launch { highlightRepository.delete(active.id) }
    }

    private fun startSelection(page: Int, point: Offset, text: PageText?) {
        val aspect = engine.pageSize(page).aspectRatio
        val index = text?.takeIf { it.hasText }
            ?.charNear(point.x, point.y, aspect, TEXT_HIT_SLOP / _zoom.value.scale)
        draft = if (text != null && index != null) {
            val word = text.wordRange(index)
            SelectionDraft.Text(page, word.first, word.last)
        } else {
            SelectionDraft.Region(page, point, point)
        }
        activeHandle = SelectionHandle.End
        grabOffset = Offset.Zero
        publishSelection()
        pendingDrag?.let { onSelectionDrag(it) }
        pendingDrag = null
    }

    private fun dragText(current: SelectionDraft.Text, point: Offset, layout: PageLayout): SelectionDraft.Text? {
        val text = textLayer.peek(current.page) ?: return null
        val aspect = layout.page.width / layout.page.height
        val index = text.nearestChar(point.x, point.y, aspect) ?: return null
        return if (activeHandle == SelectionHandle.End) {
            if (index >= current.start) {
                current.copy(end = index)
            } else {
                activeHandle = SelectionHandle.Start
                current.copy(start = index, end = current.start)
            }
        } else {
            if (index <= current.end) {
                current.copy(start = index)
            } else {
                activeHandle = SelectionHandle.End
                current.copy(start = current.end, end = index)
            }
        }
    }

    private fun publishSelection() {
        val selection = when (val current = draft) {
            null -> null
            is SelectionDraft.Text -> {
                val rects = textLayer.peek(current.page)?.rectsFor(current.start, current.end).orEmpty()
                val first = rects.firstOrNull()
                val last = rects.lastOrNull()
                if (first == null || last == null) {
                    null
                } else {
                    SelectionUi(
                        page = current.page,
                        rects = rects,
                        start = HandleMark(first.left, first.top, first.bottom),
                        end = HandleMark(last.right, last.top, last.bottom),
                        region = false,
                        color = selectionColor,
                    )
                }
            }
            is SelectionDraft.Region -> SelectionUi(
                page = current.page,
                rects = listOfNotNull(current.rect()),
                start = HandleMark(current.start.x, current.start.y, current.start.y),
                end = HandleMark(current.end.x, current.end.y, current.end.y),
                region = true,
                color = selectionColor,
            )
        }
        _markup.update { it.copy(selection = selection, activeHighlight = null) }
    }

    private fun clearSelection() {
        selectionJob?.cancel()
        selectionJob = null
        draft = null
        pendingDrag = null
        grabOffset = Offset.Zero
        _markup.update { it.copy(selection = null, activeHighlight = null) }
    }

    private fun highlightAt(position: Offset): Highlight? {
        val page = currentPage.value
        val layout = layoutFor(page) ?: return null
        val point = layout.toNormalized(_zoom.value, position)
        val slop = HIGHLIGHT_HIT_SLOP / _zoom.value.scale
        val aspect = layout.page.width / layout.page.height
        return _markup.value.highlights[page]
            ?.lastOrNull { it.contains(point.x, point.y, slop, slop * aspect) }
    }

    private fun preloadText(page: Int) {
        viewModelScope.launch { loadText(page) }
    }

    private suspend fun loadText(page: Int): PageText? =
        try {
            textLayer.get(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }

    private fun toggleChrome() {
        val ready = _uiState.value as? ReaderUiState.Ready ?: return
        if (ready.chromeVisible) {
            chromeJob?.cancel()
            updateReady { it.copy(chromeVisible = false) }
        } else {
            updateReady { it.copy(chromeVisible = true) }
            scheduleChromeHide()
        }
    }

    suspend fun pageBitmap(page: Int, viewportSize: IntSize): ImageBitmap? {
        onViewportChanged(viewportSize)
        val key = keyFor(page) ?: return null
        val bitmap = cache[key] ?: renderAsync(key).await()
        prefetchAround(page)
        return bitmap?.asImageBitmap()
    }

    private fun prefetchAround(page: Int) {
        for (neighbor in intArrayOf(page + 1, page - 1)) {
            if (neighbor !in 0 until pageCount) continue
            val key = keyFor(neighbor) ?: continue
            if (cache[key] == null) renderAsync(key)
        }
    }

    private fun renderAsync(key: PageBitmapCache.Key): Deferred<Bitmap?> =
        inFlight.getOrPut(key) {
            viewModelScope.async(start = CoroutineStart.LAZY) {
                try {
                    val size = engine.pageSize(key.page)
                    val dpi = key.widthPx / size.widthPt * PdfEngine.POINTS_DPI
                    engine.renderPage(key.page, dpi).also { cache[key] = it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    null
                } finally {
                    inFlight.remove(key)
                }
            }.also { it.start() }
        }

    private suspend fun renderDetail(zoom: ZoomState) {
        val page = currentPage.value
        if (zoom.scale < DETAIL_MIN_SCALE) {
            _detail.value = null
            return
        }
        val layout = layoutFor(page) ?: return
        val region = layout.visibleRegion(zoom) ?: return
        val size = engine.pageSize(page)
        val dpi = layout.page.width * zoom.scale / size.widthPt * PdfEngine.POINTS_DPI
        val bitmap = try {
            engine.renderPage(page, dpi, RectF(region.left, region.top, region.right, region.bottom))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        if (page == currentPage.value) _detail.value = PageDetail(page, region, bitmap.asImageBitmap())
    }

    private fun keyFor(page: Int): PageBitmapCache.Key? {
        val layout = layoutFor(page) ?: return null
        val width = layout.page.width.roundToInt().coerceIn(1, MAX_BASE_WIDTH_PX)
        return PageBitmapCache.Key(page, width)
    }

    private fun layoutFor(page: Int): PageLayout? {
        if (viewport == IntSize.Zero || page !in 0 until pageCount) return null
        return PageLayout.fit(viewport, engine.pageSize(page))
    }

    private suspend fun saveProgress(page: Int) {
        repository.saveProgress(bookId, page, pageCount)
        savedPage = page
    }

    private fun scheduleChromeHide() {
        chromeJob?.cancel()
        chromeJob = viewModelScope.launch {
            delay(CHROME_HIDE_MS)
            updateReady { it.copy(chromeVisible = false) }
        }
    }

    private inline fun updateReady(transform: (ReaderUiState.Ready) -> ReaderUiState.Ready) {
        _uiState.update { state -> if (state is ReaderUiState.Ready) transform(state) else state }
    }

    override fun onCleared() {
        val page = currentPage.value
        val pending = pageCount > 0 && page != savedPage
        val count = pageCount
        appScope.launch {
            if (pending) repository.saveProgress(bookId, page, count)
            engine.close()
        }
        cache.clear()
        textLayer.clear()
    }

    private sealed interface SelectionDraft {
        val page: Int

        data class Text(override val page: Int, val start: Int, val end: Int) : SelectionDraft

        data class Region(override val page: Int, val start: Offset, val end: Offset) : SelectionDraft {
            fun rect(): NormRect? = NormRect.spanning(start.x, start.y, end.x, end.y)
                .takeIf { it.width >= MIN_REGION_SIZE && it.height >= MIN_REGION_SIZE }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L
        const val DETAIL_DEBOUNCE_MS = 150L
        const val CHROME_HIDE_MS = 3_000L
        const val DETAIL_MIN_SCALE = 1.25f
        const val MAX_BASE_WIDTH_PX = 4096
        const val TEXT_HIT_SLOP = 0.03f
        const val HIGHLIGHT_HIT_SLOP = 0.004f
        const val MIN_REGION_SIZE = 0.01f
    }
}
