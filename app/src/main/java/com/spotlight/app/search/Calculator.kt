package com.spotlight.app.search

/**
 * 递归下降的四则运算求值器，支持 + - * / % ^、括号、常用函数和常量。
 * 不执行任何脚本，也不会抛异常：无法解析时返回 null。
 */
object Calculator {

    private val mathChars = Regex("^[0-9a-zA-Z+\\-*/%^()., ×÷π]+$")
    private val functionCall = Regex("(?i)\\b(sqrt|sin|cos|tan|log|ln|abs|exp)\\s*\\(")

    fun looksLikeExpression(raw: String): Boolean {
        val text = raw.trim()
        if (text.isEmpty() || text.length > 120) return false
        if (!mathChars.matches(text)) return false
        if (!text.any { it.isDigit() }) return false
        // 纯数字不弹计算器
        return text.any { it in "+-*/%^×÷" } || functionCall.containsMatchIn(text) || text.contains("pi") || text.contains("π")
    }

    fun evaluate(raw: String): String? {
        val value = runCatching { Parser(raw).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return format(value)
    }

    private fun format(value: Double): String {
        val rounded = Math.round(value * 1_000_000.0) / 1_000_000.0
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) {
            return rounded.toLong().toString()
        }
        return java.math.BigDecimal(rounded.toString()).stripTrailingZeros().toPlainString()
    }

    private class Parser(private val input: String) {
        private var pos = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            if (pos < input.length) throw IllegalStateException("Unexpected: ${input[pos]}")
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                val c = peek() ?: return value
                value = when (c) {
                    '+' -> { pos++; value + term() }
                    '-' -> { pos++; value - term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpaces()
                val c = peek() ?: return value
                value = when (c) {
                    '*', '×' -> { pos++; value * power() }
                    '/', '÷' -> { pos++; value / power() }
                    '%' -> {
                        pos++
                        // 百分号：既支持 "50%" 也支持 "200*5%"
                        value / 100.0
                    }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            skipSpaces()
            if (peek() == '^') {
                pos++
                return Math.pow(base, power())
            }
            return base
        }

        private fun unary(): Double {
            skipSpaces()
            return when (peek()) {
                '-' -> { pos++; -unary() }
                '+' -> { pos++; unary() }
                else -> primary()
            }
        }

        private fun primary(): Double {
            skipSpaces()
            val c = peek() ?: throw IllegalStateException("Unexpected end")
            if (c == '(') {
                pos++
                val value = expression()
                skipSpaces()
                if (peek() != ')') throw IllegalStateException("Missing )")
                pos++
                return value
            }
            if (c.isLetter() || c == 'π') return identifier()
            return number()
        }

        private fun identifier(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isLetter() || input[pos] == 'π')) pos++
            val name = input.substring(start, pos).lowercase()
            if (name == "pi" || name == "π") return Math.PI
            if (name == "e") return Math.E
            skipSpaces()
            if (peek() != '(') throw IllegalStateException("Expected ( after $name")
            pos++
            val arg = expression()
            skipSpaces()
            if (peek() != ')') throw IllegalStateException("Missing )")
            pos++
            return when (name) {
                "sqrt" -> Math.sqrt(arg)
                "sin" -> Math.sin(arg)
                "cos" -> Math.cos(arg)
                "tan" -> Math.tan(arg)
                "log" -> Math.log10(arg)
                "ln" -> Math.log(arg)
                "abs" -> Math.abs(arg)
                "exp" -> Math.exp(arg)
                else -> throw IllegalStateException("Unknown function $name")
            }
        }

        private fun number(): Double {
            skipSpaces()
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
            if (start == pos) throw IllegalStateException("Expected number")
            return input.substring(start, pos).toDouble()
        }

        private fun peek(): Char? = if (pos < input.length) input[pos] else null

        private fun skipSpaces() {
            while (pos < input.length && input[pos].isWhitespace()) pos++
        }
    }
}
