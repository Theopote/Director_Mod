package net.minecraft.client.render;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;

@Environment(EnvType.CLIENT)
public abstract class DimensionEffects {
	private static final Object2ObjectMap<Identifier, DimensionEffects> BY_IDENTIFIER = Util.make(new Object2ObjectArrayMap<>(), map -> {
		DimensionEffects.Overworld overworld = new DimensionEffects.Overworld();
		map.defaultReturnValue(overworld);
		map.put(DimensionTypes.OVERWORLD_ID, overworld);
		map.put(DimensionTypes.THE_NETHER_ID, new DimensionEffects.Nether());
		map.put(DimensionTypes.THE_END_ID, new DimensionEffects.End());
	});
	private final DimensionEffects.SkyType skyType;
	private final boolean darkened;
	private final boolean alternateSkyColor;

	public DimensionEffects(DimensionEffects.SkyType skyType, boolean darkened, boolean alternateSkyColor) {
		this.skyType = skyType;
		this.darkened = darkened;
		this.alternateSkyColor = alternateSkyColor;
	}

	public static DimensionEffects byDimensionType(DimensionType dimensionType) {
		return BY_IDENTIFIER.get(dimensionType.effects());
	}

	public boolean isSunRisingOrSetting(float skyAngle) {
		return false;
	}

	public int getSkyColor(float skyAngle) {
		return 0;
	}

	/**
	 * Transforms the given fog color based on the current height of the sun. This is used in vanilla to darken
	 * fog during night.
	 */
	public abstract Vec3d adjustFogColor(Vec3d color, float sunHeight);

	public abstract boolean useThickFog(int camX, int camY);

	public DimensionEffects.SkyType getSkyType() {
		return this.skyType;
	}

	public boolean isDarkened() {
		return this.darkened;
	}

	public boolean hasAlternateSkyColor() {
		return this.alternateSkyColor;
	}

	@Environment(EnvType.CLIENT)
	public static class End extends DimensionEffects {
		public End() {
			super(DimensionEffects.SkyType.END, false, true);
		}

		@Override
		public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
			return color.multiply(0.15F);
		}

		@Override
		public boolean useThickFog(int camX, int camY) {
			return false;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Nether extends DimensionEffects {
		public Nether() {
			super(DimensionEffects.SkyType.NONE, true, false);
		}

		@Override
		public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
			return color;
		}

		@Override
		public boolean useThickFog(int camX, int camY) {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Overworld extends DimensionEffects {
		private static final float SUN_RISE_SET_THRESHOLD = 0.4F;

		public Overworld() {
			super(DimensionEffects.SkyType.NORMAL, false, false);
		}

		@Override
		public boolean isSunRisingOrSetting(float skyAngle) {
			float f = MathHelper.cos(skyAngle * (float) (Math.PI * 2));
			return f >= -0.4F && f <= 0.4F;
		}

		@Override
		public int getSkyColor(float skyAngle) {
			float f = MathHelper.cos(skyAngle * (float) (Math.PI * 2));
			float g = f / 0.4F * 0.5F + 0.5F;
			float h = MathHelper.square(1.0F - (1.0F - MathHelper.sin(g * (float) Math.PI)) * 0.99F);
			return ColorHelper.fromFloats(h, g * 0.3F + 0.7F, g * g * 0.7F + 0.2F, 0.2F);
		}

		@Override
		public Vec3d adjustFogColor(Vec3d color, float sunHeight) {
			return color.multiply(sunHeight * 0.94F + 0.06F, sunHeight * 0.94F + 0.06F, sunHeight * 0.91F + 0.09F);
		}

		@Override
		public boolean useThickFog(int camX, int camY) {
			return false;
		}
	}

	@Environment(EnvType.CLIENT)
	public static enum SkyType {
		/**
		 * Signals the renderer not to render a sky.
		 */
		NONE,
		/**
		 * Signals the renderer to render a normal sky (as in the vanilla Overworld).
		 */
		NORMAL,
		/**
		 * Signals the renderer to draw the end sky box over the sky (as in the vanilla End).
		 */
		END;
	}
}
