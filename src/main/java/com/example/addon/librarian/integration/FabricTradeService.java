// 附魔交易所 交易服务实现
package com.example.addon.librarian.integration;

import com.example.addon.librarian.model.TradeOfferSnapshot;
import com.example.addon.librarian.model.VillagerTarget;
import com.example.addon.librarian.service.ActionResult;
import com.example.addon.librarian.service.TradeService;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 附魔交易所 · 交易服务实现（Fabric 客户端）。
 *
 * <p>负责打开村民交易界面、扫描附魔书报价、选中并购买、取出成品。
 * 报价只能从 {@code MerchantMenu.getOffers()} 读取，客户端禁止直接读
 * {@code Villager.getOffers()}，否则会抛异常导致闪退。</p>
 */
public final class FabricTradeService implements TradeService {
    private int selectedIndex = -1;
    private boolean ownedByPlugin;

    @Override
    public ActionResult open(VillagerTarget target) {
        Minecraft mc = Minecraft.getInstance();
        if (isTradeScreenReady()) {
            ownedByPlugin = true;
            return ActionResult.success();
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null) return ActionResult.retry("游戏世界尚未就绪。");
        // 先用 entity ID 快速查，失败时用 UUID 扫描兜底（chunk 重载后 entity ID 会变）
        Villager villager = null;
        if (mc.level.getEntity(target.entityId()) instanceof Villager v
                && target.uuid().equals(v.getUUID())) {
            villager = v;
        } else {
            AABB box = mc.player.getBoundingBox().inflate(64);
            villager = mc.level.getEntitiesOfClass(Villager.class, box,
                    e -> target.uuid().equals(e.getUUID()))
                .stream().findFirst().orElse(null);
        }
        if (villager == null) {
            return ActionResult.failed("当前村民实体不存在（entity ID 和 UUID 均未找到）。");
        }
        lookAt(mc, villager.getEyePosition());
        mc.gameMode.interact(mc.player, villager, new EntityHitResult(villager), InteractionHand.MAIN_HAND);
        ownedByPlugin = true;
        return ActionResult.waiting();
    }

    @Override
    public boolean isTradeScreenReady() {
        return handler() != null;
    }

    @Override
    public Optional<TradeOfferSnapshot> readFirstEnchantedBookTrade() {
        return scanTrades().stream().findFirst();
    }

    @Override
    public List<TradeOfferSnapshot> scanTrades() {
        MerchantMenu handler = handler();
        if (handler == null) {
            return List.of();
        }
        List<TradeOfferSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < handler.getOffers().size(); index++) {
            MerchantOffer offer = handler.getOffers().get(index);
            snapshot(index, offer, handler.containerId).ifPresent(snapshots::add);
        }
        return List.copyOf(snapshots);
    }

    @Override
    public ActionResult select(TradeOfferSnapshot offer) {
        Minecraft mc = Minecraft.getInstance();
        MerchantMenu handler = handler();
        if (handler == null || mc.getConnection() == null) return ActionResult.retry("交易界面尚未同步。");
        if (!sameOffer(handler, offer)) return ActionResult.failed("交易报价身份已变化。");
        mc.getConnection().send(new ServerboundSelectTradePacket(offer.tradeIndex()));
        selectedIndex = offer.tradeIndex();
        return ActionResult.success();
    }

    @Override
    public boolean isSelectedTradeSynchronized(TradeOfferSnapshot offer) {
        MerchantMenu handler = handler();
        return handler != null && selectedIndex == offer.tradeIndex() && sameOffer(handler, offer) && !handler.getSlot(2).getItem().isEmpty();
    }

    @Override
    public ActionResult takeOutput() {
        Minecraft mc = Minecraft.getInstance();
        MerchantMenu handler = handler();
        if (mc.player == null || mc.gameMode == null || handler == null) return ActionResult.retry("交易界面不可用。");
        if (handler.getSlot(2).getItem().isEmpty()) return ActionResult.waiting();
        mc.gameMode.handleContainerInput(handler.containerId, 2, 0, ContainerInput.QUICK_MOVE, mc.player);
        return ActionResult.success();
    }

    @Override
    public void close() {
        Minecraft mc = Minecraft.getInstance();
        selectedIndex = -1;
        if (ownedByPlugin && mc.player != null && isTradeScreenReady()) mc.player.closeContainer();
        ownedByPlugin = false;
    }

    private Optional<TradeOfferSnapshot> snapshot(int index, MerchantOffer offer, int syncId) {
        ItemStack output = offer.getResult();
        if (!output.is(Items.ENCHANTED_BOOK)) return Optional.empty();
        ItemEnchantments enchantments = output.get(DataComponents.STORED_ENCHANTMENTS);
        if (enchantments == null) return Optional.empty();
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            String id = holder.unwrapKey().map(key -> key.identifier().toString()).orElse(null);
            if (id == null) continue;
            int level = entry.getIntValue();
            int maxLevel = holder.value().getMaxLevel();
            ItemStack first = offer.getBaseCostA();
            ItemStack second = offer.getCostB();
            return Optional.of(new TradeOfferSnapshot(
                index,
                Integer.toString(syncId),
                "minecraft:enchanted_book",
                output.getCount(),
                id,
                level,
                maxLevel,
                first.getCount(),
                second.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(second.getItem()).toString(),
                second.isEmpty() ? 0 : second.getCount(),
                !offer.isOutOfStock(),
                false
            ));
        }
        return Optional.empty();
    }

    private boolean sameOffer(MerchantMenu handler, TradeOfferSnapshot snapshot) {
        if (!Integer.toString(handler.containerId).equals(snapshot.synchronizationId())) return false;
        if (snapshot.tradeIndex() < 0 || snapshot.tradeIndex() >= handler.getOffers().size()) return false;
        return snapshot(snapshot.tradeIndex(), handler.getOffers().get(snapshot.tradeIndex()), handler.containerId)
            .map(snapshot::equals)
            .orElse(false);
    }

    private MerchantMenu handler() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return mc.player.containerMenu instanceof MerchantMenu menu ? menu : null;
    }

    /** 强制玩家视角朝向目标坐标，并同步给服务端 */
    private void lookAt(Minecraft mc, Vec3 target) {
        if (mc.player == null || mc.getConnection() == null) return;
        Vec3 eye = mc.player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, hDist)));
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.getConnection().send(
            new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision
            )
        );
    }
}
