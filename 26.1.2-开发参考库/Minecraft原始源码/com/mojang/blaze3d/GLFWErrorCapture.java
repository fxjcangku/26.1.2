package com.mojang.blaze3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;

@Environment(EnvType.CLIENT)
public class GLFWErrorCapture implements GLFWErrorCallbackI, Iterable<GLFWErrorCapture.Error> {
	@Nullable
	private List<GLFWErrorCapture.Error> errors;

	@Override
	public void invoke(final int error, final long description) {
		if (this.errors == null) {
			this.errors = new ArrayList();
		}

		this.errors.add(new GLFWErrorCapture.Error(error, MemoryUtil.memUTF8(description)));
	}

	public Iterator<GLFWErrorCapture.Error> iterator() {
		return this.errors == null ? Collections.emptyIterator() : this.errors.iterator();
	}

	@Nullable
	public GLFWErrorCapture.Error firstError() {
		return this.errors == null ? null : (GLFWErrorCapture.Error)this.errors.getFirst();
	}

	@Environment(EnvType.CLIENT)
	public record Error(int error, String description) {
		public String toString() {
			return String.format(Locale.ROOT, "[GLFW 0x%X] %s", this.error, this.description);
		}
	}
}
