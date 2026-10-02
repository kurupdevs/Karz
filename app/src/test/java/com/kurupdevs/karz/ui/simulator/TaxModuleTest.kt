package com.kurupdevs.karz.ui.simulator

import com.kurupdevs.karz.math.generateSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Plain JVM tests for the tax module's pure math.
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class TaxModuleTest {

    private fun schedule24mo() = generateSchedule(
        500_000_000L, BigDecimal("8.5"), 24, LocalDate.of(2026, 6, 15),
    )

    @Test
    fun `fy split groups april to march`() {
        val splits = splitByFinancialYear(schedule24mo())
        assertEquals(2, splits.size)
        assertEquals("FY 2026-27", splits[0].label) // Jun 2026 - Mar 2027: 10 EMIs
        assertEquals("FY 2027-28", splits[1].label) // Apr 2027 - May 2028: 14 EMIs
        val schedule = schedule24mo()
        assertEquals(schedule.sumOf { it.principalMinor }, splits.sumOf { it.principalMinor })
        assertEquals(schedule.sumOf { it.interestMinor }, splits.sumOf { it.interestMinor })
        // Boundary: 2027-03-15 is FY 2026-27, 2027-04-15 is FY 2027-28.
        assertEquals(2026, financialYearStart(LocalDate.of(2027, 3, 15)))
        assertEquals(2027, financialYearStart(LocalDate.of(2027, 4, 1)))
    }

    @Test
    fun `estimate respects caps at 30 percent slab`() {
        // Rs 3L principal, Rs 4L interest in one FY: both caps bind.
        val split = FySplit(2026, 30_000_000L, 40_000_000L)
        val est = estimateTaxSaved(split, 30.0, TaxRegime.OLD, 100.0)
        assertEquals(15_000_000L, est.deduction80CMinor) // Rs 1.5L cap
        assertEquals(20_000_000L, est.deduction24bMinor) // Rs 2L cap
        assertEquals(10_500_000L, est.taxSavedMinor) // (1.5L + 2L) x 30% = Rs 1,05,000
    }

    @Test
    fun `new regime gives zero home loan saving`() {
        val split = FySplit(2026, 30_000_000L, 40_000_000L)
        val est = estimateTaxSaved(split, 30.0, TaxRegime.NEW, 100.0)
        assertEquals(0L, est.taxSavedMinor)
        assertEquals(0L, est.deduction80CMinor)
        assertEquals(0L, est.deduction24bMinor)
        assertTrue(est.note.isNotBlank())
    }

    @Test
    fun `joint share splits before caps`() {
        val split = FySplit(2026, 30_000_000L, 40_000_000L)
        // 50% each: Rs 1.5L principal (no cap hit), Rs 2L interest (exactly at cap).
        val half = estimateTaxSaved(split, 30.0, TaxRegime.OLD, 50.0)
        assertEquals(15_000_000L, half.deduction80CMinor)
        assertEquals(20_000_000L, half.deduction24bMinor)
        assertEquals(10_500_000L, half.taxSavedMinor)
        // 25%: nothing capped, straight quarter of the uncapped full-share saving.
        val quarter = estimateTaxSaved(split, 30.0, TaxRegime.OLD, 25.0)
        assertEquals(7_500_000L, quarter.deduction80CMinor)
        assertEquals(10_000_000L, quarter.deduction24bMinor)
        assertEquals(5_250_000L, quarter.taxSavedMinor)
    }

    @Test
    fun `income tax matches hand computed slabs with cess`() {
        // Rs 12.5L old: 2.5L@5% + 5L@20% + 2.5L@30% = 187500, x1.04 = 195000.
        assertEquals(19_500_000L, incomeTaxOld(125_000_000L))
        // Rs 10L new: 4L@5% + 2L@10% = 40000, x1.04 = 41600.
        assertEquals(4_160_000L, incomeTaxNew(100_000_000L))
        assertEquals(0L, incomeTaxOld(0L))
    }

    @Test
    fun `comparator picks old regime at 9L with maxed deductions`() {
        val cmp = compareRegimes(90_000_000L, 15_000_000L, 20_000_000L, 100.0)
        assertEquals(TaxRegime.OLD, cmp.better)
        assertEquals(1_040_000L, cmp.savingVsOtherMinor) // Rs 10,400
    }

    @Test
    fun `comparator picks new regime at 18L`() {
        val cmp = compareRegimes(180_000_000L, 30_000_000L, 40_000_000L, 100.0)
        assertEquals(TaxRegime.NEW, cmp.better)
        assertTrue(cmp.savingVsOtherMinor > 0)
    }

    @Test
    fun `ca summary contains the numbers`() {
        val est = estimateTaxSaved(FySplit(2026, 30_000_000L, 40_000_000L), 30.0, TaxRegime.OLD, 100.0)
        val summary = taxSummaryForCa(est, "HDFC Bank")
        assertTrue(summary.contains("FY 2026-27"))
        assertTrue(summary.contains("HDFC Bank"))
        assertTrue(summary.contains("1,50,000"))
        assertTrue(summary.contains("2,00,000"))
    }
}
