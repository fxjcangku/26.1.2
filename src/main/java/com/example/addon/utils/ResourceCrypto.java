package com.example.addon.utils;

import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 资源数据 AES-GCM 解密运行时工具（附魔规则 JSON 等资源加密）。
 *
 * <p>构建期的资源加密分支（见 EncryptStringsTask 的 encryptResource）会把
 * 附魔规则等关键资源文件用 AES-256-GCM 加密后写入 jar；运行时由本类直读
 * 字节流并透明还原明文，再交给 Gson 解析。</p>
 *
 * <p>密文格式：[魔数 4B "YENC"][随机 IV 12B][密文 + GCM 认证标签 16B]。
 * 每文件独立随机 IV，同一密钥下不存在 IV 复用问题。</p>
 *
 * <p>密钥设计：AES 密钥 = SHA-256(K)，其中 K[i] = StringCrypto.P[i] ^ Q[i]
 * （32 字节随机密钥源，每发布版本构建期全新生成）。构建期通过 ASM 把 K
 * 注入本类 {@code <clinit>}——密钥不落源码、不落本地文件，与字符串加密
 * 共用同一随机源；K 字段本身会被 ProGuard 混淆改名。</p>
 *
 * <p>下方 K 的默认值仅供「个人测试版（buildPersonal，资源不加密）」编译
 * 占位，发布版构建时会被 ASM 覆盖为随机值，因此这里的具体数字无意义。</p>
 *
 * <p>放在 utils 包与 StringCrypto 同源：两类密钥同一次构建生成，
 * 解密逻辑同样享受 ProGuard 混淆与反调试保护。</p>
 */
public final class ResourceCrypto {

    /** 密钥源（构建期 ASM 注入随机值；开发占位值与 StringCrypto 默认 P^Q 对应） */
    private static final int[] K = {
        0x35, 0x6F, 0x13, 0x5A, 0x52, 0x13, 0x26, 0x15,
        0x5F, 0x5C, 0x53, 0x5F, 0x5E, 0x53, 0x5C, 0x5F,
        0x69, 0x76, 0x7E, 0x7E, 0x7E, 0x7E, 0x76, 0x69,
        0x22, 0x77, 0x24, 0x6E, 0x6E, 0x24, 0x77, 0x22
    };

    /** 密文魔数 "YENC"（大端） */
    private static final int MAGIC = 0x59454E43;

    /** 密文最小长度：4 魔数 + 12 IV + 16 GCM 标签 */
    private static final int MIN_LEN = 4 + 12 + 16;

    private ResourceCrypto() {
    }

    /**
     * 判断一段字节是否为加密资源（魔数校验）。
     * 个人测试版资源为明文 JSON，不带魔数，直接返回 false 走原解析路径。
     *
     * @param data 资源完整字节
     * @return 是否为加密资源
     */
    public static boolean isEncrypted(byte[] data) {
        if (data == null || data.length < MIN_LEN) return false;
        int m = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16)
              | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        return m == MAGIC;
    }

    /**
     * 解密资源字节。非加密数据或解密失败时原样返回，保证资源读取不崩溃。
     *
     * @param enc 资源完整字节（可能带魔数的密文）
     * @return 解密后的明文字节
     */
    public static byte[] d(byte[] enc) {
        if (!isEncrypted(enc)) return enc;
        try {
            byte[] iv = Arrays.copyOfRange(enc, 4, 16);
            byte[] ct = Arrays.copyOfRange(enc, 16, enc.length);
            byte[] key = keyBytes();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(ct);
        } catch (Throwable t) {
            // 解密失败兜底：返回原字节（上层 Gson 解析会失败并记录日志，不静默崩溃）
            return enc;
        }
    }

    /** 由 K 派生 AES-256 密钥：SHA-256(K 字节) */
    private static byte[] keyBytes() throws Exception {
        byte[] k = new byte[K.length];
        for (int i = 0; i < K.length; i++) {
            k[i] = (byte) K[i];
        }
        return MessageDigest.getInstance("SHA-256").digest(k);
    }
}