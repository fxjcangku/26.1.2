package com.example.addon.utils;

/**
 * 字符串加密/解密运行时工具。
 * <p>
 * 构建期的字符串加密任务会把源码中的字符串常量替换为
 * {@code StringCrypto.d("密文")} 调用，运行时由本类还原明文。
 * 密钥与构建期加密任务里的 XOR 密钥保持一致。
 */
public final class StringCrypto {

    // XOR 密钥（构建期加密任务使用同一份密钥，改动需两边同步）
    private static final int[] KEY = {0x2A, 0x5C, 0x7E, 0x19, 0x4B, 0x6D, 0x33, 0x1F};

    private StringCrypto() {
    }

    /**
     * 解密字符串。解密失败时原样返回，避免运行时崩溃。
     *
     * @param enc Base64 编码的密文
     * @return 解密后的明文
     */
    public static String d(String enc) {
        if (enc == null || enc.isEmpty()) {
            return enc;
        }
        try {
            byte[] data = java.util.Base64.getDecoder().decode(enc);
            for (int i = 0; i < data.length; i++) {
                data[i] ^= KEY[i % KEY.length];
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // 解密失败兜底：返回原字符串，保证不因加密问题导致功能异常
            return enc;
        }
    }
}
