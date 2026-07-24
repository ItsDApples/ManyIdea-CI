plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo-momi.gtemc.cn/releases/")
    maven("https://nexus.frengor.com/repository/public/")
}

dependencies {
    // paperweight dev bundle 包含完整 Paper API + NMS (Mojang mappings, 26.1+ 去混淆)
    paperweight.paperDevBundle(libs.versions.minecraft.get() + ".build.+")
    compileOnly(libs.craft.engine.core)
    compileOnly(libs.craft.engine.bukkit)
    compileOnly(libs.craft.engine.adventure)
    compileOnly("com.frengor:ultimateadvancementapi:2.8.0")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

// 开发时自动复制 CE addon 配置到 run 目录，确保 CE 能读取
val copyCEAddon = tasks.register<Copy>("copyCEAddon") {
    from("src/main/resources/addon")
    into(layout.projectDirectory.dir("run/plugins/CraftEngine/resources"))
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
        downloadPlugins {
            modrinth("craftengine", "26.7.4")
            modrinth("ultimateadvancementapi", "2.8.0")
        }
        dependsOn(copyCEAddon)
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
