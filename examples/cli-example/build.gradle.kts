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
    implementation("io.getunleash:unleash-client-java:11.0.1")
    implementation("ch.qos.logback:logback-classic:1.4.12")
}
