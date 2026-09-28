plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "tech.granet.grove"
    compileSdk = 36
    ndkVersion = "27.3.13750724"
    buildFeatures { buildConfig = true }
    sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("rustJniLibs"))
    defaultConfig {
        applicationId = "tech.granet.grove"
        minSdk = 31
        targetSdk = 36
        versionCode = 29
        versionName = "0.1.28-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

val buildRustAndroid by tasks.registering(Exec::class) {
    val rustSources = rootProject.file("rust/grove-core")
    inputs.files(fileTree(rustSources) { include("Cargo.toml", "Cargo.lock", "src/**/*.rs") })
    outputs.dir(layout.buildDirectory.dir("rustJniLibs"))
    commandLine("bash", rootProject.file("scripts/build-rust-android.sh").absolutePath)
}
tasks.named("preBuild") { dependsOn(buildRustAndroid) }
