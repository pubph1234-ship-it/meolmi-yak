package app.motioncoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.motioncoach.*
import app.motioncoach.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF173F3A)
private val Muted = Color(0xFF617570)
private val Teal = Color(0xFF187B6C)
private val Mint = Color(0xFFE6F2EA)
private val Purple = Color(0xFF6B5595)
private val Lilac = Color(0xFFF0ECF7)
private val Paper = Color(0xFFF5F7F3)

@Composable
fun MotionCoachApp(s: CoachUiState, vm: CoachViewModel, export: () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Teal, onPrimary = Color.White,
        secondary = Purple, background = Paper, surface = Color.White, onSurface = Ink,
        onBackground = Ink, outline = Color(0xFFD1DFD7))) {
        Surface(Modifier.fillMaxSize(), color = Paper) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                val wide = maxWidth >= 850.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide && !(s.automotive && s.controlsLocked)) SideRail(s, vm)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Header(s, wide)
                        if (s.automotive && s.controlsLocked) {
                            DrivingView(s)
                        } else {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 32.dp else 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                when (s.screen) {
                                    Screen.HOME -> Home(s, vm, wide)
                                    Screen.JOURNEY -> Journey(s, vm, wide)
                                    Screen.REPORT -> Report(s, vm, export, wide)
                                    Screen.HISTORY -> History(s, vm, export)
                                    Screen.CONNECTION -> Connection(s, vm)
                                }
                                s.message?.let { Text(it, color = Teal, modifier = Modifier.testTag("message")) }
                            }
                        }
                        if (!wide && !s.controlsLocked) BottomNav(s, vm)
                    }
                }
            }
        }
    }
}

@Composable private fun Header(s: CoachUiState, wide: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = if (wide) 32.dp else 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!wide) { Icon(CoachIcons.Waves, null, tint = Teal); Spacer(Modifier.width(8.dp)) }
        Text(if (wide) "함께 편안해지는 이동" else "멀미약", fontSize = if (wide) 16.sp else 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(30.dp), color = if (s.mode == InputMode.DEMO) Lilac else Mint) {
            Text(if (s.mode == InputMode.DEMO) "시연 · 가상 데이터" else "Pleos · 차량 연결", Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = if (s.mode == InputMode.DEMO) Purple else Teal, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}
private val tabs = listOf(Triple(Screen.HOME, "홈", CoachIcons.Home), Triple(Screen.JOURNEY, "이동 코치", CoachIcons.Waves), Triple(Screen.HISTORY, "이동 기록", CoachIcons.History), Triple(Screen.CONNECTION, "연결 · 설정", CoachIcons.Settings))
@Composable private fun SideRail(s: CoachUiState, vm: CoachViewModel) {
    Column(Modifier.width(190.dp).fillMaxHeight().background(Color.White).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(CoachIcons.Waves, null, tint = Teal, modifier = Modifier.padding(top = 16.dp).size(38.dp))
        Text("멀미약", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("TEAM 멀미시러", color = Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(30.dp))
        tabs.forEach { (screen, label, icon) ->
            val selected = s.screen == screen || (screen == Screen.HISTORY && s.screen == Screen.REPORT)
            FilledTonalButton(onClick = { vm.navigate(screen) }, enabled = !s.controlsLocked || screen == Screen.CONNECTION,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = if (selected) Mint else Color.Transparent, contentColor = if (selected) Teal else Muted)) {
                Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(label, Modifier.weight(1f), fontSize = 14.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("운전자와 동승자,\n한 번의 이동을 함께.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        Text("멀미시러 · VERSION 0.2.1", color = Muted, fontSize = 10.sp)
    }
}
@Composable private fun BottomNav(s: CoachUiState, vm: CoachViewModel) {
    NavigationBar(containerColor = Color.White) { tabs.forEach { (screen, label, icon) -> NavigationBarItem(selected = s.screen == screen, onClick = { vm.navigate(screen) }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) }) } }
}
@Composable private fun Title(kicker: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(kicker, color = Teal, fontSize = 12.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 39.sp)
        Text(description, fontSize = 15.sp, color = Muted, lineHeight = 23.sp)
    }
}
@Composable private fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, color = color, shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
}
@Composable private fun TwoColumns(wide: Boolean, first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) { first(Modifier.weight(1f)); second(Modifier.weight(1f)) }
    else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { first(Modifier.fillMaxWidth()); second(Modifier.fillMaxWidth()) }
}
@Composable private fun Home(s: CoachUiState, vm: CoachViewModel, wide: Boolean) {
    Title("TWO COACHES, ONE JOURNEY", "이동이 끝난 뒤에도,\n모두 편안하도록.", "동승자에게는 필요한 순간의 안내를,\n운전자에게는 다음 이동을 위한 작은 목표를 전해요.")
    Panel(Modifier.fillMaxWidth(), Mint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (s.mode == InputMode.DEMO) "60초로 경험하는 멀미약" else "이번 이동을 함께 기록해요", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(if (s.mode == InputMode.DEMO) "회전 예고 → 동승자 체감 → 운전자 리포트" else "정차 상태에서 시작한 뒤, 짧은 음성 안내를 받아요.", color = Muted, lineHeight = 24.sp)
            }
            if (wide) Icon(CoachIcons.Route, null, tint = Teal, modifier = Modifier.size(70.dp))
        }
        if (s.mode == InputMode.DEMO) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("같은 가상 경로, 두 가지 운전 방식", fontWeight = FontWeight.Bold)
                DemoStyle.entries.forEach { style ->
                    FilterChip(selected = s.demoStyle == style, onClick = { vm.selectDemoStyle(style) }, enabled = !s.active,
                        label = { Text(style.label) }, modifier = Modifier.testTag("style-${style.name}"))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("비교용 가상 체감 자동 입력", Modifier.weight(1f))
                    Switch(s.comparison, { vm.toggleComparison() }, enabled = !s.active, modifier = Modifier.testTag("comparison"))
                }
                Text("켜면 두 시연 모두 42초에 가상 불편 입력을 넣어요. 실제 체감이나 멀미 감소 실험이 아니에요.", color = Muted, fontSize = 12.sp)
            }
        }
        Button(onClick = { if (s.active) vm.navigate(Screen.JOURNEY) else vm.start() }, enabled = !s.controlsLocked,
            modifier = Modifier.heightIn(min = 56.dp).testTag("start-demo"), shape = RoundedCornerShape(16.dp)) {
            Icon(CoachIcons.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(if (s.active) "진행 중인 이동 보기" else if (s.mode == InputMode.DEMO) "60초 시연 시작" else "이동 기록 시작", fontSize = 16.sp)
        }
        if (s.controlsLocked) Text("P단 · 정차 · 주행 제한 해제를 확인한 뒤 시작해요.", color = Muted)
    }
    TwoColumns(wide, { m -> CoachCard(m, false) }, { m -> CoachCard(m, true) })
    Text("멀미 완화 행동을 돕는 시제품이에요. 멀미 확률을 진단하거나 예방 효과를 보장하지 않아요.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
}
@Composable private fun CoachCard(modifier: Modifier, driver: Boolean) {
    Panel(modifier) {
        Icon(if (driver) CoachIcons.AutoGraph else CoachIcons.FavoriteBorder, null, tint = if (driver) Teal else Purple, modifier = Modifier.size(30.dp))
        Text(if (driver) "운전자 코치" else "동승자 코치", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (driver) "주행 변화와 동승자 체감을 함께 돌아보고, 다음 이동에서 실천할 목표 하나를 받아요." else "회전·방지턱을 앞두고 짧게 안내해요. 동승자는 자신의 불편함과 휴식 요청을 남길 수 있어요.", color = Muted, lineHeight = 24.sp)
        Text(if (driver) "도착 후, 한 가지씩" else "필요한 순간, 짧게", color = if (driver) Teal else Purple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}
@Composable private fun Journey(s: CoachUiState, vm: CoachViewModel, wide: Boolean) {
    if (!s.active) {
        Title("JOURNEY", "아직 이동 전이에요", "홈에서 시연 또는 차량 기록을 시작해주세요.")
        Button(onClick = { vm.navigate(Screen.HOME) }) { Text("출발 준비하기") }; return
    }
    Title("PASSENGER COACH", "다음 움직임을, 미리.", if (s.mode == InputMode.DEMO) "가상 경로에서 두 코치의 연결을 확인해보세요." else "차량 신호와 내비게이션 안내를 바탕으로 알려드려요.")
    if (s.mode == InputMode.DEMO) Text("${s.demoStyle.label}${if (s.comparison) " · 42초 가상 체감 자동 입력" else ""}", color = Purple)
    TwoColumns(wide, { m ->
        Panel(m, Mint) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(when (s.hint?.kind) { Maneuver.LEFT -> CoachIcons.TurnLeft; Maneuver.RIGHT -> CoachIcons.TurnRight; else -> CoachIcons.Waves }, null, tint = Teal, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(16.dp)); Text(s.cue, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp, modifier = Modifier.testTag("cue"))
            }
            Text(s.cueDetail, color = Ink, fontSize = 17.sp, lineHeight = 27.sp)
            Text("안내 간격을 두고 반복을 줄여요 · ${if (s.voice) "음성 켜짐" else "음성 꺼짐"}", color = Muted, fontSize = 12.sp)
        }
    }, { m ->
        Panel(m) {
            Text("현재 이동", color = Muted, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(s.sample?.speedMps?.let { String.format(Locale.US, "%.0f", it * 3.6) } ?: "—", "km/h")
                Metric(clock(s.elapsedMs), if (s.mode == InputMode.DEMO) "/ 01:00 시연" else "기록 시간")
            }
            if (s.mode == InputMode.DEMO) LinearProgressIndicator(progress = s.elapsedMs / 60000f, modifier = Modifier.fillMaxWidth(), color = Teal, trackColor = Mint)
            Text(if (s.mode == InputMode.DEMO) "모든 속도·경로는 시연용 가상 데이터입니다." else "속도 차이로 종가속도를 추정해요. 멀미 측정값은 아니에요.", color = Muted, fontSize = 12.sp)
        }
    })
    Panel(Modifier.fillMaxWidth(), Lilac) {
        Text("동승자는 지금 어떤가요?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (s.mode == InputMode.DEMO && s.comparison) "비교 시연은 42초에 가상 체감을 넣어요. 직접 입력은 자동 입력을 끈 시연에서 사용할 수 있어요." else if (s.mode == InputMode.DEMO) "체감 버튼을 눌러 리포트에 기록해보세요." else "차량 화면 입력은 정차 시에만 가능해요. 주행 중에는 연결한 동승자 휴대폰을 사용해요.", color = Muted)
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Feeling.entries.forEach { f -> FeelingButton(f, vm, Modifier.weight(1f), !s.controlsLocked && !(s.mode == InputMode.DEMO && s.comparison)) } }
        else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Feeling.entries.forEach { f -> FeelingButton(f, vm, Modifier.fillMaxWidth(), !s.controlsLocked && !(s.mode == InputMode.DEMO && s.comparison)) } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (s.mode == InputMode.DEMO) {
            OutlinedButton(onClick = vm::pause, modifier = Modifier.heightIn(min = 52.dp)) { Text(if (s.paused) "계속" else "일시정지") }
            OutlinedButton(onClick = vm::skip, modifier = Modifier.heightIn(min = 52.dp).testTag("skip")) { Text("5초 앞으로") }
        }
        Button(onClick = { vm.finish() }, enabled = !s.controlsLocked, modifier = Modifier.heightIn(min = 52.dp)) { Text("종료 · 리포트") }
    }
    if (s.events.isNotEmpty()) {
        Text("함께 남긴 순간", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        s.events.takeLast(4).reversed().forEach { EventRow(it) }
    }
}
@Composable private fun FeelingButton(f: Feeling, vm: CoachViewModel, modifier: Modifier, enabled: Boolean) {
    Button(onClick = { vm.feeling(f) }, enabled = enabled, modifier = modifier.heightIn(min = 58.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Purple), shape = RoundedCornerShape(16.dp)) {
        Icon(when (f) { Feeling.OK -> CoachIcons.SentimentSatisfiedAlt; Feeling.UNEASY -> CoachIcons.SentimentNeutral; Feeling.REST -> CoachIcons.LocalCafe }, null)
        Spacer(Modifier.width(10.dp)); Text(f.label, fontSize = 15.sp)
    }
}
@Composable private fun Metric(value: String, label: String) { Column { Text(value, fontSize = 36.sp, fontWeight = FontWeight.Bold); Text(label, fontSize = 12.sp, color = Muted) } }
@Composable private fun DrivingView(s: CoachUiState) {
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(CoachIcons.Waves, null, tint = Teal, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp)); Text(if (s.active) s.cue else "정차 상태를 확인하고 있어요", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp)); Text(if (s.active) "주행 중에는 짧은 안내만 전해요" else "P단과 차량 연결 상태를 확인해주세요", color = Muted, fontSize = 20.sp)
        Spacer(Modifier.height(30.dp)); Text("리포트와 화면 조작은 P단 정차 후에 가능해요", color = Muted)
    }
}
@Composable private fun Report(s: CoachUiState, vm: CoachViewModel, export: () -> Unit, wide: Boolean) {
    val report = s.report
    if (report == null) { Title("DRIVER COACH", "아직 리포트가 없어요", "이동을 마치면 운전자 코치의 피드백을 볼 수 있어요."); return }
    Title("DRIVER COACH", "다음 이동은, 조금 더 부드럽게.", "${if (report.mode == InputMode.DEMO) "시연 데이터" else "차량 기록"} · ${clock(report.durationMs)} · ${date(report.createdAt)}${if (report.interrupted) " · 중단된 기록" else ""}")
    report.scenario?.let { Text(it, color = Purple, fontWeight = FontWeight.Bold) }
    Panel(Modifier.fillMaxWidth(), Mint) {
        Text("다음 이동의 목표 하나", color = Teal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(report.goal, fontSize = 23.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("driver-goal"))
        Text(if (FeedbackReview.windows(report.events).isEmpty()) "이 목표는 전체 주행 요약을 참고했어요." else "이 목표는 불편 입력 전 구간을 먼저 참고했어요. 원인을 진단한 것은 아니에요.", color = Teal, fontSize = 13.sp)
        Text("필요한 제동이나 회피는 우선이에요. 교통 상황을 함께 고려해주세요.", color = Muted, fontSize = 13.sp)
    }
    TwoColumns(wide, { m -> Panel(m) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("${report.changesCount}", "기준을 넘은 주행 변화"); Metric("${report.feedbackCount}", "동승자 체감 기록") }
        Text("단순 횟수로 운전 실력이나 멀미 위험을 판단하지 않아요.", color = Muted, fontSize = 12.sp)
    } }, { m -> Panel(m) {
        Text("이동 속도와 동승자 체감", fontWeight = FontWeight.Bold)
        SpeedChart(report)
        Text("선: 속도  ·  점: 체감 입력  ·  음영: 불편 입력 전 최대 20초", color = Muted, fontSize = 12.sp)
    } })
    val windows = FeedbackReview.windows(report.events)
    if (windows.isNotEmpty()) Panel(Modifier.fillMaxWidth(), Lilac) {
        Text("불편했던 순간 돌아보기", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("feedback-review"))
        Text("불편 입력 직전 최대 20초를 함께 살펴봐요. 20초는 시제품의 검토 범위이며 의학적 기준이 아니에요.", color = Muted, fontSize = 13.sp)
        windows.takeLast(10).forEach { w ->
            Text("${clock(w.startMs)}–${clock(w.endMs)} · 불편 입력 ${clock(w.endMs)}", fontWeight = FontWeight.Bold, color = Purple)
            val counts = w.changes.groupingBy { it.kind }.eachCount()
            Text("가속 ${counts[EventKind.ACCELERATION] ?: 0}회 · 감속 ${counts[EventKind.BRAKING] ?: 0}회 · 조향 ${counts[EventKind.STEERING] ?: 0}회", fontSize = 14.sp)
            Text("안내 ${w.cues}회 · 안내 횟수는 운전 변화 집계에 포함하지 않아요.", color = Muted, fontSize = 12.sp)
        }
        Text("겹친 구간의 같은 주행 변화는 목표 선택에서 한 번만 반영해요. 데이터가 없거나 입력이 늦으면 당시 상황을 충분히 설명하지 못할 수 있어요.", color = Muted, fontSize = 12.sp)
    }
    Text("같은 시간에, 어떤 일이 있었나요?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text("주행 변화와 체감이 가까운 시각에 있어도 원인 관계를 의미하지 않아요.", color = Muted, fontSize = 13.sp)
    if (report.events.isEmpty()) Text("이번 이동에는 기록된 이벤트가 없어요.", color = Muted)
    report.events.takeLast(30).forEach { EventRow(it) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { vm.navigate(Screen.HOME) }) { Text("다음 이동 준비") }
        OutlinedButton(onClick = export, enabled = !s.active) { Text("기록 내보내기") }
    }
}
@Composable private fun SpeedChart(report: TripReport) {
    val samples = report.samples
    Canvas(Modifier.fillMaxWidth().height(90.dp)) {
        val maxSpeed = (samples.mapNotNull { it.speedMps }.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val duration = report.durationMs.coerceAtLeast(1)
        FeedbackReview.windows(report.events).forEach { w ->
            val left = (w.startMs.toDouble() / duration * size.width).toFloat()
            val right = (w.endMs.toDouble() / duration * size.width).toFloat()
            drawRect(Purple.copy(alpha = 0.10f), Offset(left, 0f), Size((right - left).coerceAtLeast(0f), size.height))
        }
        drawLine(Color(0xFFD8E3DB), Offset(0f, size.height - 3), Offset(size.width, size.height - 3))
        val path = Path(); var penDown = false
        samples.forEach { p ->
            val speed = p.speedMps
            if (speed == null) penDown = false else {
                val x = (p.atMs.toDouble() / duration * size.width).toFloat()
                val y = size.height - 8 - (speed / maxSpeed * (size.height - 16)).toFloat()
                if (penDown) path.lineTo(x, y) else path.moveTo(x, y)
                penDown = true
            }
        }
        drawPath(path, Teal, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        report.events.filter { it.kind == EventKind.FEEDBACK }.forEach { e -> drawCircle(Purple, 5.dp.toPx(), Offset((e.atMs.toDouble() / duration * size.width).toFloat(), size.height - 5)) }
    }
}
@Composable private fun EventRow(e: CoachEvent) {
    Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(clock(e.atMs), color = Muted, fontSize = 13.sp, modifier = Modifier.width(42.dp))
        Box(Modifier.padding(top = 5.dp).size(9.dp).background(if (e.kind == EventKind.FEEDBACK) Purple else Teal, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.title, fontWeight = FontWeight.Bold, color = if (e.kind == EventKind.FEEDBACK) Purple else Ink)
            Text(e.detail, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}
@Composable private fun History(s: CoachUiState, vm: CoachViewModel, export: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Title("JOURNAL", "우리의 이동 기록", "최근 10회가 이 기기에 저장돼요. 시연과 차량 기록은 구분해요.")
    if (s.history.isEmpty()) Panel(Modifier.fillMaxWidth()) { Text("첫 이동을 기록해보세요", fontSize = 22.sp, fontWeight = FontWeight.Bold); Text("기록을 마치면 이곳에서 다시 볼 수 있어요.", color = Muted) }
    s.history.forEach { report ->
        Panel(Modifier.fillMaxWidth()) {
            Text("${if (report.mode == InputMode.DEMO) "시연" else "차량"} · ${date(report.createdAt)}", color = Teal, fontSize = 13.sp)
            report.scenario?.let { Text(it, color = Purple, fontSize = 13.sp) }
            Text(report.goal, fontSize = 17.sp, lineHeight = 26.sp)
            Text("${clock(report.durationMs)}  ·  체감 ${report.feedbackCount}회  ·  주행 변화 ${report.changesCount}회", color = Muted)
            TextButton(onClick = { vm.openReport(report) }) { Text("리포트 보기 →") }
        }
    }
    if (s.history.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = export, enabled = !s.active) { Text("기록 내보내기") }
        TextButton(onClick = { confirm = true }, enabled = !s.active) { Text("모든 기록 지우기") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("이동 기록을 지울까요?") }, text = { Text("이 기기에 저장된 최근 10회 기록이 삭제돼요.") }, confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("지우기") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } })
}
@Composable private fun Connection(s: CoachUiState, vm: CoachViewModel) {
    val clipboard = LocalClipboardManager.current
    Title("CONNECTION", "우리 차와 연결하기", "차량 데이터가 없으면 값을 만들어 채우지 않아요.")
    Panel(Modifier.fillMaxWidth()) {
        Text("데이터 모드", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = s.mode == InputMode.DEMO, onClick = { vm.selectMode(InputMode.DEMO) }, enabled = !s.active && !(s.automotive && s.controlsLocked), label = { Text("60초 시연") })
            FilterChip(selected = s.mode == InputMode.PLEOS, onClick = { vm.selectMode(InputMode.PLEOS) }, enabled = !s.active && !(s.automotive && s.controlsLocked), label = { Text("Pleos 차량") })
        }
        Text(if (s.mode == InputMode.DEMO) "가상 속도·회전·방지턱으로 앱의 전체 흐름을 경험해요." else "Pleos Connect의 차량 속도·조향각과 내비게이션 안내를 읽어요.", color = Muted, lineHeight = 23.sp)
        if (s.mode == InputMode.PLEOS || s.automotive) {
            s.vehicle.status.forEach { (label, value) -> Text("$label  ·  $value", fontSize = 14.sp) }
            Text("P단 정차: ${if (s.vehicle.parked) "확인" else "미확인"}   |   조향각: ${s.vehicle.steeringDeg?.let { "${it.toInt()}°" } ?: "—"}", color = Muted)
            OutlinedButton(onClick = vm::connect, enabled = !s.active) { Text("다시 연결") }
        }
    }
    Panel(Modifier.fillMaxWidth()) {
        Text("안내 방식", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) { Text("음성 안내", Modifier.weight(1f)); Switch(s.voice, { vm.toggleVoice() }, enabled = !s.controlsLocked) }
        Text(s.voiceStatus, color = Muted, fontSize = 12.sp)
        OutlinedButton(onClick = vm::previewVoice, enabled = s.voice && !s.active && !s.controlsLocked) {
            Text("안내 음성 미리 듣기")
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("안내를 더 드물게"); Text("최소 간격 15초 → 25초", color = Muted, fontSize = 12.sp) }; Switch(s.sparse, { vm.toggleSparse() }, enabled = !s.controlsLocked) }
    }
    Panel(Modifier.fillMaxWidth(), Lilac) {
        Text("동승자 휴대폰 연결", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("같은 Wi-Fi에서 아래 주소를 동승자의 휴대폰으로 열면, 차량 화면을 만지지 않고 체감을 남길 수 있어요.", color = Muted, lineHeight = 23.sp)
        Button(onClick = vm::startPairing, enabled = !s.controlsLocked) { Text(if (s.pairingPath == null) "동승자 연결 시작" else "동승자 연결 종료") }
        s.pairingUrls.forEach { url ->
            Text(url, fontSize = 12.sp, color = Purple)
            TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text("주소 복사") }
        }
        if (s.pairingPath != null && s.pairingUrls.isEmpty()) Text("사용할 Wi-Fi 주소를 찾지 못했어요. 테스트 PC 연결은 README를 참고하세요.")
        Text("링크를 가진 사람이 체감을 입력할 수 있어요. 신뢰하는 Wi-Fi에서 사용하세요. 앱을 벗어나면 연결이 종료돼요.", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
    }
    Text("속도로 추정한 가속도와 핸들 조향 변화는 뒷좌석의 실제 흔들림 측정값이 아니에요. 안내 시점은 현재 속도를 이용한 근사값이에요.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
}
private fun clock(ms: Long) = String.format(Locale.US, "%02d:%02d", ms / 60000, ms / 1000 % 60)
private fun date(ms: Long) = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(ms))
