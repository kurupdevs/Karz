package com.kurupdevs.karz.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Plain JVM tests for the math engine. Run with: ./gradlew :math:test
 * Needs junit:junit:4.13.2 in the math module's testImplementation.
 */
class AmortizationTest {

    private val start = LocalDate.of(2026, 10, 5)

    // ------------------------------------------------------------------
    // EMI
    // ------------------------------------------------------------------

    @Test
    fun `emi matches known vector 50L at 8 point 5 for 240 months`() {
        // Rs 50,00,000 @ 8.5% / 240mo -> Rs 43,391.16
        val emi = emiFor(500_000_000L, BigDecimal("8.5"), 240)
        assertEquals(4_339_116L, emi)
    }

    @Test
    fun `zero rate loan is a straight split`() {
        val emi = emiFor(120_000_000L, BigDecimal.ZERO, 12)
        assertEquals(10_000_000L, emi) // Rs 1,00,000 exactly
        val schedule = generateSchedule(120_000_000L, BigDecimal.ZERO, 12, start)
        assertTrue(schedule.all { it.interestMinor == 0L })
        assertEquals(0L, schedule.last().balanceAfterMinor)
        assertEquals(120_000_000L, schedule.sumOf { it.principalMinor })
    }

    // ------------------------------------------------------------------
    // Schedule integrity
    // ------------------------------------------------------------------

    @Test
    fun `last installment absorbs residue and balance ends exactly at zero`() {
        // Odd principal and rate chosen to force rounding residue.
        val p = 12_345_678_900L // Rs 12,34,56,789
        val schedule = generateSchedule(p, BigDecimal("9.25"), 180, start)
        assertEquals(180, schedule.size)
        assertEquals(0L, schedule.last().balanceAfterMinor)
        assertEquals(p, schedule.sumOf { it.principalMinor })
        assertTrue(schedule.all { it.balanceAfterMinor >= 0 })
        // Every installment date steps exactly one month.
        schedule.forEachIndexed { i, inst ->
            assertEquals(start.plusMonths(i.toLong()), inst.dueDate)
            assertEquals(i + 1, inst.n)
        }
    }

    @Test
    fun `total interest is consistent between schedule and closed form`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val totalInterest = schedule.sumOf { it.interestMinor }
        val totalPaid = schedule.sumOf { it.emiMinor }
        assertEquals(totalPaid - 500_000_000L, totalInterest)
        assertTrue(totalInterest > 0)
    }

    // ------------------------------------------------------------------
    // tenureFromEmi
    // ------------------------------------------------------------------

    @Test
    fun `tenureFromEmi returns the true minimum months`() {
        // Note: a paise-rounded EMI can sit a hair below the exact EMI, so the
        // answer may be one MORE than the tenure the EMI was quoted for
        // (e.g. the 240mo quote pays off in 241 at the rounded EMI). What must
        // hold is minimality: `back` months clear the loan, `back - 1` do not.
        val cases = listOf(
            Triple(500_000_000L, BigDecimal("8.5"), 240),
            Triple(300_000_000L, BigDecimal("9.0"), 180),
            Triple(750_000_000L, BigDecimal("7.35"), 300),
            Triple(120_000_000L, BigDecimal("11.5"), 60),
        )
        for ((p, rate, n) in cases) {
            val emi = emiFor(p, rate, n)
            val back = tenureFromEmi(p, rate, emi)
            val r = monthlyRate(rate)
            assertTrue("p=$p rate=$rate n=$n", paysOffIn(p, r, emi, back))
            if (back > 1) {
                assertTrue(
                    "p=$p rate=$rate n=$n: $back is not minimal",
                    !paysOffIn(p, r, emi, back - 1),
                )
            }
        }
    }

    /** Independent payoff check: does paying [emi] for [months] clear [p]? */
    private fun paysOffIn(p: Long, r: BigDecimal, emi: Long, months: Int): Boolean {
        var bal = BigDecimal(p)
        repeat(months) {
            val interest = if (r.signum() == 0) BigDecimal.ZERO
            else bal.multiply(r, MATH_CONTEXT).setScale(0, RoundingMode.HALF_UP)
            bal = bal.subtract(BigDecimal(emi).subtract(interest))
            if (bal <= BigDecimal.ZERO) return true
        }
        return bal <= BigDecimal.ZERO
    }

    @Test
    fun `tenureFromEmi exact on zero rate`() {
        assertEquals(12, tenureFromEmi(120_000_000L, BigDecimal.ZERO, 10_000_000L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `tenureFromEmi rejects emi that does not cover interest`() {
        tenureFromEmi(500_000_000L, BigDecimal("8.5"), 1_000_000L) // Rs 10,000 < monthly interest
    }

    // ------------------------------------------------------------------
    // LTV bands
    // ------------------------------------------------------------------

    @Test
    fun `ltv band edges`() {
        assertEquals("Strong equity", ltvBand(60.0).label)
        assertEquals("Healthy", ltvBand(60.01).label)
        assertEquals("Healthy", ltvBand(75.0).label)
        assertEquals("High leverage", ltvBand(75.01).label)
        assertEquals("High leverage", ltvBand(90.0).label)
        assertEquals("Thin equity", ltvBand(90.01).label)
        assertEquals("Thin equity", ltvBand(112.5).label)
        assertTrue(ltvBand(60.0).copy.isNotBlank())
        // 70% band carries the highest-ROI prepayment copy.
        assertTrue(ltvBand(70.0).copy.contains("highest ROI"))
    }

    // ------------------------------------------------------------------
    // Prepay scenarios
    // ------------------------------------------------------------------

    @Test
    fun `tenure cut saves more interest than emi cut`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val cmp = comparePrepayStrategies(schedule, BigDecimal("8.5"), 50_000_000L) // Rs 5L lump
        assertTrue(cmp.reduceTenure.interestSavedMinor > cmp.reduceEmi.interestSavedMinor)
        assertEquals(PrepayStrategy.REDUCE_TENURE, cmp.recommended)
        // EMI-cut keeps the payoff date; tenure-cut moves it earlier.
        assertTrue(cmp.reduceEmi.payoffSame)
        assertEquals(schedule.last().dueDate, cmp.reduceEmi.newSchedule.last().dueDate)
        assertTrue(cmp.reduceTenure.newPayoffDate.isBefore(schedule.last().dueDate))
    }

    @Test
    fun `reduce emi lowers emi and keeps tenure`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val res = prepayScenario(schedule, BigDecimal("8.5"), 50_000_000L, PrepayStrategy.REDUCE_EMI) as ReduceEmiResult
        assertTrue(res.newEmiMinor < schedule[0].emiMinor)
        assertEquals(240, res.newSchedule.size)
        assertTrue(res.interestSavedMinor > 0)
    }

    @Test
    fun `prepay mid loan uses remaining balance`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val paid = 60
        val res = prepayScenario(schedule, BigDecimal("8.5"), 50_000_000L, PrepayStrategy.REDUCE_TENURE, paid) as ReduceTenureResult
        val balanceAfter60 = schedule[paid - 1].balanceAfterMinor
        // The new schedule is built on the reduced balance, starting at the next due date.
        assertEquals(schedule[paid].dueDate, res.newSchedule.first().dueDate)
        assertTrue(res.newTenureMonths < 180)
        val expectedNewBalance = balanceAfter60 - 50_000_000L
        val actualNewBalance = res.newSchedule[0].principalMinor + res.newSchedule[0].balanceAfterMinor
        assertEquals(expectedNewBalance, actualNewBalance)
    }

    // ------------------------------------------------------------------
    // Overpayment simulation (recurring extra + lump)
    // ------------------------------------------------------------------

    @Test
    fun `zero extra and zero lump is a no-op`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val sim = simulateOverpayment(schedule, BigDecimal("8.5"), 0L, 0L, PrepayStrategy.REDUCE_TENURE)
        assertEquals(0L, sim.interestSavedMinor)
        assertEquals(0, sim.monthsEarly)
        assertEquals(sim.baseEmiMinor, sim.newEmiMinor)
        assertEquals(schedule.last().dueDate, sim.newPayoffDate)
    }

    @Test
    fun `monthly extra shortens the loan and saves interest`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val sim = simulateOverpayment(schedule, BigDecimal("8.5"), 0L, 500_000L, PrepayStrategy.REDUCE_TENURE) // Rs 5k/mo
        assertTrue(sim.monthsEarly > 0)
        assertTrue(sim.interestSavedMinor > 0)
        assertTrue(sim.newPayoffDate.isBefore(sim.basePayoffDate))
        assertEquals(0L, sim.newSchedule.last().balanceAfterMinor)
        assertEquals(sim.newSchedule.size.toLong(), (240 - sim.monthsEarly).toLong())
    }

    @Test
    fun `lump that covers the loan closes it immediately`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val balance = schedule[0].principalMinor + schedule[0].balanceAfterMinor
        val sim = simulateOverpayment(schedule, BigDecimal("8.5"), balance, 0L, PrepayStrategy.REDUCE_TENURE)
        assertTrue(sim.newSchedule.isEmpty())
        assertEquals(sim.baseTotalInterestMinor, sim.interestSavedMinor)
    }

    @Test
    fun `overpayment comparison recommends tenure cut for standard case`() {
        val schedule = generateSchedule(500_000_000L, BigDecimal("8.5"), 240, start)
        val cmp = compareOverpaymentStrategies(schedule, BigDecimal("8.5"), 100_000_00L, 500_000L)
        assertTrue(cmp.reduceTenure.interestSavedMinor >= cmp.reduceEmi.interestSavedMinor)
        assertTrue(cmp.recommendationReason.isNotBlank())
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    @Test
    fun `inr money formatting`() {
        assertTrue(formatMoney(500_000_000L, "INR").contains("50,00,000"))
        assertEquals("\u20B950L", formatMoneyCompact(500_000_000L, "INR"))
        assertEquals("\u20B91.25Cr", formatMoneyCompact(1_250_000_000L, "INR"))
        assertEquals("\u00A31.2M", formatMoneyCompact(120_000_000L, "GBP"))
    }

    @Test
    fun `duration formatting`() {
        assertEquals("6 years 1 month", formatDuration(73))
        assertEquals("1 year 2 months", formatDuration(14))
        assertEquals("11 months", formatDuration(11))
        assertEquals("1 month", formatDuration(1))
    }
}
