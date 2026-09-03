package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public sealed interface Uniform extends AutoCloseable permits Uniform.Ubo, Uniform.Utb, Uniform.Sampler {
	default void close() {
	}

	@Environment(EnvType.CLIENT)
	public record Sampler(int location, int samplerIndex) implements Uniform {
	}

	@Environment(EnvType.CLIENT)
	public record Ubo(int blockBinding) implements Uniform {
	}

	@Environment(EnvType.CLIENT)
	public record Utb(int location, int samplerIndex, TextureFormat format, int texture) implements Uniform {
		public Utb(final int location, final int samplerIndex, final TextureFormat format) {
			this(location, samplerIndex, format, GlStateManager._genTexture());
		}

		@Override
		public void close() {
			GlStateManager._deleteTexture(this.texture);
		}
	}
}
