import org.gradle.api.file.DuplicatesStrategy.INCLUDE

plugins {
    id("email-verifier.java-conventions")
}

val rabbitVersion: String by project
val logbackVersion: String by project
val ktorVersion: String by project
val exposedVersion: String by project
val hikariVersion: String by project
val postgresVersion: String by project

dependencies {

    implementation(project(":components:serialization-support"))
    implementation(project(":components:env-support"))

    implementation("com.rabbitmq:amqp-client:$rabbitVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-java:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    testImplementation(project(":components:test-support"))
}

task<JavaExec>("run") {
    classpath = files(tasks.jar)
    environment("DATABASE_URL", "jdbc:postgresql://localhost:5555/notification_dev?user=emailverifier&password=emailverifier")
    environment("RABBIT_URL", "amqp://localhost:5672")
    environment("FROM_ADDRESS", "dev@initialcapacity.io")
    environment("SENDGRID_API_KEY", "super-secret")
    environment("SENDGRID_URL", "http://localhost:9091")
}

tasks {
    jar {
        manifest {
            attributes("Main-Class" to "io.initialcapacity.emailverifier.analyzer.AppKt")
        }

        duplicatesStrategy = INCLUDE

        from({
            configurations.runtimeClasspath.get()
                .filter { it.name.endsWith("jar") }
                .map {
                    zipTree(it)
                }
        })
    }
}
