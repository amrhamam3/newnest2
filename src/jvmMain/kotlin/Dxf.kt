import java.io.File

/**
 * قارئ ملفات DXF (ASCII DXF فقط) حقيقي.
 * بيدعم: LINE, LWPOLYLINE (بدون bulge/انحناء)، CIRCLE، ARC.
 * الناتج (DxfModel) هو نفسه اللي NestingShapeBuilder.fromModel() بيحوّله لـ NestingPolygon.
 */
fun parseDxfFile(path: String): DxfModel {
    val raw = File(path).readLines()
    // ملفات DXF بتتكون من أزواج سطرين: كود (رقم) + قيمة، سطر لكل واحد
    val lines = raw.map { it.trim() }
    var i = 0

    val dxfLines = mutableListOf<DxfLine>()
    val circles = mutableListOf<DxfCircle>()
    val arcs = mutableListOf<DxfArc>()

    fun nextPair(): Pair<String, String>? {
        if (i > lines.size - 2) return null
        val pair = lines[i] to lines[i + 1]
        i += 2
        return pair
    }

    while (i < lines.size - 1) {
        val code = lines[i]
        val value = lines.getOrNull(i + 1) ?: break

        when {
            code == "0" && value == "LINE" -> {
                i += 2
                var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
                while (i < lines.size - 1 && lines[i] != "0") {
                    val (c, v) = nextPair() ?: break
                    when (c) {
                        "10" -> x1 = v.toFloatOrNull() ?: 0f
                        "20" -> y1 = v.toFloatOrNull() ?: 0f
                        "11" -> x2 = v.toFloatOrNull() ?: 0f
                        "21" -> y2 = v.toFloatOrNull() ?: 0f
                    }
                }
                dxfLines += DxfLine(x1, y1, x2, y2)
            }

            code == "0" && value == "LWPOLYLINE" -> {
                i += 2
                val xs = mutableListOf<Float>()
                val ys = mutableListOf<Float>()
                var closed = false
                while (i < lines.size - 1 && lines[i] != "0") {
                    val (c, v) = nextPair() ?: break
                    when (c) {
                        "10" -> xs += v.toFloatOrNull() ?: 0f
                        "20" -> ys += v.toFloatOrNull() ?: 0f
                        "70" -> closed = ((v.toIntOrNull() ?: 0) and 1) != 0
                    }
                }
                val n = minOf(xs.size, ys.size)
                for (k in 0 until n) {
                    val nextIdx = if (k == n - 1) (if (closed) 0 else -1) else k + 1
                    if (nextIdx >= 0) dxfLines += DxfLine(xs[k], ys[k], xs[nextIdx], ys[nextIdx])
                }
            }

            code == "0" && value == "CIRCLE" -> {
                i += 2
                var cx = 0f; var cy = 0f; var r = 0f
                while (i < lines.size - 1 && lines[i] != "0") {
                    val (c, v) = nextPair() ?: break
                    when (c) {
                        "10" -> cx = v.toFloatOrNull() ?: 0f
                        "20" -> cy = v.toFloatOrNull() ?: 0f
                        "40" -> r = v.toFloatOrNull() ?: 0f
                    }
                }
                circles += DxfCircle(cx, cy, r)
            }

            code == "0" && value == "ARC" -> {
                i += 2
                var cx = 0f; var cy = 0f; var r = 0f; var a1 = 0f; var a2 = 0f
                while (i < lines.size - 1 && lines[i] != "0") {
                    val (c, v) = nextPair() ?: break
                    when (c) {
                        "10" -> cx = v.toFloatOrNull() ?: 0f
                        "20" -> cy = v.toFloatOrNull() ?: 0f
                        "40" -> r = v.toFloatOrNull() ?: 0f
                        "50" -> a1 = v.toFloatOrNull() ?: 0f
                        "51" -> a2 = v.toFloatOrNull() ?: 0f
                    }
                }
                arcs += DxfArc(cx, cy, r, a1, a2)
            }

            else -> i++
        }
    }

    return DxfModel(lines = dxfLines, circles = circles, arcs = arcs)
}
