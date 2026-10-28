import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room3)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "com.reater.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.reater.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.0.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Play 一次性商品 ID（需與 Play Console 建立的商品 ID 一致）
        // 改 ID 只改這裡，不用進程式碼找
        buildConfigField("String", "PRO_PRODUCT_ID", "\"pro_unlock\"")
    }

    val signingProperties = Properties()
    val signingFile = rootProject.file("../keystores/signing.properties")
    if (signingFile.exists()) signingFile.inputStream().use { signingProperties.load(it) }
    fun signingValue(name: String, vararg env: String): String? =
        env.firstNotNullOfOrNull { System.getenv(it)?.takeIf { value -> value.isNotBlank() } }
            ?: signingProperties.getProperty(name)
    val releaseStore = signingValue("storeFile", "REATER_KEYSTORE_FILE", "KEYSTORE_FILE", "ANDROID_KEYSTORE_PATH")
    val releasePassword = signingValue("storePassword", "REATER_STORE_PASS", "KEYSTORE_PASSWORD", "ANDROID_KEYSTORE_PASSWORD")
    val releaseAlias = signingValue("keyAlias", "REATER_KEY_ALIAS", "KEY_ALIAS", "ANDROID_KEY_ALIAS")
    val releaseKeyPassword = signingValue("keyPassword", "REATER_KEY_PASS", "KEY_PASSWORD", "ANDROID_KEY_PASSWORD")
    if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) } &&
        listOf(releaseStore, releasePassword, releaseAlias, releaseKeyPassword).any { it.isNullOrBlank() }) {
        error("Release signing requires environment variables or central keystores/signing.properties")
    }
    signingConfigs {
        create("release") {
            releaseStore?.let { storeFile = file(it) }
            storePassword = releasePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Room
    implementation(libs.androidx.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.androidx.room3.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network & Serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Coil
    implementation(libs.coil.compose)
    implementation("io.coil-kt:coil-gif:2.7.0")

    // Glance Widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore & Tink
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.tink.android)
    implementation(libs.androidx.billing.ktx)
    implementation("com.google.android.gms:play-services-ads:25.4.0")
}
