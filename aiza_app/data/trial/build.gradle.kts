plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aiza.data.trial"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(project(":core:logformat"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
}
