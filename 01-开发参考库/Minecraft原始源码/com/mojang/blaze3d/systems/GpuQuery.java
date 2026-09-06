package com.mojang.blaze3d.systems;

import java.util.OptionalLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface GpuQuery extends AutoCloseable {
	OptionalLong getValue();

	void close();
}
