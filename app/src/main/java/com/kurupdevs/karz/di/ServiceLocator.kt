package com.kurupdevs.karz.di

import android.app.Application
import com.kurupdevs.karz.data.auth.FirebaseAuthService
import com.kurupdevs.karz.data.documents.FirestoreDocumentRepository
import com.kurupdevs.karz.data.loans.FirestoreLoanRepository
import com.kurupdevs.karz.data.profile.FirestoreProfileRepository
import com.kurupdevs.karz.data.scenarios.FirestoreScenarioRepository

/**
 * Manual service locator. No DI framework in v1 on purpose: five
 * repositories, one wiring file, zero magic. Concrete types are exposed
 * (each implements the screens-owned interface from
 * ui.screens.data.Contracts) so screens get the interface they program
 * against, plus extras like syncState and queuedUploads where needed.
 *
 * Initialized once from KarzApp.onCreate.
 */
object ServiceLocator {

    @Volatile
    var isInitialized: Boolean = false
        private set

    private lateinit var app: Application

    fun init(application: Application) {
        if (isInitialized) return
        app = application
        isInitialized = true
    }

    private fun app(): Application {
        check(isInitialized) { "ServiceLocator.init() was not called." }
        return app
    }

    val auth: FirebaseAuthService by lazy { FirebaseAuthService(app()) }

    val loans: FirestoreLoanRepository by lazy {
        FirestoreLoanRepository(auth, app())
    }

    val documents: FirestoreDocumentRepository by lazy {
        FirestoreDocumentRepository(auth, app())
    }

    val scenarios: FirestoreScenarioRepository by lazy {
        FirestoreScenarioRepository(auth, app())
    }

    val profile: FirestoreProfileRepository by lazy {
        FirestoreProfileRepository(auth, app())
    }
}
