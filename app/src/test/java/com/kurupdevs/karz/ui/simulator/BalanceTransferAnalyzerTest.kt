package com.kurupdevs.karz.ui.simulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Plain JVM tests for the balance transfer analyzer's pure math.
 * Run with: ./gradlew :app:testDebugUnitTest
 */
class BalanceTransferAnalyzerTest {

    private fun input45L(
        remaining: Int = 180,
        current: String = "8.5",
        offered: String = "7.5",
        feeRupees: Long = 10_000,
    ) = BtInput(
        outstandingMinor = 450_000_000L, // Rs 45L
        remainingMonths = remaining,
        currentRatePct = BigDecimal(current),
        offeredRatePct = BigDecimal(offered),
        processingFeeMinor = feeRupees * 100,
    )

    @Test
    fun `100bps gap with 15y left is worth exploring`() {
        val a = analyzeBalanceTransfer(input45L())
        assertEquals(BtVerdict.WORTH_EXPLORING, a.verdict)
        assertEquals(100, a.rateDiffBps)
        assertEquals(25, a.ruleThresholdBps) // 15y left -> 25bps bar
        assertTrue(a.netSavingMinor > 0)
        assertEquals(4, a.breakevenMonths) // Rs 10k fee / ~Rs 2598 monthly saving
        assertTrue(a.verdictNote.isNotBlank())
        assertTrue(a.ruleNote.contains("25 bps"))
    }

    @Test
    fun `tenure reset trap is priced and positive`() {
        val a = analyzeBalanceTransfer(input45L(), trapResetTenureMonths = 240)
        // Same 7.5% stretched back to 240 months costs ~Rs 7.24L MORE interest
        // than staying at 8.5% for the remaining 180 months.
        assertTrue(a.trapExtraCostMinor > 70_000_000L)
        assertEquals(240, a.trapResetTenureMonths)
    }

    @Test
    fun `tiny gap with little time left is rejected`() {
        // 10bps, 3 years left: needs 100bps, and the fee alone kills it.
        val a = analyzeBalanceTransfer(input45L(remaining = 36, offered = "8.4"))
        assertEquals(BtVerdict.PROBABLY_NOT_WORTH_IT, a.verdict)
        assertEquals(100, a.ruleThresholdBps)
        assertTrue(a.netSavingMinor < 0)
    }

    @Test
    fun `higher offered rate is rejected`() {
        val a = analyzeBalanceTransfer(input45L(offered = "9.0"))
        assertEquals(BtVerdict.PROBABLY_NOT_WORTH_IT, a.verdict)
        assertEquals(-50, a.rateDiffBps)
    }

    @Test
    fun `marginal band between half and full threshold`() {
        // 60bps with 3y left: below the 100bps bar, above half of it.
        val a = analyzeBalanceTransfer(input45L(remaining = 36, offered = "7.9", feeRupees = 2_000))
        assertEquals(BtVerdict.MARGINAL, a.verdict)
    }

    @Test
    fun `rule threshold scales with years left`() {
        assertEquals(100, analyzeBalanceTransfer(input45L(remaining = 36)).ruleThresholdBps)
        assertEquals(75, analyzeBalanceTransfer(input45L(remaining = 96)).ruleThresholdBps)
        assertEquals(50, analyzeBalanceTransfer(input45L(remaining = 144)).ruleThresholdBps)
        assertEquals(25, analyzeBalanceTransfer(input45L(remaining = 240)).ruleThresholdBps)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects tenure under 6 months`() {
        analyzeBalanceTransfer(input45L(remaining = 3))
    }

    @Test
    fun `email draft mentions conversion fee`() {
        val (subject, body) = rateResetEmailDraft("HDFC Bank", "•••• 4821", BigDecimal("8.5"))
        assertTrue(subject.isNotBlank())
        assertTrue(body.contains("1,500"))
        assertTrue(body.contains("3,000"))
        assertTrue(body.contains("HDFC Bank"))
        assertTrue(body.contains("8.50%"))
    }
}
