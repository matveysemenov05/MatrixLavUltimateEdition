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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

// Unified Matrix Lab design tokens — aligned with the web edition.
val BgDark = Color(0xFF080D18)
val CardDark = Color(0xFF111A2B)
val AccentCyan = Color(0xFF54E5E5)
val AccentPurple = Color(0xFFB69AFF)
val AccentAmber = Color(0xFFFFD166)
val AccentEmerald = Color(0xFF70E1A1)
val TextWhite = Color(0xFFEDF4FF)
val TextDim = Color(0xFF9BABC5)

data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val resultText: String,
    val resultMatrix: Array<DoubleArray>?,
    val time: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HistoryItem
        if (id != other.id) return false
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

    fun saveHistory(context: Context, list: List<HistoryItem>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val serialized = list.take(MAX_HISTORY_ITEMS).joinToString("###ITEM###") { item ->
            val matStr = item.resultMatrix?.let { matrixToString(it) } ?: ""
            "${item.id}@@@${item.title}@@@${item.resultText}@@@${item.time}@@@$matStr"
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
                    HistoryItem(id = p[0].toLongOrNull() ?: 0L, title = p[1], resultText = p[2], time = p[3], resultMatrix = mat)
                } else null
            }
        } catch (_: Exception) { emptyList() }
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
        } catch (_: Exception) { null }
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
    var appMode by rememberSaveable { mutableIntStateOf(0) }

    val historyList = remember { mutableStateListOf<HistoryItem>() }
    LaunchedEffect(Unit) { historyList.addAll(HistoryStorage.loadHistory(context)) }

    fun addHistoryItem(item: HistoryItem) {
        historyList.add(0, item)
        while (historyList.size > 200) historyList.removeAt(historyList.lastIndex)
        HistoryStorage.saveHistory(context, historyList)
    }
    fun clearAllHistory() {
        historyList.clear()
        HistoryStorage.saveHistory(context, emptyList())
    }

    var showHistorySheet by remember { mutableStateOf(false) }

    var squareSize by rememberSaveable { mutableIntStateOf(3) }
    var squareSelectedTab by rememberSaveable { mutableIntStateOf(0) }
    var squareMatrixA by rememberSaveable { mutableStateOf(List(3) { List(3) { "0" } }) }
    var squareMatrixB by rememberSaveable { mutableStateOf(List(3) { List(3) { "0" } }) }
    var squareMatrixC by rememberSaveable { mutableStateOf(List(3) { List(3) { "0" } }) }
    var squareScalarK by rememberSaveable { mutableStateOf("1") }

    var rectSelectedTab by rememberSaveable { mutableIntStateOf(0) }
    var rectRowsA by rememberSaveable { mutableIntStateOf(2) }
    var rectColsA by rememberSaveable { mutableIntStateOf(3) }
    var rectMatrixA by rememberSaveable { mutableStateOf(List(2) { List(3) { "0" } }) }
    var rectRowsB by rememberSaveable { mutableIntStateOf(3) }
    var rectColsB by rememberSaveable { mutableIntStateOf(2) }
    var rectMatrixB by rememberSaveable { mutableStateOf(List(3) { List(2) { "0" } }) }
    var rectScalarK by rememberSaveable { mutableStateOf("1") }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF14233B), BgDark, Color(0xFF070B14))))) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "MATRIX LAB", fontSize = 23.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp, color = TextWhite)
                    Text(text = "THEORY OF CONTROL • MOBILE EDITION", fontSize = 10.sp, color = AccentCyan, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                }
                FilledTonalButton(
                    onClick = { showHistorySheet = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardDark),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24334D)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) { Text(text = "🕒 ${historyList.size}", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(14.dp)).background(CardDark).padding(5.dp)) {
                TabButton("Квадратные (A, B, C)", appMode == 0, Modifier.weight(1f)) { appMode = 0 }
                TabButton("Прямоугольные / Векторы", appMode == 1, Modifier.weight(1f)) { appMode = 1 }
            }

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = appMode,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width / 2 } + fadeOut())
                    } else {
                        (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(slideOutHorizontally { width -> width / 2 } + fadeOut())
                    }
                },
                label = "ScreenTransition"
            ) { mode ->
                if (mode == 0) {
                    SquareMatricesScreen(
                        size = squareSize,
                        onSizeChange = { newSize ->
                            if (newSize in 2..7) {
                                squareSize = newSize
                                squareMatrixA = resizeMatrix(squareMatrixA, newSize, newSize)
                                squareMatrixB = resizeMatrix(squareMatrixB, newSize, newSize)
                                squareMatrixC = resizeMatrix(squareMatrixC, newSize, newSize)
                            }
                        },
                        selectedTab = squareSelectedTab,
                        onTabChange = { squareSelectedTab = it },
                        matrixA = squareMatrixA, onMatrixAChange = { squareMatrixA = it },
                        matrixB = squareMatrixB, onMatrixBChange = { squareMatrixB = it },
                        matrixC = squareMatrixC, onMatrixCChange = { squareMatrixC = it },
                        scalarK = squareScalarK, onScalarKChange = { squareScalarK = it },
                        onAddToHistory = { addHistoryItem(it) }
                    )
                } else {
                    RectangularMatricesScreen(
                        selectedTab = rectSelectedTab,
                        onTabChange = { rectSelectedTab = it },
                        rowsA = rectRowsA, colsA = rectColsA,
                        onSizeAChange = { r, c ->
                            if (r in 1..7 && c in 1..7) { rectRowsA = r; rectColsA = c; rectMatrixA = resizeMatrix(rectMatrixA, r, c) }
                        },
                        matrixA = rectMatrixA, onMatrixAChange = { rectMatrixA = it },
                        rowsB = rectRowsB, colsB = rectColsB,
                        onSizeBChange = { r, c ->
                            if (r in 1..7 && c in 1..7) { rectRowsB = r; rectColsB = c; rectMatrixB = resizeMatrix(rectMatrixB, r, c) }
                        },
                        matrixB = rectMatrixB, onMatrixBChange = { rectMatrixB = it },
                        scalarK = rectScalarK, onScalarKChange = { rectScalarK = it },
                        onAddToHistory = { addHistoryItem(it) }
                    )
                }
            }
        }

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
                    onPasteToSquareA = { mat ->
                        if (mat.size == mat[0].size && mat.size in 2..7) { squareSize = mat.size; squareMatrixA = matrixToPreciseStringList(mat); showHistorySheet = false } else Toast.makeText(context, "Нужна квадратная матрица 2×2–7×7", Toast.LENGTH_LONG).show()
                    },
                    onPasteToSquareB = { mat ->
                        if (mat.size == mat[0].size && mat.size in 2..7) { squareSize = mat.size; squareMatrixB = matrixToPreciseStringList(mat); showHistorySheet = false } else Toast.makeText(context, "Нужна квадратная матрица 2×2–7×7", Toast.LENGTH_LONG).show()
                    },
                    onPasteToSquareC = { mat ->
                        if (mat.size == mat[0].size && mat.size in 2..7) { squareSize = mat.size; squareMatrixC = matrixToPreciseStringList(mat); showHistorySheet = false } else Toast.makeText(context, "Нужна квадратная матрица 2×2–7×7", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquareMatricesScreen(
    size: Int, onSizeChange: (Int) -> Unit,
    selectedTab: Int, onTabChange: (Int) -> Unit,
    matrixA: List<List<String>>, onMatrixAChange: (List<List<String>>) -> Unit,
    matrixB: List<List<String>>, onMatrixBChange: (List<List<String>>) -> Unit,
    matrixC: List<List<String>>, onMatrixCChange: (List<List<String>>) -> Unit,
    scalarK: String, onScalarKChange: (String) -> Unit,
    onAddToHistory: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val math = remember { MatrixMath() }
    val screenWidth = configuration.screenWidthDp.dp

    var lastResultMatrix by remember { mutableStateOf<Array<DoubleArray>?>(null) }
    var lastResultGrid by remember { mutableStateOf<List<List<String>>?>(null) }
    var resultScalarText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    fun showMatrixResult(
        result: Array<DoubleArray>?,
        operationName: String
    ) {
        lastResultGrid = null

        if (result == null) {
            lastResultMatrix = null
            resultScalarText =
                "Не удалось выполнить операцию \"$operationName\".\n" +
                        "Проверьте размеры матриц и допустимость операции."
            isError = true
            return
        }

        if (result.any { row -> row.any { !it.isFinite() } }) {
            lastResultMatrix = null
            resultScalarText =
                "Ошибка вычисления \"$operationName\".\n" +
                        "Результат содержит NaN или бесконечность."
            isError = true
            return
        }

        lastResultMatrix = result
        resultScalarText = ""
        isError = false

        onAddToHistory(
            HistoryItem(
                title = operationName,
                resultText = formatMatrixRaw(result),
                resultMatrix = result
            )
        )
    }

    var showPowerDialog by remember { mutableStateOf(false) }
    var powerInput by remember { mutableStateOf("3") }

    var showPolesDialog by remember { mutableStateOf(false) }
    var showFrobeniusDialog by remember { mutableStateOf(false) }
    var showLqrDialog by remember { mutableStateOf(false) }
    var frobSize by rememberSaveable { mutableIntStateOf(2) }
    var frobSizeInput by rememberSaveable { mutableStateOf("2") }
    var frobA by remember {
        mutableStateOf(List(2) { List(2) { "0" } })
    }

    var frobB by remember {
        mutableStateOf(List(2) { listOf("0") })
    }
    var lqrN by rememberSaveable { mutableIntStateOf(2) }
    var lqrM by rememberSaveable { mutableIntStateOf(1) }
    var lqrNInput by rememberSaveable { mutableStateOf("2") }
    var lqrMInput by rememberSaveable { mutableStateOf("1") }

    var lqrAInput by remember {
        mutableStateOf(List(2) { List(2) { "0" } })
    }
    var lqrBInput by remember {
        mutableStateOf(List(2) { listOf("0") })
    }
    var lqrQInput by remember {
        mutableStateOf(List(2) { List(2) { "0" } })
    }
    var lqrRInput by remember {
        mutableStateOf(listOf(listOf("0")))
    }

    var polesInput by remember { mutableStateOf("0, 0") }
    var poleAssignments by remember { mutableStateOf(List(2) { "0" }) }

    var designSize by rememberSaveable { mutableIntStateOf(2) }
    var designSizeInput by rememberSaveable { mutableStateOf("2") }

    var designA by remember {
        mutableStateOf(List(2) { List(2) { "0" } })
    }
    var designB by remember {
        mutableStateOf(List(2) { listOf("0") })
    }

    val currentData = when (selectedTab) { 0 -> matrixA 1 -> matrixB else -> matrixC }
    val currentAccent = when (selectedTab) { 0 -> AccentCyan 1 -> AccentPurple else -> AccentAmber }
    val tabName = when (selectedTab) { 0 -> "A" 1 -> "B" else -> "C" }

    fun updateCurrent(updated: List<List<String>>) {
        when (selectedTab) { 0 -> onMatrixAChange(updated) 1 -> onMatrixBChange(updated) else -> onMatrixCChange(updated) }
    }

    val calculatedCellSize = ((screenWidth - 44.dp) / size).coerceIn(44.dp, 64.dp)
    val cellFontSize = when {
        size >= 6 -> 11.sp
        size >= 5 -> 12.sp
        calculatedCellSize < 54.dp -> 13.sp
        else -> 16.sp
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardDark).padding(6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SecondaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)), containerColor = Color.Transparent, indicator = {}, divider = {}) {
                Tab(selected = selectedTab == 0, onClick = { onTabChange(0) }, text = { Text("A", fontWeight = FontWeight.Bold, color = if (selectedTab == 0) AccentCyan else TextDim, fontSize = 15.sp) })
                Tab(selected = selectedTab == 1, onClick = { onTabChange(1) }, text = { Text("B", fontWeight = FontWeight.Bold, color = if (selectedTab == 1) AccentPurple else TextDim, fontSize = 15.sp) })
                Tab(selected = selectedTab == 2, onClick = { onTabChange(2) }, text = { Text("C", fontWeight = FontWeight.Bold, color = if (selectedTab == 2) AccentAmber else TextDim, fontSize = 15.sp) })
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CounterButton("−", size > 2) { if (size > 2) onSizeChange(size - 1) }
                Text("${size}×$size", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp))
                CounterButton("+", size < 7) { if (size < 7) onSizeChange(size + 1) }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MatrixGridInput(
            rows = size, cols = size, data = currentData, accentColor = currentAccent, cellSize = calculatedCellSize, fontSize = cellFontSize,
            onValueChange = { r, c, v ->
                val updated = currentData.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                updateCurrent(updated)
            }
        )

        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { updateCurrent(List(size) { List(size) { Random.nextInt(-9, 10).toString() } }) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Рандом", color = TextWhite, fontSize = 11.sp) }
            FilledTonalButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clipText.isNullOrEmpty()) {
                    val parsed = parsePastedText(clipText, size, size)
                    if (parsed != null) { updateCurrent(parsed); Toast.makeText(context, "Вставлено в $tabName!", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(context, "Не удалось распознать!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Буфер пуст!", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentCyan.copy(alpha = 0.2f)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Вставить", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            FilledTonalButton(onClick = { updateCurrent(List(size) { List(size) { "0" } }) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Очистить", color = Color(0xFFF87171), fontSize = 11.sp) }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- БЛОК СИНТЕЗА ТАУ ---
        Text("Синтез ТАУ (A, B, C):", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start), letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("Фробениус (A,B)", Modifier.weight(1f), colorType = 2) {
                frobSize = 2
                frobSizeInput = "2"

                frobA = List(2) { List(2) { "0" } }
                frobB = List(2) { listOf("0") }

                showFrobeniusDialog = true
            }
            AdaptiveBtn("Модальное упр-е", Modifier.weight(1f), colorType = 2) {
                designSize = 2
                designSizeInput = "2"
                designA = List(2) { List(2) { "0" } }
                designB = List(2) { listOf("0") }
                polesInput = "0, 0"
                poleAssignments = List(2) { "0" }

                showPolesDialog = true
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("LQR-оптимизация", Modifier.weight(1f), colorType = 2) {
                lqrN = 2
                lqrM = 1
                lqrNInput = "2"
                lqrMInput = "1"

                lqrAInput = List(2) { List(2) { "0" } }
                lqrBInput = List(2) { listOf("0") }
                lqrQInput = List(2) { List(2) { "0" } }
                lqrRInput = listOf(listOf("0"))

                showLqrDialog = true
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // АНАЛИЗ СИСТЕМ УПРАВЛЕНИЯ (ТАУ)
        Text("Анализ систем управления (ТАУ):", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start), letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("Управляемость (A,B)", Modifier.weight(1f), colorType = 2) {
                val matA = parse(matrixA); val matB = parse(matrixB)
                val wc = math.controllability(matA, matB)
                val r = math.rank(wc)
                val state = if (r == matA.size) "✅ Система ПОЛНОСТЬЮ управляема" else "❌ Система НЕ управляема"
                resultScalarText = "rank(W_c) = $r\n\n$state"; lastResultGrid = null; lastResultMatrix = wc; isError = false
                onAddToHistory(HistoryItem(title = "Матрица управляемости W_c", resultText = resultScalarText, resultMatrix = wc))
            }
            AdaptiveBtn("Наблюдаемость (A,C)", Modifier.weight(1f), colorType = 2) {
                val matA = parse(matrixA); val matC = parse(matrixC)
                val wo = math.observability(matA, matC)
                val r = math.rank(wo)
                val state = if (r == matA.size) "✅ Система ПОЛНОСТЬЮ наблюдаема" else "❌ Система НЕ наблюдаема"
                resultScalarText = "rank(W_o) = $r\n\n$state"; lastResultGrid = null; lastResultMatrix = wo; isError = false
                onAddToHistory(HistoryItem(title = "Матрица наблюдаемости W_o", resultText = resultScalarText, resultMatrix = wo))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("ЖНФ (Комплексная)", Modifier.weight(1f), colorType = 2) {
                try {
                    val raw = parse(currentData); val (desc, jnfGrid) = math.jordanForm(raw)
                    resultScalarText = desc; lastResultGrid = jnfGrid; lastResultMatrix = null
                    isError = jnfGrid.isEmpty()
                    if (!isError) onAddToHistory(HistoryItem(title = "ЖНФ", resultText = "$desc\n" + jnfGrid.joinToString("\n") { it.joinToString("\t") }, resultMatrix = null))
                } catch (e: Exception) {
                    lastResultGrid = null; lastResultMatrix = null; isError = true
                    resultScalarText = e.message ?: "Не удалось вычислить ЖНФ"
                }
            }
            AdaptiveBtn("РЖНФ (Вещественная)", Modifier.weight(1f), colorType = 2) {
                try {
                    val raw = parse(currentData); val (desc, rjnfGrid) = math.realJordanForm(raw)
                    resultScalarText = desc; lastResultGrid = rjnfGrid; lastResultMatrix = null
                    isError = rjnfGrid.isEmpty()
                    if (!isError) onAddToHistory(HistoryItem(title = "РЖНФ", resultText = "$desc\n" + rjnfGrid.joinToString("\n") { it.joinToString("\t") }, resultMatrix = null))
                } catch (e: Exception) {
                    lastResultGrid = null; lastResultMatrix = null; isError = true
                    resultScalarText = e.message ?: "Не удалось вычислить РЖНФ"
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("λ_i (Числа)", Modifier.weight(1f), colorType = 2) {
                val raw = parse(currentData); val eig = math.eigenvalues(raw)
                val text = eig.mapIndexed { idx, c -> "λ_${idx + 1} = $c" }.joinToString("\n")
                resultScalarText = "Собственные числа спектра:\n\n$text"; lastResultMatrix = null; lastResultGrid = null; isError = false
                onAddToHistory(HistoryItem(title = "Собственные числа λ_i", resultText = text, resultMatrix = null))
            }
            AdaptiveBtn("v_i (Векторы)", Modifier.weight(1f), colorType = 2) {
                val raw = parse(currentData); val eig = math.eigenvalues(raw); val vectors = math.eigenvectors(raw, eig)
                val text = vectors.mapIndexed { idx, v -> "v_${idx + 1} (λ_${idx + 1}):\n[ " + v.joinToString(", ") { it.toString() } + " ]ᵀ" }.joinToString("\n\n")
                resultScalarText = "Собственные векторы:\n\n$text"; lastResultMatrix = null; lastResultGrid = null; isError = false
                onAddToHistory(HistoryItem(title = "Собственные векторы v_i", resultText = text, resultMatrix = null))
            }
            AdaptiveBtn(
                "Радиус ρ(A)",
                Modifier.weight(1f),
                colorType = 2
            ) {
                try {
                    val matrix = parse(currentData)
                    val eigenvalues = math.eigenvalues(matrix)

                    if (eigenvalues.isEmpty()) {
                        throw IllegalStateException(
                            "Не удалось вычислить собственные значения."
                        )
                    }

                    val spectralRadius = eigenvalues.maxOf { it.abs() }

                    if (!spectralRadius.isFinite()) {
                        throw ArithmeticException(
                            "Спектральный радиус не является конечным числом."
                        )
                    }

                    val tolerance = 1e-8

                    val stabilityMessage = when {
                        spectralRadius < 1.0 - tolerance -> {
                            "✅ АСИМПТОТИЧЕСКИ УСТОЙЧИВА\n\n" +
                                    "Все собственные значения находятся " +
                                    "строго внутри единичного круга."
                        }

                        spectralRadius > 1.0 + tolerance -> {
                            "❌ НЕУСТОЙЧИВА\n\n" +
                                    "Хотя бы одно собственное значение " +
                                    "находится вне единичного круга."
                        }

                        else -> {
                            "⚠️ ПОГРАНИЧНЫЙ СЛУЧАЙ\n\n" +
                                    "Спектральный радиус близок к 1.\n\n" +
                                    "Необходимо проверить жорданову структуру.\n\n" +
                                    "Если все собственные значения с |λ| = 1 " +
                                    "имеют только жордановы блоки размера 1, " +
                                    "система устойчива, но не асимптотически.\n\n" +
                                    "Если существует жорданов блок размера больше 1 " +
                                    "для |λ| = 1, система неустойчива."
                        }
                    }

                    val eigenvaluesText = eigenvalues
                        .mapIndexed { index, value ->
                            "λ${index + 1} = $value"
                        }
                        .joinToString("\n")

                    val resultText = buildString {
                        appendLine("АНАЛИЗ УСТОЙЧИВОСТИ")
                        appendLine("Дискретная система")
                        appendLine()
                        appendLine("ρ(A) = ${formatPreciseNumber(spectralRadius)}")
                        appendLine()
                        appendLine(stabilityMessage)
                        appendLine()
                        appendLine("Собственные значения:")
                        append(eigenvaluesText)
                    }

                    lastResultMatrix = null
                    lastResultGrid = null
                    resultScalarText = resultText
                    isError = false

                    onAddToHistory(
                        HistoryItem(
                            title = "Спектральный радиус ρ(A)",
                            resultText = resultText,
                            resultMatrix = null
                        )
                    )

                } catch (e: Exception) {
                    lastResultMatrix = null
                    lastResultGrid = null

                    resultScalarText =
                        "Ошибка анализа устойчивости:\n" +
                                (e.message ?: "Неизвестная ошибка")

                    isError = true
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Дополнительные инструменты, присутствующие в веб-версии
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("Характеристический многочлен", Modifier.weight(1f), colorType = 2) {
                try {
                    val coefficients = characteristicPolynomialCoefficients(parse(currentData))
                    val polynomial = formatCharacteristicPolynomial(coefficients)
                    resultScalarText = "Характеристический многочлен матрицы $tabName:\n\np(λ) = $polynomial"
                    lastResultMatrix = null
                    lastResultGrid = null
                    isError = false
                    onAddToHistory(HistoryItem(title = "Характеристический многочлен p(λ)", resultText = resultScalarText, resultMatrix = null))
                } catch (e: Exception) {
                    lastResultMatrix = null
                    lastResultGrid = null
                    resultScalarText = e.message ?: "Не удалось вычислить характеристический многочлен"
                    isError = true
                }
            }
            AdaptiveBtn("Устойчивость (непрерывная)", Modifier.weight(1f), colorType = 2) {
                try {
                    val eigenvalues = math.eigenvalues(parse(currentData))
                    if (eigenvalues.isEmpty()) throw IllegalStateException("Не удалось вычислить собственные значения")
                    val maxReal = eigenvalues.maxOf { it.re }
                    val verdict = when {
                        maxReal < -1e-8 -> "АСИМПТОТИЧЕСКИ УСТОЙЧИВА: все Re(λ) < 0"
                        maxReal > 1e-8 -> "НЕУСТОЙЧИВА: существует Re(λ) > 0"
                        else -> "ПОГРАНИЧНЫЙ СЛУЧАЙ: есть собственные значения на мнимой оси; требуется анализ жордановой структуры"
                    }
                    val details = eigenvalues.mapIndexed { index, value -> "λ${index + 1} = ${value.re} ${if (value.im < 0) "−" else "+"} ${kotlin.math.abs(value.im)}i" }.joinToString("\n")
                    resultScalarText = "АНАЛИЗ НЕПРЕРЫВНОЙ СИСТЕМЫ\n\n$verdict\n\nСобственные значения:\n$details"
                    lastResultMatrix = null
                    lastResultGrid = null
                    isError = false
                    onAddToHistory(HistoryItem(title = "Устойчивость непрерывной системы", resultText = resultScalarText, resultMatrix = null))
                } catch (e: Exception) {
                    lastResultMatrix = null
                    lastResultGrid = null
                    resultScalarText = e.message ?: "Ошибка анализа устойчивости"
                    isError = true
                }
            }
        }

        // ВЫСШИЕ МАТРИЧНЫЕ ФУНКЦИИ
        Text("Высшие матричные функции:", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start), letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("e^A", Modifier.weight(1f), colorType = 1) {
                val raw = parse(currentData); val res = math.matrixExponential(raw)
                if (res != null) { lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Экспонента e^A", resultText = formatMatrixRaw(res), resultMatrix = res)) }
            }
            AdaptiveBtn("ln(A)", Modifier.weight(1f), colorType = 1) {
                try { val res = math.matrixLog(parse(currentData)); if (res != null) { lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Логарифм ln(A)", resultText = formatMatrixRaw(res), resultMatrix = res)) } else { lastResultMatrix = null; lastResultGrid = null; isError = true; resultScalarText = "Логарифм матрицы не вычислен" } } catch(e: Exception) { lastResultMatrix = null; lastResultGrid = null; isError = true; resultScalarText = e.message ?: "Ошибка вычисления" }
            }
            AdaptiveBtn("√A", Modifier.weight(1f), colorType = 1) {
                try { val res = math.matrixSqrt(parse(currentData)); if (res != null) { lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Корень √A", resultText = formatMatrixRaw(res), resultMatrix = res)) } else { lastResultMatrix = null; lastResultGrid = null; isError = true; resultScalarText = "Корень матрицы не вычислен" } } catch(e: Exception) { lastResultMatrix = null; lastResultGrid = null; isError = true; resultScalarText = e.message ?: "Ошибка вычисления" }
            }
            AdaptiveBtn("A^k", Modifier.weight(1f), colorType = 1) { showPowerDialog = true }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("sin(A)", Modifier.weight(1f), colorType = 1) {
                val res = math.matrixTrig(parse(currentData)).first
                lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Синус sin(A)", resultText = formatMatrixRaw(res), resultMatrix = res))
            }
            AdaptiveBtn("cos(A)", Modifier.weight(1f), colorType = 1) {
                val res = math.matrixTrig(parse(currentData)).second
                lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Косинус cos(A)", resultText = formatMatrixRaw(res), resultMatrix = res))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Операции над матрицами:", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start), letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("A + B + C", Modifier.weight(1f)) {
                val ab = math.add(parse(matrixA), parse(matrixB))
                val res = if (ab != null) math.add(ab, parse(matrixC)) else null
                lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = res == null
                if (res != null) onAddToHistory(HistoryItem(title = "A + B + C", resultText = formatMatrixRaw(res), resultMatrix = res))
            }
            AdaptiveBtn("A - B - C", Modifier.weight(1f)) {
                val ab = math.subtract(parse(matrixA), parse(matrixB))
                val res = if (ab != null) math.subtract(ab, parse(matrixC)) else null
                lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = res == null
                if (res != null) onAddToHistory(HistoryItem(title = "A - B - C", resultText = formatMatrixRaw(res), resultMatrix = res))
            }
            AdaptiveBtn("A × B × C", Modifier.weight(1f)) {
                val ab = math.multiply(parse(matrixA), parse(matrixB))
                val res = if (ab != null) math.multiply(ab, parse(matrixC)) else null
                lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = res == null
                if (res != null) onAddToHistory(HistoryItem(title = "A × B × C", resultText = formatMatrixRaw(res), resultMatrix = res))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("A + B", Modifier.weight(1f)) {
                try {
                    val res = math.add(
                        parse(matrixA),
                        parse(matrixB)
                    )

                    showMatrixResult(res, "A + B")

                } catch (e: Exception) {
                    lastResultMatrix = null
                    lastResultGrid = null
                    resultScalarText =
                        e.message ?: "Ошибка сложения матриц"
                    isError = true
                }
            }
            AdaptiveBtn("A - B", Modifier.weight(1f)) {
                try {
                    val res = math.subtract(
                        parse(matrixA),
                        parse(matrixB)
                    )

                    showMatrixResult(res, "A - B")

                } catch (e: Exception) {
                    lastResultMatrix = null
                    lastResultGrid = null
                    resultScalarText =
                        e.message ?: "Ошибка вычитания матриц"
                    isError = true
                }
            }
            AdaptiveBtn("A × B", Modifier.weight(1f)) {
                try {
                    val res = math.multiply(
                        parse(matrixA),
                        parse(matrixB)
                    )

                    showMatrixResult(res, "A × B")

                } catch (e: Exception) {
                    lastResultMatrix = null
                    lastResultGrid = null
                    resultScalarText =
                        e.message ?: "Ошибка умножения матриц"
                    isError = true
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ОПЕРАЦИИ НАД ВЫБРАННОЙ МАТРИЦЕЙ (ВКЛЮЧАЯ ОБЕ НОРМЫ И СЛЕД)
        Text("Операции над [$tabName]:", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start), letterSpacing = 0.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("${tabName}⁻¹ (Обратная)", Modifier.weight(1f)) {
                val res = math.inverse(parse(currentData))
                if (res != null) { lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Обратная $tabName⁻¹", resultText = formatMatrixRaw(res), resultMatrix = res)) }
                else { lastResultMatrix = null; lastResultGrid = null; resultScalarText = "Определитель равен 0!\nОбратная матрица не существует."; isError = true }
            }
            AdaptiveBtn("${tabName}ᵀ (Трансп.)", Modifier.weight(1f)) {
                val res = math.transpose(parse(currentData)); lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Транспонирование ${tabName}ᵀ", resultText = formatMatrixRaw(res), resultMatrix = res)) }
            AdaptiveBtn("det($tabName)", Modifier.weight(1f)) {
                lastResultMatrix = null; lastResultGrid = null; val res = math.determinant(parse(currentData))
                val txt = "det($tabName) = ${formatNumber(res ?: 0.0)}"; resultScalarText = "Определитель:\n$txt"; isError = false; onAddToHistory(HistoryItem(title = "Определитель det($tabName)", resultText = txt, resultMatrix = null))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("Rank($tabName)", Modifier.weight(1f)) {
                lastResultMatrix = null; lastResultGrid = null; val res = math.rank(parse(currentData))
                val txt = "rank($tabName) = $res"; resultScalarText = "Ранг матрицы (Rank):\n$txt"; isError = false; onAddToHistory(HistoryItem(title = "Ранг rank($tabName)", resultText = txt, resultMatrix = null))
            }
            AdaptiveBtn("След Tr($tabName)", Modifier.weight(1f)) {
                lastResultMatrix = null; lastResultGrid = null; val res = math.trace(parse(currentData))
                val txt = "Tr($tabName) = ${formatNumber(res ?: 0.0)}"; resultScalarText = "След матрицы (Trace):\n$txt"; isError = false; onAddToHistory(HistoryItem(title = "След Tr($tabName)", resultText = txt, resultMatrix = null))
            }
            AdaptiveBtn("‖$tabName‖_F (Фроб.)", Modifier.weight(1f)) {
                lastResultMatrix = null; lastResultGrid = null; val norm = math.norm(parse(currentData))
                val txt = "‖$tabName‖_F = ${formatNumber(norm)}"; resultScalarText = "Норма Фробениуса:\n$txt"; isError = false; onAddToHistory(HistoryItem(title = "Норма ‖$tabName‖_F", resultText = txt, resultMatrix = null))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("‖$tabName‖_2 (Спектральная норма)", Modifier.weight(1f), colorType = 1) {
                lastResultMatrix = null; lastResultGrid = null; val norm2 = math.spectralNorm(parse(currentData))
                val txt = if (norm2 != null) "‖$tabName‖_2 = ${formatNumber(norm2)}" else "Ошибка вычисления нормы"
                resultScalarText = "Спектральная норма (2-норма):\n$txt"; isError = norm2 == null
                if (norm2 != null) onAddToHistory(HistoryItem(title = "Спектральная норма ‖$tabName‖_2", resultText = txt, resultMatrix = null))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Скаляр k:", color = TextWhite, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(60.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                BasicTextField(value = scalarK, onValueChange = onScalarKChange, textStyle = TextStyle(color = TextWhite, textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(AccentCyan))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val k = scalarK.toDoubleOrNull() ?: 1.0; val res = math.multiplyScalar(parse(currentData), k); lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false
                onAddToHistory(HistoryItem(title = "Умножение $k × $tabName", resultText = formatMatrixRaw(res), resultMatrix = res))
            }, modifier = Modifier.weight(1f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) {
                Text("k × $tabName", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ResultCardBlock(
            lastResultMatrix = lastResultMatrix, lastResultGrid = lastResultGrid, resultScalarText = resultScalarText, isError = isError, screenWidth = screenWidth, context = context,
            onTransferToA = { mat -> onMatrixAChange(matrixToPreciseStringList(mat)) },
            onTransferToB = { mat -> onMatrixBChange(matrixToPreciseStringList(mat)) },
            onTransferToC = { mat -> onMatrixCChange(matrixToPreciseStringList(mat)) }
        )
    }

    if (showPowerDialog) {
        AlertDialog(
            onDismissRequest = { showPowerDialog = false },
            title = { Text("Степень матрицы", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Введите степень k (от 2 до 1000):", color = TextDim, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp)).padding(12.dp)) {
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
                    val k = powerInput.toIntOrNull() ?: 2
                    if (k in 2..1000) {
                        val res = math.matrixPower(parse(currentData), k)
                        if (res != null) {
                            lastResultMatrix = res; lastResultGrid = null; resultScalarText = ""; isError = false
                            onAddToHistory(HistoryItem(title = "Степень $tabName^$k", resultText = formatMatrixRaw(res), resultMatrix = res))
                        }
                        showPowerDialog = false
                    } else { Toast.makeText(context, "Введите число от 2 до 1000", Toast.LENGTH_SHORT).show() }
                }) { Text("Вычислить", color = AccentCyan, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPowerDialog = false }) { Text("Отмена", color = TextDim) }
            },
            containerColor = CardDark
        )
    }

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
                        text = "Введите матрицы системы прямо в этом окне.",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "A должна быть n×n, B — n×1. Для результата требуется полностью управляемая система.",
                        color = TextDim,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Размер n:",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A))
                                .border(
                                    1.dp,
                                    AccentCyan,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicTextField(
                                value = frobSizeInput,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.all { it.isDigit() }) {
                                        frobSizeInput = input

                                        val n = input.toIntOrNull()

                                        if (n != null && n in 2..7 && n != frobSize) {
                                            frobSize = n
                                            frobA = List(n) { List(n) { "0" } }
                                            frobB = List(n) { listOf("0") }
                                        }
                                    }
                                },
                                textStyle = TextStyle(
                                    color = TextWhite,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                cursorBrush = SolidColor(AccentCyan),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                )
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            "от 2 до 7",
                            color = TextDim,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        "МАТРИЦА A (${frobSize}×${frobSize})",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.7.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MatrixGridInput(
                            frobSize,
                            frobSize,
                            frobA,
                            AccentCyan,
                            46.dp,
                            13.sp
                        ) { r, c, v ->
                            frobA = frobA.mapIndexed { i, row ->
                                if (i == r) {
                                    row.mapIndexed { j, old ->
                                        if (j == c) v else old
                                    }
                                } else {
                                    row
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "МАТРИЦА B (${frobSize}×1)",
                        color = AccentPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = 0.7.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MatrixGridInput(
                            frobSize,
                            1,
                            frobB,
                            AccentPurple,
                            46.dp,
                            13.sp
                        ) { r, _, v ->
                            frobB = frobB.mapIndexed { i, row ->
                                if (i == r) listOf(v) else row
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val a = parse(frobA)
                        val b = parse(frobB)
                        require(a.size == a.firstOrNull()?.size && a.size >= 2) { "Матрица A должна быть квадратной n×n" }
                        require(b.size == a.size && b.all { it.size == 1 }) { "Матрица B должна иметь размер ${a.size}×1" }
                        val res = math.frobeniusForm(a, b)
                        require(res != null) { "Форма Фробениуса недоступна: проверьте управляемость системы." }
                        val (aTilde, bTilde, tMat) = res!!
                        resultScalarText = buildString {
                            appendLine("НОРМАЛЬНАЯ ФОРМА ФРОБЕНИУСА–ЛЮЕНБЕРГЕРА")
                            appendLine()
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
                        onAddToHistory(HistoryItem(title = "Форма Фробениуса", resultText = resultScalarText, resultMatrix = aTilde))
                        showFrobeniusDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка вычисления формы Фробениуса", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showFrobeniusDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }

    if (showPolesDialog) {
        AlertDialog(
            onDismissRequest = { showPolesDialog = false },
            title = { Text("Модальное управление (K)", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                    Text("Матрицы для метода Аккермана — прямо здесь. Размерность A: n×n, B: n×1.", color = TextDim, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Размер n:", color = TextWhite)
                        Box(
                            modifier = Modifier.width(84.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, AccentCyan, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 9.dp)
                        ) {
                            BasicTextField(
                                value = designSizeInput,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.all { it.isDigit() }) {
                                        designSizeInput = input
                                        val n = input.toIntOrNull()
                                        if (n != null && n in 2..7 && n != designSize) {
                                            designSize = n
                                            designA = resizeMatrix(designA, n, n)
                                            designB = resizeMatrix(designB, n, 1)
                                            polesInput = List(n) { "0" }.joinToString(", ")
                                            poleAssignments = List(n) { "0" }
                                        }
                                    }
                                },
                                textStyle = TextStyle(color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                                cursorBrush = SolidColor(AccentCyan),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Text("Введите число от 2 до 7", color = TextDim, fontSize = 11.sp)
                    }
                    Text("A (${designSize}×${designSize})", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(designSize, designSize, designA, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        designA = designA.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("B (${designSize}×1)", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(designSize, 1, designB, AccentPurple, 48.dp, 13.sp) { r, c, v ->
                        designB = designB.mapIndexed { i, row -> if (i == r) listOf(v) else row }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Таблица соответствий собственных значений и новых полюсов", color = TextDim, fontSize = 13.sp)
                    Text("Каждая строка задаёт новый полюс для собственного значения A в этой строке.", color = TextDim, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    val eigenvaluesForTable = remember(designA) {
                        runCatching { math.eigenvalues(parse(designA)) }.getOrDefault(emptyList())
                    }
                    repeat(designSize) { index ->
                        val eig = eigenvaluesForTable.getOrNull(index)
                        val eigText = if (eig == null) "λ${index + 1} (не вычислено)" else {
                            val re = String.format(Locale.US, "%.4g", eig.re)
                            val im = String.format(Locale.US, "%.4g", kotlin.math.abs(eig.im))
                            if (kotlin.math.abs(eig.im) < 1e-9) "λ${index + 1} = $re" else "λ${index + 1} = $re ${if (eig.im >= 0) "+" else "−"} ${im}i"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(eigText, color = AccentCyan, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Box(modifier = Modifier.width(110.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp)).padding(9.dp)) {
                                BasicTextField(
                                    value = poleAssignments.getOrElse(index) { "0" },
                                    onValueChange = { value ->
                                        poleAssignments = List(designSize) { i ->
                                            if (i == index) value else poleAssignments.getOrElse(i) { "0" }
                                        }
                                    },
                                    textStyle = TextStyle(color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    cursorBrush = SolidColor(AccentCyan), modifier = Modifier.fillMaxWidth(), singleLine = true
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val aForDesign = parse(designA)
                        val bForDesign = parse(designB)
                        require(aForDesign.isNotEmpty() && aForDesign.size == aForDesign[0].size) {
                            "Матрица A должна быть квадратной n×n"
                        }
                        require(bForDesign.size == aForDesign.size && bForDesign.all { it.size == 1 }) {
                            "Матрица B должна иметь размерность ${aForDesign.size}×1"
                        }
                        val poles = poleAssignments.take(aForDesign.size).mapIndexed { index, raw ->
                            val parsed = math.parsePoles(raw)
                            require(parsed.size == 1) { "Для строки ${index + 1} укажите ровно один полюс" }
                            parsed.single()
                        }
                        val kMat = math.ackermann(aForDesign, bForDesign, poles)
                        lastResultMatrix = kMat; lastResultGrid = null; resultScalarText = "Матрица коэффициентов обратной связи (K):"; isError = false
                        onAddToHistory(HistoryItem(title = "Модальное управление (K)", resultText = resultScalarText, resultMatrix = kMat))
                        showPolesDialog = false
                    } catch(e: Exception) {
                        Toast.makeText(context, e.message ?: "Ошибка ввода полюсов", Toast.LENGTH_LONG).show()
                    }
                }) { Text("Рассчитать", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPolesDialog = false }) { Text("Отмена", color = TextDim) }
            },
            containerColor = CardDark
        )
    }

    if (showLqrDialog) {
        AlertDialog(
            onDismissRequest = { showLqrDialog = false },
            title = { Text("LQR — оптимальное управление", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                    Text("Все данные задаются отдельно от основного экрана: A — динамика, B — входы, Q — штраф за состояние, R — штраф за управление. Требуется Q ≥ 0 и R > 0.", color = TextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Состояния n", color = TextWhite, fontSize = 12.sp)
                        Box(modifier = Modifier.width(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F172A)).border(1.dp, AccentCyan, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            BasicTextField(value = lqrNInput, onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) { lqrNInput = v; v.toIntOrNull()?.takeIf { it in 2..7 }?.let { n -> if (n != lqrN) { lqrN = n; lqrAInput = resizeMatrix(lqrAInput,n,n); lqrBInput = resizeMatrix(lqrBInput,n,lqrM); lqrQInput = resizeMatrix(lqrQInput,n,n) } } } }, textStyle = TextStyle(color = TextWhite, fontSize = 14.sp), cursorBrush = SolidColor(AccentCyan), singleLine = true)
                        }
                        Text("Входы m", color = TextWhite, fontSize = 12.sp)
                        Box(modifier = Modifier.width(54.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F172A)).border(1.dp, AccentPurple, RoundedCornerShape(8.dp)).padding(8.dp)) {
                            BasicTextField(value = lqrMInput, onValueChange = { v -> if (v.isEmpty() || v.all { it.isDigit() }) { lqrMInput = v; v.toIntOrNull()?.takeIf { it in 1..4 }?.let { m -> if (m != lqrM) { lqrM = m; lqrBInput = resizeMatrix(lqrBInput,lqrN,m); lqrRInput = resizeMatrix(lqrRInput,m,m) } } } }, textStyle = TextStyle(color = TextWhite, fontSize = 14.sp), cursorBrush = SolidColor(AccentPurple), singleLine = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        runCatching {
                            val a0 = parse(matrixA); val b0 = parse(matrixB); val q0 = parse(matrixC)
                            require(a0.size == a0.firstOrNull()?.size && a0.size in 2..7) { "Матрица A на основном экране должна быть квадратной размером 2–7" }
                            require(b0.size == a0.size && b0.all { it.size == b0.first().size } && b0.first().size in 1..4) { "Размер B на основном экране должен быть n×m" }
                            require(q0.size == a0.size && q0.all { it.size == a0.size }) { "Матрица C на основном экране должна иметь размер n×n для загрузки в Q" }
                            lqrN = a0.size; lqrNInput = lqrN.toString(); lqrM = b0.first().size; lqrMInput = lqrM.toString()
                            lqrAInput = matrixA; lqrBInput = matrixB; lqrQInput = matrixC
                            lqrRInput = List(lqrM) { i -> List(lqrM) { j -> if (i == j) "1" else "0" } }
                        }.onFailure { Toast.makeText(context, it.message ?: "Не удалось загрузить A, B, C", Toast.LENGTH_LONG).show() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Загрузить A, B, C с основного экрана", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A (${lqrN}×${lqrN})", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrN, lqrAInput, AccentCyan, 40.dp, 11.sp) { r,c,v -> lqrAInput = lqrAInput.mapIndexed { i,row -> if (i==r) row.mapIndexed { j,old -> if (j==c) v else old } else row } }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("B (${lqrN}×${lqrM})", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrM, lqrBInput, AccentPurple, 40.dp, 11.sp) { r,c,v -> lqrBInput = lqrBInput.mapIndexed { i,row -> if (i==r) row.mapIndexed { j,old -> if (j==c) v else old } else row } }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Q (${lqrN}×${lqrN})", color = AccentAmber, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrN, lqrQInput, AccentAmber, 40.dp, 11.sp) { r,c,v -> lqrQInput = lqrQInput.mapIndexed { i,row -> if (i==r) row.mapIndexed { j,old -> if (j==c) v else old } else row } }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("R (${lqrM}×${lqrM})", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrM, lqrM, lqrRInput, AccentPurple, 40.dp, 11.sp) { r,c,v -> lqrRInput = lqrRInput.mapIndexed { i,row -> if (i==r) row.mapIndexed { j,old -> if (j==c) v else old } else row } }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val a = parse(lqrAInput); val b = parse(lqrBInput); val q = parse(lqrQInput); val r = parse(lqrRInput)
                        val (pMat, kMat) = math.solveLQR(a, b, q, r)
                        resultScalarText = "LQR: оптимальное управление\n\nРешение уравнения Риккати P:\n" + formatMatrixRaw(pMat) + "\n\nМатрица обратной связи K = R⁻¹BᵀP:\n" + formatMatrixRaw(kMat) + "\n\nЗакон управления: u = −Kx"
                        lastResultGrid = null; lastResultMatrix = kMat; isError = false
                        onAddToHistory(HistoryItem(title = "LQR Синтез", resultText = resultScalarText, resultMatrix = kMat))
                        showLqrDialog = false
                    } catch (e: Exception) { Toast.makeText(context, e.message ?: "Ошибка расчёта LQR", Toast.LENGTH_LONG).show() }
                }) { Text("Рассчитать", color = AccentPurple, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showLqrDialog = false }) { Text("Отмена", color = TextDim) } },
            containerColor = CardDark
        )
    }
}

@Composable
fun RectangularMatricesScreen(
    selectedTab: Int, onTabChange: (Int) -> Unit,
    rowsA: Int, colsA: Int, onSizeAChange: (Int, Int) -> Unit, matrixA: List<List<String>>, onMatrixAChange: (List<List<String>>) -> Unit,
    rowsB: Int, colsB: Int, onSizeBChange: (Int, Int) -> Unit, matrixB: List<List<String>>, onMatrixBChange: (List<List<String>>) -> Unit,
    scalarK: String, onScalarKChange: (String) -> Unit, onAddToHistory: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val math = remember { MatrixMath() }
    val screenWidth = configuration.screenWidthDp.dp

    var lastResultMatrix by remember { mutableStateOf<Array<DoubleArray>?>(null) }
    var resultScalarText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val curRows = if (selectedTab == 0) rowsA else rowsB
    val curCols = if (selectedTab == 0) colsA else colsB
    val currentData = if (selectedTab == 0) matrixA else matrixB
    val currentAccent = if (selectedTab == 0) AccentCyan else AccentPurple
    val tabName = if (selectedTab == 0) "A" else "B"

    fun updateCurrent(updated: List<List<String>>) {
        if (selectedTab == 0) onMatrixAChange(updated) else onMatrixBChange(updated)
    }

    val calculatedCellSize = ((screenWidth - 44.dp) / curCols).coerceIn(44.dp, 64.dp)
    val cellFontSize = when { curCols >= 6 -> 11.sp; curCols >= 5 -> 12.sp; calculatedCellSize < 54.dp -> 13.sp; else -> 16.sp }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(CardDark).padding(4.dp)) {
                TabButton("Матрица A (${rowsA}×$colsA)", selectedTab == 0, Modifier.weight(1f)) { onTabChange(0) }
                TabButton("Матрица B (${rowsB}×$colsB)", selectedTab == 1, Modifier.weight(1f)) { onTabChange(1) }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = {
                onMatrixAChange(matrixB); onSizeAChange(rowsB, colsB)
                onMatrixBChange(matrixA); onSizeBChange(rowsA, colsA)
                Toast.makeText(context, "A ⇄ B поменяны местами", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
                Text("A ⇄ B", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Строк (M)", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CounterButton("−", curRows > 1) { if (curRows > 1) { if (selectedTab == 0) onSizeAChange(rowsA - 1, colsA) else onSizeBChange(rowsB - 1, colsB) } }
                        Text("$curRows", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
                        CounterButton("+", curRows < 7) { if (curRows < 7) { if (selectedTab == 0) onSizeAChange(rowsA + 1, colsA) else onSizeBChange(rowsB + 1, colsB) } }
                    }
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = CardDark), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Столбцов (N)", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        CounterButton("−", curCols > 1) { if (curCols > 1) { if (selectedTab == 0) onSizeAChange(rowsA, colsA - 1) else onSizeBChange(rowsB, colsB - 1) } }
                        Text("$curCols", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp))
                        CounterButton("+", curCols < 7) { if (curCols < 7) { if (selectedTab == 0) onSizeAChange(rowsA, colsA + 1) else onSizeBChange(rowsB, colsB + 1) } }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { if (selectedTab == 0) onSizeAChange(1, 3) else onSizeBChange(1, 3) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("➔ Вектор-строка (1×3)", color = AccentCyan, fontSize = 11.sp) }
            FilledTonalButton(onClick = { if (selectedTab == 0) onSizeAChange(3, 1) else onSizeBChange(3, 1) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("➔ Вектор-столбец (3×1)", color = AccentPurple, fontSize = 11.sp) }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MatrixGridInput(
            rows = curRows, cols = curCols, data = currentData, accentColor = currentAccent, cellSize = calculatedCellSize, fontSize = cellFontSize,
            onValueChange = { r, c, v ->
                val updated = currentData.mapIndexed { ri, row -> if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row }
                updateCurrent(updated)
            }
        )

        Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { updateCurrent(List(curRows) { List(curCols) { Random.nextInt(-9, 10).toString() } }) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("Рандом", color = TextWhite, fontSize = 11.sp) }
            FilledTonalButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clipText.isNullOrEmpty()) {
                    val parsed = parsePastedText(clipText, curRows, curCols)
                    if (parsed != null) { updateCurrent(parsed); Toast.makeText(context, "Вставлено в $tabName!", Toast.LENGTH_SHORT).show() }
                    else Toast.makeText(context, "Не удалось распознать!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Буфер пуст!", Toast.LENGTH_SHORT).show()
            }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = AccentCyan.copy(alpha = 0.2f)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Вставить", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            FilledTonalButton(onClick = { updateCurrent(List(curRows) { List(curCols) { "0" } }) }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("Очистить", color = Color(0xFFF87171), fontSize = 11.sp) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("Матричные операции над A и B:", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("A × B", Modifier.weight(1f), colorType = 1) {
                if (colsA != rowsB) { lastResultMatrix = null; resultScalarText = "Нельзя умножить!\nЧисло столбцов A ($colsA) должно совпадать с числом строк B ($rowsB)."; isError = true }
                else { val res = math.multiply(parse(matrixA), parse(matrixB)); lastResultMatrix = res; resultScalarText = ""; isError = false; if (res != null) onAddToHistory(HistoryItem(title = "Умножение A × B", resultText = formatMatrixRaw(res), resultMatrix = res)) }
            }
            AdaptiveBtn("A + B", Modifier.weight(1f)) {
                if (rowsA != rowsB || colsA != colsB) { lastResultMatrix = null; resultScalarText = "Нельзя сложить!\nРазмеры не совпадают."; isError = true }
                else { val res = math.add(parse(matrixA), parse(matrixB)); lastResultMatrix = res; resultScalarText = ""; isError = false; if (res != null) onAddToHistory(HistoryItem(title = "Сложение A + B", resultText = formatMatrixRaw(res), resultMatrix = res)) }
            }
            AdaptiveBtn("A - B", Modifier.weight(1f)) {
                if (rowsA != rowsB || colsA != colsB) { lastResultMatrix = null; resultScalarText = "Нельзя вычесть!\nРазмеры не совпадают."; isError = true }
                else { val res = math.subtract(parse(matrixA), parse(matrixB)); lastResultMatrix = res; resultScalarText = ""; isError = false; if (res != null) onAddToHistory(HistoryItem(title = "Вычитание A - B", resultText = formatMatrixRaw(res), resultMatrix = res)) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Векторные операции (A и B как векторы):", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("a · b (Скалярное)", Modifier.weight(1f)) {
                val totalA = rowsA * colsA; val totalB = rowsB * colsB
                if (totalA != totalB) { lastResultMatrix = null; resultScalarText = "Ошибка! У векторов должно быть равное количество чисел ($totalA ≠ $totalB)."; isError = true }
                else { val dot = math.dotProduct(parse(matrixA), parse(matrixB)); val txt = "a · b = ${formatNumber(dot ?: 0.0)}"; lastResultMatrix = null; resultScalarText = "Скалярное произведение:\n$txt"; isError = false; onAddToHistory(HistoryItem(title = "Скалярное a · b", resultText = txt, resultMatrix = null)) }
            }
            AdaptiveBtn("a ⨯ b (Векторное 3D)", Modifier.weight(1f)) {
                val totalA = rowsA * colsA; val totalB = rowsB * colsB
                if (totalA != 3 || totalB != 3) { lastResultMatrix = null; resultScalarText = "Векторное произведение существует только для 3D векторов!"; isError = true }
                else { val cross = math.crossProduct(parse(matrixA), parse(matrixB)); lastResultMatrix = cross; resultScalarText = ""; isError = false; if (cross != null) onAddToHistory(HistoryItem(title = "Векторное a ⨯ b", resultText = formatMatrixRaw(cross), resultMatrix = cross)) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Анализ и свойства для [$tabName]:", color = TextDim, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdaptiveBtn("${tabName}ᵀ (Транспонировать)", Modifier.weight(1f)) {
                val res = math.transpose(parse(currentData)); lastResultMatrix = res; resultScalarText = ""; isError = false; onAddToHistory(HistoryItem(title = "Транспонирование ${tabName}ᵀ", resultText = formatMatrixRaw(res), resultMatrix = res))
            }
            AdaptiveBtn("Ранг ($tabName)", Modifier.weight(1f)) {
                val res = math.rank(parse(currentData)); val txt = "rank($tabName) = $res"; lastResultMatrix = null; resultScalarText = "Ранг матрицы ($tabName) = $res"; isError = false; onAddToHistory(HistoryItem(title = "Ранг rank($tabName)", resultText = txt, resultMatrix = null))
            }
            AdaptiveBtn("Норма ‖$tabName‖", Modifier.weight(1f)) {
                val norm = math.norm(parse(currentData)); val txt = "‖$tabName‖ = ${formatNumber(norm)}"; lastResultMatrix = null; resultScalarText = "Длина вектора / Норма:\n$txt"; isError = false; onAddToHistory(HistoryItem(title = "Норма ‖$tabName‖", resultText = txt, resultMatrix = null))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardDark).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Скаляр k:", color = TextWhite, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.width(60.dp).height(38.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F172A)).border(1.dp, Color(0xFF24334D), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                BasicTextField(value = scalarK, onValueChange = onScalarKChange, textStyle = TextStyle(color = TextWhite, textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(AccentCyan))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val k = scalarK.toDoubleOrNull() ?: 1.0; val res = math.multiplyScalar(parse(currentData), k); lastResultMatrix = res; resultScalarText = ""; isError = false
                onAddToHistory(HistoryItem(title = "Умножение $k × $tabName", resultText = formatMatrixRaw(res), resultMatrix = res))
            }, modifier = Modifier.weight(1f).height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(0.dp)) {
                Text("k × $tabName", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ResultCardBlock(
            lastResultMatrix = lastResultMatrix, lastResultGrid = null, resultScalarText = resultScalarText, isError = isError, screenWidth = screenWidth, context = context,
            onTransferToA = { mat -> onSizeAChange(mat.size, mat[0].size); onMatrixAChange(matrixToPreciseStringList(mat)) },
            onTransferToB = { mat -> onSizeBChange(mat.size, mat[0].size); onMatrixBChange(matrixToPreciseStringList(mat)) },
            onTransferToC = null
        )
    }
}

@Composable
fun HistoryBottomSheetContent(
    historyList: List<HistoryItem>, onClearHistory: () -> Unit,
    onPasteToSquareA: (Array<DoubleArray>) -> Unit, onPasteToSquareB: (Array<DoubleArray>) -> Unit, onPasteToSquareC: (Array<DoubleArray>) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("История вычислений", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Всего записей: ${historyList.size} (сохраняется всегда)", color = TextDim, fontSize = 12.sp) }
            if (historyList.isNotEmpty()) {
                FilledTonalButton(onClick = onClearHistory, colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF450A0A)), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text("Очистить", color = Color(0xFFFCA5A5), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Text("История пуста.\nВыполните любую операцию!", color = TextDim, textAlign = TextAlign.Center, fontSize = 14.sp) }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(historyList.size) { index ->
                    val item = historyList[index]
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)), shape = RoundedCornerShape(14.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF24334D))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title, color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.time, color = TextDim, fontSize = 11.sp); Spacer(modifier = Modifier.width(6.dp))
                                    Text("📋", fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable {
                                        val txt = if (item.resultMatrix != null) formatMatrixRaw(item.resultMatrix) else item.resultText
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Matrix Result", txt))
                                        Toast.makeText(context, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
                                    }.padding(2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (item.resultMatrix != null) {
                                HistoryMatrixGrid(item.resultMatrix)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f)), shape = RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan), contentPadding = PaddingValues(0.dp), onClick = { onPasteToSquareA(item.resultMatrix) }) { Text("➔ в A", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    Button(modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.2f)), shape = RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple), contentPadding = PaddingValues(0.dp), onClick = { onPasteToSquareB(item.resultMatrix) }) { Text("➔ в B", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    Button(modifier = Modifier.weight(1f).height(32.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.2f)), shape = RoundedCornerShape(6.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber), contentPadding = PaddingValues(0.dp), onClick = { onPasteToSquareC(item.resultMatrix) }) { Text("➔ в C", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                            } else {
                                Text(item.resultText, color = TextWhite, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun FormattedTableGrid(grid: List<List<String>>) {
    val rows = grid.size; val cols = if (rows > 0) grid[0].size else 0
    val hasComplex = grid.any { row -> row.any { it.contains("i") } }
    val cellWidth = if (hasComplex) 115.dp else 75.dp

    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.height((rows * 36).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            for (i in 0 until rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (j in 0 until cols) {
                        val text = grid[i][j]
                        Box(modifier = Modifier.width(cellWidth).height(36.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                            Text(text = text, color = if (text == "1" && i != j) AccentCyan else TextWhite, fontSize = if (hasComplex && text.length > 7) 11.sp else 13.sp, fontFamily = FontFamily.Monospace, fontWeight = if (i == j) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
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
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.height((rows * 26).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            for (i in 0 until rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (j in 0 until cols) {
                        Box(modifier = Modifier.width(70.dp).height(26.dp).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                            Text(formatNumber(matrix[i][j]), color = TextWhite, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun CounterButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(if (enabled) Color(0xFF1E293B) else Color.Transparent).border(1.dp, if (enabled) AccentCyan.copy(alpha = 0.5f) else Color(0xFF24334D), RoundedCornerShape(8.dp)).clickable(enabled = enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }, contentAlignment = Alignment.Center) {
        Text(symbol, color = if (enabled) TextWhite else TextDim.copy(alpha = 0.3f), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) AccentCyan.copy(alpha = 0.2f) else Color.Transparent).border(if (selected) 1.dp else 0.dp, if (selected) AccentCyan else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (selected) AccentCyan else TextDim, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun MatrixGridInput(rows: Int, cols: Int, data: List<List<String>>, accentColor: Color, cellSize: Dp, fontSize: androidx.compose.ui.unit.TextUnit, onValueChange: (Int, Int, String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.height((rows * cellSize.value * 0.95f).dp).width(4.dp).background(accentColor, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.animateContentSize(animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f))) {
            for (i in 0 until rows) {
                Row(modifier = Modifier.animateContentSize()) {
                    for (j in 0 until cols) {
                        val cellVal = if (i < data.size && j < data[i].size) data[i][j] else "0"
                        var isFocused by remember { mutableStateOf(false) }
                        val dynamicFontSize = when { cellVal.length > 5 -> 10.sp; cellVal.length > 3 -> 12.sp; else -> fontSize }

                        Box(modifier = Modifier.size(cellSize).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(CardDark).border(if (isFocused) 1.5.dp else 1.dp, if (isFocused) accentColor else Color(0xFF24334D), RoundedCornerShape(8.dp)).onFocusChanged { focusState -> isFocused = focusState.isFocused; if (focusState.isFocused && cellVal == "0") { onValueChange(i, j, "") } else if (!focusState.isFocused && cellVal.isEmpty()) { onValueChange(i, j, "0") } }, contentAlignment = Alignment.Center) {
                            BasicTextField(value = cellVal, onValueChange = { input: String -> if (input.isEmpty() || input.matches(Regex("^-?\\d*(\\.\\d*)?$"))) { onValueChange(i, j, input) } }, textStyle = TextStyle(color = TextWhite, fontSize = dynamicFontSize, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), cursorBrush = SolidColor(accentColor), modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp))
                            if (cellVal.isEmpty()) { Text("0", color = TextDim.copy(alpha = 0.4f), fontSize = 13.sp, textAlign = TextAlign.Center) }
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
fun ResultCardBlock(lastResultMatrix: Array<DoubleArray>?, lastResultGrid: List<List<String>>? = null, resultScalarText: String, isError: Boolean, screenWidth: Dp, context: Context, onTransferToA: (Array<DoubleArray>) -> Unit, onTransferToB: (Array<DoubleArray>) -> Unit, onTransferToC: ((Array<DoubleArray>) -> Unit)? = null) {
    val hasResult = lastResultMatrix != null || lastResultGrid != null || resultScalarText.isNotEmpty()
    AnimatedVisibility(visible = hasResult, enter = fadeIn() + slideInVertically()) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 24.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (isError) Color(0xFF3B0B0B) else CardDark), border = androidx.compose.foundation.BorderStroke(1.dp, if (isError) Color(0xFFEF4444) else AccentEmerald)) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isError) "Ошибка" else "Результат:", color = if (isError) Color(0xFFFCA5A5) else AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    TextButton(onClick = {
                        val txt = when {
                            lastResultGrid != null -> resultScalarText + "\n" + lastResultGrid.joinToString("\n") { it.joinToString("\t") }
                            lastResultMatrix != null -> formatMatrixRaw(lastResultMatrix)
                            else -> resultScalarText
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Matrix Result", txt)); Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Копировать", color = TextDim, fontSize = 11.sp) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (resultScalarText.isNotEmpty()) { Text(resultScalarText, color = TextWhite, fontSize = 14.sp, fontFamily = FontFamily.Monospace, lineHeight = 22.sp); Spacer(modifier = Modifier.height(6.dp)) }
                if (lastResultGrid != null) { FormattedTableGrid(lastResultGrid) } else if (lastResultMatrix != null) { MatrixResultGridAdaptive(lastResultMatrix, screenWidth) }
                if (lastResultMatrix != null && !isError) {
                    Spacer(modifier = Modifier.height(10.dp)); HorizontalDivider(color = Color(0xFF24334D), thickness = 1.dp); Spacer(modifier = Modifier.height(8.dp))
                    Text("Перенести ответ в:", color = TextDim, fontSize = 11.sp); Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(modifier = Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f)), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AccentCyan), contentPadding = PaddingValues(0.dp), onClick = { onTransferToA(lastResultMatrix); Toast.makeText(context, "➔ в A!", Toast.LENGTH_SHORT).show() }) { Text("➔ в A", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        Button(modifier = Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.2f)), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AccentPurple), contentPadding = PaddingValues(0.dp), onClick = { onTransferToB(lastResultMatrix); Toast.makeText(context, "➔ в B!", Toast.LENGTH_SHORT).show() }) { Text("➔ в B", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        if (onTransferToC != null) { Button(modifier = Modifier.weight(1f).height(36.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.2f)), shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, AccentAmber), contentPadding = PaddingValues(0.dp), onClick = { onTransferToC(lastResultMatrix); Toast.makeText(context, "➔ в C!", Toast.LENGTH_SHORT).show() }) { Text("➔ в C", color = AccentAmber, fontWeight = FontWeight.Bold, fontSize = 11.sp) } }
                    }
                }
            }
        }
    }
}

@Composable
fun MatrixResultGridAdaptive(matrix: Array<DoubleArray>, screenWidth: Dp) {
    val rows = matrix.size; if (rows == 0) return; val cols = matrix[0].size; if (cols == 0) return
    val colWidth = ((screenWidth - 60.dp) / cols).coerceIn(44.dp, 75.dp)
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.height((rows * 28).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp)))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            for (i in 0 until rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (j in 0 until cols) {
                        Box(modifier = Modifier.width(colWidth).height(28.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                            Text(formatNumber(matrix[i][j]), color = TextWhite, fontSize = if (colWidth < 50.dp) 11.sp else 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Box(modifier = Modifier.height((rows * 28).dp).width(3.dp).background(AccentEmerald, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)))
    }
}

@Composable
fun AdaptiveBtn(text: String, modifier: Modifier = Modifier, colorType: Int = 0, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current

    val bgColor = when (colorType) { 1 -> Color(0xFF1E293B); 2 -> Color(0xFF1E1B4B); else -> CardDark }
    val borderColor = when (colorType) { 1 -> AccentCyan.copy(alpha = 0.6f); 2 -> AccentPurple.copy(alpha = 0.5f); else -> Color(0xFF24334D) }
    val txtColor = when (colorType) { 2 -> Color(0xFFC4B5FD); else -> TextWhite }

    Button(onClick = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }, modifier = modifier.height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = bgColor), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp), border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)) {
        Text(text, color = txtColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Faddeev–LeVerrier: p(λ)=λ^n+c1*λ^(n-1)+...+cn. */
fun characteristicPolynomialCoefficients(matrix: Array<DoubleArray>): List<Double> {
    val n = matrix.size
    require(n > 0 && matrix.all { it.size == n }) { "Для характеристического многочлена нужна квадратная матрица" }
    var b = Array(n) { r -> DoubleArray(n) { c -> if (r == c) 1.0 else 0.0 } }
    val coefficients = mutableListOf<Double>()
    for (k in 1..n) {
        val ab = Array(n) { r -> DoubleArray(n) { c ->
            var sum = 0.0
            for (j in 0 until n) sum += matrix[r][j] * b[j][c]
            sum
        } }
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
    terms += "λ^$n"
    coefficients.forEachIndexed { index, raw ->
        val power = n - index - 1
        val value = if (kotlin.math.abs(raw) < 1e-10) 0.0 else raw
        if (value != 0.0) {
            val magnitude = formatNumber(kotlin.math.abs(value))
            val variable = when (power) { 0 -> ""; 1 -> "λ"; else -> "λ^$power" }
            val sign = if (terms.isEmpty()) { if (value < 0) "−" else "" } else if (value < 0) " − " else " + "
            terms += sign + (if (variable.isEmpty() || kotlin.math.abs(value - 1.0) > 1e-10) magnitude else "") + variable
        }
    }
    return terms.joinToString("")
}

fun parsePastedText(text: String, reqRows: Int, reqCols: Int): List<List<String>>? {
    try {
        val normalized = text.replace('−', '-').replace('–', '-').replace('—', '-').replace(',', '.').replace("[", " ").replace("]", " ").trim()
        val rawLines = normalized.lines().filter { it.isNotBlank() }
        val numberRegex = Regex("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?")
        val result = mutableListOf<List<String>>()
        if (rawLines.size >= reqRows) {
            for (i in 0 until reqRows) {
                val rowList = mutableListOf<String>()
                val matches = numberRegex.findAll(rawLines[i]).map { it.value }.toList()
                for (j in 0 until reqCols) { if (j < matches.size) rowList.add(matches[j]) else rowList.add("0") }
                result.add(rowList)
            }
        } else {
            val matches = numberRegex.findAll(normalized).map { it.value }.toList()
            var index = 0
            for (r in 0 until reqRows) {
                val rowList = mutableListOf<String>()
                for (c in 0 until reqCols) { if (index < matches.size) rowList.add(matches[index++]) else rowList.add("0") }
                result.add(rowList)
            }
        }
        return result
    } catch (_: Exception) { return null }
}

fun resizeMatrix(old: List<List<String>>, newR: Int, newC: Int): List<List<String>> { return List(newR) { r -> List(newC) { c -> if (r < old.size && c < old[r].size) old[r][c] else "0" } } }
fun parse(data: List<List<String>>): Array<DoubleArray> {
    require(data.isNotEmpty()) {
        "Матрица не должна быть пустой"
    }

    val cols = data[0].size

    require(cols > 0 && data.all { it.size == cols }) {
        "Матрица должна быть прямоугольной"
    }

    return Array(data.size) { i ->
        DoubleArray(cols) { j ->
            val value = data[i][j].trim().toDoubleOrNull()

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
    if (value == 0.0) return "0"
    // Scientific notation avoids Int overflow and preserves very large/small values.
    if (abs(value) >= 1e12 || abs(value) < 1e-6) {
        return String.format(Locale.US, "%.${decimals}e", value)
            .replace(Regex("0+e"), "e")
            .replace(Regex("\\.e"), "e")
    }
    val rounded = java.math.BigDecimal.valueOf(value)
        .setScale(decimals, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
    return rounded.toPlainString()
}
fun matrixToPreciseStringList(matrix: Array<DoubleArray>): List<List<String>> =
    matrix.map { row -> row.map { formatPreciseNumber(it) } }
fun formatMatrixRaw(matrix: Array<DoubleArray>): String { return matrix.joinToString("\n") { row -> row.joinToString("\t") { v -> formatNumber(v) } } }

/** Форматирует матрицу моноширинным текстом: выравнивает значения по столбцам и добавляет скобки. */
fun formatMatrixAligned(matrix: Array<DoubleArray>): String {
    if (matrix.isEmpty()) return "[ ]"
    val formatted = matrix.map { row -> row.map { formatNumber(it) } }
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
        val left = when {
            rows.size == 1 -> "⎡"
            index == 0 -> "⎡"
            index == rows.lastIndex -> "⎣"
            else -> "⎢"
        }
        val right = when {
            rows.size == 1 -> "⎤"
            index == 0 -> "⎤"
            index == rows.lastIndex -> "⎦"
            else -> "⎥"
        }
        "$left $row $right"
    }.joinToString("\n")
}
