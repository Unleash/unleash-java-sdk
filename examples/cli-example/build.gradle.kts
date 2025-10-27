plugins {
    java
    application
}

application {
    mainClass.set("io.getunleash.example.AdvancedConstraints")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.getunleash:unleash-client-java:11.2.0-SNAPSHOT")
    implementation("ch.qos.logback:logback-classic:1.5.20")
}
