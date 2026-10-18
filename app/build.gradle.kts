plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

android {
    namespace = "com.retrobit.office"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.retrobit.office"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
    // Huawei MatePad Mini optimization: support stylus, large screen, multi-window
    // Tablet optimizations
    defaultConfig {
        // Support freeform / split-screen
        // Huawei HarmonyOS compatibility
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")

    // Huawei Mobile Services (HMS) - MatePad Mini stylus & pen
    implementation("com.huawei.hms:pen:6.0.0.300")
    implementation("com.huawei.hms:base:6.11.0.300")
    // Huawei ML Kit - Handwriting recognition alternative
    implementation("com.huawei.hms:ml-computer-vision-handwriting:3.11.0.302")

    // Google ML Kit - Digital Ink Recognition (primary handwriting engine)
    implementation("com.google.mlkit:digital-ink-recognition:18.1.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")

    // Apache POI - Office file generation (docx, xlsx, pptx)
    implementation("org.apache.poi:poi:5.2.5")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // Android compatible XMLBeans
    implementation("com.github.oleg-cherednik:poi-ooxml-android:5.2.2")

    // PDF & Print
    implementation("com.itextpdf:kernel:8.0.2")
    implementation("androidx.print:print:1.0.1")

    // Graphics - Free drawing, pressure-sensitive
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0") // For Excel charts

    // Color picker for toolbar
    implementation("com.github.skydoves:colorpickerview:2.2.4")

    // File picker & Storage
    implementation("com.github.dhaval2404:imagepicker:2.1")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Icons
    implementation("com.mikepenz:iconics-core:5.4.0")
    implementation("com.mikepenz:iconics-views:5.4.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

androidComponents {
    beforeVariants(selector().all()) {
        // Enable build config
    }
}
