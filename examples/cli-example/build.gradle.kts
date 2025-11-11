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
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21
}

jib {
    container {
        mainClass = "io.getunleash.example.AdvancedConstraints"
    }
}

dependencies {
    implementation("io.getunleash:unleash-client-java:11.2.0-SNAPSHOT")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.20")
}
