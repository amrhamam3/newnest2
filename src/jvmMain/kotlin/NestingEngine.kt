import kotlin.math.*

/**
 * محرك الرص الحقيقي (Real Nesting Engine)
 * ==========================================
 * بياخد شكل واحد (NestingPolygon) وبيحاول يرصه Config.copies مرة على لوح/ألواح
 * بمقاس NestingConfig.boardWidth × boardHeight.
 *
 * الفرق عن الرص القديم اللي في index.html (JS):
 * - القديم كان بيحسب "الصندوق المحيط" (bounding box) بس ويرصه زي مستطيل.
 * - هنا بنفحص تصادم الشكل الحقيقي (edge-to-edge + احتواء نقطة) مش الصندوق،
 *   يعني لو الشكل غير منتظم (زي قطعة L أو دائرة فيها زاوية ناقصة) الرص بيبقى
 *   أدق وبيستغل المساحة أحسن.
 * - بيجرب زوايا دوران مختلفة لكل قطعة (حسب RotationMode) ويختار أفضل مكان.
 * - بيحترم "المسافة بين القطع" (clearanceMm) كمسافة حقيقية بين حواف الأشكال،
 *   مش بس فرق في الصندوق المحيط.
 *
 * ملحوظة: الفتحات الداخلية (holes) بيتم تجاهلها في اختبار التصادم لتبسيط
 * الحساب (الرص هيحترم الحد الخارجي بس). لو حبيت دقة أعلى ممكن نضيفها لاحقاً.
 */
object NestingEngine {

    fun nest(
        piece: NestingPolygon,
        config: NestingConfig,
        onProgress: ((NestingProgress) -> Unit)? = null
    ): NestingResult {
        val startTime = System.currentTimeMillis()

        val rotations = rotationCandidates(config)
        val usableWidth = config.boardWidth - config.edgeLeftMm - config.edgeRightMm
        val usableHeight = config.boardHeight - config.edgeTopMm - config.edgeBottomMm

        val sourceOuterArea = abs(NestingShapeBuilder.signedArea(piece.outer))

        val boards = mutableListOf<NestingBoard>()
        var currentBoardPieces = mutableListOf<NestingPiece>()
        var boardIndex = 0
        var placedCount = 0
        val totalRequested = config.copies

        // خطوة البحث عن مكان: كل ما الشكل أصغر كل ما بندور بدقة أعلى (وبطء أكتر)،
        // فبنربطها بحجم الشكل نفسه عشان الأداء يفضل معقول.
        val pieceBounds = boundsOf(piece.outer)
        val minSide = max(1.0, min(pieceBounds.width, pieceBounds.height))
        val step = max(2.0, minSide / 12.0)

        repeat(config.copies) { copyIndex ->
            var placement = findPlacement(
                existing = currentBoardPieces,
                piece = piece,
                rotations = rotations,
                usableWidth = usableWidth,
                usableHeight = usableHeight,
                clearance = config.clearanceMm,
                step = step
            )

            // لو مفيش مكان في اللوح الحالي، اقفله وافتح لوح جديد
            if (placement == null) {
                boards += finalizeBoard(boardIndex, currentBoardPieces, config)
                boardIndex++
                currentBoardPieces = mutableListOf()
                placement = findPlacement(
                    existing = currentBoardPieces,
                    piece = piece,
                    rotations = rotations,
                    usableWidth = usableWidth,
                    usableHeight = usableHeight,
                    clearance = config.clearanceMm,
                    step = step
                )
            }

            if (placement != null) {
                val worldPoly = transform(piece.outer, placement.rotationDeg, placement.offsetX, placement.offsetY)
                val bounds = boundsOf(worldPoly)
                // ملحوظة: x/y هنا لسه بمساحة "القابلة للاستخدام" (من غير هامش الحواف)
                // عشان اختبار التصادم مع القطع الأخرى في نفس اللوح يفضل متسق. هامش
                // الحواف بيتضاف مرة واحدة بس لحظة إقفال اللوح في finalizeBoard.
                currentBoardPieces += NestingPiece(
                    index = copyIndex,
                    polygon = piece,
                    x = placement.offsetX,
                    y = placement.offsetY,
                    rotationDeg = placement.rotationDeg,
                    boundsWidth = bounds.width,
                    boundsHeight = bounds.height
                )
                placedCount++
            }
            // لو حتى اللوح الجديد الفاضي مقدرش يستوعب القطعة (القطعة أكبر من
            // اللوح نفسه) بنسيبها من غير رص وبنكمل - القطعة دي مش قابلة للرص أصلاً.

            onProgress?.invoke(
                NestingProgress(
                    placed = placedCount,
                    total = totalRequested,
                    boardIndex = boardIndex,
                    percent = ((copyIndex + 1) * 100 / max(1, totalRequested)),
                    stage = NestingStage.NESTING,
                    stagePercent = ((copyIndex + 1) * 100 / max(1, totalRequested)),
                    stageLabel = "جاري الرص"
                )
            )
        }

        // اقفل آخر لوح (سواء فيه قطع أو حتى فاضي لو محصلش رص خالص)
        boards += finalizeBoard(boardIndex, currentBoardPieces, config)

        val elapsed = System.currentTimeMillis() - startTime
        return NestingResult(
            boards = boards,
            totalRequested = totalRequested,
            totalPlaced = placedCount,
            sourceWidth = pieceBounds.width,
            sourceHeight = pieceBounds.height,
            sourceArea = sourceOuterArea,
            elapsedMs = elapsed
        )
    }

    /** بيقفل لوح: بياخد إحداثيات القطع (بمساحة "القابل للاستخدام") ويضيفلها هامش
     *  الحواف مرة واحدة بس، عشان تبقى جاهزة للعرض/الإخراج بإحداثيات اللوح الكاملة. */
    private fun finalizeBoard(index: Int, pieces: List<NestingPiece>, config: NestingConfig): NestingBoard {
        val shifted = pieces.map { it.copy(x = it.x + config.edgeLeftMm, y = it.y + config.edgeTopMm) }
        return NestingBoard(
            index = index,
            width = config.boardWidth,
            height = config.boardHeight,
            pieces = shifted,
            color = config.boardColor
        )
    }

    // ====================== البحث عن أفضل مكان لقطعة واحدة ======================

    private data class Placement(val offsetX: Double, val offsetY: Double, val rotationDeg: Double)

    private fun findPlacement(
        existing: List<NestingPiece>,
        piece: NestingPolygon,
        rotations: List<Double>,
        usableWidth: Double,
        usableHeight: Double,
        clearance: Double,
        step: Double
    ): Placement? {
        // بنحول القطع الموجودة بالفعل على اللوح لإحداثيات عالمية جاهزة للمقارنة
        val placedPolys = existing.map { p ->
            transform(p.polygon.outer, p.rotationDeg, p.x, p.y)
        }

        for (rotationDeg in rotations) {
            val rotatedLocal = rotate(piece.outer, rotationDeg)
            val localBounds = boundsOf(rotatedLocal)
            // بنطبّع الشكل عشان أصغر إحداثي يبقى صفر، فبعدين offsetX/Y = ركن الصندوق فعلياً
            val normalized = rotatedLocal.map {
                NestingPoint(it.x - localBounds.minX, it.y - localBounds.minY)
            }
            val w = localBounds.width
            val h = localBounds.height
            if (w > usableWidth || h > usableHeight) continue // القطعة أكبر من اللوح بالاتجاه ده

            // ==== المحاولة الأولى: NFP حقيقي (Minkowski Sum) للأشكال المحدّبة ====
            // ده نفس الفكرة اللي Deepnest/SVGnest بتستخدمها: بدل ما ندور شبكيًا،
            // بنحسب مباشرة المواضع اللي القطعة الجديدة هتلامس فيها القطع المرصوصة
            // من غير تداخل. النتيجة رص متلاصق تمامًا (زي القص الصناعي الحقيقي)
            // وأسرع بكتير من المسح الشبكي.
            if (isConvex(normalized)) {
                val nfpPlacement = tryNfpPlacement(normalized, placedPolys, w, h, usableWidth, usableHeight, clearance)
                if (nfpPlacement != null) return Placement(nfpPlacement.first, nfpPlacement.second, rotationDeg)
                // لو الشكل محدّب بس مفيش موضع صالح طلع من الـ NFP (نادر، غالبًا بسبب
                // clearance)، نكمل على البحث الشبكي تحت كـ شبكة أمان (fallback)
            }

            // ==== Fallback: مسح شبكي Bottom-Left (بيشتغل مع أي شكل حتى المقعّر) ====
            var y = 0.0
            while (y + h <= usableHeight) {
                var x = 0.0
                while (x + w <= usableWidth) {
                    val candidate = normalized.map { NestingPoint(it.x + x, it.y + y) }
                    if (fits(candidate, placedPolys, clearance)) {
                        return Placement(x, y, rotationDeg)
                    }
                    x += step
                }
                y += step
            }
        }
        return null
    }

    /**
     * بيحاول يلاقي أقرب موضع "ملامسة" (touching) للقطعة المرشحة (محدّبة) باستخدام
     * الـ No-Fit-Polygon مع كل قطعة اتحطت قبل كده، وبيختار أفضل واحد (Bottom-Left).
     * برجع null لو مفيش موضع صالح (يخلي findPlacement يكمل على البحث الشبكي).
     */
    private fun tryNfpPlacement(
        candidateLocal: List<NestingPoint>,
        placedPolys: List<List<NestingPoint>>,
        w: Double,
        h: Double,
        usableWidth: Double,
        usableHeight: Double,
        clearance: Double
    ): Pair<Double, Double>? {
        val maxX = usableWidth - w
        val maxY = usableHeight - h
        if (maxX < -1e-9 || maxY < -1e-9) return null

        // اللوح فاضي: أفضل موضع هو ركن أسفل-يسار المساحة القابلة للاستخدام مباشرة
        if (placedPolys.isEmpty()) return 0.0 to 0.0

        val candidates = mutableListOf<Pair<Double, Double>>()
        candidates += 0.0 to 0.0 // احتياطي دايمًا، حتى لو القطع التانية مش هتسمح بيه

        for (placed in placedPolys) {
            if (!isConvex(placed)) continue // NFP المحدّب بس مطبّق هنا، لو القطعة المرصوصة مقعّرة بنتجاهلها من حساب NFP (هيتغطى بفحص fits لاحقًا برضه)
            val nfp = noFitPolygonConvex(placed, candidateLocal)
            candidates += nfp.map { it.x to it.y }
        }

        var best: Pair<Double, Double>? = null
        for ((cx, cy) in candidates) {
            if (cx < -1e-6 || cy < -1e-6 || cx > maxX + 1e-6 || cy > maxY + 1e-6) continue
            val x = cx.coerceIn(0.0, maxX)
            val y = cy.coerceIn(0.0, maxY)
            val candidatePoly = candidateLocal.map { NestingPoint(it.x + x, it.y + y) }
            if (!fits(candidatePoly, placedPolys, clearance)) continue
            // Bottom-Left: أقل y الأول، وبعدين أقل x
            if (best == null || y < best!!.second - 1e-9 || (abs(y - best!!.second) <= 1e-9 && x < best!!.first)) {
                best = x to y
            }
        }
        return best
    }

    /** هل شكل مرشح (candidate) يقدر يتحط من غير تصادم أو تعدي على مسافة الأمان؟ */
    private fun fits(candidate: List<NestingPoint>, placed: List<List<NestingPoint>>, clearance: Double): Boolean {
        for (other in placed) {
            // فحص سريع بالـ bounding box الأول (أرخص بكتير من فحص الحواف الكامل)
            val a = boundsOf(candidate)
            val b = boundsOf(other)
            if (a.maxX + clearance < b.minX || b.maxX + clearance < a.minX ||
                a.maxY + clearance < b.minY || b.maxY + clearance < a.minY
            ) continue // بعيدين عن بعض خالص، تجاوز الفحص الدقيق

            if (polygonsOverlap(candidate, other)) return false
            if (clearance > 0.0 && minDistance(candidate, other) < clearance) return false
        }
        return true
    }

    // ====================== أدوات هندسية (Geometry helpers) ======================

    private data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double) {
        val width get() = maxX - minX
        val height get() = maxY - minY
    }

    private fun boundsOf(p: List<NestingPoint>): Bounds {
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (pt in p) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    private fun rotate(points: List<NestingPoint>, deg: Double): List<NestingPoint> {
        if (deg == 0.0) return points
        val r = Math.toRadians(deg)
        val c = cos(r); val s = sin(r)
        return points.map { NestingPoint(it.x * c - it.y * s, it.x * s + it.y * c) }
    }

    private fun transform(points: List<NestingPoint>, deg: Double, dx: Double, dy: Double): List<NestingPoint> {
        val rotated = rotate(points, deg)
        val b = boundsOf(rotated)
        // القطع بتتخزن بحيث offsetX/Y هو ركن الصندوق المحيط بعد التطبيع (زي findPlacement)
        return rotated.map { NestingPoint(it.x - b.minX + dx, it.y - b.minY + dy) }
    }

    private fun rotationCandidates(config: NestingConfig): List<Double> {
        val base = when (config.rotationMode) {
            RotationMode.HORIZONTAL -> listOf(0.0, 180.0)
            RotationMode.VERTICAL -> listOf(90.0, 270.0)
            RotationMode.FREE -> {
                val stepDeg = config.rotationStepDeg.coerceAtLeast(1.0)
                var d = 0.0
                val list = mutableListOf<Double>()
                while (d < 360.0) { list += d; d += stepDeg }
                list
            }
        }
        return base
    }

    /** تقاطع بوليجونين حقيقي: بيفحص تقاطع كل ضلع مع كل ضلع، وكمان احتواء بالكامل (شكل جوه التاني) */
    private fun polygonsOverlap(a: List<NestingPoint>, b: List<NestingPoint>): Boolean {
        for (i in a.indices) {
            val a1 = a[i]; val a2 = a[(i + 1) % a.size]
            for (j in b.indices) {
                val b1 = b[j]; val b2 = b[(j + 1) % b.size]
                if (segmentsIntersect(a1, a2, b1, b2)) return true
            }
        }
        // مفيش تقاطع أضلاع، لكن ممكن شكل يكون بالكامل جوه التاني (زي فتحة كبيرة محتواة)
        if (a.isNotEmpty() && NestingShapeBuilder.pointInPolygon(a[0], b)) return true
        if (b.isNotEmpty() && NestingShapeBuilder.pointInPolygon(b[0], a)) return true
        return false
    }

    private fun segmentsIntersect(p1: NestingPoint, p2: NestingPoint, p3: NestingPoint, p4: NestingPoint): Boolean {
        fun cross(o: NestingPoint, a: NestingPoint, b: NestingPoint) =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val d1 = cross(p3, p4, p1)
        val d2 = cross(p3, p4, p2)
        val d3 = cross(p1, p2, p3)
        val d4 = cross(p1, p2, p4)
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        ) return true
        // حالات التماس/التوازي على نفس الخط (نادرة لكن بنغطيها للأمان)
        if (d1 == 0.0 && onSegment(p3, p4, p1)) return true
        if (d2 == 0.0 && onSegment(p3, p4, p2)) return true
        if (d3 == 0.0 && onSegment(p1, p2, p3)) return true
        if (d4 == 0.0 && onSegment(p1, p2, p4)) return true
        return false
    }

    private fun onSegment(a: NestingPoint, b: NestingPoint, p: NestingPoint): Boolean {
        return min(a.x, b.x) <= p.x && p.x <= max(a.x, b.x) &&
            min(a.y, b.y) <= p.y && p.y <= max(a.y, b.y)
    }

    /** أقل مسافة حقيقية بين حواف بوليجونين (لاستخدامها في فحص مسافة الأمان clearance) */
    private fun minDistance(a: List<NestingPoint>, b: List<NestingPoint>): Double {
        var best = Double.MAX_VALUE
        for (i in a.indices) {
            val a1 = a[i]; val a2 = a[(i + 1) % a.size]
            for (j in b.indices) {
                val b1 = b[j]; val b2 = b[(j + 1) % b.size]
                best = min(best, segmentDistance(a1, a2, b1, b2))
                if (best <= 0.0) return 0.0
            }
        }
        return best
    }

    private fun segmentDistance(p1: NestingPoint, p2: NestingPoint, p3: NestingPoint, p4: NestingPoint): Double {
        return minOf(
            pointToSegmentDistance(p1, p3, p4),
            pointToSegmentDistance(p2, p3, p4),
            pointToSegmentDistance(p3, p1, p2),
            pointToSegmentDistance(p4, p1, p2)
        )
    }

    private fun pointToSegmentDistance(p: NestingPoint, a: NestingPoint, b: NestingPoint): Double {
        val dx = b.x - a.x; val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-9) return NestingShapeBuilder.distance(p, a)
        var t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
        t = t.coerceIn(0.0, 1.0)
        val proj = NestingPoint(a.x + t * dx, a.y + t * dy)
        return NestingShapeBuilder.distance(p, proj)
    }

    // ====================== No-Fit-Polygon عبر Minkowski Sum (للأشكال المحدّبة) ======================
    // نفس الفكرة الأساسية اللي Deepnest/SVGnest بيستخدموها (orbital NFP)، لكن بنسختنا
    // هنا مطبّقة فقط على الأشكال المحدّبة (convex) عشان نضمن نتيجة صحيحة 100% بخوارزمية
    // بسيطة وسريعة (O(n+m))؛ للأشكال المقعّرة النظام بيرجع تلقائيًا للبحث الشبكي فوق.

    /** هل البوليجون محدّب؟ (كل الزوايا بتلف في نفس الاتجاه، مسموح بنقط على استقامة واحدة) */
    private fun isConvex(p: List<NestingPoint>): Boolean {
        if (p.size < 3) return false
        var sign = 0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]; val c = p[(i + 2) % p.size]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-9) continue
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s
            else if (s != sign) return false
        }
        return sign != 0
    }

    private fun ensureCcw(p: List<NestingPoint>): List<NestingPoint> =
        if (NestingShapeBuilder.signedArea(p) < 0.0) p.asReversed() else p

    /** بيعكس الشكل حوالين نقطة الأصل (0,0) — جزء أساسي من معادلة NFP = A ⊕ (-B) */
    private fun reflectPolygon(p: List<NestingPoint>): List<NestingPoint> =
        p.map { NestingPoint(-it.x, -it.y) }.asReversed() // العكس بيقلب اتجاه اللف، فبنرجّعه لـ CCW

    /** بيرجّع فهرس أدنى نقطة (أقل y، وعند التساوي أقل x) — نقطة البداية القياسية لدمج الأضلاع */
    private fun lowestIndex(p: List<NestingPoint>): Int {
        var idx = 0
        for (i in 1 until p.size) {
            if (p[i].y < p[idx].y - 1e-12 || (abs(p[i].y - p[idx].y) <= 1e-12 && p[i].x < p[idx].x)) idx = i
        }
        return idx
    }

    private fun edgeAngle(a: NestingPoint, b: NestingPoint): Double {
        var ang = atan2(b.y - a.y, b.x - a.x)
        if (ang < 0) ang += 2.0 * PI
        return ang
    }

    /**
     * مجموع Minkowski لمضلعين محدّبين (CCW) — بيدمج أضلاع الاثنين مرتبة بالزاوية.
     * ده خوارزمية كلاسيكية (O(n+m))، نفس المبدأ اللي Deepnest بيستخدمه (عندهم عن طريق
     * مكتبة boost::polygon بلغة ++C، إحنا بنعملها يدويًا بالكوتلن هنا).
     */
    private fun minkowskiSumConvex(a0: List<NestingPoint>, b0: List<NestingPoint>): List<NestingPoint> {
        val a = ensureCcw(a0)
        val b = ensureCcw(b0)
        if (a.size < 3 || b.size < 3) return emptyList()

        val ai = lowestIndex(a)
        val bi = lowestIndex(b)
        val an = a.size; val bn = b.size
        val aOrdered = List(an) { a[(ai + it) % an] }
        val bOrdered = List(bn) { b[(bi + it) % bn] }

        val result = mutableListOf<NestingPoint>()
        var cur = NestingPoint(aOrdered[0].x + bOrdered[0].x, aOrdered[0].y + bOrdered[0].y)
        result += cur

        var i = 0; var j = 0
        while (i < an || j < bn) {
            val angA = if (i < an) edgeAngle(aOrdered[i], aOrdered[(i + 1) % an]) else Double.MAX_VALUE
            val angB = if (j < bn) edgeAngle(bOrdered[j], bOrdered[(j + 1) % bn]) else Double.MAX_VALUE
            val useA = angA <= angB
            val edge = if (useA) {
                val p1 = aOrdered[i]; val p2 = aOrdered[(i + 1) % an]; i++
                NestingPoint(p2.x - p1.x, p2.y - p1.y)
            } else {
                val p1 = bOrdered[j]; val p2 = bOrdered[(j + 1) % bn]; j++
                NestingPoint(p2.x - p1.x, p2.y - p1.y)
            }
            cur = NestingPoint(cur.x + edge.x, cur.y + edge.y)
            result += cur
        }
        // آخر نقطة المفروض ترجع لنقطة البداية (الشكل مقفول) — بنشيلها لو قريبة جدًا
        if (result.size > 1 && NestingShapeBuilder.distance(result.first(), result.last()) < 1e-6) {
            result.removeAt(result.lastIndex)
        }
        return result
    }

    /**
     * الـ No-Fit-Polygon: كل المواضع الممكنة لنقطة مرجعية القطعة "orbiting" بحيث
     * تلامس القطعة "stationary" من غير ما تتداخل معاها = A ⊕ (-B) (Minkowski sum
     * للـ stationary مع انعكاس الـ orbiting).
     */
    private fun noFitPolygonConvex(stationary: List<NestingPoint>, orbiting: List<NestingPoint>): List<NestingPoint> {
        if (!isConvex(stationary) || !isConvex(orbiting)) return emptyList()
        return minkowskiSumConvex(ensureCcw(stationary), reflectPolygon(ensureCcw(orbiting)))
    }
}
