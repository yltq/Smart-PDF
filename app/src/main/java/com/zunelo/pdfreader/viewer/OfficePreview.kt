package com.zunelo.pdfreader.viewer

import com.zunelo.pdfreader.R
import com.zunelo.pdfreader.application.*
import com.zunelo.pdfreader.common.*
import com.zunelo.pdfreader.data.*
import com.zunelo.pdfreader.dialog.*
import com.zunelo.pdfreader.home.*
import com.zunelo.pdfreader.onboarding.*
import com.zunelo.pdfreader.pdf.*
import com.zunelo.pdfreader.permission.*
import com.zunelo.pdfreader.startup.*
import com.zunelo.pdfreader.tools.*
import com.zunelo.pdfreader.viewer.*

import android.content.Context
import java.io.File
import java.util.zip.ZipFile

object OfficePreview {
    fun html(context: Context, item: DocumentItem): String {
        val file = DocumentStore.cacheFile(context, item)
        return when (item.name.substringAfterLast('.', "").lowercase()) {
            "docx" -> wordHtml(file, item.name)
            "xlsx" -> excelHtml(file, item.name)
            "pptx" -> slidesHtml(file, item.name)
            "csv" -> csvHtml(file, item.name)
            "txt", "rtf" -> wordShell(item.name, "<p>${escape(file.readText()).replace("\n", "</p><p>")}</p>")
            else -> error("Unsupported Office format")
        }
    }

    private fun wordHtml(file: File, name: String): String {
        val xml = zipEntry(file, "word/document.xml")
        val paragraphs = Regex("<w:p(?:\\s[^>]*)?>(.*?)</w:p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(xml).mapNotNull { match ->
            val text = Regex("<w:t(?:\\s[^>]*)?>(.*?)</w:t>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(match.groupValues[1]).joinToString("") { decodeXml(it.groupValues[1]) }.trim()
            text.takeIf(String::isNotBlank)?.let { "<p>${escape(it)}</p>" }
        }.joinToString("")
        if (paragraphs.isBlank()) error("No Word content")
        return wordShell(name, paragraphs)
    }

    private fun excelHtml(file: File, name: String): String {
        ZipFile(file).use { zip ->
            val shared = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("<si(?:\\s[^>]*)?>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { block ->
                    Regex("<t(?:\\s[^>]*)?>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(block.groupValues[1]).joinToString("") { decodeXml(it.groupValues[1]) }
                }.toList()
            }.orEmpty()
            val sheetEntry = zip.entries().asSequence().filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }.sortedBy { sheetNumber(it.name) }.firstOrNull() ?: error("No worksheet")
            val xml = zip.getInputStream(sheetEntry).bufferedReader().use { it.readText() }
            val rows = linkedMapOf<Int, MutableMap<Int, String>>()
            var maxColumn = 0
            Regex("<c([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { cell ->
                val attributes = cell.groupValues[1]
                val reference = Regex("r=\"([A-Z]+)(\\d+)\"").find(attributes) ?: return@forEach
                val column = columnIndex(reference.groupValues[1]); val row = reference.groupValues[2].toIntOrNull() ?: return@forEach
                val raw = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(cell.groupValues[2])?.groupValues?.get(1).orEmpty()
                val value = if (attributes.contains("t=\"s\"")) shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else decodeXml(raw)
                rows.getOrPut(row) { linkedMapOf() }[column] = value; maxColumn = maxOf(maxColumn, column)
            }
            if (rows.isEmpty()) error("No worksheet content")
            val header = (0..maxColumn).joinToString("") { "<th>${columnName(it)}</th>" }
            val body = rows.entries.joinToString("") { (rowNumber, cells) -> "<tr><th class='row'>$rowNumber</th>${(0..maxColumn).joinToString("") { "<td>${escape(cells[it].orEmpty())}</td>" }}</tr>" }
            return shell("#20ad54", name, "<div class='sheet-wrap'><table><thead><tr><th class='corner'></th>$header</tr></thead><tbody>$body</tbody></table></div>")
        }
    }

    private fun csvHtml(file: File, name: String): String {
        val rows = file.readLines().map { parseCsvRow(it) }
        val max = rows.maxOfOrNull { it.size } ?: 0
        val header = (0 until max).joinToString("") { "<th>${columnName(it)}</th>" }
        val body = rows.mapIndexed { index, row -> "<tr><th class='row'>${index + 1}</th>${(0 until max).joinToString("") { "<td>${escape(row.getOrElse(it) { "" })}</td>" }}</tr>" }.joinToString("")
        return shell("#20ad54", name, "<div class='sheet-wrap'><table><thead><tr><th class='corner'></th>$header</tr></thead><tbody>$body</tbody></table></div>")
    }

    private fun slidesHtml(file: File, name: String): String {
        val slides = mutableListOf<List<String>>()
        ZipFile(file).use { zip -> zip.entries().asSequence().filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }.sortedBy { sheetNumber(it.name) }.forEach { entry ->
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            slides += Regex("<a:t(?:\\s[^>]*)?>(.*?)</a:t>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { decodeXml(it.groupValues[1]) }.filter(String::isNotBlank).toList()
        } }
        if (slides.isEmpty()) error("No slide content")
        val body = slides.mapIndexed { index, texts ->
            val title = texts.firstOrNull().orEmpty(); val content = texts.drop(1).joinToString("<br>") { escape(it) }
            "<section class='slide'><span class='slide-number'>${index + 1}</span><h2>${escape(title)}</h2><div class='slide-body'>$content</div></section>"
        }.joinToString("")
        return shell("#ff8a2b", name, body)
    }

    private fun wordShell(name: String, body: String) = shell("#3478f6", name, "<article class='word-page'><h1>${escape(name)}</h1>$body</article>")

    private fun shell(accent: String, name: String, body: String) = """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>
        *{box-sizing:border-box}body{margin:0;padding:12px;background:#fdfdfd;font-family:sans-serif;color:#171a22}.top{height:5px;background:$accent;border-radius:4px;margin-bottom:12px}.file-label{font-size:12px;color:#747c8b;margin:0 4px 10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
        .word-page{background:#fff;min-height:620px;padding:34px 30px;margin:auto;max-width:720px;box-shadow:0 2px 12px #00000014;line-height:1.72}.word-page h1{font-size:22px;margin:0 0 28px}.word-page p{margin:0 0 14px}
        .sheet-wrap{overflow:auto;background:#fff;border:1px solid #d9dde4;border-radius:8px}table{border-collapse:collapse;min-width:100%;font-size:13px}th,td{min-width:96px;height:38px;padding:7px 9px;border:1px solid #dfe3e8;text-align:left;white-space:nowrap}thead th,.row{background:#f1f4f6;color:#5d6572;font-weight:600;min-width:42px;position:sticky}.corner{min-width:42px}.row{left:0;text-align:center}
        .slide{position:relative;min-height:210px;background:#fff;margin:0 0 14px;padding:28px 30px 36px;border-radius:8px;box-shadow:0 2px 10px #00000015;overflow:visible;word-break:break-word}.slide:before{content:'';position:absolute;left:0;top:0;bottom:0;width:6px;background:$accent}.slide h2{font-size:21px;line-height:1.35;margin:0 0 18px}.slide-body{font-size:14px;line-height:1.6;white-space:normal}.slide-number{position:absolute;right:12px;bottom:9px;color:#8b919c;font-size:11px}
    </style></head><body><div class="top"></div><div class="file-label">${escape(name)}</div>$body</body></html>"""

    private fun zipEntry(file: File, path: String): String = ZipFile(file).use { zip -> val entry = zip.getEntry(path) ?: error("Missing $path"); zip.getInputStream(entry).bufferedReader().use { it.readText() } }
    private fun sheetNumber(path: String) = Regex("(\\d+)").find(path.substringAfterLast('/'))?.groupValues?.get(1)?.toIntOrNull() ?: 0
    private fun columnIndex(value: String): Int = value.fold(0) { total, char -> total * 26 + (char - 'A' + 1) } - 1
    private fun columnName(index: Int): String { var value = index + 1; val result = StringBuilder(); while (value > 0) { value--; result.append(('A'.code + value % 26).toChar()); value /= 26 }; return result.reverse().toString() }
    private fun parseCsvRow(line: String): List<String> { val values = mutableListOf<String>(); val current = StringBuilder(); var quoted = false; var index = 0; while (index < line.length) { val char = line[index]; when { char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> { current.append('"'); index++ }; char == '"' -> quoted = !quoted; char == ',' && !quoted -> { values += current.toString(); current.clear() }; else -> current.append(char) }; index++ }; values += current.toString(); return values }
    private fun decodeXml(value: String) = value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    private fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
