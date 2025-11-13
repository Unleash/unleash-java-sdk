plugins {
    java
    application
    id("com.google.cloud.tools.jib").version("3.4.5")
}

application {
    mainClass.set("io.getunleash.example.AdvancedConstraints")
}

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    targetCompatibility = JavaVersion.VERSION_25
    sourceCompatibility = JavaVersion.VERSION_25
}

jib {
    container {
        mainClass = "io.getunleash.example.AdvancedConstraints"
    }
    from {
        image = "gcr.io/distroless/java25-debian13:latest"
        platforms {
            platform {
                architecture = "amd64"
                os = "linux"
            }
            platform {
                architecture = "arm64"
                os = "linux"
            }
        }
    }
}

dependencies {
    implementation("io.getunleash:unleash-client-java:11.2.0-SNAPSHOT")
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-api:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-layout-template-json:2.25.2")
    runtimeOnly("com.fasterxml.jackson.core:jackson-databind:2.20.1")
}
