import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.FileDialog
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.*

// ملف تشخيص بسيط بيتكتب على سطح المكتب لتسجيل أي خطأ غير متوقع أثناء الرص
val debugLogFile = File(System.getProperty("user.home"), "Desktop/Amr3D_debug_log.txt")
fun logDebug(msg: String) {
    try {
        val ts = SimpleDateFormat("HH:mm:ss.SSS").format(Date())
        debugLogFile.appendText("[$ts] $msg\n")
    } catch (_: Exception) { }
}

// ====================== دالة تشغيل التطبيق الرئيسية ======================
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Amr3D Nesting Pro",
        state = rememberWindowState(width = 1360.dp, height = 860.dp)
    ) {
        MaterialTheme(colors = appDarkColors()) {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
                App()
            }
        }
    }
}

private enum class SourceMode { NONE, DXF, CUSTOM }

@Composable
fun App() {
    var sourceMode by remember { mutableStateOf(SourceMode.NONE) }
    var dxfFileName by remember { mutableStateOf<String?>(null) }
    var dxfModel by remember { mutableStateOf<DxfModel?>(null) }

    var customW by remember { mutableStateOf("600") }
    var customH by remember { mutableStateOf("450") }

    var boardW by remember { mutableStateOf("1220") }
    var boardH by remember { mutableStateOf("2440") }
    var copies by remember { mutableStateOf("10") }
    var clearance by remember { mutableStateOf("5") }
    var margin by remember { mutableStateOf("10") }
    var rotationStep by remember { mutableStateOf("15") }
    var rotationMode by remember { mutableStateOf(RotationMode.FREE) }
    var rotationMenuOpen by remember { mutableStateOf(false) }

    var status by remember { mutableStateOf("جاهز — لم يتم تحديد مصدر الرص") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<NestingResult?>(null) }

    fun startNesting() {
        val polygon: NestingPolygon? = when (sourceMode) {
            SourceMode.DXF -> dxfModel?.let { NestingShapeBuilder.fromModel(it) }
            SourceMode.CUSTOM -> {
                val w = customW.toDoubleOrNull()
                val h = customH.toDoubleOrNull()
                if (w != null && h != null && w > 0 && h > 0) {
                    NestingPolygon(
                        outer = listOf(
                            NestingPoint(0.0, 0.0),
                            NestingPoint(w, 0.0),
                            NestingPoint(w, h),
                            NestingPoint(0.0, h)
                        )
                    )
                } else null
            }
            SourceMode.NONE -> null
        }

        if (polygon == null) {
            status = "حدد مصدر الرص أولاً (ملف DXF قابل للقراءة أو قياسات مخصصة صحيحة)"
            return
        }

        val config = NestingConfig(
            boardWidth = boardW.toDoubleOrNull() ?: 1220.0,
            boardHeight = boardH.toDoubleOrNull() ?: 2440.0,
            copies = (copies.toIntOrNull() ?: 1).coerceAtLeast(1),
            rotationStepDeg = (rotationStep.toDoubleOrNull() ?: 15.0).coerceAtLeast(1.0),
            rotationMode = rotationMode,
            clearanceMm = (clearance.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
            edgeTopMm = (margin.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
            edgeBottomMm = (margin.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
            edgeLeftMm = (margin.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
            edgeRightMm = (margin.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
        )

        running = true
        result = null
        status = "جاري تنفيذ NESTING..."

        Thread {
            try {
                val r = NestingEngine.nest(polygon, config) { }
                result = r
                val utilizationText = String.format("%.1f", r.utilization)
                status = "اكتمل الرص — ${r.totalPlaced}/${r.totalRequested} قطعة | الألواح: ${r.boards.size} | التوفير التقريبي: ${utilizationText}%"
            } catch (ex: Exception) {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                logDebug("Nesting error:\n$sw")
                status = "حصل خطأ أثناء الرص: ${ex.message ?: "غير معروف"} (التفاصيل في Amr3D_debug_log.txt على سطح المكتب)"
            } finally {
                running = false
            }
        }.start()
    }

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // ====================== الهيدر العلوي ======================
        Row(
            Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(AppColors.topBar)
                .border(width = 0.5.dp, color = AppColors.line)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Amr3D Nesting Pro", color = AppColors.text, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .background(AppColors.orange, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) { Text("BETA", color = Color(0xFF111111), fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            Text(status, color = AppColors.muted, fontSize = 12.sp, maxLines = 1)
        }

        Row(Modifier.fillMaxSize()) {
            // ====================== الشريط الجانبي (الإعدادات) ======================
            Column(
                Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(AppColors.sidebarBg)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                StepCard(num = "01", title = "مصدر الشكل") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        OrangeButton(
                            text = if (dxfFileName != null) "استبدال ملف DXF" else "اختيار ملف DXF",
                            modifier = Modifier.weight(1f)
                        ) {
                            val path = pickDxfFile()
                            if (path != null) {
                                try {
                                    dxfModel = parseDxfFile(path)
                                    dxfFileName = File(path).name
                                    sourceMode = SourceMode.DXF
                                    status = "تم تحميل الملف: ${dxfFileName}"
                                } catch (ex: Exception) {
                                    status = "تعذّرت قراءة الملف: ${ex.message ?: "خطأ غير معروف"}"
                                }
                            }
                        }
                        DarkButton(text = "قياسات مخصصة", modifier = Modifier.weight(1f)) {
                            sourceMode = SourceMode.CUSTOM
                            status = "وضع القياسات المخصصة"
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    SourceInfoBox(sourceMode, dxfFileName)

                    if (sourceMode == SourceMode.CUSTOM) {
                        Spacer(Modifier.height(9.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            DarkField(customW, { customW = it }, "الطول mm", Modifier.weight(1f))
                            DarkField(customH, { customH = it }, "العرض mm", Modifier.weight(1f))
                        }
                    }
                }

                StepCard(num = "02", title = "مقاس اللوح") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        DarkField(boardW, { boardW = it }, "الطول mm", Modifier.weight(1f))
                        DarkField(boardH, { boardH = it }, "العرض mm", Modifier.weight(1f))
                    }
                }

                StepCard(num = "03", title = "عدد النسخ المطلوب رصها") {
                    DarkField(copies, { copies = it }, "العدد", Modifier.fillMaxWidth())
                }

                StepCard(num = "04", title = "ماكينة + هامش الرص") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        DarkField(clearance, { clearance = it }, "المسافة بين القطع mm", Modifier.weight(1f))
                        DarkField(margin, { margin = it }, "هامش الحواف mm", Modifier.weight(1f))
                    }
                }

                StepCard(num = "05", title = "اتجاه وطريقة الرص") {
                    Box {
                        DarkButton(
                            text = when (rotationMode) {
                                RotationMode.FREE -> "حر (يجرب كل الزوايا)"
                                RotationMode.HORIZONTAL -> "أفقي فقط (0° / 180°)"
                                RotationMode.VERTICAL -> "رأسي فقط (90° / 270°)"
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { rotationMenuOpen = true }
                        DropdownMenu(expanded = rotationMenuOpen, onDismissRequest = { rotationMenuOpen = false }) {
                            DropdownMenuItem(onClick = { rotationMode = RotationMode.FREE; rotationMenuOpen = false }) {
                                Text("حر (يجرب كل الزوايا)")
                            }
                            DropdownMenuItem(onClick = { rotationMode = RotationMode.HORIZONTAL; rotationMenuOpen = false }) {
                                Text("أفقي فقط (0° / 180°)")
                            }
                            DropdownMenuItem(onClick = { rotationMode = RotationMode.VERTICAL; rotationMenuOpen = false }) {
                                Text("رأسي فقط (90° / 270°)")
                            }
                        }
                    }
                    if (rotationMode == RotationMode.FREE) {
                        Spacer(Modifier.height(9.dp))
                        DarkField(rotationStep, { rotationStep = it }, "خطوة الدوران (درجة)", Modifier.fillMaxWidth())
                    }
                }

                Spacer(Modifier.height(6.dp))
                OrangeButton(
                    text = if (running) "جاري الرص..." else "ابدأ NESTING",
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = !running
                ) { startNesting() }
            }

            Divider(color = AppColors.line, modifier = Modifier.fillMaxHeight().width(1.dp))

            // ====================== منطقة المعاينة الحقيقية ======================
            Box(Modifier.weight(1f).fillMaxHeight().background(AppColors.mainBg).padding(18.dp)) {
                NestingPreviewView(result = result, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun SourceInfoBox(sourceMode: SourceMode, dxfFileName: String?) {
    val (title, desc) = when (sourceMode) {
        SourceMode.NONE -> "لم يتم التحديد" to "اختر مصدر الرص أولاً."
        SourceMode.DXF -> "تم تحديد ملف DXF" to (dxfFileName ?: "")
        SourceMode.CUSTOM -> "قياسات مخصصة" to "مستقلة عن أي ملف DXF."
    }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, AppColors.fieldBorder, RoundedCornerShape(8.dp))
            .padding(11.dp)
    ) {
        Text(title, color = AppColors.orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (desc.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(desc, color = AppColors.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun StepCard(num: String, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(AppColors.panel, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.line, RoundedCornerShape(12.dp))
            .padding(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(num, color = AppColors.orange, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, color = AppColors.orange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun DarkField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        modifier = modifier,
        colors = TextFieldDefaults.outlinedTextFieldColors(
            textColor = AppColors.text,
            backgroundColor = AppColors.fieldBg,
            focusedBorderColor = AppColors.orange,
            unfocusedBorderColor = AppColors.fieldBorder,
            focusedLabelColor = AppColors.orange,
            unfocusedLabelColor = AppColors.muted,
            cursorColor = AppColors.orange
        )
    )
}

@Composable
private fun OrangeButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.orange,
            contentColor = Color(0xFF111111),
            disabledBackgroundColor = AppColors.orange.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(7.dp)
    ) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun DarkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = AppColors.panel2,
            contentColor = AppColors.text
        ),
        shape = RoundedCornerShape(7.dp)
    ) { Text(text, fontSize = 13.sp) }
}

/** بيفتح مربع حوار اختيار ملف (AWT) ويرجع المسار الكامل، أو null لو المستخدم لغى */
private fun pickDxfFile(): String? {
    val dialog = FileDialog(null as java.awt.Frame?, "اختر ملف DXF", FileDialog.LOAD)
    dialog.file = "*.dxf"
    dialog.isVisible = true
    val dir = dialog.directory
    val file = dialog.file
    return if (dir != null && file != null) dir + file else null
}

// ====================== كود محرك الرص الخاص بك (Data Classes) ======================
data class NestingPoint(val x: Double, val y: Double)

data class NestingPolygon(
    val outer: List<NestingPoint>,
    val holes: List<List<NestingPoint>> = emptyList()
)

data class NestingPiece(
    val index: Int,
    val polygon: NestingPolygon,
    val x: Double,
    val y: Double,
    val rotationDeg: Double,
    val boundsWidth: Double,
    val boundsHeight: Double
)

data class NestingBoard(
    val index: Int,
    val width: Double,
    val height: Double,
    val pieces: List<NestingPiece>,
    val color: Int = 0xFF0D0F14.toInt()
)

data class NestingResult(
    val boards: List<NestingBoard>,
    val totalRequested: Int,
    val totalPlaced: Int,
    val sourceWidth: Double,
    val sourceHeight: Double,
    val sourceArea: Double,
    val elapsedMs: Long
) {
    val boardArea: Double get() = boards.sumOf { it.width * it.height }
    val usedArea: Double get() = sourceArea * totalPlaced
    val utilization: Double get() = if (boardArea > 0.0) usedArea / boardArea * 100.0 else 0.0
    val wasteArea: Double get() = (boardArea - usedArea).coerceAtLeast(0.0)
}

data class NestingConfig(
    val boardWidth: Double = 1220.0,
    val boardHeight: Double = 2440.0,
    val copies: Int = 1,
    val rotationStepDeg: Double = 15.0,
    val rotationMode: RotationMode = RotationMode.FREE,
    val grainAxis: GrainAxis = GrainAxis.FREE,
    val clearanceMm: Double = 0.0,
    val boardColor: Int = 0xFF0D0F14.toInt(),
    val edgeTopMm: Double = 0.0,
    val edgeBottomMm: Double = 0.0,
    val edgeLeftMm: Double = 0.0,
    val edgeRightMm: Double = 0.0
)

enum class RotationMode { FREE, HORIZONTAL, VERTICAL }
enum class GrainAxis { FREE, HORIZONTAL, VERTICAL }
enum class NestingStage { NESTING, SAVING, PREVIEW }

data class NestingProgress(
    val placed: Int,
    val total: Int,
    val boardIndex: Int,
    val percent: Int,
    val stage: NestingStage = NestingStage.NESTING,
    val stagePercent: Int = percent,
    val stageLabel: String = "جاري الرص"
)

// نماذج بيانات DXF الحقيقية — بيتم ملؤها فعليًا بواسطة parseDxfFile() في Dxf.kt
data class DxfModel(
    val lines: List<DxfLine> = emptyList(),
    val circles: List<DxfCircle> = emptyList(),
    val arcs: List<DxfArc> = emptyList()
)
class DxfLine(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
class DxfCircle(val cx: Float, val cy: Float, val r: Float)
class DxfArc(val cx: Float, val cy: Float, val r: Float, val startDeg: Float, val endDeg: Float)

// ====================== خوارزمية بناء الأشكال (Shape Builder) ======================
object NestingShapeBuilder {
    private const val EPS = 0.05
    fun distance(a: NestingPoint, b: NestingPoint) = sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2))
    fun signedArea(p: List<NestingPoint>): Double {
        var area = 0.0
        for (i in p.indices) {
            val j = (i + 1) % p.size
            area += p[i].x * p[j].y - p[j].x * p[i].y
        }
        return area / 2.0
    }
    fun pointInPolygon(pt: NestingPoint, poly: List<NestingPoint>): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            if (((poly[i].y > pt.y) != (poly[j].y > pt.y)) &&
                (pt.x < (poly[j].x - poly[i].x) * (pt.y - poly[i].y) / (poly[j].y - poly[i].y) + poly[i].x)) {
                c = !c
            }
            j = i
        }
        return c
    }
    fun normalizedSpan(start: Double, end: Double): Double {
        var res = end - start
        while (res < 0) res += 360.0
        return res
    }

    fun fromModel(model: DxfModel): NestingPolygon? {
        val segments = mutableListOf<Pair<NestingPoint, NestingPoint>>()
        for (l in model.lines) {
            val a = NestingPoint(l.x1.toDouble(), l.y1.toDouble())
            val b = NestingPoint(l.x2.toDouble(), l.y2.toDouble())
            if (distance(a, b) > EPS) segments += a to b
        }
        for (c in model.circles) {
            val pts = circlePoints(c.cx.toDouble(), c.cy.toDouble(), c.r.toDouble(), 96)
            for (i in pts.indices) segments += pts[i] to pts[(i + 1) % pts.size]
        }
        for (a in model.arcs) {
            val span = normalizedSpan(a.startDeg.toDouble(), a.endDeg.toDouble())
            val steps = max(8, min(500, ceil(abs(span) / 7.5).toInt()))
            val pts = (0..steps).map { i ->
                val d = a.startDeg.toDouble() + span * i / steps
                val r = Math.toRadians(d)
                NestingPoint(a.cx.toDouble() + a.r.toDouble() * cos(r), a.cy.toDouble() + a.r.toDouble() * sin(r))
            }
            for (i in 0 until pts.size - 1) segments += pts[i] to pts[i + 1]
        }
        if (segments.isEmpty()) return null
        val loops = traceFaces(segments).map { cleanLoop(it) }.filter { it.size >= 3 && abs(signedArea(it)) > 0.01 }
        if (loops.isEmpty()) return null
        val outer = loops.maxByOrNull { abs(signedArea(it)) } ?: return null
        val holes = loops.filter { it !== outer }.filter { signedArea(it) * signedArea(outer) < 0.0 }.filter { it.isNotEmpty() && pointInPolygon(it[0], outer) }.map { normalizeWinding(it, wantPositive = signedArea(outer) < 0.0) }
        val woundOuter = normalizeWinding(outer, true)
        val minX = woundOuter.minOfOrNull { it.x } ?: 0.0
        val minY = woundOuter.minOfOrNull { it.y } ?: 0.0
        val outerNorm = woundOuter.map { NestingPoint(it.x - minX, it.y - minY) }
        val holeNorm = holes.map { h -> h.map { NestingPoint(it.x - minX, it.y - minY) } }
        return NestingPolygon(outer = outerNorm, holes = holeNorm)
    }

    private fun traceFaces(segments: List<Pair<NestingPoint, NestingPoint>>): List<List<NestingPoint>> {
        val points = mutableListOf<NestingPoint>()
        val cellSize = EPS * 2.0
        val grid = HashMap<Long, MutableList<Int>>()
        fun cellKey(cx: Int, cy: Int) = (cx.toLong() shl 32) xor (cy.toLong() and 0xffffffffL)
        fun cellOf(p: NestingPoint) = floor(p.x / cellSize).toInt() to floor(p.y / cellSize).toInt()
        fun pointId(p: NestingPoint): Int {
            val (cx, cy) = cellOf(p)
            for (dx in -1..1) for (dy in -1..1) {
                val bucket = grid[cellKey(cx + dx, cy + dy)] ?: continue
                for (idx in bucket) if (distance(points[idx], p) <= EPS) return idx
            }
            points += p
            val newIdx = points.lastIndex
            grid.getOrPut(cellKey(cx, cy)) { mutableListOf() } += newIdx
            return newIdx
        }
        data class Edge(val a: Int, val b: Int)
        val edges = segments.map { Edge(pointId(it.first), pointId(it.second)) }
        if (edges.isEmpty()) return emptyList()
        data class Half(val from: Int, val to: Int, val edge: Int)
        val half = mutableListOf<Half>()
        val outgoing = Array(points.size) { mutableListOf<Int>() }
        for ((ei, e) in edges.withIndex()) {
            val h0 = half.size
            half += Half(e.a, e.b, ei)
            half += Half(e.b, e.a, ei)
            outgoing[e.a] += h0
            outgoing[e.b] += h0 + 1
        }
        val order = outgoing.map { list -> list.sortedWith(compareBy { atan2(points[half[it].to].y - points[half[it].from].y, points[half[it].to].x - points[half[it].from].x) }) }
        val next = IntArray(half.size) { -1 }
        for (h in half.indices) {
            val v = half[h].to
            val list = order[v]
            val reverse = list.indexOfFirst { half[it].to == half[h].from }
            if (reverse >= 0) next[h] = list[(reverse - 1 + list.size) % list.size]
        }
        val visited = BooleanArray(half.size)
        val faces = mutableListOf<List<NestingPoint>>()
        for (start in half.indices) {
            if (visited[start] || next[start] < 0) continue
            val loop = mutableListOf<NestingPoint>()
            var h = start
            var guard = 0
            while (!visited[h] && guard++ < half.size + 4) {
                visited[h] = true
                loop += points[half[h].from]
                h = next[h]
                if (h == start) break
            }
            if (h == start && loop.size >= 3) faces += loop
        }
        return faces
    }

    private fun cleanLoop(loop: List<NestingPoint>): List<NestingPoint> {
        val out = mutableListOf<NestingPoint>()
        for (p in loop) if (out.isEmpty() || distance(out.last(), p) > EPS) out += p
        if (out.size > 1 && distance(out.first(), out.last()) <= EPS) out.removeAt(out.lastIndex)
        return out
    }
    private fun normalizeWinding(p: List<NestingPoint>, wantPositive: Boolean): List<NestingPoint> {
        val a = signedArea(p)
        return if ((a > 0) == wantPositive) p else p.asReversed()
    }
    private fun circlePoints(cx: Double, cy: Double, r: Double, n: Int) =
        (0 until n).map {
            val a = 2.0 * PI * it / n
            NestingPoint(cx + r * cos(a), cy + r * sin(a))
        }
}
