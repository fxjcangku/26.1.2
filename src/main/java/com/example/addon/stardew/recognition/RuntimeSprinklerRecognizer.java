package com.example.addon.stardew.recognition;

import com.example.addon.stardew.model.SprinklerInfo;
import com.example.addon.stardew.model.SprinklerProfile;
import com.example.addon.stardew.model.StardewServerProfile;
import net.minecraft.core.BlockPos;

import java.util.function.Supplier;

/**
 * 运行时洒水器识别：按洒水器档案的方块 ID 匹配。
 *
 * <p>覆盖范围由 {@link SprinklerProfile} 描述，识别只负责「是不是、哪一个」。</p>
 */
public final class RuntimeSprinklerRecognizer implements SprinklerRecognizer {

    private final Supplier<StardewServerProfile> profileSupplier;

    public RuntimeSprinklerRecognizer(Supplier<StardewServerProfile> profileSupplier) {
        this.profileSupplier = profileSupplier;
    }

    @Override
    public SprinklerInfo detect(BlockPos pos) {
        StardewServerProfile profile = profileSupplier.get();
        if (profile == null) return SprinklerInfo.none();

        String blockId = RecognizerSupport.blockId(pos);
        for (SprinklerProfile sprinkler : profile.sprinklers()) {
            if (sprinkler.blockId().equals(blockId)) {
                return SprinklerInfo.known(sprinkler.sprinklerId(), sprinkler.displayName(), pos, sprinkler);
            }
        }
        return SprinklerInfo.none();
    }
}
