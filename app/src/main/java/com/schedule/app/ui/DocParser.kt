package com.schedule.app.ui

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

// ══════════════════════════════════════════════════════════════════════════════
//  DocParser  —  OLE2 / .doc  парсер расписания
//  Точный порт с JS: ole2Text → getCells → detectGroups / parseDoc / parseDocForTeacher
// ══════════════════════════════════════════════════════════════════════════════

object DocParser {

    // ── OLE2 бинарный парсер ─────────────────────────────────────────────────

    private fun u32(buf: ByteArray, off: Int): Long {
        val b = ByteBuffer.wrap(buf, off, 4).order(ByteOrder.LITTLE_ENDIAN)
        return b.int.toLong() and 0xFFFFFFFFL
    }

    private fun u16(buf: ByteArray, off: Int): Int {
        val b = ByteBuffer.wrap(buf, off, 2).order(ByteOrder.LITTLE_ENDIAN)
        return b.short.toInt() and 0xFFFF
    }

    /** Извлекает текст из OLE2 (.doc) файла — точный порт ole2Text() из JS */
    fun ole2Text(data: ByteArray): String {
        val sig = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
                              0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte())
        if (data.size < 512) return ""
        for (i in 0..7) if (data[i] != sig[i]) return ""

        val ss = 1 shl u16(data, 30)

        // DIFAT (FAT sector locations)
        val difat = mutableListOf<Long>()
        val difatCount = u32(data, 44).toInt()
        for (i in 0 until minOf(109, difatCount)) {
            val v = u32(data, 76 + i * 4)
            if (v >= 0xFFFFFFFCL) break
            difat.add(v)
        }

        // FAT
        val fat = mutableListOf<Long>()
        for (sn in difat) {
            val off = 512 + sn.toInt() * ss
            var i = 0
            while (i * 4 + 4 <= ss) {
                val p = off + i * 4
                if (p + 4 <= data.size) fat.add(u32(data, p))
                i++
            }
        }

        fun readSectors(startSec: Long, maxSz: Int = 0): ByteArray {
            val chunks = mutableListOf<ByteArray>()
            var total = 0
            var sec = startSec
            val vis = mutableSetOf<Long>()
            while (sec != 0xFFFFFFFEL && sec != 0xFFFFFFFFL && !vis.contains(sec)) {
                vis.add(sec)
                val off = 512 + sec.toInt() * ss
                if (off >= data.size) break
                val len = minOf(ss, data.size - off)
                chunks.add(data.copyOfRange(off, off + len))
                total += len
                if (maxSz > 0 && total >= maxSz) break
                if (sec >= fat.size) break
                sec = fat[sec.toInt()]
            }
            val out = ByteArray(if (maxSz > 0) minOf(total, maxSz) else total)
            var pos = 0
            for (c in chunks) {
                val take = minOf(c.size, out.size - pos)
                System.arraycopy(c, 0, out, pos, take)
                pos += take
                if (pos >= out.size) break
            }
            return out
        }

        // Directory entries
        val dirStart = u32(data, 48)
        val dd = readSectors(dirStart)

        // Find WordDocument stream
        var wordDocStart = 0L
        var wordDocSize = 0
        val entryCount = dd.size / 128
        for (i in 0 until entryCount) {
            val entry = dd.copyOfRange(i * 128, (i + 1) * 128)
            if (entry.size < 128) break
            val nl = (entry[64].toInt() and 0xFF) or ((entry[65].toInt() and 0xFF) shl 8)
            if (nl < 2) continue
            val nameBytes = entry.copyOfRange(0, nl - 2)
            val name = try { String(nameBytes, Charsets.UTF_16LE) } catch (e: Exception) { continue }
            if (name == "WordDocument") {
                val buf = ByteBuffer.wrap(dd, i * 128, 128).order(ByteOrder.LITTLE_ENDIAN)
                wordDocStart = (buf.getInt(116).toLong() and 0xFFFFFFFFL)
                wordDocSize  = buf.getInt(120)
                break
            }
        }
        if (wordDocSize == 0) return ""

        val wsd = readSectors(wordDocStart, wordDocSize)
        if (wsd.size < 32) return ""

        val wsdBuf = ByteBuffer.wrap(wsd).order(ByteOrder.LITTLE_ENDIAN)
        val fc = wsdBuf.getInt(24).toLong() and 0xFFFFFFFFL
        val cc = wsdBuf.getInt(28).toLong() and 0xFFFFFFFFL
        if (fc >= wsd.size) return ""

        val startIdx = fc.toInt()
        val endIdx   = minOf(startIdx + cc.toInt() * 2, wsd.size)
        return try {
            String(wsd.copyOfRange(startIdx, endIdx), Charsets.UTF_16LE)
        } catch (e: Exception) { "" }
    }

    // ── Получение ячеек из текста ─────────────────────────────────────────────

    /** getCells() из JS — разбивает OLE2-текст на ячейки таблицы по \x07 */
    fun getCells(data: ByteArray): List<String> {
        val raw = ole2Text(data)
        if (raw.isEmpty()) return emptyList()
        return raw.split('\u0007').map { cell ->
            val clean = cell
                .replace('\r', '\n')
                .replace(Regex("[^\t\n\\x20-\\x7E\u0401\u0410-\u044F\u0451«»№]"), "")
                .split('\n')
                .map { line ->
                    if (line.trim().isEmpty()) return@map line
                    val readable = line.count { c ->
                        c in '\u0401'..'\u0451' || c in 'A'..'z' ||
                        c in '0'..'9' || c in " .,;:!?-+=%()«»\"'№"
                    }
                    if (line.isNotEmpty() && readable.toFloat() / line.length > 0.35f || line.length < 5)
                        line else ""
                }
                .joinToString("\n")
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
            clean
        }
    }

    // ── Регулярки (как в JS) ──────────────────────────────────────────────────

    // Группа: МПД-2-24, ИС-1-24, ПК-3-23 и т.п.
    private val GRP = Regex("^[А-ЯЁA-Z0-9]{1,5}[\\-]?\\d[\\-]\\d{2}$")
    private val GRP_SEARCH = Regex("\\b([А-ЯЁA-Z0-9]{1,5}[\\-]?\\d[\\-]\\d{2})\\b")

    // Римские цифры I-VI
    private val ROM = Regex("^(I{1,3}V?|VI{0,3}|IV)$")

    // Заголовок блока расписания
    private val HDR = Regex("Расписание занятий", RegexOption.IGNORE_CASE)

    private fun norm(s: String) = s.trim().uppercase().replace(Regex("[\\s\\-.]"), "")

    // ── Определение групп ─────────────────────────────────────────────────────

    fun detectGroups(data: ByteArray): List<String> {
        val seen = linkedMapOf<String, String>()
        for (cell in getCells(data)) {
            val t = cell.trim()
            if (GRP.matches(t)) { val k = norm(t); if (!seen.containsKey(k)) seen[k] = t; continue }
            val singleLine = t.split('\n').first().trim()
            if (GRP.matches(singleLine)) { val k = norm(singleLine); if (!seen.containsKey(k)) seen[k] = singleLine; continue }
            GRP_SEARCH.findAll(t).forEach { m ->
                val k = norm(m.groupValues[1]); if (!seen.containsKey(k)) seen[k] = m.groupValues[1]
            }
        }
        return seen.values.sorted()
    }

    // ── Парсинг расписания для группы ────────────────────────────────────────

    /** parseDoc() из JS. Возвращает список пар [(roman, lesson)] и заголовок */
    fun parseDoc(data: ByteArray, group: String): Pair<List<Pair<String, String>>, String> {
        val cells = getCells(data)
        val tn = norm(group)
        val labels = mutableListOf<Int>()
        cells.forEachIndexed { i, c -> if (HDR.containsMatchIn(c)) labels.add(i) }
        if (labels.isEmpty()) return Pair(emptyList(), "")
        labels.add(cells.size)

        for (bi in 0 until labels.size - 1) {
            var li = labels[bi]
            val nx = labels[bi + 1]
            var gs = li + 1
            while (gs < nx && !GRP.matches(cells[gs])) gs++
            if (gs >= nx) continue
            var ge = gs
            while (ge < nx && GRP.matches(cells[ge])) ge++
            val fi = (gs until ge).firstOrNull { norm(cells[it]) == tn } ?: continue
            val off = fi - li
            val hdr = cells[li].split('\n').firstOrNull { HDR.containsMatchIn(it) }?.trim() ?: ""
            val sched = mutableListOf<Pair<String, String>>()
            val seen = mutableSetOf<String>()
            for (ri in ge until nx) {
                val c = cells[ri]
                if (ROM.matches(c) && !seen.contains(c)) {
                    seen.add(c)
                    val li2 = ri + off
                    sched.add(c to (if (li2 < cells.size) cells[li2].trim() else ""))
                }
            }
            return Pair(sched, hdr)
        }
        return Pair(emptyList(), "")
    }

    // ── Парсинг строки преподавателя ─────────────────────────────────────────

    data class TeacherInfo(val teacher: String, val cabinetNum: String, val korpus: String)

    /** parseTeacherLine() из JS */
    fun parseTeacherLine(line: String): TeacherInfo {
        val ki = Regex("к\\.\\d").find(line)?.range?.first ?: -1
        if (ki < 0) return TeacherInfo(line.trim(), "", "")
        val teacher = line.substring(0, ki).trim()
        val cabPart = line.substring(ki)
        val numM = Regex("к\\.(\\d+)").find(cabPart)
        val korpM = Regex("\\((\\d)\\)").find(cabPart)
        return TeacherInfo(teacher, numM?.groupValues?.getOrNull(1) ?: "", korpM?.groupValues?.getOrNull(1) ?: "")
    }

    /** Пытается разделить предмет и преподавателя если они слиты в одну строку */
    private fun splitSubjectAndTeacher(line: String): Pair<String, String>? {
        val m = Regex("^(.+?)\\s+([А-ЯЁ][а-яё]+(?:\\s+[А-ЯЁ][а-яё]+)?\\s+[А-ЯЁ]\\.[А-ЯЁ]\\.(к\\.\\d+(?:\\(\\d\\))?)?)$").find(line)
        if (m != null && m.groupValues[1].trim().length > 2)
            return m.groupValues[1].trim() to m.groupValues[2].trim()
        return null
    }

    // ── Расписание звонков ────────────────────────────────────────────────────

    // [start1, end1, start2?, end2?] — как в JS BELL_MON/TUE/SAT
    val BELL_MON = mapOf(
        "I"   to listOf("09:00","09:45","09:50","10:35"),
        "II"  to listOf("10:45","11:30","11:35","12:20"),
        "III" to listOf("12:50","13:35","13:40","14:25"),
        "IV"  to listOf("14:35","15:35",null,null),
        "V"   to listOf("15:45","16:45",null,null),
        "VI"  to listOf("16:55","17:55",null,null),
    )
    val BELL_TUE = mapOf(
        "I"   to listOf("08:30","09:15","09:20","10:05"),
        "II"  to listOf("10:15","11:00","11:05","11:50"),
        "III" to listOf("12:20","13:05","13:10","13:55"),
        "IV"  to listOf("14:05","15:05",null,null),
        "V"   to listOf("15:15","16:15",null,null),
        "VI"  to listOf("16:25","17:25",null,null),
    )
    val BELL_SAT = mapOf(
        "I"   to listOf("08:30","09:30",null,null),
        "II"  to listOf("09:40","10:40",null,null),
        "III" to listOf("10:50","11:50",null,null),
        "IV"  to listOf("12:00","13:00",null,null),
        "V"   to listOf("13:10","14:10",null,null),
        "VI"  to listOf("14:20","15:20",null,null),
    )

    private fun toMin(t: String): Int {
        val parts = t.split(':')
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    // ── Построение ScheduleDay из результата parseDoc ─────────────────────────

    fun buildScheduleDays(
        rawSched: List<Pair<String, String>>,
        header: String,
        filename: String,
    ): List<com.schedule.app.ui.screens.ScheduleDay> {

        if (rawSched.isEmpty()) return emptyList()

        val isMon = filename.uppercase().contains("ПОНЕДЕЛЬНИК")
        val isSat = filename.uppercase().contains("СУББОТ")
        val bell  = if (isMon) BELL_MON else if (isSat) BELL_SAT else BELL_TUE

        val cal    = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val dow    = cal.get(Calendar.DAY_OF_WEEK)

        // Определяем «сегодня» — сравниваем дату файла с сегодня
        val dateMatch = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})").find(header)
        val isToday = if (dateMatch != null) {
            val d = dateMatch.groupValues[1].toInt()
            val m = dateMatch.groupValues[2].toInt() - 1
            val y = dateMatch.groupValues[3].toInt()
            d == cal.get(Calendar.DAY_OF_MONTH) &&
            m == cal.get(Calendar.MONTH) &&
            y == cal.get(Calendar.YEAR)
        } else false

        val pairs = rawSched.map { (roman, lesson) ->
            val b = bell[roman]

            var isNow  = false
            var isNext = false
            var remainText: String? = null

            if (isToday && b != null && b[0] != null) {
                val startMin = toMin(b[0]!!)
                val endRaw   = b[3] ?: b[1]
                val endMin   = if (endRaw != null) toMin(endRaw) else startMin + 90
                isNow = nowMin in startMin..endMin
                if (isNow) {
                    val diff = endMin - nowMin
                    remainText = if (diff <= 0) "заканч." else "$diff мин"
                } else {
                    val diff = startMin - nowMin
                    isNext = diff in 1..30
                }
            }

            // Разбираем ячейку урока
            val rawLines = if (lesson.isNotEmpty())
                lesson.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            else emptyList()

            // Пытаемся разделить слитую строку предмет+препод
            val lines = if (rawLines.isNotEmpty()) {
                val split = splitSubjectAndTeacher(rawLines[0])
                if (split != null) listOf(split.first, split.second) + rawLines.drop(1)
                else rawLines
            } else emptyList()

            val isEmpty = lines.isEmpty()
            val subject = if (isEmpty) "Окно" else lines[0]

            // Преподаватель и кабинет — из строк 1+
            var teacher: String? = null
            var cabinet: String? = null
            var korpus: String? = null
            for (dl in lines.drop(1)) {
                val info = parseTeacherLine(dl)
                if (info.teacher.isNotEmpty()) teacher = info.teacher
                if (info.cabinetNum.isNotEmpty()) cabinet = info.cabinetNum
                if (info.korpus.isNotEmpty()) korpus = info.korpus
            }
            if (cabinet != null && korpus != null) cabinet = "к.$cabinet(${korpus})"
            else if (cabinet != null) cabinet = "к.$cabinet"

            val (s1, e1, s2, e2) = b ?: listOf(null, null, null, null)

            com.schedule.app.ui.screens.Pair(
                num         = roman,
                timeStart   = s1 ?: "",
                timeEnd     = e1 ?: "",
                breakStart  = s2,
                breakEnd    = e2,
                subject     = subject,
                teacher     = teacher,
                room        = cabinet,
                isNow       = isNow,
                isNext      = isNext,
                isWindow    = isEmpty,
                remainText  = remainText,
            )
        }

        val cleanDate = formatScheduleDate(header)
        return listOf(com.schedule.app.ui.screens.ScheduleDay(header = cleanDate ?: header, pairs = pairs))
    }

    /** Форматирует дату из заголовка расписания */
    fun formatScheduleDate(hdr: String): String? {
        val m = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})").find(hdr) ?: return null
        return m.value
    }

    // ── Скачивание файла с Яндекс.Диска ─────────────────────────────────────

    private val fileCache = mutableMapOf<String, ByteArray>()

    suspend fun downloadFile(
        publicKey: String,
        filePath: String,
        onProgress: (Float) -> Unit = {},
    ): ByteArray = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cacheKey = "$publicKey|$filePath"
        fileCache[cacheKey]?.let { return@withContext it }

        val enc = java.net.URLEncoder.encode(publicKey, "UTF-8")
        val pathEnc = java.net.URLEncoder.encode(filePath, "UTF-8")
        val dlUrl = "https://cloud-api.yandex.net/v1/disk/public/resources/download" +
                    "?public_key=$enc&path=$pathEnc"

        onProgress(0.2f)
        val conn = java.net.URL(dlUrl).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 15_000; conn.readTimeout = 30_000
        val dlJson = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
        val href = dlJson.optString("href")
        if (href.isNullOrEmpty()) throw Exception("Яндекс не вернул ссылку: $filePath")

        onProgress(0.5f)
        val fc = java.net.URL(href).openConnection() as java.net.HttpURLConnection
        fc.connectTimeout = 15_000; fc.readTimeout = 60_000
        val bytes = fc.inputStream.readBytes()
        onProgress(1.0f)
        fileCache[cacheKey] = bytes
        bytes
    }

    fun clearCache() = fileCache.clear()
}
