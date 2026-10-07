plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlinPluginSerialization)
}
kotlin {
    jvm()
    wasmJs { browser() }
    jvmToolchain(21)
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
