package net.minecraft.client.resources.server;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface PackLoadFeedback {
	void reportUpdate(UUID id, PackLoadFeedback.Update result);

	void reportFinalResult(UUID id, PackLoadFeedback.FinalResult result);

	@Environment(EnvType.CLIENT)
	public static enum FinalResult {
		DECLINED,
		APPLIED,
		DISCARDED,
		DOWNLOAD_FAILED,
		ACTIVATION_FAILED;
	}

	@Environment(EnvType.CLIENT)
	public static enum Update {
		ACCEPTED,
		DOWNLOADED;
	}
}
