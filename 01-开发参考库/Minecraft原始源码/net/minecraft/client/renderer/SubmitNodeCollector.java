package net.minecraft.client.renderer;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.feature.ParticleFeatureRenderer;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SubmitNodeCollector extends OrderedSubmitNodeCollector {
	OrderedSubmitNodeCollector order(int order);

	@Environment(EnvType.CLIENT)
	public interface CustomGeometryRenderer {
		void render(PoseStack.Pose pose, VertexConsumer buffer);
	}

	@Environment(EnvType.CLIENT)
	public interface ParticleGroupRenderer {
		boolean isEmpty();

		@Nullable
		QuadParticleRenderState.PreparedBuffers prepare(ParticleFeatureRenderer.ParticleBufferCache buffer, boolean translucent);

		void render(
			QuadParticleRenderState.PreparedBuffers buffers,
			ParticleFeatureRenderer.ParticleBufferCache bufferCache,
			RenderPass renderPass,
			TextureManager textureManager
		);
	}
}
