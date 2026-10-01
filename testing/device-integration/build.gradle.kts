plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.sentinel.di"
    compileSdk = 37
    buildToolsVersion = libs.versions.build.tools.get()
    targetProjectPath = ":app"
    defaultConfig {
        minSdk = 30
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.core)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.junit)
    implementation(libs.uiautomator)
}
