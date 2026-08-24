buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.8.1")
    }
}

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    // Meteor
    implementation(libs.meteor.client)
    implementation(files("libs/baritone-fabric-26.1.2.jar"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(libs.versions.jdk.get().toInt()))
    }
}

fun toMinecraftCompat(version: String): String {
    val match = Regex("""^(\d{2})\.([1-9]\d*)(?:\.([1-9]\d*))?$""")
        .matchEntire(version)
        ?: error("Invalid Minecraft version format: $version. Expected YY.D or YY.D.H")

    val (year, drop, _) = match.destructured
    return "~$year.$drop"
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "minecraft_version" to toMinecraftCompat(libs.versions.minecraft.get()),
            "jdk_version" to libs.versions.jdk.get(),
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        archiveFileName.set("yiyiaddon1.0-personal.jar")
        inputs.property("archivesName", project.base.archivesName.get())
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from("libs/baritone-fabric-26.1.2.jar") {
            into("META-INF/jars")
        }
        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    register("buildPersonal") {
        group = "build"
        dependsOn(jar)
    }

    register<Exec>("scanMeteorUiText") {
        group = "verification"
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/scan-meteor-ui-text.ps1")
    }

    val obfuscateOfficial by register<proguard.gradle.ProGuardTask>("obfuscateOfficial") {
        dependsOn(jar)

        val inputJar = jar.get().archiveFile.get().asFile
        val outputJar = layout.buildDirectory.file("libs/yiyiaddon1.0.jar").get().asFile

        injars(inputJar)
        outjars(outputJar)
        libraryjars(configurations.runtimeClasspath.get())

        keepattributes(
            "RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations," +
                "RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations," +
                "AnnotationDefault,Signature,InnerClasses,EnclosingMethod"
        )
        adaptresourcefilecontents("fabric.mod.json")
        adaptresourcefilecontents("**.mixins.json")
        
        // 只保留绝对必要的入口，翻译类全混淆
        keep("public class com.example.addon.AddonTemplate { public void onInitialize(); public void onRegisterCategories(); public String getPackage(); }")
        
        // Mixin 必须保留类名和方法名（Fabric 需要），但字段和局部变量会混淆
        keep("@org.spongepowered.asm.mixin.Mixin class * { *; }")
        keep("@org.spongepowered.asm.mixin.Accessor class * { *; }")
        
        // 保留基类的公共 API，但实现细节会混淆
        keep("public class com.example.addon.YiyiaddonModule { public <methods>; }")
        keep("public interface com.example.addon.YiyiaddonRefreshable { *; }")
        
        // 关闭优化和缩减（保持兼容性），但开启强混淆
        dontoptimize()
        dontshrink()
        
        // 混淆配置：使用短名字，重载混淆
        repackageclasses("")
        allowaccessmodification()
        overloadaggressively()
        
        // 输出混淆映射文件，用于复原（带版本号）
        printmapping(layout.buildDirectory.file("obfuscation-mapping-v${libs.versions.mod.version.get()}.txt").get().asFile)
        
        // 字符串加密（ProGuard 不直接支持，但可以让反编译更难读）
        // 使用自定义字典让混淆名更难识别
        obfuscationdictionary("proguard-dictionary.txt")
        classobfuscationdictionary("proguard-dictionary.txt")
        packageobfuscationdictionary("proguard-dictionary.txt")
        
        dontwarn("**")
        dontnote("**")
    }

    register("buildOfficial") {
        group = "build"
        dependsOn(obfuscateOfficial)
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }
}
