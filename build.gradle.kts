import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    id("maven-publish")
    id("priv.seventeen.artist.blink") version "1.3.13"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.kotlin.jvm") version "1.8.22"
    id("org.cadixdev.licenser") version "0.6.1"
}

license {
    setHeader(rootProject.file("HEADER"))
    include("**/*.kt", "**/*.java")
}

blink {
    name.set("ArcartX")
    version.set(project.version.toString())
    description.set("ArcartX Plugin")
    authors.set(listOf("17Artist"))
    apiVersion.set("1.20")
    packageName.set("priv.seventeen.artist.arcartx")
    softDepend.set(listOf(
        "MythicMobs", "AttributePlus", "Adyeshach",
        "PlaceholderAPI", "ModelEngine", "PlayerPoints",
        "Vault", "NeigeItems", "TrMenu", "Rondo"
    ))
    enableScript.set(true)
    enableAria.set(true)
    enableAsteroid.set(true)
    libraries.set(listOf(
        "com.zaxxer:HikariCP:4.0.3",
        "org.slf4j:slf4j-simple:2.0.7"
    ))
    logPrefix.set("§6♦ §bArc§3art§1X")
}

repositories {
    mavenCentral()
    maven("https://repo.arcartx.com/repository/maven-public/")
    maven("https://repo.arcartx.com/repository/maven-releases/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.tabooproject.org/repository/releases/")
    // 第三方插件 API 仓库
    maven("https://mvn.lumine.io/repository/maven-public/")   // MythicMobs / ModelEngine
    maven("https://repo.extendedclip.com/releases/")          // PlaceholderAPI
    maven("https://repo.rosewooddev.io/repository/public/")   // PlayerPoints
    maven("https://jitpack.io")                               // VaultAPI
}

dependencies {
    implementation("priv.seventeen.artist.blink:blink-common:1.3.13")

    // bStats 数据统计（org.bstats 在 shadowJar 中 relocate 进本插件包，避免与其他插件冲突）
    implementation("org.bstats:bstats-bukkit:3.1.0")

    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // 第三方插件 API
    compileOnly("io.lumine:Mythic-Dist:5.11.1")
    compileOnly("com.ticxo.modelengine:ModelEngine:R4.0.8")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("org.black_ixx:playerpoints:3.2.7")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")

    // 生态兼容
    compileOnly("priv.seventeen.artist:rondo:1.0.0")

    // 无公共 Maven 仓库的插件
    compileOnly(files("libs/AttributePlus-3.3.3.0.jar"))
    compileOnly(files("libs/AstraXHero-1.0.0-BETA-7.jar"))
    compileOnly(files("libs/TrMenu-3.12.3.jar"))

    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    compileOnly("io.netty:netty-all:4.1.108.Final")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("it.unimi.dsi:fastutil:8.5.12")
    compileOnly("org.apache.logging.log4j:log4j-core:2.20.0")

    implementation(files("libs/JavaAccessor-1.0-SNAPSHOT.jar"))

    compileOnly("com.zaxxer:HikariCP:4.0.3")
    compileOnly("org.slf4j:slf4j-simple:2.0.7")

    compileOnly("ink.ptms.adyeshach:api:2.1.11")

    annotationProcessor("org.projectlombok:lombok:1.18.24")
    compileOnly("org.projectlombok:lombok:1.18.24")

    // 单元测试（仅覆盖不依赖 Bukkit 的纯逻辑模块）
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjvm-default=all", "-Xskip-metadata-version-check")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    // 发布源码 jar（ArcartX-<版本>-sources.jar），方便下游对接 API 时在 IDE 中跳转/调试
    withSourcesJar()
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    // bStats 要求重定位 org.bstats，避免同服多插件的 bStats 类冲突（与 Blink 自身的 relocate 累加共存）
    relocate("org.bstats", "priv.seventeen.artist.arcartx.bstats")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("original")
}

tasks.named("build") {
    dependsOn("shadowJar")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])

            groupId = project.findProperty("group") as String
            artifactId = project.rootProject.name
            version = System.getenv("BUILD_NUMBER")?.let { "${project.version}.$it" } ?: project.version.toString()

            val relativePath = "build/libs"
            artifacts {
                fileTree(project.file(relativePath)).forEach { file ->
                    if (file.isFile && file.name.endsWith(".jar") && file.name.contains("api")) {
                        artifact(file) {
                            this.classifier = "api"
                        }
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "mavenJava"
            url = uri("https://repo.arcartx.com/repository/maven-releases/")
            credentials {
                username = System.getenv("MAVEN_USER")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}
