package net.minecraft.client.gui.screens.options;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.Level;

@Environment(EnvType.CLIENT)
public class WorldOptionsScreen extends Screen implements HasGamemasterPermissionReaction, HasDifficultyReaction {
	private static final Component TITLE = Component.translatable("options.worldOptions.title");
	private static final Component GAME_RULES = Component.translatable("editGamerule.inGame.button");
	private static final Tooltip GAMERULES_DISABLED_TOOLTIP = Tooltip.create(Component.translatable("editGamerule.inGame.disabled.tooltip"));
	private static final Component RESTRICTIONS = Component.translatable("restrictions_screen.button");
	private final Screen lastScreen;
	private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
	private final DifficultyButtons difficultyButtons;

	public WorldOptionsScreen(final Screen lastScreen, final Level level) {
		super(TITLE);
		this.lastScreen = lastScreen;
		this.difficultyButtons = DifficultyButtons.create(this.minecraft, level, this);
	}

	@Override
	protected void init() {
		this.layout.addToHeader(new StringWidget(TITLE, this.font), LayoutSettings::alignHorizontallyCenter);
		GridLayout content = this.layout.addToContents(new GridLayout(0, 0));
		GridLayout.RowHelper gridHelper = content.columnSpacing(8).rowSpacing(4).createRowHelper(2);
		gridHelper.addChild(this.difficultyButtons.layout());
		gridHelper.addChild(this.createGameRulesButton());
		gridHelper.addChild(this.createRestrictionsButton());
		this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).width(200).build());
		this.layout.visitWidgets(x$0 -> this.addRenderableWidget(x$0));
		this.repositionElements();
	}

	private Button createGameRulesButton() {
		Button gameRulesButton = Button.builder(GAME_RULES, button -> {
			if (this.minecraft.player != null) {
				this.minecraft.setScreen(new InWorldGameRulesScreen(this.minecraft.player.connection, result -> this.minecraft.setScreen(this), this));
			}
		}).build();
		if (this.minecraft.player == null || !this.minecraft.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
			gameRulesButton.active = false;
			gameRulesButton.setTooltip(GAMERULES_DISABLED_TOOLTIP);
		}

		return gameRulesButton;
	}

	private Button createRestrictionsButton() {
		return Button.builder(RESTRICTIONS, var1 -> {
			if (this.minecraft.player != null) {
				this.minecraft.setScreen(new RestrictionsScreen(this, this.minecraft.player.chatAbilities()));
			}
		}).build();
	}

	@Override
	protected void repositionElements() {
		this.layout.arrangeElements();
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.lastScreen);
	}

	@Override
	public void onGamemasterPermissionChanged(final boolean hasGamemasterPermission) {
		if (!hasGamemasterPermission) {
			this.minecraft.setScreen(this.lastScreen);
			if (this.minecraft.screen instanceof HasGamemasterPermissionReaction screen) {
				screen.onGamemasterPermissionChanged(hasGamemasterPermission);
			}
		}
	}

	@Override
	public void added() {
		this.difficultyButtons.refresh(this.minecraft);
	}

	@Override
	public void onDifficultyChanged() {
		this.difficultyButtons.refresh(this.minecraft);
	}
}
