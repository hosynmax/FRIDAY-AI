package com.friday.assistant

import kotlin.math.abs

/**
 * Safe recursive-descent calculator. No eval(), scripting engine, or network access.
 * Supports +, -, *, /, parentheses, decimals, unary signs and percentage suffix.
 */
object ExpressionCalculator {
    fun evaluate(expression: String): Double {
        val normalized = expression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace(',', '.')
            .replace(" ", "")
        require(normalized.isNotBlank()) { "عبارت خالی است." }
        val parser = Parser(normalized)
        val value = parser.parse()
        require(value.isFinite()) { "نتیجه معتبر نیست." }
        return value
    }

    fun format(value: Double): String {
        val rounded = if (abs(value - value.toLong().toDouble()) < 1e-10) value.toLong().toString()
        else String.format(java.util.Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
        return rounded
    }

    private class Parser(private val source: String) {
        private var position = 0

        fun parse(): Double {
            val result = expression()
            skipSpaces()
            require(position == source.length) { "عبارت نامعتبر است." }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    eat('+') -> value + term()
                    eat('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when {
                    eat('*') -> value * factor()
                    eat('/') -> {
                        val divisor = factor()
                        require(abs(divisor) > 1e-12) { "تقسیم بر صفر مجاز نیست." }
                        value / divisor
                    }
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            skipSpaces()
            if (eat('+')) return factor()
            if (eat('-')) return -factor()
            var value = if (eat('(')) {
                val nested = expression()
                skipSpaces()
                require(eat(')')) { "پرانتز بسته نشده است." }
                nested
            } else number()
            skipSpaces()
            if (eat('%')) value /= 100.0
            return value
        }

        private fun number(): Double {
            skipSpaces()
            val start = position
            var dots = 0
            while (position < source.length) {
                val char = source[position]
                if (char.isDigit()) position++
                else if (char == '.') {
                    dots++
                    require(dots <= 1) { "عدد اعشاری نامعتبر است." }
                    position++
                } else break
            }
            require(position > start) { "عدد معتبر وارد کن." }
            return source.substring(start, position).toDouble()
        }

        private fun eat(char: Char): Boolean {
            if (position < source.length && source[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < source.length && source[position].isWhitespace()) position++
        }
    }
}
