package demo

import demo.Calculator
import demo.JavaCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class MixedTest {

  @Test
  fun `2 + 2 should be 4`() {
    val calculator = Calculator(JavaCalculator())
    assertEquals(4, calculator.sum(2, 2))
  }

}
