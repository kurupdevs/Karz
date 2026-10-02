# Screens integration notes

All screens live in `com.kurupdevs.karz.ui.screens.*` and reuse the shared
library (`ui.components`, `ui.motion`, `ui.theme`, `data.model`, `nav`).
No files outside `ui/screens/` were touched.

## Screens and signatures

| Screen | Composable | Notes |
|---|---|---|
| S0 Onboarding | `onboarding.OnboardingScreen(auth: AuthGateway, onComplete: () -> Unit)` | phone → OTP (T9 keypad) → name → currency (INR default) → done |
| S1 Add loan | `addloan.AddLoanScreen(loans: LoanRepository, currency: String, onConfirmed: (String) -> Unit, onBack: () -> Unit = {}, onDeleted: () -> Unit = {}, existingLoan: Loan? = null)` | form → searching (≥900ms) → found card, all in one destination via Crossfade |
| S2 Home | `home.HomeScreen(loans: LoanRepository, currency: String, onEditLoan: (Loan) -> Unit, onLoanClick: (Loan) -> Unit, onManage: () -> Unit, onAddLoan: () -> Unit)` | no bottom nav (TabScaffold owns it) |
| S3 Manage | `manage.ManageScreen(loans: LoanRepository, documents: DocumentRepository, currency: String, onDocuments: () -> Unit, onSimulate: () -> Unit, onBack: () -> Unit = {})` | payment sheet + EMI-day sheet inside |
| S4 Detail | `loandetail.LoanDetailScreen(loanId: String, loans: LoanRepository, currency: String, onBack: () -> Unit, onEdit: (Loan) -> Unit, onDeleted: () -> Unit)` | takes loanId (matches `RouteLoanDetail(loanId)`) |
| S6 Documents | `documents.DocumentsScreen(documents: DocumentRepository, onBack: () -> Unit = {})` | |
| S6 viewer | `documents.DocViewerActivity` | FLAG_SECURE on this activity only |
| S7 Offers | `offers.OffersScreen()` | teaser |
| S8 Settings | `settings.SettingsScreen(profile: ProfileRepository, onBack: () -> Unit = {}, onSignedOut: () -> Unit = {})` | |

NavGraph wiring:
- `RouteOnboarding` → `OnboardingScreen(auth, onComplete = { navController.navigate(RouteAddLoan) })`
- `RouteAddLoan` → `AddLoanScreen(loans, currency, onConfirmed = { id -> navController.navigate(RouteMain) { popUpTo(RouteAddLoan) { inclusive = true } } }, onBack = { navController.popBackStack() })`
- `RouteMain` → `HomeScreen(..., onLoanClick = { navController.navigate(RouteLoanDetail(it.id)) }, onEditLoan = { /* store loan, navigate RouteAddLoan */ }, onManage = { navController.navigate(RouteManage) }, onAddLoan = { navController.navigate(RouteAddLoan) })`
- `RouteManage` → `ManageScreen(..., onDocuments = { navController.navigate(RouteDocuments) }, onSimulate = { navController.navigate(RouteSimulate) })`
- `RouteLoanDetail(loanId)` → `LoanDetailScreen(loanId, ..., onEdit = { /* RouteAddLoan with loan */ }, onDeleted = { navController.popBackStack() })`
- `RouteDocuments` → `DocumentsScreen(...)`, `RouteOffers` → `OffersScreen()`, `RouteSettings` → `SettingsScreen(...)`
- Shared-element morph works automatically: screens read `LocalSharedTransitionScope` /
  `LocalNavVisibilityScope` and apply `sharedBounds("mortgage-card-$id")` themselves.
- Edit flow needs a loan passed to `RouteAddLoan`: add an optional arg or a shared
  ViewModel/BackStackEntry holder on the nav side; `AddLoanScreen(existingLoan = ...)`
  already supports edit mode.

## Manifest

```xml
<activity
    android:name=".ui.screens.documents.DocViewerActivity"
    android:exported="false"
    android:theme="@style/Theme.Karz" />
```
(FLAG_SECURE is set programmatically; no manifest flag needed.)

## Deps the screens assume

- navigation-compose, material3, lifecycle-runtime-compose
  (`collectAsStateWithLifecycle`), activity-compose (`ComponentActivity.setContent`,
  `rememberLauncherForActivityResult`), coil3 (`coil3.compose.AsyncImage`),
- minSdk 26: `java.time`, `PdfRenderer` are fine.

## Temporary code to replace

- `ui/screens/addloan/EmiMath.kt` - REMOVED. The screens now call `emiFor`
  from the `:math` module directly (BigDecimal rate; convert at the call site).
- `Loan.ltv` extension - MOVED to `data.model` (Models.kt); `HomeScreen`
  imports it from there.

## Trust rules honored

- Found-card copy: "We get this information from your entries…" (never TransUnion).
- No phone-number gating beyond onboarding; "No spam calls. No selling your number. Ever."
- Offers: disabled teaser + "We will never sell your details to lenders." No lead-gen.
- No AI/agent credit anywhere; about card reads "Made by kurupdevs."
- No em-dashes in any string.
