plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.11.1"
}

application {
    mainClass.set("io.getunleash.example.Constraints")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.GRAAL_VM
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.getunleash:unleash-client-java:11.1.0")
    implementation("ch.qos.logback:logback-classic:1.4.12")
}

graalvmNative {
        binaries {
        named("main") {
            buildArgs.add("-O3")
            buildArgs.add("--enable-url-protocols=http,https")
        }
    }
}
