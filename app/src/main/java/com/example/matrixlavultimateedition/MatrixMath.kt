package com.example.matrixcalculator

import java.util.Locale
import kotlin.math.*

class MatrixMath {
    fun identityMatrix(n: Int): Array<DoubleArray> {
        require(n >= 0) { "Размер матрицы не может быть отрицательным" }
        return Array(n) { i -> DoubleArray(n) { j -> if (i == j) 1.0 else 0.0 } }
    }

    private fun isRectangular(a: Array<DoubleArray>): Boolean =
        a.isNotEmpty() && a[0].isNotEmpty() && a.all { it.size == a[0].size }

    private fun requireFinite(a: Array<DoubleArray>, name: String = "Матрица") {
        require(isRectangular(a)) { "$name должна быть непустой прямоугольной матрицей" }
        require(a.all { row -> row.all { it.isFinite() } }) { "$name содержит NaN или бесконечность" }
    }

    fun add(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray>? {
        if (!isRectangular(a) || !isRectangular(b) || a.size != b.size || a[0].size != b[0].size) return null
        if (a.any { row -> row.any { !it.isFinite() } } || b.any { row -> row.any { !it.isFinite() } }) return null
        val out = Array(a.size) { i -> DoubleArray(a[0].size) { j -> a[i][j] + b[i][j] } }
        return out.takeIf { it.all { row -> row.all { v -> v.isFinite() } } }
    }

    fun subtract(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray>? {
        if (!isRectangular(a) || !isRectangular(b) || a.size != b.size || a[0].size != b[0].size) return null
        if (a.any { row -> row.any { !it.isFinite() } } || b.any { row -> row.any { !it.isFinite() } }) return null
        val out = Array(a.size) { i -> DoubleArray(a[0].size) { j -> a[i][j] - b[i][j] } }
        return out.takeIf { it.all { row -> row.all { v -> v.isFinite() } } }
    }

    fun multiply(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray>? {
        if (!isRectangular(a) || !isRectangular(b)) return null
        val r1 = a.size; val c1 = a[0].size; val r2 = b.size; val c2 = b[0].size
        if (c1 != r2) return null
        if (a.any { row -> row.any { !it.isFinite() } } || b.any { row -> row.any { !it.isFinite() } }) return null
        val result = Array(r1) { DoubleArray(c2) }
        for (i in 0 until r1) {
            for (j in 0 until c2) {
                var sum = 0.0
                for (k in 0 until c1) sum += a[i][k] * b[k][j]
                result[i][j] = sum
            }
        }
        return result.takeIf { it.all { row -> row.all { v -> v.isFinite() } } }
    }

    fun multiplyScalar(a: Array<DoubleArray>, k: Double): Array<DoubleArray> {
        require(isRectangular(a)) { "Матрица должна быть непустой и прямоугольной" }
        require(a.all { row -> row.all { it.isFinite() } }) { "Матрица содержит NaN или бесконечность" }
        require(k.isFinite()) { "Множитель должен быть конечным числом" }
        val result = Array(a.size) { i -> DoubleArray(a[0].size) { j -> a[i][j] * k } }
        require(result.all { row -> row.all { it.isFinite() } }) { "Переполнение при умножении на скаляр" }
        return result
    }

    fun transpose(a: Array<DoubleArray>): Array<DoubleArray> {
        require(isRectangular(a)) { "Матрица должна быть непустой и прямоугольной" }
        require(a.all { row -> row.all { it.isFinite() } }) { "Матрица содержит NaN или бесконечность" }
        val r = a.size; val c = a[0].size
        val res = Array(c) { DoubleArray(r) }
        for (i in 0 until r) {
            for (j in 0 until c) res[j][i] = a[i][j]
        }
        return res
    }

    fun determinant(matrix: Array<DoubleArray>): Double? {
        val n = matrix.size

        if (n == 0 || matrix.any { it.size != n }) {
            return null
        }

        require(matrix.all { row -> row.all { it.isFinite() } }) {
            "Матрица содержит NaN или бесконечность"
        }

        val a = Array(n) { matrix[it].clone() }

        var sign = 1.0

        for (k in 0 until n) {
            var pivotRow = k

            for (i in k + 1 until n) {
                if (abs(a[i][k]) > abs(a[pivotRow][k])) {
                    pivotRow = i
                }
            }

            if (a[pivotRow][k] == 0.0) {
                return 0.0
            }

            if (pivotRow != k) {
                val temp = a[k]
                a[k] = a[pivotRow]
                a[pivotRow] = temp

                sign = -sign
            }

            val pivot = a[k][k]

            for (i in k + 1 until n) {
                val factor = a[i][k] / pivot

                a[i][k] = 0.0

                for (j in k + 1 until n) {
                    a[i][j] -= factor * a[k][j]
                }
            }
        }

        var result = sign

        for (i in 0 until n) {
            result *= a[i][i]
            if (!result.isFinite()) return null
        }
        if (result == 0.0 && (0 until n).all { a[it][it] != 0.0 }) return null // underflow, not a certified zero determinant
        return result
    }

    fun inverse(data: Array<DoubleArray>): Array<DoubleArray>? {
        val n = data.size

        if (n == 0 || data.any { it.size != n }) {
            return null
        }

        require(data.all { row -> row.all { it.isFinite() } }) {
            "Матрица содержит NaN или бесконечность"
        }

        val aug = Array(n) { i ->
            DoubleArray(2 * n) { j ->
                when {
                    j < n -> data[i][j]
                    j - n == i -> 1.0
                    else -> 0.0
                }
            }
        }

        // Масштабирование строк для выбора ведущего элемента.
        val scales = DoubleArray(n) { i ->
            data[i].maxOf { abs(it) }
        }

        if (scales.any { it == 0.0 }) {
            return null
        }

        for (col in 0 until n) {
            var pivotRow = col
            var maxRatio = -1.0

            for (row in col until n) {
                val ratio = abs(aug[row][col]) / scales[row]

                if (ratio > maxRatio) {
                    maxRatio = ratio
                    pivotRow = row
                }
            }

            if (aug[pivotRow][col] == 0.0) {
                return null
            }

            if (pivotRow != col) {
                val temp = aug[col]
                aug[col] = aug[pivotRow]
                aug[pivotRow] = temp

                val tempScale = scales[col]
                scales[col] = scales[pivotRow]
                scales[pivotRow] = tempScale
            }

            val pivot = aug[col][col]

            for (j in 0 until 2 * n) {
                aug[col][j] /= pivot
            }

            for (row in 0 until n) {
                if (row == col) continue

                val factor = aug[row][col]

                for (j in 0 until 2 * n) {
                    aug[row][j] -= factor * aug[col][j]
                }

                aug[row][col] = 0.0
            }
        }

        val result = Array(n) { i ->
            DoubleArray(n) { j ->
                aug[i][j + n]
            }
        }

        if (result.any { row -> row.any { !it.isFinite() } }) {
            return null
        }

// Проверяем оба произведения:
// A * A^(-1) ≈ I
// A^(-1) * A ≈ I

        val left = multiply(data, result) ?: return null
        val right = multiply(result, data) ?: return null

        val matrixNorm = norm(data)
        val inverseNorm = norm(result)

        if (!matrixNorm.isFinite() || !inverseNorm.isFinite()) {
            return null
        }

        val conditionEstimate = matrixNorm * inverseNorm

        if (!conditionEstimate.isFinite()) {
            return null
        }

        var leftError = 0.0
        var rightError = 0.0

        for (i in 0 until n) {
            for (j in 0 until n) {
                val expected = if (i == j) 1.0 else 0.0

                leftError = hypot(
                    leftError,
                    left[i][j] - expected
                )

                rightError = hypot(
                    rightError,
                    right[i][j] - expected
                )
            }
        }

// Оценка ошибки с учётом масштаба и размерности.
// При очень плохой обусловленности проверка может
// оказаться недостаточно строгой для практических задач.
        val tolerance = 100.0 * n * Math.ulp(1.0) * max(1.0, conditionEstimate)
        // If round-off amplification is already large, a plausible-looking inverse
        // is not an acceptable answer for downstream control calculations.
        if (tolerance > 1e-6) return null

        if (
            !leftError.isFinite() ||
            !rightError.isFinite() ||
            leftError > tolerance ||
            rightError > tolerance
        ) {
            return null
        }

        return result
    }

    /** Numerical rank from singular values computed by one-sided Jacobi rotations.
     * The threshold is relative to the largest singular value; exact rank is not
     * a numerically observable concept for nearly rank-deficient floating data.
     */
    fun rank(matrix: Array<DoubleArray>): Int {
        if (matrix.isEmpty()) return 0
        val cols = matrix[0].size
        require(matrix.all { it.size == cols }) { "Строки матрицы имеют разную длину" }
        require(matrix.all { row -> row.all { it.isFinite() } }) { "Матрица содержит NaN или бесконечность" }
        if (cols == 0) return 0
        val singular = singularValues(matrix)
        val largest = singular.maxOrNull() ?: 0.0
        if (largest == 0.0) return 0
        val tol = max(matrix.size, cols) * Math.ulp(1.0) * largest * 32.0
        return singular.count { it > tol }
    }

    /** Singular values via cyclic one-sided Jacobi SVD. No A^T*A is formed. */
    private fun singularValues(input: Array<DoubleArray>): DoubleArray {
        val m = input.size
        val n = input[0].size
        val scale = input.maxOf { row -> row.maxOf { abs(it) } }
        if (scale == 0.0) return DoubleArray(min(m, n))
        val b = Array(m) { i -> DoubleArray(n) { j -> input[i][j] / scale } }
        val eps = 8.0 * Math.ulp(1.0)
        var converged = false
        repeat(max(30, 8 * n)) {
            var changed = false
            for (p in 0 until n) for (q in p + 1 until n) {
                var alpha = 0.0; var beta = 0.0; var gamma = 0.0
                for (i in 0 until m) {
                    val x = b[i][p]; val y = b[i][q]
                    alpha += x * x; beta += y * y; gamma += x * y
                }
                if (alpha == 0.0 || beta == 0.0 || abs(gamma) <= eps * sqrt(alpha * beta)) continue
                val zeta = (beta - alpha) / (2.0 * gamma)
                val t = if (zeta == 0.0) 1.0 else kotlin.math.sign(zeta) / (abs(zeta) + hypot(1.0, zeta))
                val c = 1.0 / hypot(1.0, t); val sn = c * t
                for (i in 0 until m) {
                    val x = b[i][p]; val y = b[i][q]
                    b[i][p] = c * x - sn * y
                    b[i][q] = sn * x + c * y
                }
                changed = true
            }
            if (!changed) { converged = true; return@repeat }
        }
        // Singular values remain usable if the final sweep reached machine precision.
        val values = DoubleArray(n) { j ->
            var v = 0.0
            for (i in 0 until m) v = hypot(v, b[i][j])
            v * scale
        }
        if (!converged) throw ArithmeticException("Односторонний SVD Якоби не сошёлся; численный ранг не возвращён")
        if (values.any { !it.isFinite() }) throw ArithmeticException("SVD получил неконечные сингулярные значения")
        return values.sortedDescending().take(min(m, n)).toDoubleArray()
    }
    fun trace(a: Array<DoubleArray>): Double? {
        if (a.isEmpty() || a.any { it.size != a.size } || a.any { row -> row.any { !it.isFinite() } }) return null
        var sum = 0.0
        for (i in a.indices) sum += a[i][i]
        return sum.takeIf { it.isFinite() }
    }

    // Устойчивая норма Фробениуса: не возводит большие числа в квадрат.
    fun norm(a: Array<DoubleArray>): Double {
        var result = 0.0
        for (row in a) for (value in row) result = hypot(result, value)
        return result
    }

    fun spectralNorm(a: Array<DoubleArray>): Double? {
        if (!isRectangular(a) || a.any { row -> row.any { !it.isFinite() } }) return null
        return try { singularValues(a).maxOrNull() ?: 0.0 } catch (_: ArithmeticException) { null }
    }

    fun dotProduct(a: Array<DoubleArray>, b: Array<DoubleArray>): Double? { val flatA = a.flatMap { it.toList() }; val flatB = b.flatMap { it.toList() }; if (flatA.size != flatB.size) return null; return flatA.indices.sumOf { flatA[it] * flatB[it] } }
    fun crossProduct(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray>? { val flatA = a.flatMap { it.toList() }; val flatB = b.flatMap { it.toList() }; if (flatA.size != 3 || flatB.size != 3) return null; return arrayOf(doubleArrayOf(flatA[1] * flatB[2] - flatA[2] * flatB[1], flatA[2] * flatB[0] - flatA[0] * flatB[2], flatA[0] * flatB[1] - flatA[1] * flatB[0])) }

    /** Solves A X = B by scaled partial-pivoting Gaussian elimination. */
    private fun solveLinear(aInput: Array<DoubleArray>, bInput: Array<DoubleArray>): Array<DoubleArray>? {
        val n = aInput.size
        if (n == 0 || aInput.any { it.size != n } || bInput.size != n || bInput.any { it.size != bInput[0].size }) return null
        val m = bInput[0].size
        val a = Array(n) { aInput[it].clone() }
        val b = Array(n) { bInput[it].clone() }
        val scales = DoubleArray(n) { i -> a[i].maxOf { abs(it) } }
        if (scales.any { it == 0.0 }) return null
        for (k in 0 until n) {
            var pivot = k
            for (i in k + 1 until n) if (abs(a[i][k]) / scales[i] > abs(a[pivot][k]) / scales[pivot]) pivot = i
            if (abs(a[pivot][k]) <= Math.ulp(1.0) * scales[pivot] * n) return null
            if (pivot != k) { val tr=a[k]; a[k]=a[pivot]; a[pivot]=tr; val tb=b[k]; b[k]=b[pivot]; b[pivot]=tb; val ts=scales[k]; scales[k]=scales[pivot]; scales[pivot]=ts }
            for (i in k + 1 until n) {
                val f = a[i][k] / a[k][k]
                a[i][k] = 0.0
                for (j in k + 1 until n) a[i][j] -= f * a[k][j]
                for (j in 0 until m) b[i][j] -= f * b[k][j]
            }
        }
        val x = Array(n) { DoubleArray(m) }
        for (i in n - 1 downTo 0) for (j in 0 until m) {
            var v = b[i][j]
            for (k in i + 1 until n) v -= a[i][k] * x[k][j]
            x[i][j] = v / a[i][i]
            if (!x[i][j].isFinite()) return null
        }
        return x
    }

    private fun oneNorm(a: Array<DoubleArray>): Double {
        var best = 0.0
        for (j in a[0].indices) { var sum = 0.0; for (i in a.indices) sum += abs(a[i][j]); best = max(best, sum) }
        return best
    }

    // --- ВЫСШИЕ МАТРИЧНЫЕ ФУНКЦИИ ---
    fun matrixPower(
        a: Array<DoubleArray>,
        k: Int
    ): Array<DoubleArray>? {
        val n = a.size

        if (n == 0 || a.any { it.size != n } || a.any { row -> row.any { !it.isFinite() } }) return null

        if (k == 0) return identityMatrix(n)

        if (k == Int.MIN_VALUE) return null

        val base = if (k < 0) {
            inverse(a) ?: return null
        } else {
            a
        }

        var exponent = abs(k)
        var result = identityMatrix(n)
        var current = Array(n) { base[it].clone() }

        while (exponent > 0) {
            if (exponent % 2 == 1) {
                result = multiply(result, current) ?: return null
            }

            exponent /= 2

            if (exponent > 0) {
                current = multiply(current, current) ?: return null
            }
        }

        return result
    }

    fun matrixSqrt(a: Array<DoubleArray>): Array<DoubleArray>? =
        MatrixSquareRoot.computeReal(a)

    fun matrixSqrtComplex(
        a: Array<DoubleArray>
    ): Array<Array<Complex>>? =
        MatrixSquareRoot.compute(a)?.root

    /** Principal matrix logarithm by inverse scaling-and-squaring and the atanh series.
     * Returns null when a required principal real square root/inverse is unavailable.
     */
    fun matrixLog(a: Array<DoubleArray>): Array<DoubleArray>? {
        val n = a.size
        if (n == 0 || a.any { it.size != n } || a.any { row -> row.any { !it.isFinite() } }) return null
        val id = identityMatrix(n)
        var x = Array(n) { a[it].clone() }
        var roots = 0
        while (norm(subtract(x, id) ?: return null) > 0.25 && roots < 30) {
            x = matrixSqrt(x) ?: return null
            roots++
        }
        val delta = subtract(x, id) ?: return null
        val denominator = add(x, id) ?: return null
        val y = solveLinear(denominator, delta) ?: return null
        val y2 = multiply(y, y) ?: return null
        var power = Array(n) { y[it].clone() }
        var result = Array(n) { y[it].clone() }
        var converged = false
        for (k in 1..500) {
            power = multiply(power, y2) ?: return null
            val term = multiplyScalar(power, 1.0 / (2.0 * k + 1.0))
            result = add(result, term) ?: return null
            if (norm(term) <= 2.0 * Math.ulp(1.0) * max(1.0, norm(result))) { converged = true; break }
        }
        if (!converged) return null
        val factor = 2.0 * 2.0.pow(roots)
        val logarithm = multiplyScalar(result, factor)
        if (logarithm.any { row -> row.any { !it.isFinite() } }) return null
        val reconstructed = matrixExponential(logarithm) ?: return null
        val backwardError = norm(subtract(reconstructed, a) ?: return null) / max(1.0, norm(a))
        return logarithm.takeIf { backwardError.isFinite() && backwardError <= 1e-7 }
    }

    /** Matrix sine/cosine by scaled Taylor series with an explicit tail test. */
    fun matrixTrig(a: Array<DoubleArray>): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        require(a.isNotEmpty() && a.all { it.size == a.size } && a.all { row -> row.all { it.isFinite() } }) {
            "Для sin/cos нужна конечная квадратная матрица"
        }
        val n = a.size
        var scaled = Array(n) { a[it].clone() }
        var squarings = 0
        while (norm(scaled) > 0.5 && squarings < 1023) {
            scaled = multiplyScalar(scaled, 0.5)
            squarings++
        }
        if (!norm(scaled).isFinite()) throw ArithmeticException("Не удалось масштабировать матрицу")
        val a2 = multiply(scaled, scaled) ?: throw ArithmeticException("Ошибка вычисления A²")
        var sinTerm = Array(n) { scaled[it].clone() }
        var cosTerm = identityMatrix(n)
        var sinResult = Array(n) { sinTerm[it].clone() }
        var cosResult = identityMatrix(n)
        var converged = false
        for (k in 1..100) {
            sinTerm = multiplyScalar(multiply(sinTerm, a2) ?: throw ArithmeticException("Ошибка ряда sin"), -1.0 / ((2.0 * k) * (2.0 * k + 1.0)))
            cosTerm = multiplyScalar(multiply(cosTerm, a2) ?: throw ArithmeticException("Ошибка ряда cos"), -1.0 / ((2.0 * k - 1.0) * (2.0 * k)))
            sinResult = add(sinResult, sinTerm) ?: throw ArithmeticException("Ошибка накопления sin")
            cosResult = add(cosResult, cosTerm) ?: throw ArithmeticException("Ошибка накопления cos")
            val threshold = 4.0 * Math.ulp(1.0) * max(1.0, max(norm(sinResult), norm(cosResult)))
            if (max(norm(sinTerm), norm(cosTerm)) <= threshold) { converged = true; break }
        }
        if (!converged) throw ArithmeticException("Ряд матричных sin/cos не достиг заданной точности")
        val id = identityMatrix(n)
        repeat(squarings) {
            val nextSin = multiplyScalar(multiply(sinResult, cosResult) ?: throw ArithmeticException("Ошибка удвоения sin"), 2.0)
            val nextCos = subtract(multiplyScalar(multiply(cosResult, cosResult) ?: throw ArithmeticException("Ошибка удвоения cos"), 2.0), id)
                ?: throw ArithmeticException("Ошибка удвоения cos")
            sinResult = nextSin; cosResult = nextCos
            if (sinResult.any { row -> row.any { !it.isFinite() } } || cosResult.any { row -> row.any { !it.isFinite() } })
                throw ArithmeticException("Переполнение при вычислении матричных sin/cos")
        }
        val identityResidual = subtract(
            add(multiply(sinResult, sinResult) ?: throw ArithmeticException("Ошибка проверки sin²"),
                multiply(cosResult, cosResult) ?: throw ArithmeticException("Ошибка проверки cos²"))
                ?: throw ArithmeticException("Ошибка проверки тождества sin²+cos²"), id
        ) ?: throw ArithmeticException("Ошибка проверки тождества sin²+cos²")
        val identityScale = max(1.0, norm(sinResult) * norm(sinResult) + norm(cosResult) * norm(cosResult))
        if (norm(identityResidual) > 1e-8 * identityScale)
            throw ArithmeticException("Матричные sin/cos не прошли проверку sin(A)² + cos(A)² = I")
        return Pair(sinResult, cosResult)
    }

    /** Higham scaling-and-squaring with the diagonal [13/13] Padé approximant. */
    fun matrixExponential(a: Array<DoubleArray>): Array<DoubleArray>? {
        if (a.isEmpty() || a.any { it.size != a.size } || a.any { row -> row.any { !it.isFinite() } }) return null
        val n = a.size
        val theta13 = 5.371920351148152
        val norm1 = oneNorm(a)
        if (!norm1.isFinite()) return null
        val squarings = if (norm1 <= theta13) 0 else ceil(ln(norm1 / theta13) / ln(2.0)).toInt().coerceAtLeast(0)
        if (squarings > 1023) return null
        val x = multiplyScalar(a, 2.0.pow(-squarings))
        val x2 = multiply(x, x) ?: return null
        val x4 = multiply(x2, x2) ?: return null
        val x6 = multiply(x4, x2) ?: return null
        val id = identityMatrix(n)
        val b = doubleArrayOf(64764752532480000.0, 32382376266240000.0, 7771770303897600.0, 1187353796428800.0, 129060195264000.0, 10559470521600.0, 670442572800.0, 33522128640.0, 1323241920.0, 40840800.0, 960960.0, 16380.0, 182.0, 1.0)
        fun lin(vararg terms: Pair<Double, Array<DoubleArray>>): Array<DoubleArray>? {
            var out = Array(n) { DoubleArray(n) }
            for ((coef, mat) in terms) out = add(out, multiplyScalar(mat, coef)) ?: return null
            return out
        }
        val uInner = lin(b[13] to x6, b[11] to x4, b[9] to x2) ?: return null
        val uPoly = multiply(x6, uInner) ?: return null
        val uTail = lin(b[7] to x6, b[5] to x4, b[3] to x2, b[1] to id) ?: return null
        val u = multiply(x, add(uPoly, uTail) ?: return null) ?: return null
        val vInner = lin(b[12] to x6, b[10] to x4, b[8] to x2) ?: return null
        val vPoly = multiply(x6, vInner) ?: return null
        val vTail = lin(b[6] to x6, b[4] to x4, b[2] to x2, b[0] to id) ?: return null
        val v = add(vPoly, vTail) ?: return null
        val numerator = add(v, u) ?: return null
        val denominator = subtract(v, u) ?: return null
        var result = solveLinear(denominator, numerator) ?: return null
        repeat(squarings) { result = multiply(result, result) ?: return null }
        return result.takeIf { it.all { row -> row.all { v0 -> v0.isFinite() } } }
    }

    // --- КРИТЕРИИ КАЛМАНА (ТАУ) ---
    fun controllability(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>
    ): Array<DoubleArray> {
        val n = a.size

        require(n > 0 && a.all { it.size == n } && a.all { row -> row.all { it.isFinite() } }) {
            "Матрица A должна быть конечной квадратной матрицей"
        }

        require(b.size == n && b.isNotEmpty() && b.all { row -> row.all { it.isFinite() } }) {
            "Матрица B должна иметь $n строк"
        }

        val m = b[0].size

        require(m > 0 && b.all { it.size == m }) {
            "Некорректная размерность матрицы B"
        }

        val wc = Array(n) { DoubleArray(n * m) }
        var current = Array(n) { b[it].clone() }

        for (k in 0 until n) {
            for (i in 0 until n) {
                for (j in 0 until m) {
                    wc[i][k * m + j] = current[i][j]
                }
            }

            if (k < n - 1) {
                current = multiply(a, current)
                    ?: error("Ошибка вычисления A^k * B")
            }
        }

        return wc
    }

    fun observability(
        a: Array<DoubleArray>,
        c: Array<DoubleArray>
    ): Array<DoubleArray> {
        val n = a.size

        require(n > 0 && a.all { it.size == n } && a.all { row -> row.all { it.isFinite() } }) {
            "Матрица A должна быть конечной квадратной матрицей"
        }

        require(c.isNotEmpty() && c.all { it.size == n } && c.all { row -> row.all { it.isFinite() } }) {
            "Матрица C должна иметь $n столбцов"
        }

        val p = c.size

        val wo = Array(n * p) { DoubleArray(n) }
        var current = Array(p) { c[it].clone() }

        for (k in 0 until n) {
            for (i in 0 until p) {
                for (j in 0 until n) {
                    wo[k * p + i][j] = current[i][j]
                }
            }

            if (k < n - 1) {
                current = multiply(current, a)
                    ?: error("Ошибка вычисления C * A^k")
            }
        }

        return wo
    }

    // --- СИНТЕЗ ТАУ (Фробениус, Аккерман, LQR) ---
    fun frobeniusForm(matA: Array<DoubleArray>, matB: Array<DoubleArray>): Triple<Array<DoubleArray>, Array<DoubleArray>, Array<DoubleArray>>? {
        val n = matA.size
        require(n > 0 && matA.all { it.size == n } && matA.all { row -> row.all { it.isFinite() } }) {
            "Матрица A должна быть конечной квадратной матрицей"
        }
        require(matB.size == n && matB.all { it.size == 1 } && matB.all { row -> row[0].isFinite() }) {
            "Для формы Фробениуса требуется конечная матрица B размера n×1"
        }
        val bVec = Array(n) { DoubleArray(1) }
        for (i in 0 until n) bVec[i][0] = matB[i][0]

        val wc = controllability(matA, bVec)
        val wcSquare = Array(n) { DoubleArray(n) }
        for (i in 0 until n) for (j in 0 until n) wcSquare[i][j] = wc[i][j]

        if (rank(wcSquare) < n) throw Exception("Система неуправляема! Формы Фробениуса не существует.")
        val wcInv = inverse(wcSquare) ?: throw Exception("Ошибка обращения матрицы управляемости.")

        val q = wcInv[n - 1]
        val tInv = Array(n) { DoubleArray(n) }
        var currentQ = q.clone()

        for (i in 0 until n) {
            tInv[i] = currentQ
            if (i < n - 1) {
                val nextQ = DoubleArray(n)
                for (j in 0 until n) {
                    var sum = 0.0
                    for (k in 0 until n) sum += currentQ[k] * matA[k][j]
                    nextQ[j] = sum
                }
                currentQ = nextQ
            }
        }

        val tMat = inverse(tInv) ?: throw Exception("Матрица T вырождена.")
        val aTilde = multiply(multiply(tInv, matA)!!, tMat)!!
        val bTilde = multiply(tInv, bVec)!!

        for (i in 0 until n) for (j in 0 until n) if (abs(aTilde[i][j]) < 1e-10) aTilde[i][j] = 0.0
        for (i in 0 until n) if (abs(bTilde[i][0]) < 1e-10) bTilde[i][0] = 0.0
        val reconstructedA = multiply(multiply(tInv, matA) ?: throw ArithmeticException("Ошибка проверки формы"), tMat)
            ?: throw ArithmeticException("Ошибка проверки формы")
        val reconstructedB = multiply(tInv, bVec) ?: throw ArithmeticException("Ошибка проверки формы")
        val errA = norm(subtract(aTilde, reconstructedA) ?: throw ArithmeticException("Ошибка проверки формы"))
        val errB = norm(subtract(bTilde, reconstructedB) ?: throw ArithmeticException("Ошибка проверки формы"))
        if (max(errA, errB) > 1e-8 * max(1.0, max(norm(aTilde), norm(bTilde))))
            throw ArithmeticException("Преобразование Фробениуса не прошло проверку невязки")
        return Triple(aTilde, bTilde, tMat)
    }

    fun parsePoles(input: String): List<Complex> {
        if (input.isBlank()) {
            return emptyList()
        }

        return input.split(",").map { raw ->
            val s = raw.trim().replace(" ", "")

            require(s.isNotEmpty()) {
                "Обнаружен пустой полюс"
            }

            if (!s.contains("i")) {
                val real = s.toDoubleOrNull()
                    ?: throw IllegalArgumentException(
                        "Некорректный полюс: $s"
                    )

                require(real.isFinite()) {
                    "Полюс должен быть конечным числом: $s"
                }

                Complex(real)
            } else {
                require(s.endsWith("i")) {
                    "Некорректная запись комплексного полюса: $s"
                }

                require(s.count { it == 'i' } == 1) {
                    "Некорректная запись комплексного полюса: $s"
                }

                val body = s.dropLast(1)

                val splitIndex = (1 until body.length)
                    .lastOrNull { index ->
                        (body[index] == '+' || body[index] == '-') &&
                                body[index - 1] != 'e' &&
                                body[index - 1] != 'E'
                    }

                val realPart: Double
                val imaginaryPart: Double

                if (splitIndex == null) {
                    realPart = 0.0

                    imaginaryPart = when (body) {
                        "", "+" -> 1.0
                        "-" -> -1.0
                        else -> body.toDoubleOrNull()
                            ?: throw IllegalArgumentException(
                                "Некорректный полюс: $s"
                            )
                    }
                } else {
                    realPart = body.substring(
                        0,
                        splitIndex
                    ).toDoubleOrNull()
                        ?: throw IllegalArgumentException(
                            "Некорректная действительная часть: $s"
                        )

                    val imText = body.substring(splitIndex)

                    imaginaryPart = when (imText) {
                        "+" -> 1.0
                        "-" -> -1.0
                        else -> imText.toDoubleOrNull()
                            ?: throw IllegalArgumentException(
                                "Некорректная мнимая часть: $s"
                            )
                    }
                }

                require(realPart.isFinite() && imaginaryPart.isFinite()) {
                    "Полюс должен быть конечным числом: $s"
                }

                Complex(realPart, imaginaryPart)
            }
        }
    }

    private fun polyFromRoots(
        roots: List<Complex>
    ): DoubleArray {

        var poly = arrayOf(Complex(1.0))

        for (root in roots) {

            require(root.re.isFinite() && root.im.isFinite()) {
                "Корни многочлена должны быть конечными числами"
            }

            val next = Array(poly.size + 1) {
                Complex(0.0)
            }

            for (i in poly.indices) {
                next[i] = next[i] + poly[i]
                next[i + 1] = next[i + 1] - poly[i] * root
            }

            poly = next
        }

        val coefficientScale = max(
            1.0,
            poly.maxOf { it.abs() }
        )

        val tolerance = 1e-10 * coefficientScale

        require(poly.all { abs(it.im) <= tolerance }) {
            "Коэффициенты многочлена не являются вещественными. " +
                    "Проверь сопряжённость комплексных корней."
        }

        return DoubleArray(poly.size) { i ->
            poly[i].re
        }
    }

    /** Eigenvalues of a real symmetric matrix using cyclic Jacobi rotations. */
    private fun symmetricEigenvalues(input: Array<DoubleArray>): DoubleArray {
        val n = input.size
        val a = Array(n) { input[it].clone() }
        val scale = max(1.0, norm(a))
        require((0 until n).all { i -> (0 until n).all { j -> abs(a[i][j] - a[j][i]) <= 1e-12 * scale } }) { "Матрица должна быть симметричной" }
        repeat(max(20, 12 * n * n)) {
            var p = 0; var q = 0; var largest = 0.0
            for (i in 0 until n) for (j in i + 1 until n) if (abs(a[i][j]) > largest) { largest = abs(a[i][j]); p = i; q = j }
            if (largest <= 16.0 * Math.ulp(1.0) * scale) return DoubleArray(n) { a[it][it] }
            val tau = (a[q][q] - a[p][p]) / (2.0 * a[p][q])
            val t = if (tau == 0.0) 1.0 else kotlin.math.sign(tau) / (abs(tau) + hypot(1.0, tau))
            val c = 1.0 / hypot(1.0, t); val sn = t * c
            val app = a[p][p]; val aqq = a[q][q]; val apq = a[p][q]
            a[p][p] = app - t * apq; a[q][q] = aqq + t * apq; a[p][q] = 0.0; a[q][p] = 0.0
            for (k in 0 until n) if (k != p && k != q) {
                val akp = a[k][p]; val akq = a[k][q]
                a[k][p] = c * akp - sn * akq; a[p][k] = a[k][p]
                a[k][q] = sn * akp + c * akq; a[q][k] = a[k][q]
            }
        }
        throw ArithmeticException("Симметричная диагонализация Якоби не сошлась")
    }

    /** Совместимость со старым интерфейсом: R = rScalar * I. */
    fun solveLQR(matA: Array<DoubleArray>, matB: Array<DoubleArray>, matQ: Array<DoubleArray>, rScalar: Double): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        require(rScalar.isFinite() && rScalar > 0.0) { "Коэффициент R должен быть конечным числом > 0" }
        val m = matB.firstOrNull()?.size ?: 0
        require(m > 0) { "Матрица B должна иметь хотя бы один столбец" }
        val r = Array(m) { i -> DoubleArray(m) { j -> if (i == j) rScalar else 0.0 } }
        return solveLQR(matA, matB, matQ, r)
    }

    /** Непрерывная LQR-задача с полной матрицей стоимости R (m×m). */
    fun solveLQR(matA: Array<DoubleArray>, matB: Array<DoubleArray>, matQ: Array<DoubleArray>, matR: Array<DoubleArray>): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        requireFinite(matA, "Матрица A")
        requireFinite(matB, "Матрица B")
        requireFinite(matQ, "Матрица Q")
        val n = matA.size
        require(matA[0].size == n) { "Матрица A должна быть квадратной" }
        require(matQ.size == n && matQ.all { it.size == n }) { "Матрица Q должна иметь размер n×n" }
        require(matB.size == n) { "Число строк B должно совпадать с размером A" }
        val m = matB[0].size
        require(m > 0) { "Матрица B должна иметь хотя бы один столбец" }
        requireFinite(matR, "Матрица R")
        require(matR.size == m && matR.all { it.size == m }) { "Матрица R должна иметь размер m×m, где m — число столбцов B" }
        val rScale = max(1.0, norm(matR))
        require((0 until m).all { i -> (0 until m).all { j -> abs(matR[i][j] - matR[j][i]) <= 1e-10 * rScale } }) {
            "Матрица R должна быть симметричной"
        }
        // Проверка положительной определённости R методом Холецкого.
        val chol = Array(m) { DoubleArray(m) }
        for (i in 0 until m) {
            for (j in 0..i) {
                var value = matR[i][j]
                for (k in 0 until j) value -= chol[i][k] * chol[j][k]
                if (i == j) {
                    require(value > 1e-12 * rScale) { "Матрица R должна быть положительно определённой" }
                    chol[i][j] = kotlin.math.sqrt(value)
                } else {
                    chol[i][j] = value / chol[j][j]
                }
            }
        }
        val qScale = max(1.0, norm(matQ))
        require((0 until n).all { i -> (0 until n).all { j -> abs(matQ[i][j] - matQ[j][i]) <= 1e-10 * qScale } }) { "Матрица Q должна быть симметричной" }
        val qEigen = symmetricEigenvalues(matQ)
        require(qEigen.minOrNull()!! >= -1e-10 * qScale) { "Матрица Q должна быть положительно полуопределённой" }

        val rInv = inverse(matR) ?: throw ArithmeticException("Матрица R вырождена и не может быть обращена")

        val bRinvBt = multiply(multiply(matB, rInv)!!, transpose(matB))!!
        val aT = transpose(matA)

        var hMat = Array(2 * n) { DoubleArray(2 * n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                hMat[i][j] = matA[i][j]
                hMat[i][j + n] = -bRinvBt[i][j]
                hMat[i + n][j] = -matQ[i][j]
                hMat[i + n][j + n] = -aT[i][j]
            }
        }

        var converged = false
        for (iter in 0 until 100) {
            val hInv = inverse(hMat) ?: throw ArithmeticException("Гамильтонова матрица сингулярна. LQR не сходится.")
            val nextH = multiplyScalar(add(hMat, hInv)!!, 0.5)
            val delta = norm(subtract(hMat, nextH)!!)
            val scale = max(1.0, norm(nextH))
            hMat = nextH
            if (delta <= 1e-10 * scale) { converged = true; break }
        }
        if (!converged) throw ArithmeticException("Итерации LQR не сошлись. Результат не возвращён.")

        val w22PlusI = Array(n) { DoubleArray(n) }
        val w21 = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                w22PlusI[i][j] = hMat[i + n][j + n] + if (i == j) 1.0 else 0.0
                w21[i][j] = hMat[i + n][j]
            }
        }

        val invW22_I = inverse(w22PlusI) ?: throw Exception("Не удается найти решение P.")
        var pMat = multiply(invW22_I, w21)!!
        pMat = multiplyScalar(pMat, -1.0)

        for (i in 0 until n) {
            for (j in 0 until n) {
                val avg = (pMat[i][j] + pMat[j][i]) / 2.0
                pMat[i][j] = avg; pMat[j][i] = avg
            }
        }

        val btP = multiply(transpose(matB), pMat)!!
        val kMat = multiply(rInv, btP)!!

        require(pMat.all { row -> row.all { it.isFinite() } } && kMat.all { row -> row.all { it.isFinite() } }) {
            "Получено некорректное решение LQR"
        }

        // Do not expose a candidate Riccati solution without checking the CARE residual.
        val atp = multiply(aT, pMat) ?: throw ArithmeticException("Ошибка проверки CARE")
        val pa = multiply(pMat, matA) ?: throw ArithmeticException("Ошибка проверки CARE")
        val pbr = multiply(multiply(pMat, bRinvBt) ?: throw ArithmeticException("Ошибка проверки CARE"), pMat)
            ?: throw ArithmeticException("Ошибка проверки CARE")
        val care = add(subtract(add(atp, pa) ?: throw ArithmeticException("Ошибка CARE"), pbr)
            ?: throw ArithmeticException("Ошибка CARE"), matQ) ?: throw ArithmeticException("Ошибка CARE")
        val careScale = max(1.0, norm(atp) + norm(pa) + norm(pbr) + norm(matQ))
        val careRelativeResidual = norm(care) / careScale
        if (!careRelativeResidual.isFinite() || careRelativeResidual > 1e-7)
            throw ArithmeticException("Решение LQR отклонено: относительная невязка CARE = $careRelativeResidual")
        val pEigen = symmetricEigenvalues(pMat)
        if (pEigen.minOrNull()!! < -1e-8 * max(1.0, norm(pMat)))
            throw ArithmeticException("Решение LQR отклонено: P не является положительно полуопределённой")
        val closedLoop = subtract(matA, multiply(matB, kMat) ?: throw ArithmeticException("Ошибка проверки устойчивости"))
            ?: throw ArithmeticException("Ошибка проверки устойчивости")
        val poles = eigenvalues(closedLoop)
        if (poles.any { it.re >= -1e-9 * max(1.0, norm(closedLoop)) })
            throw ArithmeticException("Решение LQR отклонено: замкнутая система не подтверждена как асимптотически устойчивая")
        return Pair(pMat, kMat)
    }

    // --- СОБСТВЕННЫЕ ЧИСЛА И ВЕКТОРЫ ---
    fun eigenvalues(matrix: Array<DoubleArray>): List<Complex> {
        val n = matrix.size
        require(n > 0 && matrix.all { it.size == n }) { "Для собственных значений нужна непустая квадратная матрица" }
        require(matrix.all { row -> row.all { it.isFinite() } }) { "Матрица содержит NaN или бесконечность" }
        if (n == 1) return listOf(Complex(matrix[0][0]))
        val a = Array(n) { i -> matrix[i].clone() }
        var converged = false; var iterB = 0
        while (!converged && iterB < 50) {
            iterB++; converged = true
            for (i in 0 until n) {
                var cSum = 0.0; var rSum = 0.0
                for (j in 0 until n) if (j != i) { cSum += abs(a[j][i]); rSum += abs(a[i][j]) }
                if (cSum > 1e-10 && rSum > 1e-10) {
                    var f = 1.0; val s = cSum + rSum
                    while (cSum < rSum / 2.0) { cSum *= 2.0; f *= 2.0 }
                    while (cSum >= rSum * 2.0) { cSum /= 2.0; f /= 2.0 }
                    if ((cSum + rSum) / f < 0.95 * s) { converged = false; val g = 1.0 / f; for (j in 0 until n) a[i][j] *= g; for (j in 0 until n) a[j][i] *= f }
                }
            }
        }
        for (k in 0 until n - 2) {
            var maxIdx = k + 1; for (i in k + 2 until n) if (abs(a[i][k]) > abs(a[maxIdx][k])) maxIdx = i
            if (abs(a[maxIdx][k]) > 1e-12) {
                if (maxIdx != k + 1) { val rowTmp = a[maxIdx]; a[maxIdx] = a[k + 1]; a[k + 1] = rowTmp; for (c in 0 until n) { val colTmp = a[c][maxIdx]; a[c][maxIdx] = a[c][k + 1]; a[c][k + 1] = colTmp } }
                for (i in k + 2 until n) { val factor = a[i][k] / a[k + 1][k]; for (j in k until n) a[i][j] -= factor * a[k + 1][j]; for (j in 0 until n) a[j][k + 1] += factor * a[j][i] }
            }
        }

        val result = mutableListOf<Complex>(); var currentN = n; var maxIter = 100 * n
        while (currentN > 1 && maxIter > 0) {
            maxIter--; val last = currentN - 1
            if (abs(a[last][last - 1]) <= 1e-10 * (abs(a[last - 1][last - 1]) + abs(a[last][last]) + 1e-12)) { result.add(Complex(a[last][last])); currentN--; continue }
            if (currentN == 2 || abs(a[last - 1][last - 2]) <= 1e-10 * (abs(a[last - 2][last - 2]) + abs(a[last - 1][last - 1]) + 1e-12)) {
                val tr2 = a[last - 1][last - 1] + a[last][last]; val det2 = a[last - 1][last - 1] * a[last][last] - a[last - 1][last] * a[last][last - 1]
                val disc = tr2 * tr2 - 4.0 * det2; val cleanDisc = if (abs(disc) < 1e-9) 0.0 else disc
                if (cleanDisc >= 0) { val sq = sqrt(cleanDisc); result.add(Complex((tr2 + sq) / 2.0)); result.add(Complex((tr2 - sq) / 2.0)) }
                else { val im = sqrt(-cleanDisc) / 2.0; result.add(Complex(tr2 / 2.0, im)); result.add(Complex(tr2 / 2.0, -im)) }
                currentN -= 2; continue
            }
            val tr = a[last - 1][last - 1] + a[last][last]; val det2 = a[last - 1][last - 1] * a[last][last] - a[last - 1][last] * a[last][last - 1]
            val disc = tr * tr - 4.0 * det2; val shift = if (disc >= 0) { val s1 = (tr + sqrt(disc)) / 2.0; val s2 = (tr - sqrt(disc)) / 2.0; if (abs(s1 - a[last][last]) < abs(s2 - a[last][last])) s1 else s2 } else { tr / 2.0 }
            for (i in 0 until currentN) a[i][i] -= shift
            val cosL = DoubleArray(currentN - 1); val sinL = DoubleArray(currentN - 1)
            for (i in 0 until currentN - 1) {
                val r = hypot(a[i][i], a[i + 1][i])
                if (r > 1e-14) {
                    cosL[i] = a[i][i] / r; sinL[i] = -a[i + 1][i] / r
                    for (j in i until currentN) { val t1 = a[i][j]; val t2 = a[i + 1][j]; a[i][j] = cosL[i] * t1 - sinL[i] * t2; a[i + 1][j] = sinL[i] * t1 + cosL[i] * t2 }
                } else { cosL[i] = 1.0; sinL[i] = 0.0 }
            }
            for (i in 0 until currentN - 1) {
                for (j in 0..min(i + 2, currentN - 1)) { val t1 = a[j][i]; val t2 = a[j][i + 1]; a[j][i] = cosL[i] * t1 - sinL[i] * t2; a[j][i + 1] = sinL[i] * t1 + cosL[i] * t2 }
            }
            for (i in 0 until currentN) a[i][i] += shift
        }
        if (currentN > 1) {
            throw ArithmeticException(
                "QR-алгоритм не сошёлся за отведённое число итераций. " +
                        "Собственные значения не определены надёжно."
            )
        }

        if (currentN == 1) {
            result.add(Complex(a[0][0]))
        }

        if (result.size != n) {
            throw ArithmeticException(
                "Ошибка вычисления собственных значений: " +
                        "получено ${result.size} вместо $n"
            )
        }
        while (result.size < n) { val idx = n - 1 - result.size; result.add(Complex(a[idx][idx])) }
        require(result.all { it.re.isFinite() && it.im.isFinite() }) {
            "QR-алгоритм получил некорректные числовые значения"
        }
        // Independent spectral invariants catch many false QR deflations.
        val traceA = trace(matrix) ?: throw ArithmeticException("Не удалось проверить спектр по следу")
        val sumRe = result.sumOf { it.re }; val sumIm = result.sumOf { it.im }
        val spectralScale = max(1.0, max(abs(traceA), result.sumOf { it.abs() }))
        if (hypot(sumRe - traceA, sumIm) > 1e-7 * spectralScale * n)
            throw ArithmeticException("QR-спектр отклонён: сумма собственных значений не совпадает со следом")
        val detA = determinant(matrix)
        if (detA != null && detA.isFinite()) {
            var product = Complex(1.0)
            for (value in result) product = product * value
            val detScale = max(1.0, max(abs(detA), product.abs()))
            if (hypot(product.re - detA, product.im) > 1e-6 * detScale * n)
                throw ArithmeticException("QR-спектр отклонён: произведение собственных значений не совпадает с определителем")
        }
        return result.sortedByDescending { it.abs() }
    }

    fun eigenvectors(
        matrix: Array<DoubleArray>,
        eigenvalues: List<Complex>
    ): List<List<Complex>> {

        val n = matrix.size

        require(n > 0 && matrix.all { it.size == n }) {
            "Матрица должна быть непустой и квадратной"
        }

        require(matrix.all { row -> row.all { it.isFinite() } }) {
            "Матрица содержит NaN или Infinity"
        }

        val scale = max(
            1.0,
            matrix.maxOf { row -> row.maxOf { abs(it) } }
        )

        val zero = Complex(0.0)

        return eigenvalues.map { lambda ->

            require(lambda.re.isFinite() && lambda.im.isFinite()) {
                "Некорректное собственное значение"
            }

            val shifted = Array(n) { i ->
                Array(n) { j ->
                    val value = Complex(matrix[i][j] / scale)

                    if (i == j) {
                        value - Complex(lambda.re / scale, lambda.im / scale)
                    } else {
                        value
                    }
                }
            }

            // Вычисляем ступенчатую форму с частичным выбором
            // ведущего элемента.
            val pivotColumns = mutableListOf<Int>()
            var pivotRow = 0

            val tolerance = max(
                1e-12,
                n * Math.ulp(1.0) * 100.0
            )

            for (col in 0 until n) {

                if (pivotRow >= n) break

                var bestRow = pivotRow

                for (row in pivotRow + 1 until n) {
                    if (
                        shifted[row][col].abs() >
                        shifted[bestRow][col].abs()
                    ) {
                        bestRow = row
                    }
                }

                if (shifted[bestRow][col].abs() <= tolerance) {
                    continue
                }

                val temp = shifted[pivotRow]
                shifted[pivotRow] = shifted[bestRow]
                shifted[bestRow] = temp

                val pivot = shifted[pivotRow][col]

                for (j in col until n) {
                    shifted[pivotRow][j] =
                        shifted[pivotRow][j] / pivot
                }

                for (row in pivotRow + 1 until n) {

                    val factor = shifted[row][col]

                    for (j in col until n) {
                        shifted[row][j] =
                            shifted[row][j] -
                                    factor * shifted[pivotRow][j]
                    }
                }

                pivotColumns.add(col)
                pivotRow++
            }

            val pivotSet = pivotColumns.toSet()

            val freeColumn = (0 until n).firstOrNull {
                it !in pivotSet
            } ?: throw ArithmeticException(
                "Не удалось определить собственный вектор " +
                        "для λ = $lambda"
            )

            val vector = Array(n) { zero }
            vector[freeColumn] = Complex(1.0)

            for (row in pivotColumns.indices.reversed()) {

                val col = pivotColumns[row]
                var sum = zero

                for (j in col + 1 until n) {
                    sum = sum + shifted[row][j] * vector[j]
                }

                vector[col] = -sum
            }

            var vectorNorm = 0.0

            for (value in vector) {
                vectorNorm = hypot(vectorNorm, value.abs())
            }

            if (!vectorNorm.isFinite() || vectorNorm == 0.0) {
                throw ArithmeticException(
                    "Получен некорректный собственный вектор"
                )
            }

            val normalized = vector.map {
                it / vectorNorm
            }

            // Проверка Av = λv.
            var residual = 0.0

            for (i in 0 until n) {

                var av = zero

                for (j in 0 until n) {
                    av = av +
                            Complex(matrix[i][j] / scale) * normalized[j]
                }

                val lv = Complex(
                    lambda.re / scale,
                    lambda.im / scale
                ) * normalized[i]

                residual = hypot(residual, (av - lv).abs())
            }

            val relativeResidual = residual / max(
                1.0,
                lambda.abs() / scale
            )

            if (
                !relativeResidual.isFinite() ||
                relativeResidual > 1e-7
            ) {
                throw ArithmeticException(
                    "Собственный вектор для λ = $lambda " +
                            "не прошёл проверку. " +
                            "Невязка: $relativeResidual"
                )
            }

            normalized
        }
    }
    // --- ЖНФ И РЖНФ ---
    private data class EigenCluster(var re: Double, var im: Double, var count: Int)
    private fun clusterEigenvalues(eigs: List<Complex>): List<EigenCluster> {
        val clusters = mutableListOf<EigenCluster>()
        val spectralScale = max(1.0, eigs.maxOfOrNull { it.abs() } ?: 1.0)
        val tol = 128.0 * Math.ulp(1.0) * spectralScale
        for (e in eigs) {
            var found = false
            for (c in clusters) {
                if (abs(e.re - c.re) < tol && abs(e.im - c.im) < tol) { c.re = (c.re * c.count + e.re) / (c.count + 1); c.im = (c.im * c.count + e.im) / (c.count + 1); c.count++; found = true; break }
            }
            if (!found) clusters.add(EigenCluster(e.re, e.im, 1))
        }
        return clusters
    }
    private fun multiplyComplexMat(aRe: Array<DoubleArray>, aIm: Array<DoubleArray>, bRe: Array<DoubleArray>, bIm: Array<DoubleArray>): Pair<Array<DoubleArray>, Array<DoubleArray>> {
        val n = aRe.size; val resRe = Array(n) { DoubleArray(n) }; val resIm = Array(n) { DoubleArray(n) }
        for (i in 0 until n) for (j in 0 until n) { var sr = 0.0; var si = 0.0; for (k in 0 until n) { sr += aRe[i][k] * bRe[k][j] - aIm[i][k] * bIm[k][j]; si += aRe[i][k] * bIm[k][j] + aIm[i][k] * bRe[k][j] }; resRe[i][j] = sr; resIm[i][j] = si }
        return Pair(resRe, resIm)
    }
    private fun rankWithTolerance(matrix: Array<DoubleArray>, tol: Double = 1e-9): Int {
        val r = matrix.size; if (r == 0) return 0; val c = matrix[0].size; val a = Array(r) { i -> matrix[i].clone() }; var row = 0
        for (col in 0 until c) {
            if (row >= r) break; var maxRow = row; var maxVal = abs(a[row][col])
            for (i in row + 1 until r) { val v = abs(a[i][col]); if (v > maxVal) { maxVal = v; maxRow = i } }
            if (maxVal < tol) continue
            if (maxRow != row) { val tmp = a[row]; a[row] = a[maxRow]; a[maxRow] = tmp }
            for (i in row + 1 until r) { val factor = a[i][col] / a[row][col]; a[i][col] = 0.0; for (j in col + 1 until c) a[i][j] -= factor * a[row][j] }
            row++
        }
        return row
    }
    private fun rankComplexMat(mRe: Array<DoubleArray>, mIm: Array<DoubleArray>): Int {
        val n = mRe.size
        val realMat = Array(2 * n) { DoubleArray(2 * n) }
        for (i in 0 until n) for (j in 0 until n) {
            realMat[i][j] = mRe[i][j]; realMat[i][j + n] = -mIm[i][j]
            realMat[i + n][j] = mIm[i][j]; realMat[i + n][j + n] = mRe[i][j]
        }
        return rank(realMat) / 2
    }
    private fun getJordanBlocks(matrix: Array<DoubleArray>, lambdaRe: Double, lambdaIm: Double, maxAlgMultiplicity: Int): List<Int> {
        val n = matrix.size
        val nRe = Array(n) { i -> DoubleArray(n) { j -> matrix[i][j] - if (i == j) lambdaRe else 0.0 } }
        val nIm = Array(n) { i -> DoubleArray(n) { j -> if (i == j) -lambdaIm else 0.0 } }
        val ranks = mutableListOf(n, rankComplexMat(nRe, nIm))
        var currentRe = nRe; var currentIm = nIm
        while (ranks.size <= n && ranks.last() < ranks[ranks.lastIndex - 1]) {
            val next = multiplyComplexMat(currentRe, currentIm, nRe, nIm)
            currentRe = next.first; currentIm = next.second
            if (currentRe.any { row -> row.any { !it.isFinite() } } || currentIm.any { row -> row.any { !it.isFinite() } })
                throw ArithmeticException("Переполнение при анализе жордановой структуры")
            ranks.add(rankComplexMat(currentRe, currentIm))
        }
        while (ranks.size <= n + 1) ranks.add(ranks.last())
        val blocks = mutableListOf<Int>()
        for (size in 1..n) {
            val count = ranks[size - 1] - 2 * ranks[size] + ranks[size + 1]
            if (count < 0) throw ArithmeticException("Неустойчивая последовательность рангов: ЖНФ не определена надёжно")
            repeat(count) { blocks.add(size) }
        }
        if (blocks.sum() != maxAlgMultiplicity)
            throw ArithmeticException("Алгебраическая и геометрическая кратности не согласуются; ЖНФ не возвращена")
        return blocks.sortedDescending()
    }
    private fun formatJnfNum(v: Double): String { val clean = if (abs(v) < 1e-9) 0.0 else v; val rounded = Math.round(clean * 1000.0) / 1000.0; return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else String.format(Locale.US, "%.3f", rounded).trimEnd('0').trimEnd('.') }
    private fun formatJnfNumC(re: Double, im: Double): String { val reClean = if (abs(re) < 1e-4) 0.0 else re; val imClean = if (abs(im) < 1e-4) 0.0 else im; val reStr = formatJnfNum(reClean); val imStr = formatJnfNum(abs(imClean)); if (imClean == 0.0) return reStr; if (reClean == 0.0) return if (imClean > 0) "${imStr}i" else "-${imStr}i"; return if (imClean > 0) "$reStr + ${imStr}i" else "$reStr - ${imStr}i" }

    fun jordanForm(matrix: Array<DoubleArray>): Pair<String, List<List<String>>> {
        val n = matrix.size; if (n == 0) return Pair("Матрица пуста", emptyList())
        require(matrix.all { it.size == n } && matrix.all { row -> row.all { it.isFinite() } }) { "Для ЖНФ нужна конечная квадратная матрица" }
        return try {
            val rawEig = eigenvalues(matrix); val clusters = clusterEigenvalues(rawEig); val jnf = Array(n) { Array(n) { "0" } }; var pos = 0; var hasMultipleRoots = false
            for (cluster in clusters) {
                if (cluster.count > 1) hasMultipleRoots = true; val blocks = getJordanBlocks(matrix, cluster.re, cluster.im, cluster.count)
                for (blockSize in blocks) {
                    for (i in 0 until blockSize) { val current = pos + i; if (current < n) { jnf[current][current] = formatJnfNumC(cluster.re, cluster.im); if (i < blockSize - 1 && current + 1 < n) jnf[current][current + 1] = "1" } }
                    pos += blockSize
                }
            }
            while (pos < n) { jnf[pos][pos] = formatJnfNumC(rawEig[pos].re, rawEig[pos].im); pos++ }
            Pair(if (!hasMultipleRoots) "Диагональный вид (Простой спектр):" else "Комплексная ЖНФ:", jnf.map { it.toList() })
        } catch (e: Exception) { Pair("Ошибка вычисления ЖНФ", emptyList()) }
    }

    fun realJordanForm(matrix: Array<DoubleArray>): Pair<String, List<List<String>>> {
        val n = matrix.size; if (n == 0) return Pair("Матрица пуста", emptyList())
        require(matrix.all { it.size == n } && matrix.all { row -> row.all { it.isFinite() } }) { "Для РЖНФ нужна конечная квадратная матрица" }
        return try {
            val rawEig = eigenvalues(matrix); val clusters = clusterEigenvalues(rawEig); val jnf = Array(n) { Array(n) { "0" } }; var pos = 0; val processed = BooleanArray(clusters.size)
            for (i in clusters.indices) {
                if (processed[i]) continue; val c = clusters[i]
                val imagTol = 128.0 * Math.ulp(1.0) * max(1.0, abs(c.re))
                if (abs(c.im) <= imagTol) {
                    val blocks = getJordanBlocks(matrix, c.re, 0.0, c.count)
                    for (blockSize in blocks) { for (k in 0 until blockSize) { if (pos + k < n) { jnf[pos + k][pos + k] = formatJnfNum(c.re); if (k < blockSize - 1 && pos + k + 1 < n) jnf[pos + k][pos + k + 1] = "1" } }; pos += blockSize }
                    processed[i] = true
                } else {
                    var conjIdx = -1; for (j in i + 1 until clusters.size) { if (!processed[j] && abs(clusters[j].re - c.re) <= 128.0 * Math.ulp(1.0) * max(1.0, abs(c.re)) && abs(clusters[j].im + c.im) <= 128.0 * Math.ulp(1.0) * max(1.0, abs(c.im))) { conjIdx = j; break } }
                    val blocks = getJordanBlocks(matrix, c.re, c.im, c.count); val a = c.re; val b = abs(c.im)
                    for (blockSize in blocks) {
                        for (k in 0 until blockSize) {
                            val cur = pos + 2 * k
                            if (cur + 1 < n) {
                                jnf[cur][cur] = formatJnfNum(a); jnf[cur][cur + 1] = formatJnfNum(b); jnf[cur + 1][cur] = formatJnfNum(-b); jnf[cur + 1][cur + 1] = formatJnfNum(a)
                                if (k < blockSize - 1 && cur + 3 < n) { jnf[cur][cur + 2] = "1"; jnf[cur + 1][cur + 3] = "1" }
                            } else if (cur < n) jnf[cur][cur] = formatJnfNum(a)
                        }
                        pos += 2 * blockSize
                    }
                    processed[i] = true; if (conjIdx != -1) processed[conjIdx] = true
                }
            }
            Pair("Вещественная Жорданова форма (РЖНФ):", jnf.map { it.toList() })
        } catch (e: Exception) { Pair("Ошибка вычисления РЖНФ", emptyList()) }
    }
    fun ackermann(
        matA: Array<DoubleArray>,
        matB: Array<DoubleArray>,
        desiredPoles: List<Complex>
    ): Array<DoubleArray> {

        val n = matA.size

        require(n > 0 && matA.all { it.size == n }) {
            "Матрица A должна быть квадратной"
        }

        require(matB.size == n && matB.all { it.size == 1 }) {
            "Метод Аккермана требует матрицу B размерности n×1"
        }

        require(desiredPoles.size == n) {
            "Количество полюсов должно равняться $n"
        }

        require(
            matA.all { row -> row.all { it.isFinite() } } &&
                    matB.all { row -> row.all { it.isFinite() } } &&
                    desiredPoles.all { it.re.isFinite() && it.im.isFinite() }
        ) {
            "Матрицы и полюса должны содержать конечные числа"
        }

        // Строим характеристический многочлен:
        // (s - λ1)(s - λ2)...(s - λn)
        //
        // Коэффициенты хранятся от старшей степени
        // к младшей.
        var coefficients = arrayOf(Complex(1.0))

        for (pole in desiredPoles) {

            val next = Array(coefficients.size + 1) {
                Complex(0.0)
            }

            for (i in coefficients.indices) {
                next[i] = next[i] + coefficients[i]
                next[i + 1] =
                    next[i + 1] - coefficients[i] * pole
            }

            coefficients = next
        }

        val coefficientScale = max(
            1.0,
            coefficients.maxOf { it.abs() }
        )

        require(
            coefficients.all {
                abs(it.im) <= 1e-10 * coefficientScale
            }
        ) {
            "Для вещественного управления комплексные полюса " +
                    "должны образовывать сопряжённые пары"
        }

        val realCoefficients = DoubleArray(n + 1) {
            coefficients[it].re
        }

        // Матрица управляемости:
        // W = [B, AB, A²B, ..., A^(n-1)B]
        val w = controllability(matA, matB)

        require(rank(w) == n) {
            "Система не является полностью управляемой"
        }

        val wInverse = inverse(w)
            ?: throw ArithmeticException(
                "Не удалось обратить матрицу управляемости"
            )

        // φ(A) = A^n + a1*A^(n-1) + ... + an*I
        var polynomialMatrix = Array(n) {
            DoubleArray(n)
        }

        for (i in 0..n) {

            val power = matrixPower(matA, n - i)
                ?: throw ArithmeticException(
                    "Ошибка вычисления степени матрицы"
                )

            val term = multiplyScalar(
                power,
                realCoefficients[i]
            )

            polynomialMatrix = add(polynomialMatrix, term)
                ?: throw ArithmeticException(
                    "Ошибка вычисления матричного многочлена"
                )
        }

        // K = [0 ... 0 1] * W^(-1) * φ(A)
        val lastRow = wInverse[n - 1]

        val k = Array(1) {
            DoubleArray(n)
        }

        for (j in 0 until n) {

            var sum = 0.0

            for (i in 0 until n) {
                sum += lastRow[i] * polynomialMatrix[i][j]
            }

            k[0][j] = sum
        }

        require(k[0].all { it.isFinite() }) {
            "Получен некорректный коэффициент обратной связи"
        }
        val closedLoop = subtract(matA, multiply(matB, k) ?: throw ArithmeticException("Ошибка проверки замкнутой системы"))
            ?: throw ArithmeticException("Ошибка проверки замкнутой системы")
        val actualPoles = eigenvalues(closedLoop).toMutableList()
        val scale = max(1.0, max(desiredPoles.maxOf { it.abs() }, actualPoles.maxOf { it.abs() }))
        val tolerance = 1e-6 * scale
        for (target in desiredPoles) {
            val index = actualPoles.indices.minByOrNull { i ->
                hypot(actualPoles[i].re - target.re, actualPoles[i].im - target.im)
            } ?: throw ArithmeticException("Не удалось проверить назначенные полюса")
            val error = hypot(actualPoles[index].re - target.re, actualPoles[index].im - target.im)
            if (error > tolerance) throw ArithmeticException("Метод Аккермана отклонён: полюс $target не подтверждён (ошибка $error)")
            actualPoles.removeAt(index)
        }
        return k
    }
}
