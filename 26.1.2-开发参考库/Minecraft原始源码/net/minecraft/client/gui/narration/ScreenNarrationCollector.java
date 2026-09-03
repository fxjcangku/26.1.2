package net.minecraft.client.gui.narration;

import com.google.common.collect.Maps;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ScreenNarrationCollector {
	private int generation;
	private final Map<ScreenNarrationCollector.EntryKey, ScreenNarrationCollector.NarrationEntry> entries = Maps.newTreeMap(
		Comparator.comparing(e -> e.type).thenComparing(e -> e.depth)
	);

	public void update(final Consumer<NarrationElementOutput> updater) {
		this.generation++;
		updater.accept(new ScreenNarrationCollector.Output(0));
	}

	public String collectNarrationText(final boolean force) {
		final StringBuilder result = new StringBuilder();
		Consumer<String> appender = new Consumer<String>() {
			private boolean firstEntry;

			{
				Objects.requireNonNull(ScreenNarrationCollector.this);
				this.firstEntry = true;
			}

			public void accept(final String s) {
				if (!this.firstEntry) {
					result.append(". ");
				}

				this.firstEntry = false;
				result.append(s);
			}
		};
		this.entries.forEach((k, v) -> {
			if (v.generation == this.generation && (force || !v.alreadyNarrated)) {
				v.contents.getText(appender);
				v.alreadyNarrated = true;
			}
		});
		return result.toString();
	}

	@Environment(EnvType.CLIENT)
	private record EntryKey(NarratedElementType type, int depth) {
	}

	@Environment(EnvType.CLIENT)
	private static class NarrationEntry {
		private NarrationThunk<?> contents = NarrationThunk.EMPTY;
		private int generation = -1;
		private boolean alreadyNarrated;

		public ScreenNarrationCollector.NarrationEntry update(final int generation, final NarrationThunk<?> contents) {
			if (!this.contents.equals(contents)) {
				this.contents = contents;
				this.alreadyNarrated = false;
			} else if (this.generation + 1 != generation) {
				this.alreadyNarrated = false;
			}

			this.generation = generation;
			return this;
		}
	}

	@Environment(EnvType.CLIENT)
	private class Output implements NarrationElementOutput {
		private final int depth;

		private Output(final int depth) {
			Objects.requireNonNull(ScreenNarrationCollector.this);
			super();
			this.depth = depth;
		}

		@Override
		public void add(final NarratedElementType type, final NarrationThunk<?> contents) {
			((ScreenNarrationCollector.NarrationEntry)ScreenNarrationCollector.this.entries
					.computeIfAbsent(new ScreenNarrationCollector.EntryKey(type, this.depth), k -> new ScreenNarrationCollector.NarrationEntry()))
				.update(ScreenNarrationCollector.this.generation, contents);
		}

		@Override
		public NarrationElementOutput nest() {
			return ScreenNarrationCollector.this.new Output(this.depth + 1);
		}
	}
}
