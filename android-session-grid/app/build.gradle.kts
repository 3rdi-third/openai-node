plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.threerdi.sessiongrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.threerdi.sessiongrid"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// The connector stores the MP4 as two binary blobs. Reassemble them before
// Android resource processing so the final APK contains a normal raw MP4.
val prepareStartupVideo by tasks.registering {
    val partsDir = layout.projectDirectory.dir("startup-video-parts")
    val outputFile = layout.projectDirectory.file("src/main/res/raw/startup_intro.mp4")

    inputs.files(fileTree(partsDir) { include("part*.bin") })
    outputs.file(outputFile)

    doLast {
        val output = outputFile.asFile
        output.parentFile.mkdirs()
        val parts = fileTree(partsDir)
            .matching { include("part*.bin") }
            .files
            .sortedBy { it.name }

        require(parts.isNotEmpty()) { "Startup video binary parts are missing" }

        output.outputStream().use { destination ->
            parts.forEach { part ->
                part.inputStream().use { source -> source.copyTo(destination) }
            }
        }
    }
}

tasks.configureEach {
    if (name == "preBuild" || (name.startsWith("merge") && name.endsWith("Resources"))) {
        dependsOn(prepareStartupVideo)
    }
}
