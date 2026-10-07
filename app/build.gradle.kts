plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}
dependencies {
    implementation(project(":model"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    testImplementation(kotlin("test"))
}
application { mainClass = "org.example.app.AppKt" }
tasks.named<JavaExec>("run") {
    dependsOn(":web:wasmJsBrowserDevelopmentExecutableDistribution")
    workingDir = rootProject.projectDir
}
