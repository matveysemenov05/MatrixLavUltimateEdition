package com.example.matrixcalculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

var appIsLightTheme by mutableStateOf(false)

val BgDark: Color get() = if (appIsLightTheme) Color(0xFFF4F6FB) else Color(0xFF080D18)
val CardDark: Color get() = if (appIsLightTheme) Color(0xFFFFFFFF) else Color(0xFF111A2B)
val CardElevated: Color get() = if (appIsLightTheme) Color(0xFFF8FAFC) else Color(0xFF17233A)
val AccentCyan: Color get() = if (appIsLightTheme) Color(0xFF0284C7) else Color(0xFF54E5E5)
val AccentPurple: Color get() = if (appIsLightTheme) Color(0xFF6D28D9) else Color(0xFFB69AFF)
val AccentAmber: Color get() = if (appIsLightTheme) Color(0xFFB45309) else Color(0xFFFFD166)
val AccentEmerald: Color get() = if (appIsLightTheme) Color(0xFF047857) else Color(0xFF70E1A1)
val TextWhite: Color get() = if (appIsLightTheme) Color(0xFF1E293B) else Color(0xFFEDF4FF)
val TextDim: Color get() = if (appIsLightTheme) Color(0xFF64748B) else Color(0xFF9BABC5)



data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val resultText: String,
    val resultMatrix: Array<DoubleArray>?,
    val time: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
    val pinned: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HistoryItem
        if (id != other.id) return false
        if (pinned != other.pinned) return false
        if (title != other.title) return false
        if (resultText != other.resultText) return false
        if (resultMatrix != null) {
            if (other.resultMatrix == null) return false
            if (!resultMatrix.contentDeepEquals(other.resultMatrix)) return false
        } else if (other.resultMatrix != null) return false
        return time == other.time
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + pinned.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + resultText.hashCode()
        result = 31 * result + (resultMatrix?.contentDeepHashCode() ?: 0)
        result = 31 * result + time.hashCode()
        return result
    }
}

object HistoryStorage {
    private const val PREF_NAME = "matrix_history_prefs"
    private const val KEY_HISTORY = "saved_history"
    private const val MAX_HISTORY_ITEMS = 200

    private fun sanitizeField(s: String): String =
        s.replace("@@@", "@_@_@").replace("###ITEM###", "#_#ITEM#_#")

    fun saveHistory(context: Context, list: List<HistoryItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val serialized = list.take(MAX_HISTORY_ITEMS).joinToString("###ITEM###") { item ->
            val matStr = item.resultMatrix?.let { matrixToString(it) } ?: ""
            "${item.id}@@@${sanitizeField(item.title)}@@@${sanitizeField(item.resultText)}@@@${item.time}@@@$matStr@@@${item.pinned}"
        }
        prefs.edit().putString(KEY_HISTORY, serialized).apply()
    }

    fun loadHistory(context: Context): List<HistoryItem> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return try {
            raw.split("###ITEM###").mapNotNull { entry ->
                val p = entry.split("@@@")
                if (p.size >= 5) {
                    val mat = if (p[4].isNotBlank()) stringToMatrix(p[4]) else null
                    val isPinned = p.getOrNull(5)?.toBooleanStrictOrNull() ?: false
                    HistoryItem(
                        id = p[0].toLongOrNull() ?: 0L,
                        title = p[1],
                        resultText = p[2],
                        time = p[3],
                        resultMatrix = mat,
                        pinned = isPinned
                    )
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun matrixToString(mat: Array<DoubleArray>): String {
        return mat.joinToString(";") { row -> row.joinToString(",") }
    }

    private fun stringToMatrix(str: String): Array<DoubleArray>? {
        return try {
            val rows = str.split(";")
            Array(rows.size) { r ->
                val cols = rows[r].split(",")
                DoubleArray(cols.size) { c -> cols[c].toDouble() }
            }
        } catch (_: Exception) {
            null
        }
    }
}

object MatrixStateStorage {
    private const val PREF = "matrix_saved_cells_prefs"

    fun save(
        context: Context,
        mainTab: Int,
        rA: Int, cA: Int, rB: Int, cB: Int, rC: Int, cC: Int,
        tauN: Int, tauM: Int, tauK: Int,
        mA: List<List<String>>, mB: List<List<String>>, mC: List<List<String>>
    ) {
        fun enc(g: List<List<String>>) = g.joinToString(";") { it.joinToString(",") }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("mainTab", mainTab)
            .putInt("rA", rA).putInt("cA", cA)
            .putInt("rB", rB).putInt("cB", cB)
            .putInt("rC", rC).putInt("cC", cC)
            .putInt("tauN", tauN).putInt("tauM", tauM).putInt("tauK", tauK)
            .putString("mA", enc(mA))
            .putString("mB", enc(mB))
            .putString("mC", enc(mC))
            .apply()
    }

    fun loadGrid(context: Context, key: String): List<List<String>>? {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(key, null) ?: return null
        if (raw.isBlank()) return null
        return raw.split(";").map { it.split(",") }
    }
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MainAppSwitcher() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppSwitcher() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val math = remember { MatrixMath() }
    val screenWidth = configuration.screenWidthDp.dp
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Главный режим: 0 = ⚡ Алгебра (независимые размеры A, B, C), 1 = 🎛 ТАУ, Спектр и f(A) (A: n×n, B: n×m, C: k×n)
    var mainTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedMatrixTab by rememberSaveable { mutableIntStateOf(0) } // 0 = A, 1 = B, 2 = C

    var formulaInput by rememberSaveable { mutableStateOf("2*A^2 - 3*A + 5*I") }
    val historyList = remember { mutableStateListOf<HistoryItem>() }
    LaunchedEffect(Unit) { historyList.addAll(HistoryStorage.loadHistory(context)) }


    var showHistorySheet by remember { mutableStateOf(false) }

    // --- СОСТОЯНИЕ РАЗДЕЛА 0: "АЛГЕБРА" (Каждая матрица A, B, C регулируется отдельно M×N от 1 до 7) ---
    var algRowsA by rememberSaveable { mutableIntStateOf(3) }
    var algColsA by rememberSaveable { mutableIntStateOf(3) }
    var algRowsB by rememberSaveable { mutableIntStateOf(3) }
    var algColsB by rememberSaveable { mutableIntStateOf(3) }
    var algRowsC by rememberSaveable { mutableIntStateOf(3) }
    var algColsC by rememberSaveable { mutableIntStateOf(3) }

    // --- СОСТОЯНИЕ РАЗДЕЛА 1: "ТАУ, СПЕКТР И f(A)" (A: n×n, B: n×m, C: k×n) ---
    var tauN by rememberSaveable { mutableIntStateOf(3) } // Размерность состояния n (2..7)
    var tauM by rememberSaveable { mutableIntStateOf(1) } // Число столбцов (входов) B: m (1..7)
    var tauK by rememberSaveable { mutableIntStateOf(1) } // Число строк (выходов) C: k (1..7)

    // Единые хранилища данных матриц A, B, C (при переключении вкладок данные сохраняются и адаптируются по размеру)
    var matrixA by rememberSaveable { mutableStateOf(List(3) { List(3) { "0" } }) }
    var matrixB by rememberSaveable { mutableStateOf(List(3) { List(3) { "0" } }) }
    var matrixC by rememberSaveable { mutableStateOf(List(3) { List(3) { "0" } }) }
    var scalarK by rememberSaveable { mutableStateOf("1") }
    var showAllThreeMatrices by rememberSaveable { mutableStateOf(false) }
    val undoSnapshots = remember { mutableStateListOf< Pair<IntArray, Triple<List<List<String>>, List<List<String>>, List<List<String>>>> >() }
    val redoSnapshots = remember { mutableStateListOf< Pair<IntArray, Triple<List<List<String>>, List<List<String>>, List<List<String>>>> >() }

    fun captureState() = Pair(
        intArrayOf(algRowsA, algColsA, algRowsB, algColsB, algRowsC, algColsC, tauN, tauM, tauK),
        Triple(matrixA, matrixB, matrixC)
    )

    fun restoreState(snap: Pair<IntArray, Triple<List<List<String>>, List<List<String>>, List<List<String>>>>) {
        val d = snap.first
        algRowsA = d[0]; algColsA = d[1]
        algRowsB = d[2]; algColsB = d[3]
        algRowsC = d[4]; algColsC = d[5]
        tauN = d[6]; tauM = d[7]; tauK = d[8]
        matrixA = snap.second.first
        matrixB = snap.second.second
        matrixC = snap.second.third
    }

    fun pushUndo() {
        undoSnapshots.add(captureState())
        if (undoSnapshots.size > 30) undoSnapshots.removeAt(0)
        redoSnapshots.clear()
    }

    fun popUndo() {
        if (undoSnapshots.isEmpty()) {
            Toast.makeText(context, "Нет действий для отмены", Toast.LENGTH_SHORT).show()
            return
        }
        redoSnapshots.add(captureState())
        restoreState(undoSnapshots.removeAt(undoSnapshots.lastIndex))
        Toast.makeText(context, "↩️ Отменено (шаг назад)", Toast.LENGTH_SHORT).show()
    }

    fun popRedo() {
        if (redoSnapshots.isEmpty()) {
            Toast.makeText(context, "Нет действий для возврата вперёд", Toast.LENGTH_SHORT).show()
            return
        }
        undoSnapshots.add(captureState())
        restoreState(redoSnapshots.removeAt(redoSnapshots.lastIndex))
        Toast.makeText(context, "↪️ Возвращено (шаг вперёд)", Toast.LENGTH_SHORT).show()
    }

    var isInitialStateLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("matrix_saved_cells_prefs", Context.MODE_PRIVATE)
        if (prefs.contains("mA")) {
            mainTab = prefs.getInt("mainTab", 0)
            algRowsA = prefs.getInt("rA", 3); algColsA = prefs.getInt("cA", 3)
            algRowsB = prefs.getInt("rB", 3); algColsB = prefs.getInt("cB", 3)
            algRowsC = prefs.getInt("rC", 3); algColsC = prefs.getInt("cC", 3)
            tauN = prefs.getInt("tauN", 3); tauM = prefs.getInt("tauM", 1); tauK = prefs.getInt("tauK", 1)
            MatrixStateStorage.loadGrid(context, "mA")?.let { matrixA = it }
            MatrixStateStorage.loadGrid(context, "mB")?.let { matrixB = it }
            MatrixStateStorage.loadGrid(context, "mC")?.let { matrixC = it }
        }
        isInitialStateLoaded = true
    }

    LaunchedEffect(isInitialStateLoaded, mainTab, algRowsA, algColsA, algRowsB, algColsB, algRowsC, algColsC, tauN, tauM, tauK, matrixA, matrixB, matrixC) {
        if (!isInitialStateLoaded) return@LaunchedEffect
        kotlinx.coroutines.delay(350)
        MatrixStateStorage.save(context, mainTab, algRowsA, algColsA, algRowsB, algColsB, algRowsC, algColsC, tauN, tauM, tauK, matrixA, matrixB, matrixC)
    }

    // Актуальные размеры для текущего выбранного режима
    val curRowsA = if (mainTab == 0) algRowsA else tauN
    val curColsA = if (mainTab == 0) algColsA else tauN
    val curRowsB = if (mainTab == 0) algRowsB else tauN
    val curColsB = if (mainTab == 0) algColsB else tauM
    val curRowsC = if (mainTab == 0) algRowsC else tauK
    val curColsC = if (mainTab == 0) algColsC else tauN

    // Автоматическая подгонка списков при изменении размеров
    val viewA = remember(matrixA, curRowsA, curColsA) { resizeMatrix(matrixA, curRowsA, curColsA) }
    val viewB = remember(matrixB, curRowsB, curColsB) { resizeMatrix(matrixB, curRowsB, curColsB) }
    val viewC = remember(matrixC, curRowsC, curColsC) { resizeMatrix(matrixC, curRowsC, curColsC) }
    fun buildGivenHeaderForTitle(opTitle: String): String {
        val mA = runCatching { parse(viewA) }.getOrNull() ?: return ""
        val mB = runCatching { parse(viewB) }.getOrNull()
        val mC = runCatching { parse(viewC) }.getOrNull()
        val hasNZ = { m: Array<DoubleArray>? -> m != null && m.any { r -> r.any { abs(it) > 1e-12 } } }

        var useA = true; var useB = false; var useC = false
        when {
            opTitle.contains("(B)") || opTitle.contains("B⁻¹") || opTitle.contains("Bᵀ") || opTitle.endsWith("× B") -> { useA = false; useB = true }
            opTitle.contains("(C)") || opTitle.contains("C⁻¹") || opTitle.contains("Cᵀ") || opTitle.endsWith("× C") -> { useA = false; useC = true }
            opTitle.contains("A + B + C") || opTitle.contains("A − B − C") || opTitle.contains("A × B × C") -> { useA = true; useB = true; useC = true }
            opTitle.contains("Блок-схема") || opTitle.contains("Калман") || opTitle.contains("W(s)") -> {
                useA = true; useB = hasNZ(mB); useC = hasNZ(mC)
            }
            opTitle.contains("Управляемост") || opTitle.contains("Фробениус") || opTitle.contains("Модальное") ||
                    opTitle.contains("Регулятор") || opTitle.contains("LQR") || opTitle.contains("СЛАУ") ||
                    opTitle.contains("A + B") || opTitle.contains("A − B") || opTitle.contains("A × B") ||
                    opTitle.contains("a · b") || opTitle.contains("a ⨯ b") -> { useA = true; useB = true }
            opTitle.contains("Наблюдаемост") || opTitle.contains("Наблюдатель") -> { useA = true; useC = true }
            opTitle.contains("Формула") -> {
                useA = opTitle.contains("A", ignoreCase = true)
                useB = opTitle.contains("B", ignoreCase = true)
                useC = opTitle.contains("C", ignoreCase = true)
            }
        }

        return buildString {
            appendLine("ИСХОДНЫЕ ДАННЫЕ (ДАНО):")
            if (useA) {
                appendLine("Матрица A (${mA.size}×${mA[0].size}):")
                appendLine(formatMatrixAligned(mA, true))
            }
            if (useB && mB != null) {
                appendLine("Матрица B (${mB.size}×${mB[0].size}):")
                appendLine(formatMatrixAligned(mB, true))
            }
            if (useC && mC != null) {
                appendLine("Матрица C (${mC.size}×${mC[0].size}):")
                appendLine(formatMatrixAligned(mC, true))
            }
            appendLine("════════════════════════════════")
            append("РЕШЕНИЕ ($opTitle):")
        }
    }

    fun addHistoryItem(item: HistoryItem) {
        val givenHdr = buildGivenHeaderForTitle(item.title)
        val body = if (item.resultText.isNotBlank()) item.resultText else item.resultMatrix?.let { formatMatrixAligned(it, true) } ?: ""
        val enrichedText = if (body.contains("ИСХОДНЫЕ ДАННЫЕ (ДАНО):")) body else "$givenHdr\n$body"
        val enriched = item.copy(resultText = enrichedText)
        val firstUnpinnedIdx = historyList.indexOfFirst { !it.pinned }.let { if (it == -1) historyList.size else it }
        historyList.add(firstUnpinnedIdx, enriched)
        while (historyList.size > 200) historyList.removeAt(historyList.lastIndex)
        HistoryStorage.saveHistory(context, historyList)
    }

    fun clearAllHistory() {
        val pinnedOnly = historyList.filter { it.pinned }
        historyList.clear()
        historyList.addAll(pinnedOnly)
        HistoryStorage.saveHistory(context, historyList)
    }

    val activeRows = when (selectedMatrixTab) { 0 -> curRowsA; 1 -> curRowsB; else -> curRowsC }
    val activeCols = when (selectedMatrixTab) { 0 -> curColsA; 1 -> curColsB; else -> curColsC }
    val activeData = when (selectedMatrixTab) { 0 -> viewA; 1 -> viewB; else -> viewC }
    val activeAccent = when (selectedMatrixTab) { 0 -> AccentCyan; 1 -> AccentPurple; else -> AccentAmber }
    val tabName = when (selectedMatrixTab) { 0 -> "A"; 1 -> "B"; else -> "C" }

    fun updateActiveMatrix(updated: List<List<String>>) {
        when (selectedMatrixTab) {
            0 -> matrixA = updated
            1 -> matrixB = updated
            else -> matrixC = updated
        }
    }

    fun setMatrixDimensions(targetTab: Int, newR: Int, newC: Int) {
        val r = newR.coerceIn(1, 7)
        val c = newC.coerceIn(1, 7)
        if (mainTab == 0) {
            when (targetTab) {
                0 -> { algRowsA = r; algColsA = c; matrixA = resizeMatrix(matrixA, r, c) }
                1 -> { algRowsB = r; algColsB = c; matrixB = resizeMatrix(matrixB, r, c) }
                else -> { algRowsC = r; algColsC = c; matrixC = resizeMatrix(matrixC, r, c) }
            }
        } else {
            when (targetTab) {
                0 -> {
                    val n = r.coerceIn(2, 7)
                    tauN = n
                    matrixA = resizeMatrix(matrixA, n, n)
                    matrixB = resizeMatrix(matrixB, n, tauM)
                    matrixC = resizeMatrix(matrixC, tauK, n)
                }
                1 -> {
                    val n = r.coerceIn(2, 7)
                    tauN = n; tauM = c
                    matrixA = resizeMatrix(matrixA, n, n)
                    matrixB = resizeMatrix(matrixB, n, tauM)
                    matrixC = resizeMatrix(matrixC, tauK, n)
                }
                else -> {
                    val n = c.coerceIn(2, 7)
                    tauK = r; tauN = n
                    matrixA = resizeMatrix(matrixA, n, n)
                    matrixB = resizeMatrix(matrixB, n, tauM)
                    matrixC = resizeMatrix(matrixC, tauK, n)
                }
            }
        }
    }

    // Состояние карточки результата
    var lastResultTitle by remember { mutableStateOf("Результат") }
    var lastResultMatrix by remember { mutableStateOf<Array<DoubleArray>?>(null) }
    var lastResultGrid by remember { mutableStateOf<List<List<String>>?>(null) }
    var resultScalarText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun scrollToBottom() {
        coroutineScope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    fun showMatrixResult(result: Array<DoubleArray>?, operationName: String) {
        lastResultTitle = operationName
        lastResultGrid = null

        if (result == null) {
            lastResultMatrix = null
            resultScalarText = "Не удалось выполнить операцию \"$operationName\".\nПроверьте размеры матриц и допустимость операции."
            isError = true
            scrollToBottom()
            return
        }
        if (result.any { row -> row.any { !it.isFinite() } }) {
            lastResultMatrix = null
            resultScalarText = "Ошибка вычисления \"$operationName\".\nРезультат содержит NaN или бесконечность."
            isError = true
            scrollToBottom()
            return
        }

        lastResultMatrix = result
        resultScalarText = ""
        isError = false
        addHistoryItem(HistoryItem(title = operationName, resultText = formatMatrixRaw(result), resultMatrix = result))
        scrollToBottom()
    }

    fun transferResultToTarget(targetIdx: Int, mat: Array<DoubleArray>) {
        if (mat.isEmpty() || mat[0].isEmpty()) return
        val r = mat.size.coerceIn(1, 7)
        val c = mat[0].size.coerceIn(1, 7)
        val strGrid = matrixToPreciseStringList(mat)
        val name = listOf("A", "B", "C")[targetIdx]

        if (mainTab == 0) {
            setMatrixDimensions(targetIdx, r, c)
            when (targetIdx) {
                0 -> matrixA = resizeMatrix(strGrid, r, c)
                1 -> matrixB = resizeMatrix(strGrid, r, c)
                else -> matrixC = resizeMatrix(strGrid, r, c)
            }
            selectedMatrixTab = targetIdx
            Toast.makeText(context, "Перенесено в $name (${r}×$c)", Toast.LENGTH_SHORT).show()
        } else {
            when (targetIdx) {
                0 -> {
                    // В ТАУ матрица A всегда квадратная n×n: берем n по большему измерению (от 2 до 7)
                    val n = maxOf(r, c).coerceIn(2, 7)
                    tauN = n
                    matrixA = resizeMatrix(strGrid, n, n)
                    matrixB = resizeMatrix(matrixB, n, tauM)
                    matrixC = resizeMatrix(matrixC, tauK, n)
                    selectedMatrixTab = 0
                    Toast.makeText(context, "Перенесено в A (${n}×$n)", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    // В ТАУ матрица B имеет размер n×m: если r in 2..7, подстраиваем и n, и число столбцов m = c!
                    if (r in 2..7 && r != tauN) {
                        tauN = r
                        matrixA = resizeMatrix(matrixA, tauN, tauN)
                        matrixC = resizeMatrix(matrixC, tauK, tauN)
                    }
                    tauM = c.coerceIn(1, 7)
                    matrixB = resizeMatrix(strGrid, tauN, tauM)
                    selectedMatrixTab = 1
                    Toast.makeText(context, "Перенесено в B (${tauN}×$tauM)", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // В ТАУ матрица C имеет размер k×n: если c in 2..7, подстраиваем и n, и число строк k = r!
                    if (c in 2..7 && c != tauN) {
                        tauN = c
                        matrixA = resizeMatrix(matrixA, tauN, tauN)
                        matrixB = resizeMatrix(matrixB, tauN, tauM)
                    }
                    tauK = r.coerceIn(1, 7)
                    matrixC = resizeMatrix(strGrid, tauK, tauN)
                    selectedMatrixTab = 2
                    Toast.makeText(context, "Перенесено в C (${tauK}×$tauN)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Состояния модальных окон (Степень, Фробениус, Модальное управление, LQR)
    var showPowerDialog by remember { mutableStateOf(false) }
    var powerInput by remember { mutableStateOf("3") }

    var showFrobeniusDialog by remember { mutableStateOf(false) }
    var frobSize by rememberSaveable { mutableIntStateOf(3) }
    var frobA by remember { mutableStateOf(List(3) { List(3) { "0" } }) }
    var frobB by remember { mutableStateOf(List(3) { listOf("0") }) }

    var showPolesDialog by remember { mutableStateOf(false) }
    var designSize by rememberSaveable { mutableIntStateOf(3) }
    var designA by remember { mutableStateOf(List(3) { List(3) { "0" } }) }
    var designB by remember { mutableStateOf(List(3) { listOf("0") }) }
    var poleAssignments by remember { mutableStateOf(List(3) { "-1" }) }

    var showLqrDialog by remember { mutableStateOf(false) }
    var showFlowDialog by remember { mutableStateOf(false) }
    var showTfToSsDialog by remember { mutableStateOf(false) }
    var showSsToTfDialog by remember { mutableStateOf(false) }
    var showObserverDialog by remember { mutableStateOf(false) }
    var obsSize by rememberSaveable { mutableIntStateOf(3) }
    var obsA by remember { mutableStateOf(List(3) { List(3) { "0" } }) }
    var obsC by remember { mutableStateOf(List(1) { List(3) { "0" } }) }
    var obsPoleAssignments by remember { mutableStateOf(List(3) { "-2" }) }
    var tfOrderN by rememberSaveable { mutableIntStateOf(3) }
    var tfNumInputs by remember { mutableStateOf(List(4) { i -> if (i == 3) "1" else "0" }) }
    var tfDenInputs by remember { mutableStateOf(listOf("1", "6", "11", "6")) }
    var tfAutoLoadToMain by remember { mutableStateOf(true) }
    var flowUseB by remember { mutableStateOf(true) }
    var flowUseC by remember { mutableStateOf(true) }
    var lqrN by rememberSaveable { mutableIntStateOf(3) }
    var lqrM by rememberSaveable { mutableIntStateOf(1) }
    var lqrAInput by remember { mutableStateOf(List(3) { List(3) { "0" } }) }
    var lqrBInput by remember { mutableStateOf(List(3) { listOf("0") }) }
    var lqrQInput by remember { mutableStateOf(identityStringGrid(3)) }
    var lqrRInput by remember { mutableStateOf(identityStringGrid(1)) }

    // Мгновенная синхронизация диалогов ТАУ с основным экраном
    fun syncTauDialogToMain(newN: Int, newA: List<List<String>>, newB: List<List<String>>, newQ: List<List<String>>? = null) {
        val n = newN.coerceIn(2, 7)
        tauN = n
        algRowsA = n; algColsA = n
        matrixA = resizeMatrix(newA, n, n)
        val bCols = (newB.firstOrNull()?.size ?: 1).coerceIn(1, 7)
        if (bCols > tauM) tauM = bCols
        val mergedB = List(n) { r ->
            List(tauM) { c ->
                if (r < newB.size && c < newB[r].size) newB[r][c]
                else matrixB.getOrNull(r)?.getOrNull(c) ?: "0"
            }
        }
        matrixB = mergedB
        algRowsB = n; algColsB = tauM
        if (newQ != null) {
            matrixC = resizeMatrix(newQ, tauK.coerceAtLeast(n), n)
        } else {
            matrixC = resizeMatrix(matrixC, tauK, n)
        }
    }

    val calculatedCellSize = ((screenWidth - 44.dp) / activeCols.coerceAtLeast(2)).coerceIn(44.dp, 62.dp)
    val cellFontSize = when {
        activeCols >= 6 -> 11.sp
        activeCols >= 5 -> 12.sp
        calculatedCellSize < 54.dp -> 13.sp
        else -> 16.sp
    }

    val appBgBrush = if (appIsLightTheme) {
        Brush.verticalGradient(listOf(Color(0xFFE8EEF7), Color(0xFFF4F6FB), Color(0xFFEDF2F9)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF14233B), BgDark, Color(0xFF070B14)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appBgBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // --- ШАПКА ПРИЛОЖЕНИЯ ---

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "MATRIX LAB",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.8.sp,
                        color = TextWhite,
                        maxLines = 1
                    )
                    Text(
                        text = "ALGEBRA & THEORY OF CONTROL • PRO",
                        fontSize = 9.5.sp,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { appIsLightTheme = !appIsLightTheme },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (appIsLightTheme) "🌙" else "☀️",
                            fontSize = 14.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    FilledTonalButton(
                        onClick = { showHistorySheet = true },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "🕒 ${historyList.size}",
                            color = AccentCyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- 2 ГЛАВНЫЕ ВКЛАДКИ: "⚡ Алгебра" и "🎛 ТАУ, Спектр и f(A)" ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .border(1.dp, Color(0xFF24334D), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CategoryPill("⚡ Алгебра и Векторы", mainTab == 0, AccentCyan, Modifier.weight(1f)) {
                    mainTab = 0
                }
                CategoryPill("🎛 ТАУ, Спектр и f(A)", mainTab == 1, AccentPurple, Modifier.weight(1.1f)) {
                    mainTab = 1
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- ОСНОВНОЙ ПРОКРУЧИВАЕМЫЙ ЭКРАН ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ВЕРХНЯЯ ПАНЕЛЬ ВЫБОРА МАТРИЦЫ A / B / C С ИХ АКТУАЛЬНЫМИ РАЗМЕРАМИ
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardDark)
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MatrixSelectChip(
                        text = "A (${curRowsA}×$curColsA)",
                        selected = selectedMatrixTab == 0,
                        accent = AccentCyan,
                        modifier = Modifier.weight(1f)
                    ) { selectedMatrixTab = 0 }

                    MatrixSelectChip(
                        text = "B (${curRowsB}×$curColsB)",
                        selected = selectedMatrixTab == 1,
                        accent = AccentPurple,
                        modifier = Modifier.weight(1f)
                    ) { selectedMatrixTab = 1 }

                    MatrixSelectChip(
                        text = "C (${curRowsC}×$curColsC)",
                        selected = selectedMatrixTab == 2,
                        accent = AccentAmber,
                        modifier = Modifier.weight(1f)
                    ) { selectedMatrixTab = 2 }

                    MatrixSelectChip(
                        text = "👁 Все 3",
                        selected = showAllThreeMatrices,
                        accent = AccentEmerald,
                        modifier = Modifier.weight(0.85f)
                    ) { showAllThreeMatrices = !showAllThreeMatrices }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- БЛОК УПРАВЛЕНИЯ РАЗМЕРНОСТЬЮ (ЗАВИСИТ ОТ ВКЛАДКИ "АЛГЕБРА" ИЛИ "ТАУ") ---
                if (mainTab == 0) {
                    // Динамический базовый размер d (подстраивается под текущую матрицу или матрицу A: например 4×4, 1×4, 4×1)
                    val dynDim = maxOf(activeRows, activeCols, algRowsA).coerceIn(2, 7)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Строк $tabName:", color = TextDim, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CounterButton("−", activeRows > 1) {
                                        if (activeRows > 1) setMatrixDimensions(selectedMatrixTab, activeRows - 1, activeCols)
                                    }
                                    Text("$activeRows", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                    CounterButton("+", activeRows < 7) {
                                        if (activeRows < 7) setMatrixDimensions(selectedMatrixTab, activeRows + 1, activeCols)
                                    }
                                }
                            }
                        }
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = CardDark),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Столб. $tabName:", color = TextDim, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CounterButton("−", activeCols > 1) {
                                        if (activeCols > 1) setMatrixDimensions(selectedMatrixTab, activeRows, activeCols - 1)
                                    }
                                    Text("$activeCols", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                    CounterButton("+", activeCols < 7) {
                                        if (activeCols < 7) setMatrixDimensions(selectedMatrixTab, activeRows, activeCols + 1)
                                    }
                                }
                            }
                        }
                    }

                    // Динамические пресеты размеров: d×d Квадр., 1×d Строка, d×1 Столбец
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickActionChip("${dynDim}×$dynDim Квадр.", AccentEmerald, Color(0xFF1E293B), Modifier.weight(1f)) {
                            setMatrixDimensions(selectedMatrixTab, dynDim, dynDim)
                        }
                        QuickActionChip("1×$dynDim Строка", AccentCyan, Color(0xFF1E293B), Modifier.weight(1f)) {
                            setMatrixDimensions(selectedMatrixTab, 1, dynDim)
                        }
                        QuickActionChip("${dynDim}×1 Столбец", AccentPurple, Color(0xFF1E293B), Modifier.weight(1f)) {
                            setMatrixDimensions(selectedMatrixTab, dynDim, 1)
                        }
                    }
                } else {
                    // В РАЗДЕЛЕ "ТАУ, СПЕКТР И f(A)":
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        when (selectedMatrixTab) {
                            0 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Матрица состояния A (n×n)", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Порядок системы n для B (n×m) и C (k×n)", color = TextDim, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CounterButton("−", tauN > 2) {
                                            if (tauN > 2) setMatrixDimensions(0, tauN - 1, tauN - 1)
                                        }
                                        Text("${tauN}×$tauN", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                        CounterButton("+", tauN < 7) {
                                            if (tauN < 7) setMatrixDimensions(0, tauN + 1, tauN + 1)
                                        }
                                    }
                                }
                            }
                            1 -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Матрица управления B (${tauN}×$tauM)", color = AccentPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Строк n = $tauN 🔒 • Столбцов (входов) m:", color = TextDim, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CounterButton("−", tauM > 1) {
                                            if (tauM > 1) setMatrixDimensions(1, tauN, tauM - 1)
                                        }
                                        Text("m = $tauM", color = AccentPurple, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                        CounterButton("+", tauM < 7) {
                                            if (tauM < 7) setMatrixDimensions(1, tauN, tauM + 1)
                                        }
                                    }
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Матрица выхода C (${tauK}×$tauN)", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Столбцов n = $tauN 🔒 • Строк (выходов) k:", color = TextDim, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CounterButton("−", tauK > 1) {
                                            if (tauK > 1) setMatrixDimensions(2, tauK - 1, tauN)
                                        }
                                        Text("k = $tauK", color = AccentAmber, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                                        CounterButton("+", tauK < 7) {
                                            if (tauK < 7) setMatrixDimensions(2, tauK + 1, tauN)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- СЕТКА ВВОДА ЯЧЕЕК (ОДНА МАТРИЦА ИЛИ ВСЕ 3 МАТРИЦЫ СРАЗУ) ---
                if (!showAllThreeMatrices) {
                    MatrixGridInput(
                        rows = activeRows,
                        cols = activeCols,
                        data = activeData,
                        accentColor = activeAccent,
                        cellSize = calculatedCellSize,
                        fontSize = cellFontSize,
                        onValueChange = { r, c, v ->
                            val updated = activeData.mapIndexed { ri, row ->
                                if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row
                            }
                            updateActiveMatrix(updated)
                        }
                    )
                } else {
                    Text("Матрица A (${curRowsA}×$curColsA)", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    MatrixGridInput(curRowsA, curColsA, viewA, AccentCyan, 46.dp, 13.sp) { r, c, v ->
                        selectedMatrixTab = 0
                        matrixA = viewA.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Матрица B (${curRowsB}×$curColsB)", color = AccentPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    MatrixGridInput(curRowsB, curColsB, viewB, AccentPurple, 46.dp, 13.sp) { r, c, v ->
                        selectedMatrixTab = 1
                        matrixB = viewB.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Матрица C (${curRowsC}×$curColsC)", color = AccentAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    MatrixGridInput(curRowsC, curColsC, viewC, AccentAmber, 46.dp, 13.sp) { r, c, v ->
                        selectedMatrixTab = 2
                        matrixC = viewC.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                    }
                }

                // --- РЯД 1: Рандом | Единичная | Копир | Встав | ↩️ Отмена | ✕ | 🧹 Все ---

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickActionChip("🎲 Рандом", TextWhite, Color(0xFF1E293B), Modifier.weight(1f)) {
                        pushUndo()
                        if (showAllThreeMatrices) {
                            matrixA = List(curRowsA) { List(curColsA) { Random.nextInt(-9, 10).toString() } }
                            matrixB = List(curRowsB) { List(curColsB) { Random.nextInt(-9, 10).toString() } }
                            matrixC = List(curRowsC) { List(curColsC) { Random.nextInt(-9, 10).toString() } }
                        } else {
                            updateActiveMatrix(List(activeRows) { List(activeCols) { Random.nextInt(-9, 10).toString() } })
                        }
                    }
                    QuickActionChip("𝐈 Единич.", AccentEmerald, AccentEmerald.copy(alpha = 0.14f), Modifier.weight(1f)) {
                        pushUndo()
                        updateActiveMatrix(List(activeRows) { r -> List(activeCols) { c -> if (r == c) "1" else "0" } })
                    }
                    QuickActionChip("📄 Копир.", AccentAmber, AccentAmber.copy(alpha = 0.15f), Modifier.weight(1f)) {
                        val txt = activeData.joinToString("\n") { it.joinToString("\t") }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Matrix $tabName", txt))
                        Toast.makeText(context, "Матрица $tabName скопирована!", Toast.LENGTH_SHORT).show()
                    }
                    QuickActionChip("📋 Встав.", AccentCyan, AccentCyan.copy(alpha = 0.16f), Modifier.weight(1f)) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clipText.isNullOrEmpty()) {
                            val autoParsed = parsePastedTextAutoSize(clipText)
                            if (autoParsed != null) {
                                pushUndo()
                                val newR = autoParsed.size
                                val newC = autoParsed[0].size
                                if (mainTab == 0) {
                                    setMatrixDimensions(selectedMatrixTab, newR, newC)
                                    updateActiveMatrix(autoParsed)
                                    Toast.makeText(context, "Вставлено в $tabName (${newR}×$newC)!", Toast.LENGTH_SHORT).show()
                                } else {
                                    when (selectedMatrixTab) {
                                        0 -> {
                                            val n = maxOf(newR, newC).coerceIn(2, 7)
                                            tauN = n
                                            matrixA = resizeMatrix(autoParsed, n, n)
                                            matrixB = resizeMatrix(matrixB, n, tauM)
                                            matrixC = resizeMatrix(matrixC, tauK, n)
                                        }
                                        1 -> {
                                            if (newR in 2..7) {
                                                tauN = newR
                                                matrixA = resizeMatrix(matrixA, tauN, tauN)
                                                matrixC = resizeMatrix(matrixC, tauK, tauN)
                                            }
                                            tauM = newC.coerceIn(1, 7)
                                            matrixB = resizeMatrix(autoParsed, tauN, tauM)
                                        }
                                        else -> {
                                            if (newC in 2..7) {
                                                tauN = newC
                                                matrixA = resizeMatrix(matrixA, tauN, tauN)
                                                matrixB = resizeMatrix(matrixB, tauN, tauM)
                                            }
                                            tauK = newR.coerceIn(1, 7)
                                            matrixC = resizeMatrix(autoParsed, tauK, tauN)
                                        }
                                    }
                                    Toast.makeText(context, "Вставлено в $tabName!", Toast.LENGTH_SHORT).show()
                                }
                            } else Toast.makeText(context, "Не удалось распознать!", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, "Буфер пуст!", Toast.LENGTH_SHORT).show()
                    }
                }

                // --- РЯД 2 (4 широкие кнопки): ↩️ Назад | ↪️ Вперёд | ✕ Сброс | 🧹 Все = 0 ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickActionChip(
                        text = "↩️ Назад",
                        textColor = if (undoSnapshots.isNotEmpty()) AccentCyan else TextDim,
                        bgColor = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    ) { popUndo() }

                    QuickActionChip(
                        text = "↪️ Вперёд",
                        textColor = if (redoSnapshots.isNotEmpty()) AccentCyan else TextDim,
                        bgColor = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    ) { popRedo() }

                    QuickActionChip(
                        text = "✕ Сброс $tabName",
                        textColor = Color(0xFFF87171),
                        bgColor = Color(0xFF2A1621),
                        modifier = Modifier.weight(1f)
                    ) {
                        pushUndo()
                        updateActiveMatrix(List(activeRows) { List(activeCols) { "0" } })
                    }

                    QuickActionChip(
                        text = "🧹 Все = 0",
                        textColor = Color(0xFFF87171),
                        bgColor = Color(0xFF2A1621),
                        modifier = Modifier.weight(1f)
                    ) {
                        pushUndo()
                        matrixA = List(curRowsA) { List(curColsA) { "0" } }
                        matrixB = List(curRowsB) { List(curColsB) { "0" } }
                        matrixC = List(curRowsC) { List(curColsC) { "0" } }
                        Toast.makeText(context, "🧹 Все 3 матрицы (A, B, C) очищены!", Toast.LENGTH_SHORT).show()
                    }
                }
                // --- РЯД 2: БЫСТРЫЙ ПЕРЕНОС И ОБМЕН МЕЖДУ МАТРИЦАМИ A, B, C (УНИВЕРСАЛЬНО ДЛЯ АЛГЕБРЫ И ТАУ) ---
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val other1 = when (selectedMatrixTab) { 0 -> 1; 1 -> 0; else -> 0 }
                    val other2 = when (selectedMatrixTab) { 0 -> 2; 1 -> 2; else -> 1 }
                    val name1 = listOf("A", "B", "C")[other1]
                    val name2 = listOf("A", "B", "C")[other2]
                    val color1 = listOf(AccentCyan, AccentPurple, AccentAmber)[other1]
                    val color2 = listOf(AccentCyan, AccentPurple, AccentAmber)[other2]

                    fun copyCurrentToTarget(targetIdx: Int) {
                        val r = activeRows
                        val c = activeCols
                        val copied = resizeMatrix(activeData, r, c)
                        val targetName = listOf("A", "B", "C")[targetIdx]
                        if (mainTab == 0) {
                            // В Алгебре переносим матрицу ВМЕСТЕ С ЕЁ РАЗМЕРОМ (например, столбик 3×1 в квадратную матрицу делает её 3×1!)
                            when (targetIdx) {
                                0 -> { algRowsA = r; algColsA = c; matrixA = copied }
                                1 -> { algRowsB = r; algColsB = c; matrixB = copied }
                                else -> { algRowsC = r; algColsC = c; matrixC = copied }
                            }
                            selectedMatrixTab = targetIdx // Сразу переключаем фокус на целевую матрицу, чтобы вы видели результат!
                            Toast.makeText(context, "$tabName (${r}×$c) ➔ в $targetName (${r}×$c)!", Toast.LENGTH_SHORT).show()
                        } else {
                            val parsedCur = runCatching { parse(activeData) }.getOrNull()
                            if (parsedCur != null) {
                                transferResultToTarget(targetIdx, parsedCur)
                                selectedMatrixTab = targetIdx
                            }
                        }
                    }

                    QuickActionChip("$tabName ➔ в $name1", color1, color1.copy(alpha = 0.12f), Modifier.weight(1f)) {
                        copyCurrentToTarget(other1)
                    }
                    QuickActionChip("$tabName ➔ в $name2", color2, color2.copy(alpha = 0.12f), Modifier.weight(1f)) {
                        copyCurrentToTarget(other2)
                    }
                    QuickActionChip("$tabName ⇄ $name1", TextWhite, Color(0xFF1E293B), Modifier.weight(1f)) {
                        if (mainTab == 0) {
                            if (selectedMatrixTab == 0 || selectedMatrixTab == 1) {
                                val rA = algRowsA; val cA = algColsA; val mA = matrixA
                                algRowsA = algRowsB; algColsA = algColsB; matrixA = matrixB
                                algRowsB = rA; algColsB = cA; matrixB = mA
                            } else {
                                val rC = algRowsC; val cC = algColsC; val mC = matrixC
                                algRowsC = algRowsA; algColsC = algColsA; matrixC = matrixA
                                algRowsA = rC; algColsA = cC; matrixA = mC
                            }
                        } else {
                            // В режиме ТАУ обмениваем содержимое с сохранением канонических размерностей n×n, n×m, k×n
                            if (selectedMatrixTab == 0 || selectedMatrixTab == 1) {
                                val oldA = viewA; val oldB = viewB
                                matrixA = resizeMatrix(oldB, tauN, tauN)
                                matrixB = resizeMatrix(oldA, tauN, tauM)
                            } else {
                                val oldA = viewA; val oldC = viewC
                                matrixA = resizeMatrix(oldC, tauN, tauN)
                                matrixC = resizeMatrix(oldA, tauK, tauN)
                            }
                        }
                        Toast.makeText(context, "$tabName ⇄ $name1 обменяны!", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                // --- ПАНЕЛЬ ОПЕРАЦИЙ (ВКЛАДКА 0 = АЛГЕБРА И ВЕКТОРЫ | ВКЛАДКА 1 = ТАУ, СПЕКТР И f(A)) ---
                AnimatedContent(
                    targetState = mainTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "MainTabOperations"
                ) { mode ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardDark.copy(alpha = 0.72f))
                            .border(1.dp, Color(0xFF1F2D46), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        if (mode == 0) {
                            // ================= РАЗДЕЛ 0: АЛГЕБРА И ВЕКТОРЫ =================
                            SectionHeader("Свойства и унарные операции над [$tabName (${activeRows}×$activeCols)]")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("${tabName}⁻¹ (Обратная)", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        if (activeRows != activeCols) {
                                            lastResultTitle = "Обратная $tabName⁻¹"
                                            lastResultMatrix = null; lastResultGrid = null
                                            resultScalarText = "Обратная матрица существует только для квадратных матриц (сейчас ${activeRows}×$activeCols)!"
                                            isError = true; scrollToBottom()
                                        } else {
                                            val res = math.inverse(parse(activeData))
                                            if (res != null) {
                                                showMatrixResult(res, "Обратная $tabName⁻¹")
                                            } else {
                                                lastResultTitle = "Обратная $tabName⁻¹"
                                                lastResultMatrix = null; lastResultGrid = null
                                                resultScalarText = "Матрица вырождена (det = 0) или плохо обусловлена!\nОбратная матрица не существует."
                                                isError = true; scrollToBottom()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка обращения матрицы"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("${tabName}ᵀ (Трансп.)", Modifier.weight(1f)) {
                                    try {
                                        showMatrixResult(math.transpose(parse(activeData)), "Транспонирование ${tabName}ᵀ")
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка транспонирования"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("det($tabName)", Modifier.weight(1f)) {
                                    try {
                                        lastResultTitle = "Определитель det($tabName)"
                                        lastResultMatrix = null; lastResultGrid = null
                                        if (activeRows != activeCols) {
                                            resultScalarText = "Определитель определён только для квадратных матриц (сейчас ${activeRows}×$activeCols)!"
                                            isError = true
                                        } else {
                                            val res = math.determinant(parse(activeData))
                                            if (res == null) {
                                                resultScalarText = "Переполнение при вычислении det($tabName)"
                                                isError = true
                                            } else {
                                                val txt = "det($tabName) = ${formatNumber(res)}"
                                                resultScalarText = txt; isError = false
                                                addHistoryItem(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                            }
                                        }
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Rank($tabName)", Modifier.weight(1f)) {
                                    try {
                                        lastResultTitle = "Ранг rank($tabName)"
                                        lastResultMatrix = null; lastResultGrid = null
                                        val res = math.rank(parse(activeData))
                                        val txt = "rank($tabName) = $res"
                                        resultScalarText = txt; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("След Tr($tabName)", Modifier.weight(1f)) {
                                    try {
                                        lastResultTitle = "След Tr($tabName)"
                                        lastResultMatrix = null; lastResultGrid = null
                                        if (activeRows != activeCols) {
                                            resultScalarText = "След матрицы определён только для квадратных матриц!"
                                            isError = true
                                        } else {
                                            val res = math.trace(parse(activeData))
                                            val txt = "Tr($tabName) = ${formatNumber(res ?: 0.0)}"
                                            resultScalarText = txt; isError = false
                                            addHistoryItem(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                        }
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("‖$tabName‖_F", Modifier.weight(1f)) {
                                    try {
                                        lastResultTitle = "Норма Фробениуса ‖$tabName‖_F"
                                        lastResultMatrix = null; lastResultGrid = null
                                        val norm = math.norm(parse(activeData))
                                        val txt = "‖$tabName‖_F = ${formatNumber(norm)}"
                                        resultScalarText = txt; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("‖$tabName‖_2", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        lastResultTitle = "Спектральная норма ‖$tabName‖_2"
                                        lastResultMatrix = null; lastResultGrid = null
                                        val norm2 = math.spectralNorm(parse(activeData))
                                        val txt = if (norm2 != null) "‖$tabName‖_2 = ${formatNumber(norm2)}" else "Ошибка вычисления нормы"
                                        resultScalarText = txt; isError = norm2 == null
                                        if (norm2 != null) addHistoryItem(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Арифметика матриц A (${curRowsA}×$curColsA), B (${curRowsB}×$curColsB), C (${curRowsC}×$curColsC)")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("A + B", Modifier.weight(1f)) {
                                    try { showMatrixResult(math.add(parse(viewA), parse(viewB)), "A + B") }
                                    catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                                }
                                AdaptiveBtn("A − B", Modifier.weight(1f)) {
                                    try { showMatrixResult(math.subtract(parse(viewA), parse(viewB)), "A − B") }
                                    catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                                }
                                AdaptiveBtn("A × B", Modifier.weight(1f), colorType = 1) {
                                    try { showMatrixResult(math.multiply(parse(viewA), parse(viewB)), "A × B") }
                                    catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("A + B + C", Modifier.weight(1f)) {
                                    try {
                                        val ab = math.add(parse(viewA), parse(viewB))
                                        val res = if (ab != null) math.add(ab, parse(viewC)) else null
                                        showMatrixResult(res, "A + B + C")
                                    } catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                                }
                                AdaptiveBtn("A − B − C", Modifier.weight(1f)) {
                                    try {
                                        val ab = math.subtract(parse(viewA), parse(viewB))
                                        val res = if (ab != null) math.subtract(ab, parse(viewC)) else null
                                        showMatrixResult(res, "A − B − C")
                                    } catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                                }
                                AdaptiveBtn("A × B × C", Modifier.weight(1f)) {
                                    try {
                                        val ab = math.multiply(parse(viewA), parse(viewB))
                                        val res = if (ab != null) math.multiply(ab, parse(viewC)) else null
                                        showMatrixResult(res, "A × B × C")
                                    } catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Векторные операции (над A и B как векторами)")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("a · b (Скалярное)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Скалярное произведение a · b"
                                        val totalA = curRowsA * curColsA; val totalB = curRowsB * curColsB
                                        if (totalA != totalB) {
                                            lastResultMatrix = null; lastResultGrid = null
                                            resultScalarText = "У векторов A и B должно быть одинаковое количество элементов ($totalA ≠ $totalB)!"
                                            isError = true
                                        } else {
                                            val dot = math.dotProduct(parse(viewA), parse(viewB))
                                            val txt = "a · b = ${formatNumber(dot ?: 0.0)}"
                                            lastResultMatrix = null; lastResultGrid = null; resultScalarText = txt; isError = false
                                            addHistoryItem(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                        }
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("a ⨯ b (Векторное 3D)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Векторное произведение a ⨯ b"
                                        val totalA = curRowsA * curColsA; val totalB = curRowsB * curColsB
                                        if (totalA != 3 || totalB != 3) {
                                            lastResultMatrix = null; lastResultGrid = null
                                            resultScalarText = "Векторное произведение определено только для 3D-векторов (3 элемента)!"
                                            isError = true; scrollToBottom()
                                        } else {
                                            showMatrixResult(math.crossProduct(parse(viewA), parse(viewB)), "Векторное a ⨯ b")
                                        }
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0B1322))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Скаляр k:", color = TextDim, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardDark)
                                        .border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = scalarK,
                                        onValueChange = { scalarK = it },
                                        textStyle = TextStyle(color = TextWhite, textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        cursorBrush = SolidColor(AccentCyan)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val k = parseSmartCell(scalarK) ?: 1.0
                                            showMatrixResult(math.multiplyScalar(parse(activeData), k), "Умножение $scalarK × $tabName")
                                        } catch (e: Exception) {
                                            lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Вычислить k × $tabName", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Решение СЛАУ и Матричный калькулятор формул")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("🧮 Решить СЛАУ A · X = B (Метод Гаусса и ФСР)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        val (rep, solMat) = math.solveLinearSystemFullReport(parse(viewA), parse(viewB))
                                        lastResultTitle = "Решение СЛАУ A·X = B (Гаусс и ФСР)"
                                        resultScalarText = rep
                                        lastResultMatrix = solMat
                                        lastResultGrid = null
                                        isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = rep, resultMatrix = solMat))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка СЛАУ"; isError = true; scrollToBottom()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0B1322))
                                    .border(1.dp, if (appIsLightTheme) Color(0xFFCBD5E1) else Color.Transparent, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("Формула (доступны A, B, C, I, +, −, *, ^2, ^-1, ^T, inv(A)):", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CardDark)
                                            .border(1.dp, if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = formulaInput,
                                            onValueChange = { formulaInput = it },
                                            textStyle = TextStyle(color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                            singleLine = true,
                                            cursorBrush = SolidColor(AccentCyan),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            try {
                                                val res = math.evaluateMatrixFormula(formulaInput, parse(viewA), parse(viewB), parse(viewC))
                                                showMatrixResult(res, "Формула: $formulaInput")
                                            } catch (e: Exception) {
                                                lastResultTitle = "Формула: $formulaInput"
                                                lastResultMatrix = null; lastResultGrid = null
                                                resultScalarText = e.message ?: "Ошибка в формуле"
                                                isError = true; scrollToBottom()
                                            }
                                        },
                                        modifier = Modifier.height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp)
                                    ) {
                                        Text("Вычислить", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            // ================= РАЗДЕЛ 1: ОБЪЕДИНЕННЫЙ РАЗДЕЛ "ТАУ, СПЕКТР И f(A)" =================
                            // Матрица A: n×n, Матрица B: n×m, Матрица C: k×n
                            val targetForSquareFunc = if (activeRows == activeCols) activeData else viewA
                            val targetName = if (activeRows == activeCols) tabName else "A"

                            SectionHeader("Спектр и канонические формы матрицы [$targetName (${tauN}×$tauN)]")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("λ_i (Числа)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Собственные числа λ_i ($targetName)"
                                        val raw = parse(targetForSquareFunc)
                                        val eig = math.eigenvalues(raw)
                                        val text = eig.mapIndexed { idx, c -> "λ_${idx + 1} = $c" }.joinToString("\n")
                                        resultScalarText = text
                                        lastResultMatrix = null; lastResultGrid = null; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = text, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления λ_i"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("v_i (Векторы)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Собственные и присоед. векторы ($targetName)"
                                        val raw = parse(targetForSquareFunc)
                                        val eig = math.eigenvalues(raw)
                                        val vectorsInfo = math.eigenvectorsWithDetails(raw, eig)
                                        val text = vectorsInfo.joinToString("\n\n") { info ->
                                            val typeStr = if (info.isAssociated) {
                                                "Присоединенный вектор (ур. ${info.chainLevel})"
                                            } else {
                                                "Собственный вектор"
                                            }
                                            "v_${info.index} (λ_${info.index} = ${info.lambda}) — $typeStr:\n" +
                                                    "  Ненормированный: ${info.canon}\n" +
                                                    "  Нормированный:   ${info.unit}"
                                        }
                                        resultScalarText = text
                                        lastResultMatrix = null; lastResultGrid = null; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = text, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления векторов"; scrollToBottom()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("ЖНФ (Комплексная)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "ЖНФ ($targetName)"
                                        val raw = parse(targetForSquareFunc)
                                        val (desc, jnfGrid) = math.jordanForm(raw)
                                        resultScalarText = desc
                                        lastResultGrid = jnfGrid.takeIf { it.isNotEmpty() }
                                        lastResultMatrix = null
                                        isError = jnfGrid.isEmpty()
                                        if (!isError) {
                                            addHistoryItem(HistoryItem(title = lastResultTitle, resultText = "$desc\n" + jnfGrid.joinToString("\n") { it.joinToString("\t") }, resultMatrix = null))
                                        }
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultGrid = null; lastResultMatrix = null; isError = true
                                        resultScalarText = e.message ?: "Не удалось вычислить ЖНФ"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("РЖНФ (Вещественная)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "РЖНФ ($targetName)"
                                        val raw = parse(targetForSquareFunc)
                                        val (desc, rjnfGrid) = math.realJordanForm(raw)
                                        resultScalarText = desc
                                        lastResultGrid = rjnfGrid.takeIf { it.isNotEmpty() }
                                        lastResultMatrix = null
                                        isError = rjnfGrid.isEmpty()
                                        if (!isError) {
                                            addHistoryItem(HistoryItem(title = lastResultTitle, resultText = "$desc\n" + rjnfGrid.joinToString("\n") { it.joinToString("\t") }, resultMatrix = null))
                                        }
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultGrid = null; lastResultMatrix = null; isError = true
                                        resultScalarText = e.message ?: "Не удалось вычислить РЖНФ"; scrollToBottom()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Характеристический многочлен p(λ)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Характеристический многочлен p(λ) ($targetName)"
                                        val coefficients = characteristicPolynomialCoefficients(parse(targetForSquareFunc))
                                        val polynomial = formatCharacteristicPolynomial(coefficients)
                                        resultScalarText = "p(λ) = $polynomial"
                                        lastResultMatrix = null; lastResultGrid = null; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления p(λ)"; scrollToBottom()
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Устойчивость и критерии Калмана: A (${tauN}×$tauN), B (${tauN}×$tauM), C (${tauK}×$tauN)")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Непрерывная Re(λ) + Гурвиц", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        lastResultTitle = "Устойчивость (Спектр и Критерий Гурвица)"
                                        resultScalarText = math.hurwitzAndSpectralStability(parse(viewA))
                                        lastResultMatrix = null; lastResultGrid = null; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("Дискретная ρ(A)", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        lastResultTitle = "Спектральный радиус ρ(A)"
                                        val eigenvalues = math.eigenvalues(parse(viewA))
                                        val spectralRadius = eigenvalues.maxOf { it.abs() }
                                        val stabilityMessage = when {
                                            spectralRadius < 1.0 - 1e-8 -> "✅ АСИМПТОТИЧЕСКИ УСТОЙЧИВА (|λ_i| < 1)"
                                            spectralRadius > 1.0 + 1e-8 -> "❌ НЕУСТОЙЧИВА (существует |λ_i| > 1)"
                                            else -> "⚠️ НА ГРАНИЦЕ ЕДИНИЧНОГО КРУГА (ρ ≈ 1)"
                                        }
                                        val details = eigenvalues.mapIndexed { idx, v -> "λ_${idx + 1} = $v" }.joinToString("\n")
                                        resultScalarText = "ρ(A) = ${formatPreciseNumber(spectralRadius)}\n\n$stabilityMessage\n\nСпектр матрицы A:\n$details"
                                        lastResultMatrix = null; lastResultGrid = null; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Управляемость (A,B)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Матрица управляемости W_c"
                                        val matA = parse(viewA); val matB = parse(viewB)
                                        val wc = math.controllability(matA, matB)
                                        val r = math.rank(wc)
                                        val state = if (r == matA.size) "✅ Система ПОЛНОСТЬЮ управляема" else "❌ Система НЕ полностью управляема (rank = $r < ${matA.size})"
                                        resultScalarText = "A (${tauN}×$tauN), B (${tauN}×$tauM)\nrank(W_c) = $r из ${matA.size}\n$state"
                                        lastResultGrid = null; lastResultMatrix = wc; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = wc))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("Наблюдаемость (A,C)", Modifier.weight(1f), colorType = 2) {
                                    try {
                                        lastResultTitle = "Матрица наблюдаемости W_o"
                                        val matA = parse(viewA); val matC = parse(viewC)
                                        val wo = math.observability(matA, matC)
                                        val r = math.rank(wo)
                                        val state = if (r == matA.size) "✅ Система ПОЛНОСТЬЮ наблюдаема" else "❌ Система НЕ полностью наблюдаема (rank = $r < ${matA.size})"
                                        resultScalarText = "A (${tauN}×$tauN), C (${tauK}×$tauN)\nrank(W_o) = $r из ${matA.size}\n$state"
                                        lastResultGrid = null; lastResultMatrix = wo; isError = false
                                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = wo))
                                        scrollToBottom()
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Анализ системы (Блок-схема и Разложение Калмана)", Modifier.weight(1f), colorType = 1) {
                                    flowUseB = viewB.any { row -> row.any { (parseSmartCell(it) ?: 0.0) != 0.0 } }
                                    flowUseC = viewC.any { row -> row.any { (parseSmartCell(it) ?: 0.0) != 0.0 } }
                                    showFlowDialog = true
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Синтез регуляторов и канонические формы (ТАУ)")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Фробениус (A,B)", Modifier.weight(1f), colorType = 2) {
                                    frobSize = tauN
                                    frobA = resizeMatrix(viewA, tauN, tauN)
                                    frobB = List(tauN) { r -> listOf(viewB.getOrNull(r)?.getOrNull(0) ?: "0") }
                                    showFrobeniusDialog = true
                                }
                                AdaptiveBtn("Регулятор (K)", Modifier.weight(1f), colorType = 2) {
                                    designSize = tauN
                                    designA = resizeMatrix(viewA, tauN, tauN)
                                    designB = List(tauN) { r -> listOf(viewB.getOrNull(r)?.getOrNull(0) ?: "0") }
                                    val initEigs = runCatching { math.eigenvalues(parse(designA)) }.getOrDefault(emptyList())
                                    poleAssignments = List(tauN) { i ->
                                        val e = initEigs.getOrNull(i)
                                        when {
                                            e == null -> "-1"
                                            e.re >= 0.0 -> (-maxOf(1L, kotlin.math.round(abs(e.re)).toLong())).toString()
                                            else -> e.toString().replace(" ", "")
                                        }
                                    }
                                    showPolesDialog = true
                                }
                                AdaptiveBtn("Наблюдатель (L)", Modifier.weight(1f), colorType = 2) {
                                    obsSize = tauN
                                    obsA = resizeMatrix(viewA, tauN, tauN)
                                    obsC = List(1) { List(tauN) { c -> viewC.getOrNull(0)?.getOrNull(c) ?: "0" } }
                                    val initEigs = runCatching { math.eigenvalues(parse(obsA)) }.getOrDefault(emptyList())
                                    obsPoleAssignments = List(tauN) { i ->
                                        val e = initEigs.getOrNull(i)
                                        when {
                                            e == null -> "-2"
                                            e.re >= 0.0 -> (-maxOf(2L, kotlin.math.round(abs(e.re)).toLong() + 1L)).toString()
                                            else -> e.toString().replace(" ", "")
                                        }
                                    }
                                    showObserverDialog = true
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("LQR-оптимизация (Уравнение Риккати CARE)", Modifier.weight(1f), colorType = 2) {
                                    lqrN = tauN
                                    lqrM = tauM
                                    lqrAInput = resizeMatrix(viewA, lqrN, lqrN)
                                    lqrBInput = resizeMatrix(viewB, lqrN, lqrM)
                                    val parsedC = runCatching { parse(matrixC) }.getOrNull()
                                    val isSquareNonZeroC = parsedC != null && parsedC.size >= lqrN && parsedC[0].size >= lqrN &&
                                            parsedC.any { row -> row.any { abs(it) > 1e-12 } }
                                    lqrQInput = if (isSquareNonZeroC) resizeMatrix(matrixC, lqrN, lqrN) else identityStringGrid(lqrN)
                                    lqrRInput = identityStringGrid(lqrM)
                                    showLqrDialog = true
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Преобразование «Вход–Выход» W(s) ⇄ «Пространство состояний» (A, B, C)")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("Вход–Выход W(s) ➔ (A,B,C)", Modifier.weight(1f), colorType = 1) {
                                    runCatching {
                                        val aMat = parse(viewA)
                                        val bMat = parse(viewB)
                                        val cMat = parse(viewC)
                                        val hasAll = aMat.any { r -> r.any { abs(it) > 1e-12 } } &&
                                                bMat.any { r -> r.any { abs(it) > 1e-12 } } &&
                                                cMat.any { r -> r.any { abs(it) > 1e-12 } }
                                        if (hasAll) {
                                            val res = math.stateSpaceToTransferFunction(aMat, bMat, cMat)
                                            val n = res.denCoeffs.size - 1
                                            if (n in 1..7) {
                                                tfOrderN = n
                                                tfDenInputs = res.denCoeffs.map { formatPreciseNumber(it) }
                                                tfNumInputs = listOf("0") + res.numCoeffs.map { formatPreciseNumber(it) }
                                            }
                                        }
                                    }
                                    showTfToSsDialog = true
                                }
                                AdaptiveBtn("(A,B,C) ➔ Вход–Выход W(s)", Modifier.weight(1f), colorType = 2) {
                                    showSsToTfDialog = true
                                }
                            }



                            Spacer(modifier = Modifier.height(10.dp))
                            SectionHeader("Высшие матричные функции от [$targetName (${tauN}×$tauN)]")
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("e^$targetName", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        showMatrixResult(math.matrixExponential(parse(targetForSquareFunc)), "Экспонента e^$targetName")
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления e^A"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("ln($targetName)", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        val res = math.matrixLog(parse(targetForSquareFunc))
                                        if (res != null) {
                                            showMatrixResult(res, "Логарифм ln($targetName)")
                                        } else {
                                            lastResultTitle = "Логарифм ln($targetName)"
                                            lastResultMatrix = null; lastResultGrid = null; isError = true
                                            resultScalarText = "Вещественный главный логарифм матрицы не существует (проверьте, что det($targetName) > 0 и нет отрицательных вещественных корней)."
                                            scrollToBottom()
                                        }
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления ln(A)"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("√$targetName", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        val res = math.matrixSqrt(parse(targetForSquareFunc))
                                        if (res != null) {
                                            showMatrixResult(res, "Корень √$targetName")
                                        } else {
                                            lastResultTitle = "Корень √$targetName"
                                            lastResultMatrix = null; lastResultGrid = null; isError = true
                                            resultScalarText = "Вещественный квадратный корень матрицы не существует."
                                            scrollToBottom()
                                        }
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления √A"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("${targetName}^k", Modifier.weight(1f), colorType = 1) { showPowerDialog = true }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AdaptiveBtn("sin($targetName)", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        showMatrixResult(math.matrixTrig(parse(targetForSquareFunc)).first, "Синус sin($targetName)")
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления sin(A)"; scrollToBottom()
                                    }
                                }
                                AdaptiveBtn("cos($targetName)", Modifier.weight(1f), colorType = 1) {
                                    try {
                                        showMatrixResult(math.matrixTrig(parse(targetForSquareFunc)).second, "Косинус cos($targetName)")
                                    } catch (e: Exception) {
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = e.message ?: "Ошибка вычисления cos(A)"; scrollToBottom()
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- КАРТОЧКА РЕЗУЛЬТАТА ---
                ResultCardBlock(
                    title = lastResultTitle,
                    lastResultMatrix = lastResultMatrix,
                    lastResultGrid = lastResultGrid,
                    resultScalarText = resultScalarText,
                    isError = isError,
                    screenWidth = screenWidth,
                    context = context,
                    onClearResult = {
                        lastResultMatrix = null
                        lastResultGrid = null
                        resultScalarText = ""
                        isError = false
                    },
                    onTransferToA = { mat: Array<DoubleArray> -> transferResultToTarget(0, mat) },
                    onTransferToB = { mat: Array<DoubleArray> -> transferResultToTarget(1, mat) },
                    onTransferToC = { mat: Array<DoubleArray> -> transferResultToTarget(2, mat) }
                )
            }
        }

        // --- ШТОРКА ИСТОРИИ ВЫЧИСЛЕНИЙ ---
        if (showHistorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showHistorySheet = false },
                containerColor = CardDark,
                scrimColor = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                HistoryBottomSheetContent(
                    historyList = historyList,
                    onClearHistory = { clearAllHistory() },
                    onTogglePin = { item ->
                        val idx = historyList.indexOfFirst { it.id == item.id }
                        if (idx != -1) {
                            historyList[idx] = historyList[idx].copy(pinned = !historyList[idx].pinned)
                            val sorted = historyList.sortedWith(compareByDescending<HistoryItem> { it.pinned }.thenByDescending { it.id })
                            historyList.clear()
                            historyList.addAll(sorted)
                            HistoryStorage.saveHistory(context, historyList)
                        }
                    },
                    onRestoreSystemAndResult = { item ->
                        lastResultTitle = item.title
                        resultScalarText = item.resultText
                        lastResultMatrix = item.resultMatrix
                        lastResultGrid = null
                        isError = false
                        showHistorySheet = false
                        scrollToBottom()
                        Toast.makeText(context, "👁 Открыто в карточке результата (Исходная система + Решение)", Toast.LENGTH_SHORT).show()
                    },
                    onPasteToSquareA = { mat -> transferResultToTarget(0, mat); showHistorySheet = false },
                    onPasteToSquareB = { mat -> transferResultToTarget(1, mat); showHistorySheet = false },
                    onPasteToSquareC = { mat -> transferResultToTarget(2, mat); showHistorySheet = false }
                )
            }
        }
    }
    if (showFlowDialog) {
        AlertDialog(
            onDismissRequest = { showFlowDialog = false },
            title = { Text("Анализ системы (Блок-схема)", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Матрица A (${tauN}×$tauN) используется всегда.", color = TextDim, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { flowUseB = !flowUseB }) {
                        Checkbox(checked = flowUseB, onCheckedChange = { flowUseB = it })
                        Text("Матрица B (${tauN}×$tauM) введена", color = TextWhite, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { flowUseC = !flowUseC }) {
                        Checkbox(checked = flowUseC, onCheckedChange = { flowUseC = it })
                        Text("Матрица C (${tauK}×$tauN) введена", color = TextWhite, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        // Парсим матрицы (если пустые, они будут из нулей, и функция сама это поймет)
                        val matA = parse(viewA)
                        val matB = if (flowUseB) parse(viewB) else Array(matA.size) { DoubleArray(1) }
                        val matC = if (flowUseC) parse(viewC) else Array(1) { DoubleArray(matA.size) }

                        // Вызываем нашу мощную функцию, которая сразу возвращает готовый текст
                        val report = math.fullBlockDiagramAnalysis(matA, matB, matC)

                        lastResultTitle = "Полный анализ системы (Блок-схема)"
                        resultScalarText = report
                        lastResultGrid = null
                        lastResultMatrix = null
                        isError = false

                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                        showFlowDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка анализа", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Выполнить", color = AccentCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showFlowDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }

    // --- ДИАЛОГ ПРЕОБРАЗОВАНИЯ «ВХОД–ВЫХОД» W(s) -> ПРОСТРАНСТВО СОСТОЯНИЙ (A, B, C) ---
    if (showTfToSsDialog) {
        AlertDialog(
            onDismissRequest = { showTfToSsDialog = false },
            title = { Text("Вход–Выход W(s) ➔ (A, B, C)", color = TextWhite, fontSize = 17.5.sp, fontWeight = FontWeight.Bold) },
            text = {
                val previewNum = remember(tfNumInputs, tfOrderN) {
                    DoubleArray(tfOrderN + 1) { i -> parseSmartCell(tfNumInputs.getOrElse(i) { "0" }) ?: 0.0 }
                }
                val previewDen = remember(tfDenInputs, tfOrderN) {
                    DoubleArray(tfOrderN + 1) { i -> parseSmartCell(tfDenInputs.getOrElse(i) { "0" }) ?: 0.0 }
                }

                Column(modifier = Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Введите коэффициенты многочлена в числителе B(s) и знаменателе A(s) от старшей степени к младшей:",
                        color = TextDim,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Порядок знаменателя n:", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CounterButton("−", tfOrderN > 1) {
                                if (tfOrderN > 1) {
                                    tfOrderN--
                                    tfNumInputs = List(tfOrderN + 1) { i -> tfNumInputs.getOrElse(i + 1) { "0" } }
                                    tfDenInputs = List(tfOrderN + 1) { i -> tfDenInputs.getOrElse(i + 1) { if (i == 0) "1" else "0" } }
                                }
                            }
                            Text("n = $tfOrderN", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
                            CounterButton("+", tfOrderN < 7) {
                                if (tfOrderN < 7) {
                                    tfOrderN++
                                    tfNumInputs = listOf("0") + tfNumInputs
                                    tfDenInputs = listOf("1") + tfDenInputs.mapIndexed { idx, v -> if (idx == 0 && v == "0") "1" else v }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ЧИСЛИТЕЛЬ B(s) = bₙsⁿ + ... + b₁s + b₀:", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0..tfOrderN) {
                            val p = tfOrderN - i
                            val label = when (p) { 0 -> "b₀ (1)"; 1 -> "b₁ (s)"; else -> "b${toSubscript(p.toString())} (s${toSuperscript(p.toString())})" }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, color = TextDim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A))
                                        .border(1.dp, AccentEmerald.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = tfNumInputs.getOrElse(i) { "0" },
                                        onValueChange = { v -> tfNumInputs = List(tfOrderN + 1) { idx -> if (idx == i) v else tfNumInputs.getOrElse(idx) { "0" } } },
                                        textStyle = TextStyle(color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                        singleLine = true,
                                        cursorBrush = SolidColor(AccentEmerald),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ЗНАМЕНАТЕЛЬ A(s) = aₙsⁿ + ... + a₁s + a₀:", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0..tfOrderN) {
                            val p = tfOrderN - i
                            val label = when (p) { 0 -> "a₀ (1)"; 1 -> "a₁ (s)"; else -> "a${toSubscript(p.toString())} (s${toSuperscript(p.toString())})" }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, color = TextDim, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .width(62.dp)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A))
                                        .border(1.dp, AccentCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = tfDenInputs.getOrElse(i) { "0" },
                                        onValueChange = { v -> tfDenInputs = List(tfOrderN + 1) { idx -> if (idx == i) v else tfDenInputs.getOrElse(idx) { "0" } } },
                                        textStyle = TextStyle(color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                                        singleLine = true,
                                        cursorBrush = SolidColor(AccentCyan),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0B1322))
                            .border(1.dp, AccentPurple.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Предпросмотр передаточной функции W(s):", color = TextDim, fontSize = 10.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(math.formatPolynomialS(previewNum), color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Box(modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 3.dp).height(1.2.dp).background(AccentCyan))
                        Text(math.formatPolynomialS(previewDen), color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { tfAutoLoadToMain = !tfAutoLoadToMain }
                    ) {
                        Checkbox(checked = tfAutoLoadToMain, onCheckedChange = { tfAutoLoadToMain = it })
                        Text("Загрузить полученные матрицы A, B, C на главный экран", color = TextWhite, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val numArr = DoubleArray(tfOrderN + 1) { i -> parseSmartCell(tfNumInputs.getOrElse(i) { "0" }) ?: 0.0 }
                        val denArr = DoubleArray(tfOrderN + 1) { i -> parseSmartCell(tfDenInputs.getOrElse(i) { "0" }) ?: 0.0 }
                        val res = math.transferFunctionToStateSpace(numArr, denArr)

                        if (tfAutoLoadToMain) {
                            if (res.n >= 2) {
                                tauN = res.n; tauM = 1; tauK = 1
                            }
                            algRowsA = res.n; algColsA = res.n
                            algRowsB = res.n; algColsB = 1
                            algRowsC = 1; algColsC = res.n
                            matrixA = matrixToPreciseStringList(res.aCtrl)
                            matrixB = matrixToPreciseStringList(res.bCtrl)
                            matrixC = matrixToPreciseStringList(res.cCtrl)
                        }

                        lastResultTitle = "Вход–Выход W(s) ➔ Пространство состояний"
                        resultScalarText = res.report
                        lastResultMatrix = res.aCtrl
                        lastResultGrid = null
                        isError = false
                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = res.report, resultMatrix = res.aCtrl))
                        showTfToSsDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка преобразования W(s)", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Преобразовать", color = AccentCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTfToSsDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }


    // --- ДИАЛОГ СТЕПЕНИ МАТРИЦЫ A^k ---
    if (showPowerDialog) {
        val targetForSquareFunc = if (activeRows == activeCols) activeData else viewA
        val targetName = if (activeRows == activeCols) tabName else "A"
        AlertDialog(
            onDismissRequest = { showPowerDialog = false },
            title = { Text("Степень матрицы", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Введите целую степень k (от -100 до 1000):", color = TextDim, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A))
                            .border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = powerInput,
                            onValueChange = { powerInput = it },
                            textStyle = TextStyle(color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(AccentCyan),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val k = powerInput.toIntOrNull()
                    if (k != null && k in -100..1000) {
                        try {
                            val res = math.matrixPower(parse(targetForSquareFunc), k)
                            showMatrixResult(res, "Степень $targetName^$k")
                            showPowerDialog = false
                        } catch (e: Exception) {
                            Toast.makeText(context, e.message ?: "Ошибка возведения в степень", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Введите целое число от -100 до 1000", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Вычислить", color = AccentCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPowerDialog = false }) { Text("Отмена", color = TextDim) }
            },
            containerColor = CardDark
        )
    }

    // --- ДИАЛОГ ФОРМЫ ФРОБЕНИУСА ---
    if (showFrobeniusDialog) {
        AlertDialog(
            onDismissRequest = { showFrobeniusDialog = false },
            title = { Text("Форма Фробениуса–Люенбергера", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Матрица A и первый столбец B синхронизированы с главным экраном.",
                        color = TextDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Размер n:", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(10.dp))
                        CounterButton("−", frobSize > 2) {
                            if (frobSize > 2) {
                                frobSize--
                                frobA = resizeMatrix(frobA, frobSize, frobSize)
                                frobB = resizeMatrix(frobB, frobSize, 1)
                                syncTauDialogToMain(frobSize, frobA, frobB)
                            }
                        }
                        Text("${frobSize}×$frobSize", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                        CounterButton("+", frobSize < 7) {
                            if (frobSize < 7) {
                                frobSize++
                                frobA = resizeMatrix(frobA, frobSize, frobSize)
                                frobB = resizeMatrix(frobB, frobSize, 1)
                                syncTauDialogToMain(frobSize, frobA, frobB)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("МАТРИЦА A (${frobSize}×${frobSize})", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    MatrixGridInput(frobSize, frobSize, frobA, AccentCyan, 46.dp, 13.sp) { r, c, v ->
                        frobA = frobA.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncTauDialogToMain(frobSize, frobA, frobB)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("МАТРИЦА B (${frobSize}×1)", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    MatrixGridInput(frobSize, 1, frobB, AccentPurple, 46.dp, 13.sp) { r, _, v ->
                        frobB = frobB.mapIndexed { i, row -> if (i == r) listOf(v) else row }
                        syncTauDialogToMain(frobSize, frobA, frobB)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        syncTauDialogToMain(frobSize, frobA, frobB)
                        val a = parse(frobA)
                        val b = parse(frobB)
                        val res = math.frobeniusForm(a, b)
                        require(res != null) { "Форма Фробениуса недоступна: проверьте управляемость системы." }
                        val (aTilde, bTilde, tMat) = res
                        lastResultTitle = "Форма Фробениуса–Люенбергера"
                        resultScalarText = buildString {
                            appendLine("Матрица Ã:")
                            appendLine(formatMatrixAligned(aTilde))
                            appendLine()
                            appendLine("Матрица B̃:")
                            appendLine(formatMatrixAligned(bTilde))
                            appendLine()
                            appendLine("Матрица преобразования T (x = Tz):")
                            append(formatMatrixAligned(tMat))
                        }
                        lastResultGrid = null; lastResultMatrix = aTilde; isError = false
                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = aTilde))
                        showFrobeniusDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка вычисления формы Фробениуса", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showFrobeniusDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }

    // --- ДИАЛОГ МОДАЛЬНОГО УПРАВЛЕНИЯ (АККЕРМАН + РАЗЛОЖЕНИЕ КАЛМАНА) ---
    if (showPolesDialog) {
        AlertDialog(
            onDismissRequest = { showPolesDialog = false },
            title = { Text("Модальное управление (K)", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                var modalAnalysis by remember { mutableStateOf<Triple<List<Complex>, BooleanArray, Int>?>(null) }
                LaunchedEffect(designA, designB) {
                    kotlinx.coroutines.delay(120)
                    modalAnalysis = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        runCatching {
                            val aMat = parse(designA)
                            val bMat = parse(designB)
                            val eigs = math.eigenvalues(aMat)
                            val flags = math.getControllableModeFlags(aMat, bMat, eigs)
                            val r = flags.count { it }
                            Triple(eigs, flags, r)
                        }.getOrNull()
                    }
                }
                val eigenvaluesForTable = modalAnalysis?.first ?: emptyList()
                val ctrlFlags = modalAnalysis?.second ?: BooleanArray(designSize) { true }
                val ctrlRank = modalAnalysis?.third ?: designSize

                Column(modifier = Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Система в реальном времени синхронизирована с главным экраном.",
                        color = TextDim,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Размер n:", color = TextWhite, fontWeight = FontWeight.SemiBold)
                        CounterButton("−", designSize > 2) {
                            if (designSize > 2) {
                                designSize--
                                designA = resizeMatrix(designA, designSize, designSize)
                                designB = resizeMatrix(designB, designSize, 1)
                                poleAssignments = List(designSize) { i -> poleAssignments.getOrElse(i) { "-1" } }
                                syncTauDialogToMain(designSize, designA, designB)
                            }
                        }
                        Text("${designSize}×$designSize", color = TextWhite, fontWeight = FontWeight.Bold)
                        CounterButton("+", designSize < 7) {
                            if (designSize < 7) {
                                designSize++
                                designA = resizeMatrix(designA, designSize, designSize)
                                designB = resizeMatrix(designB, designSize, 1)
                                poleAssignments = List(designSize) { i -> poleAssignments.getOrElse(i) { "-1" } }
                                syncTauDialogToMain(designSize, designA, designB)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A (${designSize}×${designSize})", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(designSize, designSize, designA, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        designA = designA.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncTauDialogToMain(designSize, designA, designB)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("B (${designSize}×1)", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(designSize, 1, designB, AccentPurple, 46.dp, 13.sp) { r, _, v ->
                        designB = designB.mapIndexed { i, row -> if (i == r) listOf(v) else row }
                        syncTauDialogToMain(designSize, designA, designB)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val rankStatus = when {
                        ctrlRank == designSize -> "✅ Полностью управляема (rank W_c = $ctrlRank)"
                        ctrlRank > 0 -> "⚠️ Частично управляема: управляемых мод $ctrlRank из $designSize.\nИзменяйте полюса напротив управляемых мод (неуправляемые фиксированы)!"
                        else -> "❌ Неуправляема (rank W_c = 0)"
                    }
                    Text(rankStatus, color = if (ctrlRank == designSize) AccentEmerald else AccentAmber, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Таблица соответствий (исходное λᵢ ➔ новый полюс μᵢ):", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    repeat(designSize) { index ->
                        val eig = eigenvaluesForTable.getOrNull(index)
                        val isCtrl = ctrlFlags.getOrElse(index) { true }
                        val statusTag = if (ctrlRank < designSize) {
                            if (isCtrl) " [упр.]" else " [неупр.]"
                        } else ""
                        val eigText = if (eig == null) {
                            "λ${toSubscript((index + 1).toString())}$statusTag"
                        } else {
                            "λ${toSubscript((index + 1).toString())} = $eig$statusTag"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text(
                                text = eigText,
                                color = if (isCtrl) AccentCyan else TextDim,
                                fontSize = 11.5.sp,
                                fontWeight = if (isCtrl) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isCtrl) {
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A))
                                        .border(1.dp, AccentCyan.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                        .padding(9.dp)
                                ) {
                                    BasicTextField(
                                        value = poleAssignments.getOrElse(index) { "-1" },
                                        onValueChange = { value ->
                                            poleAssignments = List(designSize) { i ->
                                                if (i == index) value else poleAssignments.getOrElse(i) { "-1" }
                                            }
                                        },
                                        textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        cursorBrush = SolidColor(AccentCyan),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (appIsLightTheme) Color(0xFFE2E8F0) else Color(0xFF161F30))
                                        .border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp))
                                        .padding(9.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = "${eig ?: "фикс."} 🔒",
                                        color = TextDim,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        syncTauDialogToMain(designSize, designA, designB)
                        val aForDesign = parse(designA)
                        val bForDesign = parse(designB)
                        val openEigs = math.eigenvalues(aForDesign)
                        val flags = math.getControllableModeFlags(aForDesign, bForDesign, openEigs)
                        val r = flags.count { it }

                        val poles = List(aForDesign.size) { index ->
                            if (!flags[index]) {
                                openEigs[index]
                            } else {
                                val raw = poleAssignments.getOrElse(index) { "-1" }
                                val parsed = math.parsePoles(raw)
                                require(parsed.size == 1) { "Для полюса μ${index + 1} укажите ровно одно число" }
                                parsed.single()
                            }
                        }

                        val kMat = math.ackermann(aForDesign, bForDesign, poles)
                        val closedLoop = math.subtract(aForDesign, math.multiply(bForDesign, kMat)!!)!!
                        val newSpectrum = math.eigenvalues(closedLoop)
                        val isStabilized = newSpectrum.all { it.re < -1e-7 }

                        lastResultTitle = "Модальное управление (K)"
                        lastResultMatrix = kMat
                        lastResultGrid = null
                        resultScalarText = buildString {
                            if (r < aForDesign.size) {
                                appendLine("Разложение Калмана: rank(W_c) = $r < ${aForDesign.size}")
                                appendLine("Перемещено управляемых мод: $r")
                                appendLine(if (isStabilized) "✅ Система СТАБИЛИЗИРУЕМА и стабилизирована!" else "⚠️ Неуправляемое подпространство содержит неустойчивые моды!")
                                appendLine()
                            }
                            appendLine("Спектр замкнутой системы A − BK:")
                            newSpectrum.forEachIndexed { idx, c -> appendLine("μ${toSubscript((idx + 1).toString())} = $c") }
                            appendLine()
                            append("Матрица обратной связи K (u = −Kx):")
                        }
                        isError = false
                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = kMat))
                        showPolesDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка расчёта модального управления", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showPolesDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }

    // --- ДИАЛОГ LQR ОПТИМИЗАЦИИ ---
    if (showLqrDialog) {
        AlertDialog(
            onDismissRequest = { showLqrDialog = false },
            title = { Text("LQR — оптимальное управление", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Матрица B (${lqrN}×$lqrM) напрямую синхронизирована с вкладкой ТАУ на главном экране!",
                        color = TextDim,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0B1322))
                                .border(1.dp, Color(0xFF24334D), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Состояния (n)", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CounterButton("−", lqrN > 2) {
                                    if (lqrN > 2) {
                                        lqrN--
                                        lqrAInput = resizeMatrix(lqrAInput, lqrN, lqrN)
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrQInput = resizeIdentityMatrix(lqrQInput, lqrN)
                                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                                    }
                                }
                                Text("$lqrN", color = AccentCyan, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                                CounterButton("+", lqrN < 7) {
                                    if (lqrN < 7) {
                                        lqrN++
                                        lqrAInput = resizeMatrix(lqrAInput, lqrN, lqrN)
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrQInput = resizeIdentityMatrix(lqrQInput, lqrN)
                                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0B1322))
                                .border(1.dp, Color(0xFF24334D), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Столбцов B (m)", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CounterButton("−", lqrM > 1) {
                                    if (lqrM > 1) {
                                        lqrM--; tauM = lqrM
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrRInput = resizeIdentityMatrix(lqrRInput, lqrM)
                                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                                    }
                                }
                                Text("$lqrM", color = AccentPurple, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                                CounterButton("+", lqrM < 7) {
                                    if (lqrM < 7) {
                                        lqrM++; tauM = lqrM
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrRInput = resizeIdentityMatrix(lqrRInput, lqrM)
                                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("A (${lqrN}×${lqrN})", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrN, lqrAInput, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        lqrAInput = lqrAInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("B (${lqrN}×${lqrM}) — матрица входов", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrM, lqrBInput, AccentPurple, 42.dp, 12.sp) { r, c, v ->
                        lqrBInput = lqrBInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Q (${lqrN}×${lqrN}, Q ≥ 0)", color = AccentAmber, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrN, lqrQInput, AccentAmber, 42.dp, 12.sp) { r, c, v ->
                        lqrQInput = lqrQInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("R (${lqrM}×${lqrM}, R > 0)", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrM, lqrM, lqrRInput, AccentPurple, 42.dp, 12.sp) { r, c, v ->
                        lqrRInput = lqrRInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        tauM = lqrM
                        syncTauDialogToMain(lqrN, lqrAInput, lqrBInput)
                        val a = parse(lqrAInput)
                        val b = parse(lqrBInput)
                        val q = parse(lqrQInput)
                        val r = parse(lqrRInput)
                        val (pMat, kMat) = math.solveLQR(a, b, q, r)
                        lastResultTitle = "LQR — Оптимальное управление"
                        resultScalarText = "Решение уравнения Риккати P (${lqrN}×$lqrN):\n" +
                                formatMatrixAligned(pMat) +
                                "\n\nМатрица обратной связи K = R⁻¹BᵀP (${lqrM}×$lqrN):\n" +
                                formatMatrixAligned(kMat) +
                                "\n\nЗакон управления: u = −Kx"
                        lastResultGrid = null; lastResultMatrix = kMat; isError = false
                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = kMat))
                        showLqrDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка расчёта LQR", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLqrDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }
    // --- ДИАЛОГ (A, B, C) -> ВХОД-ВЫХОД W(s) (ВСЕ 3 МАТРИЦЫ НА ОДНОМ ЭКРАНЕ) ---
    if (showSsToTfDialog) {
        AlertDialog(
            onDismissRequest = { showSsToTfDialog = false },
            title = { Text("(A, B, C) ➔ Вход–Выход W(s)", color = TextWhite, fontSize = 17.5.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Проверьте или заполните матрицы A, B и C на одном экране:", color = TextDim, fontSize = 11.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Порядок n:", color = TextWhite, fontWeight = FontWeight.SemiBold)
                        CounterButton("−", tauN > 2) { if (tauN > 2) setMatrixDimensions(0, tauN - 1, tauN - 1) }
                        Text("${tauN}×$tauN", color = AccentCyan, fontWeight = FontWeight.Bold)
                        CounterButton("+", tauN < 7) { if (tauN < 7) setMatrixDimensions(0, tauN + 1, tauN + 1) }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("МАТРИЦА СОСТОЯНИЯ A (${tauN}×$tauN)", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    MatrixGridInput(tauN, tauN, viewA, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        matrixA = viewA.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("МАТРИЦА ВХОДА B (${tauN}×$tauM)", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    MatrixGridInput(tauN, tauM, viewB, AccentPurple, 42.dp, 12.sp) { r, c, v ->
                        matrixB = viewB.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("МАТРИЦА ВЫХОДА C (${tauK}×$tauN)", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    MatrixGridInput(tauK, tauN, viewC, AccentAmber, 42.dp, 12.sp) { r, c, v ->
                        matrixC = viewC.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        lastResultTitle = "Коэффициент передачи W(s) из (A, B, C)"
                        val res = math.stateSpaceToTransferFunction(parse(viewA), parse(viewB), parse(viewC))
                        resultScalarText = res.report
                        lastResultMatrix = null; lastResultGrid = null; isError = false
                        val n = res.denCoeffs.size - 1
                        if (n in 1..7) {
                            tfOrderN = n
                            tfDenInputs = res.denCoeffs.map { formatPreciseNumber(it) }
                            tfNumInputs = listOf("0") + res.numCoeffs.map { formatPreciseNumber(it) }
                        }
                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                        showSsToTfDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка вычисления W(s)", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать W(s)", color = AccentCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showSsToTfDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }

    // --- ДИАЛОГ СИНТЕЗА НАБЛЮДАТЕЛЯ ЛЮЕНБЕРГЕРА (L) ---
    if (showObserverDialog) {
        AlertDialog(
            onDismissRequest = { showObserverDialog = false },
            title = { Text("Наблюдатель Люенбергера (L)", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                val obsAnalysis = remember(obsA, obsC) {
                    runCatching {
                        val aMat = parse(obsA)
                        val cMat = parse(obsC)
                        val eigs = math.eigenvalues(aMat)
                        val flags = math.getObservableModeFlags(aMat, cMat, eigs)
                        val r = flags.count { it }
                        Triple(eigs, flags, r)
                    }.getOrNull()
                }
                val eigenvaluesForTable = obsAnalysis?.first ?: emptyList()
                val obsFlags = obsAnalysis?.second ?: BooleanArray(obsSize) { true }
                val obsRank = obsAnalysis?.third ?: obsSize

                Column(modifier = Modifier.heightIn(max = 580.dp).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Размер n:", color = TextWhite, fontWeight = FontWeight.SemiBold)
                        CounterButton("−", obsSize > 2) {
                            if (obsSize > 2) {
                                obsSize--; tauN = obsSize
                                obsA = resizeMatrix(obsA, obsSize, obsSize)
                                obsC = resizeMatrix(obsC, 1, obsSize)
                                obsPoleAssignments = List(obsSize) { i -> obsPoleAssignments.getOrElse(i) { "-2" } }
                                matrixA = resizeMatrix(obsA, obsSize, obsSize)
                                matrixC = resizeMatrix(obsC, tauK, obsSize)
                            }
                        }
                        Text("${obsSize}×$obsSize", color = TextWhite, fontWeight = FontWeight.Bold)
                        CounterButton("+", obsSize < 7) {
                            if (obsSize < 7) {
                                obsSize++; tauN = obsSize
                                obsA = resizeMatrix(obsA, obsSize, obsSize)
                                obsC = resizeMatrix(obsC, 1, obsSize)
                                obsPoleAssignments = List(obsSize) { i -> obsPoleAssignments.getOrElse(i) { "-2" } }
                                matrixA = resizeMatrix(obsA, obsSize, obsSize)
                                matrixC = resizeMatrix(obsC, tauK, obsSize)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A (${obsSize}×$obsSize)", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(obsSize, obsSize, obsA, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        obsA = obsA.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        matrixA = resizeMatrix(obsA, obsSize, obsSize)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("C (1×$obsSize)", color = AccentAmber, fontWeight = FontWeight.Bold)
                    MatrixGridInput(1, obsSize, obsC, AccentAmber, 44.dp, 13.sp) { _, c, v ->
                        obsC = listOf(obsC[0].mapIndexed { j, old -> if (j == c) v else old })
                        matrixC = resizeMatrix(obsC, tauK, obsSize)
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    val rankStatus = when {
                        obsRank == obsSize -> "✅ Полностью наблюдаема (rank W_o = $obsRank)"
                        obsRank > 0 -> "⚠️ Частично наблюдаема: наблюдаемых мод $obsRank из $obsSize.\nНенаблюдаемые моды фиксированы!"
                        else -> "❌ Ненаблюдаема (rank W_o = 0)"
                    }
                    Text(rankStatus, color = if (obsRank == obsSize) AccentEmerald else AccentAmber, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))

                    repeat(obsSize) { index ->
                        val eig = eigenvaluesForTable.getOrNull(index)
                        val isObs = obsFlags.getOrElse(index) { true }
                        val statusTag = if (obsRank < obsSize) (if (isObs) " [набл.]" else " [ненабл.]") else ""
                        val eigText = "λ${toSubscript((index + 1).toString())} = ${eig ?: "?"}$statusTag"

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text(eigText, color = if (isObs) AccentAmber else TextDim, fontSize = 11.5.sp, fontWeight = if (isObs) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                            if (isObs) {
                                Box(
                                    modifier = Modifier.width(110.dp).clip(RoundedCornerShape(8.dp)).background(if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)).border(1.dp, AccentAmber.copy(alpha = 0.7f), RoundedCornerShape(8.dp)).padding(9.dp)
                                ) {
                                    BasicTextField(
                                        value = obsPoleAssignments.getOrElse(index) { "-2" },
                                        onValueChange = { value -> obsPoleAssignments = List(obsSize) { i -> if (i == index) value else obsPoleAssignments.getOrElse(i) { "-2" } } },
                                        textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        cursorBrush = SolidColor(AccentAmber),
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.width(110.dp).clip(RoundedCornerShape(8.dp)).background(if (appIsLightTheme) Color(0xFFE2E8F0) else Color(0xFF161F30)).border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp)).padding(9.dp)
                                ) {
                                    Text("${eig ?: "фикс."} 🔒", color = TextDim, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val aMat = parse(obsA)
                        val cMat = parse(obsC)
                        val openEigs = math.eigenvalues(aMat)
                        val flags = math.getObservableModeFlags(aMat, cMat, openEigs)
                        val r = flags.count { it }
                        val poles = List(aMat.size) { index ->
                            if (!flags[index]) openEigs[index]
                            else math.parsePoles(obsPoleAssignments.getOrElse(index) { "-2" }).single()
                        }
                        val lMat = math.luenbergerObserver(aMat, cMat, poles)
                        val clObs = math.subtract(aMat, math.multiply(lMat, cMat)!!)!!
                        val newSpec = math.eigenvalues(clObs)
                        val isDetOk = newSpec.all { it.re < -1e-7 }

                        lastResultTitle = "Наблюдатель Люенбергера (L)"
                        lastResultMatrix = lMat
                        lastResultGrid = null
                        resultScalarText = buildString {
                            if (r < aMat.size) {
                                appendLine("Разложение Калмана: rank(W_o) = $r < ${aMat.size}")
                                appendLine(if (isDetOk) "✅ Система ДЕТЕКТИРУЕМА, ошибка наблюдения затухает!" else "⚠️ Ненаблюдаемое подпространство содержит неустойчивые моды!")
                                appendLine()
                            }
                            appendLine("Спектр матрицы ошибки наблюдения (A − LC):")
                            newSpec.forEachIndexed { idx, c -> appendLine("μ${toSubscript((idx + 1).toString())} = $c") }
                            appendLine("════════════════════════════════")
                            appendLine("Матрица коэффициентов наблюдателя L (${aMat.size}×1):")
                            appendLine(math.formatMatrixAligned(lMat))
                        }
                        isError = false
                        addHistoryItem(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = lMat))
                        showObserverDialog = false
                        scrollToBottom()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка расчёта наблюдателя L", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать L", color = AccentAmber, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showObserverDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
fun MatrixSelectChip(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) accent else TextDim,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

@Composable
fun QuickActionChip(
    text: String,
    textColor: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val effectiveBg = if (appIsLightTheme) {
        when (bgColor) {
            Color(0xFF1E293B), Color(0xFF162033) -> Color(0xFFF1F5F9)
            Color(0xFF2A1621) -> Color(0xFFFEE2E2)
            else -> bgColor
        }
    } else bgColor

    val effectiveText = if (appIsLightTheme && bgColor == Color(0xFF2A1621)) {
        Color(0xFFDC2626)
    } else textColor

    val effectiveBorder = if (appIsLightTheme && (bgColor == Color(0xFF1E293B) || bgColor == Color(0xFF162033))) {
        Color(0xFFCBD5E1)
    } else effectiveText.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(effectiveBg)
            .border(1.dp, effectiveBorder, RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = effectiveText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CounterButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val bgCol = if (!enabled) Color.Transparent else if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val borderCol = if (enabled) AccentCyan.copy(alpha = 0.5f) else if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgCol)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = if (enabled) TextWhite else TextDim.copy(alpha = 0.35f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AdaptiveBtn(text: String, modifier: Modifier = Modifier, colorType: Int = 0, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val prettyLabel = remember(text) { prettifyMathText(text) }

    val bgColor = if (appIsLightTheme) {
        when (colorType) {
            2 -> Color(0xFFFAF5FF)
            else -> Color(0xFFF0F9FF)
        }
    } else {
        when (colorType) {
            2 -> Color(0xFF1F1B44)
            else -> Color(0xFF192841)
        }
    }
    val borderColor = if (appIsLightTheme) {
        when (colorType) {
            2 -> Color(0xFFD8B4FE)
            else -> Color(0xFF7DD3FC)
        }
    } else {
        when (colorType) {
            2 -> AccentPurple.copy(alpha = 0.6f)
            else -> AccentCyan.copy(alpha = 0.6f)
        }
    }
    val txtColor = if (appIsLightTheme) {
        when (colorType) {
            2 -> Color(0xFF6D28D9)
            else -> Color(0xFF0369A1)
        }
    } else {
        when (colorType) {
            2 -> Color(0xFFD2C4FF)
            else -> AccentCyan
        }
    }

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor),
        shape = RoundedCornerShape(11.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            prettyLabel,
            color = txtColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun CategoryPill(
    text: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) TextWhite else TextDim,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HistoryBottomSheetContent(
    historyList: List<HistoryItem>,
    onClearHistory: () -> Unit,
    onTogglePin: ((HistoryItem) -> Unit)? = null,
    onRestoreSystemAndResult: ((HistoryItem) -> Unit)? = null,
    onPasteToSquareA: (Array<DoubleArray>) -> Unit,
    onPasteToSquareB: (Array<DoubleArray>) -> Unit,
    onPasteToSquareC: (Array<DoubleArray>) -> Unit
) {
    val context = LocalContext.current
    var itemForExport by remember { mutableStateOf<HistoryItem?>(null) }

    val cardBg = if (appIsLightTheme) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val cardBorder = if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D)
    val chipBg = if (appIsLightTheme) Color(0xFFE2E8F0) else Color(0xFF1E293B)
    val pdfChipBg = if (appIsLightTheme) Color(0xFFE0F2FE) else Color(0xFF192841)
    val latexBtnBg = if (appIsLightTheme) Color(0xFFF3E8FF) else Color(0xFF1F1B44)
    val trashBg = if (appIsLightTheme) Color(0xFFFEE2E2) else Color(0xFF2D1622)

    fun cleanHistoryStr(raw: String): String {
        return prettifyMathText(raw)
            .replace(Regex("\\s*\\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\\]]+\\]"), "")
            .replace(Regex("\\[ПФ:\\s*([^/]+?)\\s*///\\s*([^\\]]+?)\\]")) { m -> "(${m.groupValues[1].trim()}) / (${m.groupValues[2].trim()})" }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("История вычислений", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("👁 открывает задачу и ответ • ⭐ закрепляет", color = TextDim, fontSize = 11.5.sp)
            }
            if (historyList.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(trashBg)
                        .border(1.dp, Color(0xFFF87171).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { onClearHistory() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑️", fontSize = 16.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("История пуста.\nВыполните любую операцию!", color = TextDim, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(historyList.size, key = { historyList[it].id }) { index ->
                    val item = historyList[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(if (item.pinned) 1.4.dp else 1.dp, if (item.pinned) AccentAmber else cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (if (item.pinned) "⭐ " else "") + prettifyMathText(item.title),
                                    color = if (item.pinned) AccentAmber else AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.time, color = TextDim, fontSize = 10.5.sp)
                                    if (onTogglePin != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(chipBg)
                                                .border(1.dp, cardBorder, RoundedCornerShape(6.dp))
                                                .clickable { onTogglePin(item) }
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text(if (item.pinned) "⭐" else "☆", color = if (item.pinned) AccentAmber else TextDim, fontSize = 12.sp)
                                        }
                                    }
                                    if (onRestoreSystemAndResult != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AccentEmerald.copy(alpha = 0.16f))
                                                .border(1.dp, AccentEmerald, RoundedCornerShape(6.dp))
                                                .clickable { onRestoreSystemAndResult(item) }
                                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                        ) {
                                            Text("👁", fontSize = 12.sp)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(chipBg)
                                            .border(1.dp, cardBorder, RoundedCornerShape(6.dp))
                                            .clickable {
                                                val txt = cleanHistoryStr(item.resultText)
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Matrix Result", txt))
                                                Toast.makeText(context, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    ) {
                                        Text("📋", fontSize = 12.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(pdfChipBg)
                                            .border(1.dp, AccentCyan.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                            .clickable { itemForExport = item }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("📤 PDF", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                cleanHistoryStr(item.resultText),
                                color = TextWhite,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                            if (item.resultMatrix != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.16f)),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = { onPasteToSquareA(item.resultMatrix) }
                                    ) { Text("➔ в A", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    Button(
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.16f)),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentPurple),
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = { onPasteToSquareB(item.resultMatrix) }
                                    ) { Text("➔ в B", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    Button(
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.16f)),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentAmber),
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = { onPasteToSquareC(item.resultMatrix) }
                                    ) { Text("➔ в C", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }

    itemForExport?.let { targetItem ->
        val rawForExport = targetItem.resultText
        AlertDialog(
            onDismissRequest = { itemForExport = null },
            title = { Text("Экспорт из истории", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(prettifyMathText(targetItem.title), color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Button(
                        onClick = {
                            val itm = targetItem
                            itemForExport = null
                            exportReportToPdfAndShare(context, itm.title, rawForExport)
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = pdfChipBg),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentCyan)
                    ) {
                        Text("📕 PDF-документ с условием и формулами", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = {
                            val itm = targetItem
                            itemForExport = null
                            val latex = convertReportToLatex(prettifyMathText(itm.title), prettifyMathText(rawForExport))
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LaTeX Report", latex))
                            Toast.makeText(context, "Код LaTeX скопирован в буфер!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = latexBtnBg),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentPurple)
                    ) {
                        Text("📐 Скопировать код LaTeX (\\begin{bmatrix})", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = {
                            val itm = targetItem
                            itemForExport = null
                            val txt = "${prettifyMathText(itm.title)}\n\n${cleanHistoryStr(rawForExport)}"
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                putExtra(android.content.Intent.EXTRA_TEXT, txt)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Отправить текст решения"))
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = chipBg),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, cardBorder)
                    ) {
                        Text("📄 Отправить как обычный текст", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { itemForExport = null }) { Text("Закрыть", color = TextDim) } },
            containerColor = CardDark
        )
    }
}
@Composable
fun FormattedTableGrid(grid: List<List<String>>) {
    val rows = grid.size; val cols = if (rows > 0) grid[0].size else 0
    val hasComplex = grid.any { row -> row.any { it.contains("i") } }
    val cellWidth = if (hasComplex) 115.dp else 75.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.height((rows * 36).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            for (i in 0 until rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (j in 0 until cols) {
                        val text = grid[i][j]
                        Box(
                            modifier = Modifier.width(cellWidth).height(36.dp).padding(horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = text,
                                color = if (text == "1" && i != j) AccentCyan else TextWhite,
                                fontSize = if (hasComplex && text.length > 7) 11.sp else 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (i == j) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.height((rows * 36).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)))
    }
}

@Composable
fun HistoryMatrixGrid(matrix: Array<DoubleArray>) {
    val rows = matrix.size; if (rows == 0) return; val cols = matrix[0].size; if (cols == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.height((rows * 26).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            for (i in 0 until rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (j in 0 until cols) {
                        Box(
                            modifier = Modifier.width(70.dp).height(26.dp).padding(horizontal = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                formatNumber(matrix[i][j]),
                                color = TextWhite,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.height((rows * 26).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)))
    }
}

@Composable
fun MatrixGridInput(
    rows: Int,
    cols: Int,
    data: List<List<String>>,
    accentColor: Color,
    cellSize: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onValueChange: (Int, Int, String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.height((rows * cellSize.value * 0.95f).dp).width(4.dp).background(accentColor, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f))) {
            for (i in 0 until rows) {
                Row(modifier = Modifier.animateContentSize()) {
                    for (j in 0 until cols) {
                        key(i, j) {
                            val cellVal = if (i < data.size && j < data[i].size) data[i][j] else "0"
                            var isFocused by remember { mutableStateOf(false) }
                            val dynamicFontSize = when {
                                cellVal.length > 5 -> 10.sp
                                cellVal.length > 3 -> 12.sp
                                else -> fontSize
                            }
                            val defaultBorder = if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D)

                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardDark)
                                    .border(
                                        if (isFocused) 1.5.dp else 1.dp,
                                        if (isFocused) accentColor else defaultBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .onFocusChanged { focusState ->
                                        isFocused = focusState.isFocused
                                        if (focusState.isFocused && cellVal == "0") {
                                            onValueChange(i, j, "")
                                        } else if (!focusState.isFocused && cellVal.isEmpty()) {
                                            onValueChange(i, j, "0")
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = cellVal,
                                    onValueChange = { input: String ->
                                        if (input.isEmpty() || input.matches(Regex("^-?\\d*([./]\\d*)?$")) || input == "e" || input == "-e") {
                                            onValueChange(i, j, input)
                                        }
                                    },
                                    textStyle = TextStyle(
                                        color = TextWhite,
                                        fontSize = dynamicFontSize,
                                        textAlign = TextAlign.Center,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                    cursorBrush = SolidColor(accentColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 2.dp)
                                )
                                if (cellVal.isEmpty()) {
                                    Text("0", color = TextDim.copy(alpha = 0.4f), fontSize = 13.sp, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.height((rows * cellSize.value * 0.95f).dp).width(4.dp).background(accentColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
    }
}

@Composable
fun ResultCardBlock(
    title: String = "Результат",
    lastResultMatrix: Array<DoubleArray>?,
    lastResultGrid: List<List<String>>? = null,
    resultScalarText: String,
    isError: Boolean,
    screenWidth: Dp,
    context: Context,
    onClearResult: () -> Unit,
    onTransferToA: (Array<DoubleArray>) -> Unit,
    onTransferToB: (Array<DoubleArray>) -> Unit,
    onTransferToC: ((Array<DoubleArray>) -> Unit)? = null
) {
    val hasResult = lastResultMatrix != null || lastResultGrid != null || resultScalarText.isNotEmpty()
    var symbolicMode by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val alreadyHasFormattedMatrixInText = resultScalarText.contains("⎡") || resultScalarText.contains("⎣") || resultScalarText.contains("│")

    val displayedScalarText = remember(resultScalarText, symbolicMode) {
        prettifyMathText(resultScalarText)
    }

    val smallBtnBg = if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF1E293B)
    val smallBtnBorder = if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF334155)
    val closeBtnBg = if (appIsLightTheme) Color(0xFFFEE2E2) else Color(0xFF281A24)
    val closeBtnTxt = if (appIsLightTheme) Color(0xFFDC2626) else Color(0xFFFCA5A5)
    val errCardBg = if (appIsLightTheme) Color(0xFFFEF2F2) else Color(0xFF331016)
    val errTxtCol = if (appIsLightTheme) Color(0xFFDC2626) else Color(0xFFFCA5A5)

    fun buildCleanShareText(): String {
        val raw = when {
            lastResultGrid != null -> displayedScalarText + "\n" + lastResultGrid.joinToString("\n") { it.joinToString("\t") }
            displayedScalarText.isNotEmpty() -> displayedScalarText
            lastResultMatrix != null -> formatMatrixRaw(lastResultMatrix, symbolicMode)
            else -> displayedScalarText
        }
        return raw
            .replace(Regex("\\s*\\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\\]]+\\]"), "")
            .replace(Regex("\\[ПФ:\\s*([^/]+?)\\s*///\\s*([^\\]]+?)\\]")) { m -> "(${m.groupValues[1].trim()}) / (${m.groupValues[2].trim()})" }
    }

    AnimatedVisibility(visible = hasResult, enter = fadeIn() + slideInVertically()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 28.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isError) errCardBg else CardDark),
            border = BorderStroke(1.2.dp, if (isError) Color(0xFFEF4444) else AccentEmerald)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isError) "⚠️ Ошибка" else "✅ ${prettifyMathText(title)}",
                        color = if (isError) errTxtCol else AccentEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        if ((lastResultMatrix != null || alreadyHasFormattedMatrixInText) && !isError) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (symbolicMode) AccentCyan.copy(alpha = 0.2f) else smallBtnBg)
                                    .border(1.dp, if (symbolicMode) AccentCyan else smallBtnBorder, RoundedCornerShape(7.dp))
                                    .clickable { symbolicMode = !symbolicMode }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (symbolicMode) "Дроби: ВКЛ" else "a/b · eⁿ",
                                    color = if (symbolicMode) AccentCyan else TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(smallBtnBg)
                                .border(1.dp, smallBtnBorder, RoundedCornerShape(7.dp))
                                .clickable {
                                    val txt = buildCleanShareText()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Matrix Result", txt))
                                    Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("📋", color = TextWhite, fontSize = 11.5.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(smallBtnBg)
                                .border(1.dp, smallBtnBorder, RoundedCornerShape(7.dp))
                                .clickable { showExportDialog = true }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("📤", color = AccentCyan, fontSize = 11.5.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(closeBtnBg)
                                .border(1.dp, closeBtnTxt.copy(alpha = 0.4f), RoundedCornerShape(7.dp))
                                .clickable { onClearResult() }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("✕", color = closeBtnTxt, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (displayedScalarText.isNotEmpty()) {
                    RichReportComposeView(
                        reportText = displayedScalarText,
                        screenWidth = screenWidth,
                        symbolicMode = symbolicMode,
                        context = context,
                        onTransferToA = onTransferToA,
                        onTransferToB = onTransferToB,
                        onTransferToC = onTransferToC
                    )
                }

                if (lastResultGrid != null) {
                    FormattedTableGrid(lastResultGrid)
                } else if (lastResultMatrix != null && !alreadyHasFormattedMatrixInText) {
                    MatrixResultGridAdaptive(
                        matrix = lastResultMatrix,
                        screenWidth = screenWidth,
                        symbolicMode = symbolicMode,
                        isExpMode = title.contains("Экспонента")
                    )
                }

                if (lastResultMatrix != null && !isError && !alreadyHasFormattedMatrixInText) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF24334D), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Перенести матрицу (${lastResultMatrix.size}×${lastResultMatrix[0].size}) в:", color = TextDim, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.16f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentCyan),
                            contentPadding = PaddingValues(0.dp),
                            onClick = { onTransferToA(lastResultMatrix) }
                        ) { Text("➔ в A", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        Button(
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.16f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentPurple),
                            contentPadding = PaddingValues(0.dp),
                            onClick = { onTransferToB(lastResultMatrix) }
                        ) { Text("➔ в B", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        if (onTransferToC != null) {
                            Button(
                                modifier = Modifier.weight(1f).height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.16f)),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, AccentAmber),
                                contentPadding = PaddingValues(0.dp),
                                onClick = { onTransferToC(lastResultMatrix) }
                            ) { Text("➔ в C", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        val pdfBtnBg = if (appIsLightTheme) Color(0xFFF0F9FF) else Color(0xFF192841)
        val latexBtnBg = if (appIsLightTheme) Color(0xFFFAF5FF) else Color(0xFF1F1B44)
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Экспорт и отправка решения", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Выберите формат документа:", color = TextDim, fontSize = 12.5.sp)
                    fun resolveCurrentExportText(): String {
                        if (resultScalarText.contains("ИСХОДНЫЕ ДАННЫЕ (ДАНО):")) return resultScalarText
                        val matchingHist = HistoryStorage.loadHistory(context)
                            .filter { it.title == title }
                            .maxByOrNull { it.id }
                            ?.resultText ?: ""
                        return when {
                            matchingHist.contains("ИСХОДНЫЕ ДАННЫЕ (ДАНО):") -> matchingHist
                            resultScalarText.isNotEmpty() -> resultScalarText
                            lastResultMatrix != null -> formatMatrixAligned(lastResultMatrix, true)
                            else -> ""
                        }
                    }
                    Button(
                        onClick = {
                            showExportDialog = false
                            exportReportToPdfAndShare(context, title, resolveCurrentExportText())
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = pdfBtnBg),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentCyan)
                    ) {
                        Text("📕 PDF-документ с формулами (.pdf)", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = {
                            showExportDialog = false
                            val fullRaw = resolveCurrentExportText()
                            val latex = convertReportToLatex(prettifyMathText(title), prettifyMathText(fullRaw))
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("LaTeX Report", latex))
                            Toast.makeText(context, "Код LaTeX скопирован в буфер!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = latexBtnBg),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentPurple)
                    ) {
                        Text("📐 Скопировать код LaTeX (\\begin{bmatrix})", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                    Button(
                        onClick = {
                            showExportDialog = false
                            val txt = "${prettifyMathText(title)}\n\n${buildCleanShareText()}"
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                putExtra(android.content.Intent.EXTRA_TEXT, txt)
                                type = "text/plain"
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Отправить текст решения"))
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = smallBtnBg),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, smallBtnBorder)
                    ) {
                        Text("📄 Отправить как обычный текст", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Закрыть", color = TextDim) } },
            containerColor = CardDark
        )
    }
}
@Composable
fun RichReportComposeView(
    reportText: String,
    screenWidth: Dp,
    symbolicMode: Boolean = false,
    context: Context? = null,
    onTransferToA: ((Array<DoubleArray>) -> Unit)? = null,
    onTransferToB: ((Array<DoubleArray>) -> Unit)? = null,
    onTransferToC: ((Array<DoubleArray>) -> Unit)? = null
) {
    val sections = remember(reportText) {
        reportText.split(Regex("[═─]{6,}")).map { it.trim() }.filter { it.isNotEmpty() }
    }
    var selectedSecIdx by remember(reportText) { mutableIntStateOf(-1) }
    val displayedSections = if (selectedSecIdx in sections.indices) listOf(sections[selectedSecIdx]) else sections

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Полоска быстрых закладок по шагам большого отчёта
        if (sections.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickActionChip(
                    text = "📑 Все (${sections.size})",
                    textColor = if (selectedSecIdx == -1) AccentCyan else TextDim,
                    bgColor = if (selectedSecIdx == -1) AccentCyan.copy(alpha = 0.2f) else Color(0xFF162033),
                    modifier = Modifier.width(86.dp)
                ) { selectedSecIdx = -1 }

                sections.forEachIndexed { idx, sec ->
                    val firstLine = sec.lines().firstOrNull()?.trim() ?: ""
                    val tabTitle = when {
                        firstLine.contains("СОСТОЯНИЙ") -> "Система"
                        firstLine.contains("УПРАВЛЯЕМОСТИ") -> "1) Упр."
                        firstLine.contains("НАБЛЮДАЕМОСТИ") -> "2) Набл."
                        firstLine.contains("КАЛМАНА") -> "3) Калман T"
                        firstLine.contains("ИТОГОВЫЙ") -> "Итог"
                        firstLine.contains("РЕГУЛЯТОРА") -> "Регулятор K"
                        firstLine.contains("НАБЛЮДАТЕЛЯ") -> "Наблюдатель L"
                        firstLine.contains("ГУРВИЦА") -> "2) Гурвиц"
                        firstLine.contains("ЗНАМЕНАТЕЛЬ") -> "1) Знам. A(s)"
                        firstLine.contains("ЧИСЛИТЕЛЬ") -> "2) Числ. B(s)"
                        firstLine.contains("КОЭФФИЦИЕНТ ПЕРЕДАЧИ") -> "3) Дробь W(s)"
                        else -> "Шаг ${idx + 1}"
                    }
                    QuickActionChip(
                        text = tabTitle,
                        textColor = if (selectedSecIdx == idx) AccentCyan else TextWhite,
                        bgColor = if (selectedSecIdx == idx) AccentCyan.copy(alpha = 0.2f) else Color(0xFF162033),
                        modifier = Modifier.width(102.dp)
                    ) { selectedSecIdx = idx }
                }
            }
        }

        displayedSections.forEach { sec ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))

                    .background(if (appIsLightTheme) Color(0xFFF8FAFC) else Color(0xFF0C1424))
                    .border(1.dp, if (appIsLightTheme) Color(0xFFCBD5E1) else Color(0xFF1F2E47), RoundedCornerShape(13.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val lines = sec.lines()
                var i = 0
                var lastMetaTag = ""

                while (i < lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) { i++; continue }

                    val isMatLine = { s: String ->
                        val t = s.trim()
                        t.startsWith("⎡") || t.startsWith("⎢") || t.startsWith("⎣") || t.startsWith("│") ||
                                (t.startsWith("[") && t.endsWith("]") && !t.contains(";") && !t.contains("ᵀ"))
                    }

                    if (isMatLine(line)) {
                        val matRows = mutableListOf<DoubleArray>()
                        while (i < lines.size && isMatLine(lines[i])) {
                            val rawRow = lines[i].trim()
                                .removePrefix("⎡").removePrefix("⎢").removePrefix("⎣").removePrefix("│").removePrefix("[")
                                .removeSuffix("⎤").removeSuffix("⎥").removeSuffix("⎦").removeSuffix("│").removeSuffix("]")
                                .trim()
                            val nums = rawRow.split(Regex("\\s+")).mapNotNull { parseSmartCell(it) }.toDoubleArray()
                            if (nums.isNotEmpty()) matRows.add(nums)
                            i++
                        }
                        if (matRows.isNotEmpty()) {
                            val parsedMat = matRows.toTypedArray()
                            MatrixResultGridAdaptive(
                                matrix = parsedMat,
                                screenWidth = screenWidth,
                                symbolicMode = symbolicMode,
                                isExpMode = false,
                                subspaceMeta = lastMetaTag
                            )
                            if (onTransferToA != null && onTransferToB != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                                ) {
                                    QuickActionChip("➔ в A", AccentCyan, AccentCyan.copy(alpha = 0.12f), Modifier.width(68.dp)) {
                                        onTransferToA(parsedMat)
                                    }
                                    QuickActionChip("➔ в B", AccentPurple, AccentPurple.copy(alpha = 0.12f), Modifier.width(68.dp)) {
                                        onTransferToB(parsedMat)
                                    }
                                    if (onTransferToC != null) {
                                        QuickActionChip("➔ в C", AccentAmber, AccentAmber.copy(alpha = 0.12f), Modifier.width(68.dp)) {
                                            onTransferToC(parsedMat)
                                        }
                                    }
                                    if (context != null) {
                                        QuickActionChip("📋", TextWhite, Color(0xFF1E293B), Modifier.width(42.dp)) {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Matrix", formatMatrixRaw(parsedMat, symbolicMode)))
                                            Toast.makeText(context, "Матрица скопирована!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                        lastMetaTag = ""
                        continue
                    }

                    lastMetaTag = Regex("\\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\\]]+\\]").find(line)?.value ?: ""
                    val cleanLine = line.replace(Regex("\\s*\\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\\]]+\\]"), "")

                    if (cleanLine.matches(Regex("^\\d+\\).*")) || cleanLine.endsWith("СИСТЕМА В ПРОСТРАНСТВЕ СОСТОЯНИЙ:") ||
                        cleanLine.startsWith("ПОСТРОЕНИЕ ") || cleanLine.startsWith("ИТОГОВЫЙ ВЫВОД") || cleanLine.startsWith("ПРЕОБРАЗОВАНИЕ ")
                    ) {
                        Text(
                            text = cleanLine,
                            color = AccentCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                        )
                    } else if (cleanLine.startsWith("➔") || cleanLine.startsWith("✅") || cleanLine.startsWith("❌") || cleanLine.startsWith("⚠️")) {
                        val isBad = cleanLine.contains("НЕ ") || cleanLine.startsWith("❌")
                        val isGood = cleanLine.contains("СТАБИЛИЗИРУЕМА") || cleanLine.contains("ДЕТЕКТИРУЕМА") || cleanLine.contains("УСТОЙЧИВА") || cleanLine.contains("полностью") || cleanLine.startsWith("✅")
                        val badgeColor = when {
                            isBad -> Color(0xFFF87171)
                            isGood -> AccentEmerald
                            else -> AccentAmber
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(badgeColor.copy(alpha = 0.13f))
                                .border(1.dp, badgeColor.copy(alpha = 0.45f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(text = cleanLine, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                        }
                    } else if (cleanLine.contains("[ПФ:") && cleanLine.contains("///")) {
                        val prefix = cleanLine.substringBefore("[ПФ:").trim()
                        val inside = cleanLine.substringAfter("[ПФ:").substringBeforeLast("]").trim()
                        val parts = inside.split("///").map { it.trim() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentPurple.copy(alpha = 0.14f))
                                .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = "$prefix ", color = TextWhite, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = parts.getOrElse(0) { "0" }, color = AccentEmerald, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                Box(modifier = Modifier.widthIn(min = 90.dp).fillMaxWidth(0.75f).padding(vertical = 3.dp).height(1.3.dp).background(AccentCyan))
                                Text(text = parts.getOrElse(1) { "1" }, color = AccentCyan, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    } else if (cleanLine.startsWith("u =") || cleanLine.startsWith("λ =") || cleanLine.startsWith("p(s) =")) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(AccentPurple.copy(alpha = 0.14f))
                                .border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(text = cleanLine, color = TextWhite, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        val isLabel = cleanLine.endsWith(":")
                        Text(
                            text = cleanLine,
                            color = if (isLabel) AccentAmber else TextWhite,
                            fontWeight = if (isLabel) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                    i++
                }
            }
        }
    }
}

data class SymbolicToken(
    val sign: String = "",
    val whole: String = "",
    val num: String = "",
    val den: String = "",
    val suffix: String = "",
    val plainText: String = ""
) {
    val isFraction: Boolean get() = num.isNotEmpty() && den.isNotEmpty()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MatrixResultGridAdaptive(
    matrix: Array<DoubleArray>,
    screenWidth: Dp,
    symbolicMode: Boolean = false,
    isExpMode: Boolean = false,
    subspaceMeta: String = ""
) {
    val rows = matrix.size; if (rows == 0) return; val cols = matrix[0].size; if (cols == 0) return

    val cellWidth = ((screenWidth - 68.dp) / cols).coerceIn(78.dp, 112.dp)
    val cellHeight = if (symbolicMode) 48.dp else 38.dp

    val ctrlB = Regex("\\[блоки:\\s*упр=(\\d+),\\s*неупр=(\\d+)\\]").find(subspaceMeta)
    val ctrlC = Regex("\\[стб:\\s*упр=(\\d+),\\s*неупр=(\\d+)\\]").find(subspaceMeta)
    val ctrlR = Regex("\\[стр:\\s*упр=(\\d+),\\s*неупр=(\\d+)\\]").find(subspaceMeta)
    val kalB = Regex("\\[калман:\\s*T1=(\\d+),\\s*T2=(\\d+),\\s*T3=(\\d+),\\s*T4=(\\d+)\\]").find(subspaceMeta)
    val kalC = Regex("\\[стб_калман:\\s*T1=(\\d+),\\s*T2=(\\d+),\\s*T3=(\\d+),\\s*T4=(\\d+)\\]").find(subspaceMeta)
    val kalR = Regex("\\[стр_калман:\\s*T1=(\\d+),\\s*T2=(\\d+),\\s*T3=(\\d+),\\s*T4=(\\d+)\\]").find(subspaceMeta)

    fun cellZoneColor(r: Int, c: Int): Color? {
        if (ctrlB != null) {
            val rC = ctrlB.groupValues[1].toInt()
            if (r < rC && c < rC) return AccentEmerald
            if (r >= rC && c >= rC) return Color(0xFFF87171)

        } else if (ctrlC != null) {
            val rC = ctrlC.groupValues[1].toInt()
            return if (c < rC) AccentEmerald else Color(0xFFF87171)
        } else if (ctrlR != null) {
            val rC = ctrlR.groupValues[1].toInt()
            return if (r < rC) AccentEmerald else Color(0xFFF87171)
        } else if (kalB != null || kalC != null || kalR != null) {
            val m = (kalB ?: kalC ?: kalR)!!
            val n1 = m.groupValues[1].toInt(); val n2 = m.groupValues[2].toInt(); val n3 = m.groupValues[3].toInt()
            val colZ = when { c < n1 -> 1; c < n1 + n2 -> 2; c < n1 + n2 + n3 -> 3; else -> 4 }
            val rowZ = when { r < n1 -> 1; r < n1 + n2 -> 2; r < n1 + n2 + n3 -> 3; else -> 4 }
            fun zColor(z: Int) = when (z) { 1 -> AccentEmerald; 2 -> AccentCyan; 3 -> AccentAmber; else -> Color(0xFFF87171) }
            if (kalC != null) return zColor(colZ)
            if (kalR != null) return zColor(rowZ)
            if (rowZ == colZ) return zColor(rowZ)
        }
        return null
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height((rows * (cellHeight.value + 5f)).dp)
                    .width(3.5.dp)
                    .background(AccentEmerald, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in 0 until rows) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (j in 0 until cols) {
                            val value = matrix[i][j]
                            val zCol = cellZoneColor(i, j)
                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .height(cellHeight)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(zCol?.copy(alpha = 0.16f) ?: if (appIsLightTheme) Color(0xFFF1F5F9) else Color(0xFF0B1322))
                                    .border(
                                        if (zCol != null) 1.5.dp else 1.dp,
                                        zCol ?: if (symbolicMode) AccentCyan.copy(alpha = 0.3f) else Color(0xFF1F2E47),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!symbolicMode) {
                                    val txt = formatNumber(value)
                                    Text(
                                        text = txt,
                                        color = TextWhite,
                                        fontSize = if (txt.length > 8) 11.sp else 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else {
                                    val token = remember(value, isExpMode) { toSymbolicToken(value, isExpMode) }
                                    PrettySymbolicTokenCell(token)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .height((rows * (cellHeight.value + 5f)).dp)
                    .width(3.5.dp)
                    .background(AccentEmerald, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
            )
        }

        val legendItems = remember(subspaceMeta) {
            buildList {
                if (ctrlB != null || ctrlC != null || ctrlR != null) {
                    val m = (ctrlB ?: ctrlC ?: ctrlR)!!
                    add(Pair("🟩 Управляемая (${m.groupValues[1]})", AccentEmerald))
                    add(Pair("🟥 Неуправляемая (${m.groupValues[2]})", Color(0xFFF87171)))
                } else if (kalB != null || kalC != null || kalR != null) {
                    val m = (kalB ?: kalC ?: kalR)!!
                    val n1 = m.groupValues[1].toInt(); val n2 = m.groupValues[2].toInt()
                    val n3 = m.groupValues[3].toInt(); val n4 = m.groupValues[4].toInt()
                    if (n1 > 0) add(Pair("🟩 T₁ ($n1): упр., набл.", AccentEmerald))
                    if (n2 > 0) add(Pair("🟦 T₂ ($n2): упр., ненабл.", AccentCyan))
                    if (n3 > 0) add(Pair("🟨 T₃ ($n3): неупр., набл.", AccentAmber))
                    if (n4 > 0) add(Pair("🟥 T₄ ($n4): неупр., ненабл.", Color(0xFFF87171)))
                }
            }
        }
        if (legendItems.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                legendItems.forEach { (label, col) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(col.copy(alpha = 0.14f))
                            .border(1.dp, col, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(label, color = col, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PrettySymbolicTokenCell(token: SymbolicToken) {
    if (!token.isFraction) {
        val isSpecial = token.plainText.contains("e") || token.plainText.contains("√")
        Text(
            text = token.plainText,
            color = if (isSpecial) AccentCyan else TextWhite,
            fontSize = if (token.plainText.length > 8) 11.sp else 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (token.sign.isNotEmpty() || token.whole.isNotEmpty()) {
            Text(
                text = token.sign + token.whole,
                color = TextWhite,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(end = 2.dp)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = token.num,
                color = AccentCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 10.sp,
                maxLines = 1,
                softWrap = false
            )
            Box(
                modifier = Modifier
                    .width((maxOf(token.num.length, token.den.length) * 6 + 6).dp)
                    .height(1.2.dp)
                    .background(AccentCyan)
            )
            Text(
                text = token.den,
                color = AccentCyan,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 10.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        if (token.suffix.isNotEmpty()) {
            Text(
                text = token.suffix,
                color = AccentAmber,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

fun toSymbolicToken(value: Double, isExpMode: Boolean = false): SymbolicToken {
    if (!value.isFinite()) return SymbolicToken(plainText = value.toString())
    if (abs(value) < 1e-9) return SymbolicToken(plainText = "0")

    val nearestInt = kotlin.math.round(value)
    if (abs(value - nearestInt) < 1e-6 && abs(nearestInt) < 1e9) {
        return SymbolicToken(plainText = nearestInt.toLong().toString())
    }

    fun gcd(a0: Long, b0: Long): Long {
        var a = abs(a0); var b = abs(b0)
        while (b != 0L) { val t = a % b; a = b; b = t }
        return if (a == 0L) 1L else a
    }

    fun findBestFraction(x: Double, maxQ: Long = 64L, tol: Double = 1e-6): Pair<Long, Long>? {
        val ax = abs(x)
        if (ax < 1e-7 || ax > 1e7) return null
        var h1 = 1L; var h2 = 0L
        var k1 = 0L; var k2 = 1L
        var b = ax
        for (step in 0 until 18) {
            val a = kotlin.math.floor(b).toLong()
            val h = a * h1 + h2
            val k = a * k1 + k2
            if (k > maxQ) break
            h2 = h1; h1 = h
            k2 = k1; k1 = k
            val rem = b - a
            if (rem < 1e-11) break
            b = 1.0 / rem
        }
        if (k1 <= 1L || h1 == 0L) return null
        val relErr = abs(ax - h1.toDouble() / k1.toDouble()) / maxOf(1.0, ax)
        return if (relErr <= tol) Pair(h1, k1) else null
    }

    val signStr = if (value < 0) "−" else ""
    val absVal = abs(value)

    // 1. В режиме экспоненты e^A гарантированно переводим КАЖДУЮ ячейку в форму с e^k и дробью!
    if (isExpMode) {
        val k0 = kotlin.math.round(kotlin.math.ln(absVal)).toInt().coerceIn(-20, 30)
        val localPowers = mutableListOf<Int>()
        for (d in listOf(0, 1, -1, 2, -2, 3, -3)) {
            val k = k0 + d
            if (k != 0) localPowers.add(k)
        }
        for (k in listOf(1, 2, 3, -1, -2)) {
            if (k !in localPowers) localPowers.add(k)
        }

        for (k in localPowers) {
            val ePow = kotlin.math.exp(k.toDouble())
            val eStr = if (k == 1) "e" else "e${toSuperscript(k.toString())}"
            for (q in 1L..12L) {
                val scaled = value * q.toDouble()
                for (a in listOf(1L, -1L, 2L, -2L, 3L, -3L, 4L, -4L, 5L, -5L, 6L, -6L, 8L, -8L, 9L, -9L, 12L, -12L)) {
                    if (a == 0L) continue
                    val b = kotlin.math.round(scaled - a.toDouble() * ePow).toLong()
                    if (abs(b) > 16L) continue
                    val approx = (a.toDouble() * ePow + b.toDouble()) / q.toDouble()
                    if (abs(value - approx) / maxOf(1.0, absVal) <= 1e-6) {
                        val g = gcd(gcd(a, b), q)
                        val a1 = a / g; val b1 = b / g; val q1 = q / g
                        val aTerm = when (a1) { 1L -> eStr; -1L -> "−$eStr"; else -> "$a1$eStr" }
                        val expr = when { b1 == 0L -> aTerm; b1 > 0L -> "$aTerm+$b1"; else -> "$aTerm−${abs(b1)}" }
                        return if (q1 == 1L) SymbolicToken(plainText = expr)
                        else if (b1 == 0L) SymbolicToken(sign = if (a1 < 0) "−" else "", num = abs(a1).toString(), den = q1.toString(), suffix = eStr)
                        else SymbolicToken(num = expr, den = q1.toString())
                    }
                }
            }
        }

        var bestCombo: SymbolicToken? = null
        var bestErr = 1e-3
        for (i in localPowers.indices) {
            for (j in i + 1 until localPowers.size) {
                val k1 = localPowers[i]; val k2 = localPowers[j]
                val e1 = kotlin.math.exp(k1.toDouble()); val e2 = kotlin.math.exp(k2.toDouble())
                val s1 = if (k1 == 1) "e" else "e${toSuperscript(k1.toString())}"
                val s2 = if (k2 == 1) "e" else "e${toSuperscript(k2.toString())}"
                for (q in 1L..9L) {
                    val scaled = value * q.toDouble()
                    for (a in -9L..9L) {
                        if (a == 0L) continue
                        val b = kotlin.math.round((scaled - a.toDouble() * e1) / e2).toLong()
                        if (b == 0L || abs(b) > 9L) continue
                        val approx = (a.toDouble() * e1 + b.toDouble() * e2) / q.toDouble()
                        val err = abs(value - approx) / maxOf(1.0, absVal)
                        if (err < bestErr) {
                            bestErr = err
                            val g = gcd(gcd(a, b), q)
                            val a1 = a / g; val b1 = b / g; val q1 = q / g
                            val t1 = when (a1) { 1L -> s1; -1L -> "−$s1"; else -> "$a1$s1" }
                            val t2 = when { b1 == 1L -> "+$s2"; b1 == -1L -> "−$s2"; b1 > 0 -> "+$b1$s2"; else -> "−${abs(b1)}$s2" }
                            val expr = "$t1$t2"
                            bestCombo = if (q1 == 1L) SymbolicToken(plainText = expr) else SymbolicToken(num = expr, den = q1.toString())
                        }
                    }
                }
            }
        }
        if (bestCombo != null) return bestCombo

        val kUse = if (k0 == 0) 1 else k0
        val eStr = if (kUse == 1) "e" else "e${toSuperscript(kUse.toString())}"
        val ratio = absVal / kotlin.math.exp(kUse.toDouble())
        val (pRaw, qRaw) = findBestFraction(ratio, maxQ = 32L, tol = 1e-2)
            ?: Pair(maxOf(1L, kotlin.math.round(ratio * 10.0).toLong()), 10L)
        val g = gcd(pRaw, qRaw)
        val p1 = pRaw / g; val q1 = qRaw / g
        return if (q1 == 1L) SymbolicToken(plainText = "$signStr${if (p1 == 1L) "" else p1}$eStr")
        else SymbolicToken(sign = signStr, num = p1.toString(), den = q1.toString(), suffix = eStr)
    }

    // 2. Короткие точные дроби до 16
    findBestFraction(absVal, maxQ = 16L, tol = 1e-5)?.let { (p, q) ->
        return SymbolicToken(sign = signStr, num = p.toString(), den = q.toString())
    }

    // 3. Корни √2, √3, √5, √6, √7
    if (absVal <= 30.0) {
        for (rad in listOf(2, 3, 5, 6, 7)) {
            val rVal = kotlin.math.sqrt(rad.toDouble())
            val rStr = "√$rad"
            for (q in 1L..12L) {
                val scaled = value * q.toDouble()
                for (a in -12L..12L) {
                    if (a == 0L) continue
                    val b = kotlin.math.round(scaled - a.toDouble() * rVal).toLong()
                    if (abs(b) > 12L) continue
                    val approx = (a.toDouble() * rVal + b.toDouble()) / q.toDouble()
                    if (abs(value - approx) / maxOf(1.0, absVal) <= 1e-6) {
                        val g = gcd(gcd(a, b), q)
                        val a1 = a / g; val b1 = b / g; val q1 = q / g
                        val aTerm = when (a1) { 1L -> rStr; -1L -> "−$rStr"; else -> "$a1$rStr" }
                        val expr = when {
                            b1 == 0L -> aTerm
                            b1 > 0L -> "$b1+$aTerm".replace("+-", "−").replace("+−", "−")
                            else -> "${b1}+$aTerm".replace("+-", "−").replace("+−", "−")
                        }
                        return if (q1 == 1L) SymbolicToken(plainText = expr)
                        else if (b1 == 0L) SymbolicToken(sign = if (a1 < 0) "−" else "", num = abs(a1).toString(), den = q1.toString(), suffix = rStr)
                        else SymbolicToken(num = expr, den = q1.toString())
                    }
                }
            }
        }
    }

    // 4. Для остальных рациональных чисел — точная дробь со знаменателем до 64
    findBestFraction(absVal, maxQ = 64L, tol = 1e-5)?.let { (p, q) ->
        return SymbolicToken(sign = signStr, num = p.toString(), den = q.toString())
    }

    return SymbolicToken(plainText = formatNumber(value))
}
fun formatSymbolic(value: Double): String {
    val t = toSymbolicToken(value)
    return if (!t.isFraction) {
        t.plainText
    } else {
        val w = if (t.whole.isNotEmpty()) "${t.whole} " else ""
        val sfx = if (t.suffix.isNotEmpty()) "·${t.suffix}" else ""
        "${t.sign}$w${t.num}/${t.den}$sfx"
    }
}


fun toSuperscript(exp: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '−' to '⁻', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'k' to 'ᵏ', 'm' to 'ᵐ', 'i' to 'ⁱ', 'A' to 'ᴬ', 'B' to 'ᴮ', 'C' to 'ᶜ', 'T' to 'ᵀ'
    )
    return exp.map { map[it] ?: it }.joinToString("")
}

fun toSubscript(sub: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '⁸', '9' to '₉',
        '+' to '₊', '-' to '₋', '−' to '₋', '(' to '₍', ')' to '₎',
        'i' to 'ᵢ', 'j' to 'ⱼ', 'k' to 'ₖ', 'n' to 'ₙ', 'm' to 'ₘ',
        'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ', 'r' to 'ᵣ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ', 'c' to '꜀', 'p' to 'ₚ', 's' to 'ₛ', 't' to 'ₜ'
    )
    return sub.map { map[it] ?: it }.joinToString("")
}

fun prettifyMathText(input: String): String {
    if (input.isEmpty()) return input
    var s = input
    // Заменяем степени вида ^{...}, ^(...), ^2
    s = Regex("\\^\\{([^}]+)\\}").replace(s) { m -> toSuperscript(m.groupValues[1]) }
    s = Regex("\\^\\(([^)]+)\\)").replace(s) { m -> "⁽" + toSuperscript(m.groupValues[1]) + "⁾" }
    s = Regex("\\^([+-]?[0-9nkmT]+)").replace(s) { m -> toSuperscript(m.groupValues[1]) }
    // Заменяем нижние индексы вида _{n-k}, _{1,2} и _1, _k, _n, _c, _o, _u
    s = Regex("_\\{([^}]+)\\}").replace(s) { m -> toSubscript(m.groupValues[1]) }
    s = Regex("_([0-9ijknmcoau]+)").replace(s) { m -> toSubscript(m.groupValues[1]) }
    // Красивое умножение и минус
    s = s.replace(" - ", " − ").replace("...", "…")
    return s
}

/** Конвертирует отчёт в готовый LaTeX-документ с матрицами bmatrix и дробями \frac */
fun convertReportToLatex(title: String, reportText: String): String {
    val clean = reportText.replace(Regex("\\s*\\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\\]]+\\]"), "")
    val lines = clean.lines()
    val isMatLine = { str: String ->
        val t = str.trim()
        t.startsWith("⎡") || t.startsWith("⎢") || t.startsWith("⎣") || t.startsWith("│") ||
                (t.startsWith("[") && t.endsWith("]") && !t.contains(";") && !t.contains("ᵀ"))
    }
    return buildString {
        appendLine("% Сгенерировано в MATRIX LAB PRO")
        appendLine("\\section*{$title}")
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) { i++; continue }
            if (isMatLine(line)) {
                appendLine("\\[ \\begin{bmatrix}")
                val rows = mutableListOf<String>()
                while (i < lines.size && isMatLine(lines[i])) {
                    val raw = lines[i].trim()
                        .removePrefix("⎡").removePrefix("⎢").removePrefix("⎣").removePrefix("│").removePrefix("[")
                        .removeSuffix("⎤").removeSuffix("⎥").removeSuffix("⎦").removeSuffix("│").removeSuffix("]")
                        .trim()
                    val cells = raw.split(Regex("\\s+")).map { cell ->
                        if (cell.contains("/")) {
                            val p = cell.split("/")
                            val sign = if (p[0].startsWith("-") || p[0].startsWith("−")) "-" else ""
                            "$sign\\frac{${p[0].removePrefix("-").removePrefix("−")}}{${p[1]}}"
                        } else cell
                    }
                    rows.add("  " + cells.joinToString(" & "))
                    i++
                }
                appendLine(rows.joinToString(" \\\\\n"))
                appendLine("\\end{bmatrix} \\]")
                continue
            }
            if (line.contains("[ПФ:") && line.contains("///")) {
                val pref = line.substringBefore("[ПФ:").trim()
                val ins = line.substringAfter("[ПФ:").substringBeforeLast("]").trim()
                val parts = ins.split("///").map { it.trim() }
                appendLine("\\[ $pref \\frac{${parts.getOrElse(0) { "0" }}}{${parts.getOrElse(1) { "1" }}} \\]")
            } else if (!line.startsWith("══") && !line.startsWith("──")) {
                appendLine("\\noindent ${line}\\\\[4pt]")
            }
            i++
        }
    }
}

/**
 * Рисует векторный академический PDF-документ с формулами, двухэтажными дробями и матрицами
 * и открывает системное меню отправки файла .pdf (и сохраняет в Загрузки).
 */
fun exportReportToPdfAndShare(context: Context, title: String, reportText: String) {
    try {
        val pdf = android.graphics.pdf.PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdf.startPage(pageInfo)
        var canvas = page.canvas
        var y = 48f

        val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textSize = 16f
            color = android.graphics.Color.rgb(15, 23, 42)
        }
        val secPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textSize = 12.5f
            color = android.graphics.Color.rgb(14, 116, 144)
        }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.NORMAL)
            textSize = 11.5f
            color = android.graphics.Color.rgb(30, 41, 59)
        }
        val mathPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
            textSize = 12f
            color = android.graphics.Color.rgb(15, 23, 42)
        }
        val numPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
            textSize = 11f
            color = android.graphics.Color.rgb(15, 23, 42)
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val fracSmallPaint = android.graphics.Paint(numPaint).apply { textSize = 9.2f }
        val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 41, 59)
            strokeWidth = 1.3f
            style = android.graphics.Paint.Style.STROKE
        }
        val boxFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }

        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
        canvas.drawText("MATRIX LAB PRO • Математический отчёт", 40f, y, secPaint)
        y += 22f
        canvas.drawText(prettifyMathText(title), 40f, y, titlePaint)
        y += 10f
        canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
        y += 20f

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 42f) {
                pdf.finishPage(page)
                pageNumber++
                pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)
                y = 44f
            }
        }

        val cleanText = prettifyMathText(reportText)
            .replace(Regex("\\s*\\[(блоки|стб|стр|калман|стб_калман|стр_калман):[^\\]]+\\]"), "")
        val lines = cleanText.lines()
        val isMatLine = { s: String ->
            val t = s.trim()
            t.startsWith("⎡") || t.startsWith("⎢") || t.startsWith("⎣") || t.startsWith("│") ||
                    (t.startsWith("[") && t.endsWith("]") && !t.contains(";") && !t.contains("ᵀ"))
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) { y += 6f; i++; continue }
            if (line.startsWith("══") || line.startsWith("──")) {
                ensureSpace(16f)
                y += 4f
                linePaint.color = android.graphics.Color.rgb(203, 213, 225)
                canvas.drawLine(40f, y, (pageWidth - 40).toFloat(), y, linePaint)
                linePaint.color = android.graphics.Color.rgb(30, 41, 59)
                y += 14f
                i++
                continue
            }

            // 1. Отрисовка матрицы с настоящими скобками [ ... ] и двухэтажными дробями p/q
            if (isMatLine(line)) {
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && isMatLine(lines[i])) {
                    val raw = lines[i].trim()
                        .removePrefix("⎡").removePrefix("⎢").removePrefix("⎣").removePrefix("│").removePrefix("[")
                        .removeSuffix("⎤").removeSuffix("⎥").removeSuffix("⎦").removeSuffix("│").removeSuffix("]")
                        .trim()
                    val cells = raw.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (cells.isNotEmpty()) rows.add(cells)
                    i++
                }
                if (rows.isNotEmpty()) {
                    val rCnt = rows.size
                    val cCnt = rows.maxOf { it.size }
                    val cellW = 54f
                    val cellH = 28f
                    val matW = cCnt * cellW + 16f
                    val matH = rCnt * cellH + 8f
                    ensureSpace(matH + 14f)

                    val startX = 56f
                    val topY = y
                    val botY = y + matH

                    // Левая и правая квадратные скобки матрицы (как в LaTeX bmatrix)
                    linePaint.strokeWidth = 1.6f
                    canvas.drawLine(startX, topY, startX + 7f, topY, linePaint)
                    canvas.drawLine(startX, topY, startX, botY, linePaint)
                    canvas.drawLine(startX, botY, startX + 7f, botY, linePaint)

                    val rightX = startX + matW
                    canvas.drawLine(rightX - 7f, topY, rightX, topY, linePaint)
                    canvas.drawLine(rightX, topY, rightX, botY, linePaint)
                    canvas.drawLine(rightX - 7f, botY, rightX, botY, linePaint)
                    linePaint.strokeWidth = 1.2f

                    for (ri in 0 until rCnt) {
                        for (ci in 0 until rows[ri].size) {
                            val cellStr = rows[ri][ci]
                            val cx = startX + 8f + ci * cellW + cellW / 2f
                            val cy = topY + 4f + ri * cellH + cellH / 2f
                            if (cellStr.contains("/")) {
                                val parts = cellStr.split("/")
                                val numS = parts[0]
                                val denS = parts.getOrElse(1) { "1" }
                                canvas.drawText(numS, cx, cy - 3f, fracSmallPaint)
                                canvas.drawLine(cx - 12f, cy, cx + 12f, cy, linePaint)
                                canvas.drawText(denS, cx, cy + 9.5f, fracSmallPaint)
                            } else {
                                canvas.drawText(cellStr, cx, cy + 4f, numPaint)
                            }
                        }
                    }
                    y = botY + 14f
                }
                continue
            }

            // 2. Отрисовка двухэтажной передаточной функции W(s) = B(s) / A(s)
            if (line.contains("[ПФ:") && line.contains("///")) {
                ensureSpace(46f)
                val prefix = line.substringBefore("[ПФ:").trim()
                val inside = line.substringAfter("[ПФ:").substringBeforeLast("]").trim()
                val parts = inside.split("///").map { it.trim() }
                val numStr = parts.getOrElse(0) { "0" }
                val denStr = parts.getOrElse(1) { "1" }

                boxFillPaint.color = android.graphics.Color.rgb(241, 245, 249)
                val rect = android.graphics.RectF(40f, y - 4f, (pageWidth - 40).toFloat(), y + 36f)
                canvas.drawRoundRect(rect, 8f, 8f, boxFillPaint)

                canvas.drawText(prefix, 54f, y + 20f, mathPaint)
                val prefW = mathPaint.measureText("$prefix  ")
                val fracW = maxOf(numPaint.measureText(numStr), numPaint.measureText(denStr)) + 24f
                val fCenterX = 54f + prefW + fracW / 2f

                canvas.drawText(numStr, fCenterX, y + 11f, numPaint)
                canvas.drawLine(54f + prefW, y + 15.5f, 54f + prefW + fracW, y + 15.5f, linePaint)
                canvas.drawText(denStr, fCenterX, y + 29f, numPaint)
                y += 46f
                i++
                continue
            }

            // 3. Обычные строки, заголовки и выводы
            ensureSpace(22f)
            val paintToUse = when {
                line.matches(Regex("^\\d+\\).*")) || line.endsWith("СОСТОЯНИЙ:") || line.startsWith("ПОСТРОЕНИЕ ") || line.startsWith("ПРЕОБРАЗОВАНИЕ ") -> secPaint
                line.startsWith("u =") || line.startsWith("λ =") || line.startsWith("p(s) =") -> mathPaint
                else -> textPaint
            }

            // Перенос длинных строк по ширине страницы
            val maxLineWidth = pageWidth - 84f
            var remaining = line
            while (remaining.isNotEmpty()) {
                ensureSpace(18f)
                val count = paintToUse.breakText(remaining, true, maxLineWidth, null)
                var cut = count
                if (cut < remaining.length) {
                    val lastSpace = remaining.substring(0, cut).lastIndexOf(' ')
                    if (lastSpace > 10) cut = lastSpace + 1
                }
                canvas.drawText(remaining.substring(0, cut).trim(), 42f, y + 11f, paintToUse)
                remaining = remaining.substring(cut).trim()
                y += 16.5f
            }
            i++
        }

        pdf.finishPage(page)

        val fileName = "MatrixLab_${System.currentTimeMillis() / 1000}.pdf"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out -> pdf.writeTo(out) }
                pdf.close()
                Toast.makeText(context, "PDF сохранён в Загрузки: $fileName", Toast.LENGTH_SHORT).show()
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, title)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Отправить PDF-отчёт"))
                return
            }
        }
        val fallbackDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        val outFile = java.io.File(fallbackDir, fileName)
        java.io.FileOutputStream(outFile).use { out -> pdf.writeTo(out) }
        pdf.close()
        Toast.makeText(context, "PDF сохранён: ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка создания PDF: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
fun identityStringGrid(n: Int): List<List<String>> =
    List(n) { r -> List(n) { c -> if (r == c) "1" else "0" } }

fun resizeIdentityMatrix(old: List<List<String>>, newN: Int): List<List<String>> =
    List(newN) { r ->
        List(newN) { c ->
            if (r < old.size && c < old[r].size) old[r][c] else if (r == c) "1" else "0"
        }
    }

fun characteristicPolynomialCoefficients(matrix: Array<DoubleArray>): List<Double> {
    val n = matrix.size
    require(n > 0 && matrix.all { it.size == n }) { "Для характеристического многочлена нужна квадратная матрица" }
    var b = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }
    val coefficients = mutableListOf<Double>()
    for (k in 1..n) {
        val ab = Array(n) { r ->
            DoubleArray(n) { c ->
                var sum = 0.0
                for (j in 0 until n) sum += matrix[r][j] * b[j][c]
                sum
            }
        }
        val trace = (0 until n).sumOf { ab[it][it] }
        val coefficient = -trace / k.toDouble()
        coefficients += coefficient
        b = Array(n) { r -> DoubleArray(n) { c -> ab[r][c] + if (r == c) coefficient else 0.0 } }
    }
    return coefficients
}

fun formatCharacteristicPolynomial(coefficients: List<Double>): String {
    val n = coefficients.size
    val terms = mutableListOf<String>()
    terms += "λ${toSuperscript(n.toString())}"
    coefficients.forEachIndexed { index, raw ->
        val power = n - index - 1
        val value = if (abs(raw) < 1e-10) 0.0 else raw
        if (value != 0.0) {
            val absVal = abs(value)
            val magnitude = formatSymbolic(absVal)
            val variable = when (power) {
                0 -> ""
                1 -> "λ"
                else -> "λ${toSuperscript(power.toString())}"
            }
            val sign = if (terms.isEmpty()) {
                if (value < 0) "−" else ""
            } else if (value < 0) {
                " − "
            } else {
                " + "
            }
            val showCoeff = variable.isEmpty() || abs(absVal - 1.0) > 1e-10
            terms += sign + (if (showCoeff) magnitude else "") + variable
        }
    }
    return terms.joinToString("")
}

/**
 * Автоматически определяет размерность вставляемой матрицы (строки × столбцы от 1 до 7)
 * по строкам и числам из буфера обмена, чтобы не нужно было менять размер вручную!
 */
fun parsePastedTextAutoSize(text: String): List<List<String>>? {
    try {
        val normalized = text
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace(',', '.')
            .replace("[", "\n")
            .replace("]", "\n")
            .replace("⎡", "\n").replace("⎢", "\n").replace("⎣", "\n")
            .replace("⎤", "\n").replace("⎥", "\n").replace("⎦", "\n")
            .trim()
        val numberRegex = Regex("[+-]?(?:\\d+(?:/\\d+|\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
        val rowsWithNums = normalized.lines()
            .map { line -> numberRegex.findAll(line).map { it.value }.toList() }
            .filter { it.isNotEmpty() }

        if (rowsWithNums.isEmpty()) return null
        val detectedRows = rowsWithNums.size.coerceIn(1, 7)
        val detectedCols = rowsWithNums.maxOf { it.size }.coerceIn(1, 7)

        return List(detectedRows) { r ->
            List(detectedCols) { c ->
                rowsWithNums.getOrNull(r)?.getOrNull(c) ?: "0"
            }
        }
    } catch (_: Exception) {
        return null
    }
}
fun parsePastedText(text: String, reqRows: Int, reqCols: Int): List<List<String>>? {
    try {
        val normalized = text
            .replace('−', '-')
            .replace('–', '-')
            .replace('—', '-')
            .replace(',', '.')
            .replace("[", " ")
            .replace("]", " ")
            .trim()
        val rawLines = normalized.lines().filter { it.isNotBlank() }
        val numberRegex = Regex("[+-]?(?:\\d+(?:/\\d+|\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
        val result = mutableListOf<List<String>>()
        if (rawLines.size >= reqRows) {
            for (i in 0 until reqRows) {
                val rowList = mutableListOf<String>()
                val matches = numberRegex.findAll(rawLines[i]).map { it.value }.toList()
                for (j in 0 until reqCols) {
                    if (j < matches.size) rowList.add(matches[j]) else rowList.add("0")
                }
                result.add(rowList)
            }
        } else {
            val matches = numberRegex.findAll(normalized).map { it.value }.toList()
            var index = 0
            for (r in 0 until reqRows) {
                val rowList = mutableListOf<String>()
                for (c in 0 until reqCols) {
                    if (index < matches.size) rowList.add(matches[index++]) else rowList.add("0")
                }
                result.add(rowList)
            }
        }
        return result
    } catch (_: Exception) {
        return null
    }
}

fun resizeMatrix(old: List<List<String>>, newR: Int, newC: Int): List<List<String>> {
    return List(newR) { r ->
        List(newC) { c ->
            if (r < old.size && c < old[r].size) old[r][c] else "0"
        }
    }
}

fun parseSmartCell(rawInput: String): Double? {
    val s = rawInput.trim()
        .replace('−', '-')
        .replace(',', '.')
        .replace(" ", "")
    if (s.isEmpty() || s == "-") return 0.0
    s.toDoubleOrNull()?.let { return it }

    if (s.contains("/")) {
        val parts = s.split("/")
        if (parts.size == 2) {
            val num = parseSmartCell(parts[0]) ?: return null
            val den = parseSmartCell(parts[1]) ?: return null
            if (abs(den) > 1e-15) return num / den
        }
    }
    if (s == "e") return Math.E
    if (s == "-e") return -Math.E
    if (s.startsWith("e^")) {
        val exp = s.removePrefix("e^").toDoubleOrNull() ?: return null
        return kotlin.math.exp(exp)
    }
    if (s.startsWith("√")) {
        val rad = s.removePrefix("√").toDoubleOrNull() ?: return null
        return if (rad >= 0) kotlin.math.sqrt(rad) else null
    }
    return null
}

fun parse(data: List<List<String>>): Array<DoubleArray> {
    require(data.isNotEmpty()) { "Матрица не должна быть пустой" }
    val cols = data[0].size
    require(cols > 0 && data.all { it.size == cols }) { "Матрица должна быть прямоугольной" }

    return Array(data.size) { i ->
        DoubleArray(cols) { j ->
            val value = parseSmartCell(data[i][j])
            require(value != null && value.isFinite()) {
                "Некорректное число в строке ${i + 1}, столбце ${j + 1}"
            }
            value
        }
    }
}

fun formatPreciseNumber(v: Double): String = formatFiniteNumber(v, 5)

fun formatNumber(v: Double): String = formatFiniteNumber(v, 3)

private fun formatFiniteNumber(value: Double, decimals: Int): String {
    if (!value.isFinite()) return value.toString()
    if (abs(value) < 1e-12) return "0"
    if (abs(value) >= 1e12 || abs(value) < 1e-6) {
        return String.format(Locale.US, "%.${decimals}e", value)
            .replace(Regex("0+e"), "e")
            .replace(Regex("\\.e"), "e")
    }
    val rounded = java.math.BigDecimal.valueOf(value)
        .setScale(decimals, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
    val str = rounded.toPlainString()
    return if (str == "-0" || str == "-0.0") "0" else str
}

fun matrixToPreciseStringList(matrix: Array<DoubleArray>): List<List<String>> =
    matrix.map { row -> row.map { formatPreciseNumber(it) } }

fun formatMatrixRaw(matrix: Array<DoubleArray>, symbolic: Boolean = false): String {
    return matrix.joinToString("\n") { row ->
        row.joinToString("\t") { v -> if (symbolic) formatSymbolic(v) else formatNumber(v) }
    }
}

fun formatMatrixAligned(matrix: Array<DoubleArray>, symbolic: Boolean = false): String {
    if (matrix.isEmpty()) return "[ ]"
    val formatted =
        matrix.map { row -> row.map { if (symbolic) formatSymbolic(it) else formatNumber(it) } }
    val columnCount = formatted.maxOfOrNull { it.size } ?: 0
    if (columnCount == 0) return "[ ]"
    val widths = (0 until columnCount).map { col ->
        formatted.maxOfOrNull { row -> row.getOrNull(col)?.length ?: 0 } ?: 1
    }
    val rows = formatted.map { row ->
        (0 until columnCount).joinToString("  ") { col ->
            (row.getOrNull(col) ?: "").padStart(widths[col])
        }
    }
    return rows.mapIndexed { index, row ->
        if (rows.size == 1) {
            "[ $row ]"
        } else {
            val left = when (index) {
                0 -> "⎡"
                rows.lastIndex -> "⎣"
                else -> "⎢"
            }
            val right = when (index) {
                0 -> "⎤"
                rows.lastIndex -> "⎦"
                else -> "⎥"
            }
            "$left $row $right"
        }
    }.joinToString("\n")
}

    @Suppress("UNCHECKED_CAST")
    fun MatrixMath.solveLinearSystemFullReport(
        matA: Array<DoubleArray>,
        matB: Array<DoubleArray>
    ): Pair<String, Array<DoubleArray>?> {
        fun subDigitLocal(n: Int): String {
            val map = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
            return n.toString().map { if (it in '0'..'9') map[it - '0'] else it }.joinToString("")
        }

        fun cleanFracLocal(v: Double): String {
            if (!v.isFinite()) return v.toString()
            if (abs(v) < 1e-9) return "0"
            val roundedInt = kotlin.math.round(v).toInt()
            if (abs(v - roundedInt) < 1e-5) return roundedInt.toString()
            val sign = if (v < 0) "-" else ""
            val av = abs(v)
            for (den in 2..24) {
                val num = kotlin.math.round(av * den).toInt()
                if (abs(av - num.toDouble() / den.toDouble()) < 1e-4) {
                    var a = num; var b = den
                    while (b != 0) { val t = a % b; a = b; b = t }
                    val n1 = num / a; val d1 = den / a
                    return if (d1 == 1) "$sign$n1" else "$sign$n1/$d1"
                }
            }
            return String.format(Locale.US, "%.3f", v).trimEnd('0').trimEnd('.')
        }

        val m = matA.size
        val n = matA[0].size
        val bCol = DoubleArray(m) { r -> if (r < matB.size && matB[r].isNotEmpty()) matB[r][0] else 0.0 }
        val aug = Array(m) { r -> DoubleArray(n + 1) { c -> if (c < n) matA[r][c] else bCol[r] } }

        val rA = rank(matA)
        val rAb = rank(aug)

        val rref = Array(m) { r -> aug[r].clone() }
        val maxVal = rref.maxOf { row -> row.maxOf { abs(it) } }.coerceAtLeast(1.0)
        val tol = 1e-7 * maxVal
        val pivotCols = mutableListOf<Int>()
        var pivRow = 0

        for (col in 0 until n) {
            if (pivRow >= m) break
            var best = pivRow
            for (r in pivRow + 1 until m) {
                if (abs(rref[r][col]) > abs(rref[best][col])) best = r
            }
            if (abs(rref[best][col]) <= tol) continue
            val tmp = rref[pivRow]; rref[pivRow] = rref[best]; rref[best] = tmp
            val pv = rref[pivRow][col]
            for (j in col..n) rref[pivRow][j] /= pv
            for (r in 0 until m) {
                if (r == pivRow) continue
                val f = rref[r][col]
                if (abs(f) > 0.0) {
                    for (j in col..n) rref[r][j] -= f * rref[pivRow][j]
                }
            }
            pivotCols.add(col)
            pivRow++
        }

        for (r in 0 until m) for (c in 0..n) if (abs(rref[r][c]) < 1e-9) rref[r][c] = 0.0

        val x0 = DoubleArray(n)
        for (idx in pivotCols.indices) {
            x0[pivotCols[idx]] = rref[idx][n]
        }
        val x0Mat = Array(n) { i -> doubleArrayOf(x0[i]) }

        val report = buildString {
            appendLine("РЕШЕНИЕ СЛАУ A·X = B (A: ${m}×$n, B: ${m}×1):")
            appendLine("Расширенная матрица системы [A | B]:")
            appendLine(formatMatrixAligned(aug))
            appendLine("Приведённая ступенчатая форма Гаусса — Жордана (RREF):")
            appendLine(formatMatrixAligned(rref))
            appendLine("════════════════════════════════")
            appendLine("1) ПРОВЕРКА ПО ТЕОРЕМЕ КРОНЕКЕРА — КАПЕЛЛИ:")
            appendLine("• Ранг матрицы коэффициентов: rank(A) = $rA")
            appendLine("• Ранг расширенной матрицы: rank(A | B) = $rAb")
            appendLine("• Число неизвестных: n = $n")

            if (rA < rAb) {
                appendLine("❌ Так как rank(A) < rank(A | B), по теореме Кронекера — Капелли система НЕСОВМЕСТНА (решений нет).")
                return Pair(this.toString(), null)
            }

            if (rA == n) {
                appendLine("✅ Так как rank(A) = rank(A | B) = n, система совместна и определённа (имеет ЕДИНСТВЕННОЕ решение).")
                appendLine("════════════════════════════════")
                appendLine("2) ЕДИНСТВЕННОЕ РЕШЕНИЕ СИСТЕМЫ X:")
                appendLine("Вектор-столбец решения X:")
                appendLine(formatMatrixAligned(x0Mat))
                x0.forEachIndexed { i, v -> appendLine("• x${subDigitLocal(i + 1)} = ${cleanFracLocal(v)}") }
                return Pair(this.toString(), x0Mat)
            }

            val pivSet = pivotCols.toSet()
            val freeCols = (0 until n).filter { it !in pivSet }
            val fsrVectors = freeCols.map { fc ->
                val v = DoubleArray(n)
                v[fc] = 1.0
                for (idx in pivotCols.indices) {
                    v[pivotCols[idx]] = if (abs(rref[idx][fc]) < 1e-10) 0.0 else -rref[idx][fc]
                }
                v
            }

            appendLine("⚠️ Так как rank(A) = rank(A | B) = $rA < n ($n), система совместна и неопределённа (БЕСКОНЕЧНО МНОГО решений).")
            appendLine("• Главные переменные (${pivotCols.size}): ${pivotCols.joinToString(", ") { "x${subDigitLocal(it + 1)}" }}")
            appendLine("• Свободные переменные (${freeCols.size}): ${freeCols.joinToString(", ") { "x${subDigitLocal(it + 1)}" }}")
            appendLine("════════════════════════════════")
            appendLine("2) ЧАСТНОЕ РЕШЕНИЕ И ФУНДАМЕНТАЛЬНАЯ СИСТЕМА РЕШЕНИЙ (ФСР):")
            appendLine("Частное решение неоднородной системы X_частн (при свободных = 0):")
            appendLine(formatMatrixAligned(x0Mat))
            fsrVectors.forEachIndexed { k, vec ->
                appendLine("Базисный вектор ФСР V${subDigitLocal(k + 1)} (при x${subDigitLocal(freeCols[k] + 1)} = 1):")
                appendLine(formatMatrixAligned(Array(n) { r -> doubleArrayOf(vec[r]) }))
            }
            appendLine("════════════════════════════════")
            appendLine("3) ОБЩЕЕ РЕШЕНИЕ СЛАУ:")
            appendLine("X = X_частн + ${fsrVectors.indices.joinToString(" + ") { k -> "c${subDigitLocal(k + 1)}·V${subDigitLocal(k + 1)}" }}")
        }

        return Pair(report, x0Mat)
    }

@Suppress("UNCHECKED_CAST")
fun MatrixMath.evaluateMatrixFormula(
    exprStr: String,
    matA: Array<DoubleArray>,
    matB: Array<DoubleArray>,
    matC: Array<DoubleArray>
): Array<DoubleArray> {
    val inferredN = when {
        exprStr.contains("A", ignoreCase = true) && matA.size == matA[0].size -> matA.size
        exprStr.contains("B", ignoreCase = true) && matB.size == matB[0].size -> matB.size
        exprStr.contains("C", ignoreCase = true) && matC.size == matC[0].size -> matC.size
        else -> matA.size
    }
    val n = inferredN
    val matI = identityMatrix(n)

        fun asMat(v: Any, rows: Int = n, cols: Int = n): Array<DoubleArray> {
            if (v is Array<*>) return v as Array<DoubleArray>
            val d = v as Double
            require(rows == cols) { "Скаляр $d можно складывать только с квадратной матрицей (как k·I)" }
            return multiplyScalar(identityMatrix(rows), d)
        }

        fun addV(a: Any, b: Any): Any {
            if (a is Double && b is Double) return a + b
            val mB = if (b is Array<*>) (b as Array<DoubleArray>) else null
            val mA = if (a is Array<*>) (a as Array<DoubleArray>) else null
            val left = mA ?: asMat(a, mB!!.size, mB[0].size)
            val right = mB ?: asMat(b, mA!!.size, mA[0].size)
            return add(left, right) ?: throw IllegalArgumentException("Несовместимые размеры матриц для сложения")
        }

        fun subV(a: Any, b: Any): Any {
            if (a is Double && b is Double) return a - b
            val mB = if (b is Array<*>) (b as Array<DoubleArray>) else null
            val mA = if (a is Array<*>) (a as Array<DoubleArray>) else null
            val left = mA ?: asMat(a, mB!!.size, mB[0].size)
            val right = mB ?: asMat(b, mA!!.size, mA[0].size)
            return subtract(left, right) ?: throw IllegalArgumentException("Несовместимые размеры матриц для вычитания")
        }

        fun mulV(a: Any, b: Any): Any {
            if (a is Double && b is Double) return a * b
            if (a is Double) return multiplyScalar(asMat(b), a)
            if (b is Double) return multiplyScalar(asMat(a), b)
            val left = asMat(a); val right = asMat(b)
            return multiply(left, right)
                ?: throw IllegalArgumentException("Несовместимые размеры для умножения (${left.size}×${left[0].size} и ${right.size}×${right[0].size})")
        }

        var s = exprStr.replace('−', '-').replace('–', '-').replace('×', '*').replace(" ", "")
        s = Regex("(\\d)(?=[ABCIEabcie(])").replace(s, "$1*")
        s = Regex("([ABCIE)])(?=[ABCIE(])").replace(s, "$1*")

        val tokens = mutableListOf<String>()
        var idx = 0
        while (idx < s.length) {
            val ch = s[idx]
            when {
                ch in "+-*/^()" -> { tokens.add(ch.toString()); idx++ }
                ch.isDigit() || ch == '.' -> {
                    val sb = StringBuilder()
                    while (idx < s.length && (s[idx].isDigit() || s[idx] == '.')) sb.append(s[idx++])
                    tokens.add(sb.toString())
                }
                ch.isLetter() -> {
                    val sb = StringBuilder()
                    while (idx < s.length && s[idx].isLetter()) sb.append(s[idx++])
                    tokens.add(sb.toString())
                }
                else -> throw IllegalArgumentException("Недопустимый символ в формуле: $ch")
            }
        }

        val parser = object {
            var pos = 0
            fun peek(): String? = tokens.getOrNull(pos)
            fun consume(): String? = tokens.getOrNull(pos++)

            fun parseExpr(): Any {
                var left = parseTerm()
                while (peek() == "+" || peek() == "-") {
                    val op = consume()
                    val right = parseTerm()
                    left = if (op == "+") addV(left, right) else subV(left, right)
                }
                return left
            }

            fun parseTerm(): Any {
                var left = parseUnary()
                while (peek() == "*" || peek() == "/") {
                    val op = consume()
                    val right = parseUnary()
                    left = if (op == "*") mulV(left, right) else {
                        require(right is Double && abs(right) > 1e-15) { "Делить можно только на ненулевое число" }
                        mulV(left, 1.0 / right)
                    }
                }
                return left
            }

            fun parseUnary(): Any {
                if (peek() == "+") { consume(); return parseUnary() }
                if (peek() == "-") { consume(); return mulV(-1.0, parseUnary()) }
                var base = parsePrimary()
                while (peek() == "^") {
                    consume()
                    var sign = 1
                    if (peek() == "-") { consume(); sign = -1 } else if (peek() == "+") consume()
                    val expTok = consume() ?: throw IllegalArgumentException("Ожидается степень после ^")
                    if (expTok.equals("T", ignoreCase = true)) {
                        base = if (base is Double) base else transpose(asMat(base))
                    } else {
                        val p = (expTok.toIntOrNull() ?: throw IllegalArgumentException("Некорректная степень: $expTok")) * sign
                        base = if (base is Double) Math.pow(base, p.toDouble()) else (matrixPower(asMat(base), p)
                            ?: throw ArithmeticException("Матрица не обратима для степени $p"))
                    }
                }
                return base
            }

            fun parsePrimary(): Any {
                val tok = consume() ?: throw IllegalArgumentException("Неожиданный конец формулы")
                if (tok == "(") {
                    val v = parseExpr()
                    require(consume() == ")") { "Пропущена закрывающая скобка ')'" }
                    return v
                }
                tok.toDoubleOrNull()?.let { return it }
                return when (tok.uppercase()) {
                    "A" -> matA
                    "B" -> matB
                    "C" -> matC
                    "I", "E" -> matI
                    "INV", "T" -> {
                        require(consume() == "(") { "После $tok ожидается '('" }
                        val arg = asMat(parseExpr())
                        require(consume() == ")") { "Пропущена ')'" }
                        if (tok.equals("T", true)) transpose(arg) else (inverse(arg)
                            ?: throw ArithmeticException("Матрица вырождена"))
                    }
                    else -> throw IllegalArgumentException("Неизвестное имя в формуле: $tok")
                }
            }
        }

        val res = parser.parseExpr()
        require(parser.pos == tokens.size) { "Ошибка разбора формулы возле: ${tokens.drop(parser.pos).joinToString("")}" }
        return if (res is Double) arrayOf(doubleArrayOf(res)) else asMat(res)
    }
