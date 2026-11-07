package com.example.matrixcalculator

import kotlin.math.*

object MatrixSquareRoot {

    data class Result(
        val root: Array<Array<Complex>>,
        val relativeResidual: Double
    )

    private fun zero(n: Int): Array<Array<Complex>> =
        Array(n) { Array(n) { Complex(0.0) } }

    private fun identity(n: Int): Array<Array<Complex>> =
        Array(n) { i ->
            Array(n) { j ->
                Complex(if (i == j) 1.0 else 0.0)
            }
        }

    private fun copy(a: Array<Array<Complex>>) =
        Array(a.size) { i -> a[i].clone() }

    private fun add(
        a: Array<Array<Complex>>,
        b: Array<Array<Complex>>
    ): Array<Array<Complex>> =
        Array(a.size) { i ->
            Array(a.size) { j -> a[i][j] + b[i][j] }
        }

    private fun subtract(
        a: Array<Array<Complex>>,
        b: Array<Array<Complex>>
    ): Array<Array<Complex>> =
        Array(a.size) { i ->
            Array(a.size) { j -> a[i][j] - b[i][j] }
        }

    private fun scale(
        a: Array<Array<Complex>>,
        k: Double
    ): Array<Array<Complex>> =
        Array(a.size) { i ->
            Array(a.size) { j -> a[i][j] * k }
        }

    private fun transpose(
        a: Array<Array<Complex>>
    ): Array<Array<Complex>> =
        Array(a.size) { i ->
            Array(a.size) { j -> a[j][i] }
        }

    private fun multiply(
        a: Array<Array<Complex>>,
        b: Array<Array<Complex>>
    ): Array<Array<Complex>> {
        val n = a.size
        val result = zero(n)

        for (i in 0 until n) {
            for (j in 0 until n) {
                var sum = Complex(0.0)
                for (k in 0 until n) {
                    sum = sum + a[i][k] * b[k][j]
                }
                result[i][j] = sum
            }
        }
        return result
    }

    private fun norm(a: Array<Array<Complex>>): Double {
        var result = 0.0
        for (row in a) {
            for (v in row) {
                result = hypot(result, v.abs())
            }
        }
        return result
    }

    private fun finite(a: Array<Array<Complex>>): Boolean =
        a.all { row ->
            row.all { it.re.isFinite() && it.im.isFinite() }
        }

    private fun inverse(
        input: Array<Array<Complex>>
    ): Array<Array<Complex>>? {
        val n = input.size
        val a = copy(input)
        val inv = identity(n)
        val scales = DoubleArray(n) { r ->
            max(1e-15, a[r].maxOf { it.abs() })
        }

        for (col in 0 until n) {
            var pivotRow = col
            var bestRatio = -1.0

            for (row in col until n) {
                val ratio = a[row][col].abs() / scales[row]
                if (ratio > bestRatio) {
                    bestRatio = ratio
                    pivotRow = row
                }
            }

            val pivot = a[pivotRow][col]
            if (pivot.abs() <= n * Math.ulp(1.0) * scales[pivotRow] * 16.0 || !pivot.abs().isFinite()) {
                return null
            }

            if (pivotRow != col) {
                val tempA = a[col]; a[col] = a[pivotRow]; a[pivotRow] = tempA
                val tempInv = inv[col]; inv[col] = inv[pivotRow]; inv[pivotRow] = tempInv
                val tempScale = scales[col]; scales[col] = scales[pivotRow]; scales[pivotRow] = tempScale
            }

            for (j in 0 until n) {
                a[col][j] = a[col][j] / pivot
                inv[col][j] = inv[col][j] / pivot
            }

            for (row in 0 until n) {
                if (row == col) continue
                val factor = a[row][col]
                if (factor.abs() == 0.0) continue
                for (j in 0 until n) {
                    a[row][j] = a[row][j] - factor * a[col][j]
                    inv[row][j] = inv[row][j] - factor * inv[col][j]
                }
            }
        }

        return if (finite(inv)) inv else null
    }

    private fun sqrtComplex(z: Complex): Complex {
        val magnitude = z.abs()
        if (magnitude == 0.0) return Complex(0.0)

        val real = sqrt(max(0.0, (magnitude + z.re) / 2.0))
        val imaginary = sqrt(max(0.0, (magnitude - z.re) / 2.0))

        return Complex(
            real,
            if (z.im < 0.0) -imaginary else imaginary
        )
    }

    private fun residual(
        root: Array<Array<Complex>>,
        original: Array<Array<Complex>>
    ): Double {
        val squared = multiply(root, root)
        return norm(subtract(squared, original)) / max(1.0, norm(original))
    }

    private fun accepted(
        root: Array<Array<Complex>>,
        original: Array<Array<Complex>>,
        tolerance: Double
    ): Result? {
        if (!finite(root)) return null
        val error = residual(root, original)
        return if (error.isFinite() && error <= tolerance) {
            Result(root, error)
        } else {
            null
        }
    }

    /**
     * Алгоритм Бьёрка — Хаммарлинга (Björck–Hammarling) для верхнетреугольных матриц.
     * Находит главный корень за O(n^3) без обращения матрицы (работает также для жордановых клеток).
     */
    private fun solveUpperTriangular(
        t: Array<Array<Complex>>
    ): Array<Array<Complex>>? {
        val n = t.size
        val u = zero(n)
        for (i in 0 until n) {
            u[i][i] = sqrtComplex(t[i][i])
        }
        for (gap in 1 until n) {
            for (i in 0 until n - gap) {
                val j = i + gap
                var s = t[i][j]
                for (k in i + 1 until j) {
                    s = s - u[i][k] * u[k][j]
                }
                val denom = u[i][i] + u[j][j]
                if (denom.abs() <= 1e-14) {
                    if (s.abs() <= 1e-14) {
                        u[i][j] = Complex(0.0)
                    } else {
                        // Нильпотентный жорданов блок с нулевой диагональю не имеет корня
                        return null
                    }
                } else {
                    u[i][j] = s / denom
                }
            }
        }
        return u
    }

    fun compute(
        input: Array<DoubleArray>,
        tolerance: Double = 1e-8
    ): Result? {
        val n = input.size

        require(n > 0 && input.all { it.size == n }) {
            "Матрица должна быть непустой и квадратной"
        }

        require(input.all { row -> row.all { it.isFinite() } }) {
            "Матрица содержит NaN или Infinity"
        }

        require(tolerance > 0.0 && tolerance.isFinite()) {
            "Некорректная точность"
        }

        val a = Array(n) { i ->
            Array(n) { j -> Complex(input[i][j]) }
        }

        // 1. Диагональные матрицы, включая вырожденные.
        val isDiagonal = (0 until n).all { i ->
            (0 until n).all { j -> i == j || input[i][j] == 0.0 }
        }

        if (isDiagonal) {
            val root = zero(n)
            for (i in 0 until n) {
                root[i][i] = sqrtComplex(a[i][i])
            }
            return accepted(root, a, tolerance)
        }

        // 2. Верхнетреугольные и нижнетреугольные матрицы (метод Бьёрка — Хаммарлинга).
        val isUpperTriangular = (0 until n).all { i ->
            (0 until i).all { j -> input[i][j] == 0.0 }
        }
        if (isUpperTriangular) {
            val u = solveUpperTriangular(a) ?: return null
            return accepted(u, a, tolerance)
        }

        val isLowerTriangular = (0 until n).all { i ->
            (i + 1 until n).all { j -> input[i][j] == 0.0 }
        }
        if (isLowerTriangular) {
            val uT = solveUpperTriangular(transpose(a)) ?: return null
            return accepted(transpose(uT), a, tolerance)
        }

        // 3. Специальная аналитическая формула Кэли — Гамильтона для матриц 2x2:
        // X = (A + sI) / t, где s^2 = det(A), t^2 = tr(A) + 2s.
        if (n == 2) {
            val determinant = a[0][0] * a[1][1] - a[0][1] * a[1][0]
            val trace = a[0][0] + a[1][1]
            val s0 = sqrtComplex(determinant)

            val candidates = mutableListOf<Result>()
            for (s in listOf(s0, -s0)) {
                val t = sqrtComplex(trace + s * 2.0)
                if (t.abs() <= 1e-14) continue

                val root = zero(2)
                for (i in 0 until 2) {
                    for (j in 0 until 2) {
                        val numerator = a[i][j] + if (i == j) s else Complex(0.0)
                        root[i][j] = numerator / t
                    }
                }
                accepted(root, a, tolerance)?.let { candidates.add(it) }
            }

            if (candidates.isNotEmpty()) {
                // Предпочитаем вещественный главный корень (с минимальной мнимой частью и положительным следом)
                return candidates.minByOrNull { cand ->
                    val imagNorm = cand.root.sumOf { row -> row.sumOf { abs(it.im) } }
                    val traceRe = cand.root[0][0].re + cand.root[1][1].re
                    imagNorm * 1000.0 + (if (traceRe >= 0.0) 0.0 else 10.0) + cand.relativeResidual
                }
            }
        }

        // 4. Итерационный метод Денмана — Биверса с масштабированием Хайэма (Higham scaling).
        var y = copy(a)
        var z = identity(n)

        for (iteration in 0 until 100) {
            val invY = inverse(y) ?: break
            val invZ = inverse(z) ?: break

            val normY = norm(y)
            val normZ = norm(z)
            val normInvY = norm(invY)
            val normInvZ = norm(invZ)

            val gamma = if (normY > 0.0 && normZ > 0.0 && normInvY > 0.0 && normInvZ > 0.0) {
                ((normInvY * normInvZ) / (normY * normZ)).pow(0.25).coerceIn(0.1, 10.0)
            } else {
                1.0
            }

            val nextY = scale(add(scale(y, gamma), scale(invZ, 1.0 / gamma)), 0.5)
            val nextZ = scale(add(scale(z, gamma), scale(invY, 1.0 / gamma)), 0.5)

            if (!finite(nextY) || !finite(nextZ)) break

            val checked = accepted(nextY, a, tolerance)
            if (checked != null) return checked

            y = nextY
            z = nextZ
        }

        return null
    }

    fun computeReal(
        input: Array<DoubleArray>,
        tolerance: Double = 1e-8
    ): Array<DoubleArray>? {
        val result = compute(input, tolerance) ?: return null
        val root = result.root
        val n = root.size

        val maxMagnitude = root.maxOf { row ->
            row.maxOf { it.abs() }
        }
        val imaginaryTolerance = max(1e-7, tolerance * 10.0) * max(1.0, maxMagnitude)

        if (root.any { row -> row.any { abs(it.im) > imaginaryTolerance } }) {
            return null
        }

        return Array(n) { i ->
            DoubleArray(n) { j ->
                val v = root[i][j].re
                if (abs(v) < 1e-12) 0.0 else v
            }
        }
    }
}