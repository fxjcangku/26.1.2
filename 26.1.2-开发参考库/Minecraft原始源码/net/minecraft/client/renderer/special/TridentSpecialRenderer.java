package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.projectile.TridentModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Vector3f;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class TridentSpecialRenderer implements NoDataSpecialModelRenderer {
	public static final Transformation DEFAULT_TRANSFORMATION = new Transformation(null, null, new Vector3f(1.0F, -1.0F, -1.0F), null);
	private final TridentModel model;

	public TridentSpecialRenderer(final TridentModel model) {
		this.model = model;
	}

	@Override
	public void submit(
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final int lightCoords,
		final int overlayCoords,
		final boolean hasFoil,
		final int outlineColor
	) {
		submitNodeCollector.submitModelPart(
			this.model.root(), poseStack, this.model.renderType(TridentModel.TEXTURE), lightCoords, overlayCoords, null, false, hasFoil, -1, null, outlineColor
		);
	}

	@Override
	public void getExtents(final Consumer<Vector3fc> output) {
		PoseStack poseStack = new PoseStack();
		this.model.root().getExtentsForGui(poseStack, output);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
		public static final MapCodec<TridentSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new TridentSpecialRenderer.Unbaked());

		@Override
		public MapCodec<TridentSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		public TridentSpecialRenderer bake(final SpecialModelRenderer.BakingContext context) {
			return new TridentSpecialRenderer(new TridentModel(context.entityModelSet().bakeLayer(ModelLayers.TRIDENT)));
		}
	}
}
