// Top-level build file. Version catalog: gradle/libs.versions.toml
plugins {
    alias(libs.plugins.android.application) apply false
    // NOTE (AGP 9+): org.jetbrains.kotlin.android is built into AGP now and
    // must NOT be applied. kotlin.jvm is still declared for the :math module.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // google-services and crashlytics are applied in app/build.gradle.kts
    // only after google-services.json is added (see FIREBASE.md note there).
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}
