plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun resolveSigningParam(name: String): String? =
    (project.findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.musely.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.musely.app"
        minSdk = 26
        targetSdk = 36
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 4
        versionName = (project.findProperty("versionName") as String?) ?: "2.0.0"
    }

    androidResources {
        localeFilters += "en"
    }

    val releaseKeystorePath = resolveSigningParam("MUSELY_KEYSTORE_PATH")
    val releaseKeystorePassword = resolveSigningParam("MUSELY_KEYSTORE_PASSWORD")
    val releaseKeyAlias = resolveSigningParam("MUSELY_KEY_ALIAS")
    val releaseKeyPassword = resolveSigningParam("MUSELY_KEY_PASSWORD")
    val hasReleaseSigning = listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { it != null } && file(releaseKeystorePath!!).isFile

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Use persistent release keystore when configured; fall back to debug for PRs/forks.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
