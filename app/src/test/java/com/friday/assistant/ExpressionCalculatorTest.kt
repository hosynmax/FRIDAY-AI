package com.friday.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpressionCalculatorTest {
    @Test fun respectsOperatorPrecedence() {
        assertEquals(14.0, ExpressionCalculator.evaluate("2+3*4"), 0.000001)
    }

    @Test fun supportsParenthesesAndUnarySigns() {
        assertEquals(-10.0, ExpressionCalculator.evaluate("-(2+3)*2"), 0.000001)
    }

    @Test fun supportsPercentAndDecimalComma() {
        assertEquals(2.5, ExpressionCalculator.evaluate("250,0*1%"), 0.000001)
    }

    @Test fun rejectsDivisionByZero() {
        assertThrows(IllegalArgumentException::class.java) {
            ExpressionCalculator.evaluate("12/0")
        }
    }
}
