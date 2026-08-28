plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "com.example.dimanow.pipeline.DataPipelineMainKt"
}

dependencies {
    implementation(project(":sync-contract"))
    implementation(libs.jsoup)
    testImplementation(libs.junit)
}
