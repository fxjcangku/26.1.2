package net.minecraft.client.main;

import com.mojang.blaze3d.platform.DisplayData;
import java.io.File;
import java.net.Proxy;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.User;
import net.minecraft.client.resources.IndexedAssetSource;
import net.minecraft.util.StringUtil;
import org.jspecify.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GameConfig {
	public final GameConfig.UserData user;
	public final DisplayData display;
	public final GameConfig.FolderData location;
	public final GameConfig.GameData game;
	public final GameConfig.QuickPlayData quickPlay;

	public GameConfig(
		final GameConfig.UserData userData,
		final DisplayData displayData,
		final GameConfig.FolderData folderData,
		final GameConfig.GameData gameData,
		final GameConfig.QuickPlayData quickPlayData
	) {
		this.user = userData;
		this.display = displayData;
		this.location = folderData;
		this.game = gameData;
		this.quickPlay = quickPlayData;
	}

	@Environment(EnvType.CLIENT)
	public static class FolderData {
		public final File gameDirectory;
		public final File resourcePackDirectory;
		public final File assetDirectory;
		@Nullable
		public final String assetIndex;

		public FolderData(final File gameDirectory, final File resourcePackDirectory, final File assetDirectory, @Nullable final String assetIndex) {
			this.gameDirectory = gameDirectory;
			this.resourcePackDirectory = resourcePackDirectory;
			this.assetDirectory = assetDirectory;
			this.assetIndex = assetIndex;
		}

		public Path getExternalAssetSource() {
			return this.assetIndex == null ? this.assetDirectory.toPath() : IndexedAssetSource.createIndexFs(this.assetDirectory.toPath(), this.assetIndex);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class GameData {
		public final boolean demo;
		public final String launchVersion;
		public final String versionType;
		public final boolean disableMultiplayer;
		public final boolean disableChat;
		public final boolean captureTracyImages;
		public final boolean renderDebugLabels;
		public final boolean offlineDeveloperMode;

		public GameData(
			final boolean demo,
			final String launchVersion,
			final String versionType,
			final boolean disableMultiplayer,
			final boolean disableChat,
			final boolean captureTracyImages,
			final boolean renderDebugLabels,
			final boolean offlineDeveloperMode
		) {
			this.demo = demo;
			this.launchVersion = launchVersion;
			this.versionType = versionType;
			this.disableMultiplayer = disableMultiplayer;
			this.disableChat = disableChat;
			this.captureTracyImages = captureTracyImages;
			this.renderDebugLabels = renderDebugLabels;
			this.offlineDeveloperMode = offlineDeveloperMode;
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayData(@Nullable String logPath, GameConfig.QuickPlayVariant variant) {
		public boolean isEnabled() {
			return this.variant.isEnabled();
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayDisabled() implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return false;
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayMultiplayerData(String serverAddress) implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return !StringUtil.isBlank(this.serverAddress);
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlayRealmsData(String realmId) implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return !StringUtil.isBlank(this.realmId);
		}
	}

	@Environment(EnvType.CLIENT)
	public record QuickPlaySinglePlayerData(@Nullable String worldId) implements GameConfig.QuickPlayVariant {
		@Override
		public boolean isEnabled() {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	public sealed interface QuickPlayVariant
		permits GameConfig.QuickPlaySinglePlayerData,
		GameConfig.QuickPlayMultiplayerData,
		GameConfig.QuickPlayRealmsData,
		GameConfig.QuickPlayDisabled {
		GameConfig.QuickPlayVariant DISABLED = new GameConfig.QuickPlayDisabled();

		boolean isEnabled();
	}

	@Environment(EnvType.CLIENT)
	public static class UserData {
		public final User user;
		public final Proxy proxy;

		public UserData(final User user, final Proxy proxy) {
			this.user = user;
			this.proxy = proxy;
		}
	}
}
