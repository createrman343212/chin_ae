import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.chinae"
    // 설치된 플랫폼이 android-36 뿐이라 36으로 컴파일, 동작 기준은 targetSdk 35
    compileSdk = 36
    useLibrary("android.car")

    defaultConfig {
        applicationId = "app.chinae"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        // 심사용 휴대폰 음성. 키는 local.properties(git 제외)에만 두고, 비어 있으면 기본 TTS를 씀
        val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"${local.getProperty("elevenlabs.apiKey", "")}\"")
        buildConfigField("String", "ELEVENLABS_VOICE_ID", "\"${local.getProperty("elevenlabs.voiceId", "")}\"")
    }
    // shortcut: 배포용 서명 키가 없어 release도 디버그 키로 서명함. GitHub Releases 정식 배포 전에 전용 키(저장소 밖 보관)로 교체
    buildTypes { release { signingConfig = signingConfigs.getByName("debug") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")

    // 저장소: Room + SQLCipher(masterplan 9장 암호화)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    testImplementation("junit:junit:4.13.2")
    // Room DAO와 보존 정리를 JVM에서 실제 SQLite로 확인
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
