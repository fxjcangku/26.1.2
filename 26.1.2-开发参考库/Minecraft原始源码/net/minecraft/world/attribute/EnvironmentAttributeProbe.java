package net.minecraft.world.attribute;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class EnvironmentAttributeProbe {
	private final Map<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbes = new Reference2ObjectOpenHashMap<>();
	private final Function<EnvironmentAttribute<?>, EnvironmentAttributeProbe.ValueProbe<?>> valueProbeFactory = x$0 -> new EnvironmentAttributeProbe.ValueProbe(
		x$0
	);
	@Nullable
	private Level level;
	@Nullable
	private Vec3 position;
	private final SpatialAttributeInterpolator biomeInterpolator = new SpatialAttributeInterpolator();

	public void reset() {
		this.level = null;
		this.position = null;
		this.biomeInterpolator.clear();
		this.valueProbes.clear();
	}

	public void tick(final Level level, final Vec3 position) {
		this.level = level;
		this.position = position;
		this.valueProbes.values().removeIf(EnvironmentAttributeProbe.ValueProbe::tick);
		this.biomeInterpolator.clear();
		GaussianSampler.sample(
			position.scale(0.25),
			level.getBiomeManager()::getNoiseBiomeAtQuart,
			(weight, biome) -> this.biomeInterpolator.accumulate(weight, biome.value().getAttributes())
		);
	}

	public <Value> Value getValue(final EnvironmentAttribute<Value> attribute, final float partialTicks) {
		EnvironmentAttributeProbe.ValueProbe<Value> valueProbe = (EnvironmentAttributeProbe.ValueProbe<Value>)this.valueProbes
			.computeIfAbsent(attribute, this.valueProbeFactory);
		return valueProbe.get(attribute, partialTicks);
	}

	private class ValueProbe<Value> {
		private Value lastValue;
		@Nullable
		private Value newValue;

		public ValueProbe(final EnvironmentAttribute<Value> attribute) {
			Objects.requireNonNull(EnvironmentAttributeProbe.this);
			super();
			Value value = this.getValueFromLevel(attribute);
			this.lastValue = value;
			this.newValue = value;
		}

		private Value getValueFromLevel(final EnvironmentAttribute<Value> attribute) {
			return EnvironmentAttributeProbe.this.level != null && EnvironmentAttributeProbe.this.position != null
				? EnvironmentAttributeProbe.this.level
					.environmentAttributes()
					.getValue(attribute, EnvironmentAttributeProbe.this.position, EnvironmentAttributeProbe.this.biomeInterpolator)
				: attribute.defaultValue();
		}

		public boolean tick() {
			if (this.newValue == null) {
				return true;
			} else {
				this.lastValue = this.newValue;
				this.newValue = null;
				return false;
			}
		}

		public Value get(final EnvironmentAttribute<Value> attribute, final float partialTicks) {
			if (this.newValue == null) {
				this.newValue = this.getValueFromLevel(attribute);
			}

			return attribute.type().partialTickLerp().apply(partialTicks, this.lastValue, this.newValue);
		}
	}
}
