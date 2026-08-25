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
        // 文件名跟着 gradle/libs.versions.toml 的 mod-version 走，
        // 不要硬编码版本号，否则改了版本号 jar 名还是旧的（beta 版尤其容易漏）
        archiveFileName.set("yiyiaddon${libs.versions.mod.version.get()}-personal.jar")
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
        // 同样跟随版本号，与映射文件 obfuscation-mapping-v{版本}.txt 保持一一对应
        val outputJar = layout.buildDirectory.file("libs/yiyiaddon${libs.versions.mod.version.get()}.jar").get().asFile

        injars(inputJar)
        outjars(outputJar)
        libraryjars(configurations.runtimeClasspath.get())

        // 只保留运行时必需的注解属性，删掉调试和类型信息：
        //   保留：运行时注解（Mixin/Fabric 需要）
        //   删除：Signature（泛型信息）、InnerClasses/EnclosingMethod（嵌套结构信息）
        //         LineNumberTable/LocalVariableTable（调试信息，已通过不声明而默认删除）
        keepattributes(
            "RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations," +
                "RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations," +
                "AnnotationDefault"
        )
        // 统一源文件名为单字母，堆栈里看不出真实文件
        renamesourcefileattribute("S")
        adaptresourcefilecontents("fabric.mod.json")
        adaptresourcefilecontents("**.mixins.json")
        
        // 只保留绝对必要的入口，翻译类全混淆
        // 注意包路径是 com.example.addon.core，不是 com.example.addon。
        // 写错包名 ProGuard 不会报错，规则静默失效，入口类被混淆后
        // fabric.mod.json 里声明的 entrypoint 找不到类，游戏直接起不来。
        keep("public class com.example.addon.core.AddonTemplate { public void onInitialize(); public void onRegisterCategories(); public String getPackage(); }")
        
        // Mixin 必须保留类名和方法名（Fabric 需要），但字段和局部变量会混淆
        keep("@org.spongepowered.asm.mixin.Mixin class * { *; }")
        keep("@org.spongepowered.asm.mixin.Accessor class * { *; }")
        
        // 保留基类的公共 API，但实现细节会混淆
        keep("public class com.example.addon.core.YiyiaddonModule { public <methods>; }")
        keep("public interface com.example.addon.core.YiyiaddonRefreshable { *; }")
        
        // 不做缩减：模块、指令、HUD 都由 Meteor 通过反射和注解发现，
        // shrink 会误判它们是死代码而删掉。混淆强度靠改名和优化拿，不靠删。
        dontshrink()

        // 不做优化：ProGuard 7.8.1 在 dontshrink + optimizations 组合下会报
        // "Can't find common super class" 错误（类层次分析失败）。
        // 混淆强度主要靠改名：repackageclasses + overloadaggressively + 易混字典。
        dontoptimize()

        // 混淆配置：短名字 + 重载混淆
        repackageclasses("")
        flattenpackagehierarchy("")
        allowaccessmodification()
        overloadaggressively()
        // 注意：不要调 useuniqueclassmembernames()，那是「强制成员名唯一」，
        // 会削弱 overloadaggressively 的效果。默认允许重名才是我们想要的。
        
        // 映射文件直接写进 Obfuscation/映射存档/，不要留在 build/ 里。
        // build/ 在 .gitignore 内，且 gradlew clean 会整个删掉——映射一旦丢失，
        // 该版本的崩溃日志就永远无法还原成真实类名了。
        printmapping(file("Obfuscation/映射存档/混淆映射-v${libs.versions.mod.version.get()}.txt"))
        
        // 自定义字典：让混淆后的名字更难辨认（O0/l1/I1 这类易混字符）
        // 必须用 file() 传绝对路径，直接传相对路径字符串 ProGuard 找不到文件，
        // 且不会报错——只会静默退回默认的 a/b/c 命名，很容易误判为「已生效」。
        val 字典 = file("Obfuscation/字典/混淆字典.txt")
        // 声明为任务输入，确保字典内容改变时会重新混淆，否则 Gradle 会用缓存
        inputs.file(字典)
        obfuscationdictionary(字典)
        classobfuscationdictionary(字典)
        packageobfuscationdictionary(字典)
        
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
