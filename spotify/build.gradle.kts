plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

// The Spotify Web API client shared by the launcher (the player on the TV) and the phone app (login and remote).
android {
    namespace = "com.liquidglass.spotify"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
