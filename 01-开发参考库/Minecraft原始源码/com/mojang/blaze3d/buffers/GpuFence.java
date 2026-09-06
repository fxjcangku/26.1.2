package com.mojang.blaze3d.buffers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface GpuFence extends AutoCloseable {
	void close();

	boolean awaitCompletion(final long timeoutMs);
}
