package net.minecraft.world.level.levelgen.synth;

import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;

public class PerlinSimplexNoise {
	private final SimplexNoise[] noiseLevels;
	private final double highestFreqValueFactor;
	private final double highestFreqInputFactor;

	public PerlinSimplexNoise(final RandomSource random, final List<Integer> octaveSet) {
		this(random, new IntRBTreeSet(octaveSet));
	}

	private PerlinSimplexNoise(final RandomSource random, final IntSortedSet octaveSet) {
		if (octaveSet.isEmpty()) {
			throw new IllegalArgumentException("Need some octaves!");
		} else {
			int lowFreqOctaves = -octaveSet.firstInt();
			int highFreqOctaves = octaveSet.lastInt();
			int octaves = lowFreqOctaves + highFreqOctaves + 1;
			if (octaves < 1) {
				throw new IllegalArgumentException("Total number of octaves needs to be >= 1");
			} else {
				SimplexNoise zeroOctave = new SimplexNoise(random);
				int zeroOctaveIndex = highFreqOctaves;
				this.noiseLevels = new SimplexNoise[octaves];
				if (highFreqOctaves >= 0 && highFreqOctaves < octaves && octaveSet.contains(0)) {
					this.noiseLevels[highFreqOctaves] = zeroOctave;
				}

				for (int i = highFreqOctaves + 1; i < octaves; i++) {
					if (i >= 0 && octaveSet.contains(zeroOctaveIndex - i)) {
						this.noiseLevels[i] = new SimplexNoise(random);
					} else {
						random.consumeCount(262);
					}
				}

				if (highFreqOctaves > 0) {
					long positiveOctaveSeed = (long)(zeroOctave.getValue(zeroOctave.xo, zeroOctave.yo, zeroOctave.zo) * 9.223372E18F);
					RandomSource highFreqRandom = new WorldgenRandom(new LegacyRandomSource(positiveOctaveSeed));

					for (int ix = zeroOctaveIndex - 1; ix >= 0; ix--) {
						if (ix < octaves && octaveSet.contains(zeroOctaveIndex - ix)) {
							this.noiseLevels[ix] = new SimplexNoise(highFreqRandom);
						} else {
							highFreqRandom.consumeCount(262);
						}
					}
				}

				this.highestFreqInputFactor = Math.pow(2.0, highFreqOctaves);
				this.highestFreqValueFactor = 1.0 / (Math.pow(2.0, octaves) - 1.0);
			}
		}
	}

	public double getValue(final double x, final double y, final boolean useNoiseStart) {
		double value = 0.0;
		double factor = this.highestFreqInputFactor;
		double valueFactor = this.highestFreqValueFactor;

		for (SimplexNoise noiseLevel : this.noiseLevels) {
			if (noiseLevel != null) {
				value += noiseLevel.getValue(x * factor + (useNoiseStart ? noiseLevel.xo : 0.0), y * factor + (useNoiseStart ? noiseLevel.yo : 0.0)) * valueFactor;
			}

			factor /= 2.0;
			valueFactor *= 2.0;
		}

		return value;
	}
}
