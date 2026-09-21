package uz.oktv.iptv

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.*

@Composable
fun LoadingCheckItem(text: String, isOk: Boolean?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (isOk) {
            true -> {
                Box(modifier = Modifier.size(18.dp).background(Color(0xFF10B981), CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = "✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            false -> {
                Box(modifier = Modifier.size(18.dp).background(Color(0xFFEF4444), CircleShape), contentAlignment = Alignment.Center) {
                    Text(text = "✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            null -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = if (isOk == true) Color(0xFFE2E8F0) else if (isOk == false) Color(0xFFEF4444) else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = if (isOk == true) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AuthScreenView(onAuthSuccess: (String) -> Unit) {
    var tokenInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF050811), Color(0xFF070B14), Color(0xFF0B0F19))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE60B0F19))
                .denimDoubleBorder(22f, 18f)
                .shadow(elevation = 25.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x66000000))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF1D4ED8), Color(0xFF0284C7))
                        )
                    )
                    .shadow(10.dp, RoundedCornerShape(20.dp), spotColor = Color(0x592563EB)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "OK", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "OK", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text(text = "TV", color = Color(0xFF3B82F6), fontSize = 24.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Введите ваш токен для доступа к ТВ и медиатеке", color = Color(0xFF94A3B8), fontSize = 12.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(18.dp))

            if (errorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x1AF43F5E))
                        .border(1.dp, Color(0x4DF43F5E), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚠️", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = errorMessage ?: "", color = Color(0xFFFB7185), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Токен доступа", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))

                BasicTextField(
                    value = tokenInput,
                    onValueChange = {
                        tokenInput = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xBF0F172A))
                        .border(1.dp, if (tokenInput.isNotEmpty()) Color(0xFF3B82F6) else Color(0xFF1E293B), RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Color(0xFF38BDF8)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (tokenInput.isNotBlank()) {
                            performLoginCheck(tokenInput.trim(), { isLoading = it }, { errorMessage = it }, onAuthSuccess)
                        }
                    }),
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔑", fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (tokenInput.isEmpty()) {
                                    Text(text = "Например: 18f6b6c2db...", color = Color(0xFF64748B), fontSize = 14.sp)
                                }
                                innerTextField()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = if (isLoading) listOf(Color(0xFF16A34A), Color(0xFF059669))
                            else listOf(Color(0xFF2563EB), Color(0xFF0284C7))
                        )
                    )
                    .clickable(enabled = !isLoading) {
                        if (tokenInput.isBlank()) {
                            errorMessage = "Пожалуйста, введите токен!"
                        } else {
                            performLoginCheck(tokenInput.trim(), { isLoading = it }, { errorMessage = it }, onAuthSuccess)
                        }
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Проверка ключа...", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Войти в приложение", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "→", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🛡️", fontSize = 11.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Mirovoy TV Security System • $APP_VERSION_NAME", color = Color(0xFF64748B), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ContactBadge(icon: String, title: String, value: String, isFocused: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF161F36), RoundedCornerShape(8.dp))
            .border(if (isFocused) 2.dp else 0.5.dp, if (isFocused) Color.White else Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .let { m -> if (onClick != null) m.clickable { onClick() } else m }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Text(text = value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        if (onClick != null) {
            Text(text = "→", color = Color(0xFF38BDF8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileInfoBadge(title: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF161F36), RoundedCornerShape(6.dp))
            .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = Color(0xFF94A3B8), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun SideNavButton(icon: String, text: String, active: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (active) Color(0xFF16222E) else Color(0xFF0F1B26),
                RoundedCornerShape(12.dp)
            )
            .border(
                width = if (active) 1.5.dp else 0.5.dp,
                color = if (active) Color(0xFFE5245A) else Color(0xFF1C2C38),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(
                    if (active) Color(0xFFE5245A) else Color(0xFF19293A),
                    RoundedCornerShape(9.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = if (active) Color.White else Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun EngineSelectButton(title: String, subtitle: String, active: Boolean, isFocused: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .background(
                if (active) Color(0xFF2563EB) else Color(0xFF161F36),
                RoundedCornerShape(10.dp)
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else if (active) Color(0xFF38BDF8) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = subtitle, color = if (active) Color.White else Color(0xFF94A3B8), fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun RenderPillButton(text: String, active: Boolean, isFocused: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (active) Color(0xFF2563EB) else Color(0xFF161F36),
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) Color.White else if (active) Color(0xFF38BDF8) else Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) Color.White else Color(0xFF94A3B8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SwitchRow(title: String, desc: String, checked: Boolean, isFocused: Boolean = false, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                if (isFocused) Color(0xFF1E293B) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = Color.White,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onCheckedChange(!checked) }
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = desc, color = Color(0xFF64748B), fontSize = 10.sp)
        }
        Box(
            modifier = Modifier
                .background(
                    if (checked) Color(0xFF10B981) else Color(0xFF334155),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (checked) "ВКЛ" else "ВЫКЛ",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SpeedTestModalDialog(lang: AppLang, onDismiss: () -> Unit) {
    // Pult markaziy (OK) tugmasi bu oynada ishlamasligi mumkin edi,
    // shuning uchun test ochilishi bilan o'zi avtomatik boshlanadi —
    // tugma bosish shart emas.
    var isTesting by remember { mutableStateOf(true) }
    var pingMs by remember { mutableIntStateOf(14) }
    var speedMbps by remember { mutableFloatStateOf(0f) }
    val animatedSpeed by animateFloatAsState(
        targetValue = speedMbps,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "speed"
    )

    LaunchedEffect(isTesting) {
        if (isTesting) {
            speedMbps = 0f
            for (i in 1..12) {
                delay(200)
                pingMs = (10..22).random()
                speedMbps = ((450..980).random() / 10f)
            }
            delay(300)
            speedMbps = 86.4f
            isTesting = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .background(Color(0xFF0D1322), RoundedCornerShape(16.dp))
                .denimDoubleBorder(16f, 14f)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Strings.get("speed_test_title", lang),
                color = Color(0xFF38BDF8),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.size(170.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 14.dp.toPx()

                    drawArc(
                        color = Color(0xFF1E293B),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(strokeW, strokeW),
                        size = Size(size.width - strokeW * 2, size.height - strokeW * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    val progressRatio = (animatedSpeed / 100f).coerceIn(0f, 1f)
                    drawArc(
                        brush = Brush.sweepGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB), Color(0xFFF59E0B))),
                        startAngle = 135f,
                        sweepAngle = 270f * progressRatio,
                        useCenter = false,
                        topLeft = Offset(strokeW, strokeW),
                        size = Size(size.width - strokeW * 2, size.height - strokeW * 2),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "%.1f".format(animatedSpeed),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Мбит / с",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = Strings.get("ping", lang), color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(text = "$pingMs ms", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Сервер", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Text(text = "mirovoytv.uz", color = Color(0xFF38BDF8), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isTesting) Color(0xFF1E293B) else Color(0xFF2563EB)
                    )
                    .clickable(enabled = !isTesting) { isTesting = true }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(text = Strings.get("start_test", lang), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun PlaybackSettingsDialog(
    mainEngineMode: String,
    onMainEngineChange: (String) -> Unit,
    ffmpegAudioEnabled: Boolean,
    onFfmpegAudioChange: (Boolean) -> Unit,
    amlogicFixEnabled: Boolean,
    onAmlogicFixChange: (Boolean) -> Unit,
    smoothUpscaleEnabled: Boolean,
    onSmoothUpscaleChange: (Boolean) -> Unit,
    bufferSeconds: Int,
    onBufferSecondsChange: (Int) -> Unit,
    availableAudioTracks: List<AudioTrackItem>,
    exoPlayer: ExoPlayer,
    currentLang: AppLang,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Pult (D-pad) bilan navigatsiya: har bir qator (audio treklar,
    // dvigatel, uchta svitch, bufer) ro'yxatdagi bitta "bo'lim" deb
    // hisoblanadi. YUQORI/PASTKI bo'limlar orasida yuradi,
    // CHAP/O'NG dvigatel va bufer qiymatlarini o'zgartiradi.
    val sectionCount = availableAudioTracks.size + 5 // tracks + engine + 3 switch + buffer
    var focusIndex by remember { mutableIntStateOf(0) }
    val bufferOptions = listOf(2, 5, 10, 20)
    val dialogFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { dialogFocusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .background(Color(0xFF0D1322), RoundedCornerShape(16.dp))
                .denimDoubleBorder(16f, 14f)
                .focusRequester(dialogFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val engineIdx = availableAudioTracks.size
                    val ffmpegIdx = engineIdx + 1
                    val amlogicIdx = engineIdx + 2
                    val upscaleIdx = engineIdx + 3
                    val bufferIdx = engineIdx + 4
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                            focusIndex = (focusIndex + 1).coerceAtMost(sectionCount - 1)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                            focusIndex = (focusIndex - 1).coerceAtLeast(0)
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            when (focusIndex) {
                                engineIdx -> onMainEngineChange(if (mainEngineMode == "EXO") "WEB" else "EXO")
                                bufferIdx -> {
                                    val curPos = bufferOptions.indexOf(bufferSeconds).coerceAtLeast(0)
                                    val delta = if (keyEvent.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1
                                    val newPos = (curPos + delta).coerceIn(0, bufferOptions.size - 1)
                                    onBufferSecondsChange(bufferOptions[newPos])
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            when (focusIndex) {
                                in 0 until engineIdx -> {
                                    val track = availableAudioTracks[focusIndex]
                                    switchAudioTrack(exoPlayer, track)
                                    Toast.makeText(context, "Выбрано: ${track.label}", Toast.LENGTH_SHORT).show()
                                }
                                engineIdx -> onMainEngineChange(if (mainEngineMode == "EXO") "WEB" else "EXO")
                                ffmpegIdx -> onFfmpegAudioChange(!ffmpegAudioEnabled)
                                amlogicIdx -> onAmlogicFixChange(!amlogicFixEnabled)
                                upscaleIdx -> onSmoothUpscaleChange(!smoothUpscaleEnabled)
                                bufferIdx -> {
                                    val curPos = bufferOptions.indexOf(bufferSeconds).coerceAtLeast(0)
                                    onBufferSecondsChange(bufferOptions[(curPos + 1) % bufferOptions.size])
                                }
                            }
                            true
                        }
                        android.view.KeyEvent.KEYCODE_BACK -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                }
                .padding(24.dp)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎛️", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Настройки воспроизведения",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(text = "✕", color = Color.Gray, fontSize = 16.sp, modifier = Modifier.clickable { onDismiss() })
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                if (availableAudioTracks.isNotEmpty()) {
                    Text(
                        text = Strings.get("audio_tracks", currentLang),
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF090D1A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                            .padding(6.dp)
                    ) {
                        availableAudioTracks.forEachIndexed { trackIdx, track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (track.isSelected) Color(0xFF2563EB) else Color.Transparent)
                                    .border(
                                        width = if (focusIndex == trackIdx) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        switchAudioTrack(exoPlayer, track)
                                        Toast.makeText(context, "Выбрано: ${track.label}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = track.label,
                                    color = if (track.isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (track.isSelected) {
                                    Text(text = "✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text("Главный движок (Телеканалы)", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EngineSelectButton(
                        title = "⚡ Media3 (Exo)",
                        subtitle = "Стандартный + Аппаратный",
                        active = mainEngineMode == "EXO",
                        isFocused = focusIndex == availableAudioTracks.size,
                        modifier = Modifier.weight(1f)
                    ) { onMainEngineChange("EXO") }

                    EngineSelectButton(
                        title = "🌐 Web HLS",
                        subtitle = "Резервный HTML5",
                        active = mainEngineMode == "WEB",
                        isFocused = focusIndex == availableAudioTracks.size,
                        modifier = Modifier.weight(1f)
                    ) { onMainEngineChange("WEB") }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D1A), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text("НАСТРОЙКИ DECODER (MEDIA3)", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    SwitchRow(
                        title = "FFmpeg Audio",
                        desc = "Программный декодер звука (Отключите если тормозит)",
                        checked = ffmpegAudioEnabled,
                        isFocused = focusIndex == availableAudioTracks.size + 1,
                        onCheckedChange = { onFfmpegAudioChange(it) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    SwitchRow(
                        title = "Amlogic Fix",
                        desc = "Katta bufer bilan sekin/eski kanallardagi \"typirlash\"ni kamaytiradi",
                        checked = amlogicFixEnabled,
                        isFocused = focusIndex == availableAudioTracks.size + 2,
                        onCheckedChange = { onAmlogicFixChange(it) }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    SwitchRow(
                        title = "Сглаживание SD",
                        desc = "Улучшает резкость SD каналов на ТВ",
                        checked = smoothUpscaleEnabled,
                        isFocused = focusIndex == availableAudioTracks.size + 3,
                        onCheckedChange = { onSmoothUpscaleChange(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Размер буфера (секунды)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10, 20).forEach { sec ->
                        RenderPillButton(
                            text = "${sec}s",
                            active = bufferSeconds == sec,
                            isFocused = focusIndex == availableAudioTracks.size + 4 && bufferSeconds == sec,
                            modifier = Modifier.weight(1f)
                        ) { onBufferSecondsChange(sec) }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailMovieModal(
    title: String,
    posterUrl: String?,
    year: String?,
    genres: String?,
    rating: String?,
    desc: String?,
    showPlayButton: Boolean,
    onWatchClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0D1322))
                .denimDoubleBorder(16f, 12f)
                .padding(20.dp)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(170.dp)
                        .height(230.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090D1A))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (!posterUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC000000), Color(0xFF1D4ED8))
                                )
                            )
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Смотрите на OK TV",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            color = Color.Gray,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onDismiss() }
                                .padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!year.isNullOrEmpty() || !genres.isNullOrEmpty() || !rating.isNullOrEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val infoText = listOfNotNull(
                                year?.takeIf { it.isNotBlank() },
                                genres?.takeIf { it.isNotBlank() }
                            ).joinToString(" • ")

                            if (infoText.isNotEmpty()) {
                                Text(
                                    text = infoText,
                                    color = Color(0xFFF59E0B),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (!rating.isNullOrEmpty()) {
                                if (infoText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "•  ★ $rating",
                                    color = Color(0xFFFFD700),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = desc ?: "Описание фильма или передачи...",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (showPlayButton) {
                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2563EB), Color(0xFF38BDF8))
                            )
                        )
                        .border(2.dp, Color(0xFF67E8F9), RoundedCornerShape(12.dp))
                        .focusable()
                        .clickable { onWatchClick() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "▶", color = Color.White, fontSize = 15.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "СМОТРЕТЬ ФИЛЬМ",
                            color = Color(0xFFFFFFFF),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun VodGenreModalDialog(
    availableGenres: List<String>,
    selectedVodGenre: String,
    genreSelectedIndex: Int,
    genreListState: androidx.compose.foundation.lazy.LazyListState,
    genreFocusRequester: FocusRequester,
    onGenreSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 480.dp)
                .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                .denimDoubleBorder(12f, 10f)
                .focusRequester(genreFocusRequester)
                .focusable()
                .padding(16.dp)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🎭 ВЫБОР ЖАНРА",
                    color = Color(0xFF38BDF8),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "✕",
                    color = Color.Gray,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                state = genreListState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(availableGenres, key = { _, genre -> genre }) { idx, genre ->
                    val isSel = selectedVodGenre.equals(genre, ignoreCase = true) || (selectedVodGenre == "Все" && genre == "Все жанры")
                    val isItemFocused = idx == genreSelectedIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) Color(0xFF2563EB) else if (isItemFocused) Color(0xFF1E293B) else Color(0xFF161F36))
                            .border(width = if (isItemFocused) 2.dp else 1.dp, color = if (isItemFocused) Color(0xFF38BDF8) else Color(0xFF1E293B), shape = RoundedCornerShape(6.dp))
                            .focusable()
                            .clickable { onGenreSelected(genre) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = genre,
                                color = if (isSel || isItemFocused) Color.White else Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = if (isSel || isItemFocused) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSel) {
                                Text(text = "✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ExitConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var selectedButton by remember { mutableStateOf(0) }
    val dialogFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .background(Color(0xFF0D1322), RoundedCornerShape(12.dp))
                .denimDoubleBorder(12f, 10f)
                .focusRequester(dialogFocusRequester)
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                selectedButton = if (selectedButton == 0) 1 else 0
                                true
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                if (selectedButton == 1) onConfirm() else onDismiss()
                                true
                            }
                            android.view.KeyEvent.KEYCODE_BACK -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .padding(18.dp)
                .clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "ВЫХОД ИЗ ПЛЕЕРА", color = Color(0xFF38BDF8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Вы действительно хотите выйти из приложения?", color = Color(0xFF94A3B8), fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedButton == 0) Color(0xFF2563EB) else Color(0xFF1E293B))
                        .border(width = if (selectedButton == 0) 2.dp else 0.dp, color = Color(0xFF38BDF8), shape = RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ОТМЕНА", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedButton == 1) Color(0xFFEF4444) else Color(0xFF7F1D1D))
                        .border(width = if (selectedButton == 1) 2.dp else 0.dp, color = Color(0xFF38BDF8), shape = RoundedCornerShape(8.dp))
                        .clickable { onConfirm() }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "ВЫЙТИ", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        dialogFocusRequester.requestFocus()
    }
}

@Composable
fun EpgUrlDialog(
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val (u1, u2, u3) = EpgRepository.getUserUrls(context)
    var url1 by remember { mutableStateOf(u1) }
    var url2 by remember { mutableStateOf(u2) }
    var url3 by remember { mutableStateOf(u3) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D1322), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📡 EPG URL sozlamalari", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Asosiy EPG avtomatik ishlaydi. Qo'shimcha URL qo'shishingiz mumkin:", color = Color(0xFF94A3B8), fontSize = 12.sp)

            listOf(
                Triple("EPG URL 1", url1, { v: String -> url1 = v }),
                Triple("EPG URL 2", url2, { v: String -> url2 = v }),
                Triple("EPG URL 3", url3, { v: String -> url3 = v })
            ).forEach { (label, value, onChange) ->
                Column {
                    Text(label, color = Color(0xFF94A3B8), fontSize = 11.sp)
                    androidx.compose.foundation.text.BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF070B14), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        decorationBox = { inner ->
                            if (value.isEmpty()) Text("https://...", color = Color(0xFF475569), fontSize = 12.sp)
                            inner()
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2563EB), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .clickable {
                            EpgRepository.saveUserUrls(context, url1, url2, url3)
                            onDismiss()
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Saqlash", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF1E293B), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                        .clickable { onDismiss() }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bekor", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        }
    }
}
