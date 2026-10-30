/*
 * Smart Island (2026)
 * © Animesh Gupta — github.com/agupta07505
 * Licensed under the GNU GPL v3 License
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.agupta07505.smartisland.ui.hyper

import android.app.ActivityOptions
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agupta07505.smartisland.R
import com.agupta07505.smartisland.model.HyperFormat
import com.agupta07505.smartisland.model.HyperTemplate
import com.agupta07505.smartisland.model.IslandNotification
import com.agupta07505.smartisland.model.IslandNotificationAction
import com.agupta07505.smartisland.util.CallActionRole
import com.agupta07505.smartisland.util.VoipCallDetector
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val Ink = Color.White
private val InkDim = Color.White.copy(alpha = 0.62f)
private val Accent = Color(0xFF8AB4FF)

@Composable
internal fun HyperLeadingIcon(notification: IslandNotification?, size: Dp) {
    val bitmap = notification?.largeIcon ?: notification?.icon ?: notification?.smallIcon
    if (bitmap != null && !bitmap.isRecycled) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = notification?.appName,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = notification?.appName?.firstOrNull()?.uppercase() ?: "",
                color = Ink,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun HyperCompactStatus(notification: IslandNotification?, template: HyperTemplate) {
    if (notification == null) {
        Text(
            text = stringResource(R.string.hyper_nothing),
            color = InkDim,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val now = rememberTickingNow(notification.mode.name + notification.key)
    val primary = notification.title.ifBlank { notification.appName }
    val secondary = compactSecondary(
        notification,
        template,
        now,
        incoming = stringResource(R.string.hyper_incoming)
    )
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = primary,
            color = Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (secondary.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (template == HyperTemplate.Music) {
                    HyperWaveform(playing = notification.mediaIsPlaying, modifier = Modifier.padding(end = 4.dp))
                }
                Text(
                    text = secondary,
                    color = InkDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun HyperCardBody(
    notification: IslandNotification?,
    template: HyperTemplate,
    onReplyActive: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when (template) {
            HyperTemplate.Music -> MusicBody(notification)
            HyperTemplate.Call -> CallBody(notification)
            HyperTemplate.Timer, HyperTemplate.Stopwatch -> ClockBody(notification, template)
            HyperTemplate.Charging -> ChargingBody(notification)
            HyperTemplate.Navigation, HyperTemplate.Ride, HyperTemplate.Progress -> ProgressBody(notification, template)
            HyperTemplate.HeadsUp, HyperTemplate.Empty -> HeadsUpBody(notification, onReplyActive)
        }
    }
}

@Composable
private fun MusicBody(notification: IslandNotification?) {
    val context = LocalContext.current
    val token = notification?.mediaToken
    val controller = remember(token) {
        token?.let { runCatching { android.media.session.MediaController(context, it) }.getOrNull() }
    }
    var position by remember(notification?.key, notification?.mediaPositionMs) {
        mutableLongStateOf(notification?.mediaPositionMs ?: 0L)
    }
    val playing = notification?.mediaIsPlaying == true
    LaunchedEffect(notification?.key, playing, notification?.mediaPositionMs) {
        position = notification?.mediaPositionMs ?: 0L
        if (!playing) return@LaunchedEffect
        while (true) {
            delay(500)
            position += 500
        }
    }
    val duration = notification?.mediaDurationMs
    val fraction = if (duration != null && duration > 0L) {
        (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = notification?.title.orEmpty(),
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notification?.text.orEmpty(),
                color = InkDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column {
            HyperTrack(fraction)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HyperIconButton(Icons.Rounded.SkipPrevious, stringResource(R.string.hyper_previous)) {
                    controller?.transportControls?.skipToPrevious()
                }
                HyperIconButton(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) R.string.hyper_pause else R.string.hyper_play)
                ) {
                    val controls = controller?.transportControls ?: return@HyperIconButton
                    if (playing) controls.pause() else controls.play()
                }
                HyperIconButton(Icons.Rounded.SkipNext, stringResource(R.string.hyper_next)) {
                    controller?.transportControls?.skipToNext()
                }
            }
        }
    }
}

@Composable
private fun CallBody(notification: IslandNotification?) {
    val context = LocalContext.current
    val now = rememberTickingNow(notification?.key ?: "call")
    val ringing = notification?.isCallRinging == true
    val elapsed = if (notification != null && !ringing && notification.timeMillis in 1 until now) {
        HyperFormat.clock((now - notification.timeMillis) / 1000L)
    } else {
        ""
    }
    val status = when {
        notification == null -> ""
        notification.callStatusText.isNotBlank() -> notification.callStatusText
        ringing -> stringResource(R.string.hyper_incoming)
        elapsed.isNotBlank() -> elapsed
        else -> stringResource(R.string.hyper_on_call)
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = notification?.title?.ifBlank { notification.appName }.orEmpty(),
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = status, color = InkDim, fontSize = 13.sp, maxLines = 1)
        }
        val actions = notification?.actionIntents.orEmpty()
        val answer = actions.firstOrNull { VoipCallDetector.classifyCallAction(it.title) == CallActionRole.Answer }
        val hangup = actions.firstOrNull {
            val role = VoipCallDetector.classifyCallAction(it.title)
            role == CallActionRole.HangUp
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (answer != null) {
                HyperPillButton(stringResource(R.string.hyper_accept), Color(0xFF1F8F4E), Icons.Rounded.Call) {
                    sendIslandAction(context, answer)
                }
            }
            if (hangup != null) {
                HyperPillButton(
                    stringResource(if (ringing) R.string.hyper_decline else R.string.hyper_hang_up),
                    Color(0xFFB43333),
                    Icons.Rounded.CallEnd
                ) { sendIslandAction(context, hangup) }
            }
        }
    }
}

@Composable
private fun ClockBody(notification: IslandNotification?, template: HyperTemplate) {
    val now = rememberTickingNow(notification?.key ?: template.name)
    val label = stringResource(if (template == HyperTemplate.Stopwatch) R.string.hyper_stopwatch else R.string.hyper_timer)
    val value = clockValue(notification, template, now)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(text = label, color = InkDim, fontSize = 12.sp)
        Text(text = value, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        val detail = notification?.text.orEmpty()
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = InkDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChargingBody(notification: IslandNotification?) {
    val fraction = HyperFormat.fraction(notification?.progress ?: 0, notification?.progressMax ?: 0)
        ?: percentIn(notification?.title, notification?.text)
    val shown = fraction ?: 0f
    val pulse = rememberInfiniteTransition(label = "charge")
    val glow by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "chargeGlow"
    )
    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                drawArc(
                    color = Color.White.copy(alpha = 0.14f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color(0xFF3DDC84),
                    startAngle = -90f,
                    sweepAngle = 360f * shown * glow,
                    useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color(0xFF3DDC84), modifier = Modifier.size(28.dp))
        }
        Column {
            Text(
                text = notification?.title?.ifBlank { stringResource(R.string.hyper_charging) }
                    ?: stringResource(R.string.hyper_charging),
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val body = notification?.text.orEmpty()
            val detail = when {
                fraction != null -> stringResource(R.string.hyper_percent, (fraction * 100).toInt())
                body.isNotBlank() -> body
                else -> ""
            }
            if (detail.isNotBlank()) {
                Text(text = detail, color = InkDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ProgressBody(notification: IslandNotification?, template: HyperTemplate) {
    val label = stringResource(
        when (template) {
            HyperTemplate.Navigation -> R.string.hyper_navigation
            HyperTemplate.Ride -> R.string.hyper_ride
            else -> R.string.hyper_progress
        }
    )
    val fraction = HyperFormat.fraction(notification?.progress ?: 0, notification?.progressMax ?: 0)
        ?: percentIn(notification?.title, notification?.text)
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(text = label, color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = notification?.title.orEmpty(),
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notification?.text?.ifBlank { notification.transferDetail }.orEmpty(),
                color = InkDim,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        HyperTrack(fraction)
    }
}

@Composable
private fun HeadsUpBody(notification: IslandNotification?, onReplyActive: (Boolean) -> Unit) {
    val context = LocalContext.current
    val reply = notification?.actionIntents?.firstOrNull { it.isQuickReply }
        ?: notification?.actionIntents?.firstOrNull { it.title.contains("reply", ignoreCase = true) }
    var draft by remember(notification?.key) { mutableStateOf("") }
    var writing by remember(notification?.key) { mutableStateOf(false) }
    val focus = remember(notification?.key) { FocusRequester() }
    DisposableEffect(Unit) {
        onDispose { onReplyActive(false) }
    }
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                text = notification?.title?.ifBlank { notification.appName } ?: stringResource(R.string.hyper_nothing),
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notification?.bigText?.ifBlank { notification.text }.orEmpty(),
                color = InkDim,
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (reply != null && writing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LaunchedEffect(writing) {
                    if (writing) {
                        onReplyActive(true)
                        runCatching { focus.requestFocus() }
                    }
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(stringResource(R.string.hyper_message), color = InkDim, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.hyper_send),
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        if (draft.isNotBlank()) {
                            sendReply(context, reply, draft)
                            draft = ""
                            writing = false
                            onReplyActive(false)
                        }
                    }
                )
            }
        } else if (reply != null) {
            Text(
                text = stringResource(R.string.hyper_reply),
                color = Accent,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    writing = true
                    onReplyActive(true)
                }
            )
        }
    }
}

@Composable
internal fun HyperWaveform(playing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 720, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase"
    )
    Canvas(modifier.size(width = 18.dp, height = 12.dp)) {
        val bars = 4
        val slot = size.width / bars
        for (i in 0 until bars) {
            val wave = sin((phase + i * 0.23f) * 2.0 * PI).toFloat()
            val amount = if (playing) 0.3f + 0.7f * ((wave + 1f) / 2f) else 0.28f
            val h = size.height * amount
            drawRoundRect(
                color = Accent,
                topLeft = Offset(i * slot + slot * 0.2f, (size.height - h) / 2f),
                size = Size(slot * 0.55f, h),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
private fun HyperTrack(fraction: Float?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.14f))
    ) {
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.04f, 1f))
                    .height(4.dp)
                    .background(Accent)
            )
        }
    }
}

@Composable
private fun HyperIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Ink)
    }
}

@Composable
private fun HyperPillButton(
    label: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Ink, modifier = Modifier.size(16.dp))
        Text(text = label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun rememberTickingNow(key: String): Long {
    var now by remember(key) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(key) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    return now
}

private fun compactSecondary(
    notification: IslandNotification,
    template: HyperTemplate,
    now: Long,
    incoming: String
): String {
    return when (template) {
        HyperTemplate.Call -> {
            when {
                notification.callStatusText.isNotBlank() -> notification.callStatusText
                notification.isCallRinging -> incoming
                notification.timeMillis in 1 until now -> HyperFormat.clock((now - notification.timeMillis) / 1000L)
                else -> notification.text
            }
        }
        HyperTemplate.Timer, HyperTemplate.Stopwatch -> clockValue(notification, template, now)
        HyperTemplate.Music -> notification.text
        else -> notification.text.ifBlank { notification.transferDetail }
    }
}

private fun clockValue(notification: IslandNotification?, template: HyperTemplate, now: Long): String {
    if (notification == null) return HyperFormat.clock(0)
    if (template == HyperTemplate.Stopwatch) {
        val frozen = notification.stopwatchElapsedSeconds
        if (notification.recordingPaused && frozen != null) return HyperFormat.clock(frozen)
        val base = notification.stopwatchChronometerBase
        if (base != null && !notification.recordingPaused) {
            val elapsed = (android.os.SystemClock.elapsedRealtime() - base) / 1000L
            if (elapsed >= 0) return HyperFormat.clock(elapsed)
        }
        if (notification.timeMillis in 1 until now) return HyperFormat.clock((now - notification.timeMillis) / 1000L)
    }
    val remaining = notification.timerRemainingSeconds
    if (remaining != null) return HyperFormat.clock(remaining)
    if (notification.timeMillis > now) return HyperFormat.clock((notification.timeMillis - now) / 1000L)
    return notification.text.ifBlank { HyperFormat.clock(0) }
}

private fun percentIn(title: String?, text: String?): Float? {
    val blob = "$title $text"
    val match = Regex("""(\d{1,3})\s*%""").find(blob) ?: return null
    val value = match.groupValues[1].toIntOrNull() ?: return null
    return (value / 100f).coerceIn(0f, 1f)
}

private fun sendIslandAction(context: Context, action: IslandNotificationAction) {
    val pending = action.pendingIntent ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()
            pending.send(context, 0, null, null, null, null, options)
        } else {
            pending.send(context, 0, null)
        }
    }
}

private fun sendReply(context: Context, action: IslandNotificationAction, text: String) {
    val pending = action.pendingIntent
    if (pending == null) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        return
    }
    val key = action.remoteInputKey ?: "key_text_reply"
    val intent = Intent()
    val bundle = Bundle()
    bundle.putCharSequence(key, text)
    val remote = RemoteInput.Builder(key).build()
    RemoteInput.addResultsToIntent(arrayOf(remote), intent, bundle)
    runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val options = ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                .toBundle()
            pending.send(context, 0, intent, null, null, null, options)
        } else {
            pending.send(context, 0, intent)
        }
    }
}
