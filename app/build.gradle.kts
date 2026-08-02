import java.util.Properties

plugins {
    // No kotlin-android plugin: AGP 9 compiles Kotlin out of the box.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

/**
 * An optional Tankerkoenig key compiled into the app.
 *
 * With it, a build starts ready to use instead of sending the user through
 * onboarding; without it, BuildConfig.API_KEY is empty and nothing changes. A
 * key entered in the app always wins over this one.
 *
 * Set it out of tree, never in the committed gradle.properties:
 *
 *     ~/.gradle/gradle.properties:  tankblick.apiKey=<uuid>
 *     environment:                  TANKBLICK_API_KEY=<uuid>
 *     one build:                    ./gradlew assembleRelease -Ptankblick.apiKey=<uuid>
 *
 * A key in an APK is readable by anyone who has the APK - `strings` is enough.
 * Only bake in a key for builds that stay on your own devices, or one that is
 * meant to be public and whose rate limit you are willing to share.
 *
 * Read through providers so the configuration cache invalidates when the value
 * changes. [TankblickApiKey] does the validating and accepts the reversed
 * base64 spelling the F-Droid recipe uses.
 */
val buildApiKey: String = TankblickApiKey.resolve(
    providers.gradleProperty("tankblick.apiKey")
        .orElse(providers.environmentVariable("TANKBLICK_API_KEY"))
        .getOrElse(""),
)

android {
    namespace = "de.mymiggi.tankblick"
    // androidx.core 1.19 and lifecycle 2.11 require compiling against API 37.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.mymiggi.tankblick"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_KEY", "\"$buildApiKey\"")
    }

    buildFeatures {
        compose = true
        // For VERSION_NAME, which goes into the User-Agent header, and API_KEY.
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }

    /**
     * Keeps AGP from embedding its dependency metadata blob in the APK.
     *
     * That blob is encrypted with a Google public key, which makes it opaque to
     * anyone verifying the build and prevents the APK from being reproducible.
     * F-Droid rebuilds every app from source and compares the result, so this
     * has to be off.
     */
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    /**
     * Signing is opt-in through an untracked keystore.properties.
     *
     * F-Droid signs with its own key after rebuilding from source, so this is
     * only for producing a locally signed APK. Without the file the release
     * build stays unsigned rather than failing, which is what CI and a fresh
     * clone need.
     */
    val keystoreProperties = rootProject.file("keystore.properties")
    if (keystoreProperties.exists()) {
        val properties = Properties().apply {
            keystoreProperties.inputStream().use { load(it) }
        }

        // Checked up front: a typo would otherwise surface as a bare
        // InvalidUserDataException on every Gradle invocation, not just on
        // assembleRelease, and with nothing pointing at the actual cause.
        val required = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        val missing = required.filter { properties.getProperty(it).isNullOrBlank() }
        require(missing.isEmpty()) {
            "keystore.properties is missing: ${missing.joinToString()}. " +
                "See RELEASING.md, or delete the file to build unsigned."
        }

        signingConfigs {
            create("release") {
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

// With built-in Kotlin, jvmTarget follows android.compileOptions.targetCompatibility.

ksp {
    // Schemas are checked in, so a change to the database shows up in review
    // and migrations can be tested against the previous version.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.datastore.preferences.core)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
