plugins {
    id("com.android.library") version("8.7.3")
    id("org.jetbrains.kotlin.android") version("2.1.10")
    id("maven-publish")
}

android {
    namespace = "com.whitekod.g2ray"
    compileSdk = 35

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                groupId = "io.jitpack"
                artifactId = "library"
                version = "0.1"

                afterEvaluate {
                    from(components["release"])
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.so"))))
    implementation(files("libs/libv2ray.aar"))
}