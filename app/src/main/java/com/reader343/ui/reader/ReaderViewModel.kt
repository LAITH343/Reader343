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
import com.reader343.data.repo.ReaderRepository
import com.reader343.di.ApplicationScope
import com.reader343.pdf.PageBitmapCache
import com.reader343.pdf.PageSize
import com.reader343.pdf.PdfEngine
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
    private val engine: PdfEngine,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val bookId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _zoom = MutableStateFlow(ZoomState())
    val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()

    private val _detail = MutableStateFlow<PageDetail?>(null)
    val detail: StateFlow<PageDetail?> = _detail.asStateFlow()

    private val cache = PageBitmapCache()
    private val inFlight = mutableMapOf<PageBitmapCache.Key, Deferred<Bitmap?>>()
    private val currentPage = MutableStateFlow(0)
    private var viewport = IntSize.Zero
    private var pageCount = 0
    private var savedPage = -1
    private var chromeJob: Job? = null

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
        updateReady { it.copy(currentPage = page) }
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

    fun onTap() {
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
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L
        const val DETAIL_DEBOUNCE_MS = 150L
        const val CHROME_HIDE_MS = 3_000L
        const val DETAIL_MIN_SCALE = 1.25f
        const val MAX_BASE_WIDTH_PX = 4096
    }
}
