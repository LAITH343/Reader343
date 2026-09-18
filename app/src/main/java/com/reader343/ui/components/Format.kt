package com.reader343.ui.components

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import com.reader343.R
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
@ReadOnlyComposable
fun formatPercent(fraction: Float): String =
    NumberFormat.getPercentInstance(currentLocale()).apply { maximumFractionDigits = 0 }
        .format(fraction.coerceIn(0f, 1f).toDouble())

@Composable
@ReadOnlyComposable
fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance(currentLocale()).format(value)

@Composable
@ReadOnlyComposable
fun formatNumber(value: Int): String = formatNumber(value.toLong())

@Composable
@ReadOnlyComposable
fun formatDecimal(value: Float): String =
    NumberFormat.getNumberInstance(currentLocale()).apply {
        maximumFractionDigits = if (value >= 10f) 0 else 1
        minimumFractionDigits = 0
    }.format(value.toDouble())

@Composable
@ReadOnlyComposable
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    return when {
        hours > 0L -> stringResource(R.string.duration_hours_minutes, hours, minutes % 60L)
        minutes > 0L -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_seconds, ms / 1_000L)
    }
}

@Composable
@ReadOnlyComposable
fun formatMinutes(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    return if (hours > 0L) {
        stringResource(R.string.duration_hours_minutes, hours, minutes % 60L)
    } else {
        stringResource(R.string.duration_minutes, minutes)
    }
}

@Composable
@ReadOnlyComposable
fun formatRelative(time: Long, now: Long = System.currentTimeMillis()): String {
    val locale = currentLocale()
    val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))
    val elapsed = (now - time).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> formatter.format(
            RelativeDateTimeFormatter.Direction.PLAIN,
            RelativeDateTimeFormatter.AbsoluteUnit.NOW,
        )
        hours < 1L -> formatter.format(
            minutes.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.MINUTES,
        )
        days < 1L -> formatter.format(
            hours.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.HOURS,
        )
        days < 7L -> formatter.format(
            days.toDouble(),
            RelativeDateTimeFormatter.Direction.LAST,
            RelativeDateTimeFormatter.RelativeUnit.DAYS,
        )
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate())
    }
}

@Composable
@ReadOnlyComposable
fun formatWeekday(date: LocalDate, style: TextStyle = TextStyle.SHORT): String =
    date.dayOfWeek.getDisplayName(style, currentLocale())

@Composable
@ReadOnlyComposable
fun formatDate(date: LocalDate): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()).format(date)

@Composable
@ReadOnlyComposable
fun bidiWrap(text: String): String = BidiFormatter.getInstance(currentLocale()).unicodeWrap(text)
