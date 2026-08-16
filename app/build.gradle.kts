plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    jacoco
}

android {
    namespace = "com.bitter"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bitter"
        minSdk = 26
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 5
        versionName = (project.findProperty("versionName") as? String) ?: "0.1.4"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            // release is signed with the debug key so the APK is installable
            // without a production keystore. Replace for real distribution.
        }
    }

    buildTypes {
        debug {
            isTestCoverageEnabled = true
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.timber)

    debugImplementation(libs.hyperion.core)
    debugImplementation(libs.hyperion.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.compose.ui.tooling)
}

val jacocoTestReport = tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    val corePackages = listOf(
        "com/bitter/crypto/**",
        "com/bitter/model/**",
        "com/bitter/merkle/**",
        "com/bitter/ble/AdvertPacket*",
        "com/bitter/ble/NicknamePacket*",
        "com/bitter/ble/FrameCodec*",
        "com/bitter/ble/FrameStream*",
        "com/bitter/ble/CollisionResolver*",
        "com/bitter/ble/BleProtocol*",
        "com/bitter/sync/**",
        "com/bitter/store/DayKey*",
        "com/bitter/store/EventRepository*",
        "com/bitter/store/InMemoryEventStore*",
        "com/bitter/mesh/**",
        "com/bitter/log/**",
        "com/bitter/ui/TimelineModel*",
    )

    val classDirs = fileTree("$buildDir/tmp/kotlin-classes/debug") {
        include(corePackages)
    }

    sourceDirectories.setFrom(
        files(
            "src/main/java/com/bitter/crypto",
            "src/main/java/com/bitter/model",
            "src/main/java/com/bitter/merkle",
            "src/main/java/com/bitter/ble",
            "src/main/java/com/bitter/sync",
            "src/main/java/com/bitter/store",
            "src/main/java/com/bitter/mesh",
            "src/main/java/com/bitter/log",
            "src/main/java/com/bitter/ui",
        ),
    )
    classDirectories.setFrom(classDirs)
    executionData.setFrom(fileTree("$buildDir") {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

