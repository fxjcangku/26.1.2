package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBVertexAttribBinding;
import org.lwjgl.opengl.GLCapabilities;

@Environment(EnvType.CLIENT)
public abstract class VertexArrayCache {
	public static VertexArrayCache create(final GLCapabilities capabilities, final GlDebugLabel debugLabels, final Set<String> enabledExtensions) {
		if (capabilities.GL_ARB_vertex_attrib_binding && GlDevice.USE_GL_ARB_vertex_attrib_binding) {
			enabledExtensions.add("GL_ARB_vertex_attrib_binding");
			return new VertexArrayCache.Separate(debugLabels);
		} else {
			return new VertexArrayCache.Emulated(debugLabels);
		}
	}

	public abstract void bindVertexArray(final VertexFormat format, @Nullable final GlBuffer vertexBuffer);

	@Environment(EnvType.CLIENT)
	private static class Emulated extends VertexArrayCache {
		private final Map<VertexFormat, VertexArrayCache.VertexArray> cache = new HashMap();
		private final GlDebugLabel debugLabels;

		public Emulated(final GlDebugLabel debugLabels) {
			this.debugLabels = debugLabels;
		}

		@Override
		public void bindVertexArray(final VertexFormat format, @Nullable final GlBuffer vertexBuffer) {
			VertexArrayCache.VertexArray vertexArray = (VertexArrayCache.VertexArray)this.cache.get(format);
			if (vertexArray == null) {
				int id = GlStateManager._glGenVertexArrays();
				GlStateManager._glBindVertexArray(id);
				if (vertexBuffer != null) {
					GlStateManager._glBindBuffer(34962, vertexBuffer.handle);
					setupCombinedAttributes(format, true);
				}

				VertexArrayCache.VertexArray vao = new VertexArrayCache.VertexArray(id, format, vertexBuffer);
				this.debugLabels.applyLabel(vao);
				this.cache.put(format, vao);
			} else {
				GlStateManager._glBindVertexArray(vertexArray.id);
				if (vertexBuffer != null && vertexArray.lastVertexBuffer != vertexBuffer) {
					GlStateManager._glBindBuffer(34962, vertexBuffer.handle);
					vertexArray.lastVertexBuffer = vertexBuffer;
					setupCombinedAttributes(format, false);
				}
			}
		}

		private static void setupCombinedAttributes(final VertexFormat format, final boolean enable) {
			int vertexSize = format.getVertexSize();
			List<VertexFormatElement> elements = format.getElements();

			for (int i = 0; i < elements.size(); i++) {
				VertexFormatElement element = (VertexFormatElement)elements.get(i);
				if (enable) {
					GlStateManager._enableVertexAttribArray(i);
				}

				if (!element.normalized() && element.type() != VertexFormatElement.Type.FLOAT) {
					GlStateManager._vertexAttribIPointer(i, element.count(), GlConst.toGl(element.type()), vertexSize, format.getOffset(element));
				} else {
					GlStateManager._vertexAttribPointer(i, element.count(), GlConst.toGl(element.type()), element.normalized(), vertexSize, format.getOffset(element));
				}
			}
		}
	}

	@Environment(EnvType.CLIENT)
	private static class Separate extends VertexArrayCache {
		private final Map<VertexFormat, VertexArrayCache.VertexArray> cache = new HashMap();
		private final GlDebugLabel debugLabels;
		private final boolean needsMesaWorkaround;

		public Separate(final GlDebugLabel debugLabels) {
			this.debugLabels = debugLabels;
			if ("Mesa".equals(GlStateManager._getString(7936))) {
				String version = GlStateManager._getString(7938);
				this.needsMesaWorkaround = version.contains("25.0.0") || version.contains("25.0.1") || version.contains("25.0.2");
			} else {
				this.needsMesaWorkaround = false;
			}
		}

		@Override
		public void bindVertexArray(final VertexFormat format, @Nullable final GlBuffer vertexBuffer) {
			VertexArrayCache.VertexArray vertexArray = (VertexArrayCache.VertexArray)this.cache.get(format);
			if (vertexArray != null) {
				GlStateManager._glBindVertexArray(vertexArray.id);
				if (vertexBuffer != null && vertexArray.lastVertexBuffer != vertexBuffer) {
					if (this.needsMesaWorkaround && vertexArray.lastVertexBuffer != null && vertexArray.lastVertexBuffer.handle == vertexBuffer.handle) {
						ARBVertexAttribBinding.glBindVertexBuffer(0, 0, 0L, 0);
					}

					ARBVertexAttribBinding.glBindVertexBuffer(0, vertexBuffer.handle, 0L, format.getVertexSize());
					vertexArray.lastVertexBuffer = vertexBuffer;
				}
			} else {
				int id = GlStateManager._glGenVertexArrays();
				GlStateManager._glBindVertexArray(id);
				if (vertexBuffer != null) {
					List<VertexFormatElement> elements = format.getElements();

					for (int i = 0; i < elements.size(); i++) {
						VertexFormatElement element = (VertexFormatElement)elements.get(i);
						GlStateManager._enableVertexAttribArray(i);
						if (!element.normalized() && element.type() != VertexFormatElement.Type.FLOAT) {
							ARBVertexAttribBinding.glVertexAttribIFormat(i, element.count(), GlConst.toGl(element.type()), format.getOffset(element));
						} else {
							ARBVertexAttribBinding.glVertexAttribFormat(i, element.count(), GlConst.toGl(element.type()), element.normalized(), format.getOffset(element));
						}

						ARBVertexAttribBinding.glVertexAttribBinding(i, 0);
					}
				}

				if (vertexBuffer != null) {
					ARBVertexAttribBinding.glBindVertexBuffer(0, vertexBuffer.handle, 0L, format.getVertexSize());
				}

				VertexArrayCache.VertexArray vao = new VertexArrayCache.VertexArray(id, format, vertexBuffer);
				this.debugLabels.applyLabel(vao);
				this.cache.put(format, vao);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class VertexArray {
		final int id;
		final VertexFormat format;
		@Nullable
		GlBuffer lastVertexBuffer;

		private VertexArray(final int id, final VertexFormat format, @Nullable final GlBuffer lastVertexBuffer) {
			this.id = id;
			this.format = format;
			this.lastVertexBuffer = lastVertexBuffer;
		}
	}
}
