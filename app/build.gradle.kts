import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val releaseSecrets = Properties().apply {
    val secretsFile = rootProject.file("signing.properties")
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
android {
    namespace = "app.motioncoach"
    compileSdk = 35
    useLibrary("android.car", false)
    defaultConfig {
        applicationId = "app.motioncoach"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "0.2.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseSecrets.containsKey("storeFile")) {
            create("submission") {
                storeFile = rootProject.file(releaseSecrets.getProperty("storeFile"))
                storePassword = releaseSecrets.getProperty("storePassword")
                keyAlias = releaseSecrets.getProperty("keyAlias")
                keyPassword = releaseSecrets.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (releaseSecrets.containsKey("storeFile")) signingConfig = signingConfigs.getByName("submission")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    packaging { resources.excludes += setOf("/META-INF/AL2.0", "/META-INF/LGPL2.1") }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
