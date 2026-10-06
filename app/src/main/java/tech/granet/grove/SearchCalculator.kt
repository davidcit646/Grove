package tech.granet.grove

import java.math.BigDecimal
import java.math.MathContext

/** Bounded arithmetic only. No script engine, network, persistence or Android dependency. */
internal object SearchCalculator {
    sealed class Result {
        data object NotCalculation : Result()
        data class Answer(val expression: String, val value: String, val approximate: Boolean) : Result()
        data class Invalid(val reason: Reason) : Result()
    }
    enum class Reason { SYNTAX, DIVISION_BY_ZERO, LIMIT }
    fun calculate(query: String): Result {
        if (query.length > 256) return Result.Invalid(Reason.LIMIT)
        val text = query.trim()
        if (!text.endsWith("=") || text.any { it.isLetter() }) return Result.NotCalculation
        val expression = text.dropLast(1).trim()
        return try {
            val parser = Parser(expression)
            val value = parser.parse().stripTrailingZeros()
            val answer = value.toPlainString()
            if (answer.length > 128) Result.Invalid(Reason.LIMIT)
            else Result.Answer(expression, answer, parser.approximate)
        } catch (error: InvalidExpression) { Result.Invalid(error.reason) }
    }
    private class InvalidExpression(val reason: Reason) : Exception()
    private class Parser(private val text: String) {
        private var index = 0
        private var operations = 0
        var approximate = false
            private set
        fun parse(): BigDecimal {
            val result = expression(0)
            whitespace()
            if (index != text.length) fail(Reason.SYNTAX)
            return result
        }
        private fun expression(depth: Int): BigDecimal {
            var value = term(depth)
            while (true) {
                whitespace()
                val op = peek()
                if (op != '+' && op != '-' && op != '−') return value
                operator()
                val right = term(depth)
                value = bounded(if (op == '+') value.add(right) else value.subtract(right))
            }
        }
        private fun term(depth: Int): BigDecimal {
            var value = factor(depth)
            while (true) {
                whitespace()
                val op = peek()
                if (op !in listOf('*', '×', '/', '÷')) return value
                operator()
                val right = factor(depth)
                value = bounded(if (op == '*' || op == '×') value.multiply(right) else {
                    if (right.signum() == 0) fail(Reason.DIVISION_BY_ZERO)
                    try { value.divide(right) } catch (_: ArithmeticException) {
                        approximate = true
                        value.divide(right, MathContext.DECIMAL128)
                    }
                })
            }
        }
        private fun factor(depth: Int): BigDecimal {
            whitespace()
            var negative = false
            while (peek() in listOf('+', '-', '−')) {
                if (peek() != '+') negative = !negative
                operator(); whitespace()
            }
            val value = if (peek() == '(') {
                if (depth >= 16) fail(Reason.LIMIT)
                index++
                val inner = expression(depth + 1)
                whitespace()
                if (peek() != ')') fail(Reason.SYNTAX)
                index++; inner
            } else number()
            return if (negative) value.negate() else value
        }
        private fun number(): BigDecimal {
            val start = index
            var digits = 0
            while (peek() in '0'..'9') { index++; digits++ }
            if (peek() == '.') {
                index++
                while (peek() in '0'..'9') { index++; digits++ }
            }
            if (digits == 0) fail(Reason.SYNTAX)
            if (index - start > 64) fail(Reason.LIMIT)
            return BigDecimal(text.substring(start, index))
        }
        private fun bounded(value: BigDecimal): BigDecimal {
            if (value.precision() > 128 || kotlin.math.abs(value.scale()) > 128) fail(Reason.LIMIT)
            return value
        }
        private fun operator() { index++; if (++operations > 128) fail(Reason.LIMIT) }
        private fun peek(): Char = text.getOrNull(index) ?: '\u0000'
        private fun whitespace() { while (peek().isWhitespace()) index++ }
        private fun fail(reason: Reason): Nothing = throw InvalidExpression(reason)
    }
}
