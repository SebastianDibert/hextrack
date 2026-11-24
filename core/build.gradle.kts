import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    systemProperty("hextrak.out", rootProject.layout.buildDirectory.dir("host").get().asFile.absolutePath)
    systemProperty("hextrak.native", rootProject.layout.buildDirectory.file("host/libhextrak_host.so").get().asFile.absolutePath)
    inputs.files(fileTree("${rootDir}/app/src/main/cpp"))
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
