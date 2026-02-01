package net.minecraft.client.render.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.render.DimensionEffects;

@Environment(EnvType.CLIENT)
public class SkyRenderState implements FabricRenderState {
	public DimensionEffects.SkyType skyType = DimensionEffects.SkyType.NONE;
	public boolean isSunTransition;
	public boolean shouldRenderSkyDark;
	public float solarAngle;
	public float time;
	public float rainGradient;
	public float starBrightness;
	public int sunriseAndSunsetColor;
	public int moonPhase;
	public int skyColor;
	public float endFlashIntensity;
	public float endFlashPitch;
	public float endFlashYaw;

	public void clear() {
		this.skyType = DimensionEffects.SkyType.NONE;
	}
}
