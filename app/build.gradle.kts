plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sentinel.app"
    compileSdk = 37
    buildToolsVersion = libs.versions.build.tools.get()
    defaultConfig {
        applicationId = "com.sentinel.adblock"
        versionCode = 1
        versionName = "0.1.0"
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // UT-AP-1-04 只在 G8（全部模块入口到齐后）执行；M8 结束时删掉这条排除
        unitTests.all { it.useJUnit { excludeCategories("com.sentinel.app.di.G8Only") } }
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":engine-vpn"))
    implementation(project(":engine-a11y"))
    implementation(project(":engine-notify"))
    implementation(project(":engine-system"))
    implementation(project(":guard"))
    implementation(project(":data"))
    implementation(project(":core-rules"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.androidx.core)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.runtime)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.koin.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
