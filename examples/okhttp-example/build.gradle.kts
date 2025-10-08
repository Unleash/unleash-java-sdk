plugins {
    java
    application
}

application {
    mainClass.set("io.getunleash.example.UnleashOkHttp")
}
repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.getunleash:unleash-client-java:11.1.0")
    implementation("com.squareup.okhttp3:okhttp:5.2.0")
}
