package com.mojang.blaze3d.pipeline;

import com.mojang.blaze3d.platform.CompareOp;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record DepthStencilState(CompareOp depthTest, boolean writeDepth, float depthBiasScaleFactor, float depthBiasConstant) {
	public static final DepthStencilState DEFAULT = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);

	public DepthStencilState(final CompareOp depthTest, final boolean depthWrite) {
		this(depthTest, depthWrite, 0.0F, 0.0F);
	}
}
