import android.databinding.tool.ext.capitalizeUS
import com.github.kr328.golang.GolangBuildTask
import com.github.kr328.golang.GolangPlugin
import java.io.ByteArrayOutputStream

plugins {
    kotlin("android")
    id("com.android.library")
    id("kotlinx-serialization")
    id("golang-android")
}

val golangSource = file("src/main/golang/native")

golang {
    sourceSets {
        create("alpha") {
            tags.set(listOf("foss","with_gvisor","cmfa"))
            srcDir.set(file("src/foss/golang"))
        }
        create("meta") {
            tags.set(listOf("foss","with_gvisor","cmfa"))
            srcDir.set(file("src/foss/golang"))
        }
        all {
            fileName.set("libclash.so")
            packageName.set("lees/native")
        }
    }
}

android {
    productFlavors {
        all {
            externalNativeBuild {
                cmake {
                    arguments("-DGO_SOURCE:STRING=${golangSource}")
                    arguments("-DGO_OUTPUT:STRING=${GolangPlugin.outputDirOf(project, null, null)}")
                    arguments("-DFLAVOR_NAME:STRING=$name")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core)
    implementation(libs.kotlin.coroutine)
    implementation(libs.kotlin.serialization.json)
}

afterEvaluate {
    tasks.withType(GolangBuildTask::class.java).forEach { goTask ->
        goTask.inputs.dir(golangSource)
        goTask.inputs.property("nativeMemoryAdvice", "madvdontneed=1")
        goTask.doFirst {
            val command = goTask.commandLine.toMutableList()
            val tagIndex = command.indexOf("-tags")
            val output = ByteArrayOutputStream()
            project.exec {
                workingDir = goTask.workingDir
                environment(goTask.environment)
                commandLine(listOf(command.first(), "list", "-f", "{{.DefaultGODEBUG}}") +
                    (if (tagIndex >= 0) listOf("-tags", command[tagIndex + 1]) else emptyList()) + command.last())
                standardOutput = output
            }
            // Android shared Go libraries start with an empty environment.
            // Preserve Go's module/version compatibility defaults when setting
            // the page-release policy at link time, rather than replacing them.
            val defaults = output.toString(Charsets.UTF_8).trim()
            check(!defaults.startsWith("<")) { "Unable to read Go runtime compatibility defaults" }
            val advice = listOf(defaults, "madvdontneed=1").filter { it.isNotEmpty() }.joinToString(",")
            val flag = "-X=runtime.godebugDefault=$advice -X=lees/native/diagnostics.runtimeMemoryAdvice=madvdontneed=1"
            val flagIndex = command.indexOf("-ldflags")
            if (flagIndex >= 0) command[flagIndex + 1] += " $flag"
            else command.addAll(command.size - 1, listOf("-ldflags", flag))
            goTask.commandLine(command)
        }
    }
}

val abis = listOf("arm64-v8a" to "Arm64V8a", "armeabi-v7a" to "ArmeabiV7a", "x86" to "X86", "x86_64" to "X8664")
val injectedAbis = providers.gradleProperty("android.injected.build.abi").orNull?.split(',')

androidComponents.onVariants { variant ->
    val cmakeName = if (variant.buildType == "debug") "Debug" else "RelWithDebInfo"
    val goTasks = abis.filter { injectedAbis == null || it.first in injectedAbis }.map { (_, goAbi) ->
        "externalGolangBuild${variant.name.capitalizeUS()}$goAbi"
    }
    tasks.configureEach {
        // CMake task names omit the flavor. Add dependencies only to the variant's merge task.
        if (name == "merge${variant.name.capitalizeUS()}NativeLibs") dependsOn(goTasks)
        if (name.startsWith("configureCMake$cmakeName[") || name.startsWith("buildCMake$cmakeName[")) {
            mustRunAfter(goTasks)
        }
    }
}
