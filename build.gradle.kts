plugins {
    id("java")
}

group = "me.shawshark"
version = "2.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Folia API is a superset of the Paper API (which itself extends Bukkit/Spigot API).
    // Compiling against folia-api guarantees every symbol we use also exists on Paper,
    // so this same jar runs unmodified on Paper, Purpur, and Folia.
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    jar {
        archiveBaseName.set("AutoMessage")
        archiveVersion.set(project.version.toString())
    }
}
