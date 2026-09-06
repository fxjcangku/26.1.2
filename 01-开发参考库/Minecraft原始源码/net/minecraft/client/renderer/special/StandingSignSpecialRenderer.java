package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.StandingSignRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.PlainSignBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.joml.Vector3fc;

@Environment(EnvType.CLIENT)
public class StandingSignSpecialRenderer implements NoDataSpecialModelRenderer {
	private final SpriteGetter sprites;
	private final Model.Simple model;
	private final SpriteId sprite;

	public StandingSignSpecialRenderer(final SpriteGetter sprites, final Model.Simple model, final SpriteId sprite) {
		this.sprites = sprites;
		this.model = model;
		this.sprite = sprite;
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
		StandingSignRenderer.submitSpecial(this.sprites, poseStack, submitNodeCollector, lightCoords, overlayCoords, this.model, this.sprite);
	}

	@Override
	public void getExtents(final Consumer<Vector3fc> output) {
		PoseStack poseStack = new PoseStack();
		this.model.root().getExtentsForGui(poseStack, output);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(WoodType woodType, PlainSignBlock.Attachment attachment, Optional<Identifier> texture) implements NoDataSpecialModelRenderer.Unbaked {
		public static final MapCodec<StandingSignSpecialRenderer.Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(
			i -> i.group(
					WoodType.CODEC.fieldOf("wood_type").forGetter(StandingSignSpecialRenderer.Unbaked::woodType),
					PlainSignBlock.Attachment.CODEC
						.optionalFieldOf("attachement", PlainSignBlock.Attachment.GROUND)
						.forGetter(StandingSignSpecialRenderer.Unbaked::attachment),
					Identifier.CODEC.optionalFieldOf("texture").forGetter(StandingSignSpecialRenderer.Unbaked::texture)
				)
				.apply(i, StandingSignSpecialRenderer.Unbaked::new)
		);

		public Unbaked(final WoodType woodType, final PlainSignBlock.Attachment attachment) {
			this(woodType, attachment, Optional.empty());
		}

		@Override
		public MapCodec<StandingSignSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		public StandingSignSpecialRenderer bake(final SpecialModelRenderer.BakingContext context) {
			Model.Simple model = StandingSignRenderer.createSignModel(context.entityModelSet(), this.woodType, this.attachment);
			SpriteId sprite = (SpriteId)this.texture.map(Sheets.SIGN_MAPPER::apply).orElseGet(() -> Sheets.getSignSprite(this.woodType));
			return new StandingSignSpecialRenderer(context.sprites(), model, sprite);
		}
	}
}
