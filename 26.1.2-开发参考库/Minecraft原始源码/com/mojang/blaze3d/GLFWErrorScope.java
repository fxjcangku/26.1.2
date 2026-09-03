package com.mojang.blaze3d;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWErrorCallbackI;

@Environment(EnvType.CLIENT)
public class GLFWErrorScope implements AutoCloseable {
	@Nullable
	private final GLFWErrorCallback previousCallback;
	private final GLFWErrorCallback expectedCallback;

	public GLFWErrorScope(final GLFWErrorCallbackI callback) {
		this.expectedCallback = GLFWErrorCallback.create(callback);
		this.previousCallback = GLFW.glfwSetErrorCallback(this.expectedCallback);
	}

	public void close() {
		GLFWErrorCallback currentCallback = GLFW.glfwSetErrorCallback(this.previousCallback);
		if (currentCallback != null && currentCallback.address() == this.expectedCallback.address()) {
			currentCallback.close();
		} else {
			throw new IllegalStateException("GLFW error callback has unexpectedly changed during this scope!");
		}
	}
}
