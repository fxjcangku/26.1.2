package net.minecraft.client.renderer;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.jtracy.TracyClient;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.CrashReportDetail;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.font.ActiveArea;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.GuiBannerResultRenderer;
import net.minecraft.client.gui.render.pip.GuiBookModelRenderer;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.gui.render.pip.GuiProfilerChartRenderer;
import net.minecraft.client.gui.render.pip.GuiSignRenderer;
import net.minecraft.client.gui.render.pip.GuiSkinRenderer;
import net.minecraft.client.gui.screens.debug.DebugOptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.WindowRenderState;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.waypoints.TrackedWaypoint;
import org.apache.commons.io.IOUtils;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GameRenderer implements AutoCloseable, TrackedWaypoint.Projector {
	private static final Identifier BLUR_POST_CHAIN_ID = Identifier.withDefaultNamespace("blur");
	public static final int MAX_BLUR_RADIUS = 10;
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final float PROJECTION_3D_HUD_Z_FAR = 100.0F;
	private static final float PORTAL_SPINNING_SPEED = 20.0F;
	private static final float NAUSEA_SPINNING_SPEED = 7.0F;
	private final Minecraft minecraft;
	private final GameRenderState gameRenderState = new GameRenderState();
	private final RandomSource random = RandomSource.create();
	public final ItemInHandRenderer itemInHandRenderer;
	private final ScreenEffectRenderer screenEffectRenderer;
	private final RenderBuffers renderBuffers;
	private float spinningEffectTime;
	private float spinningEffectSpeed;
	private float bossOverlayWorldDarkening;
	private float bossOverlayWorldDarkeningO;
	private boolean renderBlockOutline = true;
	private long lastScreenshotAttempt;
	private boolean hasWorldScreenshot;
	private final Lightmap lightmap = new Lightmap();
	private final LightmapRenderStateExtractor lightmapRenderStateExtractor;
	private final UiLightmap uiLightmap = new UiLightmap();
	private boolean useUiLightmap;
	private final OverlayTexture overlayTexture = new OverlayTexture();
	protected final Panorama panorama = new Panorama();
	private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
	private final FogRenderer fogRenderer = new FogRenderer();
	private final GuiRenderer guiRenderer;
	private final SubmitNodeStorage submitNodeStorage;
	private final FeatureRenderDispatcher featureRenderDispatcher;
	@Nullable
	private Identifier postEffectId;
	private boolean effectActive;
	private final Camera mainCamera = new Camera();
	private final Projection hudProjection = new Projection();
	private final Lighting lighting = new Lighting();
	private final GlobalSettingsUniform globalSettingsUniform = new GlobalSettingsUniform();
	private final ProjectionMatrixBuffer levelProjectionMatrixBuffer = new ProjectionMatrixBuffer("level");
	private final ProjectionMatrixBuffer hud3dProjectionMatrixBuffer = new ProjectionMatrixBuffer("3d hud");

	public GameRenderer(final Minecraft minecraft, final ItemInHandRenderer itemInHandRenderer, final RenderBuffers renderBuffers, final ModelManager modelManager) {
		this.minecraft = minecraft;
		this.itemInHandRenderer = itemInHandRenderer;
		this.lightmapRenderStateExtractor = new LightmapRenderStateExtractor(this, minecraft);
		this.renderBuffers = renderBuffers;
		MultiBufferSource.BufferSource bufferSource = renderBuffers.bufferSource();
		AtlasManager atlasManager = minecraft.getAtlasManager();
		this.submitNodeStorage = new SubmitNodeStorage();
		this.featureRenderDispatcher = new FeatureRenderDispatcher(
			this.submitNodeStorage,
			modelManager,
			bufferSource,
			atlasManager,
			renderBuffers.outlineBufferSource(),
			renderBuffers.crumblingBufferSource(),
			minecraft.font,
			this.gameRenderState
		);
		this.guiRenderer = new GuiRenderer(
			this.gameRenderState.guiRenderState,
			bufferSource,
			this.submitNodeStorage,
			this.featureRenderDispatcher,
			List.of(
				new GuiEntityRenderer(bufferSource, minecraft.getEntityRenderDispatcher()),
				new GuiSkinRenderer(bufferSource),
				new GuiBookModelRenderer(bufferSource),
				new GuiBannerResultRenderer(bufferSource, atlasManager),
				new GuiSignRenderer(bufferSource, atlasManager),
				new GuiProfilerChartRenderer(bufferSource)
			)
		);
		this.screenEffectRenderer = new ScreenEffectRenderer(minecraft, atlasManager, bufferSource);
	}

	public void close() {
		this.globalSettingsUniform.close();
		this.lightmap.close();
		this.overlayTexture.close();
		this.uiLightmap.close();
		this.resourcePool.close();
		this.guiRenderer.close();
		this.levelProjectionMatrixBuffer.close();
		this.hud3dProjectionMatrixBuffer.close();
		this.lighting.close();
		this.fogRenderer.close();
		this.featureRenderDispatcher.close();
	}

	public SubmitNodeStorage getSubmitNodeStorage() {
		return this.submitNodeStorage;
	}

	public FeatureRenderDispatcher getFeatureRenderDispatcher() {
		return this.featureRenderDispatcher;
	}

	public GameRenderState getGameRenderState() {
		return this.gameRenderState;
	}

	public void setRenderBlockOutline(final boolean renderBlockOutline) {
		this.renderBlockOutline = renderBlockOutline;
	}

	public void clearPostEffect() {
		this.postEffectId = null;
		this.effectActive = false;
	}

	public void togglePostEffect() {
		this.effectActive = !this.effectActive;
	}

	public void checkEntityPostEffect(@Nullable final Entity cameraEntity) {
		switch (cameraEntity) {
			case Creeper ignored:
				this.setPostEffect(Identifier.withDefaultNamespace("creeper"));
				break;
			case Spider ignoredx:
				this.setPostEffect(Identifier.withDefaultNamespace("spider"));
				break;
			case EnderMan ignoredxx:
				this.setPostEffect(Identifier.withDefaultNamespace("invert"));
				break;
			case null:
			default:
				this.clearPostEffect();
		}
	}

	private void setPostEffect(final Identifier id) {
		this.postEffectId = id;
		this.effectActive = true;
	}

	public void processBlurEffect() {
		PostChain postChain = this.minecraft.getShaderManager().getPostChain(BLUR_POST_CHAIN_ID, LevelTargetBundle.MAIN_TARGETS);
		if (postChain != null) {
			postChain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
		}
	}

	public void preloadUiShader(final ResourceProvider resourceProvider) {
		GpuDevice device = RenderSystem.getDevice();
		ShaderSource shaderSource = (id, type) -> {
			Identifier location = type.idConverter().idToFile(id);

			try {
				Reader reader = resourceProvider.getResourceOrThrow(location).openAsReader();

				String t$;
				try {
					t$ = IOUtils.toString(reader);
				} catch (Throwable var8) {
					if (reader != null) {
						try {
							reader.close();
						} catch (Throwable var7) {
							var8.addSuppressed(var7);
						}
					}

					throw var8;
				}

				if (reader != null) {
					reader.close();
				}

				return t$;
			} catch (IOException var9) {
				LOGGER.error("Coudln't preload {} shader {}: {}", type, id, var9);
				return null;
			}
		};
		device.precompilePipeline(RenderPipelines.GUI, shaderSource);
		device.precompilePipeline(RenderPipelines.GUI_TEXTURED, shaderSource);
		if (TracyClient.isAvailable()) {
			device.precompilePipeline(RenderPipelines.TRACY_BLIT, shaderSource);
		}
	}

	public void tick() {
		this.lightmapRenderStateExtractor.tick();
		LocalPlayer player = this.minecraft.player;
		if (this.mainCamera.entity() == null) {
			this.mainCamera.setEntity(player);
		}

		this.mainCamera.tick();
		this.itemInHandRenderer.tick();
		float portalIntensity = player.portalEffectIntensity;
		float nauseaIntensity = player.getEffectBlendFactor(MobEffects.NAUSEA, 1.0F);
		if (!(portalIntensity > 0.0F) && !(nauseaIntensity > 0.0F)) {
			this.spinningEffectSpeed = 0.0F;
		} else {
			this.spinningEffectSpeed = (portalIntensity * 20.0F + nauseaIntensity * 7.0F) / (portalIntensity + nauseaIntensity);
			this.spinningEffectTime = this.spinningEffectTime + this.spinningEffectSpeed;
		}

		if (this.minecraft.level.tickRateManager().runsNormally()) {
			this.bossOverlayWorldDarkeningO = this.bossOverlayWorldDarkening;
			if (this.minecraft.gui.getBossOverlay().shouldDarkenScreen()) {
				this.bossOverlayWorldDarkening += 0.05F;
				if (this.bossOverlayWorldDarkening > 1.0F) {
					this.bossOverlayWorldDarkening = 1.0F;
				}
			} else if (this.bossOverlayWorldDarkening > 0.0F) {
				this.bossOverlayWorldDarkening -= 0.0125F;
			}

			this.screenEffectRenderer.tick();
			ProfilerFiller profiler = Profiler.get();
			profiler.push("levelRenderer");
			this.minecraft.levelRenderer.tick(this.mainCamera);
			profiler.pop();
		}
	}

	@Nullable
	public Identifier currentPostEffect() {
		return this.postEffectId;
	}

	public void resize(final int width, final int height) {
		this.resourcePool.clear();
		RenderTarget mainRenderTarget = this.minecraft.getMainRenderTarget();
		mainRenderTarget.resize(width, height);
		this.minecraft.levelRenderer.resize(width, height);
	}

	private void bobHurt(final CameraRenderState cameraState, final PoseStack poseStack) {
		if (cameraState.entityRenderState.isLiving) {
			float hurt = cameraState.entityRenderState.hurtTime;
			if (cameraState.entityRenderState.isDeadOrDying) {
				float duration = Math.min(cameraState.entityRenderState.deathTime, 20.0F);
				poseStack.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (duration + 200.0F)));
			}

			if (hurt < 0.0F) {
				return;
			}

			hurt /= cameraState.entityRenderState.hurtDuration;
			hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
			float rr = cameraState.entityRenderState.hurtDir;
			poseStack.mulPose(Axis.YP.rotationDegrees(-rr));
			float tiltAmount = (float)(-hurt * 14.0 * this.gameRenderState.optionsRenderState.damageTiltStrength);
			poseStack.mulPose(Axis.ZP.rotationDegrees(tiltAmount));
			poseStack.mulPose(Axis.YP.rotationDegrees(rr));
		}
	}

	private void bobView(final CameraRenderState cameraState, final PoseStack poseStack) {
		if (cameraState.entityRenderState.isPlayer) {
			float backwardsInterpolatedWalkDistance = cameraState.entityRenderState.backwardsInterpolatedWalkDistance;
			float bob = cameraState.entityRenderState.bob;
			poseStack.translate(
				Mth.sin(backwardsInterpolatedWalkDistance * (float) Math.PI) * bob * 0.5F,
				-Math.abs(Mth.cos(backwardsInterpolatedWalkDistance * (float) Math.PI) * bob),
				0.0F
			);
			poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(backwardsInterpolatedWalkDistance * (float) Math.PI) * bob * 3.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(backwardsInterpolatedWalkDistance * (float) Math.PI - 0.2F) * bob) * 5.0F));
		}
	}

	private void renderItemInHand(final CameraRenderState cameraState, final float deltaPartialTick, final Matrix4fc modelViewMatrix) {
		if (!cameraState.isPanoramicMode) {
			this.featureRenderDispatcher.renderAllFeatures();
			this.renderBuffers.bufferSource().endBatch();
			PoseStack poseStack = new PoseStack();
			poseStack.pushPose();
			poseStack.mulPose(modelViewMatrix.invert(new Matrix4f()));
			Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
			modelViewStack.pushMatrix().mul(modelViewMatrix);
			this.bobHurt(cameraState, poseStack);
			if (this.gameRenderState.optionsRenderState.bobView) {
				this.bobView(cameraState, poseStack);
			}

			if (this.gameRenderState.optionsRenderState.cameraType.isFirstPerson()
				&& !cameraState.entityRenderState.isSleeping
				&& !this.gameRenderState.optionsRenderState.hideGui
				&& this.minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
				this.itemInHandRenderer
					.renderHandsWithItems(
						deltaPartialTick,
						poseStack,
						this.minecraft.gameRenderer.getSubmitNodeStorage(),
						this.minecraft.player,
						this.minecraft.getEntityRenderDispatcher().getPackedLightCoords(this.minecraft.player, deltaPartialTick)
					);
			}

			modelViewStack.popMatrix();
			poseStack.popPose();
		}
	}

	public static float getNightVisionScale(final LivingEntity camera, final float a) {
		MobEffectInstance nightVision = camera.getEffect(MobEffects.NIGHT_VISION);
		return !nightVision.endsWithin(200) ? 1.0F : 0.7F + Mth.sin((nightVision.getDuration() - a) * (float) Math.PI * 0.2F) * 0.3F;
	}

	public void update(final DeltaTracker deltaTracker, final boolean advanceGameTime) {
		ProfilerFiller profiler = Profiler.get();
		profiler.push("camera");
		this.mainCamera.update(deltaTracker);
		profiler.pop();
		boolean resourcesLoaded = this.minecraft.isGameLoadFinished();
		boolean shouldRenderLevel = resourcesLoaded && advanceGameTime && this.minecraft.level != null;
		if (shouldRenderLevel) {
			this.minecraft.levelRenderer.update(this.mainCamera);
		}
	}

	public void extract(final DeltaTracker deltaTracker, final boolean advanceGameTime) {
		boolean resourcesLoaded = this.minecraft.isGameLoadFinished();
		boolean shouldRenderLevel = resourcesLoaded && advanceGameTime && this.minecraft.level != null;
		float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
		this.extractWindow();
		this.extractOptions();
		if (shouldRenderLevel) {
			this.lightmapRenderStateExtractor.extract(this.gameRenderState.lightmapRenderState, 1.0F);
			float cameraEntityPartialTicks = this.mainCamera.getCameraEntityPartialTicks(deltaTracker);
			this.extractCamera(deltaTracker, worldPartialTicks, cameraEntityPartialTicks);
			this.minecraft.levelRenderer.extractLevel(deltaTracker, this.mainCamera, worldPartialTicks);
		}

		this.extractGui(deltaTracker, shouldRenderLevel, resourcesLoaded);
	}

	public void render(final DeltaTracker deltaTracker, final boolean advanceGameTime) {
		ProfilerFiller profiler = Profiler.get();
		profiler.push("render");
		if (this.gameRenderState.windowRenderState.isResized) {
			this.resize(this.gameRenderState.windowRenderState.width, this.gameRenderState.windowRenderState.height);
		}

		RenderTarget mainRenderTarget = this.minecraft.getMainRenderTarget();
		RenderSystem.getDevice()
			.createCommandEncoder()
			.clearColorAndDepthTextures(
				mainRenderTarget.getColorTexture(), this.gameRenderState.guiRenderState.clearColorOverride, mainRenderTarget.getDepthTexture(), 1.0
			);
		boolean resourcesLoaded = this.minecraft.isGameLoadFinished();
		boolean shouldRenderLevel = resourcesLoaded && advanceGameTime && this.minecraft.level != null;
		this.globalSettingsUniform
			.update(
				this.gameRenderState.windowRenderState.width,
				this.gameRenderState.windowRenderState.height,
				this.gameRenderState.optionsRenderState.glintStrength,
				this.minecraft.level == null ? 0L : this.minecraft.level.getGameTime(),
				deltaTracker,
				this.gameRenderState.optionsRenderState.menuBackgroundBlurriness,
				this.gameRenderState.levelRenderState.cameraRenderState.pos,
				this.gameRenderState.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS
			);
		if (shouldRenderLevel) {
			this.lightmap.render(this.gameRenderState.lightmapRenderState);
			profiler.push("world");
			this.renderLevel(deltaTracker);
			this.tryTakeScreenshotIfNeeded();
			this.minecraft.levelRenderer.doEntityOutline();
			if (this.postEffectId != null && this.effectActive) {
				PostChain postChain = this.minecraft.getShaderManager().getPostChain(this.postEffectId, LevelTargetBundle.MAIN_TARGETS);
				if (postChain != null) {
					postChain.process(this.minecraft.getMainRenderTarget(), this.resourcePool);
				}
			}

			profiler.pop();
		}

		this.fogRenderer.endFrame();
		RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(mainRenderTarget.getDepthTexture(), 1.0);
		this.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
		this.useUiLightmap = true;
		profiler.push("gui");
		this.guiRenderer.render(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
		this.guiRenderer.endFrame();
		profiler.pop();
		this.useUiLightmap = false;
		this.submitNodeStorage.endFrame();
		this.featureRenderDispatcher.endFrame();
		this.resourcePool.endFrame();
		profiler.pop();
	}

	private void extractGui(final DeltaTracker deltaTracker, final boolean shouldRenderLevel, final boolean resourcesLoaded) {
		ProfilerFiller profiler = Profiler.get();
		int xMouse = (int)this.minecraft.mouseHandler.getScaledXPos(this.minecraft.getWindow());
		int yMouse = (int)this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow());
		profiler.push("gui");
		this.gameRenderState.guiRenderState.reset();
		GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(this.minecraft, this.gameRenderState.guiRenderState, xMouse, yMouse);
		if (shouldRenderLevel) {
			profiler.push("inGameGui");
			this.minecraft.gui.extractRenderState(graphics, deltaTracker);
			profiler.pop();
		}

		if (this.minecraft.getOverlay() != null) {
			profiler.push("overlay");

			try {
				this.minecraft.getOverlay().extractRenderState(graphics, xMouse, yMouse, deltaTracker.getGameTimeDeltaTicks());
			} catch (Throwable var13) {
				CrashReport report = CrashReport.forThrowable(var13, "Extracting overlay render state");
				CrashReportCategory category = report.addCategory("Overlay details");
				category.setDetail("Overlay name", (CrashReportDetail<String>)(() -> this.minecraft.getOverlay().getClass().getCanonicalName()));
				throw new ReportedException(report);
			}

			profiler.pop();
		} else if (resourcesLoaded && this.minecraft.screen != null) {
			profiler.push("screen");

			try {
				this.minecraft.screen.extractRenderStateWithTooltipAndSubtitles(graphics, xMouse, yMouse, deltaTracker.getGameTimeDeltaTicks());
			} catch (Throwable var12) {
				CrashReport report = CrashReport.forThrowable(var12, "Rendering screen");
				CrashReportCategory category = report.addCategory("Screen render details");
				category.setDetail("Screen name", (CrashReportDetail<String>)(() -> this.minecraft.screen.getClass().getCanonicalName()));
				this.minecraft.mouseHandler.fillMousePositionDetails(category, this.minecraft.getWindow());
				throw new ReportedException(report);
			}

			if (SharedConstants.DEBUG_CURSOR_POS) {
				this.minecraft.mouseHandler.drawDebugMouseInfo(this.minecraft.font, graphics);
			}

			try {
				if (this.minecraft.screen != null) {
					this.minecraft.screen.handleDelayedNarration();
				}
			} catch (Throwable var15) {
				CrashReport report = CrashReport.forThrowable(var15, "Narrating screen");
				CrashReportCategory category = report.addCategory("Screen details");
				category.setDetail("Screen name", (CrashReportDetail<String>)(() -> this.minecraft.screen.getClass().getCanonicalName()));
				throw new ReportedException(report);
			}

			profiler.pop();
		}

		if (shouldRenderLevel) {
			this.minecraft.gui.extractSavingIndicator(graphics, deltaTracker);
		}

		if (resourcesLoaded) {
			try (Zone ignored = profiler.zone("toasts")) {
				this.minecraft.getToastManager().extractRenderState(graphics);
			}
		}

		if (!(this.minecraft.screen instanceof DebugOptionsScreen)) {
			this.minecraft.gui.extractDebugOverlay(graphics);
		}

		this.minecraft.gui.extractDeferredSubtitles();
		if (SharedConstants.DEBUG_ACTIVE_TEXT_AREAS) {
			this.renderActiveTextDebug();
		}

		profiler.pop();
		graphics.applyCursor(this.minecraft.getWindow());
	}

	private void renderActiveTextDebug() {
		GuiRenderState guiRenderState = this.gameRenderState.guiRenderState;
		guiRenderState.nextStratum();
		guiRenderState.forEachText(
			text -> text.ensurePrepared()
				.visit(
					new Font.GlyphVisitor() {
						private int index;

						{
							Objects.requireNonNull(GameRenderer.this);
						}

						@Override
						public void acceptGlyph(final TextRenderable.Styled glyph) {
							this.renderDebugMarkers(glyph, false);
						}

						@Override
						public void acceptEmptyArea(final EmptyArea empty) {
							this.renderDebugMarkers(empty, true);
						}

						private void renderDebugMarkers(final ActiveArea glyph, final boolean isEmpty) {
							int intensity = (isEmpty ? 128 : 255) - (this.index++ & 1) * 64;
							Style style = glyph.style();
							int red = style.getClickEvent() != null ? intensity : 0;
							int green = style.getHoverEvent() != null ? intensity : 0;
							int blue = red != 0 && green != 0 ? 0 : intensity;
							int color = ARGB.color(128, red, green, blue);
							guiRenderState.addGuiElement(
								new ColoredRectangleRenderState(
									RenderPipelines.GUI,
									TextureSetup.noTexture(),
									text.pose,
									(int)glyph.activeLeft(),
									(int)glyph.activeTop(),
									(int)glyph.activeRight(),
									(int)glyph.activeBottom(),
									color,
									color,
									text.scissor
								)
							);
						}
					}
				)
		);
	}

	private void tryTakeScreenshotIfNeeded() {
		if (!this.hasWorldScreenshot && this.minecraft.isLocalServer()) {
			long time = Util.getMillis();
			if (time - this.lastScreenshotAttempt >= 1000L) {
				this.lastScreenshotAttempt = time;
				IntegratedServer server = this.minecraft.getSingleplayerServer();
				if (server != null && !server.isStopped()) {
					server.getWorldScreenshotFile().ifPresent(path -> {
						if (Files.isRegularFile(path, new LinkOption[0])) {
							this.hasWorldScreenshot = true;
						} else {
							this.takeAutoScreenshot(path);
						}
					});
				}
			}
		}
	}

	private void takeAutoScreenshot(final Path screenshotFile) {
		if (this.minecraft.levelRenderer.countRenderedSections() > 10 && this.minecraft.levelRenderer.hasRenderedAllSections()) {
			Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget(), screenshot -> Util.ioPool().execute(() -> {
				int width = screenshot.getWidth();
				int height = screenshot.getHeight();
				int x = 0;
				int y = 0;
				if (width > height) {
					x = (width - height) / 2;
					width = height;
				} else {
					y = (height - width) / 2;
					height = width;
				}

				try (NativeImage scaled = new NativeImage(64, 64, false)) {
					screenshot.resizeSubRectTo(x, y, width, height, scaled);
					scaled.writeToFile(screenshotFile);
				} catch (IOException var16) {
					LOGGER.warn("Couldn't save auto screenshot", (Throwable)var16);
				} finally {
					screenshot.close();
				}
			}));
		}
	}

	private boolean shouldRenderBlockOutline() {
		if (!this.renderBlockOutline) {
			return false;
		} else {
			Entity cameraEntity = this.minecraft.getCameraEntity();
			boolean renderOutline = cameraEntity instanceof Player && !this.minecraft.options.hideGui;
			if (renderOutline && !((Player)cameraEntity).getAbilities().mayBuild) {
				ItemStack itemStack = ((LivingEntity)cameraEntity).getMainHandItem();
				HitResult hitResult = this.minecraft.hitResult;
				if (hitResult != null && hitResult.getType() == HitResult.Type.BLOCK) {
					BlockPos pos = ((BlockHitResult)hitResult).getBlockPos();
					BlockState blockState = this.minecraft.level.getBlockState(pos);
					if (this.minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR) {
						renderOutline = blockState.getMenuProvider(this.minecraft.level, pos) != null;
					} else {
						BlockInWorld blockInWorld = new BlockInWorld(this.minecraft.level, pos, false);
						Registry<Block> blockRegistry = this.minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
						renderOutline = !itemStack.isEmpty() && (itemStack.canBreakBlockInAdventureMode(blockInWorld) || itemStack.canPlaceOnBlockInAdventureMode(blockInWorld));
					}
				}
			}

			return renderOutline;
		}
	}

	public void renderLevel(final DeltaTracker deltaTracker) {
		float worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
		float cameraEntityPartialTicks = this.mainCamera.getCameraEntityPartialTicks(deltaTracker);
		LocalPlayer player = this.minecraft.player;
		ProfilerFiller profiler = Profiler.get();
		boolean renderOutline = this.shouldRenderBlockOutline();
		OptionsRenderState optionsState = this.gameRenderState.optionsRenderState;
		CameraRenderState cameraState = this.gameRenderState.levelRenderState.cameraRenderState;
		Matrix4fc modelViewMatrix = cameraState.viewRotationMatrix;
		profiler.push("matrices");
		Matrix4f projectionMatrix = new Matrix4f(cameraState.projectionMatrix);
		PoseStack bobStack = new PoseStack();
		this.bobHurt(cameraState, bobStack);
		if (optionsState.bobView) {
			this.bobView(cameraState, bobStack);
		}

		projectionMatrix.mul(bobStack.last().pose());
		float screenEffectScale = optionsState.screenEffectScale;
		float portalIntensity = Mth.lerp(worldPartialTicks, player.oPortalEffectIntensity, player.portalEffectIntensity);
		float nauseaIntensity = player.getEffectBlendFactor(MobEffects.NAUSEA, worldPartialTicks);
		float spinningEffectIntensity = Math.max(portalIntensity, nauseaIntensity) * (screenEffectScale * screenEffectScale);
		if (spinningEffectIntensity > 0.0F) {
			float skew = 5.0F / (spinningEffectIntensity * spinningEffectIntensity + 5.0F) - spinningEffectIntensity * 0.04F;
			skew *= skew;
			Vector3f axis = new Vector3f(0.0F, Mth.SQRT_OF_TWO / 2.0F, Mth.SQRT_OF_TWO / 2.0F);
			float angle = (this.spinningEffectTime + worldPartialTicks * this.spinningEffectSpeed) * (float) (Math.PI / 180.0);
			projectionMatrix.rotate(angle, axis);
			projectionMatrix.scale(1.0F / skew, 1.0F, 1.0F);
			projectionMatrix.rotate(-angle, axis);
		}

		RenderSystem.setProjectionMatrix(this.levelProjectionMatrixBuffer.getBuffer(projectionMatrix), ProjectionType.PERSPECTIVE);
		profiler.popPush("fog");
		this.fogRenderer.updateBuffer(cameraState.fogData);
		GpuBufferSlice terrainFog = this.fogRenderer.getBuffer(FogRenderer.FogMode.WORLD);
		profiler.popPush("level");
		boolean shouldCreateBossFog = this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
		this.minecraft
			.levelRenderer
			.renderLevel(
				this.resourcePool,
				deltaTracker,
				renderOutline,
				cameraState,
				modelViewMatrix,
				terrainFog,
				cameraState.fogData.color,
				!shouldCreateBossFog,
				this.gameRenderState.levelRenderState.chunkSectionsToRender
			);
		profiler.popPush("hand");
		boolean isSleeping = cameraState.entityRenderState.isSleeping;
		this.hudProjection
			.setupPerspective(0.05F, 100.0F, cameraState.hudFov, this.gameRenderState.windowRenderState.width, this.gameRenderState.windowRenderState.height);
		RenderSystem.setProjectionMatrix(this.hud3dProjectionMatrixBuffer.getBuffer(this.hudProjection), ProjectionType.PERSPECTIVE);
		RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(this.minecraft.getMainRenderTarget().getDepthTexture(), 1.0);
		this.renderItemInHand(cameraState, cameraEntityPartialTicks, modelViewMatrix);
		profiler.popPush("screenEffects");
		MultiBufferSource.BufferSource bufferSource = this.renderBuffers.bufferSource();
		this.screenEffectRenderer
			.renderScreenEffect(optionsState.cameraType.isFirstPerson(), isSleeping, worldPartialTicks, this.submitNodeStorage, optionsState.hideGui);
		this.featureRenderDispatcher.renderAllFeatures();
		bufferSource.endBatch();
		profiler.pop();
		RenderSystem.setShaderFog(this.fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
		if (this.minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.THREE_DIMENSIONAL_CROSSHAIR)
			&& optionsState.cameraType.isFirstPerson()
			&& !optionsState.hideGui) {
			this.minecraft.getDebugOverlay().render3dCrosshair(cameraState, this.gameRenderState.windowRenderState.guiScale);
		}
	}

	private void extractWindow() {
		WindowRenderState windowState = this.gameRenderState.windowRenderState;
		Window window = this.minecraft.getWindow();
		windowState.width = window.getWidth();
		windowState.height = window.getHeight();
		windowState.guiScale = window.getGuiScale();
		windowState.appropriateLineWidth = window.getAppropriateLineWidth();
		windowState.isMinimized = window.isMinimized();
		windowState.isResized = window.isResized();
	}

	private void extractOptions() {
		OptionsRenderState optionsState = this.gameRenderState.optionsRenderState;
		Options options = this.minecraft.options;
		optionsState.cloudRange = options.cloudRange().get();
		optionsState.cutoutLeaves = options.cutoutLeaves().get();
		optionsState.improvedTransparency = options.improvedTransparency().get();
		optionsState.ambientOcclusion = options.ambientOcclusion().get();
		optionsState.menuBackgroundBlurriness = options.getMenuBackgroundBlurriness();
		optionsState.panoramaSpeed = options.panoramaSpeed().get();
		optionsState.maxAnisotropyValue = options.maxAnisotropyValue();
		optionsState.textureFiltering = options.textureFiltering().get();
		optionsState.bobView = options.bobView().get();
		optionsState.hideGui = options.hideGui;
		optionsState.screenEffectScale = options.screenEffectScale().get().floatValue();
		optionsState.glintSpeed = options.glintSpeed().get();
		optionsState.glintStrength = options.glintStrength().get();
		optionsState.damageTiltStrength = options.damageTiltStrength().get();
		optionsState.backgroundForChatOnly = options.backgroundForChatOnly().get();
		optionsState.textBackgroundOpacity = options.textBackgroundOpacity().get().floatValue();
		optionsState.cloudStatus = options.getCloudStatus();
		optionsState.cameraType = options.getCameraType();
		optionsState.renderDistance = options.getEffectiveRenderDistance();
	}

	private void extractCamera(final DeltaTracker deltaTracker, final float worldPartialTicks, final float cameraEntityPartialTicks) {
		CameraRenderState cameraState = this.gameRenderState.levelRenderState.cameraRenderState;
		this.mainCamera.extractRenderState(cameraState, cameraEntityPartialTicks);
		cameraState.fogType = this.mainCamera.getFluidInCamera();
		cameraState.fogData = this.fogRenderer
			.setupFog(
				this.mainCamera,
				this.minecraft.options.getEffectiveRenderDistance(),
				deltaTracker,
				this.getBossOverlayWorldDarkening(worldPartialTicks),
				this.minecraft.level
			);
	}

	public void resetData() {
		this.screenEffectRenderer.resetItemActivation();
		this.minecraft.getMapTextureManager().resetData();
		this.mainCamera.reset();
		this.hasWorldScreenshot = false;
	}

	public void displayItemActivation(final ItemStack itemStack) {
		this.screenEffectRenderer.displayItemActivation(itemStack, this.random);
	}

	public Minecraft getMinecraft() {
		return this.minecraft;
	}

	public float getBossOverlayWorldDarkening(final float a) {
		return Mth.lerp(a, this.bossOverlayWorldDarkeningO, this.bossOverlayWorldDarkening);
	}

	public Camera getMainCamera() {
		return this.mainCamera;
	}

	public GpuTextureView lightmap() {
		return this.useUiLightmap ? this.uiLightmap.getTextureView() : this.lightmap.getTextureView();
	}

	public GpuTextureView levelLightmap() {
		return this.lightmap.getTextureView();
	}

	public OverlayTexture overlayTexture() {
		return this.overlayTexture;
	}

	@Override
	public Vec3 projectPointToScreen(final Vec3 point) {
		Matrix4f mvp = this.mainCamera.getViewRotationProjectionMatrix(new Matrix4f());
		Vec3 camPos = this.mainCamera.position();
		Vec3 offset = point.subtract(camPos);
		Vector3f vector3f = mvp.transformProject(offset.toVector3f());
		return new Vec3(vector3f);
	}

	@Override
	public double projectHorizonToScreen() {
		float xRot = this.mainCamera.xRot();
		if (xRot <= -90.0F) {
			return Double.NEGATIVE_INFINITY;
		} else if (xRot >= 90.0F) {
			return Double.POSITIVE_INFINITY;
		} else {
			float fov = this.mainCamera.getFov();
			return Math.tan(xRot * (float) (Math.PI / 180.0)) / Math.tan(fov / 2.0F * (float) (Math.PI / 180.0));
		}
	}

	public GlobalSettingsUniform getGlobalSettingsUniform() {
		return this.globalSettingsUniform;
	}

	public Lighting getLighting() {
		return this.lighting;
	}

	public void setLevel(@Nullable final ClientLevel level) {
		if (level != null) {
			this.lighting.updateLevel(level.dimensionType().cardinalLightType());
		}

		this.mainCamera.setLevel(level);
	}

	public Panorama getPanorama() {
		return this.panorama;
	}

	public void registerPanoramaTextures(final TextureManager textureManager) {
		this.guiRenderer.registerPanoramaTextures(textureManager);
	}
}
