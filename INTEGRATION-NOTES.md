# Karz: Math + Simulator integration notes

Math + simulator integration notes. The Gradle scaffold, the screens,
and the Firebase data layer each live in their own area of the tree.

## What exists

```
Karz/
  math/                                   pure Kotlin/JVM Gradle module (no Android)
    src/main/kotlin/com/kurupdevs/karz/math/Amortization.kt
    src/test/kotlin/com/kurupdevs/karz/math/AmortizationTest.kt
  app/src/main/java/com/kurupdevs/karz/ui/simulator/
    SimulatorModels.kt        SimulatorLoan, ScheduleApplier, ScenarioStore (+in-memory impl)
    SimulatorComponents.kt    local SPEC tokens, WorkingExpandable, StrategyToggle, HeroNumber
    SimulatorScreen.kt        the prepayment simulator screen
    SimulatorViewModel.kt
    ShareCardRenderer.kt      1080x1350 Canvas share card + Sharesheet (MediaStore, no FileProvider needed)
    TaxModule.kt              80C/24(b) pure math + UI section (INR only, hidden in GBP mode)
    TaxViewModel.kt
    BalanceTransferAnalyzer.kt  true-cost analyzer pure math + UI section
    BalanceTransferViewModel.kt
  app/src/test/java/com/kurupdevs/karz/ui/simulator/
    TaxModuleTest.kt, BalanceTransferAnalyzerTest.kt
```

## Gradle wiring

- `settings.gradle`: `include(":math")`.
- `math/build.gradle.kts`: `kotlin("jvm")` plugin, `testImplementation("junit:junit:4.13.2")`.
  (Same junit for `:app` unit tests.)
- `app/build.gradle`: `implementation(project(":math"))`, plus
  `lifecycle-viewmodel`, `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`
  (viewModelScope), Compose BOM + material3 + foundation + animation.
- Run math tests: `./gradlew :math:test`. App unit tests: `./gradlew :app:testDebugUnitTest`.

## Integration points

### For the data layer

1. **ScheduleApplier** (`SimulatorModels.kt`):
   `interface ScheduleApplier { suspend fun apply(input: ApplyScenarioInput) }`.
   `ApplyScenarioInput(loanId, strategy, lumpMinor, extraPerMonthMinor, newEmiMinor,
   newSchedule: List<Installment>)`. Implement the batched write: rewrite
   `loans/{id}/schedule/*`, update loan fields (balance, emi, nextDueDate),
   append `auditLog`. Pass your impl into `SimulatorScreen(loan, scheduleApplier = ...)`.
   Until then the Apply button is hidden automatically (`canApply`).

2. **ScenarioStore** (`SimulatorModels.kt`):
   `save / list(loanId) / delete`. Maps 1:1 to `users/{uid}/scenarios/{id}`.
   Until wired, `InMemoryScenarioStore` keeps save/load working for the session.

3. **SimulatorLoan** is the handoff type. Map your Firestore loan to:
   `SimulatorLoan(loanId, lenderName, currencyCode, annualRatePct, schedule, paidInstallments)`
   where `schedule` is the FULL amortization schedule and `paidInstallments`
   is the count of EMIs already paid. The simulator works on the remaining tail.

### For screens/nav

- `SimulatorScreen(loan: SimulatorLoan, scheduleApplier?, scenarioStore?, onBack?)`
  is the Simulate tab content. It is scrollable and self-contained.
- `TaxModuleSection(loan: SimulatorLoan)` and `BalanceTransferSection(loan: SimulatorLoan?)`
  are standalone cards: place them below the simulator or on their own tab.
  `TaxModuleSection` returns early (renders nothing) when `loan.isGbp`.
- Local design tokens live in `SimulatorComponents.kt` (`SimColors`, `SimMotion`);
  consolidate with the app theme when convenient. No dependency on the app's
  component names was taken, so nothing breaks either way.

## Test vectors (all verified against an independent Python/BigDecimal model)

- Rs 50,00,000 @ 8.5% / 240mo -> EMI 4339116 paise = Rs 43,391.16 (SPEC vector).
- Zero-rate: Rs 12,00,000 / 12mo -> Rs 1,00,000 exactly, zero interest, ends at 0.
- Last installment absorbs residue; balance ends exactly 0; principal sums to P.
- `tenureFromEmi` returns the true minimum months (naive log formula is corrected
  by exact simulation; the 240mo quote pays off in 241 at the paise-rounded EMI).
- Rs 5L lump at start: tenure-cut saves ~Rs 17.59L vs EMI-cut ~Rs 5.41L interest.
- LTV edges: 60 -> Strong equity, 60.01/75 -> Healthy, 75.01/90 -> High leverage,
  90.01 -> Thin equity.
- Tax: 80C cap Rs 1.5L, 24(b) cap Rs 2L, FY 2026-27 slabs + 4% cess verified by hand
  (Rs 12.5L old-regime -> Rs 1,95,000 tax).
- BT: 100bps @ 15y left -> WORTH_EXPLORING, breakeven 4 months; tenure-reset trap
  on the same offer costs ~Rs 7.24L MORE interest.

## Conventions to preserve

- Money is ALWAYS minor units (Long) until `formatMoney`/`formatMoneyCompact`.
- BigDecimal MC 32 HALF_UP; rounding only at EMI quote and per-installment interest.
- No em-dashes anywhere in code or user-facing strings. No AI/agent credit.
- Every simulator number has a "Show the working" expander; keep it that way.
