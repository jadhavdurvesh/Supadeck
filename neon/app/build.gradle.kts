plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kestrane.neondeck"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kestrane.neondeck"
        minSdk = 26
        targetSdk = 35
        // Set by CI from the pushed tag (e.g. neon-v1.0.0 -> versionName 1.0.0).
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "0.1.0-dev"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    packaging {
        resources {
            // pgjdbc ships a couple of license/notice files under META-INF that collide
            // with other libraries during packaging; keep the first one found.
            pickFirsts += listOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Pure-Java (Type 4) Postgres driver — same wire protocol Neon speaks, works unmodified on Android.
    implementation("org.postgresql:postgresql:42.7.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
