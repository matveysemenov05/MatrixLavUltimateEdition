package com.example.matrixcalculator

import com.example.matrixlavultimateedition.MatrixMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MatrixEigenvectorsTest {

    private val math = MatrixMath()

    private fun checkEigenvector(
        a: Array<DoubleArray>,
        lambda: Complex,
        v: List<Complex>
    ) {
        val n = a.size

        assertEquals(n, v.size)

        val vectorNorm = sqrt(
            v.sumOf { it.abs() * it.abs() }
        )

        assertTrue(
            "Собственный вектор нулевой",
            vectorNorm > 0.0
        )

        var error = 0.0

        for (i in 0 until n) {

            var av = Complex(0.0)

            for (j in 0 until n) {
                av = av + Complex(a[i][j]) * v[j]
            }

            val lv = lambda * v[i]
            val difference = av - lv

            error += difference.abs().pow(2.0)
        }

        val residual = sqrt(error)

        assertTrue(
            "Неверный собственный вектор: $residual",
            residual < 1e-7
        )
    }

    @Test
    fun diagonalMatrix() {

        val a = arrayOf(
            doubleArrayOf(2.0, 0.0),
            doubleArrayOf(0.0, 3.0)
        )

        val values = listOf(
            Complex(2.0),
            Complex(3.0)
        )

        val vectors = math.eigenvectors(a, values)

        assertEquals(2, vectors.size)

        for (i in values.indices) {
            checkEigenvector(a, values[i], vectors[i])
        }
    }

    @Test
    fun rotationMatrix() {

        val a = arrayOf(
            doubleArrayOf(0.0, -1.0),
            doubleArrayOf(1.0, 0.0)
        )

        val values = listOf(
            Complex(0.0, 1.0),
            Complex(0.0, -1.0)
        )

        val vectors = math.eigenvectors(a, values)

        for (i in values.indices) {
            checkEigenvector(a, values[i], vectors[i])
        }
    }

    @Test
    fun jordanMatrix() {

        val a = arrayOf(
            doubleArrayOf(2.0, 1.0),
            doubleArrayOf(0.0, 2.0)
        )

        val values = listOf(Complex(2.0))

        val vectors = math.eigenvectors(a, values)

        checkEigenvector(a, values[0], vectors[0])
    }

    @Test
    fun identityMatrix() {

        val a = arrayOf(
            doubleArrayOf(1.0, 0.0, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 1.0)
        )

        val values = List(3) { Complex(1.0) }

        val vectors = math.eigenvectors(a, values)

        assertEquals(3, vectors.size)

        for (i in values.indices) {
            checkEigenvector(a, values[i], vectors[i])
        }
    }

    @Test
    fun invalidEigenvalueRejected() {

        val a = arrayOf(
            doubleArrayOf(2.0, 0.0),
            doubleArrayOf(0.0, 3.0)
        )

        try {
            math.eigenvectors(a, listOf(Complex(100.0)))

            fail("Ожидалась ошибка для неверного собственного значения")

        } catch (e: ArithmeticException) {
            // Ожидаемое поведение.
        }
    }
}