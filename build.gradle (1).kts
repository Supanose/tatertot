plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.tatertots"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.tatertots"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    // A fixed signing key kept in the project, so every build (laptop or GitHub) can
    // install over the previous one without uninstalling and losing your logs.
    // Fine for a family app; make a private key before putting anything on the Play Store.
    signingConfigs {
        create("family") {
            storeFile = file("tatertots-debug.keystore")
            storePassword = "tatertots"
            keyAlias = "tatertots"
            keyPassword = "tatertots"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("family")
        }
        release {
            signingConfig = signingConfigs.getByName("family")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
