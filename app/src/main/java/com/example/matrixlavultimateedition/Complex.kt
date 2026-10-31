package com.example.matrixcalculator

import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

data class Complex(
    val re: Double,
    val im: Double = 0.0
) {

    operator fun plus(b: Complex): Complex {
        return Complex(
            re + b.re,
            im + b.im
        )
    }

    operator fun minus(b: Complex): Complex {
        return Complex(
            re - b.re,
            im - b.im
        )
    }

    operator fun unaryMinus(): Complex {
        return Complex(-re, -im)
    }

    operator fun times(b: Complex): Complex {
        return Complex(
            re * b.re - im * b.im,
            re * b.im + im * b.re
        )
    }

    operator fun times(k: Double): Complex {
        return Complex(
            re * k,
            im * k
        )
    }

    operator fun div(k: Double): Complex {
        require(k != 0.0 && k.isFinite()) {
            "Деление на ноль или некорректный скаляр"
        }

        return Complex(
            re / k,
            im / k
        )
    }

    operator fun div(b: Complex): Complex {
        require(
            b.re.isFinite() &&
                    b.im.isFinite() &&
                    (b.re != 0.0 || b.im != 0.0)
        ) {
            "Деление на нулевое или некорректное комплексное число"
        }

        // Устойчивее прямого вычисления
        // b.re * b.re + b.im * b.im
        return if (abs(b.re) >= abs(b.im)) {

            val ratio = b.im / b.re
            val denominator = b.re + b.im * ratio

            Complex(
                (re + im * ratio) / denominator,
                (im - re * ratio) / denominator
            )

        } else {

            val ratio = b.re / b.im
            val denominator = b.im + b.re * ratio

            Complex(
                (re * ratio + im) / denominator,
                (im * ratio - re) / denominator
            )
        }
    }

    fun abs(): Double {
        return hypot(re, im)
    }

    override fun toString(): String {

        fun formatNumber(value: Double): String {
            if (!value.isFinite()) {
                return value.toString()
            }

            val cleaned = if (abs(value) < 1e-4) {
                0.0
            } else {
                value
            }

            return String.format(
                Locale.US,
                "%.3f",
                cleaned
            ).trimEnd('0')
                .trimEnd('.')
                .ifEmpty { "0" }
        }

        val real = if (abs(re) < 1e-4) 0.0 else re
        val imaginary = if (abs(im) < 1e-4) 0.0 else im

        return when {

            imaginary == 0.0 -> {
                formatNumber(real)
            }

            real == 0.0 -> {
                if (imaginary > 0.0) {
                    "${formatNumber(imaginary)}i"
                } else {
                    "-${formatNumber(-imaginary)}i"
                }
            }

            imaginary > 0.0 -> {
                "${formatNumber(real)} + ${formatNumber(imaginary)}i"
            }

            else -> {
                "${formatNumber(real)} - ${formatNumber(-imaginary)}i"
            }
        }
    }
}