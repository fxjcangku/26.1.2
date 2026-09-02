package com.example.addon.autofarm.controller;

import com.example.addon.autofarm.model.CropProfile;
import com.example.addon.autofarm.model.FarmSite;
import com.example.addon.autofarm.model.SiteType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动前配置自检。
 *
 * 根据启用作物数量推断所需的专用作物箱（单/双/三），并一次性收集全部缺项，
 * 避免「配好一项下次还缺一项」的挤牙膏式报错。自检失败时不启动 Controller、
 * 不启动 Baritone、不扫描、不操作箱子。
 */
public final class FarmSelfCheck {

    /** 允许同时启用的作物数量上限 */
    public static final int MAX_CROPS = 3;

    /**
     * 执行自检。
     *
     * @param enabledCrops 启用作物集合
     * @param sites        六个点位（SiteType → FarmSite，未绑定为 null）
     * @return 缺失项清单，为空表示自检通过
     */
    public List<String> check(Set<CropProfile> enabledCrops, Map<SiteType, FarmSite> sites) {
        List<String> missing = new ArrayList<>();

        // 作物数量检测
        if (enabledCrops.isEmpty()) {
            missing.add("§e作物§f·未启用任何农作物");
            return missing;
        }
        if (enabledCrops.size() > MAX_CROPS) {
            missing.add("§c作物§f·当前版本最多支持 " + MAX_CROPS + " 种农作物（当前 " + enabledCrops.size() + " 种）");
            return missing;
        }

        // 农场范围点位（必选）
        requireSite(missing, sites, SiteType.START, "§a农场点位1");
        requireSite(missing, sites, SiteType.END, "§e农场点位2");

        // 根据启用数量确定所需作物箱
        SiteType requiredStorage = SiteType.cropStorageFor(enabledCrops.size());
        if (requiredStorage != null) {
            requireSite(missing, sites, requiredStorage, "§6" + requiredStorage.cn());
        }

        // 毒马铃薯箱始终需要（独立处理毒产物）
        requireSite(missing, sites, SiteType.POISON_STORAGE, "§d毒马铃薯箱");

        // 维度一致性检测
        checkDimension(missing, sites, SiteType.START, "§a农场点位1");
        checkDimension(missing, sites, SiteType.END, "§e农场点位2");
        if (requiredStorage != null) checkDimension(missing, sites, requiredStorage, "§6" + requiredStorage.cn());
        checkDimension(missing, sites, SiteType.POISON_STORAGE, "§d毒马铃薯箱");

        // 农场范围有效性：两个对角不能重合
        FarmSite start = sites.get(SiteType.START);
        FarmSite end = sites.get(SiteType.END);
        if (start != null && end != null
            && start.pos().equals(end.pos())) {
            missing.add("§c农田范围§f·农场点位1与农场点位2重合了");
        }

        return missing;
    }

    private void requireSite(List<String> missing, Map<SiteType, FarmSite> sites, SiteType type, String label) {
        if (sites.get(type) == null) missing.add(label + "§f·未绑定");
    }

    private void checkDimension(List<String> missing, Map<SiteType, FarmSite> sites, SiteType type, String label) {
        FarmSite site = sites.get(type);
        if (site != null && !site.inCurrentDimension()) {
            missing.add(label + "§f·不在当前维度");
        }
    }
}
