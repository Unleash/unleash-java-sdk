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
    implementation("io.getunleash:unleash-client-java:10.2.0")
    implementation("ch.qos.logback:logback-classic:1.4.12")
}
