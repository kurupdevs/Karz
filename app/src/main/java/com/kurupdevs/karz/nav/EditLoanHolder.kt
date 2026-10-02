package com.kurupdevs.karz.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kurupdevs.karz.data.model.Loan

/**
 * Holds the loan being edited while navigating Main/Detail -> AddLoan.
 * Set before navigating to RouteAddLoan; the AddLoan destination consumes
 * it as existingLoan and clears it after save or delete.
 */
class EditLoanHolder {
    var loan: Loan? by mutableStateOf(null)
}
