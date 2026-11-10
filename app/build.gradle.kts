plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.glicemixii"
    // 37: exigido pela androidx.core 1.19. Só afeta a compilação; o app continua rodando do Android 8.0 em diante.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.glicemix.app"
        minSdk = 26          // datas (java.time) sem bibliotecas extras
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Chave fixa: cada APK novo instala por cima do anterior sem perder os dados.
    signingConfigs {
        create("glicemix") {
            storeFile = file("glicemix.keystore")
            storePassword = "glicemix"
            keyAlias = "glicemix"
            keyPassword = "glicemix"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("glicemix")
        }
        release {
            signingConfig = signingConfigs.getByName("glicemix")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Avisos do lint não impedem gerar o APK.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}