package net.minecraft.world.level.levelgen.structure.templatesystem;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class RuleProcessor extends StructureProcessor {
	public static final MapCodec<RuleProcessor> CODEC = ProcessorRule.CODEC.listOf().fieldOf("rules").xmap(RuleProcessor::new, p -> p.rules);
	private final ImmutableList<ProcessorRule> rules;

	public RuleProcessor(final List<? extends ProcessorRule> rules) {
		this.rules = ImmutableList.copyOf(rules);
	}

	@Nullable
	@Override
	public StructureTemplate.StructureBlockInfo processBlock(
		final LevelReader level,
		final BlockPos targetPosition,
		final BlockPos referencePos,
		final StructureTemplate.StructureBlockInfo originalBlockInfo,
		final StructureTemplate.StructureBlockInfo processedBlockInfo,
		final StructurePlaceSettings settings
	) {
		RandomSource random = RandomSource.create(Mth.getSeed(processedBlockInfo.pos()));
		BlockState locState = level.getBlockState(processedBlockInfo.pos());

		for (ProcessorRule rule : this.rules) {
			if (rule.test(processedBlockInfo.state(), locState, originalBlockInfo.pos(), processedBlockInfo.pos(), referencePos, random)) {
				return new StructureTemplate.StructureBlockInfo(processedBlockInfo.pos(), rule.getOutputState(), rule.getOutputTag(random, processedBlockInfo.nbt()));
			}
		}

		return processedBlockInfo;
	}

	@Override
	protected StructureProcessorType<?> getType() {
		return StructureProcessorType.RULE;
	}
}
