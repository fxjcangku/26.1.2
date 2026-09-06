package com.example.addon.utils;

import net.minecraft.client.Minecraft;

/**
 * 玩家会话身份工具：统一从客户端登录会话（mc.getUser()）读取身份信息，
 * 而不是服务器分配的 mc.player.getUUID()。
 *
 * 原因：盗版 / 离线服务器会把 mc.player.getUUID() 换成「离线派生 UUID」
 * （对 "OfflinePlayer:名字" 做 MD5），导致正版玩家真实身份无法关联、被误判为盗版。
 * 会话身份随登录账号而定（正版=微软 UUID，离线=离线派生 UUID），不受服务器影响。
 */
public final class YiyiaddonIdentity {

    private YiyiaddonIdentity() {}

    /** 会话身份 UUID（正版=微软 UUID，离线=离线派生 UUID）。获取失败返回 null。 */
    public static String uuid(Minecraft mc) {
        try {
            if (mc == null || mc.getUser() == null) return null;
            var id = mc.getUser().getProfileId();
            return id == null ? null : id.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 会话玩家名。获取失败返回 null。 */
    public static String name(Minecraft mc) {
        try {
            if (mc == null || mc.getUser() == null) return null;
            return mc.getUser().getName();
        } catch (Exception e) {
            return null;
        }
    }

    /** 微软账号 XUID（Xbox User ID），仅正版账户有，离线账户返回 null。 */
    public static String xuid(Minecraft mc) {
        try {
            if (mc == null || mc.getUser() == null) return null;
            var xu = mc.getUser().getXuid();
            if (xu.isPresent() && !xu.get().isEmpty()) {
                String v = xu.get().trim();
                // 仅接受纯数字 XUID；authlib-injector 未注入时会返回 ${auth_xuid} 之类占位符，需过滤
                if (v.matches("\\d{8,20}")) return v;
            }
        } catch (Exception e) {
        }
        return null;
    }

    /** 是否为正版账户（微软登录，存在 XUID）。 */
    public static boolean isPremium(Minecraft mc) {
        String xu = xuid(mc);
        return xu != null && !xu.isEmpty();
    }

    /** 是否为假玩家（离线默认名 Player+数字，如 Player166）。 */
    public static boolean isFakePlayer(String name) {
        return name == null || name.matches("^Player\\d+$");
    }
}