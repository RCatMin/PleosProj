import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pleosproj"
    compileSdk {
        version = release(37)
    }

    // API 키는 local.properties에만 둔다. 없으면(CI 등) 빈 문자열로 빌드된다
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun localKey(name: String) = "\"" + localProps.getProperty(name, "") + "\""

    defaultConfig {
        applicationId = "com.example.pleosproj"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "KMA_SERVICE_KEY", localKey("KMA_FORECAST_KEY"))
        buildConfigField("String", "ITS_API_KEY", localKey("ITS_API_KEY"))
        buildConfigField("String", "KAKAO_REST_KEY", localKey("KAKAO_REST_KEY"))

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    // android.car (CarPropertyManager) is provided by the Pleos / AAOS system image
    useLibrary("android.car")

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.maplibre)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.text.to.speech)
    implementation(libs.pleos.speech.to.text)
    implementation(libs.pleos.fused.location)
    testImplementation(libs.junit)
    // 단위 테스트(JVM)에서는 안드로이드의 org.json이 비어 있어 진짜 구현을 넣는다
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}