plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.sentinel.regression"
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
    // 规则回归（RR-*）与跨模块契约测试（MT-CT-*，contract/ 目录）需要全部功能模块
    testImplementation(project(":core-rules"))
    testImplementation(project(":data"))
    testImplementation(project(":guard"))
    testImplementation(project(":engine-vpn"))
    testImplementation(project(":engine-a11y"))
    testImplementation(project(":engine-notify"))
    testImplementation(project(":engine-system"))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.serialization.json)
    testImplementation(libs.room.runtime)
    testImplementation(libs.koin.android)
    testImplementation(libs.koin.test)
    testImplementation(libs.work.testing)
}
