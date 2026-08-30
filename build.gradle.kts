import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.8.1")
        classpath("org.ow2.asm:asm:9.8")
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

    // 字符串加密：在 ProGuard 混淆之前，把源码字符串常量替换为运行时解密调用。
    // 放在 jar 之后、obfuscateOfficial 之前，ProGuard 会自动追踪并同步解密调用的类/方法名。
    val encryptStrings by register<EncryptStringsTask>("encryptStrings") {
        dependsOn(jar)
        inputJar.set(jar.get().archiveFile.get().asFile)
        outputJar.set(layout.buildDirectory.file("libs/yiyiaddon${libs.versions.mod.version.get()}-encrypted.jar").get().asFile)
    }

    val obfuscateOfficial by register<proguard.gradle.ProGuardTask>("obfuscateOfficial") {
        dependsOn(encryptStrings)

        val inputJar = encryptStrings.outputJar.get().asFile
        // 同样跟随版本号，与映射文件 obfuscation-mapping-v{版本}.txt 保持一一对应
        val outputJar = layout.buildDirectory.file("libs/yiyiaddon${libs.versions.mod.version.get()}.jar").get().asFile

        injars(inputJar)
        outjars(outputJar)
        libraryjars(configurations.runtimeClasspath.get())
        // 自动定位当前电脑的 JDK 25，避免写死某台电脑的安装路径
        val jdkVersion = libs.versions.jdk.get()
        val configuredJdk = providers.gradleProperty("proguardJdkHome").orNull
            ?: providers.environmentVariable("JAVA_HOME").orNull
        val jdkCandidates = buildList {
            configuredJdk?.let { add(file(it)) }
            addAll(listOf(
                "C:/Program Files/Java",
                "C:/Program Files/Eclipse Adoptium",
                "C:/Program Files/BellSoft"
            ).flatMap { root ->
                file(root).listFiles()?.filter {
                    it.isDirectory && (it.name.startsWith("jdk-$jdkVersion") || it.name.contains("JDK-$jdkVersion", ignoreCase = true))
                } ?: emptyList()
            })
        }
        val jmods = jdkCandidates.asSequence()
            .map { it.resolve("jmods") }
            .firstOrNull { it.isDirectory && it.resolve("java.base.jmod").isFile }
            ?: error("未找到完整 JDK $jdkVersion。请安装 JDK $jdkVersion，或使用 -PproguardJdkHome=JDK目录 指定路径。")
        // 使用完整 JDK 的所有模块，为 ProGuard 建立与编译环境一致的完整类层次
        libraryjars(jmods)

        // 加入 Minecraft remapped jar（yarn 命名），为 optimize 提供完整的 Minecraft 类层次，
        // 解决开启优化时的 "Can't find common super class" 错误。
        // 路径跟随 Loom 缓存目录跨电脑可用；minecraft-merged.jar 是 Loom 下载并 remap 后的合并 jar。
        val minecraftJar = file("${gradle.gradleUserHomeDir.absolutePath}/caches/fabric-loom/${libs.versions.minecraft.get()}/minecraft-merged.jar")
        libraryjars(minecraftJar)

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

        // 保留字符串解密工具类与方法名：构建期加密任务注入的调用引用这个原始类名/方法名，
        // 若被 repackageclasses 移动或重命名，运行时解密调用会找不到方法导致功能异常。
        keep("public class com.example.addon.utils.StringCrypto { public static java.lang.String d(java.lang.String); }")
        
        // Mixin 类名由 mixins.json 按名称加载，必须保留；实现方法允许混淆。
        keepnames("@org.spongepowered.asm.mixin.Mixin class *")
        keepnames("@org.spongepowered.asm.mixin.Mixin interface *")

        // 仅保留 Mixin 与目标类绑定所必需的成员名称，避免把整个 Mixin 实现暴露出来。
        keepclassmembers("class * { @org.spongepowered.asm.mixin.Shadow <fields>; }")
        keepclassmembers("class * { @org.spongepowered.asm.mixin.Shadow <methods>; }")
        keepclassmembers("class * { @org.spongepowered.asm.mixin.gen.Accessor <methods>; }")
        keepclassmembers("class * { @org.spongepowered.asm.mixin.gen.Invoker <methods>; }")
        
        // 保留基类的公共 API，但实现细节会混淆
        keep("public class com.example.addon.core.YiyiaddonModule { public <methods>; }")
        keep("public interface com.example.addon.core.YiyiaddonRefreshable { *; }")
        
        // 不做缩减：模块、指令、HUD 都由 Meteor 通过反射和注解发现，
        // shrink 会误判它们是死代码而删掉。混淆强度靠改名和优化拿，不靠删。
        dontshrink()

        // 开启优化：已加入 Minecraft remapped jar，类层次完整。
        // optimize 做控制流混淆、方法内联、常量折叠、死代码消除，进一步提升反编译难度。
        // 排除类合并/水平合并——它们依赖 shrink，而 dontshrink 已关闭 shrink，强开会报错。
        optimizations("!class/merging/*,!class/unboxing/enum")

        // 将普通实现类集中到无语义包，隐藏 modules、villager、navigation、utils 等原始结构。
        // Fabric 入口由 keep 保留，Mixin 类由 keepnames 保留，因此其配置路径不会改变。
        repackageclasses("com.example.addon.x")
        overloadaggressively()
        // 保留访问边界，避免改变 Mixin 与第三方类之间的可见性语义。
        // allowaccessmodification()
        
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

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// 字符串加密任务：遍历 jar 中的 class，用 ASM 把 LDC 字符串常量替换为
// StringCrypto.d(密文) 调用。跳过 Mixin 类（@Mixin 注解，其方法名/字段名等
// 字符串是 Mixin 运行时绑定必需，不能加密）与含 switch 的方法（switch 的 case
// 字符串必须是编译期常量）。
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
abstract class EncryptStringsTask : DefaultTask() {
    // 与 StringCrypto 里的 XOR 密钥保持一致
    private val key = intArrayOf(0x2A, 0x5C, 0x7E, 0x19, 0x4B, 0x6D, 0x33, 0x1F)

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun run() {
        val inFile = inputJar.get().asFile
        val outFile = outputJar.get().asFile
        outFile.parentFile?.mkdirs()

        JarFile(inFile).use { jin ->
            JarOutputStream(FileOutputStream(outFile)).use { jout ->
                val entries = jin.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val bytes = jin.getInputStream(entry).readBytes()
                    // 只处理主 jar 里的 class（嵌套的 baritone 等第三方 jar 是独立文件，不加密）
                    val outBytes = if (entry.name.endsWith(".class")) {
                        encryptClass(bytes)
                    } else {
                        bytes
                    }
                    jout.putNextEntry(JarEntry(entry.name))
                    jout.write(outBytes)
                    jout.closeEntry()
                }
            }
        }
    }

    // XOR + Base64 加密，与 StringCrypto.d 解密逻辑对应
    private fun encrypt(s: String): String {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        for (i in b.indices) {
            b[i] = (b[i].toInt() xor key[i % key.size]).toByte()
        }
        return Base64.getEncoder().encodeToString(b)
    }

    private fun encryptClass(bytes: ByteArray): ByteArray {
        val cr = ClassReader(bytes)
        // COMPUTE_MAXS：重算栈深度以容纳新增的解密调用，但保留原 StackMapTable，
        // 不解析类型层次（避免因 Minecraft 类不在 classpath 而失败）。
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            private var isMixin = false

            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                if (descriptor == "Lorg/spongepowered/asm/mixin/Mixin;") {
                    isMixin = true
                }
                return super.visitAnnotation(descriptor, visible)
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                // Mixin 类整体跳过，避免破坏 Mixin 注入所需的字符串
                if (isMixin) return mv
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    // switch 的 case 字符串必须是编译期常量，一旦加密会导致 hash/equals 匹配不上
                    private var hasSwitch = false

                    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
                        hasSwitch = true
                        super.visitLookupSwitchInsn(dflt, keys, labels)
                    }

                    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
                        hasSwitch = true
                        super.visitTableSwitchInsn(min, max, dflt, *labels)
                    }

                    override fun visitLdcInsn(value: Any) {
                        if (value is String && !hasSwitch) {
                            // 明文 LDC 替换为「密文 LDC + StringCrypto.d()」调用
                            super.visitLdcInsn(encrypt(value))
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "com/example/addon/utils/StringCrypto",
                                "d",
                                "(Ljava/lang/String;)Ljava/lang/String;",
                                false
                            )
                        } else {
                            super.visitLdcInsn(value)
                        }
                    }
                }
            }
        }
        cr.accept(cv, 0)
        return cw.toByteArray()
    }
}
