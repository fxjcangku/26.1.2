import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TableSwitchInsnNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
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
        // README.txt（jar 内反编译说明）也需要跟随版本号展开，避免改造版本号后说明仍写旧版本
        filesMatching("README.txt") {
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

    // 资源打包：把附魔规则等散文件打成单一 rulepack.bin（去除文件名语义），
    // 个人版与官方版共用此步骤——个人版 bin 为明文 zip 容器，官方版再整体 AES 加密。
    val packedJar = layout.buildDirectory.file("libs/yiyiaddon${libs.versions.mod.version.get()}-packed.jar")
    val packResources by register<EncryptStringsTask>("packResources") {
        dependsOn(jar)
        packOnly.set(true)
        inputJar.set(jar.get().archiveFile.get().asFile)
        outputJar.set(packedJar)
    }

    // 覆盖 jar 输出为打包版（doLast 复制不注册任务输出，避免与 encryptStrings 的
    // packed.jar 输入产生 Gradle 9 隐式依赖校验冲突）
    register("finalizePersonalJar") {
        dependsOn(packResources)
        doLast {
            Files.copy(
                packedJar.get().asFile.toPath(),
                jar.get().archiveFile.get().asFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    register("buildPersonal") {
        group = "build"
        dependsOn("finalizePersonalJar")
    }

    register<Exec>("scanMeteorUiText") {
        group = "verification"
        commandLine("powershell", "-ExecutionPolicy", "Bypass", "-File", "scripts/scan-meteor-ui-text.ps1")
    }

    // 字符串加密：在 ProGuard 混淆之前，把源码字符串常量替换为运行时解密调用。
    // 放在 jar 之后、obfuscateOfficial 之前，ProGuard 会自动追踪并同步解密调用的类/方法名。
    val encryptStrings by register<EncryptStringsTask>("encryptStrings") {
        dependsOn(packResources)
        packOnly.set(false)
        inputJar.set(packedJar.get().asFile)
        outputJar.set(layout.buildDirectory.file("libs/yiyiaddon${libs.versions.mod.version.get()}-encrypted.jar").get().asFile)
    }

    val obfuscateOfficial by register<proguard.gradle.ProGuardTask>("obfuscateOfficial") {
        dependsOn(encryptStrings)

        val inputJar = encryptStrings.outputJar.get().asFile
        // 同样跟随版本号，与映射文件 obfuscation-mapping-v{版本}.txt 保持一一对应
        val outputJar = layout.buildDirectory.file("libs/yiyiaddon${libs.versions.mod.version.get()}.jar").get().asFile

        // 铁律：ProGuard outjars 是合并式写入，不会删除输出 jar 里已存在的旧条目。
        // 上一版构建的类名（如 A/aA/aB）会残留在新 jar 里，与本次字典类名（l/I/O0）并存：
        // 业务类双命名重复、jar 膨胀、且旧残留类不在本次映射里导致崩溃日志无法还原。
        // 必须每次构建前清空旧输出。
        doFirst {
            if (outputJar.exists()) outputJar.delete()
        }

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
        keep("public class com.example.addon.utils.StringCrypto { public static java.lang.String d(java.lang.String); public static int di(int); public static long dl(long); public static float df(float); public static double dd(double); }")
        // 不额外 keep 密钥字段：dontshrink 已保证字段不被删除，P/Q 既被 <clinit> 写入
        // 又被 d() 读取，optimize 也不会内联可变数组字段。字段名交给 ProGuard 混淆，
        // 让「密钥源 + 掩码」的两个 int[] 字段也变成 l/I 这类无语义名，进一步隐藏重组逻辑。
        
        // Mixin 类必须完整保留：类名 + 所有成员（方法签名、参数都不能被 optimize 改）。
        // 铁律：不能用 keepnames。keepnames 只保名字不保结构，optimize 会删除
        // @Inject 方法里未使用的参数（如 ExampleMixin 的 GameConfig），导致方法签名
        // 与目标方法不匹配，Mixin 注入直接崩溃（InvalidInjectionException: Invalid descriptor）。
        keep("@org.spongepowered.asm.mixin.Mixin class * { *; }")
        keep("@org.spongepowered.asm.mixin.Mixin interface * { *; }")

        // 保护枚举类核心结构：Class.getEnumConstants() 底层反射调用 values()/valueOf()，
        // optimize 会重命名或内联这两个方法，导致 EnumSetting 构造时 getEnumConstants()
        // 返回 null（Cannot read the array length because "this.values" is null）。
        // 必须用 keepclassmembers（保留方法体 + 原名），不能用 keepnames。
        keepclassmembers("enum * { public static **[] values(); public static ** valueOf(java.lang.String); }")

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
        
        // 映射文件加密后写进 Obfuscation/映射存档/：明文只经 build/ 临时文件，加密后删除。
        // 密文即使随仓库泄露也无法直接还原类名，需 Obfuscation/映射密钥.txt 才能解密。
        // build/ 在 .gitignore 内，且 gradlew clean 会整个删掉——映射一旦丢失，
        // 该版本的崩溃日志就永远无法还原成真实类名了，所以最终密文必须落在 Obfuscation/。
        val mappingPlain = layout.buildDirectory.file("obfuscation-mapping-v${libs.versions.mod.version.get()}.txt")
        printmapping(mappingPlain.get().asFile)

        // 任务结束：把明文映射加密成密文存档，删除明文临时文件
        doLast {
            val plainFile = mappingPlain.get().asFile
            if (plainFile.exists()) {
                val key = loadOrCreateMappingKey(file("Obfuscation/映射密钥.txt"))
                val 目标 = file("Obfuscation/映射存档/混淆映射-v${libs.versions.mod.version.get()}.txt")
                目标.parentFile.mkdirs()
                目标.writeText(xorEncryptBase64(plainFile.readText(StandardCharsets.UTF_8), key), StandardCharsets.UTF_8)
                plainFile.delete()
            }
        }
        
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
        // 源码统一 UTF-8（中文注释/字符串），Windows 下 javac 默认 GBK 会导致中文乱码编译报错
        options.encoding = "UTF-8"
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
    // 密钥长度（字节）。真实密钥 K[i] = P[i] ^ Q[i]，P 为随机源、Q 为随机掩码。
    // 构建期每次生成全新随机 P、Q，不存在固定密钥可反推。
    private val keyLength = 32

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    /** 仅打包模式：资源打成一个明文 zip 容器（个人测试版），class 不动、资源不加密 */
    @get:Input
    @get:Optional
    abstract val packOnly: Property<Boolean>

    @TaskAction
    fun run() {
        val inFile = inputJar.get().asFile
        val outFile = outputJar.get().asFile
        outFile.parentFile?.mkdirs()

        // 每个发布版本一份全新随机密钥：P（随机源）+ Q（随机掩码），
        // 运行时由 StringCrypto.d() 用 K[i] = P[i] ^ Q[i] 重组还原。
        val random = SecureRandom()
        val P = IntArray(keyLength) { random.nextInt(256) }
        val Q = IntArray(keyLength) { random.nextInt(256) }

        // 待打包资源：原 jar 路径 → 内容（收集后统一压缩进 rulepack.bin，散文件不再落盘）
        val packEntries = LinkedHashMap<String, ByteArray>()

        JarFile(inFile).use { jin ->
            JarOutputStream(FileOutputStream(outFile)).use { jout ->
                val entries = jin.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val bytes = jin.getInputStream(entry).readBytes()
                    // 附魔规则等关键数据：收集进资源包（不按原路径写入）
                    if (isPackableResource(entry.name)) {
                        packEntries[entry.name] = bytes
                        continue
                    }
                    // class：官方版做字符串加密，个人打包版原样透传
                    val outBytes = when {
                        entry.name == RULE_PACK && !packOnly.get() -> encryptResource(bytes, P, Q)
                        entry.name.endsWith(".class") && !packOnly.get() -> encryptClass(bytes, P, Q)
                        else -> bytes
                    }
                    jout.putNextEntry(JarEntry(entry.name))
                    jout.write(outBytes)
                    jout.closeEntry()
                }
                // 统一写入资源包：个人版明文 zip 容器；官方版容器整体 AES-GCM 加密
                if (packEntries.isNotEmpty()) {
                    val packed = zipPack(packEntries)
                    val outBytes = if (packOnly.get()) packed else encryptResource(packed, P, Q)
                    jout.putNextEntry(JarEntry(RULE_PACK))
                    jout.write(outBytes)
                    jout.closeEntry()
                }
            }
        }
    }

    /** 统一资源包路径：唯一无意义文件名，替代 80+ 个语义化散文件 */
    private val RULE_PACK = "assets/yiyiaddon/rulepack.bin"

    // ── 资源打包（附魔规则 JSON 等关键数据） ──────────────────────────────
    // 收集范围：enchantment/ 全部规则数据 + assets/yiyiaddon/gear-enchants.json。
    // 语言文件/图标等非核心资源保持散文件（无反编译价值，打包徒增运行开销）。
    private fun isPackableResource(name: String): Boolean =
        name.startsWith("enchantment/") || name == "assets/yiyiaddon/gear-enchants.json"

    // 把散资源打成内存 zip 容器：entry 名保留原 jar 路径，运行时 DataPack 按原路径查询。
    // zip 自带 DEFLATE 压缩，80+ 个 JSON 打包后体积进一步缩小。
    private fun zipPack(entries: LinkedHashMap<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zout ->
            for ((name, bytes) in entries) {
                zout.putNextEntry(ZipEntry(name))
                zout.write(bytes)
                zout.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    // AES-256-GCM 加密资源：格式 [魔数 "YENC" 4B][随机 IV 12B][密文 + GCM 认证标签 16B]。
    // 每文件独立随机 IV，防止多文件同密钥下的 IV 复用攻击；GCM 认证标签防密文篡改。
    // 不用 XOR 流密钥：JSON 头部（如 {"gear"）高度可预测，多文件共用流密钥
    // 会构成 known-plaintext 攻击，可恢复整个密钥流。
    private fun encryptResource(bytes: ByteArray, P: IntArray, Q: IntArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveAesKey(P, Q), "AES"), GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(bytes)
        return byteArrayOf('Y'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte()) + iv + ct
    }

    // AES 密钥 = SHA-256(K)，K[i] = P[i] ^ Q[i]——与 StringCrypto 共用同一随机密钥源，
    // ResourceCrypto 运行期的 K 字段由下方 ASM 注入同一份 K 值。
    private fun deriveAesKey(P: IntArray, Q: IntArray): ByteArray {
        val k = ByteArray(P.size)
        for (i in k.indices) k[i] = (P[i] xor Q[i]).toByte()
        return MessageDigest.getInstance("SHA-256").digest(k)
    }

    // XOR + Base64 加密，与 StringCrypto.d 解密逻辑对应（K[i] = P[i] ^ Q[i]）
    private fun encrypt(s: String, P: IntArray, Q: IntArray): String {
        val b = s.toByteArray(StandardCharsets.UTF_8)
        for (i in b.indices) {
            val k = i and (keyLength - 1)
            b[i] = (b[i].toInt() xor (P[k] xor Q[k])).toByte()
        }
        return Base64.getEncoder().encodeToString(b)
    }

    private fun encryptClass(bytes: ByteArray, P: IntArray, Q: IntArray): ByteArray {
        val cr = ClassReader(bytes)
        // 枚举类走 Tree API：需要回溯构造调用点，精确区分 name/ordinal（保留）
        // 与用户中文字段（加密）。流式 MethodVisitor 无法回溯前驱指令，故单独处理。
        if ((cr.access and Opcodes.ACC_ENUM) != 0) {
            return encryptEnumClass(bytes, P, Q)
        }
        // COMPUTE_MAXS：重算栈深度以容纳新增的解密调用，但保留原 StackMapTable，
        // 不解析类型层次（避免因 Minecraft 类不在 classpath 而失败）。
        val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
        val cv = object : ClassVisitor(Opcodes.ASM9, cw) {
            private var isMixin = false
            private var isCrypto = false
            private var cryptoName = ""

            override fun visit(version: Int, access: Int, name: String?, signature: String?, superName: String?, interfaces: Array<out String>?) {
                // 加密工具类自身（StringCrypto / ResourceCrypto）：跳过字符串加密
                // （避免 d() 内部自引用递归），改由 <clinit> 注入随机密钥。
                isCrypto = name == "com/example/addon/utils/StringCrypto"
                    || name == "com/example/addon/utils/ResourceCrypto"
                if (name != null) cryptoName = name
                return super.visit(version, access, name, signature, superName, interfaces)
            }

            override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor {
                if (descriptor == "Lorg/spongepowered/asm/mixin/Mixin;") {
                    isMixin = true
                }
                return super.visitAnnotation(descriptor, visible)
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor {
                // 加密工具类的 <clinit>：重写为注入随机密钥的字节码，
                // 覆盖源码里的默认占位值，实现「构建期随机密钥 + 运行时重组」。
                if (isCrypto && name == "<clinit>") {
                    val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                    mv.visitCode()
                    // 按类分派注入：StringCrypto 注入 P、Q；ResourceCrypto 注入 K = P ^ Q
                    when (cryptoName) {
                        "com/example/addon/utils/StringCrypto" -> {
                            emitIntArray(mv, P)
                            mv.visitFieldInsn(Opcodes.PUTSTATIC, "com/example/addon/utils/StringCrypto", "P", "[I")
                            emitIntArray(mv, Q)
                            mv.visitFieldInsn(Opcodes.PUTSTATIC, "com/example/addon/utils/StringCrypto", "Q", "[I")
                        }
                        "com/example/addon/utils/ResourceCrypto" -> {
                            val K = IntArray(keyLength) { i -> P[i] xor Q[i] }
                            emitIntArray(mv, K)
                            mv.visitFieldInsn(Opcodes.PUTSTATIC, "com/example/addon/utils/ResourceCrypto", "K", "[I")
                        }
                    }
                    mv.visitInsn(Opcodes.RETURN)
                    mv.visitMaxs(0, 0)
                    mv.visitEnd()
                    // 丢弃原 <clinit> 方法体事件（旧占位初始化已无意义）
                    return object : MethodVisitor(Opcodes.ASM9) {}
                }

                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                // Mixin 类或 StringCrypto 自身跳过：Mixin 字符串是注入绑定必需；
                // 枚举已在 encryptClass 入口分流到 Tree API，不会走到这里。
                if (isMixin || isCrypto) return mv
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
                            super.visitLdcInsn(encrypt(value, P, Q))
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

            // 生成 `new int[len]` + 逐元素 IASTORE 的数组初始化字节码序列（值保留在栈上）
            private fun emitIntArray(mv: MethodVisitor, values: IntArray) {
                pushInt(mv, values.size)
                mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT)
                for (i in values.indices) {
                    mv.visitInsn(Opcodes.DUP)
                    pushInt(mv, i)
                    pushInt(mv, values[i])
                    mv.visitInsn(Opcodes.IASTORE)
                }
            }

            // 按取值范围选最优的整型常量压栈指令（ICONST / BIPUSH / SIPUSH / LDC）
            private fun pushInt(mv: MethodVisitor, v: Int) {
                when {
                    v in -1..5 -> mv.visitInsn(Opcodes.ICONST_0 + v)
                    v in Byte.MIN_VALUE..Byte.MAX_VALUE -> mv.visitIntInsn(Opcodes.BIPUSH, v)
                    v in Short.MIN_VALUE..Short.MAX_VALUE -> mv.visitIntInsn(Opcodes.SIPUSH, v)
                    else -> mv.visitLdcInsn(v)
                }
            }
        }
        cr.accept(cv, 0)
        // 普通类（非 Mixin、非 Crypto）额外做数字常量加密，隐藏魔法数字
        return encryptNumbers(cw.toByteArray(), P, Q)
    }

    // ── 枚举类字符串加密（Tree API）────────────────────────────────────────
    // 枚举 name 是 Enum.name()/valueOf()/getEnumConstants() 的绑定标识，必须保留明文；
    // 但构造函数的「用户中文字段」、toString 等普通方法的字符串可以加密。
    // 用 Tree API 才能从构造调用点回溯参数，区分 name/ordinal 与用户字段。

    private fun encryptEnumClass(bytes: ByteArray, P: IntArray, Q: IntArray): ByteArray {
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        for (method in cn.methods) {
            if (method.name == "<clinit>") {
                encryptEnumClinit(method, P, Q)
            } else {
                encryptMethodStrings(method, P, Q)
            }
        }
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cn.accept(cw)
        return cw.toByteArray()
    }

    // 枚举 <clinit>：定位枚举构造调用，加密 name、ordinal 之外的 String 参数。
    // 枚举常量初始化字节码固定为 new / dup / [name] / [ordinal] / [用户字段...] / invokespecial，
    // 故从调用点向前收集「参数个数」条指令即为各参数（name 与 ordinal 各占一条压栈指令）。
    private fun encryptEnumClinit(method: MethodNode, P: IntArray, Q: IntArray) {
        val insns = method.instructions
        for (node in insns.toArray()) {
            if (node !is MethodInsnNode) continue
            if (node.opcode != Opcodes.INVOKESPECIAL || node.name != "<init>") continue
            val args = Type.getArgumentTypes(node.desc)
            // 参数 0=name、1=ordinal，都是编译器隐式注入；索引 2 起才是用户字段
            if (args.size < 3) continue
            val argNodes = collectArgNodes(node, args.size)
            for (i in 2 until args.size) {
                val arg = argNodes.getOrNull(i) ?: continue
                if (arg is LdcInsnNode && arg.cst is String) {
                    arg.cst = encrypt(arg.cst as String, P, Q)
                    insns.insert(arg, MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/example/addon/utils/StringCrypto",
                        "d",
                        "(Ljava/lang/String;)Ljava/lang/String;",
                        false
                    ))
                }
            }
        }
    }

    // 普通方法：加密 LDC 字符串；含 switch 的方法整体跳过（case 字符串是编译期常量）
    private fun encryptMethodStrings(method: MethodNode, P: IntArray, Q: IntArray) {
        for (node in method.instructions.toArray()) {
            if (node is LookupSwitchInsnNode || node is TableSwitchInsnNode) return
        }
        for (node in method.instructions.toArray()) {
            if (node is LdcInsnNode && node.cst is String) {
                node.cst = encrypt(node.cst as String, P, Q)
                method.instructions.insert(node, MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/example/addon/utils/StringCrypto",
                    "d",
                    "(Ljava/lang/String;)Ljava/lang/String;",
                    false
                ))
            }
        }
    }

    // 从调用点向前收集恰好 count 条指令，作为该调用的参数（按序返回 [arg0, arg1, ...]）
    private fun collectArgNodes(methodInsn: MethodInsnNode, count: Int): List<AbstractInsnNode> {
        val result = ArrayList<AbstractInsnNode>()
        var node: AbstractInsnNode? = methodInsn.previous
        while (node != null && result.size < count) {
            result.add(node)
            node = node.previous
        }
        return result.reversed()
    }

    // ── 数字常量加密（Tree API）────────────────────────────────────────────
    // 把 BIPUSH/SIPUSH/LDC int 常量 XOR 加密为密文 + StringCrypto.di() 还原。
    // XOR 加密 + 解密是透明的：每个加密常量后紧跟 di() 调用，任何使用点拿到的都是原值，
    // 因此无需排除数组长度等场景。ICONST(-1..5) 是 InsnNode 不在此列，天然保留。

    private fun encryptNumbers(bytes: ByteArray, P: IntArray, Q: IntArray): ByteArray {
        val cn = ClassNode()
        ClassReader(bytes).accept(cn, 0)
        // Mixin 与 StringCrypto 自身跳过：Mixin 常量可能是注入绑定所需，Crypto 的 di 自身不能加密
        if (cn.name == "com/example/addon/utils/StringCrypto") return bytes
        if (cn.visibleAnnotations?.any { it.desc == "Lorg/spongepowered/asm/mixin/Mixin;" } == true) return bytes
        for (method in cn.methods) {
            encryptMethodInts(method, P, Q)
        }
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)
        cn.accept(cw)
        return cw.toByteArray()
    }

    // 遍历方法指令，加密数值常量（int/long/float/double）
    private fun encryptMethodInts(method: MethodNode, P: IntArray, Q: IntArray) {
        val insns = method.instructions
        for (node in insns.toArray()) {
            when (node) {
                is IntInsnNode -> {
                    if (node.opcode == Opcodes.BIPUSH || node.opcode == Opcodes.SIPUSH) {
                        val ldc = LdcInsnNode(encryptInt(node.operand, P, Q))
                        val inv = MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/example/addon/utils/StringCrypto",
                            "di",
                            "(I)I",
                            false
                        )
                        insns.set(node, ldc)
                        insns.insert(ldc, inv)
                    }
                }
                is LdcInsnNode -> {
                    when (node.cst) {
                        is Int -> {
                            node.cst = encryptInt(node.cst as Int, P, Q)
                            insns.insert(node, MethodInsnNode(
                                Opcodes.INVOKESTATIC, "com/example/addon/utils/StringCrypto", "di", "(I)I", false
                            ))
                        }
                        is Long -> {
                            node.cst = encryptLong(node.cst as Long, P, Q)
                            insns.insert(node, MethodInsnNode(
                                Opcodes.INVOKESTATIC, "com/example/addon/utils/StringCrypto", "dl", "(J)J", false
                            ))
                        }
                        is Float -> {
                            node.cst = encryptFloat(node.cst as Float, P, Q)
                            insns.insert(node, MethodInsnNode(
                                Opcodes.INVOKESTATIC, "com/example/addon/utils/StringCrypto", "df", "(F)F", false
                            ))
                        }
                        is Double -> {
                            node.cst = encryptDouble(node.cst as Double, P, Q)
                            insns.insert(node, MethodInsnNode(
                                Opcodes.INVOKESTATIC, "com/example/addon/utils/StringCrypto", "dd", "(D)D", false
                            ))
                        }
                    }
                }
            }
        }
    }

    // 32 位密钥：由 P/Q 前四字节派生，与 StringCrypto.di/df 一致
    private fun keyInt(P: IntArray, Q: IntArray): Int =
        ((P[0] xor Q[0]) shl 24) or ((P[1] xor Q[1]) shl 16) or ((P[2] xor Q[2]) shl 8) or (P[3] xor Q[3])

    // 64 位密钥：keyInt 复用为高/低 32 位，与 StringCrypto.dl/dd 一致
    private fun keyLong(P: IntArray, Q: IntArray): Long {
        val k = keyInt(P, Q)
        return (k.toLong() shl 32) or (k.toLong() and 0xFFFFFFFFL)
    }

    private fun encryptInt(v: Int, P: IntArray, Q: IntArray): Int = v xor keyInt(P, Q)

    private fun encryptLong(v: Long, P: IntArray, Q: IntArray): Long = v xor keyLong(P, Q)

    // float 加密：清除 exponent 位（bit 23..30），保证 XOR 结果不是 NaN，避免 intBitsToFloat 规范化
    private fun encryptFloat(v: Float, P: IntArray, Q: IntArray): Float =
        Float.fromBits(v.toRawBits() xor (keyInt(P, Q) and 0x7F800000.inv()))

    // double 加密：清除 exponent 位（bit 52..62），保证 XOR 结果不是 NaN，避免 longBitsToDouble 规范化
    private fun encryptDouble(v: Double, P: IntArray, Q: IntArray): Double =
        Double.fromBits(v.toRawBits() xor (keyLong(P, Q) and 0x7FF0000000000000L.inv()))
}

// ── 映射文件加密辅助（XOR + Base64）────────────────────────────────────────
// 映射文件是「还原类名的钥匙」，随 source 分支入库有泄露风险。这里把它加密成密文，
// 密钥随机生成存 Obfuscation/映射密钥.txt（加入 .gitignore，不进仓库）。
// 还原崩溃日志.js 用同一密钥解密，密钥文件丢失则该版本映射无法还原。

fun loadOrCreateMappingKey(keyFile: File): ByteArray {
    if (keyFile.exists()) {
        return hexToBytes(keyFile.readText().trim())
    }
    val key = ByteArray(32)
    SecureRandom().nextBytes(key)
    keyFile.parentFile?.mkdirs()
    keyFile.writeText(bytesToHex(key))
    return key
}

fun xorEncryptBase64(plain: String, key: ByteArray): String {
    val data = plain.toByteArray(StandardCharsets.UTF_8)
    val out = ByteArray(data.size)
    for (i in data.indices) {
        out[i] = (data[i].toInt() xor (key[i % key.size].toInt() and 0xFF)).toByte()
    }
    return Base64.getEncoder().encodeToString(out)
}

fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

fun hexToBytes(hex: String): ByteArray =
    ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
