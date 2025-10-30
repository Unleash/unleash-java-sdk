plugins {
  java
  id("org.springframework.boot") version "3.5.7"
  id("io.spring.dependency-management") version "1.1.7"
}

repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-devtools")
  implementation("io.getunleash:unleash-client-java:11.2.0-SNAPSHOT")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
  useJUnitPlatform()
}
