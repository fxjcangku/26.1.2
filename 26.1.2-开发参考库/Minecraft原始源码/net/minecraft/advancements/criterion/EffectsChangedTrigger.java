package net.minecraft.advancements.criterion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.Validatable;
import net.minecraft.world.level.storage.loot.ValidationContextSource;
import org.jspecify.annotations.Nullable;

public class EffectsChangedTrigger extends SimpleCriterionTrigger<EffectsChangedTrigger.TriggerInstance> {
	@Override
	public Codec<EffectsChangedTrigger.TriggerInstance> codec() {
		return EffectsChangedTrigger.TriggerInstance.CODEC;
	}

	public void trigger(final ServerPlayer player, @Nullable final Entity source) {
		LootContext wrappedSource = source != null ? EntityPredicate.createContext(player, source) : null;
		this.trigger(player, t -> t.matches(player, wrappedSource));
	}

	public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<MobEffectsPredicate> effects, Optional<ContextAwarePredicate> source)
		implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<EffectsChangedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
			i -> i.group(
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(EffectsChangedTrigger.TriggerInstance::player),
					MobEffectsPredicate.CODEC.optionalFieldOf("effects").forGetter(EffectsChangedTrigger.TriggerInstance::effects),
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("source").forGetter(EffectsChangedTrigger.TriggerInstance::source)
				)
				.apply(i, EffectsChangedTrigger.TriggerInstance::new)
		);

		public static Criterion<EffectsChangedTrigger.TriggerInstance> hasEffects(final MobEffectsPredicate.Builder effects) {
			return CriteriaTriggers.EFFECTS_CHANGED.createCriterion(new EffectsChangedTrigger.TriggerInstance(Optional.empty(), effects.build(), Optional.empty()));
		}

		public static Criterion<EffectsChangedTrigger.TriggerInstance> gotEffectsFrom(final EntityPredicate.Builder source) {
			return CriteriaTriggers.EFFECTS_CHANGED
				.createCriterion(new EffectsChangedTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.of(EntityPredicate.wrap(source.build()))));
		}

		public boolean matches(final ServerPlayer player, @Nullable final LootContext source) {
			return this.effects.isPresent() && !((MobEffectsPredicate)this.effects.get()).matches((LivingEntity)player)
				? false
				: !this.source.isPresent() || source != null && ((ContextAwarePredicate)this.source.get()).matches(source);
		}

		@Override
		public void validate(final ValidationContextSource validator) {
			SimpleCriterionTrigger.SimpleInstance.super.validate(validator);
			Validatable.validate(validator.entityContext(), "source", this.source);
		}
	}
}
