import com.carmanager.build.AdMobConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Source de production explicite, locale et non versionnee ; aucune lecture de secrets globaux.
val adMobProperties = Properties().apply {
    val configFile = rootProject.file("admob.properties")
    if (configFile.isFile) configFile.inputStream().use { load(it) }
}
val productionAdMobAppId = adMobProperties.getProperty("ADMOB_APP_ID")
val productionBannerId = adMobProperties.getProperty("ADMOB_BANNER_AD_UNIT_ID")
val productionAdMobErrors = AdMobConfiguration.releaseErrors(productionAdMobAppId, productionBannerId)

val validateReleaseAdsConfiguration = tasks.register("validateReleaseAdsConfiguration") {
    group = "verification"
    description = "Refuse un release sans configuration AdMob de production explicite et valide."
    doLast {
        if (productionAdMobErrors.isNotEmpty()) throw GradleException(
            "MANUAL REQUIRED STEP - AdMob production configuration: " + productionAdMobErrors.joinToString("; ") +
                ". Renseigner admob.properties a la racine du projet. Aucune valeur n'est affichee."
        )
    }
}
// La garde precede chaque entree de variante release, y compris manifests et BuildConfig.
tasks.configureEach {
    if (name.contains("Release") && name != "validateReleaseAdsConfiguration") {
        dependsOn(validateReleaseAdsConfiguration)
    }
}

android {
    namespace = "com.carmanager.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.carmanager.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Configuration pour les migrations automatiques de Room
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildTypes {
        debug {
            manifestPlaceholders["ADMOB_APP_ID"] = AdMobConfiguration.DEBUG_APP_ID
            buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"${AdMobConfiguration.DEBUG_BANNER_ID}\"")
        }
        release {
            // Sans valeurs valides, aucune valeur vide/factice/demo n'est injectee dans un manifeste.
            if (productionAdMobErrors.isEmpty()) {
                manifestPlaceholders["ADMOB_APP_ID"] = productionAdMobAppId!!
                buildConfigField("String", "ADMOB_BANNER_AD_UNIT_ID", "\"${productionBannerId!!}\"")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    // Les tests exercent exactement le validateur utilise par Gradle ; source de build uniquement.
    sourceSets.getByName("test").java.srcDir(rootProject.file("buildSrc/src/main/java"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.google.mlkit.text.recognition)
    implementation(libs.google.ads)
    implementation(libs.google.ump)
    implementation(libs.billing.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
