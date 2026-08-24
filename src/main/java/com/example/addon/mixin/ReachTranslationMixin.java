package com.example.addon.mixin;

import com.example.addon.YiyiaddonTranslator;
import meteordevelopment.meteorclient.systems.modules.player.Reach;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = Reach.class, remap = false)
public abstract class ReachTranslationMixin {
    @ModifyConstant(
        method = "getWidget(Lmeteordevelopment/meteorclient/gui/GuiTheme;)Lmeteordevelopment/meteorclient/gui/widgets/WWidget;",
        constant = @Constant(stringValue = "Note: on vanilla servers you may give yourself up to 4 blocks of additional reach for specific actions - interacting with block entities (chests, furnaces, etc.) or with vehicles. This does not work on paper servers.")
    )
    private String yiyiaddon$translateReachNote(String text) {
        return YiyiaddonTranslator.translateVisible(text);
    }
}
