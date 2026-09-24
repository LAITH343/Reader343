package com.reader343.ui.reader

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader343.data.repo.BookmarkRepository
import com.reader343.data.repo.HighlightRepository
import com.reader343.data.repo.NoteRepository
import com.reader343.data.repo.ReaderRepository
import com.reader343.data.repo.SessionRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.di.ApplicationScope
import com.reader343.domain.Bookmark
import com.reader343.domain.Chapter
import com.reader343.domain.Highlight
import com.reader343.domain.NewHighlight
import com.reader343.domain.NormRect
import com.reader343.domain.Note
import com.reader343.domain.NoteAnchor
import com.reader343.domain.OutlineEntry
import com.reader343.domain.ReadingPace
import com.reader343.domain.chapterAt
import com.reader343.domain.detectTextLayer
import com.reader343.domain.PageAppearance
import com.reader343.domain.ReadAloudAvailability
import com.reader343.domain.ReadAloudSettings
import com.reader343.domain.SpeechUnit
import com.reader343.domain.TtsIssue
import com.reader343.domain.TtsState
import com.reader343.domain.TtsStatus
import com.reader343.domain.TtsVoice
import com.reader343.domain.estimateReadAloudProgress
import com.reader343.domain.readAloudAvailability
import com.reader343.domain.voicePreferences
import com.reader343.domain.unitAtChar
import com.reader343.pdf.PageBitmapCache
import com.reader343.pdf.PageSize
import com.reader343.pdf.PageText
import com.reader343.pdf.PdfEngine
import com.reader343.pdf.TextLayer
import com.reader343.tts.ReadAloudPlayer
import com.reader343.ui.nav.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data object Error : ReaderUiState
    data class Ready(
        val title: String,
        val pageSizes: List<PageSize>,
        val initialPage: Int,
        val currentPage: Int,
        val chromeVisible: Boolean,
        val pageRequest: Int? = null,
        val outline: List<OutlineEntry> = emptyList(),
        val chapter: Chapter? = null,
        val bookmarks: List<Bookmark> = emptyList(),
        val pace: ReadingPace = ReadingPace.Unknown,
        val session: SessionUi? = null,
        val contentsVisible: Boolean = false,
    ) : ReaderUiState {
        val pageCount: Int get() = pageSizes.size
        val percent: Float get() = if (pageCount == 0) 0f else (currentPage + 1).toFloat() / pageCount
        val bookmarked: Boolean get() = bookmarks.any { it.page == currentPage }
        val hasContents: Boolean get() = outline.isNotEmpty() || bookmarks.isNotEmpty()
    }
}

data class SessionUi(val startedAt: Long, val pages: Int)

class PageDetail(val page: Int, val region: Rect, val tier: Float, val bitmap: ImageBitmap)

private class ReadAloudInputs(
    val tts: TtsState,
    val voices: List<TtsVoice>,
    val availability: ReadAloudAvailability,
    val voicesVisible: Boolean,
    val settings: ReadAloudSettings,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ReaderRepository,
    private val highlightRepository: HighlightRepository,
    private val noteRepository: NoteRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val sessionRepository: SessionRepository,
    settingsRepository: SettingsRepository,
    private val engine: PdfEngine,
    private val player: ReadAloudPlayer,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel(), MarkupActions, NoteActions, ReaderActions, ReadAloudActions {

    private val bookId: Long = checkNotNull(savedStateHandle[Routes.ARG_BOOK_ID])

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _zoom = MutableStateFlow(ZoomState())
    val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()

    private val _detail = MutableStateFlow<PageDetail?>(null)
    val detail: StateFlow<PageDetail?> = _detail.asStateFlow()

    private val _markup = MutableStateFlow(MarkupState())
    val markup: StateFlow<MarkupState> = _markup.asStateFlow()

    private val _notes = MutableStateFlow(NotesUiState())
    val notes: StateFlow<NotesUiState> = _notes.asStateFlow()

    val pageAppearance: StateFlow<PageAppearance> = settingsRepository.settings
        .map { it.pageAppearance }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PageAppearance.Normal)

    private val currentPage = MutableStateFlow(0)
    private val textLayerFlag = MutableStateFlow<Boolean?>(false)
    private val usablePages = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    private val voicesVisible = MutableStateFlow(false)
    private var bookTitle = ""
    private var coverPath: String? = null
    private var spokenPage = -1
    private var spokenUnits: List<SpeechUnit> = emptyList()
    private var spokenSkip = true

    private val availability = combine(textLayerFlag, currentPage, usablePages) { flag, page, usable ->
        readAloudAvailability(flag, usable[page])
    }

    val readAloud: StateFlow<ReadAloudUi> =
        combine(player.state, player.voices, availability, voicesVisible, player.settings, ::ReadAloudInputs)
            .mapLatest(::readAloudUi)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReadAloudUi())

    private val cache = PageBitmapCache()
    private val textLayer = TextLayer(engine)
    private var draft: SelectionDraft? = null
    private var activeHandle = SelectionHandle.End
    private var grabOffset = Offset.Zero
    private var pendingDrag: Offset? = null
    private var selectionColor = HighlightColor.entries.first().argb
    private val inFlight = mutableMapOf<PageBitmapCache.Key, Deferred<Bitmap?>>()
    private var viewport = IntSize.Zero
    private var pageCount = 0
    private var savedPage = -1
    private var chromeJob: Job? = null
    private var zoomJob: Job? = null
    private var touch: Offset? = null
    private var selectionJob: Job? = null
    private var editorKey = 0L
    private var foreground = false
    private var sessionOpen = false
    private var sessionPages = 0
    private var sessionBasePages = 0
    private var sessionUiJob: Job? = null
    private var checkpointJob: Job? = null

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
        val requested = savedStateHandle.get<Int>(Routes.ARG_PAGE) ?: -1
        savedStateHandle[Routes.ARG_PAGE] = -1
        val initial = (if (requested >= 0) requested else book.lastPage).coerceIn(0, pageCount - 1)
        savedPage = initial
        currentPage.value = initial
        bookTitle = book.title
        coverPath = book.coverPath
        textLayerFlag.value = book.hasTextLayer
        val outline = engine.outline()
        _uiState.value = ReaderUiState.Ready(
            title = book.title,
            pageSizes = engine.pageSizes(),
            initialPage = initial,
            currentPage = initial,
            chromeVisible = true,
            outline = outline,
            chapter = outline.chapterAt(initial, pageCount),
        )
        scheduleChromeHide()
        openSession()
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
        viewModelScope.launch {
            noteRepository.observe(bookId).collect { notes ->
                _notes.update { state ->
                    state.copy(
                        loaded = true,
                        byPage = notes.groupBy { it.page },
                        editor = state.editor?.takeIf { editor ->
                            editor.noteId == null || notes.any { it.id == editor.noteId }
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            bookmarkRepository.observe(bookId).collect { bookmarks ->
                updateReady { it.copy(bookmarks = bookmarks) }
            }
        }
        viewModelScope.launch {
            val pace = sessionRepository.pace(bookId)
            updateReady { it.copy(pace = pace) }
        }
        viewModelScope.launch {
            player.state
                .filter { it.bookId == bookId && it.status == TtsStatus.Playing }
                .mapNotNull { it.unit }
                .distinctUntilChanged()
                .collect(::followSpoken)
        }
        preloadText(initial)
        if (book.hasTextLayer == null) checkTextLayer()
    }

    private fun checkTextLayer() {
        viewModelScope.launch {
            val hasTextLayer = try {
                detectTextLayer(pageCount) { engine.extractText(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return@launch
            }
            textLayerFlag.value = hasTextLayer
            repository.setHasTextLayer(bookId, hasTextLayer)
        }
    }

    fun onViewportChanged(size: IntSize) {
        if (size == viewport || size.width <= 0 || size.height <= 0) return
        viewport = size
        stopZoomAnimation()
        cache.clear()
        _detail.value = null
        _zoom.value = ZoomState()
    }

    fun onPageSettled(page: Int) {
        if (page != currentPage.value) {
            currentPage.value = page
            if (sessionOpen && !followingSpeech(page)) sessionPages++
            stopZoomAnimation()
            _zoom.value = ZoomState()
            _detail.value = null
            clearSelection()
            val pages = sessionBasePages + sessionPages
            updateReady { state ->
                state.copy(
                    currentPage = page,
                    chapter = state.outline.chapterAt(page, state.pageCount),
                    session = state.session?.copy(pages = pages),
                )
            }
            preloadText(page)
        }
    }

    fun onForeground() {
        foreground = true
        openSession()
        if (speakingHere()) player.refreshVoices()
    }

    fun onBackground() {
        foreground = false
        player.stopPreview()
        closeSession()
    }

    fun onPageRequestHandled() {
        updateReady { it.copy(pageRequest = null) }
    }

    fun onZoomGestureStart() = stopZoomAnimation()

    fun onTransform(centroid: Offset, pan: Offset, factor: Float) {
        val layout = layoutFor(currentPage.value) ?: return
        stopZoomAnimation()
        _zoom.update { layout.transform(it, centroid, pan, factor) }
    }

    fun onZoomGestureEnd(centroid: Offset, velocity: Velocity) {
        val layout = layoutFor(currentPage.value) ?: return
        val zoom = _zoom.value
        val settled = zoom.scale.coerceIn(ZoomState.MIN_SCALE, ZoomState.MAX_SCALE)
        when {
            settled != zoom.scale -> animateScale(layout, centroid, settled)
            zoom.isZoomed && hypot(velocity.x, velocity.y) >= MIN_FLING_VELOCITY -> fling(layout, velocity)
        }
    }

    fun onDoubleTap(position: Offset) {
        val layout = layoutFor(currentPage.value) ?: return
        val target = if (_zoom.value.isZoomed) ZoomState.MIN_SCALE else ZoomState.DOUBLE_TAP_SCALE
        animateScale(layout, position, target)
    }

    override fun onChromeHold(held: Boolean) {
        if (held) chromeJob?.cancel() else scheduleChromeHide()
    }

    override fun onToggleBookmark() {
        scheduleChromeHide()
        val page = currentPage.value
        viewModelScope.launch { bookmarkRepository.toggle(bookId, page) }
    }

    override fun onRemoveBookmark(page: Int) {
        viewModelScope.launch { bookmarkRepository.remove(bookId, page) }
    }

    override fun onAddPageNote() {
        onDismissSelection()
        startEditor(
            noteId = null,
            page = currentPage.value,
            anchor = NoteAnchor(rect = PAGE_NOTE_RECT, highlightId = null, snippet = null),
            body = "",
        )
    }

    override fun onToggleHighlightMode() {
        if (_markup.value.highlighting) {
            onDismissSelection()
        } else {
            clearSelection()
            _markup.update { it.copy(highlightMode = true) }
            updateReady { it.copy(chromeVisible = true) }
        }
    }

    override fun onDismissSelection() {
        clearSelection()
        _markup.update { it.copy(highlightMode = false) }
        scheduleChromeHide()
    }

    override fun onShowContents() {
        onDismissSelection()
        updateReady { it.copy(contentsVisible = true) }
    }

    override fun onHideContents() {
        updateReady { it.copy(contentsVisible = false) }
        scheduleChromeHide()
    }

    override fun onJumpToPage(page: Int) {
        if (page !in 0 until pageCount) return
        val request = page.takeIf { it != currentPage.value }
        updateReady { it.copy(contentsVisible = false, pageRequest = request) }
        scheduleChromeHide()
    }

    override fun onReadAloud() {
        scheduleChromeHide()
        if (speakingHere()) {
            player.toggle()
            return
        }
        if (readAloud.value.availability != ReadAloudAvailability.Ready) return
        onDismissSelection()
        player.start(bookId, currentPage.value)
    }

    override fun onReadAloudToggle() {
        scheduleChromeHide()
        player.toggle()
    }

    override fun onReadAloudPrevious() {
        scheduleChromeHide()
        player.previous()
    }

    override fun onReadAloudNext() {
        scheduleChromeHide()
        player.next()
    }

    override fun onReadAloudStop() {
        scheduleChromeHide()
        voicesVisible.value = false
        player.stop()
    }

    override fun onReadAloudSpeed() {
        scheduleChromeHide()
        player.cycleSpeed()
    }

    override fun onShowVoices() {
        chromeJob?.cancel()
        player.refreshVoices()
        voicesVisible.value = true
    }

    override fun onHideVoices() {
        voicesVisible.value = false
        player.stopPreview()
        if (readAloud.value.missingLanguage != null) player.dismissIssue()
        scheduleChromeHide()
    }

    override fun onSelectVoice(voice: TtsVoice) {
        player.setVoice(voice)
        player.preview(voice = voice)
    }

    override fun onAwaitVoice() {
        voicesVisible.value = false
        player.awaitVoice()
        scheduleChromeHide()
    }

    override fun onReadInstead() {
        voicesVisible.value = false
        player.readInstead()
        scheduleChromeHide()
    }

    private fun speakingHere(): Boolean {
        val tts = player.state.value
        return tts.bookId == bookId && tts.status != TtsStatus.Idle
    }

    private fun followingSpeech(page: Int): Boolean {
        val tts = player.state.value
        return tts.bookId == bookId && tts.status == TtsStatus.Playing && tts.position?.page == page
    }

    private fun speakFrom(page: Int, point: Offset, layout: PageLayout) {
        clearSelection()
        selectionJob = viewModelScope.launch {
            val text = loadText(page) ?: return@launch
            val index = text.nearestChar(point.x, point.y, layout.page.width / layout.page.height) ?: return@launch
            val unit = unitsFor(page).unitAtChar(index) ?: return@launch
            player.start(bookId, page, unit.sentenceIndex)
        }
    }

    private suspend fun unitsFor(page: Int): List<SpeechUnit> {
        val skip = player.settings.value.skipFurniture
        if (skip != spokenSkip) {
            spokenSkip = skip
            spokenPage = -1
        }
        if (page != spokenPage || spokenUnits.isEmpty()) {
            spokenUnits = player.pageUnits(page)
            spokenPage = page
        }
        return spokenUnits
    }

    private suspend fun readAloudUi(inputs: ReadAloudInputs): ReadAloudUi {
        val tts = inputs.tts
        val own = tts.bookId == bookId && tts.status != TtsStatus.Idle
        val missing = tts.missingLanguage.takeIf {
            own && (tts.issue == TtsIssue.MissingVoiceData || tts.issue == TtsIssue.LanguageUnsupported)
        }
        val base = ReadAloudUi(
            availability = inputs.availability,
            status = if (own) tts.status else TtsStatus.Idle,
            title = bookTitle,
            coverPath = coverPath,
            rate = tts.rate,
            voice = tts.voice,
            locale = tts.locale,
            voices = inputs.voices,
            voicesVisible = own && inputs.voicesVisible,
            highlight = inputs.settings.highlight,
            keepScreenOn = own && inputs.settings.keepScreenOn,
            missingLanguage = missing?.let(Locale::forLanguageTag),
            fallbackLanguage = missing?.let { Locale.forLanguageTag(inputs.settings.voicePreferences.fallback(it)) },
        )
        val position = tts.position
        if (!own || position == null) return base
        val units = unitsFor(position.page)
        val index = units.indexOfFirst { it.sentenceIndex == position.sentenceIndex }
        val chapter = (_uiState.value as? ReaderUiState.Ready)?.outline?.chapterAt(position.page, pageCount)
        val progress = estimateReadAloudProgress(
            pageUnits = units,
            sentenceIndex = position.sentenceIndex,
            page = position.page,
            startPage = chapter?.startPage ?: 0,
            endPage = chapter?.endPage ?: pageCount,
        )
        return base.copy(
            unit = tts.unit?.takeIf { it.page == position.page },
            sentence = if (index >= 0) index + 1 else 0,
            sentences = units.size,
            remainingMs = progress.remainingMs(tts.rate),
            inChapter = chapter != null,
        )
    }

    private fun followSpoken(unit: SpeechUnit) {
        if (unit.page != currentPage.value) {
            if (!_markup.value.highlighting) updateReady { it.copy(pageRequest = unit.page) }
            return
        }
        val zoom = _zoom.value
        if (!zoom.isZoomed) return
        val layout = layoutFor(unit.page) ?: return
        val bounds = unit.rects.reduceOrNull(NormRect::union) ?: return
        val visible = layout.visibleRegion(zoom) ?: return
        val inView = bounds.left >= visible.left && bounds.right <= visible.right &&
            bounds.top >= visible.top && bounds.bottom <= visible.bottom
        if (inView) return
        val center = layout.toContent((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f)
        stopZoomAnimation()
        _zoom.value = layout.clamp(
            zoom.copy(
                offsetX = viewport.width / 2f - center.x * zoom.scale,
                offsetY = viewport.height / 2f - center.y * zoom.scale,
            ),
        )
    }

    private fun animateScale(layout: PageLayout, focus: Offset, target: Float) {
        stopZoomAnimation()
        val start = _zoom.value
        zoomJob = viewModelScope.launch(AndroidUiDispatcher.Main) {
            animate(
                initialValue = start.scale,
                targetValue = target,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ ->
                _zoom.value = layout.clamp(layout.scaleAbout(start, focus, value))
            }
        }
    }

    private fun fling(layout: PageLayout, velocity: Velocity) {
        stopZoomAnimation()
        zoomJob = viewModelScope.launch(AndroidUiDispatcher.Main) {
            var previous = Offset.Zero
            AnimationState(
                typeConverter = Offset.VectorConverter,
                initialValue = Offset.Zero,
                initialVelocityVector = Offset.VectorConverter.convertToVector(Offset(velocity.x, velocity.y)),
            ).animateDecay(exponentialDecay()) {
                val delta = value - previous
                previous = value
                val before = _zoom.value
                val after = layout.pan(before, delta)
                _zoom.value = after
                if (after == before) cancelAnimation()
            }
        }
    }

    private fun stopZoomAnimation() {
        zoomJob?.cancel()
        zoomJob = null
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
        if (speakingHere() && !_markup.value.highlightMode) {
            speakFrom(page, point, layout)
            return
        }
        clearSelection()
        touch = position
        showLoupe()
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

    override fun onHandleGrab(handle: SelectionHandle, position: Offset, grabOffset: Offset) {
        activeHandle = handle
        this.grabOffset = grabOffset
        touch = position
        showLoupe()
    }

    override fun onSelectionDrag(position: Offset) {
        val current = draft
        if (current == null) {
            if (selectionJob?.isActive != true) return
            pendingDrag = position
            touch = position
            showLoupe()
            return
        }
        touch = position
        val layout = layoutFor(current.page) ?: return
        val point = layout.toNormalized(_zoom.value, position + grabOffset)
        val next = when (current) {
            is SelectionDraft.Text -> dragText(current, point, layout)
            is SelectionDraft.Region -> {
                val clamped = Offset(point.x.coerceIn(0f, 1f), point.y.coerceIn(0f, 1f))
                if (activeHandle == SelectionHandle.End) current.copy(end = clamped) else current.copy(start = clamped)
            }
        }
        if (next == null) {
            showLoupe()
            return
        }
        draft = next
        publishSelection()
    }

    override fun onSelectionDragEnd() {
        grabOffset = Offset.Zero
        pendingDrag = null
        touch = null
        _markup.update { it.copy(loupe = null) }
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
        onDismissSelection()
        if (highlight != null) viewModelScope.launch { highlightRepository.add(bookId, highlight) }
    }

    override fun onDeleteHighlight() {
        val active = _markup.value.activeHighlight ?: return
        clearSelection()
        viewModelScope.launch { highlightRepository.delete(active.id) }
    }

    override fun onAddNoteFromSelection() {
        val anchor = when (val current = draft) {
            null -> null
            is SelectionDraft.Text -> {
                val text = textLayer.peek(current.page)
                text?.rectsFor(current.start, current.end)?.reduceOrNull(NormRect::union)?.let { rect ->
                    NoteAnchor(
                        rect = rect,
                        highlightId = null,
                        snippet = text.textFor(current.start, current.end).ifEmpty { null },
                    )
                }
            }
            is SelectionDraft.Region -> current.rect()?.let { NoteAnchor(rect = it, highlightId = null, snippet = null) }
        }
        val page = draft?.page ?: return
        onDismissSelection()
        if (anchor != null) startEditor(noteId = null, page = page, anchor = anchor, body = "")
    }

    override fun onAddNoteFromHighlight() {
        val active = _markup.value.activeHighlight ?: return
        clearSelection()
        val existing = _notes.value.forHighlight(active.id)
        if (existing != null) {
            openEditor(existing)
            return
        }
        val rect = active.rects.reduceOrNull(NormRect::union) ?: return
        startEditor(
            noteId = null,
            page = active.page,
            anchor = NoteAnchor(rect = rect, highlightId = active.id, snippet = active.snippet),
            body = "",
        )
    }

    override fun onOpenNote(noteId: Long) {
        val note = findNote(noteId) ?: return
        clearSelection()
        openEditor(note)
    }

    override fun onSaveNote(body: String) {
        val editor = _notes.value.editor ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        closeEditor()
        viewModelScope.launch {
            if (editor.noteId == null) {
                noteRepository.add(bookId, editor.page, editor.anchor, text)
            } else {
                noteRepository.updateBody(editor.noteId, text)
            }
        }
    }

    override fun onDeleteNote() {
        val noteId = _notes.value.editor?.noteId
        closeEditor()
        if (noteId != null) viewModelScope.launch { noteRepository.delete(noteId) }
    }

    override fun onDismissNote() = closeEditor()

    fun onLeaveForNotes() {
        clearSelection()
        _markup.update { it.copy(highlightMode = false) }
        closeEditor()
    }

    private fun findNote(noteId: Long): Note? =
        _notes.value.byPage.values.firstNotNullOfOrNull { notes -> notes.firstOrNull { it.id == noteId } }

    private fun openEditor(note: Note) =
        startEditor(noteId = note.id, page = note.page, anchor = note.anchor, body = note.body)

    private fun startEditor(noteId: Long?, page: Int, anchor: NoteAnchor, body: String) {
        editorKey++
        _notes.update {
            it.copy(editor = NoteEditor(key = editorKey, noteId = noteId, page = page, anchor = anchor, body = body))
        }
    }

    private fun closeEditor() {
        _notes.update { it.copy(editor = null) }
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
                        text = textLayer.peek(current.page)?.textFor(current.start, current.end)?.ifEmpty { null },
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
        _markup.update { it.copy(selection = selection, activeHighlight = null, loupe = loupeFor(selection)) }
        if (selection != null) updateReady { it.copy(chromeVisible = true) }
    }

    private fun showLoupe() {
        _markup.update { it.copy(loupe = loupeFor(it.selection)) }
    }

    private fun loupeFor(selection: SelectionUi?): Loupe? {
        val position = touch ?: return null
        val point = position + grabOffset
        val layout = selection?.let { layoutFor(it.page) }
        if (selection == null || layout == null || selection.region) return Loupe(point, position)
        val mark = if (activeHandle == SelectionHandle.Start) selection.start else selection.end
        val line = layout.toScreen(_zoom.value, mark.x, (mark.top + mark.bottom) / 2f)
        return Loupe(Offset(point.x, line.y), position)
    }

    private fun clearSelection() {
        selectionJob?.cancel()
        selectionJob = null
        draft = null
        pendingDrag = null
        grabOffset = Offset.Zero
        touch = null
        _markup.update { it.copy(selection = null, activeHighlight = null, loupe = null) }
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
            textLayer.get(page).also { text -> usablePages.update { it + (page to text.isUsable) } }
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
        val visible = layout.visibleRegion(zoom) ?: return
        val tier = DETAIL_TIERS.firstOrNull { it >= zoom.scale - ZoomState.ZOOM_EPSILON } ?: DETAIL_TIERS.last()
        val current = _detail.value
        if (current != null && current.page == page && current.tier == tier && current.region.encloses(visible)) return
        val region = visible.padded(DETAIL_PADDING)
        val widthPx = layout.page.width * tier * region.width
        val heightPx = layout.page.height * tier * region.height
        val limit = sqrt(MAX_DETAIL_PIXELS / (widthPx * heightPx)).coerceAtMost(1f)
        val size = engine.pageSize(page)
        val dpi = layout.page.width * tier * limit / size.widthPt * PdfEngine.POINTS_DPI
        val bitmap = try {
            engine.renderPage(page, dpi, RectF(region.left, region.top, region.right, region.bottom))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return
        }
        if (page == currentPage.value) _detail.value = PageDetail(page, region, tier, bitmap.asImageBitmap())
    }

    private fun Rect.encloses(other: Rect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    private fun Rect.padded(fraction: Float): Rect {
        val dx = width * fraction
        val dy = height * fraction
        return Rect(
            left = (left - dx).coerceAtLeast(0f),
            top = (top - dy).coerceAtLeast(0f),
            right = (right + dx).coerceAtMost(1f),
            bottom = (bottom + dy).coerceAtMost(1f),
        )
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

    private fun openSession() {
        if (sessionOpen || !foreground || _uiState.value !is ReaderUiState.Ready) return
        sessionOpen = true
        sessionPages = 0
        val now = System.currentTimeMillis()
        val start = appScope.async(start = CoroutineStart.UNDISPATCHED) { sessionRepository.open(bookId, now) }
        sessionUiJob = viewModelScope.launch {
            val info = try {
                start.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                return@launch
            }
            sessionBasePages = info.basePages
            val pages = info.basePages + sessionPages
            updateReady { it.copy(session = SessionUi(startedAt = info.startTs, pages = pages)) }
        }
        checkpointJob = viewModelScope.launch {
            while (true) {
                delay(SESSION_CHECKPOINT_MS)
                val at = System.currentTimeMillis()
                val pages = sessionPages
                appScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    sessionRepository.checkpoint(bookId, at, pages)
                }
            }
        }
    }

    private fun closeSession() {
        if (!sessionOpen) return
        sessionOpen = false
        checkpointJob?.cancel()
        checkpointJob = null
        sessionUiJob?.cancel()
        sessionUiJob = null
        sessionBasePages = 0
        updateReady { it.copy(session = null) }
        val now = System.currentTimeMillis()
        val pages = sessionPages
        appScope.launch(start = CoroutineStart.UNDISPATCHED) { sessionRepository.close(bookId, now, pages) }
    }

    private fun scheduleChromeHide() {
        chromeJob?.cancel()
        chromeJob = viewModelScope.launch {
            delay(CHROME_HIDE_MS)
            if (!_markup.value.highlighting) updateReady { it.copy(chromeVisible = false) }
        }
    }

    private inline fun updateReady(transform: (ReaderUiState.Ready) -> ReaderUiState.Ready) {
        _uiState.update { state -> if (state is ReaderUiState.Ready) transform(state) else state }
    }

    override fun onCleared() {
        foreground = false
        closeSession()
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
        const val SESSION_CHECKPOINT_MS = 30_000L
        const val DETAIL_MIN_SCALE = 1.25f
        val DETAIL_TIERS = floatArrayOf(1.5f, 2f, 3f, 4f, 5f)
        const val DETAIL_PADDING = 0.25f
        const val MAX_DETAIL_PIXELS = 12_000_000f
        const val MIN_FLING_VELOCITY = 400f
        const val MAX_BASE_WIDTH_PX = 4096
        const val TEXT_HIT_SLOP = 0.03f
        const val HIGHLIGHT_HIT_SLOP = 0.004f
        const val MIN_REGION_SIZE = 0.01f
        val PAGE_NOTE_RECT = NormRect(0f, 0f, 1f, 0f)
    }
}
