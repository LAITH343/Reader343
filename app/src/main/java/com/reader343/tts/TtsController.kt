package com.reader343.tts

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.reader343.domain.SpeechPosition
import com.reader343.domain.SpeechSource
import com.reader343.domain.SpeechUnit
import com.reader343.domain.TtsIssue
import com.reader343.domain.TtsState
import com.reader343.domain.TtsStatus
import com.reader343.domain.TtsVoice
import com.reader343.domain.position
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    private val _voices = MutableStateFlow<List<TtsVoice>>(emptyList())
    val voices: StateFlow<List<TtsVoice>> = _voices.asStateFlow()

    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private var cursor: SpeechCursor? = null
    private val queued = LinkedHashMap<String, SpeechUnit>()
    private var lastQueuedPage = -1
    private var endReached = false
    private var errors = 0
    private var command: Job? = null
    private var append: Job? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            scope.launch { onUnitStart(utteranceId) }
        }

        override fun onDone(utteranceId: String) {
            scope.launch { onUnitDone(utteranceId) }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            scope.launch { onUnitError(utteranceId, TextToSpeech.ERROR) }
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            scope.launch { onUnitError(utteranceId, errorCode) }
        }
    }

    fun play(source: SpeechSource, page: Int, sentenceIndex: Int = 0) {
        if (cursor?.source !== source) {
            cursor = SpeechCursor(source)
            _state.update { it.copy(bookId = source.bookId, position = null, unit = null) }
        }
        launchCommand { speakFrom(SpeechPosition(page, sentenceIndex)) }
    }

    fun pause() {
        if (_state.value.status != TtsStatus.Playing) return
        command?.cancel()
        silence()
        _state.update { it.copy(status = TtsStatus.Paused) }
    }

    fun resume() {
        val current = _state.value
        if (current.status != TtsStatus.Paused) return
        val position = current.position ?: return
        launchCommand { speakFrom(position) }
    }

    fun toggle() {
        when (_state.value.status) {
            TtsStatus.Playing -> pause()
            TtsStatus.Paused -> resume()
            TtsStatus.Idle -> _state.value.position?.let { position ->
                cursor?.let { play(it.source, position.page, position.sentenceIndex) }
            }
        }
    }

    fun stop() {
        command?.cancel()
        silence()
        _state.update { it.copy(status = TtsStatus.Idle, unit = null) }
    }

    fun next() = step { cursor, position -> cursor.next(position) }

    fun previous() = step { cursor, position -> cursor.previous(position) }

    fun setRate(rate: Float) {
        val value = rate.coerceIn(MIN_RATE, MAX_RATE)
        if (value == _state.value.rate) return
        _state.update { it.copy(rate = value) }
        restartIfPlaying()
    }

    fun setPitch(pitch: Float) {
        val value = pitch.coerceIn(MIN_PITCH, MAX_PITCH)
        if (value == _state.value.pitch) return
        _state.update { it.copy(pitch = value) }
        restartIfPlaying()
    }

    fun setLocale(locale: Locale?) {
        if (locale == _state.value.locale) return
        _state.update { it.copy(locale = locale, voice = null) }
        restartIfPlaying()
    }

    fun setVoice(voice: TtsVoice?) {
        if (voice == _state.value.voice) return
        _state.update { it.copy(voice = voice, locale = voice?.locale ?: it.locale) }
        restartIfPlaying()
    }

    fun clearIssue() {
        _state.update { it.copy(issue = null) }
    }

    fun refreshVoices() {
        scope.launch { engine()?.let(::loadVoices) }
    }

    fun release() {
        command?.cancel()
        silence()
        tts?.shutdown()
        tts = null
        ready = null
        cursor = null
        _state.update { TtsState(rate = it.rate, pitch = it.pitch, locale = it.locale, voice = it.voice) }
    }

    private fun step(target: suspend (SpeechCursor, SpeechPosition) -> SpeechUnit?) {
        val cursor = cursor ?: return
        val position = _state.value.position ?: return
        launchCommand {
            val unit = guarded { target(cursor, position) } ?: return@launchCommand
            if (_state.value.status == TtsStatus.Playing) {
                speakFrom(unit.position)
            } else {
                _state.update { it.copy(position = unit.position, unit = unit) }
            }
        }
    }

    private fun restartIfPlaying() {
        val current = _state.value
        if (current.status != TtsStatus.Playing) return
        val position = current.position ?: return
        launchCommand { speakFrom(position) }
    }

    private fun launchCommand(block: suspend () -> Unit) {
        command?.cancel()
        silence()
        command = scope.launch { block() }
    }

    private fun silence() {
        append?.cancel()
        queued.clear()
        lastQueuedPage = -1
        endReached = false
        tts?.stop()
    }

    private suspend fun speakFrom(position: SpeechPosition) {
        val cursor = cursor ?: return
        val engine = engine() ?: return
        if (!configure(engine, cursor.source.locale)) return
        val units = guarded { cursor.from(position) } ?: return
        if (units.isEmpty()) {
            finish()
            return
        }
        errors = 0
        if (!enqueue(engine, units, TextToSpeech.QUEUE_FLUSH)) return
        val first = units.first()
        _state.update { it.copy(status = TtsStatus.Playing, position = first.position, unit = first, issue = null) }
    }

    private fun enqueue(engine: TextToSpeech, units: List<SpeechUnit>, mode: Int): Boolean {
        units.forEachIndexed { index, unit ->
            queued[unit.id] = unit
            val queueMode = if (index == 0) mode else TextToSpeech.QUEUE_ADD
            if (engine.speak(unit.text, queueMode, null, unit.id) != TextToSpeech.SUCCESS) {
                fail(TtsIssue.PlaybackFailed)
                return false
            }
        }
        lastQueuedPage = units.last().page
        return true
    }

    private fun onUnitStart(id: String) {
        val unit = queued[id] ?: return
        if (_state.value.status != TtsStatus.Playing) return
        val iterator = queued.keys.iterator()
        while (iterator.hasNext() && iterator.next() != id) iterator.remove()
        _state.update { it.copy(position = unit.position, unit = unit) }
        if (queued.keys.last() == id) appendNext()
    }

    private fun onUnitDone(id: String) {
        if (_state.value.status != TtsStatus.Playing) return
        if (!queued.containsKey(id)) return
        errors = 0
        if (queued.keys.last() != id) return
        if (endReached) finish() else appendNext()
    }

    private fun onUnitError(id: String, code: Int) {
        if (_state.value.status != TtsStatus.Playing) return
        if (!queued.containsKey(id)) return
        when {
            code == TextToSpeech.ERROR_NOT_INSTALLED_YET -> fail(TtsIssue.MissingVoiceData)
            code == TextToSpeech.ERROR_NETWORK || code == TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                fail(TtsIssue.MissingVoiceData)
            ++errors >= MAX_ERRORS -> fail(TtsIssue.PlaybackFailed)
            queued.keys.last() == id -> if (endReached) finish() else appendNext()
        }
    }

    private fun appendNext() {
        if (append?.isActive == true || endReached) return
        val cursor = cursor ?: return
        val page = lastQueuedPage
        append = scope.launch {
            val engine = tts ?: return@launch
            val units = guarded { cursor.after(page) } ?: return@launch
            if (units == null) {
                endReached = true
                if (!engine.isSpeaking) finish()
                return@launch
            }
            enqueue(engine, units, TextToSpeech.QUEUE_ADD)
        }
    }

    private fun finish() {
        silence()
        _state.update { it.copy(status = TtsStatus.Idle, unit = null) }
    }

    private fun fail(issue: TtsIssue) {
        silence()
        _state.update {
            it.copy(status = if (it.position != null) TtsStatus.Paused else TtsStatus.Idle, issue = issue)
        }
    }

    private suspend fun <T> guarded(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            fail(TtsIssue.PlaybackFailed)
            null
        }

    private suspend fun engine(): TextToSpeech? {
        val existing = tts
        val pending = ready
        if (existing != null && pending != null) {
            if (pending.await()) return existing
            fail(TtsIssue.EngineUnavailable)
            return null
        }
        val deferred = CompletableDeferred<Boolean>()
        ready = deferred
        val created = TextToSpeech(context) { status -> deferred.complete(status == TextToSpeech.SUCCESS) }
        created.setOnUtteranceProgressListener(listener)
        created.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts = created
        if (!deferred.await()) {
            created.shutdown()
            if (tts === created) {
                tts = null
                ready = null
            }
            fail(TtsIssue.EngineUnavailable)
            return null
        }
        loadVoices(created)
        return created
    }

    private fun configure(engine: TextToSpeech, fallback: Locale): Boolean {
        val current = _state.value
        engine.setSpeechRate(current.rate)
        engine.setPitch(current.pitch)
        val chosen = current.voice?.let { voice -> offlineVoices(engine).firstOrNull { it.name == voice.name } }
        if (chosen != null) {
            if (chosen.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                fail(TtsIssue.MissingVoiceData)
                return false
            }
            if (engine.setVoice(chosen) == TextToSpeech.SUCCESS) return true
        }
        val locale = current.locale ?: fallback
        when (engine.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA -> {
                fail(TtsIssue.MissingVoiceData)
                return false
            }
            TextToSpeech.LANG_NOT_SUPPORTED -> {
                fail(TtsIssue.LanguageUnsupported)
                return false
            }
        }
        if (activeVoice(engine)?.isNetworkConnectionRequired == true) {
            val offline = offlineVoices(engine)
                .filter { it.locale.language == locale.language && isInstalled(it) }
                .maxByOrNull { it.quality }
            if (offline == null) {
                fail(TtsIssue.MissingVoiceData)
                return false
            }
            engine.setVoice(offline)
        }
        return true
    }

    private fun loadVoices(engine: TextToSpeech) {
        _voices.value = offlineVoices(engine)
            .map { TtsVoice(name = it.name, locale = it.locale, installed = isInstalled(it)) }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
    }

    private fun offlineVoices(engine: TextToSpeech): List<Voice> =
        runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filterNot { it.isNetworkConnectionRequired }

    private fun activeVoice(engine: TextToSpeech): Voice? = runCatching { engine.voice }.getOrNull()

    private fun isInstalled(voice: Voice): Boolean =
        !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    companion object {
        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2f
        private const val MAX_ERRORS = 3
    }
}
