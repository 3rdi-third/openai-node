plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

import java.security.MessageDigest

android {
    namespace = "com.threerdi.sessiongrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.threerdi.sessiongrid"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.0"
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            // Installable release candidate without publishing a private production key.
            // Replace this with a private release signing config before store distribution.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// The startup MP4 is stored as small binary chunks. Reassemble and verify it
// before Android resource processing so the APK contains the exact media file.
val prepareStartupVideo by tasks.registering {
    val partsDir = layout.projectDirectory.dir("startup-video-chunks")
    val outputFile = layout.projectDirectory.file("src/main/res/raw/startup_intro.mp4")
    val expectedSize = 20_909L
    val expectedSha256 = "90395e60d5d57a227e56c69781aea5143d9b0193e28bca3a2e75904531b7107f"

    inputs.files(fileTree(partsDir) { include("chunk*.bin") })
    outputs.file(outputFile)

    doLast {
        val output = outputFile.asFile
        output.parentFile.mkdirs()
        val parts = fileTree(partsDir)
            .matching { include("chunk*.bin") }
            .files
            .sortedBy { it.name }

        require(parts.size == 7) { "Expected 7 startup-video chunks, found ${parts.size}" }

        output.outputStream().use { destination ->
            parts.forEach { part ->
                part.inputStream().use { source -> source.copyTo(destination) }
            }
        }

        require(output.length() == expectedSize) {
            "Startup video size mismatch: ${output.length()} != $expectedSize"
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(output.readBytes())
            .joinToString("") { "%02x".format(it) }
        require(digest == expectedSha256) {
            "Startup video SHA-256 mismatch: $digest"
        }
    }
}

tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("Resources"))) {
        dependsOn(prepareStartupVideo)
    }
}
