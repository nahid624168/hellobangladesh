plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val configuredApiBaseUrl = providers.gradleProperty("EXAMBASE_API_BASE_URL")
    .orElse(providers.environmentVariable("EXAMBASE_API_BASE_URL"))
    .orElse("")
    .get()
val debugApiBaseUrl = configuredApiBaseUrl.ifBlank { "http://10.0.2.2:8080" }
val releaseApiBaseUrl = configuredApiBaseUrl

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.exambase.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.exambase.ai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.5.0"
    }

    buildTypes {
        debug {
            // Override with -PEXAMBASE_API_BASE_URL=https://your-public-backend.example
            // The fallback is only useful from the Android emulator, not a physical phone.
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugApiBaseUrl))
        }
        release {
            // Set with -PEXAMBASE_API_BASE_URL=https://your-api.example.com or env var.
            buildConfigField("String", "API_BASE_URL", buildConfigString(releaseApiBaseUrl))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room writes its schema JSON here; commit the files so future migrations can be tested.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime)
    implementation(libs.security.crypto)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

// Prevent publishing an APK that points to an empty or non-HTTPS API endpoint.
val validateReleaseApiBaseUrl by tasks.registering {
    group = "verification"
    description = "Validates the production API URL before release packaging."
    doLast {
        if (!releaseApiBaseUrl.startsWith("https://")) {
            throw GradleException(
                "Set EXAMBASE_API_BASE_URL to your real HTTPS backend URL before building a release. " +
                    "Example: -PEXAMBASE_API_BASE_URL=https://api.example.com"
            )
        }
    }
}

tasks.configureEach {
    if (name.contains("Release", ignoreCase = true) &&
        (name.startsWith("assemble") || name.startsWith("bundle"))) {
        dependsOn(validateReleaseApiBaseUrl)
    }
}
