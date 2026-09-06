package com.example.addon.utils;

import com.mojang.logging.LogUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;

/**
 * 统一资源包读取（DataPack）——配合构建期资源打包防文件名语义泄露。
 *
 * <p>构建期（packResources 任务）把 {@code enchantment/**} 等 80+ 个语义化
 * 散文件（如 {@code diamond_pickaxe-level30.json}——文件名即泄露装备覆盖范围）
 * 合并成单一无意义文件 {@code assets/yiyiaddon/rulepack.bin}：</p>
 * <ul>
 *   <li>个人测试版：bin 为明文 zip 容器；</li>
 *   <li>官方混淆版：bin 整体 AES-256-GCM 加密（密文魔数 YENC）。</li>
 * </ul>
 *
 * <p>本类负责三层读取：先查解包内存缓存；缓存未命中则读 bin 并按需走
 * {@link ResourceCrypto} 解密后解 zip；bin 不存在（IDE 开发运行时
 * classpath 是 build/resources 散文件，未过打包任务）则回退到
 * classpath 直接读散文件。三条路径对调用方完全透明。</p>
 */
public final class DataPack {

    private static final Logger LOG = LogUtils.getLogger();

    /** 统一资源包在 jar 内的路径（与构建期 RULE_PACK 常量保持一致） */
    public static final String RULE_PACK = "/assets/yiyiaddon/rulepack.bin";

    /** 包拆解缓存：包路径(带前导斜杠) → (包内条目名(带前导斜杠) → 内容) */
    private static final Map<String, Map<String, byte[]>> CACHE = new ConcurrentHashMap<>();

    /** 资源包不存在的哨兵值，避免开发环境每次读取都重试拆包 */
    private static final Map<String, byte[]> NOT_FOUND = new ConcurrentHashMap<>();

    private DataPack() {
    }

    /**
     * 读取资源内容（包内或散文件，透明解密）。
     *
     * @param path 资源路径，带前导斜杠；同时作为包内条目名（构建期保留原 jar 路径），
     *             例如 {@code /enchantment/vanilla/meta/rules.json}
     * @return 资源字节；路径不存在返回 null
     */
    public static byte[] get(String path) {
        // 归一化：包内条目名无前导斜杠，统一成带斜杠形式作 key
        String key = path.startsWith("/") ? path : "/" + path;
        Map<String, byte[]> pack = openPack();
        if (pack != NOT_FOUND) {
            byte[] bytes = pack.get(key);
            if (bytes != null) return bytes;
            LOG.warn("[DataPack] 资源包中不存在条目：{}", key);
            return null;
        }
        // 开发环境回退：资源未打包（build/resources 散文件），直接读 classpath
        return readScattered(key);
    }

    /** 打开（并缓存）统一资源包：读 bin → 按需解密 → 解 zip → 缓存；不存在则缓存哨兵 */
    private static Map<String, byte[]> openPack() {
        return CACHE.computeIfAbsent(RULE_PACK, k -> {
            Map<String, byte[]> m = unpackOrNull(k);
            return m != null ? m : NOT_FOUND;
        });
    }

    private static Map<String, byte[]> unpackOrNull(String packPath) {
        byte[] bin = readBin(packPath);
        if (bin == null) return null;
        // 个人版 bin 为明文 zip（无 YENC），ResourceCrypto.d 原样返回；官方版先解密
        byte[] zipBytes = ResourceCrypto.d(bin);
        try {
            Map<String, byte[]> map = new ConcurrentHashMap<>();
            try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
                ZipEntry entry;
                while ((entry = zin.getNextEntry()) != null) {
                    map.put("/" + entry.getName(), zin.readAllBytes());
                }
            }
            return map;
        } catch (Exception e) {
            LOG.error("[DataPack] 资源包结构损坏：{}", packPath, e);
            return null;
        }
    }

    /** 读取 bin 字节：classpath 直读（jar 内或散文件均可） */
    private static byte[] readBin(String packPath) {
        try (InputStream in = DataPack.class.getResourceAsStream(packPath)) {
            return in == null ? null : in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    /** 开发环境散文件回退：build/resources 下的原始散 JSON */
    private static byte[] readScattered(String path) {
        try (InputStream in = DataPack.class.getResourceAsStream(path)) {
            if (in == null) return null;
            byte[] raw = in.readAllBytes();
            // 散文件理论上不会加密，但保持兜底走一遍解密（非密文原样返回）
            return ResourceCrypto.d(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /** 诊断用：返回当前读取模式（打包/散文件），供模块启动报告打印 */
    public static String mode() {
        return readBin(RULE_PACK) != null ? "资源包" : "散文件(开发)";
    }
}