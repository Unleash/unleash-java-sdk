plugins {
    java
    application
}

application {
    mainClass.set("io.getunleash.example.StreamingExample")
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.getunleash:unleash-client-java:11.1.0")
    implementation("com.launchdarkly:okhttp-eventsource:4.1.1")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}