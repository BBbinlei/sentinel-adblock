plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.sentinel.guard"
    compileSdk = 37
    buildToolsVersion = libs.versions.build.tools.get()
    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":data"))
    implementation(project(":core-rules"))
    implementation(libs.koin.android)
    implementation(libs.androidx.core)
    implementation(libs.coroutines.android)
    implementation(libs.work.runtime)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.koin.test)
    testImplementation(libs.work.testing)
    testImplementation(libs.room.runtime)
}
