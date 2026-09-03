package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class DebugEntryFps implements DebugScreenEntry {
	@Override
	public void display(
		final DebugScreenDisplayer displayer,
		@Nullable final Level serverOrClientLevel,
		@Nullable final LevelChunk clientChunk,
		@Nullable final LevelChunk serverChunk
	) {
		Minecraft minecraft = Minecraft.getInstance();
		int framerateLimit = minecraft.getFramerateLimitTracker().getFramerateLimit();
		Options options = minecraft.options;
		displayer.addPriorityLine(
			String.format(Locale.ROOT, "%d fps T: %s%s", minecraft.getFps(), framerateLimit == 260 ? "inf" : framerateLimit, options.enableVsync().get() ? " vsync" : "")
		);
	}

	@Override
	public boolean isAllowed(final boolean reducedDebugInfo) {
		return true;
	}
}
