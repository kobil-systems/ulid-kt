import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  val kotlinVersion = "1.5.0"
  kotlin("jvm") version kotlinVersion
  jacoco
  `maven-publish`
  id("org.jlleitschuh.gradle.ktlint") version "10.0.0"
}

group = "com.kobil.ulid"
version = "1.2.2"

repositories {
  mavenCentral()
}

val projectGroup = group
val projectVersion = version

val snapshotRepo: String by project
val releaseRepo: String by project
val deployUser: String by project
val deployPassword: String by project

jacoco.toolVersion = "0.8.7" // override default because of incompatibility with Kotlin 1.5.0

val watchForChange = "src/**/*"
val doOnChange = "$projectDir/gradlew classes"

val junitJupiterVersion = "5.7.0"
val kotestVersion = "4.6.3"
val kotestArrowVersion = "1.0.3"
val arrowVersion = "1.0.0"

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  implementation("io.arrow-kt:arrow-core:$arrowVersion")

  testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
  testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
  testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
  testImplementation("io.kotest:kotest-property:$kotestVersion")
  testImplementation("io.kotest.extensions:kotest-assertions-arrow:$kotestArrowVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions.jvmTarget = "16"

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions.jvmTarget = "16"

tasks.withType<Test> {
  useJUnitPlatform()
  testLogging {
    events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
  }

  finalizedBy(tasks.jacocoTestReport) // report is always generated after tests run
}

tasks.jacocoTestReport {
  dependsOn(tasks.test) // tests are required to run before generating the report

  reports {
    xml.isEnabled = true
  }
}
