package app.chinae.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chinae.data.Settings
import app.chinae.data.Tone
import app.chinae.trip.LatLng
import app.chinae.trip.Places
import java.time.DayOfWeek

/** Navy #14213D, Cream #F4EDE0, Coral #FF6B5B. 남색 바탕에 산호색 빛이 번지는 유리 카드 */
private val Navy = Color(0xFF14213D)
private val Cream = Color(0xFFF4EDE0)
private val Coral = Color(0xFFFF6B5B)
private val Colors = darkColorScheme(
    // 크림 글자는 산호색 위에서 대비가 2.4:1이라 산호색 위 글자는 남색(5.7:1)
    primary = Coral, onPrimary = Navy,
    background = Navy, onBackground = Cream,
    surface = Navy, onSurface = Cream,
    surfaceVariant = Color(0xFF1C2B4D), onSurfaceVariant = Cream.copy(alpha = 0.68f),
    outline = Cream.copy(alpha = 0.28f), outlineVariant = Cream.copy(alpha = 0.12f),
    error = Coral,
)

@Composable
fun ChinaeTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, content = content)

/** masterplan 1장 원칙 7: 첫 실행과 설정 화면에 함께 보여 줌 */
private const val NOTICE = "친애는 걱정을 맡아 두었다가 돌려주는 습관 도구예요. 상담이나 치료를 대신하지 않아요. " +
    "마음이 많이 힘들면 자살예방 상담전화 109로 연락해 주세요.\n\n맡긴 말은 이 차 안에만 암호화해 저장돼요."

private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 첫 실행(온보딩)과 설정을 한 화면으로. 왼쪽은 소개와 안내, 오른쪽은 설정 목록.
 * 값은 바꾸는 즉시 저장되고 서비스는 다음 대화부터 새 값을 읽음. 주행 중에는 입력 요소를 숨김(9장)
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    driving: Boolean,
    location: LatLng?,
    pending: Int,
    caption: String?,
    onStart: () -> Unit,
    onWipe: () -> Unit,
) {
    var onboarded by remember { mutableStateOf(settings.onboardingDone) }
    // 설정 카드는 첫 실행과 설정 버튼을 눌렀을 때만. 평소에는 그 자리에 보관함(Stage)
    var showSettings by remember { mutableStateOf(!settings.onboardingDone) }
    var tone by remember { mutableStateOf(settings.tone) }
    var alias by remember { mutableStateOf(settings.alias.orEmpty()) }
    var workdays by remember { mutableStateOf(settings.workdays) }
    var places by remember { mutableStateOf(settings.manualPlaces) }
    var confirmWipe by remember { mutableStateOf(false) }
    var wiped by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Row(Modifier.fillMaxSize().drawBehind { glow() }.padding(horizontal = 48.dp, vertical = 40.dp)) {
        Column(Modifier.weight(0.4f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("〜 친애", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                if (onboarded && !driving) Choice(if (showSettings) "닫기" else "설정", showSettings) { showSettings = !showSettings }
            }
            Spacer(Modifier.weight(1f))
            Text("오늘의 걱정은\n여기 두고 가요.", fontSize = 52.sp, lineHeight = 64.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(16.dp))
            Text("퇴근길에 맡기고, 출근길에 돌려받아요.", fontSize = 18.sp, color = muted)
            Spacer(Modifier.height(36.dp))
            Text(NOTICE, fontSize = 14.sp, lineHeight = 22.sp, color = muted)
            if (!onboarded && !driving) {
                Spacer(Modifier.height(28.dp))
                Button(
                    { settings.onboardingDone = true; onboarded = true; showSettings = false; onStart() },
                    Modifier.height(60.dp), shape = CircleShape,
                ) { Text("시작하기  ↗", Modifier.padding(horizontal = 20.dp), fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
            }
        }

        Spacer(Modifier.width(64.dp))

        // 유리 카드: 반투명 남색과 얇은 크림 테두리
        Box(
            Modifier.weight(0.6f).fillMaxHeight().clip(RoundedCornerShape(28.dp))
                .background(Navy.copy(alpha = 0.55f)).border(1.dp, Cream.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                .padding(horizontal = 36.dp, vertical = 12.dp),
        ) {
            // 주행 중에는 입력 요소 없이 상태만(9장)
            if (!showSettings || driving) {
                Stage(pending, caption.takeIf { !driving }, Modifier.fillMaxSize())
                return@Box
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingRow("말투", "친애가 말을 거는 방식") {
                    Segmented(listOf("반말", "해요체"), tone.ordinal) { tone = Tone.entries[it]; settings.tone = tone }
                }
                SettingRow("부를 이름", "비워 두면 이름 없이 말해요") {
                    OutlinedTextField(
                        alias, { alias = it; settings.alias = it.trim().ifEmpty { null } },
                        Modifier.width(260.dp), singleLine = true, placeholder = { Text("예: 민준") },
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                SettingRow("출근하는 요일", "고른 요일에만 먼저 말을 걸어요", stacked = true) {
                    DayOfWeek.entries.forEachIndexed { i, d ->
                        Choice(DAYS[i], d in workdays, round = true) {
                            workdays = if (d in workdays) workdays - d else workdays + d
                            settings.workdays = workdays
                        }
                    }
                }
                SettingRow("집", placeLabel(places.home)) {
                    Choice("지금 위치로", false, enabled = location != null) {
                        places = places.copy(home = location); settings.manualPlaces = places
                    }
                }
                SettingRow("회사", placeLabel(places.work)) {
                    Choice("지금 위치로", false, enabled = location != null) {
                        places = places.copy(work = location); settings.manualPlaces = places
                    }
                }
                if (places.home != null || places.work != null) {
                    TextButton({ places = Places(null, null); settings.manualPlaces = places }) {
                        Text("직접 지정을 지우고 내비 즐겨찾기 쓰기", fontSize = 16.sp)
                    }
                }
                SettingRow("모든 기록 지우기", if (wiped) "모두 지웠어요" else "맡긴 걱정과 정리 결과를 지워요", divider = false) {
                    TextButton({ confirmWipe = true }) {
                        Text("지우기", fontSize = 18.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("모든 기록을 지울까요?") },
        text = { Text("맡긴 걱정과 정리 결과가 모두 지워지고 되돌릴 수 없어요.") },
        confirmButton = {
            TextButton({ onWipe(); confirmWipe = false; wiped = true }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton({ confirmWipe = false }) { Text("취소") } },
    )
}


/** 왼쪽 아래에서 번지는 산호색 빛과 세로 결(유리 질감) */
private fun DrawScope.glow() {
    drawRect(Navy)
    drawRect(Brush.radialGradient(
        listOf(Coral.copy(alpha = 0.55f), Color(0xFF8A3A4A).copy(alpha = 0.25f), Color.Transparent),
        center = Offset(size.width * 0.28f, size.height * 0.85f), radius = size.width * 0.5f,
    ))
    val step = 6.dp.toPx()
    var x = 0f
    while (x < size.width) { drawLine(Cream.copy(alpha = 0.035f), Offset(x, 0f), Offset(x, size.height), 1f); x += step }
}

/**
 * 평소 화면. 맡아 둔 걱정이 많을수록 거품이 많아지고, 거품은 가운데를 돌며 원을 그림. 내용은 동승자가 볼 수 있어 쓰지 않음(9장)
 * 정차 중 대화에서는 아래 안내 대신 지금 말하는 문장을 자막으로 띄움. 주행 중에는 시선을 뺏지 않게 띄우지 않음(10장)
 * shortcut: 다음 대화 시점·주간 회고 수치는 서비스 상태를 화면으로 넘기는 연결이 생기면 추가
 */
@Composable
private fun Stage(pending: Int, caption: String?, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val t by rememberInfiniteTransition(label = "float").animateFloat(
        0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing)), label = "t",
    )
    val count = if (pending == 0) IDLE_BUBBLES else (pending * BUBBLES_PER_WORRY).coerceAtMost(MAX_BUBBLES)
    val bubbles = remember(count) {
        val rnd = Random(7)
        List(count) {
            Bubble(rnd.nextFloat() * TWO_PI, BAND_INNER + (1f - BAND_INNER) * (rnd.nextFloat() + rnd.nextFloat()) / 2, rnd.nextFloat() * TWO_PI, 1 + rnd.nextInt(2), 1.2f + rnd.nextFloat() * 4.8f)
        }
    }
    Column(modifier.padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 자체 레이어로 분리해 매 프레임 배경(세로 결, 빛 번짐)까지 다시 그리지 않게 함
        Canvas(Modifier.fillMaxWidth().weight(1f).graphicsLayer()) {
            val orbit = size.minDimension * 0.42f
            val line = 1.dp.toPx()
            val dim = if (pending == 0) 0.35f else 1f
            bubbles.forEach { b ->
                // 가운데를 도는 궤도. 궤도 반지름이 살짝 숨 쉬듯 변함
                val angle = b.angle + t * b.speed * TWO_PI
                val d = orbit * b.dist * (1f + 0.06f * sin(t * 4 * TWO_PI + b.phase))
                val c = center + Offset(cos(angle) * d, sin(angle) * d)
                // 안쪽은 밝은 노랑, 바깥으로 갈수록 산호색으로 흐려짐
                val u = (b.dist - BAND_INNER) / (1f - BAND_INNER)
                val tint = if (u < 0.5f) lerp(Ember, Amber, u * 2) else lerp(Amber, Coral, u * 2 - 1)
                val a = (1f - u * 0.55f) * dim
                val r = b.radius.dp.toPx() * (1.2f - 0.5f * u)
                drawCircle(tint.copy(alpha = 0.8f * a), r, c, style = Stroke(line))
            }
        }
        Text(if (pending > 0) "맡아 둔 걱정 ${pending}개" else "맡아 둔 걱정이 없어요", fontSize = 30.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        if (caption != null) Text(caption, fontSize = 24.sp, textAlign = TextAlign.Center, maxLines = 2)
        else Text(if (pending > 0) "출근길에 하나씩 돌려줄게요" else "퇴근길에 먼저 말을 걸게요", fontSize = 18.sp, color = muted)
    }
}

private const val BAND_INNER = 0.66f

/** dist는 궤도 반지름 비율(BAND_INNER~1, 띠 가운데에 많이 모임), speed는 한 주기에 도는 바퀴 수(정수라 반복이 끊기지 않음) */
private class Bubble(val angle: Float, val dist: Float, val phase: Float, val speed: Int, val radius: Float)

private const val TWO_PI = (2 * PI).toFloat()

private val Ember = Color(0xFFFFE14D)
private val Amber = Color(0xFFFF9A3C)
private const val IDLE_BUBBLES = 80
private const val BUBBLES_PER_WORRY = 250
private const val MAX_BUBBLES = 800

private fun placeLabel(p: LatLng?) = if (p != null) "직접 지정한 위치" else "내비 즐겨찾기를 써요"

/** 왼쪽에 제목과 설명, 오른쪽에 조작 요소. 조작 요소가 넓으면(stacked) 설명 아래로 내림 */
@Composable
private fun SettingRow(
    title: String, desc: String, divider: Boolean = true, stacked: Boolean = false, control: @Composable () -> Unit,
) {
    val label = @Composable {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(desc, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val controls = @Composable {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) { control() }
    }
    if (stacked) Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        label()
        Spacer(Modifier.height(16.dp))
        controls()
    } else Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { label() }
        controls()
    }
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 말투 고르기. 산호색 물방울이 앞쪽 끝부터 늘어나 옮겨 가고, 뒤쪽 끝이 늦게 따라붙으며 다시 둥글어짐 */
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val left = remember { Animatable(selected.toFloat()) }
    val right = remember { Animatable(selected + 1f) }
    LaunchedEffect(selected) {
        val forward = selected > left.value
        val fast = spring<Float>(dampingRatio = 0.6f, stiffness = 700f)
        val slow = spring<Float>(dampingRatio = 0.7f, stiffness = 160f)
        launch { left.animateTo(selected.toFloat(), if (forward) slow else fast) }
        launch { right.animateTo(selected + 1f, if (forward) fast else slow) }
    }
    Box(
        Modifier.height(56.dp).border(1.dp, c.outline, CircleShape).drawBehind {
            val unit = size.width / labels.size
            // 늘어난 만큼 위아래로 얇아짐(물방울 부피 유지 느낌)
            val stretch = (right.value - left.value - 1f).coerceIn(0f, 1f)
            val h = size.height * (1f - 0.25f * stretch)
            val w = ((right.value - left.value) * unit).coerceAtLeast(h)
            drawRoundRect(c.primary, Offset(left.value * unit, (size.height - h) / 2), Size(w, h), CornerRadius(h / 2))
        },
    ) {
        Row {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val color by animateColorAsState(if (on) c.onPrimary else c.onSurface, tween(220), label = "segText")
                Box(
                    Modifier.width(112.dp).fillMaxHeight().clip(CircleShape)
                        .selectable(on, role = Role.RadioButton) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = color, fontSize = 18.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal) }
            }
        }
    }
}

/** 고르면 산호색 물방울이 가운데서 퍼지며 살짝 넘쳤다 자리잡음. 차 안에서 누르기 쉽게 높이 56dp */
@Composable
private fun Choice(label: String, selected: Boolean, round: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fill by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "drop")
    val text by animateColorAsState(
        if (selected) c.onPrimary else if (enabled) c.onSurface else c.outline, tween(180), label = "dropText",
    )
    Surface(
        onClick, enabled = enabled,
        modifier = (if (round) Modifier.size(56.dp) else Modifier.height(56.dp)).drawBehind {
            if (fill <= 0f) return@drawBehind
            val s = Size(size.width * fill, size.height * fill)
            drawRoundRect(c.primary, Offset((size.width - s.width) / 2, (size.height - s.height) / 2), s, CornerRadius(s.height / 2))
        },
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = text,
        border = if (selected) null else BorderStroke(1.dp, c.outline),
    ) {
        Box(Modifier.padding(horizontal = if (round) 0.dp else 24.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
