import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 从 local.properties 读取 Supabase 配置，避免将密钥硬编码进源码或提交到仓库
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localValue(key: String): String = (localProperties.getProperty(key) ?: "").trim()

android {
    namespace = "com.example.ledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.ledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // 直连 Supabase 会话池所需参数（不使用代理，走 pooler 的 5432 端口）
        buildConfigField("String", "SUPABASE_DB_HOST", "\"${localValue("SUPABASE_DB_HOST")}\"")
        buildConfigField("String", "SUPABASE_DB_PORT", "\"${localValue("SUPABASE_DB_PORT")}\"")
        buildConfigField("String", "SUPABASE_DB_NAME", "\"${localValue("SUPABASE_DB_NAME")}\"")
        buildConfigField("String", "SUPABASE_DB_USER", "\"${localValue("SUPABASE_DB_USER")}\"")
        buildConfigField("String", "SUPABASE_DB_PASSWORD", "\"${localValue("SUPABASE_DB_PASSWORD")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose 基础
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")

    // 生命周期与 ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // 导航
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // PostgreSQL JDBC：直连 Supabase pooler（*.supabase.co 的 HTTP 接口被网络封锁）
    // Android 上没有 java.lang.management，因此使用 app/libs 下打过补丁的 42.7.4
    implementation(files("libs/postgresql-42.7.4.jar"))

    // 序列化与协程
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
