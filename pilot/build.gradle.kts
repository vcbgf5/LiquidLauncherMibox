plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.liquidglass.tvpilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.liquidglass.tvpilot"
        minSdk = 26
        targetSdk = 34
        // CI sets these (build number, release tag); local builds keep the defaults.
        versionCode = (System.getenv("VERSION_CODE") ?: "2").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.1.0"
        // The public Client ID of the Spotify app (CI passes it from a secret). It is not a secret, only an id;
        // sign-in uses PKCE, so the app never needs (and must never contain) the Client Secret.
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${System.getenv("SPOTIFY_CLIENT_ID").orEmpty().trim()}\"")
        // Last.fm has no PKCE-style flow: signing in (even with just a username and password) needs the
        // app's own API key and secret, so both are baked in here too.
        buildConfigField("String", "LASTFM_API_KEY", "\"${System.getenv("LASTFM_API_KEY").orEmpty().trim()}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${System.getenv("LASTFM_API_SECRET").orEmpty().trim()}\"")
    }

    // The same key as the launcher: one release, two apps signed alike.
    val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "liquidtv"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // The Spotify Web API (shared with the launcher).
    implementation(project(":spotify"))

    // Scans the QR code shown on the TV (Google's scanner: no camera permission needed).
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
