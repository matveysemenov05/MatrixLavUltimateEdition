package com.example.matrixcalculator

import com.example.matrixlavultimateedition.MatrixMath
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/**
 * Расширенная проверка MatrixMath: более 100 отдельных математических сценариев.
 * Не меняет реализацию MatrixMath. Падение теста означает необходимость анализа
 * реализации ИЛИ численной обусловленности конкретного сценария.
 * Запуск: app/src/test/java/com/example/matrixcalculator/MatrixMathTest.kt
 */
class MatrixMathTest {
    private val math = MatrixMath()
    private fun m(vararg rows: DoubleArray): Array<DoubleArray> = arrayOf(*rows)
    private fun diag(vararg x: Double): Array<DoubleArray> = Array(x.size) { i -> DoubleArray(x.size) { j -> if (i == j) x[i] else 0.0 } }
    private fun matrix(expected: Array<DoubleArray>, actual: Array<DoubleArray>?, eps: Double = 1e-6) {
        assertNotNull("Матрица оказалась null", actual)
        val a = actual!!
        assertEquals("Число строк", expected.size, a.size)
        for (i in expected.indices) {
            assertEquals("Число столбцов в строке $i", expected[i].size, a[i].size)
            for (j in expected[i].indices) assertEquals("Элемент [$i,$j]", expected[i][j], a[i][j], eps)
        }
    }
    private fun near(expected: Double, actual: Double?, eps: Double = 1e-6) {
        assertNotNull(actual)
        assertEquals(expected, actual!!, eps)
    }
    private fun expectInvalid(block: () -> Unit) {
        try { block(); fail("Ожидалось исключение для некорректного ввода") }
        catch (e: IllegalArgumentException) { /* ожидаемо */ }
    }
    private fun assertEigContains(eigs: List<Complex>, re: Double, im: Double = 0.0, eps: Double = 1e-5) {
        assertTrue("Нет собственного значения $re + ${im}i среди $eigs", eigs.any { abs(it.re-re)<eps && abs(it.im-im)<eps })
    }

    @Test fun identityAndTranspose_18cases() {
        for (n in 1..8) matrix(diag(*DoubleArray(n) { 1.0 }), math.identityMatrix(n)) // 8
        for (rows in 1..5) for (cols in 1..2) { // 10
            val a = Array(rows) { i -> DoubleArray(cols) { j -> (i * 3 + j + 1).toDouble() } }
            matrix(a, math.transpose(math.transpose(a)))
        }
    }

    @Test fun additionSubtraction_20cases() {
        for (n in 1..10) { // 20
            val a = diag(*DoubleArray(n) { (it + 1).toDouble() })
            val b = math.multiplyScalar(a, 2.0)
            matrix(math.multiplyScalar(a, 3.0), math.add(a, b))
            matrix(a, math.subtract(b, a))
        }
        assertNull(math.add(m(doubleArrayOf(1.0, 2.0)), m(doubleArrayOf(1.0))))
        assertNull(math.subtract(m(doubleArrayOf(1.0, 2.0)), m(doubleArrayOf(1.0))))
    }

    @Test fun multiplicationAndScalar_20cases() {
        for (n in 1..10) { // 20
            val a = diag(*DoubleArray(n) { (it + 1).toDouble() })
            matrix(a, math.multiply(a, math.identityMatrix(n)))
            matrix(diag(*DoubleArray(n) { (it + 1.0).pow(2) }), math.multiply(a, a))
        }
        matrix(m(doubleArrayOf(14.0), doubleArrayOf(32.0)), math.multiply(
            m(doubleArrayOf(1.0,2.0,3.0),doubleArrayOf(4.0,5.0,6.0)),
            m(doubleArrayOf(1.0),doubleArrayOf(2.0),doubleArrayOf(3.0))))
        assertNull(math.multiply(m(doubleArrayOf(1.0), doubleArrayOf(2.0)), m(doubleArrayOf(1.0),doubleArrayOf(2.0))))
        matrix(m(doubleArrayOf(-2.0, -4.0)), math.multiplyScalar(m(doubleArrayOf(1.0,2.0)), -2.0))
    }

    @Test fun determinantAndTrace_24cases() {
        for (n in 1..8) { // 24
            val d = DoubleArray(n) { (it + 1).toDouble() }
            val a = diag(*d)
            near(d.fold(1.0) { acc, v -> acc * v }, math.determinant(a))
            near(d.sum(), math.trace(a))
            near(1.0, math.determinant(math.identityMatrix(n)))
        }
        near(-2.0, math.determinant(m(doubleArrayOf(1.0,2.0),doubleArrayOf(3.0,4.0))))
        near(0.0, math.determinant(m(doubleArrayOf(1.0,2.0),doubleArrayOf(2.0,4.0))))
        assertNull(math.determinant(m(doubleArrayOf(1.0,2.0))))
        assertNull(math.trace(m(doubleArrayOf(1.0,2.0))))
    }

    @Test
    fun inverseAndPower_28cases() {
        for (n in 1..7) {
            val a = diag(*DoubleArray(n) { (it + 2).toDouble() })

            val inv = math.inverse(a)
            assertNotNull(inv)

            matrix(
                math.identityMatrix(n),
                math.multiply(a, inv!!)
            )

            matrix(
                math.identityMatrix(n),
                math.matrixPower(a, 0)
            )

            matrix(
                requireNotNull(math.multiply(a, a)),
                math.matrixPower(a, 2)
            )

            matrix(
                inv,
                math.matrixPower(a, -1)
            )
        }

        assertNull(
            math.inverse(
                m(
                    doubleArrayOf(1.0, 2.0),
                    doubleArrayOf(2.0, 4.0)
                )
            )
        )

        assertNull(
            math.matrixPower(
                m(doubleArrayOf(1.0, 2.0)),
                2
            )
        )

        assertNull(
            math.matrixPower(
                diag(0.0, 2.0),
                -1
            )
        )

        assertNull(
            math.matrixPower(
                diag(2.0),
                Int.MIN_VALUE
            )
        )
    }

    @Test fun ranksAndNorms_18cases() {
        for (n in 1..6) { // 18
            near(n.toDouble(), math.rank(math.identityMatrix(n)).toDouble())
            near(sqrt(n.toDouble()), math.norm(math.identityMatrix(n)))
            near(1.0, math.spectralNorm(math.identityMatrix(n)))
        }
        assertEquals(1, math.rank(m(doubleArrayOf(1.0,2.0),doubleArrayOf(2.0,4.0))))
        assertEquals(0, math.rank(diag(0.0,0.0)))
        near(5.0, math.norm(m(doubleArrayOf(3.0,4.0))))
        near(5.0, math.spectralNorm(diag(3.0,5.0)))
    }

    @Test fun dotAndCross_12cases() {
        val x = m(doubleArrayOf(1.0,0.0,0.0))
        val y = m(doubleArrayOf(0.0,1.0,0.0))
        val z = m(doubleArrayOf(0.0,0.0,1.0))
        near(0.0, math.dotProduct(x,y))
        near(1.0, math.dotProduct(x,x))
        near(0.0, math.dotProduct(y,z))
        matrix(z, math.crossProduct(x,y))
        matrix(math.multiplyScalar(z,-1.0), math.crossProduct(y,x))
        matrix(x, math.crossProduct(y,z))
        matrix(y, math.crossProduct(z,x))
        matrix(m(doubleArrayOf(0.0,0.0,0.0)), math.crossProduct(x,x))
        assertNull(math.dotProduct(x,m(doubleArrayOf(1.0,2.0))))
        assertNull(math.crossProduct(x,m(doubleArrayOf(1.0,2.0))))
        near(32.0, math.dotProduct(m(doubleArrayOf(1.0,2.0,3.0)),m(doubleArrayOf(4.0,5.0,6.0))))
        matrix(m(doubleArrayOf(-3.0,6.0,-3.0)), math.crossProduct(m(doubleArrayOf(1.0,2.0,3.0)),m(doubleArrayOf(4.0,5.0,6.0))))
    }

    @Test fun exponential_14cases() {
        for (v in listOf(-2.0,-1.0,-0.5,0.0,0.5,1.0,2.0)) { // 14
            matrix(diag(exp(v)), math.matrixExponential(diag(v)), 2e-6)
            matrix(diag(exp(v), exp(-v)), math.matrixExponential(diag(v,-v)), 2e-6)
        }
        val nilpotent = m(doubleArrayOf(0.0,1.0),doubleArrayOf(0.0,0.0))
        matrix(m(doubleArrayOf(1.0,1.0),doubleArrayOf(0.0,1.0)), math.matrixExponential(nilpotent))
    }

    @Test fun logarithm_12cases() {
        for (v in listOf(0.25,0.5,0.75,1.0,1.25,1.5)) { // 12
            matrix(diag(ln(v)), math.matrixLog(diag(v)), 1e-5)
            matrix(diag(ln(v),ln(2.0)), math.matrixLog(diag(v,2.0)), 1e-5)
        }
        matrix(m(doubleArrayOf(ln(2.0),0.5,-0.125),doubleArrayOf(0.0,ln(2.0),0.5),doubleArrayOf(0.0,0.0,ln(2.0))),
            math.matrixLog(m(doubleArrayOf(2.0,1.0,0.0),doubleArrayOf(0.0,2.0,1.0),doubleArrayOf(0.0,0.0,2.0))), 2e-5)
    }

    @Test fun squareRoot_12cases() {
        for (v in listOf(0.25,0.5,1.0,2.0,4.0,9.0)) { // 12
            matrix(diag(sqrt(v)), math.matrixSqrt(diag(v)), 1e-6)
            matrix(diag(sqrt(v),2.0), math.matrixSqrt(diag(v,4.0)), 1e-6)
        }
    }

    @Test fun trigonometry_20cases() {
        for (v in listOf(-2.0,-1.0,-0.5,0.0,0.5,1.0,1.5,2.0,3.0,4.0)) { // 20
            val (s,c) = math.matrixTrig(diag(v))
            matrix(diag(sin(v)), s, 2e-6)
            matrix(diag(cos(v)), c, 2e-6)
        }
        val a = diag(0.2,1.0)
        val (s,c) = math.matrixTrig(a)
        matrix(math.identityMatrix(2), math.add(math.multiply(s,s)!!,math.multiply(c,c)!!), 2e-6)
    }

    @Test fun controllability_10cases() {
        val a = m(doubleArrayOf(0.0,1.0),doubleArrayOf(-2.0,-3.0))
        val b = m(doubleArrayOf(0.0),doubleArrayOf(1.0))
        matrix(m(doubleArrayOf(0.0,1.0),doubleArrayOf(1.0,-3.0)),math.controllability(a,b))
        assertEquals(2, math.rank(math.controllability(a,b)))
        val bi = math.identityMatrix(2)
        matrix(m(doubleArrayOf(1.0,0.0,0.0,1.0),doubleArrayOf(0.0,1.0,-2.0,-3.0)),math.controllability(a,bi))
        assertEquals(2, math.rank(math.controllability(a,bi)))
        assertEquals(1, math.rank(math.controllability(diag(1.0,2.0),m(doubleArrayOf(1.0),doubleArrayOf(0.0)))))
        matrix(m(doubleArrayOf(1.0)),math.controllability(diag(2.0),m(doubleArrayOf(1.0))))
        expectInvalid { math.controllability(a,m(doubleArrayOf(1.0))) }
        expectInvalid { math.controllability(a,m(doubleArrayOf(1.0,2.0,3.0),doubleArrayOf(1.0))) }
        expectInvalid { math.controllability(a,m(doubleArrayOf(),doubleArrayOf())) }
        expectInvalid { math.controllability(m(doubleArrayOf(1.0,2.0)),b) }
    }

    @Test fun observability_10cases() {
        val a = m(doubleArrayOf(0.0,1.0),doubleArrayOf(-2.0,-3.0))
        val c = m(doubleArrayOf(1.0,0.0))
        matrix(m(doubleArrayOf(1.0,0.0),doubleArrayOf(0.0,1.0)),math.observability(a,c))
        assertEquals(2, math.rank(math.observability(a,c)))
        matrix(m(doubleArrayOf(1.0,0.0),doubleArrayOf(0.0,1.0),doubleArrayOf(0.0,1.0),doubleArrayOf(-2.0,-3.0)),math.observability(a,math.identityMatrix(2)))
        assertEquals(2, math.rank(math.observability(a,math.identityMatrix(2))))
        assertEquals(1, math.rank(math.observability(diag(1.0,2.0),c)))
        matrix(m(doubleArrayOf(1.0)),math.observability(diag(2.0),m(doubleArrayOf(1.0))))
        expectInvalid { math.observability(a,m(doubleArrayOf(1.0))) }
        expectInvalid { math.observability(a,m(doubleArrayOf(1.0,0.0),doubleArrayOf(1.0))) }
        expectInvalid { math.observability(a,emptyArray()) }
        expectInvalid { math.observability(m(doubleArrayOf(1.0,2.0)),c) }
    }

    @Test fun eigenvalues_12cases() {
        for (v in listOf(-3.0,-1.0,0.0,1.0,2.0,4.0)) { // 12
            assertEigContains(math.eigenvalues(diag(v)),v)
            val es = math.eigenvalues(diag(v,v+1.0))
            assertEigContains(es,v)
            assertEigContains(es,v+1.0)
        }
        val es = math.eigenvalues(m(doubleArrayOf(0.0,-2.0),doubleArrayOf(2.0,0.0)))
        assertEigContains(es,0.0,2.0)
        assertEigContains(es,0.0,-2.0)
    }

    @Test fun eigenvectors_8cases() {
        for (v in listOf(-2.0,-1.0,0.0,1.0,2.0,3.0,4.0,5.0)) {
            val a = diag(v,v+2.0)
            val eigs = math.eigenvalues(a)
            val vecs = math.eigenvectors(a,eigs)
            assertEquals(eigs.size,vecs.size)
            for (k in eigs.indices) {
                val x = vecs[k]
                assertEquals(2,x.size)
                assertTrue("Нулевой собственный вектор", x.sumOf { it.abs()*it.abs() } > 0.5)
                for (i in 0..1) {
                    val lhs = Complex(a[i][0])*x[0] + Complex(a[i][1])*x[1]
                    val rhs = eigs[k]*x[i]
                    assertEquals(lhs.re,rhs.re,1e-6)
                    assertEquals(lhs.im,rhs.im,1e-6)
                }
            }
        }
    }

    @Test fun frobenius_5cases() {
        val a = m(doubleArrayOf(0.0,1.0),doubleArrayOf(-2.0,-3.0))
        val b = m(doubleArrayOf(0.0),doubleArrayOf(1.0))
        val result = math.frobeniusForm(a,b)!!
        matrix(a,result.first)
        matrix(b,result.second)
        matrix(math.identityMatrix(2),result.third)
        val altA = m(doubleArrayOf(-3.0,-2.0),doubleArrayOf(1.0,0.0))
        val altB = m(doubleArrayOf(1.0),doubleArrayOf(0.0))
        val (af,bf,t) = math.frobeniusForm(altA,altB)!!
        matrix(af,math.multiply(math.multiply(math.inverse(t)!!,altA)!!,t))
        matrix(bf,math.multiply(math.inverse(t)!!,altB))
    }

    @Test fun ackermann_7cases() {
        val a = m(doubleArrayOf(0.0,1.0),doubleArrayOf(-2.0,-3.0))
        val b = m(doubleArrayOf(0.0),doubleArrayOf(1.0))
        for (p in listOf(-2.0 to -3.0,-3.0 to -4.0,-4.0 to -5.0,-1.0 to -6.0,-2.0 to -7.0)) {
            val k = math.ackermann(a,b,listOf(Complex(p.first),Complex(p.second)))
            val closed = math.subtract(a,math.multiply(b,k)!!)!!
            val eigs = math.eigenvalues(closed)
            assertEigContains(eigs,p.first,eps=1e-4)
            assertEigContains(eigs,p.second,eps=1e-4)
        }
        try { math.ackermann(a,b,listOf(Complex(-1.0))); fail("Ожидалось исключение") }
        catch (_: Exception) { /* ожидаемо */ }
        try { math.ackermann(diag(1.0,2.0),m(doubleArrayOf(1.0),doubleArrayOf(0.0)),listOf(Complex(-1.0),Complex(-2.0))); fail("Ожидалось исключение") }
        catch (_: Exception) { /* ожидаемо */ }
    }

    @Test fun lqr_4cases() {
        val a = m(doubleArrayOf(0.0,1.0),doubleArrayOf(-2.0,-3.0))
        val b = math.identityMatrix(2)
        for (r in listOf(0.5,1.0,2.0,4.0)) {
            val (p,k) = math.solveLQR(a,b,math.identityMatrix(2),r)
            matrix(math.multiplyScalar(p,1.0/r),k,2e-4)
            val atp = math.multiply(math.transpose(a),p)!!
            val pa = math.multiply(p,a)!!
            val pp = math.multiply(p,p)!!
            val residual = math.add(math.subtract(math.add(atp,pa)!!,math.multiplyScalar(pp,1.0/r))!!,math.identityMatrix(2))!!
            assertTrue("Невязка Риккати R=$r: ${math.norm(residual)}",math.norm(residual)<2e-3)
        }
    }

    @Test fun jordan_6cases() {
        val j = m(doubleArrayOf(3.0,1.0,0.0),doubleArrayOf(0.0,3.0,1.0),doubleArrayOf(0.0,0.0,3.0))
        val (_,complex) = math.jordanForm(j)
        assertEquals(3,complex.size)
        for (i in 0..2) assertEquals("3",complex[i][i])
        assertEquals("1",complex[0][1])
        assertEquals("1",complex[1][2])
        val (_,real) = math.realJordanForm(j)
        assertEquals(3,real.size)
        assertEquals("1",real[0][1])
        assertEquals("1",real[1][2])
    }

    @Test fun parsePoles_10cases() {
        val cases = listOf(
            "2" to Complex(2.0), "-3" to Complex(-3.0), "i" to Complex(0.0,1.0),
            "-i" to Complex(0.0,-1.0), "2i" to Complex(0.0,2.0),
            "-2i" to Complex(0.0,-2.0), "1+2i" to Complex(1.0,2.0),
            "1-2i" to Complex(1.0,-2.0), "-1+2i" to Complex(-1.0,2.0),
            "-1-2i" to Complex(-1.0,-2.0)
        )
        for ((text,expected) in cases) {
            val actual = math.parsePoles(text).single()
            assertEquals("Re($text)",expected.re,actual.re,1e-9)
            assertEquals("Im($text)",expected.im,actual.im,1e-9)
        }
    }
}
