package com.reader343.tts

import android.content.Context
import android.content.res.Configuration
import com.reader343.R
import com.reader343.data.repo.ReaderRepository
import com.reader343.data.repo.SessionHolder
import com.reader343.data.repo.SessionRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.domain.OutlineEntry
import com.reader343.domain.ReadAloudSettings
import com.reader343.domain.SleepTimer
import com.reader343.domain.SpeechUnit
import com.reader343.domain.TtsState
import com.reader343.domain.TtsStatus
import com.reader343.domain.TtsVoice
import com.reader343.domain.chapterAt
import com.reader343.domain.nextReadAloudSpeed
import com.reader343.domain.voicePreferences
import com.reader343.pdf.PdfEngine
import com.reader343.pdf.TextLayer
import com.reader343.pdf.TextLayerSpeechSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
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
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val engines: Provider<PdfEngine>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private var engine: PdfEngine? = null
    private var source: TextLayerSpeechSource? = null
    private var command: Job? = null

    val settings: StateFlow<ReadAloudSettings> = settingsRepository.settings
        .map { it.readAloud }
        .stateIn(scope, SharingStarted.Eagerly, ReadAloudSettings())

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
        scope.launch { settings.collect(::apply) }
        scope.launch { runSleepTimer() }
        scope.launch { trackListening() }
    }

    fun start(bookId: Long, page: Int, sentenceIndex: Int = 0) {
        launchCommand {
            apply(settingsRepository.settings.first().readAloud)
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

    fun cycleSpeed() {
        val next = nextReadAloudSpeed(controller.state.value.rate)
        controller.setRate(next)
        scope.launch { settingsRepository.setReadAloudSpeed(next) }
    }

    fun setVoice(voice: TtsVoice) {
        scope.launch { settingsRepository.setReadAloudVoice(voice.locale.language, voice.name) }
    }

    fun refreshVoices() = controller.refreshVoices()

    fun preview(voice: TtsVoice? = null, speed: Float? = null, pitch: Float? = null) {
        val current = settings.value
        val language = voice?.locale?.language ?: current.voicePreferences.preferred()
        val localized = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val text = context.createConfigurationContext(localized).getString(R.string.read_aloud_preview_sample)
        controller.preview(
            text = text,
            language = language,
            voiceName = voice?.name ?: current.voices[language],
            rate = speed ?: current.speed,
            pitch = pitch ?: current.pitch,
        )
    }

    fun stopPreview() = controller.stopPreview()

    fun readInstead() {
        val language = controller.state.value.missingLanguage ?: return
        controller.substitute(language)
        resume()
    }

    fun awaitVoice() = controller.dismissIssue()

    fun dismissIssue() = controller.clearIssue()

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
        val skip = settings.value.skipFurniture
        return TextLayerSpeechSource(TextLayer(opened), book.id, pageCount, skipFurniture = skip).also { source = it }
    }

    private fun apply(value: ReadAloudSettings) {
        controller.setRate(value.speed)
        controller.setPitch(value.pitch)
        controller.setPreferences(value.voicePreferences)
        controller.setContinuous(value.autoPage)
        val current = source
        if (current != null && current.skipFurniture != value.skipFurniture) {
            val updated = TextLayerSpeechSource(
                current.textLayer,
                current.bookId,
                current.pageCount,
                skipFurniture = value.skipFurniture,
            )
            source = updated
            controller.reload(updated)
        }
    }

    private suspend fun runSleepTimer() {
        val playing = controller.state.map { it.status == TtsStatus.Playing }.distinctUntilChanged()
        val timer = settings.map { it.sleep }.distinctUntilChanged()
        combine(playing, timer, ::Pair).collectLatest { (active, sleep) ->
            if (!active || sleep == SleepTimer.Off) return@collectLatest
            val minutes = sleep.minutes
            if (minutes != null) {
                delay(minutes * MINUTE_MS)
            } else {
                val page = controller.state.value.position?.page ?: return@collectLatest
                val book = _book.value ?: return@collectLatest
                val end = book.outline.chapterAt(page, book.pageCount)?.endPage ?: return@collectLatest
                controller.state.first { (it.position?.page ?: page) >= end }
            }
            controller.pause()
        }
    }

    private suspend fun trackListening() {
        controller.state
            .map { state -> state.bookId.takeIf { state.status == TtsStatus.Playing } }
            .distinctUntilChanged()
            .collectLatest { bookId ->
                if (bookId == null) return@collectLatest
                sessionRepository.open(bookId, System.currentTimeMillis(), SessionHolder.Listening)
                var pages = 0
                try {
                    coroutineScope {
                        launch {
                            controller.state
                                .mapNotNull { state -> state.position?.page?.takeIf { state.bookId == bookId } }
                                .distinctUntilChanged()
                                .drop(1)
                                .collect { pages++ }
                        }
                        while (true) {
                            delay(CHECKPOINT_MS)
                            sessionRepository.checkpoint(bookId, System.currentTimeMillis(), pages, SessionHolder.Listening)
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        sessionRepository.close(bookId, System.currentTimeMillis(), pages, SessionHolder.Listening)
                    }
                }
            }
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
        const val CHECKPOINT_MS = 30_000L
        const val MINUTE_MS = 60_000L
    }
}
