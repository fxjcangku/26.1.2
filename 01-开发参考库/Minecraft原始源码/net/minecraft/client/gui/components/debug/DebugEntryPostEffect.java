package net.minecraft.client.gui.components.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryPostEffect implements DebugScreenEntry {
	@Override
	public void display(
		final DebugScreenDisplayer displayer,
		@Nullable final Level serverOrClientLevel,
		@Nullable final LevelChunk clientChunk,
		@Nullable final LevelChunk serverChunk
	) {
		Minecraft minecraft = Minecraft.getInstance();
		Identifier effectId = minecraft.gameRenderer.currentPostEffect();
		if (effectId != null) {
			displayer.addLine("Post: " + effectId);
		}
	}
}
