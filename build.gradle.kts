plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
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
        apkSource.copyTo(file("Trading-Zone.apk"), overwrite = true)
        apkSource.copyTo(file("releases/Trading-Zone.apk"), overwrite = true)
        apkSource.copyTo(file("releases/TRADING.CO.LTD.apk"), overwrite = true)
        println("Prepared downloadable APKs at Trading-Zone.apk and releases/Trading-Zone.apk")
    }
}
