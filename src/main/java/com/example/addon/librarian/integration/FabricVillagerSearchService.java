// 附魔交易所 村民搜索服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.BlockPosition;
import com.example.addon.librarian.model.VillagerTarget;
import com.example.addon.librarian.service.DebugLoggerService;
import com.example.addon.librarian.service.VillagerSearchService;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class FabricVillagerSearchService implements VillagerSearchService {
    private final DebugLoggerService logger;
    private final BooleanSupplier debugEnabled;

    public FabricVillagerSearchService(DebugLoggerService logger, BooleanSupplier debugEnabled) {
        this.logger = logger;
        this.debugEnabled = debugEnabled;
    }

    @Override
    public Optional<VillagerTarget> findNearestUnemployedVillager(int radiusBlocks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        AABB box = mc.player.getBoundingBox().inflate(radiusBlocks);
        List<Villager> candidates = mc.level.getEntitiesOfClass(Villager.class, box, this::usable);
        return candidates.stream()
            .filter(this::unemployed)
            .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(mc.player)))
            .map(this::snapshot);
    }

    @Override
    public boolean isValid(VillagerTarget target) {
        return resolve(target).filter(this::usable).isPresent();
    }

    @Override
    public boolean isUnemployed(VillagerTarget target) {
        return resolve(target).filter(this::unemployed).isPresent();
    }

    @Override
    public boolean isLibrarian(VillagerTarget target) {
        return resolve(target).filter(v -> isProfession(v, VillagerProfession.LIBRARIAN)).isPresent();
    }

    private Optional<Villager> resolve(VillagerTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return Optional.empty();
        // 先用 entity ID 快速查找
        if (mc.level.getEntity(target.entityId()) instanceof Villager villager
                && target.uuid().equals(villager.getUUID())) {
            return Optional.of(villager);
        }
        // entity ID 在 chunk 重载后可能失效，兜底用 UUID 扫描附近村民
        if (mc.player == null) return Optional.empty();
        AABB box = mc.player.getBoundingBox().inflate(64);
        return mc.level.getEntitiesOfClass(Villager.class, box,
                v -> target.uuid().equals(v.getUUID()))
            .stream().findFirst();
    }

    private boolean usable(Villager villager) {
        return villager.isAlive() && !villager.isRemoved();
    }

    private boolean unemployed(Villager villager) {
        return isProfession(villager, VillagerProfession.NONE);
    }

    // 通过 Holder 的注册表键判断职业（26.1.2：VillagerProfession.LIBRARIAN/NONE 是 ResourceKey，不能直接用 == 比较 value）
    private boolean isProfession(Villager villager, ResourceKey<VillagerProfession> key) {
        return villager.getVillagerData().profession().unwrapKey()
            .map(k -> k.equals(key))
            .orElse(false);
    }

    private VillagerTarget snapshot(Villager villager) {
        return new VillagerTarget(
            villager.getUUID(),
            villager.getId(),
            new BlockPosition(villager.getBlockX(), villager.getBlockY(), villager.getBlockZ())
        );
    }
}
