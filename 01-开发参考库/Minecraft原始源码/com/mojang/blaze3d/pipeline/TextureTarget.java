package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TextureTarget extends RenderTarget {
	public TextureTarget(@Nullable final String label, final int width, final int height, final boolean useDepth) {
		super(label, useDepth);
		RenderSystem.assertOnRenderThread();
		this.resize(width, height);
	}
}
