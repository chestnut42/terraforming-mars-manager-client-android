import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Build-time configuration. Values are looked up in local.properties, then
// Gradle properties (-P or ~/.gradle/gradle.properties), then env variables.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun config(name: String, env: String, default: String): String =
    localProps.getProperty(name)
        ?: (project.findProperty(name) as String?)
        ?: System.getenv(env)
        ?: default

android {
    namespace = "xyz.blockthem.mars.manager"
    compileSdk = 35

    defaultConfig {
        applicationId = "xyz.blockthem.mars.manager"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String", "API_BASE_URL",
            "\"${config("mars.apiBaseUrl", "MARS_API_BASE_URL", "https://mars.blockthem.xyz")}\"",
        )
        // OAuth 2.0 *Web application* client ID (not a secret). Google ID
        // tokens are issued with this value as the audience, so the server
        // must accept it (MARS_GOOGLE_CLIENT_IDS).
        buildConfigField(
            "String", "GOOGLE_SERVER_CLIENT_ID",
            "\"${config(
                "mars.googleServerClientId",
                "MARS_GOOGLE_SERVER_CLIENT_ID",
                "843528807889-3ism1tkq2v9v9655vq4npk0o8spg78fa.apps.googleusercontent.com",
            )}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
