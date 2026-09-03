package net.minecraft.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;

@Environment(EnvType.CLIENT)
public class CustomFeatureRenderer {
	public void renderSolid(final SubmitNodeCollection nodeCollection, final MultiBufferSource.BufferSource bufferSource) {
		CustomFeatureRenderer.Storage storage = nodeCollection.getCustomGeometrySubmits();

		for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entry : storage.solidCustomGeometrySubmits.entrySet()) {
			VertexConsumer buffer = bufferSource.getBuffer((RenderType)entry.getKey());

			for (SubmitNodeStorage.CustomGeometrySubmit customGeometrySubmit : (List)entry.getValue()) {
				customGeometrySubmit.customGeometryRenderer().render(customGeometrySubmit.pose(), buffer);
			}
		}
	}

	public void renderTranslucent(final SubmitNodeCollection nodeCollection, final MultiBufferSource.BufferSource bufferSource) {
		CustomFeatureRenderer.Storage storage = nodeCollection.getCustomGeometrySubmits();

		for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entry : storage.translucentCustomGeometrySubmits.entrySet()) {
			VertexConsumer buffer = bufferSource.getBuffer((RenderType)entry.getKey());

			for (SubmitNodeStorage.CustomGeometrySubmit customGeometrySubmit : (List)entry.getValue()) {
				customGeometrySubmit.customGeometryRenderer().render(customGeometrySubmit.pose(), buffer);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Storage {
		private final Map<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> solidCustomGeometrySubmits = new HashMap();
		private final Map<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> translucentCustomGeometrySubmits = new HashMap();
		private final Set<RenderType> solidCustomGeometrySubmitsUsage = new ObjectOpenHashSet<>();
		private final Set<RenderType> translucentCustomGeometrySubmitsUsage = new ObjectOpenHashSet<>();

		public void add(final PoseStack poseStack, final RenderType renderType, final SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
			SubmitNodeStorage.CustomGeometrySubmit submit = new SubmitNodeStorage.CustomGeometrySubmit(poseStack.last().copy(), customGeometryRenderer);
			if (!renderType.hasBlending()) {
				((List)this.solidCustomGeometrySubmits.computeIfAbsent(renderType, rt -> new ArrayList())).add(submit);
			} else {
				((List)this.translucentCustomGeometrySubmits.computeIfAbsent(renderType, rt -> new ArrayList())).add(submit);
			}
		}

		public void clear() {
			for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entry : this.solidCustomGeometrySubmits.entrySet()) {
				if (!((List)entry.getValue()).isEmpty()) {
					this.solidCustomGeometrySubmitsUsage.add((RenderType)entry.getKey());
					((List)entry.getValue()).clear();
				}
			}

			for (Entry<RenderType, List<SubmitNodeStorage.CustomGeometrySubmit>> entryx : this.translucentCustomGeometrySubmits.entrySet()) {
				if (!((List)entryx.getValue()).isEmpty()) {
					this.translucentCustomGeometrySubmitsUsage.add((RenderType)entryx.getKey());
					((List)entryx.getValue()).clear();
				}
			}
		}

		public void endFrame() {
			this.solidCustomGeometrySubmits.keySet().removeIf(renderType -> !this.solidCustomGeometrySubmitsUsage.contains(renderType));
			this.solidCustomGeometrySubmitsUsage.clear();
			this.translucentCustomGeometrySubmits.keySet().removeIf(renderType -> !this.translucentCustomGeometrySubmitsUsage.contains(renderType));
			this.translucentCustomGeometrySubmitsUsage.clear();
		}
	}
}
