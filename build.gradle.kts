buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 has built-in Kotlin support and pins KGP 2.2.10 / KSP 2.2.10-2.0.2.
        // These entries raise both to the versions the project actually targets.
        // Keep them in sync with `kotlin` and `ksp` in gradle/libs.versions.toml —
        // version catalog accessors are not available inside a `buildscript` block.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
