package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryStack;

@Environment(EnvType.CLIENT)
public class ProjectionMatrixBuffer implements AutoCloseable {
	GpuBuffer buffer;
	GpuBufferSlice bufferSlice;
	@Nullable
	private Projection lastUploadedProjection;
	private long projectionMatrixVersion;
	private final Matrix4f tempMatrix = new Matrix4f();

	public ProjectionMatrixBuffer(final String name) {
		this.lastUploadedProjection = null;
		this.projectionMatrixVersion = -1L;
		GpuDevice device = RenderSystem.getDevice();
		this.buffer = device.createBuffer(() -> "Camera projection matrix UBO " + name, 136, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
		this.bufferSlice = this.buffer.slice(0L, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
	}

	public GpuBufferSlice getBuffer(final Projection projection) {
		assert projection.getMatrixVersion() != -1L;

		if (this.lastUploadedProjection == projection && projection.getMatrixVersion() == this.projectionMatrixVersion) {
			return this.bufferSlice;
		} else {
			this.lastUploadedProjection = projection;
			this.projectionMatrixVersion = projection.getMatrixVersion();
			return this.writeBuffer(projection.getMatrix(this.tempMatrix));
		}
	}

	public GpuBufferSlice getBuffer(final Matrix4f projectionMatrix) {
		this.lastUploadedProjection = null;
		this.projectionMatrixVersion = -1L;
		return this.writeBuffer(projectionMatrix);
	}

	private GpuBufferSlice writeBuffer(final Matrix4f projectionMatrix) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			ByteBuffer byteBuffer = Std140Builder.onStack(stack, RenderSystem.PROJECTION_MATRIX_UBO_SIZE).putMat4f(projectionMatrix).get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.buffer.slice(), byteBuffer);
		}

		return this.bufferSlice;
	}

	public void close() {
		this.buffer.close();
	}
}
