plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lazzy.gallery"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.gallery"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

tasks.register("buildRustCore") {
    notCompatibleWithConfigurationCache("cargo-ndk external build process")
    doLast {
        val rustDir = File(rootDir.parentFile, "rust_core")
        val outputDir = File(projectDir, "src/main/jniLibs")
        if (rustDir.exists()) {
            try {
                val pb = ProcessBuilder(
                    "cargo-ndk",
                    "-t", "arm64-v8a",
                    "-t", "armeabi-v7a",
                    "-t", "x86_64",
                    "-p", "24",
                    "-o", outputDir.absolutePath,
                    "build",
                    "--release"
                )
                pb.directory(rustDir)
                pb.redirectErrorStream(true)
                val process = pb.start()
                val exitCode = process.waitFor()
                println("cargo-ndk exited with code $exitCode")
            } catch (e: Exception) {
                println("Warning: cargo-ndk execution failed: ${e.message}")
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("buildRustCore")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.exifinterface)
    implementation("androidx.print:print:1.0.0")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.2")

    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
