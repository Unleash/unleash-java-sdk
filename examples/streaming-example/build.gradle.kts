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
    implementation("io.getunleash:unleash-client-java:12.2.1-SNAPSHOT")
    implementation("com.launchdarkly:okhttp-eventsource:4.2.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")
}
