package com.example.addon.utils;

/**
 * 字符串加密/解密运行时工具。
 *
 * <p>构建期的字符串加密任务（{@code EncryptStringsTask}）会把源码中的字符串常量
 * 替换为 {@code StringCrypto.d("密文")} 调用，运行时由本类还原明文。</p>
 *
 * <p>密钥设计：真实密钥字节流 {@code K[i] = P[i] ^ Q[i]}，即密钥被拆成「随机源 + 随机掩码」
 * 两段存储。构建期用 {@link java.security.SecureRandom} 为每个发布版本生成全新的 P、Q，
 * 并通过 ASM 直接注入本类的 {@code <clinit>}——因此：</p>
 * <ul>
 *   <li>密钥不再以固定常量写死在源码里，无法从其它版本或源码反推；</li>
 *   <li>P、Q 两个数组字段本身会被 ProGuard 混淆改名，读取端与初始化端同步重命名；</li>
 *   <li>攻击者若想还原字符串，必须先定位两个字段、理解 XOR 重组逻辑、再逐条解密。</li>
 * </ul>
 *
 * <p>下方 P、Q 的默认值仅供「个人测试版（buildPersonal，不做字符串加密）」编译占位，
 * 发布版（buildOfficial）构建时会被 ASM 覆盖为随机值，因此这里的具体数字无意义。</p>
 */
public final class StringCrypto {

    // 密钥源（构建期 ASM 注入随机值；开发默认值与掩码 Q 相配合）
    private static final int[] P = {
        0x2A, 0x5C, 0x7E, 0x19, 0x4B, 0x6D, 0x33, 0x1F,
        0x4E, 0x27, 0x6A, 0x0D, 0x52, 0x39, 0x7B, 0x11,
        0x3C, 0x68, 0x0F, 0x5A, 0x24, 0x71, 0x1E, 0x45,
        0x6F, 0x13, 0x2D, 0x58, 0x36, 0x09, 0x64, 0x4D
    };

    // 掩码（构建期 ASM 注入随机值）
    private static final int[] Q = {
        0x1F, 0x33, 0x6D, 0x4B, 0x19, 0x7E, 0x5C, 0x2A,
        0x11, 0x7B, 0x39, 0x52, 0x0D, 0x6A, 0x27, 0x4E,
        0x45, 0x1E, 0x71, 0x24, 0x5A, 0x0F, 0x68, 0x3C,
        0x4D, 0x64, 0x09, 0x36, 0x58, 0x2D, 0x13, 0x6F
    };

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
                int k = i & 31;
                data[i] = (byte) (data[i] ^ (P[k] ^ Q[k]));
            }
            return new String(data, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            // 解密失败兜底：返回原字符串，保证不因加密问题导致功能异常
            return enc;
        }
    }

    /**
     * 解密整型常量（构建期对 BIPUSH/SIPUSH/LDC int 加密，运行时 XOR 还原）。
     * 密钥由 P/Q 前四字节派生，与构建期 encryptInt 完全一致。
     *
     * @param enc 加密后的 int
     * @return 解密还原的 int
     */
    public static int di(int enc) {
        int k = ((P[0] ^ Q[0]) << 24) | ((P[1] ^ Q[1]) << 16) | ((P[2] ^ Q[2]) << 8) | (P[3] ^ Q[3]);
        return enc ^ k;
    }

    /**
     * 解密 long 常量。密钥 64 位由 keyInt 复用为高/低 32 位，与构建期 encryptLong 一致。
     */
    public static long dl(long enc) {
        int k = ((P[0] ^ Q[0]) << 24) | ((P[1] ^ Q[1]) << 16) | ((P[2] ^ Q[2]) << 8) | (P[3] ^ Q[3]);
        long kl = ((long) k << 32) | (k & 0xFFFFFFFFL);
        return enc ^ kl;
    }

    /**
     * 解密 float 常量。密钥清除 exponent 位（bit 23..30），确保 XOR 结果不会是 NaN，
     * 避免 {@link Float#intBitsToFloat} 规范化 NaN 导致位不精确、解密无法还原。
     */
    public static float df(float enc) {
        int k = ((P[0] ^ Q[0]) << 24) | ((P[1] ^ Q[1]) << 16) | ((P[2] ^ Q[2]) << 8) | (P[3] ^ Q[3]);
        int kf = k & ~0x7F800000;
        return Float.intBitsToFloat(Float.floatToRawIntBits(enc) ^ kf);
    }

    /**
     * 解密 double 常量。密钥清除 exponent 位（bit 52..62），避免 {@link Double#longBitsToDouble}
     * 规范化 NaN 导致位不精确，保证解密可逆。
     */
    public static double dd(double enc) {
        int k = ((P[0] ^ Q[0]) << 24) | ((P[1] ^ Q[1]) << 16) | ((P[2] ^ Q[2]) << 8) | (P[3] ^ Q[3]);
        long kl = ((long) k << 32) | (k & 0xFFFFFFFFL);
        long kd = kl & ~0x7FF0000000000000L;
        return Double.longBitsToDouble(Double.doubleToRawLongBits(enc) ^ kd);
    }
}
