import com.google.protobuf.gradle.GenerateProtoTask

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.protobuf")
}

val IS_ANDROID_BUILD = true

android {
    namespace = "com.example.image_multi_recognition"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.image_multi_recognition"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // If you want to use Hilt in your Android instrumented test,
        // you need to create a custom AndroidJUnitRunner with the class name of HiltTestApplication set
        // see https://developer.android.com/training/dependency-injection/hilt-testing
        //testInstrumentationRunner = "com.example.image_multi_recognition.testrunner.HiltAndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:stabilityConfigurationPath=" +
                    "${rootDir}/compose_compiler_config.conf"
        )
    }
    buildFeatures {
        compose = true
        // viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/attach_hotspot_windows.dll"
            excludes += "META-INF/licenses/**"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
    // Fix from https://github.com/google/dagger/issues/2040
    hilt {
        enableAggregatingTask = true
    }
}

val roomVersion = "2.6.1"
val hiltVersion = "2.51"
val daggerVersion = "2.51"
val pagingVersion = "3.2.1"
val glideVersion = "4.16.0"
val lifecycleVersion = "2.7.0"
val navigationVersion = "2.7.7"
val coilVersion = "2.6.0"

dependencies {

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    // ContextualFlowRow, ...
    implementation("androidx.compose.foundation:foundation-layout-android:1.7.0-alpha06")
    // material 3
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    // Navigation drawer & Bottom navigation
    implementation("androidx.navigation:navigation-fragment-ktx:$navigationVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navigationVersion")
    // Tabbed navigation
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    // Moshi JSON
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    // Use FusedLocationProviderClient to get user's current location
    implementation("com.google.android.gms:play-services-location:21.2.0")
    // WorkManager
    implementation("androidx.work:work-runtime:2.9.0")
    // navigation compose
    implementation("androidx.navigation:navigation-compose:$navigationVersion")
    // Jetpack Compose: ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout-compose-android:1.1.0-alpha13")
    // LiveData
    implementation("androidx.compose.runtime:runtime-livedata")
    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    // To use collectAsStateWithLifecycle()
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVersion")
    // ZXing Android Embedded for Scanning QR code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // datastore-preferences
    implementation ("androidx.datastore:datastore-preferences:1.0.0")
    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    // support for returning PagingSource<K, V> from a DAO query
    implementation("androidx.room:room-paging:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    // Kotlin Extensions and Coroutines support for Room to write an observable query
    implementation("androidx.room:room-ktx:$roomVersion")
    // To use Kotlin Symbol Processing (KSP)
    ksp("androidx.room:room-compiler:$roomVersion")
    // Dependency injection: Hilt
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    implementation("com.google.dagger:dagger:$daggerVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    ksp("com.google.dagger:dagger-compiler:$hiltVersion")
    ksp("com.google.dagger:hilt-android-compiler:$hiltVersion")
    // proto DataStore
    implementation("androidx.datastore:datastore:1.0.0")
    //implementation("com.google.protobuf:protobuf-javalite:3.25.2")
    // Access Exif tag for image files
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // gRPC, from
    // https://github.com/grpc/grpc-kotlin/tree/master/compiler
    // https://github.com/grpc/grpc-java/tree/master
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
    if(IS_ANDROID_BUILD){
        implementation("io.grpc:grpc-okhttp:1.61.0")
        implementation("io.grpc:grpc-protobuf-lite:1.61.0")
        implementation("com.google.protobuf:protobuf-kotlin-lite:3.25.2")
    }else{
        runtimeOnly("io.grpc:grpc-netty-shaded:1.61.0")
        implementation("io.grpc:grpc-protobuf:1.61.0")
        implementation("com.google.protobuf:protobuf-kotlin:3.25.2")
    }
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    // paging
    implementation("androidx.paging:paging-runtime-ktx:$pagingVersion")
    implementation("androidx.paging:paging-compose:$pagingVersion")
    testImplementation("androidx.paging:paging-common-ktx:$pagingVersion")
    // ml-kit
    implementation("com.google.mlkit:object-detection:17.0.1")
    implementation("com.google.mlkit:image-labeling:17.0.8")
    // implementation("com.google.mediapipe:tasks-vision:latest.release")
    // Glide for image
    // implementation("com.github.bumptech.glide:glide:$glideVersion")
    // ksp("com.github.bumptech.glide:compiler:$glideVersion")
    // Coil for loading image
    implementation("io.coil-kt:coil:$coilVersion")
    implementation("io.coil-kt:coil-compose:$coilVersion")
    // implementation("io.coil-kt.coil3:coil:3.0.0-alpha06")
    // implementation("io.coil-kt.coil3:coil-compose:3.0.0-alpha06")
    // implementation("io.coil-kt.coil3:coil-core:3.0.0-alpha06")
    // implementation("io.coil-kt.coil3:coil-compose-core:3.0.0-alpha06")

    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.8.0")
    // Kotest assertion library
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.8.0")
    // Kotest Property Testing
    testImplementation("io.kotest:kotest-framework-datatest:5.8.0")
    testImplementation ("io.kotest:kotest-property:5.8.0")
    // Extra Arbs https://kotest.io/docs/proptest/property-test-extra-arbs.html
    testImplementation ("io.kotest.extensions:kotest-property-arbs:2.1.2")
    // Android Test
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    //Assertions
    testImplementation("org.assertj:assertj-core:3.25.1")
    androidTestImplementation("org.assertj:assertj-core:3.25.1")
    // Hilt for Android integration test
    testImplementation("com.google.dagger:hilt-android-testing:$hiltVersion")
    androidTestImplementation("com.google.dagger:hilt-android-testing:$hiltVersion")
    kspTest("com.google.dagger:hilt-android-compiler:$hiltVersion")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.2"
    }
    // From https://developers.googleblog.com/2021/11/announcing-kotlin-support-for-protocol.html
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.61.0"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    // Generates the java Protobuf-lite code for the Protobufs in this project. See
    // https://github.com/google/protobuf-gradle-plugin#customizing-protobuf-compilation
    // https://github.com/grpc/grpc-kotlin/tree/master/compiler
    // https://github.com/grpc/grpc-java/tree/master
    // for more information.
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc"){
                    if(IS_ANDROID_BUILD){
                        option("lite")
                    }
                }
                create("grpckt")
            }
            // From https://github.com/google/protobuf-gradle-plugin/issues/518
            task.builtins {
                create("java") {
                    if(IS_ANDROID_BUILD) option("lite")
                }
            }
            task.builtins{
                create("kotlin"){
                    if(IS_ANDROID_BUILD) option("lite")
                }
            }
        }
    }
}
// https://github.com/google/ksp/issues/1590
androidComponents {
    onVariants(selector().all()) { variant ->
        afterEvaluate {
            val protoTask =
                project.tasks.getByName("generate" + variant.name.replaceFirstChar { it.uppercaseChar() } + "Proto") as GenerateProtoTask

            project.tasks.getByName("ksp" + variant.name.replaceFirstChar { it.uppercaseChar() } + "Kotlin") {
                dependsOn(protoTask)
                (this as org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompileTool<*>).setSource(
                    protoTask.outputBaseDir
                )
            }
        }
    }
}