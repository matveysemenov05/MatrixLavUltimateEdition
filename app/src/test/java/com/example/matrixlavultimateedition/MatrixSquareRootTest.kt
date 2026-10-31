package com.example.matrixcalculator

import com.example.matrixlavultimateedition.MatrixMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class MatrixSquareRootTest {

    private val math = MatrixMath()

    private fun matrix(
        vararg rows: DoubleArray
    ): Array<DoubleArray> = arrayOf(*rows)

    private fun assertValidRoot(
        a: Array<DoubleArray>,
        root: Array<Array<Complex>>,
        tolerance: Double = 1e-7
    ) {
        val n = a.size

        assertEquals(n, root.size)

        for (row in root) {
            assertEquals(n, row.size)
        }

        var errorSquared = 0.0
        var inputNorm = 0.0

        for (i in 0 until n) {
            for (j in 0 until n) {

                var value = Complex(0.0)

                for (k in 0 until n) {
                    value = value + root[i][k] * root[k][j]
                }

                val difference = value - Complex(a[i][j])

                errorSquared += difference.abs().pow(2.0)
                inputNorm += a[i][j] * a[i][j]
            }
        }

        val relativeError =
            sqrt(errorSquared) / max(1.0, sqrt(inputNorm))

        assertTrue(
            "Неверный корень. Невязка = $relativeError",
            relativeError.isFinite() &&
                    relativeError < tolerance
        )
    }

    @Test
    fun diagonalMatrix() {
        val a = matrix(
            doubleArrayOf(4.0, 0.0),
            doubleArrayOf(0.0, 9.0)
        )

        val result = MatrixSquareRoot.compute(a)

        assertNotNull(result)
        assertValidRoot(a, result!!.root)

        val real = math.matrixSqrt(a)

        assertNotNull(real)
        assertEquals(2.0, real!![0][0], 1e-8)
        assertEquals(3.0, real[1][1], 1e-8)
    }

    @Test
    fun singularDiagonalMatrix() {
        val a = matrix(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 4.0)
        )

        val result = MatrixSquareRoot.compute(a)

        assertNotNull(result)
        assertValidRoot(a, result!!.root)
    }

    @Test
    fun negativeScalarMatrix() {
        val a = matrix(
            doubleArrayOf(-1.0)
        )

        val result = MatrixSquareRoot.compute(a)

        assertNotNull(result)
        assertValidRoot(a, result!!.root)

        assertNull(math.matrixSqrt(a))
    }

    @Test
    fun jordanBlock() {
        val a = matrix(
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(0.0, 1.0)
        )

        val result = MatrixSquareRoot.compute(a)

        assertNotNull(result)
        assertValidRoot(a, result!!.root)
    }

    @Test
    fun rotationMatrix() {
        val a = matrix(
            doubleArrayOf(0.0, -1.0),
            doubleArrayOf(1.0, 0.0)
        )

        val result = MatrixSquareRoot.compute(a)

        assertNotNull(result)
        assertValidRoot(a, result!!.root)
    }

    @Test
    fun zeroMatrix() {
        val a = matrix(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 0.0)
        )

        val result = MatrixSquareRoot.compute(a)

        assertNotNull(result)
        assertValidRoot(a, result!!.root)
    }

    @Test
    fun nilpotentMatrixWithoutSquareRoot() {
        val a = matrix(
            doubleArrayOf(0.0, 1.0),
            doubleArrayOf(0.0, 0.0)
        )

        val result = MatrixSquareRoot.compute(a)

        // У этой матрицы нет квадратного корня
        // размера 2x2 даже над комплексными числами.
        assertNull(result)
    }

    @Test
    fun nonSquareMatrixRejected() {
        val a = matrix(
            doubleArrayOf(1.0, 2.0, 3.0),
            doubleArrayOf(4.0, 5.0, 6.0)
        )

        try {
            MatrixSquareRoot.compute(a)
            fail("Ожидалась ошибка размерности")
        } catch (e: IllegalArgumentException) {
            // Ожидаемое поведение.
        }
    }
}