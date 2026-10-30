plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.liquidglass.tvlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.liquidglass.tvlauncher"
        minSdk = 23
        targetSdk = 34
        // CI sets these (build number, release tag); local builds keep the defaults.
        versionCode = (System.getenv("VERSION_CODE") ?: "2").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.1.0"
        // Last.fm's signed calls (scrobbling) need both an API key and a secret, unlike Spotify's PKCE sign-in.
        buildConfigField("String", "LASTFM_API_KEY", "\"${System.getenv("LASTFM_API_KEY").orEmpty().trim()}\"")
        buildConfigField("String", "LASTFM_API_SECRET", "\"${System.getenv("LASTFM_API_SECRET").orEmpty().trim()}\"")
    }

    // The release key lives outside the repo: CI decrypts it and points KEYSTORE_PATH at it.
    // Every build signed with the same key is what lets one install update the previous one.
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

    // Two editions of the same app (same package and key, so one replaces the other and either updates
    // itself from its own file in the release): the normal one, and "lite" for old TVs with about
    // 0.5 GB of RAM, which decodes smaller pictures, starts in the economy mode and keeps the windows plain.
    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "LITE", "false")
        }
        create("lite") {
            dimension = "edition"
            buildConfigField("boolean", "LITE", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the CI key it falls back to the debug key, which installs fine but
            // can't be updated in place (every machine has its own debug key).
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
        // The lite edition is built with this one: the same as release, but R8 removes the code and resources that
        // are not used, which makes the APK a fraction of the size. The normal edition stays unshrunk.
        create("shrunk") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Dominant-colour backdrops for apps that only ship a square icon.
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Talks to the box's own ADB service (see SelfAdb): silent updates, power, memory, permissions.
    implementation("dev.mobile:dadb:2.0.0")
    // The QR code of the phone remote's address (pure Java, no camera or Google services).
    implementation("com.google.zxing:core:3.5.3")
    // The Spotify Web API (the phone app signs in and hands the session to the launcher's player).
    implementation(project(":spotify"))
}
