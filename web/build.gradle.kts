plugins { kotlin("multiplatform") }
kotlin {
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "designer.js" } }
        binaries.executable()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":model"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
        }
    }
}
