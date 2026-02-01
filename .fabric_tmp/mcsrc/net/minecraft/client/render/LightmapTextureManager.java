package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.dimension.DimensionType;
import org.joml.Vector3f;

/**
 * The lightmap texture manager maintains a texture containing the RGBA overlay for each of the 16&times;16 sky and block light combinations.
 * <p>
 * Also contains some utilities to pack and unpack lightmap coordinates from sky and block light values,
 * and some lightmap coordinates constants.
 */
@Environment(EnvType.CLIENT)
public class LightmapTextureManager implements AutoCloseable {
	/**
	 * Represents the maximum lightmap coordinate, where both sky light and block light equals {@code 15}.
	 * The value of this maximum lightmap coordinate is {@value}.
	 */
	public static final int MAX_LIGHT_COORDINATE = 15728880;
	/**
	 * Represents the maximum sky-light-wise lightmap coordinate whose value is {@value}.
	 * This is equivalent to a {@code 15} sky light and {@code 0} block light.
	 */
	public static final int MAX_SKY_LIGHT_COORDINATE = 15728640;
	/**
	 * Represents the maximum block-light-wise lightmap coordinate whose value is {@value}.
	 * This is equivalent to a {@code 0} sky light and {@code 15} block light.
	 */
	public static final int MAX_BLOCK_LIGHT_COORDINATE = 240;
	private static final int field_53098 = 16;
	private static final int UBO_SIZE = new Std140SizeCalculator()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putFloat()
		.putVec3()
		.putVec3()
		.get();
	private static final Vector3f ALTERNATE_SKY_LIGHT_COLOR = new Vector3f(0.9F, 0.5F, 1.0F);
	private final GpuTexture glTexture;
	private final GpuTextureView glTextureView;
	private boolean dirty;
	private float flickerIntensity;
	private final GameRenderer renderer;
	private final MinecraftClient client;
	private final MappableRingBuffer buffer;

	public LightmapTextureManager(GameRenderer renderer, MinecraftClient client) {
		this.renderer = renderer;
		this.client = client;
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.glTexture = gpuDevice.createTexture("Light Texture", 12, TextureFormat.RGBA8, 16, 16, 1, 1);
		this.glTexture.setTextureFilter(FilterMode.LINEAR, false);
		this.glTextureView = gpuDevice.createTextureView(this.glTexture);
		gpuDevice.createCommandEncoder().clearColorTexture(this.glTexture, -1);
		this.buffer = new MappableRingBuffer(() -> "Lightmap UBO", 130, UBO_SIZE);
	}

	public GpuTextureView getGlTextureView() {
		return this.glTextureView;
	}

	public void close() {
		this.glTexture.close();
		this.glTextureView.close();
		this.buffer.close();
	}

	public void tick() {
		this.flickerIntensity = this.flickerIntensity + (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
		this.flickerIntensity *= 0.9F;
		this.dirty = true;
	}

	public void disable() {
		RenderSystem.setShaderTexture(2, null);
	}

	public void enable() {
		RenderSystem.setShaderTexture(2, this.glTextureView);
	}

	private float getDarkness(LivingEntity entity, float factor, float tickProgress) {
		float f = 0.45F * factor;
		return Math.max(0.0F, MathHelper.cos((entity.age - tickProgress) * (float) Math.PI * 0.025F) * f);
	}

	public void update(float tickProgress) {
		if (this.dirty) {
			this.dirty = false;
			Profiler profiler = Profilers.get();
			profiler.push("lightTex");
			ClientWorld clientWorld = this.client.world;
			if (clientWorld != null) {
				float f = clientWorld.getSkyBrightness(1.0F);
				float h;
				Vector3f vector3f;
				if (clientWorld.getDimensionEffects().hasAlternateSkyColor()) {
					vector3f = new Vector3f(0.99F, 1.12F, 1.0F);
					EndLightFlashManager endLightFlashManager = clientWorld.getEndLightFlashManager();
					if (endLightFlashManager != null && !this.client.options.getHideLightningFlashes().getValue()) {
						float g = endLightFlashManager.getSkyFactor(tickProgress);
						if (this.client.inGameHud.getBossBarHud().shouldThickenFog()) {
							h = g / 3.0F;
						} else {
							h = g;
						}
					} else {
						h = 0.0F;
					}
				} else {
					vector3f = new Vector3f(1.0F, 1.0F, 1.0F);
					if (clientWorld.getLightningTicksLeft() > 0) {
						h = 1.0F;
					} else {
						h = f * 0.95F + 0.05F;
					}
				}

				float i = this.client.options.getDarknessEffectScale().getValue().floatValue();
				float g = this.client.player.getEffectFadeFactor(StatusEffects.DARKNESS, tickProgress) * i;
				float j = this.getDarkness(this.client.player, g, tickProgress) * i;
				float k = this.client.player.getUnderwaterVisibility();
				float l;
				if (this.client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
					l = GameRenderer.getNightVisionStrength(this.client.player, tickProgress);
				} else if (k > 0.0F && this.client.player.hasStatusEffect(StatusEffects.CONDUIT_POWER)) {
					l = k;
				} else {
					l = 0.0F;
				}

				Vector3f vector3f2;
				if (clientWorld.getDimensionEffects().hasAlternateSkyColor()) {
					vector3f2 = ALTERNATE_SKY_LIGHT_COLOR;
				} else {
					vector3f2 = new Vector3f(f, f, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
				}

				float m = this.flickerIntensity + 1.5F;
				float n = clientWorld.getDimension().ambientLight();
				float o = this.client.options.getGamma().getValue().floatValue();
				CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();

				try (GpuBuffer.MappedView mappedView = commandEncoder.mapBuffer(this.buffer.getBlocking(), false, true)) {
					Std140Builder.intoBuffer(mappedView.data())
						.putFloat(n)
						.putFloat(h)
						.putFloat(m)
						.putFloat(l)
						.putFloat(j)
						.putFloat(this.renderer.getSkyDarkness(tickProgress))
						.putFloat(Math.max(0.0F, o - g))
						.putVec3(vector3f2)
						.putVec3(vector3f);
				}

				try (RenderPass renderPass = commandEncoder.createRenderPass(() -> "Update light", this.glTextureView, OptionalInt.empty())) {
					renderPass.setPipeline(RenderPipelines.BILT_SCREEN_LIGHTMAP);
					RenderSystem.bindDefaultUniforms(renderPass);
					renderPass.setUniform("LightmapInfo", this.buffer.getBlocking());
					renderPass.draw(0, 3);
				}

				this.buffer.rotate();
				profiler.pop();
			}
		}
	}

	public static float getBrightness(DimensionType type, int lightLevel) {
		return getBrightness(type.ambientLight(), lightLevel);
	}

	public static float getBrightness(float ambientLight, int lightLevel) {
		float f = lightLevel / 15.0F;
		float g = f / (4.0F - 3.0F * f);
		return MathHelper.lerp(ambientLight, g, 1.0F);
	}

	public static int pack(int block, int sky) {
		return block << 4 | sky << 20;
	}

	public static int getBlockLightCoordinates(int light) {
		return light >>> 4 & 15;
	}

	public static int getSkyLightCoordinates(int light) {
		return light >>> 20 & 15;
	}

	public static int applyEmission(int light, int lightEmission) {
		if (lightEmission == 0) {
			return light;
		} else {
			int i = Math.max(getSkyLightCoordinates(light), lightEmission);
			int j = Math.max(getBlockLightCoordinates(light), lightEmission);
			return pack(j, i);
		}
	}
}
