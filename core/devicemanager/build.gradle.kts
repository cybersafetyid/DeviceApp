plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.enterprise.busvalidator.core.devicemanager"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:hardware-api"))

    // Vendor SDK compileOnly dependencies
    compileOnly(files("../../libs/vendor-sdk/e60/E60Q/E60QSDK-release.aar"))
    compileOnly(files("../../libs/vendor-sdk/e60/E60Q/jtbqrcodesdk-release.aar"))
    compileOnly(files("../../libs/vendor-sdk/e60/E60V2/E60V2SDK-release.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
