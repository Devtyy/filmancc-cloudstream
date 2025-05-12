plugins {
    id 'com.android.library'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'xyz.filman.filmancc'
    compileSdk 33

    defaultConfig {
        minSdk 21
        targetSdk 33
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    implementation 'org.jsoup:jsoup:1.15.3'
    implementation(name: 'cloudstream', ext: 'aar')
}
