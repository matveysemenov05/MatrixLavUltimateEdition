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

        for (col in 0 until n) {
            var pivotRow = col

            for (row in col + 1 until n) {
                if (a[row][col].abs() > a[pivotRow][col].abs()) {
                    pivotRow = row
                }
            }

            val pivot = a[pivotRow][col]

            if (pivot.abs() == 0.0 || !pivot.abs().isFinite()) {
                return null
            }

            if (pivotRow != col) {
                val tempA = a[col]
                a[col] = a[pivotRow]
                a[pivotRow] = tempA

                val tempInv = inv[col]
                inv[col] = inv[pivotRow]
                inv[pivotRow] = tempInv
            }

            for (j in 0 until n) {
                a[col][j] = a[col][j] / pivot
                inv[col][j] = inv[col][j] / pivot
            }

            for (row in 0 until n) {
                if (row == col) continue

                val factor = a[row][col]

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

        return norm(subtract(squared, original)) /
                max(1.0, norm(original))
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
        val diagonal = (0 until n).all { i ->
            (0 until n).all { j ->
                i == j || input[i][j] == 0.0
            }
        }

        if (diagonal) {
            val root = zero(n)

            for (i in 0 until n) {
                root[i][i] = sqrtComplex(a[i][i])
            }

            return accepted(root, a, tolerance)
        }

        // 2. Специальная формула для матриц 2x2.
        //
        // Если X = (A + sI) / t,
        // где s^2 = det(A), t^2 = tr(A) + 2s,
        // то X^2 = A.
        if (n == 2) {
            val determinant =
                a[0][0] * a[1][1] - a[0][1] * a[1][0]

            val trace = a[0][0] + a[1][1]

            val s0 = sqrtComplex(determinant)

            for (s in listOf(s0, -s0)) {
                val t = sqrtComplex(trace + s * 2.0)

                if (t.abs() == 0.0) continue

                val root = zero(2)

                for (i in 0 until 2) {
                    for (j in 0 until 2) {
                        val numerator =
                            a[i][j] +
                                    if (i == j) s else Complex(0.0)

                        root[i][j] = numerator / t
                    }
                }

                val checked = accepted(root, a, tolerance)

                if (checked != null) return checked
            }
        }

        // 3. Итерационный метод Денмана — Биверса.
        //
        // Это численный метод, не гарантирующий
        // сходимость для произвольной матрицы.
        var y = copy(a)
        var z = identity(n)

        for (iteration in 0 until 100) {
            val invY = inverse(y) ?: break
            val invZ = inverse(z) ?: break

            val nextY = scale(add(y, invZ), 0.5)
            val nextZ = scale(add(z, invY), 0.5)

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

        // Проверяем, что мнимая часть пренебрежимо мала.
        val maxMagnitude = root.maxOf { row ->
            row.maxOf { it.abs() }
        }

        val imaginaryTolerance =
            tolerance * max(1.0, maxMagnitude)

        if (root.any { row ->
                row.any { abs(it.im) > imaginaryTolerance }
            }) {
            return null
        }

        return Array(n) { i ->
            DoubleArray(n) { j -> root[i][j].re }
        }
    }
}