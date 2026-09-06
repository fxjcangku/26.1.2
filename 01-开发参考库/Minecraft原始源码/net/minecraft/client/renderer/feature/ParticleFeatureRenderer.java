package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Queue;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ParticleFeatureRenderer implements AutoCloseable {
	private final Queue<ParticleFeatureRenderer.ParticleBufferCache> availableBuffers = new ArrayDeque();
	private final List<ParticleFeatureRenderer.ParticleBufferCache> usedBuffers = new ArrayList();

	public void renderSolid(final SubmitNodeCollection nodeCollection) {
		this.render(nodeCollection, false);
	}

	public void renderTranslucent(final SubmitNodeCollection nodeCollection) {
		this.render(nodeCollection, true);
	}

	private void render(final SubmitNodeCollection nodeCollection, final boolean translucent) {
		if (!nodeCollection.getParticleGroupRenderers().isEmpty()) {
			GpuDevice device = RenderSystem.getDevice();
			Minecraft minecraft = Minecraft.getInstance();
			TextureManager textureManager = minecraft.getTextureManager();
			RenderTarget mainTarget = minecraft.getMainRenderTarget();
			RenderTarget particleTarget = minecraft.levelRenderer.getParticlesTarget();

			for (SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer : nodeCollection.getParticleGroupRenderers()) {
				if (!particleGroupRenderer.isEmpty()) {
					ParticleFeatureRenderer.ParticleBufferCache buffer = (ParticleFeatureRenderer.ParticleBufferCache)this.availableBuffers.poll();
					if (buffer == null) {
						buffer = new ParticleFeatureRenderer.ParticleBufferCache();
					}

					this.usedBuffers.add(buffer);
					QuadParticleRenderState.PreparedBuffers prepared = particleGroupRenderer.prepare(buffer, translucent);
					if (prepared != null) {
						boolean useParticleTarget = particleTarget != null && translucent;
						GpuTextureView colorTextureView = useParticleTarget ? particleTarget.getColorTextureView() : mainTarget.getColorTextureView();
						GpuTextureView depthTextureView = useParticleTarget ? particleTarget.getDepthTextureView() : mainTarget.getDepthTextureView();

						try (RenderPass renderPass = device.createCommandEncoder()
								.createRenderPass(
									() -> "Particles - " + (translucent ? "Translucent" : "Solid"), colorTextureView, OptionalInt.empty(), depthTextureView, OptionalDouble.empty()
								)) {
							this.prepareRenderPass(renderPass);
							particleGroupRenderer.render(prepared, buffer, renderPass, textureManager);
						}
					}
				}
			}
		}
	}

	public void endFrame() {
		for (ParticleFeatureRenderer.ParticleBufferCache usedBuffer : this.usedBuffers) {
			usedBuffer.rotate();
		}

		this.availableBuffers.addAll(this.usedBuffers);
		this.usedBuffers.clear();
	}

	private void prepareRenderPass(final RenderPass renderPass) {
		renderPass.setUniform("Projection", RenderSystem.getProjectionMatrixBuffer());
		renderPass.setUniform("Fog", RenderSystem.getShaderFog());
		renderPass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
	}

	public void close() {
		this.availableBuffers.forEach(ParticleFeatureRenderer.ParticleBufferCache::close);
	}

	@Environment(EnvType.CLIENT)
	public static class ParticleBufferCache implements AutoCloseable {
		@Nullable
		private MappableRingBuffer ringBuffer;

		public void write(final ByteBuffer byteBuffer) {
			if (this.ringBuffer == null || this.ringBuffer.size() < byteBuffer.remaining()) {
				if (this.ringBuffer != null) {
					this.ringBuffer.close();
				}

				this.ringBuffer = new MappableRingBuffer(() -> "Particle Vertices", 34, byteBuffer.remaining());
			}

			try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.ringBuffer.currentBuffer().slice(), false, true)) {
				view.data().put(byteBuffer);
			}
		}

		public GpuBuffer get() {
			if (this.ringBuffer == null) {
				throw new IllegalStateException("Can't get buffer before it's made");
			} else {
				return this.ringBuffer.currentBuffer();
			}
		}

		void rotate() {
			if (this.ringBuffer != null) {
				this.ringBuffer.rotate();
			}
		}

		public void close() {
			if (this.ringBuffer != null) {
				this.ringBuffer.close();
			}
		}
	}
}
