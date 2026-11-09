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

val BgDark = Color(0xFF080D18)
val CardDark = Color(0xFF111A2B)
val CardElevated = Color(0xFF17233A)
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
                    HistoryItem(
                        id = p[0].toLongOrNull() ?: 0L,
                        title = p[1],
                        resultText = p[2],
                        time = p[3],
                        resultMatrix = mat
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14233B), BgDark, Color(0xFF070B14))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MATRIX LAB",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        color = TextWhite
                    )
                    Text(
                        text = "THEORY OF CONTROL • PRO EDITION",
                        fontSize = 10.sp,
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                }
                FilledTonalButton(
                    onClick = { showHistorySheet = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = CardDark),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF24334D)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🕒 ${historyList.size}",
                        color = AccentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .padding(4.dp)
            ) {
                TabButton("Квадратные (A, B, C)", appMode == 0, Modifier.weight(1f)) { appMode = 0 }
                TabButton("Прямоугольные / Векторы", appMode == 1, Modifier.weight(1f)) { appMode = 1 }
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedContent(
                targetState = appMode,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { width -> width / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> -width / 2 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                            slideOutHorizontally { width -> width / 2 } + fadeOut()
                        )
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
                            if (r in 1..7 && c in 1..7) {
                                rectRowsA = r; rectColsA = c; rectMatrixA = resizeMatrix(rectMatrixA, r, c)
                            }
                        },
                        matrixA = rectMatrixA, onMatrixAChange = { rectMatrixA = it },
                        rowsB = rectRowsB, colsB = rectColsB,
                        onSizeBChange = { r, c ->
                            if (r in 1..7 && c in 1..7) {
                                rectRowsB = r; rectColsB = c; rectMatrixB = resizeMatrix(rectMatrixB, r, c)
                            }
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
                        if (mat.size == mat[0].size && mat.size in 2..7) {
                            squareSize = mat.size
                            squareMatrixA = matrixToPreciseStringList(mat)
                            showHistorySheet = false
                        } else Toast.makeText(context, "Нужна квадратная матрица 2×2–7×7", Toast.LENGTH_LONG).show()
                    },
                    onPasteToSquareB = { mat ->
                        if (mat.size == mat[0].size && mat.size in 2..7) {
                            squareSize = mat.size
                            squareMatrixB = matrixToPreciseStringList(mat)
                            showHistorySheet = false
                        } else Toast.makeText(context, "Нужна квадратная матрица 2×2–7×7", Toast.LENGTH_LONG).show()
                    },
                    onPasteToSquareC = { mat ->
                        if (mat.size == mat[0].size && mat.size in 2..7) {
                            squareSize = mat.size
                            squareMatrixC = matrixToPreciseStringList(mat)
                            showHistorySheet = false
                        } else Toast.makeText(context, "Нужна квадратная матрица 2×2–7×7", Toast.LENGTH_LONG).show()
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
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var operationCategory by rememberSaveable { mutableIntStateOf(0) }

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

    fun showMatrixResult(
        result: Array<DoubleArray>?,
        operationName: String
    ) {
        lastResultTitle = operationName
        lastResultGrid = null

        if (result == null) {
            lastResultMatrix = null
            resultScalarText =
                "Не удалось выполнить операцию \"$operationName\".\n" +
                        "Проверьте размеры матриц и допустимость операции."
            isError = true
            scrollToBottom()
            return
        }

        if (result.any { row -> row.any { !it.isFinite() } }) {
            lastResultMatrix = null
            resultScalarText =
                "Ошибка вычисления \"$operationName\".\n" +
                        "Результат содержит NaN или бесконечность."
            isError = true
            scrollToBottom()
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
        scrollToBottom()
    }

    var showPowerDialog by remember { mutableStateOf(false) }
    var powerInput by remember { mutableStateOf("3") }

    var showPolesDialog by remember { mutableStateOf(false) }
    var showFrobeniusDialog by remember { mutableStateOf(false) }
    var showLqrDialog by remember { mutableStateOf(false) }

    var frobSize by rememberSaveable { mutableIntStateOf(2) }
    var frobSizeInput by rememberSaveable { mutableStateOf("2") }
    var frobA by remember { mutableStateOf(List(2) { List(2) { "0" } }) }
    var frobB by remember { mutableStateOf(List(2) { listOf("0") }) }

    var lqrN by rememberSaveable { mutableIntStateOf(2) }
    var lqrM by rememberSaveable { mutableIntStateOf(1) }
    var lqrNInput by rememberSaveable { mutableStateOf("2") }
    var lqrMInput by rememberSaveable { mutableStateOf("1") }

    var lqrAInput by remember { mutableStateOf(List(2) { List(2) { "0" } }) }
    var lqrBInput by remember { mutableStateOf(List(2) { listOf("0") }) }
    var lqrQInput by remember { mutableStateOf(identityStringGrid(2)) }
    var lqrRInput by remember { mutableStateOf(identityStringGrid(1)) }

    var polesInput by remember { mutableStateOf("0, 0") }
    var poleAssignments by remember { mutableStateOf(List(2) { "0" }) }

    var designSize by rememberSaveable { mutableIntStateOf(2) }
    var designSizeInput by rememberSaveable { mutableStateOf("2") }
    var designA by remember { mutableStateOf(List(2) { List(2) { "0" } }) }
    var designB by remember { mutableStateOf(List(2) { listOf("0") }) }

    val currentData = when (selectedTab) {
        0 -> matrixA
        1 -> matrixB
        else -> matrixC
    }
    val currentAccent = when (selectedTab) {
        0 -> AccentCyan
        1 -> AccentPurple
        else -> AccentAmber
    }
    val tabName = when (selectedTab) {
        0 -> "A"
        1 -> "B"
        else -> "C"
    }

    fun updateCurrent(updated: List<List<String>>) {
        when (selectedTab) {
            0 -> onMatrixAChange(updated)
            1 -> onMatrixBChange(updated)
            else -> onMatrixCChange(updated)
        }
    }

    fun transferSquareOrResize(targetSetter: (List<List<String>>) -> Unit, mat: Array<DoubleArray>, targetName: String) {
        if (mat.isNotEmpty() && mat.size == mat[0].size && mat.size in 2..7) {
            if (mat.size != size) onSizeChange(mat.size)
            targetSetter(matrixToPreciseStringList(mat))
            Toast.makeText(context, "Перенесено в $targetName (${mat.size}×${mat.size})", Toast.LENGTH_SHORT).show()
        } else {
            val resized = resizeMatrix(matrixToPreciseStringList(mat), size, size)
            targetSetter(resized)
            Toast.makeText(context, "Вписано в $targetName (${size}×$size)", Toast.LENGTH_SHORT).show()
        }
    }

    val calculatedCellSize = ((screenWidth - 44.dp) / size).coerceIn(44.dp, 62.dp)
    val cellFontSize = when {
        size >= 6 -> 11.sp
        size >= 5 -> 12.sp
        calculatedCellSize < 54.dp -> 13.sp
        else -> 16.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(CardDark)
                .padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MatrixSelectChip("Матрица A", selectedTab == 0, AccentCyan, Modifier.weight(1f)) { onTabChange(0) }
                MatrixSelectChip("Матрица B", selectedTab == 1, AccentPurple, Modifier.weight(1f)) { onTabChange(1) }
                MatrixSelectChip("Матрица C", selectedTab == 2, AccentAmber, Modifier.weight(1f)) { onTabChange(2) }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CounterButton("−", size > 2) { if (size > 2) onSizeChange(size - 1) }
                Text(
                    "${size}×$size",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                CounterButton("+", size < 7) { if (size < 7) onSizeChange(size + 1) }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MatrixGridInput(
            rows = size,
            cols = size,
            data = currentData,
            accentColor = currentAccent,
            cellSize = calculatedCellSize,
            fontSize = cellFontSize,
            onValueChange = { r, c, v ->
                val updated = currentData.mapIndexed { ri, row ->
                    if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row
                }
                updateCurrent(updated)
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickActionChip("🎲 Рандом", TextWhite, Color(0xFF1E293B), Modifier.weight(1f)) {
                updateCurrent(List(size) { List(size) { Random.nextInt(-9, 10).toString() } })
            }
            QuickActionChip("𝐈 Единичная", AccentEmerald, AccentEmerald.copy(alpha = 0.14f), Modifier.weight(1f)) {
                updateCurrent(identityStringGrid(size))
            }
            QuickActionChip("📋 Вставить", AccentCyan, AccentCyan.copy(alpha = 0.16f), Modifier.weight(1f)) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clipText.isNullOrEmpty()) {
                    val parsed = parsePastedText(clipText, size, size)
                    if (parsed != null) {
                        updateCurrent(parsed)
                        Toast.makeText(context, "Вставлено в $tabName!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Не удалось распознать!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Буфер пуст!", Toast.LENGTH_SHORT).show()
            }
            QuickActionChip("✕ Сброс", Color(0xFFF87171), Color(0xFF2A1621), Modifier.weight(0.9f)) {
                updateCurrent(List(size) { List(size) { "0" } })
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(CardDark)
                .border(1.dp, Color(0xFF24334D), RoundedCornerShape(13.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CategoryPill("⚡ Алгебра", operationCategory == 0, AccentCyan, Modifier.weight(1f)) { operationCategory = 0 }
            CategoryPill("🎛 Спектр и ТАУ", operationCategory == 1, AccentPurple, Modifier.weight(1.15f)) { operationCategory = 1 }
            CategoryPill("🚀 Синтез и f(A)", operationCategory == 2, AccentAmber, Modifier.weight(1.15f)) { operationCategory = 2 }
        }

        Spacer(modifier = Modifier.height(10.dp))

        AnimatedContent(
            targetState = operationCategory,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "CategoryContent"
        ) { category ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark.copy(alpha = 0.7f))
                    .border(1.dp, Color(0xFF1F2D46), RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                when (category) {
                    0 -> {
                        SectionHeader("Свойства и унарные операции над [$tabName]")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("${tabName}⁻¹ (Обратная)", Modifier.weight(1f), colorType = 1) {
                                try {
                                    val res = math.inverse(parse(currentData))
                                    if (res != null) {
                                        showMatrixResult(res, "Обратная $tabName⁻¹")
                                    } else {
                                        lastResultTitle = "Обратная $tabName⁻¹"
                                        lastResultMatrix = null; lastResultGrid = null
                                        resultScalarText = "Матрица вырождена (det = 0) или плохо обусловлена!\nОбратная матрица не существует."
                                        isError = true; scrollToBottom()
                                    }
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка обращения матрицы"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("${tabName}ᵀ (Трансп.)", Modifier.weight(1f)) {
                                try {
                                    showMatrixResult(math.transpose(parse(currentData)), "Транспонирование ${tabName}ᵀ")
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка транспонирования"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("det($tabName)", Modifier.weight(1f)) {
                                try {
                                    lastResultTitle = "Определитель det($tabName)"
                                    lastResultMatrix = null; lastResultGrid = null
                                    val res = math.determinant(parse(currentData))
                                    if (res == null) {
                                        resultScalarText = "Переполнение при вычислении det($tabName)"
                                        isError = true
                                    } else {
                                        val txt = "det($tabName) = ${formatNumber(res)}"
                                        resultScalarText = txt
                                        isError = false
                                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                    }
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления определителя"; scrollToBottom()
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("Rank($tabName)", Modifier.weight(1f)) {
                                try {
                                    lastResultTitle = "Ранг rank($tabName)"
                                    lastResultMatrix = null; lastResultGrid = null
                                    val res = math.rank(parse(currentData))
                                    val txt = "rank($tabName) = $res"
                                    resultScalarText = txt; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления ранга"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("След Tr($tabName)", Modifier.weight(1f)) {
                                try {
                                    lastResultTitle = "След Tr($tabName)"
                                    lastResultMatrix = null; lastResultGrid = null
                                    val res = math.trace(parse(currentData))
                                    if (res == null) {
                                        resultScalarText = "Ошибка вычисления следа"; isError = true
                                    } else {
                                        val txt = "Tr($tabName) = ${formatNumber(res)}"
                                        resultScalarText = txt; isError = false
                                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                    }
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления следа"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("‖$tabName‖_F", Modifier.weight(1f)) {
                                try {
                                    lastResultTitle = "Норма Фробениуса ‖$tabName‖_F"
                                    lastResultMatrix = null; lastResultGrid = null
                                    val norm = math.norm(parse(currentData))
                                    val txt = "‖$tabName‖_F = ${formatNumber(norm)}"
                                    resultScalarText = txt; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления нормы"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("‖$tabName‖_2", Modifier.weight(1f), colorType = 1) {
                                try {
                                    lastResultTitle = "Спектральная норма ‖$tabName‖_2"
                                    lastResultMatrix = null; lastResultGrid = null
                                    val norm2 = math.spectralNorm(parse(currentData))
                                    val txt = if (norm2 != null) "‖$tabName‖_2 = ${formatNumber(norm2)}" else "Ошибка вычисления нормы"
                                    resultScalarText = txt; isError = norm2 == null
                                    if (norm2 != null) onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления нормы"; scrollToBottom()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        SectionHeader("Арифметика матриц A, B, C")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("A + B", Modifier.weight(1f)) {
                                try { showMatrixResult(math.add(parse(matrixA), parse(matrixB)), "A + B") }
                                catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                            }
                            AdaptiveBtn("A − B", Modifier.weight(1f)) {
                                try { showMatrixResult(math.subtract(parse(matrixA), parse(matrixB)), "A − B") }
                                catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                            }
                            AdaptiveBtn("A × B", Modifier.weight(1f), colorType = 1) {
                                try { showMatrixResult(math.multiply(parse(matrixA), parse(matrixB)), "A × B") }
                                catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("A + B + C", Modifier.weight(1f)) {
                                try {
                                    val ab = math.add(parse(matrixA), parse(matrixB))
                                    val res = if (ab != null) math.add(ab, parse(matrixC)) else null
                                    showMatrixResult(res, "A + B + C")
                                } catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                            }
                            AdaptiveBtn("A − B − C", Modifier.weight(1f)) {
                                try {
                                    val ab = math.subtract(parse(matrixA), parse(matrixB))
                                    val res = if (ab != null) math.subtract(ab, parse(matrixC)) else null
                                    showMatrixResult(res, "A − B − C")
                                } catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                            }
                            AdaptiveBtn("A × B × C", Modifier.weight(1f)) {
                                try {
                                    val ab = math.multiply(parse(matrixA), parse(matrixB))
                                    val res = if (ab != null) math.multiply(ab, parse(matrixC)) else null
                                    showMatrixResult(res, "A × B × C")
                                } catch (e: Exception) { lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom() }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0B1322))
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
                                    onValueChange = onScalarKChange,
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
                                        val k = scalarK.toDoubleOrNull() ?: 1.0
                                        showMatrixResult(math.multiplyScalar(parse(currentData), k), "Умножение $k × $tabName")
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
                    }

                    1 -> {
                        SectionHeader("Спектр и канонические формы матрицы [$tabName]")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("λ_i (Числа)", Modifier.weight(1f), colorType = 2) {
                                try {
                                    lastResultTitle = "Собственные числа λ_i ($tabName)"
                                    val raw = parse(currentData)
                                    val eig = math.eigenvalues(raw)
                                    val text = eig.mapIndexed { idx, c -> "λ_${idx + 1} = $c" }.joinToString("\n")
                                    resultScalarText = text
                                    lastResultMatrix = null; lastResultGrid = null; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = text, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления λ_i"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("v_i (Векторы)", Modifier.weight(1f), colorType = 2) {
                                try {
                                    lastResultTitle = "Собственные и присоед. векторы ($tabName)"
                                    val raw = parse(currentData)
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
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = text, resultMatrix = null))
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
                                    lastResultTitle = "ЖНФ ($tabName)"
                                    val raw = parse(currentData)
                                    val (desc, jnfGrid) = math.jordanForm(raw)
                                    resultScalarText = desc
                                    lastResultGrid = jnfGrid.takeIf { it.isNotEmpty() }
                                    lastResultMatrix = null
                                    isError = jnfGrid.isEmpty()
                                    if (!isError) {
                                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = "$desc\n" + jnfGrid.joinToString("\n") { it.joinToString("\t") }, resultMatrix = null))
                                    }
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultGrid = null; lastResultMatrix = null; isError = true
                                    resultScalarText = e.message ?: "Не удалось вычислить ЖНФ"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("РЖНФ (Вещественная)", Modifier.weight(1f), colorType = 2) {
                                try {
                                    lastResultTitle = "РЖНФ ($tabName)"
                                    val raw = parse(currentData)
                                    val (desc, rjnfGrid) = math.realJordanForm(raw)
                                    resultScalarText = desc
                                    lastResultGrid = rjnfGrid.takeIf { it.isNotEmpty() }
                                    lastResultMatrix = null
                                    isError = rjnfGrid.isEmpty()
                                    if (!isError) {
                                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = "$desc\n" + rjnfGrid.joinToString("\n") { it.joinToString("\t") }, resultMatrix = null))
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
                            AdaptiveBtn("Многочлен p(λ)", Modifier.weight(1f), colorType = 2) {
                                try {
                                    lastResultTitle = "Характеристический многочлен p(λ)"
                                    val coefficients = characteristicPolynomialCoefficients(parse(currentData))
                                    val polynomial = formatCharacteristicPolynomial(coefficients)
                                    resultScalarText = "p(λ) = $polynomial"
                                    lastResultMatrix = null; lastResultGrid = null; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления p(λ)"; scrollToBottom()
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        SectionHeader("Устойчивость и критерии Калмана")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("Непрерывная Re(λ)", Modifier.weight(1f), colorType = 1) {
                                try {
                                    lastResultTitle = "Устойчивость (непрерывная)"
                                    val eigenvalues = math.eigenvalues(parse(currentData))
                                    val maxReal = eigenvalues.maxOf { it.re }
                                    val verdict = when {
                                        maxReal < -1e-8 -> "✅ АСИМПТОТИЧЕСКИ УСТОЙЧИВА\nВсе Re(λ_i) < 0"
                                        maxReal > 1e-8 -> "❌ НЕУСТОЙЧИВА\nСуществует Re(λ_i) > 0"
                                        else -> "⚠️ ПОГРАНИЧНЫЙ СЛУЧАЙ\nЕсть корни на мнимой оси Re(λ) = 0"
                                    }
                                    val details = eigenvalues.mapIndexed { idx, v -> "λ_${idx + 1} = $v" }.joinToString("\n")
                                    resultScalarText = "$verdict\n\nСпектр:\n$details"
                                    lastResultMatrix = null; lastResultGrid = null; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка анализа устойчивости"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("Дискретная ρ(A)", Modifier.weight(1f), colorType = 1) {
                                try {
                                    lastResultTitle = "Спектральный радиус ρ($tabName)"
                                    val eigenvalues = math.eigenvalues(parse(currentData))
                                    val spectralRadius = eigenvalues.maxOf { it.abs() }
                                    val stabilityMessage = when {
                                        spectralRadius < 1.0 - 1e-8 -> "✅ АСИМПТОТИЧЕСКИ УСТОЙЧИВА (|λ_i| < 1)"
                                        spectralRadius > 1.0 + 1e-8 -> "❌ НЕУСТОЙЧИВА (существует |λ_i| > 1)"
                                        else -> "⚠️ НА ГРАНИЦЕ ЕДИНИЧНОГО КРУГА (ρ ≈ 1)"
                                    }
                                    val details = eigenvalues.mapIndexed { idx, v -> "λ_${idx + 1} = $v" }.joinToString("\n")
                                    resultScalarText = "ρ($tabName) = ${formatPreciseNumber(spectralRadius)}\n\n$stabilityMessage\n\nСпектр:\n$details"
                                    lastResultMatrix = null; lastResultGrid = null; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = null))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления ρ(A)"; scrollToBottom()
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("Управляемость (A,B)", Modifier.weight(1f), colorType = 2) {
                                try {
                                    lastResultTitle = "Матрица управляемости W_c"
                                    val matA = parse(matrixA); val matB = parse(matrixB)
                                    val wc = math.controllability(matA, matB)
                                    val r = math.rank(wc)
                                    val state = if (r == matA.size) "✅ Система ПОЛНОСТЬЮ управляема" else "❌ Система НЕ управляема"
                                    resultScalarText = "rank(W_c) = $r из ${matA.size}\n$state"
                                    lastResultGrid = null; lastResultMatrix = wc; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = wc))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("Наблюдаемость (A,C)", Modifier.weight(1f), colorType = 2) {
                                try {
                                    lastResultTitle = "Матрица наблюдаемости W_o"
                                    val matA = parse(matrixA); val matC = parse(matrixC)
                                    val wo = math.observability(matA, matC)
                                    val r = math.rank(wo)
                                    val state = if (r == matA.size) "✅ Система ПОЛНОСТЬЮ наблюдаема" else "❌ Система НЕ наблюдаема"
                                    resultScalarText = "rank(W_o) = $r из ${matA.size}\n$state"
                                    lastResultGrid = null; lastResultMatrix = wo; isError = false
                                    onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = wo))
                                    scrollToBottom()
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка"; scrollToBottom()
                                }
                            }
                        }
                    }

                    else -> {
                        SectionHeader("Синтез регуляторов и канонические формы (ТАУ)")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("Фробениус (A,B)", Modifier.weight(1f), colorType = 2) {
                                // Автоматически берем текущие матрицы A и первый столбец B с главного экрана
                                frobSize = size
                                frobSizeInput = size.toString()
                                frobA = resizeMatrix(matrixA, size, size)
                                frobB = List(size) { r -> listOf(matrixB.getOrNull(r)?.getOrNull(0) ?: "0") }
                                showFrobeniusDialog = true
                            }
                            AdaptiveBtn("Модальное упр-е", Modifier.weight(1f), colorType = 2) {
                                // Автоматически берем текущие матрицы A и первый столбец B с главного экрана
                                designSize = size
                                designSizeInput = size.toString()
                                designA = resizeMatrix(matrixA, size, size)
                                designB = List(size) { r -> listOf(matrixB.getOrNull(r)?.getOrNull(0) ?: "0") }
                                val initEigs = runCatching { math.eigenvalues(parse(designA)) }.getOrDefault(emptyList())
                                poleAssignments = List(size) { i -> initEigs.getOrNull(i)?.toString()?.replace(" ", "") ?: "-1" }
                                showPolesDialog = true
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("LQR-оптимизация (Уравнение Риккати CARE)", Modifier.weight(1f), colorType = 2) {
                                // Автоматически берем A, B (определяем число ненулевых столбцов m) и C (в Q) с главного экрана
                                lqrN = size
                                lqrNInput = size.toString()
                                val parsedB = runCatching { parse(matrixB) }.getOrNull()
                                val activeCols = if (parsedB != null) {
                                    val lastNonZeroCol = (size - 1 downTo 0).firstOrNull { col ->
                                        (0 until size).any { row -> abs(parsedB[row][col]) > 1e-12 }
                                    } ?: 0
                                    (lastNonZeroCol + 1).coerceIn(1, size)
                                } else 1
                                lqrM = activeCols
                                lqrMInput = lqrM.toString()
                                lqrAInput = resizeMatrix(matrixA, lqrN, lqrN)
                                lqrBInput = resizeMatrix(matrixB, lqrN, lqrM)
                                val parsedC = runCatching { parse(matrixC) }.getOrNull()
                                val isZeroC = parsedC == null || parsedC.all { row -> row.all { abs(it) < 1e-12 } }
                                lqrQInput = if (isZeroC) identityStringGrid(lqrN) else resizeMatrix(matrixC, lqrN, lqrN)
                                lqrRInput = identityStringGrid(lqrM)
                                showLqrDialog = true
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        SectionHeader("Высшие матричные функции от [$tabName]")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("e^$tabName", Modifier.weight(1f), colorType = 1) {
                                try {
                                    showMatrixResult(math.matrixExponential(parse(currentData)), "Экспонента e^$tabName")
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления e^A"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("ln($tabName)", Modifier.weight(1f), colorType = 1) {
                                try {
                                    val res = math.matrixLog(parse(currentData))
                                    if (res != null) {
                                        showMatrixResult(res, "Логарифм ln($tabName)")
                                    } else {
                                        lastResultTitle = "Логарифм ln($tabName)"
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = "Вещественный главный логарифм матрицы не существует (проверьте, что det($tabName) > 0 и нет отрицательных вещественных корней)."
                                        scrollToBottom()
                                    }
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления ln(A)"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("√$tabName", Modifier.weight(1f), colorType = 1) {
                                try {
                                    val res = math.matrixSqrt(parse(currentData))
                                    if (res != null) {
                                        showMatrixResult(res, "Корень √$tabName")
                                    } else {
                                        lastResultTitle = "Корень √$tabName"
                                        lastResultMatrix = null; lastResultGrid = null; isError = true
                                        resultScalarText = "Вещественный квадратный корень матрицы не существует."
                                        scrollToBottom()
                                    }
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления √A"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("${tabName}^k", Modifier.weight(1f), colorType = 1) { showPowerDialog = true }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AdaptiveBtn("sin($tabName)", Modifier.weight(1f), colorType = 1) {
                                try {
                                    showMatrixResult(math.matrixTrig(parse(currentData)).first, "Синус sin($tabName)")
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления sin(A)"; scrollToBottom()
                                }
                            }
                            AdaptiveBtn("cos($tabName)", Modifier.weight(1f), colorType = 1) {
                                try {
                                    showMatrixResult(math.matrixTrig(parse(currentData)).second, "Косинус cos($tabName)")
                                } catch (e: Exception) {
                                    lastResultMatrix = null; lastResultGrid = null; isError = true
                                    resultScalarText = e.message ?: "Ошибка вычисления cos(A)"; scrollToBottom()
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
            onTransferToA = { mat: Array<DoubleArray> -> transferSquareOrResize(onMatrixAChange, mat, "A") },
            onTransferToB = { mat: Array<DoubleArray> -> transferSquareOrResize(onMatrixBChange, mat, "B") },
            onTransferToC = { mat: Array<DoubleArray> -> transferSquareOrResize(onMatrixCChange, mat, "C") }
        )
    }

    if (showPowerDialog) {
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
                            .background(Color(0xFF0F172A))
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
                            val res = math.matrixPower(parse(currentData), k)
                            showMatrixResult(res, "Степень $tabName^$k")
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

    fun syncDialogToMain(newN: Int, newA: List<List<String>>, newB: List<List<String>>, newC: List<List<String>>? = null) {
        if (size != newN) onSizeChange(newN)
        onMatrixAChange(resizeMatrix(newA, newN, newN))
        val mergedB = List(newN) { r ->
            List(newN) { c ->
                if (r < newB.size && c < newB[r].size) newB[r][c]
                else matrixB.getOrNull(r)?.getOrNull(c) ?: "0"
            }
        }
        onMatrixBChange(mergedB)
        if (newC != null) {
            onMatrixCChange(resizeMatrix(newC, newN, newN))
        }
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
                                frobSize--; frobSizeInput = frobSize.toString()
                                frobA = resizeMatrix(frobA, frobSize, frobSize)
                                frobB = resizeMatrix(frobB, frobSize, 1)
                                syncDialogToMain(frobSize, frobA, frobB)
                            }
                        }
                        Text("${frobSize}×$frobSize", color = TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                        CounterButton("+", frobSize < 7) {
                            if (frobSize < 7) {
                                frobSize++; frobSizeInput = frobSize.toString()
                                frobA = resizeMatrix(frobA, frobSize, frobSize)
                                frobB = resizeMatrix(frobB, frobSize, 1)
                                syncDialogToMain(frobSize, frobA, frobB)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("МАТРИЦА A (${frobSize}×${frobSize})", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    MatrixGridInput(frobSize, frobSize, frobA, AccentCyan, 46.dp, 13.sp) { r, c, v ->
                        frobA = frobA.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncDialogToMain(frobSize, frobA, frobB)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("МАТРИЦА B (${frobSize}×1)", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    MatrixGridInput(frobSize, 1, frobB, AccentPurple, 46.dp, 13.sp) { r, _, v ->
                        frobB = frobB.mapIndexed { i, row -> if (i == r) listOf(v) else row }
                        syncDialogToMain(frobSize, frobA, frobB)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        syncDialogToMain(frobSize, frobA, frobB)
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
                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = aTilde))
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

    if (showPolesDialog) {
        AlertDialog(
            onDismissRequest = { showPolesDialog = false },
            title = { Text("Модальное управление (K)", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                val modalAnalysis = remember(designA, designB) {
                    runCatching {
                        val aMat = parse(designA)
                        val bMat = parse(designB)
                        val eigs = math.eigenvalues(aMat)
                        val flags = math.getControllableModeFlags(aMat, bMat, eigs)
                        val r = flags.count { it }
                        Triple(eigs, flags, r)
                    }.getOrNull()
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
                                designSize--; designSizeInput = designSize.toString()
                                designA = resizeMatrix(designA, designSize, designSize)
                                designB = resizeMatrix(designB, designSize, 1)
                                poleAssignments = List(designSize) { i -> poleAssignments.getOrElse(i) { "-1" } }
                                syncDialogToMain(designSize, designA, designB)
                            }
                        }
                        Text("${designSize}×$designSize", color = TextWhite, fontWeight = FontWeight.Bold)
                        CounterButton("+", designSize < 7) {
                            if (designSize < 7) {
                                designSize++; designSizeInput = designSize.toString()
                                designA = resizeMatrix(designA, designSize, designSize)
                                designB = resizeMatrix(designB, designSize, 1)
                                poleAssignments = List(designSize) { i -> poleAssignments.getOrElse(i) { "-1" } }
                                syncDialogToMain(designSize, designA, designB)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("A (${designSize}×${designSize})", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(designSize, designSize, designA, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        designA = designA.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncDialogToMain(designSize, designA, designB)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("B (${designSize}×1)", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(designSize, 1, designB, AccentPurple, 46.dp, 13.sp) { r, _, v ->
                        designB = designB.mapIndexed { i, row -> if (i == r) listOf(v) else row }
                        syncDialogToMain(designSize, designA, designB)
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
                                        .background(Color(0xFF0F172A))
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
                                        .background(Color(0xFF161F30))
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
                        syncDialogToMain(designSize, designA, designB)
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
                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = kMat))
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

    if (showLqrDialog) {
        AlertDialog(
            onDismissRequest = { showLqrDialog = false },
            title = { Text("LQR — оптимальное управление", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Матрица B может иметь любую размерность n×m (от 1 столбца до n×n). Данные синхронизируются с главным экраном.",
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
                                .background(Color(0xFF0B1322))
                                .border(1.dp, Color(0xFF24334D), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Состояния (n)", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CounterButton("−", lqrN > 2) {
                                    if (lqrN > 2) {
                                        lqrN--; lqrNInput = lqrN.toString()
                                        lqrAInput = resizeMatrix(lqrAInput, lqrN, lqrN)
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrQInput = resizeIdentityMatrix(lqrQInput, lqrN)
                                        syncDialogToMain(lqrN, lqrAInput, lqrBInput, lqrQInput)
                                    }
                                }
                                Text(
                                    "$lqrN",
                                    color = AccentCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                CounterButton("+", lqrN < 7) {
                                    if (lqrN < 7) {
                                        lqrN++; lqrNInput = lqrN.toString()
                                        lqrAInput = resizeMatrix(lqrAInput, lqrN, lqrN)
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrQInput = resizeIdentityMatrix(lqrQInput, lqrN)
                                        syncDialogToMain(lqrN, lqrAInput, lqrBInput, lqrQInput)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0B1322))
                                .border(1.dp, Color(0xFF24334D), RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Столбцов B (m)", color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CounterButton("−", lqrM > 1) {
                                    if (lqrM > 1) {
                                        lqrM--; lqrMInput = lqrM.toString()
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrRInput = resizeIdentityMatrix(lqrRInput, lqrM)
                                    }
                                }
                                Text(
                                    "$lqrM",
                                    color = AccentPurple,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                CounterButton("+", lqrM < 7) {
                                    if (lqrM < 7) {
                                        lqrM++; lqrMInput = lqrM.toString()
                                        lqrBInput = resizeMatrix(lqrBInput, lqrN, lqrM)
                                        lqrRInput = resizeIdentityMatrix(lqrRInput, lqrM)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            lqrN = size; lqrNInput = lqrN.toString()
                            lqrM = size; lqrMInput = lqrM.toString()
                            lqrAInput = resizeMatrix(matrixA, lqrN, lqrN)
                            lqrBInput = resizeMatrix(matrixB, lqrN, lqrM)
                            val parsedC = runCatching { parse(matrixC) }.getOrNull()
                            val isZeroC = parsedC == null || parsedC.all { row -> row.all { abs(it) < 1e-12 } }
                            lqrQInput = if (isZeroC) identityStringGrid(lqrN) else resizeMatrix(matrixC, lqrN, lqrN)
                            lqrRInput = identityStringGrid(lqrM)
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF192841)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            "📥 Взять полные A, B (${size}×$size), C с экрана",
                            color = AccentCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("A (${lqrN}×${lqrN})", color = AccentCyan, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrN, lqrAInput, AccentCyan, 42.dp, 12.sp) { r, c, v ->
                        lqrAInput = lqrAInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncDialogToMain(lqrN, lqrAInput, lqrBInput, lqrQInput)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("B (${lqrN}×${lqrM}) — матрица входов", color = AccentPurple, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrM, lqrBInput, AccentPurple, 42.dp, 12.sp) { r, c, v ->
                        lqrBInput = lqrBInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncDialogToMain(lqrN, lqrAInput, lqrBInput, lqrQInput)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Q (${lqrN}×${lqrN}, Q ≥ 0)", color = AccentAmber, fontWeight = FontWeight.Bold)
                    MatrixGridInput(lqrN, lqrN, lqrQInput, AccentAmber, 42.dp, 12.sp) { r, c, v ->
                        lqrQInput = lqrQInput.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, old -> if (j == c) v else old } else row }
                        syncDialogToMain(lqrN, lqrAInput, lqrBInput, lqrQInput)
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
                        syncDialogToMain(lqrN, lqrAInput, lqrBInput, lqrQInput)
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
                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = resultScalarText, resultMatrix = kMat))
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
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var lastResultTitle by remember { mutableStateOf("Результат") }
    var lastResultMatrix by remember { mutableStateOf<Array<DoubleArray>?>(null) }
    var resultScalarText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    fun scrollToBottom() {
        coroutineScope.launch {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val curRows = if (selectedTab == 0) rowsA else rowsB
    val curCols = if (selectedTab == 0) colsA else colsB
    val currentData = if (selectedTab == 0) matrixA else matrixB
    val currentAccent = if (selectedTab == 0) AccentCyan else AccentPurple
    val tabName = if (selectedTab == 0) "A" else "B"

    fun updateCurrent(updated: List<List<String>>) {
        if (selectedTab == 0) onMatrixAChange(updated) else onMatrixBChange(updated)
    }

    val calculatedCellSize = ((screenWidth - 44.dp) / curCols).coerceIn(44.dp, 62.dp)
    val cellFontSize = when {
        curCols >= 6 -> 11.sp
        curCols >= 5 -> 12.sp
        calculatedCellSize < 54.dp -> 13.sp
        else -> 16.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MatrixSelectChip("A (${rowsA}×$colsA)", selectedTab == 0, AccentCyan, Modifier.weight(1f)) { onTabChange(0) }
                MatrixSelectChip("B (${rowsB}×$colsB)", selectedTab == 1, AccentPurple, Modifier.weight(1f)) { onTabChange(1) }
            }
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    val tmpRows = rowsA; val tmpCols = colsA; val tmpMat = matrixA
                    onSizeAChange(rowsB, colsB); onMatrixAChange(matrixB)
                    onSizeBChange(tmpRows, tmpCols); onMatrixBChange(tmpMat)
                    Toast.makeText(context, "A ⇄ B поменяны местами", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("A ⇄ B", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Строк M:", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CounterButton("−", curRows > 1) {
                            if (curRows > 1) {
                                if (selectedTab == 0) onSizeAChange(rowsA - 1, colsA) else onSizeBChange(rowsB - 1, colsB)
                            }
                        }
                        Text("$curRows", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                        CounterButton("+", curRows < 7) {
                            if (curRows < 7) {
                                if (selectedTab == 0) onSizeAChange(rowsA + 1, colsA) else onSizeBChange(rowsB + 1, colsB)
                            }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Столб. N:", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CounterButton("−", curCols > 1) {
                            if (curCols > 1) {
                                if (selectedTab == 0) onSizeAChange(rowsA, colsA - 1) else onSizeBChange(rowsB, colsB - 1)
                            }
                        }
                        Text("$curCols", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp))
                        CounterButton("+", curCols < 7) {
                            if (curCols < 7) {
                                if (selectedTab == 0) onSizeAChange(rowsA, colsA + 1) else onSizeBChange(rowsB, colsB + 1)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip("➔ Вектор-строка (1×3)", AccentCyan, Color(0xFF1E293B), Modifier.weight(1f)) {
                if (selectedTab == 0) onSizeAChange(1, 3) else onSizeBChange(1, 3)
            }
            QuickActionChip("➔ Вектор-столбец (3×1)", AccentPurple, Color(0xFF1E293B), Modifier.weight(1f)) {
                if (selectedTab == 0) onSizeAChange(3, 1) else onSizeBChange(3, 1)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        MatrixGridInput(
            rows = curRows,
            cols = curCols,
            data = currentData,
            accentColor = currentAccent,
            cellSize = calculatedCellSize,
            fontSize = cellFontSize,
            onValueChange = { r, c, v ->
                val updated = currentData.mapIndexed { ri, row ->
                    if (ri == r) row.mapIndexed { ci, cur -> if (ci == c) v else cur } else row
                }
                updateCurrent(updated)
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QuickActionChip("🎲 Рандом", TextWhite, Color(0xFF1E293B), Modifier.weight(1f)) {
                updateCurrent(List(curRows) { List(curCols) { Random.nextInt(-9, 10).toString() } })
            }
            QuickActionChip("📋 Вставить", AccentCyan, AccentCyan.copy(alpha = 0.16f), Modifier.weight(1f)) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!clipText.isNullOrEmpty()) {
                    val parsed = parsePastedText(clipText, curRows, curCols)
                    if (parsed != null) {
                        updateCurrent(parsed)
                        Toast.makeText(context, "Вставлено в $tabName!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(context, "Не удалось распознать!", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Буфер пуст!", Toast.LENGTH_SHORT).show()
            }
            QuickActionChip("✕ Очистить", Color(0xFFF87171), Color(0xFF2A1621), Modifier.weight(1f)) {
                updateCurrent(List(curRows) { List(curCols) { "0" } })
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark.copy(alpha = 0.7f))
                .border(1.dp, Color(0xFF1F2D46), RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            SectionHeader("Матричные и векторные операции над A и B")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveBtn("A × B", Modifier.weight(1f), colorType = 1) {
                    try {
                        lastResultTitle = "Умножение A × B"
                        if (colsA != rowsB) {
                            lastResultMatrix = null
                            resultScalarText = "Нельзя умножить!\nЧисло столбцов A ($colsA) должно совпадать с числом строк B ($rowsB)."
                            isError = true
                        } else {
                            val res = math.multiply(parse(matrixA), parse(matrixB))
                            lastResultMatrix = res; resultScalarText = ""; isError = res == null
                            if (res != null) onAddToHistory(HistoryItem(title = lastResultTitle, resultText = formatMatrixRaw(res), resultMatrix = res))
                        }
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
                AdaptiveBtn("A + B", Modifier.weight(1f)) {
                    try {
                        lastResultTitle = "Сложение A + B"
                        if (rowsA != rowsB || colsA != colsB) {
                            lastResultMatrix = null
                            resultScalarText = "Нельзя сложить!\nРазмеры A (${rowsA}×$colsA) и B (${rowsB}×$colsB) не совпадают."
                            isError = true
                        } else {
                            val res = math.add(parse(matrixA), parse(matrixB))
                            lastResultMatrix = res; resultScalarText = ""; isError = res == null
                            if (res != null) onAddToHistory(HistoryItem(title = lastResultTitle, resultText = formatMatrixRaw(res), resultMatrix = res))
                        }
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
                AdaptiveBtn("A − B", Modifier.weight(1f)) {
                    try {
                        lastResultTitle = "Вычитание A − B"
                        if (rowsA != rowsB || colsA != colsB) {
                            lastResultMatrix = null
                            resultScalarText = "Нельзя вычесть!\nРазмеры A (${rowsA}×$colsA) и B (${rowsB}×$colsB) не совпадают."
                            isError = true
                        } else {
                            val res = math.subtract(parse(matrixA), parse(matrixB))
                            lastResultMatrix = res; resultScalarText = ""; isError = res == null
                            if (res != null) onAddToHistory(HistoryItem(title = lastResultTitle, resultText = formatMatrixRaw(res), resultMatrix = res))
                        }
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveBtn("a · b (Скалярное)", Modifier.weight(1f), colorType = 2) {
                    try {
                        lastResultTitle = "Скалярное произведение a · b"
                        val totalA = rowsA * colsA; val totalB = rowsB * colsB
                        if (totalA != totalB) {
                            lastResultMatrix = null
                            resultScalarText = "Ошибка! У векторов должно быть равное количество элементов ($totalA ≠ $totalB)."
                            isError = true
                        } else {
                            val dot = math.dotProduct(parse(matrixA), parse(matrixB))
                            val txt = "a · b = ${formatNumber(dot ?: 0.0)}"
                            lastResultMatrix = null; resultScalarText = txt; isError = false
                            onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                        }
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
                AdaptiveBtn("a ⨯ b (Векторное 3D)", Modifier.weight(1f), colorType = 2) {
                    try {
                        lastResultTitle = "Векторное произведение a ⨯ b"
                        val totalA = rowsA * colsA; val totalB = rowsB * colsB
                        if (totalA != 3 || totalB != 3) {
                            lastResultMatrix = null
                            resultScalarText = "Векторное произведение существует только для 3D векторов (3 элемента)!"
                            isError = true
                        } else {
                            val cross = math.crossProduct(parse(matrixA), parse(matrixB))
                            lastResultMatrix = cross; resultScalarText = ""; isError = cross == null
                            if (cross != null) onAddToHistory(HistoryItem(title = lastResultTitle, resultText = formatMatrixRaw(cross), resultMatrix = cross))
                        }
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            SectionHeader("Свойства выбранной матрицы [$tabName]")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveBtn("${tabName}ᵀ (Трансп.)", Modifier.weight(1f)) {
                    try {
                        lastResultTitle = "Транспонирование ${tabName}ᵀ"
                        val res = math.transpose(parse(currentData))
                        lastResultMatrix = res; resultScalarText = ""; isError = false
                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = formatMatrixRaw(res), resultMatrix = res))
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
                AdaptiveBtn("Rank($tabName)", Modifier.weight(1f)) {
                    try {
                        lastResultTitle = "Ранг rank($tabName)"
                        val res = math.rank(parse(currentData))
                        val txt = "rank($tabName) = $res"
                        lastResultMatrix = null; resultScalarText = txt; isError = false
                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                        scrollToBottom()
                    } catch (e: Exception) {
                        lastResultMatrix = null; resultScalarText = e.message ?: "Ошибка"; isError = true; scrollToBottom()
                    }
                }
                AdaptiveBtn("Норма ‖$tabName‖", Modifier.weight(1f)) {
                    try {
                        lastResultTitle = "Норма ‖$tabName‖"
                        val norm = math.norm(parse(currentData))
                        val txt = "‖$tabName‖ = ${formatNumber(norm)}"
                        lastResultMatrix = null; resultScalarText = txt; isError = false
                        onAddToHistory(HistoryItem(title = lastResultTitle, resultText = txt, resultMatrix = null))
                        scrollToBottom()
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
                    .background(Color(0xFF0B1322))
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
                        onValueChange = onScalarKChange,
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
                            lastResultTitle = "Умножение $scalarK × $tabName"
                            val k = scalarK.toDoubleOrNull() ?: 1.0
                            val res = math.multiplyScalar(parse(currentData), k)
                            lastResultMatrix = res; resultScalarText = ""; isError = false
                            onAddToHistory(HistoryItem(title = lastResultTitle, resultText = formatMatrixRaw(res), resultMatrix = res))
                            scrollToBottom()
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        ResultCardBlock(
            title = lastResultTitle,
            lastResultMatrix = lastResultMatrix,
            lastResultGrid = null,
            resultScalarText = resultScalarText,
            isError = isError,
            screenWidth = screenWidth,
            context = context,
            onClearResult = {
                lastResultMatrix = null
                resultScalarText = ""
                isError = false
            },
            onTransferToA = { mat: Array<DoubleArray> -> onSizeAChange(mat.size, mat[0].size); onMatrixAChange(matrixToPreciseStringList(mat)) },
            onTransferToB = { mat: Array<DoubleArray> -> onSizeBChange(mat.size, mat[0].size); onMatrixBChange(matrixToPreciseStringList(mat)) },
            onTransferToC = null
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
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
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
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) TextWhite else TextDim,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("История вычислений", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Всего записей: ${historyList.size}", color = TextDim, fontSize = 12.sp)
            }
            if (historyList.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF2D1622))
                        .border(1.dp, Color(0xFFF87171).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
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
                items(historyList.size) { index ->
                    val item = historyList[index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFF24334D))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.title, color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.time, color = TextDim, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "📋",
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable {
                                                val txt = if (item.resultMatrix != null) formatMatrixRaw(item.resultMatrix) else item.resultText
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Matrix Result", txt))
                                                Toast.makeText(context, "Скопировано в буфер!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (item.resultMatrix != null) {
                                HistoryMatrixGrid(item.resultMatrix)
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentCyan),
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = { onPasteToSquareA(item.resultMatrix) }
                                    ) { Text("➔ в A", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    Button(
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentPurple),
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = { onPasteToSquareB(item.resultMatrix) }
                                    ) { Text("➔ в B", color = AccentPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                    Button(
                                        modifier = Modifier.weight(1f).height(32.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, AccentAmber),
                                        contentPadding = PaddingValues(0.dp),
                                        onClick = { onPasteToSquareC(item.resultMatrix) }
                                    ) { Text("➔ в C", color = AccentAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }
                            } else {
                                Text(
                                    item.resultText,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
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
fun CounterButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) Color(0xFF1E293B) else Color.Transparent)
            .border(1.dp, if (enabled) AccentCyan.copy(alpha = 0.5f) else Color(0xFF24334D), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = if (enabled) TextWhite else TextDim.copy(alpha = 0.3f),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TabButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.2f) else Color.Transparent)
            .border(if (selected) 1.dp else 0.dp, if (selected) AccentCyan else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) AccentCyan else TextDim,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                        val cellVal = if (i < data.size && j < data[i].size) data[i][j] else "0"
                        var isFocused by remember { mutableStateOf(false) }
                        val dynamicFontSize = when {
                            cellVal.length > 5 -> 10.sp
                            cellVal.length > 3 -> 12.sp
                            else -> fontSize
                        }

                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CardDark)
                                .border(
                                    if (isFocused) 1.5.dp else 1.dp,
                                    if (isFocused) accentColor else Color(0xFF24334D),
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
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
    val alreadyHasFormattedMatrixInText = resultScalarText.contains("⎡") || resultScalarText.contains("⎣")

    val displayedScalarText = remember(resultScalarText, symbolicMode) {
        prettifyMathText(resultScalarText)
    }

    AnimatedVisibility(visible = hasResult, enter = fadeIn() + slideInVertically()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 28.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = if (isError) Color(0xFF331016) else CardDark),
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
                        color = if (isError) Color(0xFFFCA5A5) else AccentEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (lastResultMatrix != null && !isError && !alreadyHasFormattedMatrixInText) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(if (symbolicMode) AccentCyan.copy(alpha = 0.22f) else Color(0xFF1E293B))
                                    .border(
                                        1.dp,
                                        if (symbolicMode) AccentCyan else Color(0xFF334155),
                                        RoundedCornerShape(7.dp)
                                    )
                                    .clickable { symbolicMode = !symbolicMode }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
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
                                .background(Color(0xFF1E293B))
                                .clickable {
                                    val txt = when {
                                        lastResultGrid != null -> displayedScalarText + "\n" + lastResultGrid.joinToString("\n") { it.joinToString("\t") }
                                        displayedScalarText.isNotEmpty() && alreadyHasFormattedMatrixInText -> displayedScalarText
                                        lastResultMatrix != null -> formatMatrixRaw(lastResultMatrix, symbolicMode)
                                        else -> displayedScalarText
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Matrix Result", txt))
                                    Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("📋 Копировать", color = TextWhite, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF281A24))
                                .clickable { onClearResult() }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text("✕", color = Color(0xFFFCA5A5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (displayedScalarText.isNotEmpty()) {
                    Text(
                        displayedScalarText,
                        color = TextWhite,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (lastResultGrid != null) {
                    FormattedTableGrid(lastResultGrid)
                } else if (lastResultMatrix != null && !alreadyHasFormattedMatrixInText) {
                    MatrixResultGridAdaptive(lastResultMatrix, screenWidth, symbolicMode, isExpMode = title.contains("Экспонента"))
                }

                if (lastResultMatrix != null && !isError) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF24334D), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Перенести матрицу (${lastResultMatrix.size}×${lastResultMatrix[0].size}) в:",
                        color = TextDim,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentCyan),
                            contentPadding = PaddingValues(0.dp),
                            onClick = { onTransferToA(lastResultMatrix) }
                        ) { Text("➔ в A", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        Button(
                            modifier = Modifier.weight(1f).height(34.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentPurple.copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, AccentPurple),
                            contentPadding = PaddingValues(0.dp),
                            onClick = { onTransferToB(lastResultMatrix) }
                        ) { Text("➔ в B", color = AccentPurple, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                        if (onTransferToC != null) {
                            Button(
                                modifier = Modifier.weight(1f).height(34.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber.copy(alpha = 0.18f)),
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

@Composable
fun MatrixResultGridAdaptive(
    matrix: Array<DoubleArray>,
    screenWidth: Dp,
    symbolicMode: Boolean = false,
    isExpMode: Boolean = false
) {
    val rows = matrix.size; if (rows == 0) return; val cols = matrix[0].size; if (cols == 0) return

    val cellWidth = ((screenWidth - 68.dp) / cols).coerceIn(78.dp, 112.dp)
    val cellHeight = if (symbolicMode) 48.dp else 38.dp

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
                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(cellHeight)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0B1322))
                                .border(
                                    1.dp,
                                    if (symbolicMode) AccentCyan.copy(alpha = 0.3f) else Color(0xFF1F2E47),
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
                                val token = toSymbolicToken(value, isExpMode)
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

    fun findExactFraction(x: Double, maxQ: Long = 9999L, tol: Double = 1e-6): Pair<Long, Long>? {
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

    // 1. Простые точные дроби со знаменателем до 16 (1/2, 1/3, 1/4 и т.д.)
    findExactFraction(absVal, maxQ = 16L, tol = 1e-6)?.let { (p, q) ->
        return SymbolicToken(sign = signStr, num = p.toString(), den = q.toString())
    }

    // 2. Формулы с экспонентой e^k ИЩЕМ ТОЛЬКО тогда, когда нажата кнопка "Экспонента e^A"!
    // Чтобы в LQR или A^-1 никогда случайно не появлялась буква e!
    if (isExpMode) {
        val powers = (1..25).toList() + (-1 downTo -15).toList()
        for (k in powers) {
            val ePow = kotlin.math.exp(k.toDouble())
            val eStr = if (k == 1) "e" else "e${toSuperscript(k.toString())}"

            for (q in 1L..16L) {
                val scaled = value * q.toDouble()
                for (a in -16L..16L) {
                    if (a == 0L) continue
                    val b = kotlin.math.round(scaled - a.toDouble() * ePow).toLong()
                    if (abs(b) > 16L) continue
                    val approx = (a.toDouble() * ePow + b.toDouble()) / q.toDouble()
                    val relErr = abs(value - approx) / maxOf(1.0, abs(value))
                    if (relErr <= 1e-4) {
                        val g = gcd(gcd(a, b), q)
                        val a1 = a / g; val b1 = b / g; val q1 = q / g
                        val aTerm = when (a1) {
                            1L -> eStr
                            -1L -> "−$eStr"
                            else -> "$a1$eStr"
                        }
                        val expr = when {
                            b1 == 0L -> aTerm
                            b1 > 0L -> "$aTerm+$b1"
                            else -> "$aTerm−${abs(b1)}"
                        }
                        return if (q1 == 1L) {
                            SymbolicToken(plainText = expr)
                        } else if (b1 == 0L) {
                            val s = if (a1 < 0) "−" else ""
                            SymbolicToken(sign = s, num = abs(a1).toString(), den = q1.toString(), suffix = eStr)
                        } else {
                            SymbolicToken(num = expr, den = q1.toString())
                        }
                    }
                }
            }
        }

        // Комбинации двух разных степеней e: (a * e^k1 + b * e^k2) / q
        val smallPowers = listOf(1, 2, 3, 4, 5, 6, 7, 8, -1, -2, -3)
        for (i in smallPowers.indices) {
            for (j in i + 1 until smallPowers.size) {
                val k1 = smallPowers[i]; val k2 = smallPowers[j]
                val e1 = kotlin.math.exp(k1.toDouble()); val e2 = kotlin.math.exp(k2.toDouble())
                val s1 = if (k1 == 1) "e" else "e${toSuperscript(k1.toString())}"
                val s2 = if (k2 == 1) "e" else "e${toSuperscript(k2.toString())}"
                for (q in 1L..6L) {
                    val scaled = value * q.toDouble()
                    for (a in -6L..6L) {
                        if (a == 0L) continue
                        val b = kotlin.math.round((scaled - a.toDouble() * e1) / e2).toLong()
                        if (b == 0L || abs(b) > 6L) continue
                        val approx = (a.toDouble() * e1 + b.toDouble() * e2) / q.toDouble()
                        if (abs(value - approx) / maxOf(1.0, abs(value)) <= 1e-4) {
                            val g = gcd(gcd(a, b), q)
                            val a1 = a / g; val b1 = b / g; val q1 = q / g
                            val t1 = when (a1) { 1L -> s1; -1L -> "−$s1"; else -> "$a1$s1" }
                            val t2 = when { b1 == 1L -> "+$s2"; b1 == -1L -> "−$s2"; b1 > 0 -> "+$b1$s2"; else -> "−${abs(b1)}$s2" }
                            val expr = "$t1$t2"
                            return if (q1 == 1L) SymbolicToken(plainText = expr)
                            else SymbolicToken(num = expr, den = q1.toString())
                        }
                    }
                }
            }
        }
    }

    // 3. Корни и квадратичные иррациональности вида (a * √d + b) / q (например: √3, 1+√2, (1+√5)/2)
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
                if (abs(value - approx) / maxOf(1.0, abs(value)) <= 1e-6) {
                    val g = gcd(gcd(a, b), q)
                    val a1 = a / g; val b1 = b / g; val q1 = q / g
                    val aTerm = when (a1) {
                        1L -> rStr
                        -1L -> "−$rStr"
                        else -> "$a1$rStr"
                    }
                    val expr = when {
                        b1 == 0L -> aTerm
                        b1 > 0L -> "$b1+$aTerm".replace("+-", "−").replace("+−", "−")
                        else -> "${b1}+$aTerm".replace("+-", "−").replace("+−", "−")
                    }
                    return if (q1 == 1L) {
                        SymbolicToken(plainText = expr)
                    } else if (b1 == 0L) {
                        val s = if (a1 < 0) "−" else ""
                        SymbolicToken(sign = s, num = abs(a1).toString(), den = q1.toString(), suffix = rStr)
                    } else {
                        SymbolicToken(num = expr, den = q1.toString())
                    }
                }
            }
        }
    }

    // 4. Все остальные рациональные дроби со знаменателем до 9999
    findExactFraction(absVal, maxQ = 9999L, tol = 1e-3)?.let { (p, q) ->
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

@Composable
fun AdaptiveBtn(text: String, modifier: Modifier = Modifier, colorType: Int = 0, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val prettyLabel = remember(text) { prettifyMathText(text) }

    val bgColor = when (colorType) {
        2 -> Color(0xFF1F1B44)
        else -> Color(0xFF192841)
    }
    val borderColor = when (colorType) {
        2 -> AccentPurple.copy(alpha = 0.6f)
        else -> AccentCyan.copy(alpha = 0.6f)
    }
    val txtColor = when (colorType) {
        2 -> Color(0xFFD2C4FF)
        else -> AccentCyan
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

fun toSuperscript(exp: String): String {
    val map = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '−' to '⁻', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'k' to 'ᵏ', 'i' to 'ⁱ', 'A' to 'ᴬ', 'B' to 'ᴮ', 'C' to 'ᶜ', 'T' to 'ᵀ'
    )
    return exp.map { map[it] ?: it }.joinToString("")
}

fun toSubscript(sub: String): String {
    val map = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', 'i' to 'ᵢ', 'c' to '꜀', 'o' to 'ₒ'
    )
    return sub.map { map[it] ?: it }.joinToString("")
}

fun prettifyMathText(input: String): String {
    if (input.isEmpty()) return input
    var s = input
    s = Regex("\\^\\(?([+-]?[0-9a-zA-Z]+)\\)?").replace(s) { m ->
        toSuperscript(m.groupValues[1])
    }
    s = Regex("([λvμ])_([0-9i]+)").replace(s) { m ->
        m.groupValues[1] + toSubscript(m.groupValues[2])
    }
    return s
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
    val formatted = matrix.map { row -> row.map { if (symbolic) formatSymbolic(it) else formatNumber(it) } }
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
