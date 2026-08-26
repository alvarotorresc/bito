plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.alvarotc.bito"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alvarotc.bito"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("BITO_KEYSTORE_PATH")
            if (path != null) {
                val storePasswordEnv = System.getenv("BITO_KEYSTORE_PASSWORD")
                val keyAliasEnv = System.getenv("BITO_KEY_ALIAS")
                val keyPasswordEnv = System.getenv("BITO_KEY_PASSWORD")

                if (storePasswordEnv == null) error("Release signing: BITO_KEYSTORE_PATH is set but BITO_KEYSTORE_PASSWORD is missing")
                if (keyAliasEnv == null) error("Release signing: BITO_KEYSTORE_PATH is set but BITO_KEY_ALIAS is missing")
                if (keyPasswordEnv == null) error("Release signing: BITO_KEYSTORE_PATH is set but BITO_KEY_PASSWORD is missing")

                storeFile = file(path)
                storePassword = storePasswordEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPasswordEnv
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("BITO_KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    packaging {
        // bcprov-jdk18on ships a multi-release manifest that collides with jspecify's; both are
        // metadata only, safe to drop one copy.
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.reorderable)
    implementation(libs.bouncycastle)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
