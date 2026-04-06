package com.example.clawpaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** 单条气泡内：纯文本段或 Markdown 表格（GFM：表头 + 分隔行 + 数据行）。 */
sealed class ChatContentSegment {
    data class Plain(val text: String) : ChatContentSegment()
    data class Table(val rows: List<List<String>>) : ChatContentSegment()
}

private val separatorCellRegex = Regex("^:?-{3,}:?$")

internal fun parseTableRow(line: String): List<String> {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return emptyList()
    val core = when {
        trimmed.startsWith('|') && trimmed.endsWith('|') -> trimmed.substring(1, trimmed.length - 1)
        trimmed.startsWith('|') -> trimmed.substring(1)
        trimmed.endsWith('|') -> trimmed.substring(0, trimmed.length - 1)
        else -> trimmed
    }
    if (core.isBlank()) return emptyList()
    return core.split('|').map { it.trim() }
}

internal fun isMarkdownTableSeparatorRow(line: String): Boolean {
    val cells = parseTableRow(line)
    if (cells.isEmpty()) return false
    return cells.all { it.matches(separatorCellRegex) }
}

/**
 * 从 [lines] 的 [startIndex] 起解析 GFM 表格；返回 (表体行列表含表头, 下一行下标)。
 * 表头与分隔行不进入 rows；数据行不足时仍返回仅表头的一行表格。
 */
internal fun extractMarkdownTable(lines: List<String>, startIndex: Int): Pair<List<List<String>>, Int>? {
    if (startIndex + 1 >= lines.size) return null
    val headerLine = lines[startIndex]
    val sepLine = lines[startIndex + 1]
    if (!headerLine.contains('|') || !isMarkdownTableSeparatorRow(sepLine)) return null
    val header = parseTableRow(headerLine)
    if (header.isEmpty()) return null
    var j = startIndex + 2
    val body = mutableListOf<List<String>>()
    while (j < lines.size) {
        val line = lines[j]
        if (line.isBlank()) break
        if (!line.contains('|')) break
        body.add(parseTableRow(line))
        j++
    }
    val colCount = header.size
    fun padRow(r: List<String>) = List(colCount) { idx -> r.getOrNull(idx) ?: "" }
    val rows = listOf(padRow(header)) + body.map { padRow(it) }
    return Pair(rows, j)
}

fun splitChatContentWithTables(raw: String): List<ChatContentSegment> {
    if (raw.isEmpty()) return emptyList()
    val lines = raw.split('\n').map { it.trimEnd('\r') }
    val out = mutableListOf<ChatContentSegment>()
    val textBuf = StringBuilder()
    var i = 0
    fun flushText() {
        if (textBuf.isNotEmpty()) {
            out.add(ChatContentSegment.Plain(textBuf.toString()))
            textBuf.clear()
        }
    }
    while (i < lines.size) {
        val table = extractMarkdownTable(lines, i)
        if (table != null) {
            flushText()
            out.add(ChatContentSegment.Table(table.first))
            i = table.second
            continue
        }
        if (textBuf.isNotEmpty()) textBuf.append('\n')
        textBuf.append(lines[i])
        i++
    }
    flushText()
    return out
}

@Composable
fun ChatMessageRichContent(
    text: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val segments = remember(text) { splitChatContentWithTables(text) }
    val color = MaterialTheme.colorScheme.onSurface
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val border = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    Column(modifier = modifier) {
        segments.forEach { seg ->
            when (seg) {
                is ChatContentSegment.Plain ->
                    if (seg.text.isNotEmpty()) {
                        Text(
                            text = seg.text,
                            style = textStyle,
                            color = color,
                        )
                    }
                is ChatContentSegment.Table ->
                    ChatMarkdownTable(
                        rows = seg.rows,
                        textStyle = textStyle,
                        textColor = color,
                        headerBackground = headerBg,
                        borderColor = border,
                    )
            }
        }
    }
}

@Composable
private fun ChatMarkdownTable(
    rows: List<List<String>>,
    textStyle: TextStyle,
    textColor: Color,
    headerBackground: Color,
    borderColor: Color,
) {
    if (rows.isEmpty()) return
    val colCount = rows.maxOf { it.size }
    val padded = remember(rows, colCount) {
        rows.map { r -> List(colCount) { idx -> r.getOrNull(idx) ?: "" } }
    }
    val density = LocalDensity.current
    val fontPx = with(density) { textStyle.fontSize.toPx() }
    val colWidthsDp = remember(padded, fontPx, density) {
        (0 until colCount).map { ci ->
            val maxChars = padded.maxOf { row -> row[ci].length }
            with(density) {
                (maxChars.coerceAtLeast(1) * fontPx * 0.52f).toDp().coerceIn(44.dp, 240.dp)
            }
        }
    }
    val corner = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .padding(top = 4.dp, bottom = 4.dp)
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor, corner)
    ) {
        padded.forEachIndexed { rowIndex, row ->
            if (rowIndex == 1) {
                HorizontalDivider(thickness = 1.dp, color = borderColor)
            }
            Row(
                modifier = Modifier.background(
                    if (rowIndex == 0) headerBackground else Color.Transparent
                )
            ) {
                row.forEachIndexed { colIndex, cell ->
                    Text(
                        text = cell,
                        style = textStyle,
                        color = textColor,
                        modifier = Modifier
                            .width(colWidthsDp[colIndex])
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
