plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.aiza.tools.replay.MainKt")
}

dependencies {
    implementation(project(":core:direction"))
    implementation(project(":core:logformat"))
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
