import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    kotlin("android")
    id("kotlinx-serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    kotlin("kapt")
    id("com.android.application")
}

android { buildFeatures { compose = true } }
// Let ART map and share DEX pages instead of decompressing a private copy in
// both the UI and service processes. Does not disable dexopt or JIT/AOT.
android.packaging.dex.useLegacyPackaging = false
android.defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
android.sourceSets["androidTest"].assets.srcDir("../core/src/main/golang/native/config/testdata")

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    // Preserve all native features while reducing APK downloads; Android extracts at install time.
    variant.packaging.jniLibs.useLegacyPackaging.set(true)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.01.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    compileOnly(project(":hideapi"))

    implementation(project(":core"))
    implementation(project(":service"))
    implementation(project(":design"))
    implementation(project(":common"))

    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.coordinator)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)
    implementation(libs.quickie.bundled)
    implementation(libs.androidx.activity.ktx)
}

tasks.getByName("clean", type = Delete::class) {
    delete(file("release"))
}

val prepareNotices by tasks.registering(Copy::class) {
    from(rootProject.file("LICENSE"), rootProject.file("NOTICE"))
    into(layout.buildDirectory.dir("generated/notices"))
}
android.sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/notices"))
tasks.named("preBuild").configure { dependsOn(prepareNotices) }

// Resources are committed snapshots with publisher SHA-256 values; builds never pull mutable latest assets.
android.sourceSets.getByName("main").assets.srcDir(rootProject.file("resources/geodata"))
val verifyGeoFiles by tasks.registering {
    inputs.dir(rootProject.file("resources/geodata"))
    doLast {
        val manifest = groovy.json.JsonSlurper().parse(rootProject.file("resources/geodata/manifest.json")) as Map<*, *>
        val assets = manifest["assets"] as List<*>
        assets.forEach { item ->
            val entry = item as Map<*, *>
            val asset = rootProject.file("resources/geodata/${entry["name"]}")
            val actual = MessageDigest.getInstance("SHA-256").digest(asset.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == entry["sha256"]) { "Geo asset checksum mismatch: ${asset.name}" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyGeoFiles) }
