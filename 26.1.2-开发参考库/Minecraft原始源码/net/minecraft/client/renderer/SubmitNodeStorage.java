package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class SubmitNodeStorage implements SubmitNodeCollector {
	private final Int2ObjectAVLTreeMap<SubmitNodeCollection> submitsPerOrder = new Int2ObjectAVLTreeMap<>();

	public SubmitNodeCollection order(final int order) {
		return this.submitsPerOrder.computeIfAbsent(order, ignored -> new SubmitNodeCollection(this));
	}

	@Override
	public void submitShadow(final PoseStack poseStack, final float radius, final List<EntityRenderState.ShadowPiece> pieces) {
		this.order(0).submitShadow(poseStack, radius, pieces);
	}

	@Override
	public void submitNameTag(
		final PoseStack poseStack,
		@Nullable final Vec3 nameTagAttachment,
		final int offset,
		final Component name,
		final boolean seeThrough,
		final int lightCoords,
		final double distanceToCameraSq,
		final CameraRenderState camera
	) {
		this.order(0).submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, distanceToCameraSq, camera);
	}

	@Override
	public void submitText(
		final PoseStack poseStack,
		final float x,
		final float y,
		final FormattedCharSequence string,
		final boolean dropShadow,
		final Font.DisplayMode displayMode,
		final int lightCoords,
		final int color,
		final int backgroundColor,
		final int outlineColor
	) {
		this.order(0).submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
	}

	@Override
	public void submitFlame(final PoseStack poseStack, final EntityRenderState renderState, final Quaternionf rotation) {
		this.order(0).submitFlame(poseStack, renderState, rotation);
	}

	@Override
	public void submitLeash(final PoseStack poseStack, final EntityRenderState.LeashState leashState) {
		this.order(0).submitLeash(poseStack, leashState);
	}

	@Override
	public <S> void submitModel(
		final Model<? super S> model,
		final S state,
		final PoseStack poseStack,
		final RenderType renderType,
		final int lightCoords,
		final int overlayCoords,
		final int tintedColor,
		@Nullable final TextureAtlasSprite sprite,
		final int outlineColor,
		@Nullable final ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		this.order(0).submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite, outlineColor, crumblingOverlay);
	}

	@Override
	public void submitModelPart(
		final ModelPart modelPart,
		final PoseStack poseStack,
		final RenderType renderType,
		final int lightCoords,
		final int overlayCoords,
		@Nullable final TextureAtlasSprite sprite,
		final boolean sheeted,
		final boolean hasFoil,
		final int tintedColor,
		@Nullable final ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		final int outlineColor
	) {
		this.order(0)
			.submitModelPart(modelPart, poseStack, renderType, lightCoords, overlayCoords, sprite, sheeted, hasFoil, tintedColor, crumblingOverlay, outlineColor);
	}

	@Override
	public void submitMovingBlock(final PoseStack poseStack, final MovingBlockRenderState movingBlockRenderState) {
		this.order(0).submitMovingBlock(poseStack, movingBlockRenderState);
	}

	@Override
	public void submitBlockModel(
		final PoseStack poseStack,
		final RenderType renderType,
		final List<BlockStateModelPart> modelParts,
		final int[] tintLayers,
		final int lightCoords,
		final int overlayCoords,
		final int outlineColor
	) {
		this.order(0).submitBlockModel(poseStack, renderType, modelParts, tintLayers, lightCoords, overlayCoords, outlineColor);
	}

	@Override
	public void submitBreakingBlockModel(final PoseStack poseStack, final BlockStateModel model, final long seed, final int progress) {
		this.order(0).submitBreakingBlockModel(poseStack, model, seed, progress);
	}

	@Override
	public void submitItem(
		final PoseStack poseStack,
		final ItemDisplayContext displayContext,
		final int lightCoords,
		final int overlayCoords,
		final int outlineColor,
		final int[] tintLayers,
		final List<BakedQuad> quads,
		final ItemStackRenderState.FoilType foilType
	) {
		this.order(0).submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
	}

	@Override
	public void submitCustomGeometry(
		final PoseStack poseStack, final RenderType renderType, final SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer
	) {
		this.order(0).submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
	}

	@Override
	public void submitParticleGroup(final SubmitNodeCollector.ParticleGroupRenderer particleGroupRenderer) {
		this.order(0).submitParticleGroup(particleGroupRenderer);
	}

	public void clear() {
		this.submitsPerOrder.values().forEach(SubmitNodeCollection::clear);
	}

	public void endFrame() {
		this.submitsPerOrder.values().removeIf(collection -> !collection.wasUsed());
		this.submitsPerOrder.values().forEach(SubmitNodeCollection::endFrame);
	}

	public Int2ObjectAVLTreeMap<SubmitNodeCollection> getSubmitsPerOrder() {
		return this.submitsPerOrder;
	}

	@Environment(EnvType.CLIENT)
	public record BlockModelSubmit(
		PoseStack.Pose pose, RenderType renderType, List<BlockStateModelPart> modelParts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor
	) {
	}

	@Environment(EnvType.CLIENT)
	public record BreakingBlockModelSubmit(PoseStack.Pose pose, BlockStateModel model, long seed, int progress) {
	}

	@Environment(EnvType.CLIENT)
	public record CustomGeometrySubmit(PoseStack.Pose pose, SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
	}

	@Environment(EnvType.CLIENT)
	public record FlameSubmit(PoseStack.Pose pose, EntityRenderState entityRenderState, Quaternionf rotation) {
	}

	@Environment(EnvType.CLIENT)
	public record ItemSubmit(
		PoseStack.Pose pose,
		ItemDisplayContext displayContext,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		ItemStackRenderState.FoilType foilType
	) {
	}

	@Environment(EnvType.CLIENT)
	public record LeashSubmit(Matrix4f pose, EntityRenderState.LeashState leashState) {
	}

	@Environment(EnvType.CLIENT)
	public record ModelPartSubmit(
		PoseStack.Pose pose,
		ModelPart modelPart,
		int lightCoords,
		int overlayCoords,
		@Nullable TextureAtlasSprite sprite,
		boolean sheeted,
		boolean hasFoil,
		int tintedColor,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		int outlineColor
	) {
	}

	@Environment(EnvType.CLIENT)
	public record ModelSubmit<S>(
		PoseStack.Pose pose,
		Model<? super S> model,
		S state,
		int lightCoords,
		int overlayCoords,
		int tintedColor,
		@Nullable TextureAtlasSprite sprite,
		int outlineColor,
		@Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
	}

	@Environment(EnvType.CLIENT)
	public record MovingBlockSubmit(Matrix4fc pose, MovingBlockRenderState movingBlockRenderState) {
	}

	@Environment(EnvType.CLIENT)
	public record NameTagSubmit(Matrix4fc pose, float x, float y, Component text, int lightCoords, int color, int backgroundColor, double distanceToCameraSq) {
	}

	@Environment(EnvType.CLIENT)
	public record ShadowSubmit(Matrix4fc pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
	}

	@Environment(EnvType.CLIENT)
	public record TextSubmit(
		Matrix4fc pose,
		float x,
		float y,
		FormattedCharSequence string,
		boolean dropShadow,
		Font.DisplayMode displayMode,
		int lightCoords,
		int color,
		int backgroundColor,
		int outlineColor
	) {
	}

	@Environment(EnvType.CLIENT)
	public record TranslucentModelSubmit<S>(SubmitNodeStorage.ModelSubmit<S> modelSubmit, RenderType renderType, Vector3f position) {
	}
}
