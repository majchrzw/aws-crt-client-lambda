plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.allopen") version "2.2.10"
    id("io.quarkus")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:quarkus-amazon-services-bom:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-amazon-lambda")
    implementation("io.quarkiverse.amazonservices:quarkus-amazon-sns")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.quarkus:quarkus-arc")

//    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("software.amazon.awssdk:aws-crt-client")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:kotlin-extensions")
    testImplementation("software.amazon.awssdk:url-connection-client")
    testImplementation("io.quarkiverse.amazonservices:quarkus-amazon-sqs")
}

group = "org.acme"
version = "1.0.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
    }
}
