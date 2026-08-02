plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.enterprise.busvalidator"
    compileSdk = 35

    val gitCommitCount = runCatching {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.inputStream.bufferedReader().readText().trim().toInt()
    }.getOrDefault(142)

    val gitCommitHash = runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
        process.inputStream.bufferedReader().readText().trim()
    }.getOrDefault("a1b2c3d")

    val dynamicVersionName = "2.5.0-$gitCommitCount.$gitCommitHash"

    defaultConfig {
        applicationId = "com.enterprise.busvalidator"
        minSdk = 21
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = dynamicVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GIT_HASH", "\"$gitCommitHash\"")
        buildConfigField("Long", "BUILD_TIME", "${System.currentTimeMillis()}L")

        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    flavorDimensions += "deviceSdk"
    productFlavors {
        create("e60q") {
            dimension = "deviceSdk"
        }
        create("e60v2") {
            dimension = "deviceSdk"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:security"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:hardware-api"))
    implementation(project(":core:hardware-drivers"))
    implementation(project(":core:payment"))
    implementation(project(":core:sync"))
    implementation(project(":core:location"))
    implementation(project(":core:devicemanager"))
    implementation(project(":feature:validator"))
    implementation(project(":feature:diagnostic"))
    implementation(project(":feature:settings"))

    // E60Q and E60V2 expose duplicate classes/native libs. Keep them in mutually
    // exclusive runtime flavors so no APK packages both SDKs at once.
    "e60qImplementation"(files("../libs/vendor-sdk/e60/E60Q/E60QSDK-release.aar"))
    "e60qImplementation"(files("../libs/vendor-sdk/e60/E60Q/jtbqrcodesdk-release.aar"))
    "e60v2Implementation"(files("../libs/vendor-sdk/e60/E60V2/E60V2SDK-release.aar"))
    implementation(files("../libs/vendor-sdk/kmt/MultitripAndroid-release.aar"))
    implementation(files("../libs/vendor-sdk/netlibs/netlibs-release.aar"))
    implementation(files("../libs/vendor-sdk/netlibs/filelog-release.aar"))
    implementation(libs.gson)
    implementation(libs.commons.net)
    implementation(libs.commons.lang3)
    compileOnly(files("../libs/vendor-sdk/e60/E60Q/E60QSDK-release.aar"))
    compileOnly(files("../libs/vendor-sdk/e60/E60V2/E60V2SDK-release.aar"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // Room Database & SQLCipher Encryption
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)

    // Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
}
