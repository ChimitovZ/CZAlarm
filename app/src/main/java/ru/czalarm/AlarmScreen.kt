package ru.czalarm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal class AlarmScreenState(
    val alarms: List<Alarm>,
    val today: LocalDate,
    val permission: PermissionPrompt?,
) {
    val nextAlarm = alarms.filter { it.armedAt != 0L }.minByOrNull { it.armedAt }
}

internal class PermissionPrompt(val label: String, val request: () -> Unit)

private val alarmColors = lightColorScheme(
    primary = Color(Ui.PINE),
    onPrimary = Color(Ui.WHITE),
    secondaryContainer = Color(Ui.SAGE),
    onSecondaryContainer = Color(Ui.PINE),
    background = Color(Ui.PAPER),
    onBackground = Color(Ui.INK),
    surface = Color(Ui.CARD),
    surfaceContainer = Color(Ui.CARD),
    onSurface = Color(Ui.INK),
    onSurfaceVariant = Color(Ui.MUTED),
    outline = Color(0xff80887d),
    outlineVariant = Color(Ui.LINE),
)

@Composable
internal fun AlarmScreen(
    state: AlarmScreenState,
    onEdit: (Alarm?) -> Unit,
    onToggle: (Alarm, Boolean) -> Unit,
    onSound: () -> Unit,
    onUpdates: () -> Unit,
) {
    MaterialTheme(colorScheme = alarmColors) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, top = 20.dp, end = 22.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        AlarmHeader(state.today, onSound, onUpdates)
                    }
                    state.permission?.let { prompt ->
                        item(key = "permission") {
                            FilledTonalButton(
                                onClick = prompt.request,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Text(prompt.label, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    item(key = "next") {
                        NextAlarmCard(state, onEdit)
                    }
                    item(key = "list-header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Все будильники",
                                modifier = Modifier.weight(1f).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            FilledIconButton(onClick = { onEdit(null) }, modifier = Modifier.size(48.dp)) {
                                Icon(painterResource(R.drawable.ic_add), "Добавить будильник")
                            }
                        }
                    }
                    items(state.alarms, key = { it.id }) { alarm ->
                        AlarmRow(alarm, onEdit = { onEdit(alarm) }, onToggle = { onToggle(alarm, it) })
                    }
                    if (state.alarms.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "Нажмите +, чтобы добавить будильник",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "week") {
                        WeekStrip(state.alarms, state.today)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmHeader(today: LocalDate, onSound: () -> Unit, onUpdates: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = colors.secondaryContainer, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_sunrise), null, Modifier.size(23.dp))
                }
            }
            Text(
                Ui.date(today),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Box {
                FilledTonalIconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.surface, contentColor = colors.primary,
                    ),
                ) {
                    Icon(painterResource(R.drawable.ic_settings), "Настройки")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Звук") }, onClick = {
                        menuOpen = false
                        onSound()
                    })
                    DropdownMenuItem(text = { Text("Обновления") }, onClick = {
                        menuOpen = false
                        onUpdates()
                    })
                }
            }
        }
        Text(
            "Будильники",
            modifier = Modifier.padding(bottom = 5.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineLarge,
            fontSize = 35.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NextAlarmCard(state: AlarmScreenState, onEdit: (Alarm) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val next = state.nextAlarm
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primary, contentColor = colors.onPrimary),
    ) {
        val edit = if (next != null) {
            Modifier.clickable(role = Role.Button, onClickLabel = "Изменить") { onEdit(next) }
        } else {
            Modifier
        }
        Box(Modifier.fillMaxWidth().then(edit)) {
            Image(
                painter = painterResource(R.drawable.dawn),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(listOf(Color(0xd7112119), Color(0x9914251b), Color(0x26304434)))
                )
            )
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 158.dp).padding(horizontal = 19.dp, vertical = 13.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (next == null) {
                    Text(
                        "Нет запланированных сигналов",
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 20.sp,
                    )
                } else {
                    val scheduled = Instant.ofEpochMilli(next.armedAt).atZone(ZoneId.systemDefault())
                    val day = when (val date = scheduled.toLocalDate()) {
                        state.today -> "Сегодня"
                        state.today.plusDays(1) -> "Завтра"
                        else -> Ui.date(date)
                    }
                    Text("Ближайший будильник", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        Ui.time(scheduled.hour, scheduled.minute),
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 55.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp,
                    )
                    Text("$day · ${next.label}", style = MaterialTheme.typography.titleSmall)
                    Countdown(next.armedAt, state)
                }
            }
        }
    }
}

@Composable
private fun Countdown(at: Long, state: AlarmScreenState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var remaining by remember(at, state) { mutableLongStateOf(at - System.currentTimeMillis()) }
    LaunchedEffect(at, state, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val millis = at - System.currentTimeMillis()
                remaining = millis
                if (millis <= 0) break
                delay(if (millis < 60_000) (millis - 1) % 1_000 + 1 else millis % 60_000 + 1)
            }
        }
    }
    val millis = remaining
    val label = when {
        millis <= 0 -> "Сейчас"
        millis < 60_000 -> stringResource(R.string.countdown_seconds, (millis + 999) / 1_000)
        else -> buildString {
            val minutes = millis / 60_000
            val days = minutes / (24 * 60)
            val hours = (minutes / 60) % 24
            append("Через")
            if (days > 0) append(" $days д")
            if (hours > 0) append(" $hours ч")
            if (minutes % 60 != 0L) append(" ${minutes % 60} мин")
        }
    }
    Surface(
        modifier = Modifier.padding(top = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun AlarmRow(alarm: Alarm, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = Ui.time(alarm.hour, alarm.minute)
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val details = Modifier.weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Изменить", onClick = onEdit)
                .heightIn(min = 56.dp)
                .padding(start = 13.dp, top = 10.dp, end = 8.dp, bottom = 10.dp)
            if (LocalDensity.current.fontScale >= 1.5f) {
                Column(details, verticalArrangement = Arrangement.Center) {
                    AlarmTime(time, alarm.enabled)
                    AlarmLabels(alarm)
                }
            } else {
                Row(
                    modifier = details,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlarmTime(time, alarm.enabled)
                    AlarmLabels(alarm)
                }
            }
            Switch(
                checked = alarm.enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "Будильник «${alarm.label}»" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = Color(0xff42604d),
                    uncheckedThumbColor = colors.onSurfaceVariant,
                    uncheckedTrackColor = colors.secondaryContainer,
                    uncheckedBorderColor = colors.outline,
                ),
            )
        }
    }
}

@Composable
private fun AlarmTime(time: String, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    Text(
        time,
        style = MaterialTheme.typography.headlineSmall,
        fontSize = 26.sp,
        letterSpacing = (-0.75).sp,
        color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun AlarmLabels(alarm: Alarm) {
    Column {
        Text(alarm.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "${Ui.dayLabel(alarm.days)} · ${alarm.interval} мин",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekStrip(alarms: List<Alarm>, today: LocalDate) {
    val colors = MaterialTheme.colorScheme
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Ui.DAYS.forEachIndexed { index, weekday ->
                val date = monday.plusDays(index.toLong())
                val isToday = date == today
                val hasAlarm = alarms.any { it.enabled && date != it.firedDate && (it.days and (1 shl index)) != 0 }
                Column(
                    modifier = Modifier.weight(1f)
                        .background(if (isToday) colors.primary else Color.Transparent, RoundedCornerShape(12.dp))
                        .clearAndSetSemantics {
                            contentDescription = Ui.date(date) + if (hasAlarm) ", по расписанию" else ""
                            if (isToday) selected = true
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        weekday,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isToday) colors.onPrimary else colors.onSurfaceVariant,
                    )
                    Text(
                        date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isToday) colors.onPrimary else colors.onSurface,
                    )
                    val dotColor = when {
                        !hasAlarm -> Color.Transparent
                        isToday -> colors.onPrimary
                        else -> Color(0xff63816a)
                    }
                    Box(Modifier.padding(top = 2.dp).size(4.dp).background(dotColor, CircleShape))
                }
            }
        }
    }
}
