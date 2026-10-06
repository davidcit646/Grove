package tech.granet.grove

import org.junit.Assert.*
import org.junit.Test

class SearchCalculatorTest {
    private fun answer(query: String, value: String, approximate: Boolean = false) {
        val result = SearchCalculator.calculate(query) as SearchCalculator.Result.Answer
        assertEquals(value, result.value)
        assertEquals(approximate, result.approximate)
    }
    private fun invalid(query: String, reason: SearchCalculator.Reason = SearchCalculator.Reason.SYNTAX) {
        assertEquals(SearchCalculator.Result.Invalid(reason), SearchCalculator.calculate(query))
    }
    @Test fun numbersAndPrecedence() {
        answer("42=", "42")
        answer("2+3*4=", "14")
        answer("(2+3)*4=", "20")
        answer("20/5/2=", "2")
        answer("9-3-2=", "4")
        answer(" 2 + 2 = ", "4")
    }
    @Test fun decimalsAreExactWithoutBinaryFloatingPointArtifacts() {
        answer("0.1+0.2=", "0.3")
        answer("1.25*8=", "10")
        answer("1/8=", "0.125")
        answer(".5+1.=", "1.5")
        answer("1.00-1=", "0")
    }
    @Test fun negativeNumbersUnarySignsAndKeyboardOperators() {
        answer("-2*-3=", "6")
        answer("2--3=", "5")
        answer("-(2+3)=", "-5")
        answer("−6÷2+4×3=", "9")
        answer("--2=", "2")
        answer("-0=", "0")
    }
    @Test fun nonTerminatingDivisionIsExplicitlyApproximate() {
        answer("1/3=", "0.3333333333333333333333333333333333", true)
        val result = SearchCalculator.calculate("(1/3)*3=") as SearchCalculator.Result.Answer
        assertTrue(result.approximate)
    }
    @Test fun zeroDivisionAndMalformedExpressionsNeverReturnAnswers() {
        invalid("1/0=", SearchCalculator.Reason.DIVISION_BY_ZERO)
        invalid("1/(2-2)=", SearchCalculator.Reason.DIVISION_BY_ZERO)
        listOf("=", "2+=", "2==", "2(3)=", "(2+3=", "2+3)=", "1..2=", "2^3=", ".=").forEach { invalid(it) }
    }
    @Test fun ordinaryQueriesAndUnfinishedArithmeticStayInNormalSearch() {
        listOf("", "wifi", "2+2", "2+2=4", "contacts=", "Runtime.exec(1)=", "sin(2)=", "https://example.com/?x=", "1e3=")
            .forEach { assertEquals(SearchCalculator.Result.NotCalculation, SearchCalculator.calculate(it)) }
    }
    @Test fun inputLiteralNestingAndResultLimitsAreEnforced() {
        invalid("1".repeat(257)+"=", SearchCalculator.Reason.LIMIT)
        invalid("1".repeat(65)+"=", SearchCalculator.Reason.LIMIT)
        invalid("(".repeat(17)+"1"+")".repeat(17)+"=", SearchCalculator.Reason.LIMIT)
        answer("(".repeat(16)+"1"+")".repeat(16)+"=", "1")
        val large = "9".repeat(64)
        invalid("$large*$large*9=", SearchCalculator.Reason.LIMIT)
        val small = "0."+"0".repeat(61)+"1"
        invalid("$small*$small*$small=", SearchCalculator.Reason.LIMIT)
    }
    @Test fun calculatorFramesInvalidateOnAnswerOrErrorAndRespectQueryGeneration() {
        val target = Any(); val gate = SearchFrameGate()
        val answer = SearchCalculator.calculate("2+2=")
        assertTrue(gate.shouldRender(target, listOf("2+2=", answer)))
        assertFalse(gate.shouldRender(target, listOf("2+2=", SearchCalculator.calculate("2+2="))))
        assertTrue(gate.shouldRender(target, listOf("2+3=", SearchCalculator.calculate("2+3="))))
        assertTrue(gate.shouldRender(target, listOf("2/0=", SearchCalculator.calculate("2/0="))))
        assertFalse(SearchPublicationGate.allowed(1, 2, true, true, true))
        assertFalse(SearchPublicationGate.allowed(2, 2, false, true, true))
    }
}
