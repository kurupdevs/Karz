// Pure Kotlin/JVM module: the loan math engine. Zero Android dependencies.
plugins {
    // Version-less: the plugin version is pinned once in the root build file
    // (alias(libs.plugins.kotlin.jvm) apply false). Requesting it with a
    // version here trips Gradle's "already on the classpath with an unknown
    // version" check because kotlin-gradle-plugin also backs kotlin.android.
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.junit4)
}

tasks.withType<Test> {
    useJUnit()
}
