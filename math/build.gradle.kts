// Pure Kotlin/JVM module: the loan math engine. Zero Android dependencies.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
