plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.chahyn.combat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chahyn.combat"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // L'appli web (cutting/index.html) est embarquée telle quelle dans l'APK.
    sourceSets["main"].assets.srcDirs("../cutting")

    signingConfigs {
        create("maison") {
            storeFile = file("../app/maison.keystore")
            storePassword = "maison123"
            keyAlias = "maison"
            keyPassword = "maison123"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("maison") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("maison")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}
