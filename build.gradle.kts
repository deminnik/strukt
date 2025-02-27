plugins {
    kotlin("jvm") version "2.0.21"
}

group = "engineering.sketches"
version = "0.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}
