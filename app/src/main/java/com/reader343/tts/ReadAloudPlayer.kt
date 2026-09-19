package com.reader343.tts

import android.content.Context
import com.reader343.data.repo.ReaderRepository
import com.reader343.domain.OutlineEntry
import com.reader343.domain.SpeechUnit
import com.reader343.domain.TtsState
import com.reader343.domain.TtsStatus
import com.reader343.domain.TtsVoice
import com.reader343.domain.nextReadAloudSpeed
import com.reader343.pdf.PdfEngine
import com.reader343.pdf.TextLayer
import com.reader343.pdf.TextLayerSpeechSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

data class ReadAloudBook(
    val id: Long,
    val title: String,
    val pageCount: Int,
    val coverPath: String?,
    val outline: List<OutlineEntry>,
)

@Singleton
class ReadAloudPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: TtsController,
    private val repository: ReaderRepository,
    private val engines: Provider<PdfEngine>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var engine: PdfEngine? = null
    private var source: TextLayerSpeechSource? = null
    private var command: Job? = null

    private val _book = MutableStateFlow<ReadAloudBook?>(null)
    val book: StateFlow<ReadAloudBook?> = _book.asStateFlow()

    val state: StateFlow<TtsState> get() = controller.state

    val voices: StateFlow<List<TtsVoice>> get() = controller.voices

    init {
        scope.launch {
            controller.state
                .map { state ->
                    val page = state.position?.page
                    if (state.status == TtsStatus.Idle || page == null) null else state.bookId to page
                }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (bookId, page) ->
                    val book = _book.value?.takeIf { it.id == bookId } ?: return@collect
                    repository.saveProgress(book.id, page, book.pageCount)
                }
        }
        scope.launch {
            controller.state.map { it.status }.distinctUntilChanged().collectLatest { status ->
                if (status != TtsStatus.Idle) return@collectLatest
                delay(IDLE_RELEASE_MS)
                mutex.withLock {
                    if (controller.state.value.status == TtsStatus.Idle && command?.isActive != true) {
                        closeBook(releaseSpeech = true)
                    }
                }
            }
        }
    }

    fun start(bookId: Long, page: Int, sentenceIndex: Int = 0) {
        launchCommand {
            val source = mutex.withLock { open(bookId) } ?: return@launchCommand
            controller.clearIssue()
            controller.play(source, page, sentenceIndex)
            awaitPlaybackAndPromote()
        }
    }

    fun resume() {
        if (controller.state.value.status != TtsStatus.Paused) return
        launchCommand {
            controller.clearIssue()
            controller.resume()
            awaitPlaybackAndPromote()
        }
    }

    fun toggle() {
        when (controller.state.value.status) {
            TtsStatus.Playing -> controller.pause()
            TtsStatus.Paused -> resume()
            TtsStatus.Idle -> {
                val book = _book.value ?: return
                val position = controller.state.value.position ?: return
                start(book.id, position.page, position.sentenceIndex)
            }
        }
    }

    fun pause() = controller.pause()

    fun stop() {
        command?.cancel()
        controller.stop()
    }

    fun next() = controller.next()

    fun previous() = controller.previous()

    fun cycleSpeed() = controller.setRate(nextReadAloudSpeed(controller.state.value.rate))

    fun setVoice(voice: TtsVoice?) = controller.setVoice(voice)

    fun refreshVoices() = controller.refreshVoices()

    suspend fun pageUnits(page: Int): List<SpeechUnit> {
        val source = source ?: return emptyList()
        return try {
            source.units(page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private fun launchCommand(block: suspend () -> Unit) {
        command?.cancel()
        command = scope.launch { block() }
    }

    private suspend fun awaitPlaybackAndPromote() {
        val outcome = withTimeoutOrNull(START_TIMEOUT_MS) {
            controller.state.first { it.status == TtsStatus.Playing || it.issue != null }
        }
        if (outcome?.status == TtsStatus.Playing) TtsPlaybackService.start(context)
    }

    private suspend fun open(bookId: Long): TextLayerSpeechSource? {
        source?.takeIf { it.bookId == bookId }?.let { return it }
        controller.stop()
        closeBook(releaseSpeech = false)
        val book = repository.getBook(bookId) ?: return null
        val opened = engines.get()
        try {
            opened.open(book.filePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        val pageCount = opened.pageCount
        engine = opened
        _book.value = ReadAloudBook(
            id = book.id,
            title = book.title,
            pageCount = pageCount,
            coverPath = book.coverPath,
            outline = opened.outline(),
        )
        return TextLayerSpeechSource(TextLayer(opened), book.id, pageCount).also { source = it }
    }

    private suspend fun closeBook(releaseSpeech: Boolean) {
        val opened = engine ?: return
        if (releaseSpeech) controller.release()
        engine = null
        source = null
        _book.value = null
        opened.close()
    }

    private companion object {
        const val START_TIMEOUT_MS = 15_000L
        const val IDLE_RELEASE_MS = 60_000L
    }
}
