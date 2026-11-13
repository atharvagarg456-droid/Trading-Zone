import java.util.Base64

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
}

// Automatically materialize debug.keystore from debug.keystore.base64 on clean checkouts (such as GitHub Actions)
val rootKeystoreFile = file("debug.keystore")
val rootKeystoreBase64File = file("debug.keystore.base64")
if (!rootKeystoreFile.exists() && rootKeystoreBase64File.exists()) {
    runCatching {
        val rawBase64 = rootKeystoreBase64File.readText().replace("\\s".toRegex(), "")
        rootKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(rawBase64))
    }
}

tasks.register("prepareGithubApk") {
    dependsOn(":app:assembleDebug")
    doLast {
        val apkSource = file("app/build/outputs/apk/debug/app-debug.apk")
        if (!apkSource.exists()) {
            throw GradleException("Debug APK not found at ${apkSource.absolutePath}")
        }
        val releasesDir = file("releases")
        if (!releasesDir.exists()) {
            releasesDir.mkdirs()
        }
        val docsDir = file("docs")
        if (!docsDir.exists()) {
            docsDir.mkdirs()
        }
        apkSource.copyTo(file("Trading-Zone.apk"), overwrite = true)
        apkSource.copyTo(file("releases/Trading-Zone.apk"), overwrite = true)
        apkSource.copyTo(file("releases/TRADING.CO.LTD.apk"), overwrite = true)
        apkSource.copyTo(file("docs/Trading-Zone.apk"), overwrite = true)
        println("Prepared downloadable APKs (${apkSource.length() / 1024} KB) at Trading-Zone.apk, releases/Trading-Zone.apk, and docs/Trading-Zone.apk")
    }
}
