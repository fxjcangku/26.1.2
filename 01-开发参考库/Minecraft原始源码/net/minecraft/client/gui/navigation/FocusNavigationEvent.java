package net.minecraft.client.gui.navigation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface FocusNavigationEvent {
	ScreenDirection getVerticalDirectionForInitialFocus();

	@Environment(EnvType.CLIENT)
	public record ArrowNavigation(ScreenDirection direction, @Nullable ScreenRectangle previousFocus) implements FocusNavigationEvent {
		public ArrowNavigation(final ScreenDirection direction) {
			this(direction, null);
		}

		@Override
		public ScreenDirection getVerticalDirectionForInitialFocus() {
			return this.direction.getAxis() == ScreenAxis.VERTICAL ? this.direction : ScreenDirection.DOWN;
		}

		public FocusNavigationEvent.ArrowNavigation with(final ScreenRectangle previousFocus) {
			return new FocusNavigationEvent.ArrowNavigation(this.direction(), previousFocus);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class InitialFocus implements FocusNavigationEvent {
		@Override
		public ScreenDirection getVerticalDirectionForInitialFocus() {
			return ScreenDirection.DOWN;
		}
	}

	@Environment(EnvType.CLIENT)
	public record TabNavigation(boolean forward) implements FocusNavigationEvent {
		@Override
		public ScreenDirection getVerticalDirectionForInitialFocus() {
			return this.forward ? ScreenDirection.DOWN : ScreenDirection.UP;
		}
	}
}
