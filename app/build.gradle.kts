import com.android.build.api.dsl.AaptOptions

plugins {
    id("com.android.application")  // Apply the Android application plugin
}

android {
    namespace = "com.example.vidocapture"  // Define the namespace for the app
    compileSdk = 34  // Specify the SDK version to compile against

    defaultConfig {
        applicationId = "com.example.vidocapture"  // Unique application ID
        minSdk = 29  // Minimum SDK version supported
        targetSdk = 34  // Target SDK version
        versionCode = 1  // Version code for the app
        versionName = "1.0"  // Version name for the app
        renderscriptTargetApi = 21
        renderscriptSupportModeEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"  // Test runner for instrumentation tests
    }


    // Prevents the model from being compressed
    androidResources {
        noCompress += "tflite"
    }

    buildTypes {
        release {
            isMinifyEnabled = false  // Disable code shrinking for release build
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")  // ProGuard configuration files
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8  // Java source compatibility
        targetCompatibility = JavaVersion.VERSION_1_8  // Java target compatibility
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")  // AppCompat library for backward compatibility
    implementation("com.google.android.material:material:1.8.0")  // Material Design components
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")  // ConstraintLayout for flexible UI design

    // CameraX dependencies (using stable versions)
    implementation("androidx.camera:camera-core:1.1.0")  // Core CameraX library
    implementation("androidx.camera:camera-camera2:1.1.0")  // Camera2 implementation for CameraX
    implementation("androidx.camera:camera-lifecycle:1.1.0")  // Lifecycle-aware components for CameraX
    implementation("androidx.camera:camera-view:1.1.0")  // View class for CameraX
    implementation("androidx.camera:camera-video:1.1.0")  // Video recording support for CameraX
    implementation("androidx.camera:camera-extensions:1.1.0")
    implementation(project(":openCV"))  // Extensions for CameraX

    testImplementation("junit:junit:4.13.2")  // JUnit for unit testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")  // AndroidX JUnit extension for Android tests
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")  // Espresso for UI testing

    implementation ("androidx.camera:camera-core:1.0.0")
    implementation ("androidx.camera:camera-camera2:1.0.0")
    implementation ("androidx.camera:camera-lifecycle:1.0.0")
    implementation ("androidx.camera:camera-video:1.0.0")
    implementation ("com.google.android.material:material:1.4.0")

//    implementation("org.tensorflow:tensorflow-lite:2.9.0") // Check for the latest version
    implementation ("org.tensorflow:tensorflow-lite:2.10.0")

    implementation("org.tensorflow:tensorflow-lite-gpu:2.10.0") // For GPU support, if needed

    // TensorFlow Lite Support Library for image and model handling
    implementation("org.tensorflow:tensorflow-lite-support:0.3.1")


    // Other dependencies
    implementation("androidx.appcompat:appcompat:1.3.0")
    implementation("androidx.core:core-ktx:1.6.0")



}
