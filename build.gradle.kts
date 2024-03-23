// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.21" apply false
    id("com.google.devtools.ksp") version "1.9.21-1.0.15"
    id("com.google.dagger.hilt.android") version "2.50" apply false
    id("com.google.protobuf") version "0.9.4" apply false
}

// generate compose metrics, from:
// https://getstream.io/blog/jetpack-compose-stability/
// https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md
subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        kotlinOptions.freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                    project.buildDir.absolutePath + "/compose_metrics"
        )
        kotlinOptions.freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=" +
                    project.buildDir.absolutePath + "/compose_metrics"
        )

        // strong skipping mode
        // compilerOptions.freeCompilerArgs.addAll(
        //    "-P",
        //    "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true",
        // )
    }
}