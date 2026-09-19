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
import com.reader343.domain.VoicePreferences
import com.reader343.domain.position
import com.reader343.domain.speechLanguage
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
    private var drained = false
    private var blocked: Blocked? = null
    private var preferences = VoicePreferences()
    private var continuous = true
    private val substitutes = mutableSetOf<String>()
    private var appliedLanguage: String? = null
    private var appliedVoice: TtsVoice? = null
    private val queuedVoices = HashMap<String, TtsVoice?>()
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

    fun reload(source: SpeechSource) {
        if (cursor?.source === source) return
        cursor = SpeechCursor(source)
        restartIfPlaying()
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

    fun setPreferences(value: VoicePreferences) {
        if (value == preferences) return
        preferences = value
        appliedLanguage = null
        syncPreferredVoice()
        restartIfPlaying()
    }

    private fun syncPreferredVoice() {
        val current = _state.value.voice ?: return
        val name = preferences.voices[current.locale.language] ?: return
        if (name == current.name) return
        val preferred = _voices.value.firstOrNull { it.installed && it.name == name } ?: return
        _state.update { it.copy(voice = preferred, locale = preferred.locale) }
    }

    fun setContinuous(value: Boolean) {
        continuous = value
    }

    fun substitute(language: String) {
        substitutes += language
        appliedLanguage = null
        clearIssue()
    }

    fun clearIssue() {
        _state.update { it.copy(issue = null, missingLanguage = null) }
    }

    fun dismissIssue() {
        _state.update { it.copy(issue = null) }
    }

    fun refreshVoices() {
        scope.launch {
            val engine = engine() ?: return@launch
            loadVoices(engine)
            val missing = _state.value.missingLanguage
            if (missing != null && _voices.value.any { it.installed && it.locale.language == missing }) {
                appliedLanguage = null
                clearIssue()
                if (_state.value.status == TtsStatus.Paused) resume()
            }
            if (cursor == null && _state.value.status == TtsStatus.Idle) shutdown()
        }
    }

    fun release() {
        command?.cancel()
        silence()
        shutdown()
        cursor = null
        substitutes.clear()
        _state.update { TtsState(rate = it.rate, pitch = it.pitch) }
    }

    private fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = null
        appliedLanguage = null
        appliedVoice = null
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
        queuedVoices.clear()
        lastQueuedPage = -1
        endReached = false
        drained = false
        blocked = null
        tts?.stop()
    }

    private suspend fun speakFrom(position: SpeechPosition) {
        val cursor = cursor ?: return
        val engine = engine() ?: return
        engine.setSpeechRate(_state.value.rate)
        engine.setPitch(_state.value.pitch)
        val units = guarded { cursor.from(position) } ?: return
        if (units.isEmpty()) {
            finish()
            return
        }
        errors = 0
        if (!enqueue(engine, units, TextToSpeech.QUEUE_FLUSH)) return
        val first = units.first()
        val voice = queuedVoices[first.id]
        _state.update {
            it.copy(
                status = TtsStatus.Playing,
                position = first.position,
                unit = first,
                voice = voice,
                locale = voice?.locale,
                issue = null,
                missingLanguage = null,
            )
        }
    }

    private fun enqueue(engine: TextToSpeech, units: List<SpeechUnit>, mode: Int): Boolean {
        units.forEachIndexed { index, unit ->
            val language = languageFor(unit)
            val issue = applyLanguage(engine, language)
            if (issue != null) {
                val pending = Blocked(unit, issue, language)
                if (index == 0 && (mode == TextToSpeech.QUEUE_FLUSH || drained)) {
                    failAt(pending)
                    return false
                }
                blocked = pending
                lastQueuedPage = unit.page
                return true
            }
            queued[unit.id] = unit
            queuedVoices[unit.id] = appliedVoice
            val queueMode = if (index == 0) mode else TextToSpeech.QUEUE_ADD
            if (engine.speak(unit.text, queueMode, null, unit.id) != TextToSpeech.SUCCESS) {
                fail(TtsIssue.PlaybackFailed)
                return false
            }
            drained = false
        }
        lastQueuedPage = units.last().page
        return true
    }

    private fun onUnitStart(id: String) {
        val unit = queued[id] ?: return
        if (_state.value.status != TtsStatus.Playing) return
        val iterator = queued.keys.iterator()
        while (iterator.hasNext() && iterator.next() != id) iterator.remove()
        val voice = queuedVoices[id]
        _state.update { it.copy(position = unit.position, unit = unit, voice = voice, locale = voice?.locale) }
        if (queued.keys.last() == id && continuous) appendNext()
    }

    private fun onUnitDone(id: String) {
        if (_state.value.status != TtsStatus.Playing) return
        if (!queued.containsKey(id)) return
        errors = 0
        if (queued.keys.last() != id) return
        advance()
    }

    private fun onUnitError(id: String, code: Int) {
        if (_state.value.status != TtsStatus.Playing) return
        if (!queued.containsKey(id)) return
        when {
            code == TextToSpeech.ERROR_NOT_INSTALLED_YET -> fail(TtsIssue.MissingVoiceData, appliedLanguage)
            code == TextToSpeech.ERROR_NETWORK || code == TextToSpeech.ERROR_NETWORK_TIMEOUT ->
                fail(TtsIssue.MissingVoiceData, appliedLanguage)
            ++errors >= MAX_ERRORS -> fail(TtsIssue.PlaybackFailed)
            queued.keys.last() == id -> advance()
        }
    }

    private fun advance() {
        drained = true
        val pending = blocked
        when {
            pending != null -> failAt(pending)
            endReached -> finish()
            !continuous -> holdAtNextPage()
            else -> appendNext()
        }
    }

    private fun appendNext() {
        if (append?.isActive == true || endReached || blocked != null) return
        val cursor = cursor ?: return
        val page = lastQueuedPage
        append = scope.launch {
            val engine = tts ?: return@launch
            val units = guarded { cursor.after(page).orEmpty() } ?: return@launch
            if (units.isEmpty()) {
                endReached = true
                if (drained) finish()
                return@launch
            }
            enqueue(engine, units, TextToSpeech.QUEUE_ADD)
        }
    }

    private fun holdAtNextPage() {
        val cursor = cursor ?: return
        val page = lastQueuedPage
        append = scope.launch {
            val units = guarded { cursor.after(page).orEmpty() } ?: return@launch
            val first = units.firstOrNull()
            if (first == null) {
                finish()
                return@launch
            }
            silence()
            _state.update { it.copy(status = TtsStatus.Paused, position = first.position, unit = first) }
        }
    }

    private fun finish() {
        silence()
        _state.update { it.copy(status = TtsStatus.Idle, unit = null) }
    }

    private fun failAt(pending: Blocked) {
        _state.update { it.copy(position = pending.unit.position, unit = pending.unit) }
        fail(pending.issue, pending.language)
    }

    private fun fail(issue: TtsIssue, language: String? = null) {
        silence()
        _state.update {
            it.copy(
                status = if (it.position != null) TtsStatus.Paused else TtsStatus.Idle,
                issue = issue,
                missingLanguage = language ?: it.missingLanguage,
            )
        }
    }

    private fun languageFor(unit: SpeechUnit): String {
        val language = speechLanguage(unit.text, preferences.preferred())
        return if (language in substitutes) preferences.fallback(language) else language
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

    private fun applyLanguage(engine: TextToSpeech, language: String): TtsIssue? {
        if (language == appliedLanguage) return null
        val installed = offlineVoices(engine).filter(::isInstalled)
        val preferred = preferences.voices[language]?.let { name -> installed.firstOrNull { it.name == name } }
        val chosen = preferred ?: defaultVoice(engine, installed.filter { it.locale.language == language })
        if (chosen != null && engine.setVoice(chosen) == TextToSpeech.SUCCESS) {
            appliedLanguage = language
            appliedVoice = chosen.toTtsVoice()
            return null
        }
        when (engine.setLanguage(Locale.forLanguageTag(language))) {
            TextToSpeech.LANG_MISSING_DATA -> return TtsIssue.MissingVoiceData
            TextToSpeech.LANG_NOT_SUPPORTED -> return TtsIssue.LanguageUnsupported
        }
        val active = activeVoice(engine)
        if (active == null || active.isNetworkConnectionRequired || !isInstalled(active)) return TtsIssue.MissingVoiceData
        appliedLanguage = language
        appliedVoice = active.toTtsVoice()
        return null
    }

    private fun defaultVoice(engine: TextToSpeech, candidates: List<Voice>): Voice? {
        if (candidates.isEmpty()) return null
        val engineDefault = runCatching { engine.defaultVoice }.getOrNull()
        candidates.firstOrNull { it.name == engineDefault?.name }?.let { return it }
        val country = Locale.getDefault().country
        return candidates.sortedWith(
            compareByDescending<Voice> { it.locale.country == country }
                .thenByDescending { it.quality }
                .thenBy { it.latency },
        ).first()
    }

    private fun Voice.toTtsVoice() = TtsVoice(name = name, locale = locale, installed = isInstalled(this))

    private fun loadVoices(engine: TextToSpeech) {
        _voices.value = offlineVoices(engine)
            .map { it.toTtsVoice() }
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
    }

    private fun offlineVoices(engine: TextToSpeech): List<Voice> =
        runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .filterNot { it.isNetworkConnectionRequired }

    private fun activeVoice(engine: TextToSpeech): Voice? = runCatching { engine.voice }.getOrNull()

    private fun isInstalled(voice: Voice): Boolean =
        !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    private class Blocked(val unit: SpeechUnit, val issue: TtsIssue, val language: String)

    companion object {
        const val MIN_RATE = 0.25f
        const val MAX_RATE = 4f
        const val MIN_PITCH = 0.5f
        const val MAX_PITCH = 2f
        private const val MAX_ERRORS = 3
    }
}
