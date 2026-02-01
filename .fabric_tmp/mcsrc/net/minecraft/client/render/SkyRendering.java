package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class SkyRendering implements AutoCloseable {
	private static final Identifier SUN_TEXTURE = Identifier.ofVanilla("textures/environment/sun.png");
	private static final Identifier END_FLASH_TEXTURE = Identifier.ofVanilla("textures/environment/end_flash.png");
	private static final Identifier MOON_PHASES_TEXTURE = Identifier.ofVanilla("textures/environment/moon_phases.png");
	private static final Identifier END_SKY_TEXTURE = Identifier.ofVanilla("textures/environment/end_sky.png");
	private static final float field_53144 = 512.0F;
	private static final int field_57932 = 10;
	private static final int field_57933 = 1500;
	private static final float field_62950 = 30.0F;
	private static final float field_62951 = 100.0F;
	private static final float field_62952 = 20.0F;
	private static final float field_62953 = 100.0F;
	private static final int field_62954 = 16;
	private static final int field_57934 = 6;
	private static final float field_62955 = 100.0F;
	private static final float field_62956 = 60.0F;
	private final GpuBuffer starVertexBuffer;
	private final RenderSystem.ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
	private final GpuBuffer topSkyVertexBuffer;
	private final GpuBuffer bottomSkyVertexBuffer;
	private final GpuBuffer endSkyVertexBuffer;
	private final GpuBuffer sunVertexBuffer;
	private final GpuBuffer moonPhaseVertexBuffer;
	private final GpuBuffer sunRiseVertexBuffer;
	private final GpuBuffer endFlashVertexBuffer;
	private final RenderSystem.ShapeIndexBuffer indexBuffer2 = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
	@Nullable
	private AbstractTexture sunTexture;
	@Nullable
	private AbstractTexture moonPhasesTexture;
	@Nullable
	private AbstractTexture endSkyTexture;
	@Nullable
	private AbstractTexture endFlashTexture;
	private int starIndexCount;

	public SkyRendering() {
		this.starVertexBuffer = this.createStars();
		this.endSkyVertexBuffer = createEndSky();
		this.endFlashVertexBuffer = this.createEndFlash();
		this.sunVertexBuffer = this.createSun();
		this.moonPhaseVertexBuffer = this.createMoonPhases();
		this.sunRiseVertexBuffer = this.createSunRise();

		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(10 * VertexFormats.POSITION.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION);
			this.createSky(bufferBuilder, 16.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				this.topSkyVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Top sky vertex buffer", GpuBuffer.USAGE_VERTEX, builtBuffer.getBuffer());
			}

			bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION);
			this.createSky(bufferBuilder, -16.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				this.bottomSkyVertexBuffer = RenderSystem.getDevice().createBuffer(() -> "Bottom sky vertex buffer", GpuBuffer.USAGE_VERTEX, builtBuffer.getBuffer());
			}
		}
	}

	protected void method_74924() {
		this.endSkyTexture = this.bindTexture(END_SKY_TEXTURE);
		this.endFlashTexture = this.bindTexture(END_FLASH_TEXTURE);
		this.sunTexture = this.bindTexture(SUN_TEXTURE);
		this.moonPhasesTexture = this.bindTexture(MOON_PHASES_TEXTURE);
	}

	private AbstractTexture bindTexture(Identifier identifier) {
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
		AbstractTexture abstractTexture = textureManager.getTexture(identifier);
		abstractTexture.setUseMipmaps(false);
		return abstractTexture;
	}

	private GpuBuffer createSunRise() {
		int i = 18;
		int j = VertexFormats.POSITION_COLOR.getVertexSize();

		GpuBuffer var16;
		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(18 * j)) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
			int k = ColorHelper.getWhite(1.0F);
			int l = ColorHelper.getWhite(0.0F);
			bufferBuilder.vertex(0.0F, 100.0F, 0.0F).color(k);

			for (int m = 0; m <= 16; m++) {
				float f = m * (float) (Math.PI * 2) / 16.0F;
				float g = MathHelper.sin(f);
				float h = MathHelper.cos(f);
				bufferBuilder.vertex(g * 120.0F, h * 120.0F, -h * 40.0F).color(l);
			}

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				var16 = RenderSystem.getDevice().createBuffer(() -> "Sunrise/Sunset fan", GpuBuffer.USAGE_VERTEX, builtBuffer.getBuffer());
			}
		}

		return var16;
	}

	private GpuBuffer createSun() {
		GpuBuffer var5;
		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(4 * VertexFormats.POSITION_TEXTURE.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
			Matrix4f matrix4f = new Matrix4f();
			bufferBuilder.vertex(matrix4f, -1.0F, 0.0F, -1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(matrix4f, 1.0F, 0.0F, -1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(matrix4f, 1.0F, 0.0F, 1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(matrix4f, -1.0F, 0.0F, 1.0F).texture(0.0F, 1.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				var5 = RenderSystem.getDevice().createBuffer(() -> "Sun quad", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, builtBuffer.getBuffer());
			}
		}

		return var5;
	}

	private GpuBuffer createMoonPhases() {
		int i = 8;
		int j = VertexFormats.POSITION_TEXTURE.getVertexSize();

		GpuBuffer var18;
		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(32 * j)) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
			Matrix4f matrix4f = new Matrix4f();

			for (int k = 0; k < 8; k++) {
				int l = k % 4;
				int m = k / 4 % 2;
				float f = l / 4.0F;
				float g = m / 2.0F;
				float h = (l + 1) / 4.0F;
				float n = (m + 1) / 2.0F;
				bufferBuilder.vertex(matrix4f, -1.0F, 0.0F, 1.0F).texture(h, n);
				bufferBuilder.vertex(matrix4f, 1.0F, 0.0F, 1.0F).texture(f, n);
				bufferBuilder.vertex(matrix4f, 1.0F, 0.0F, -1.0F).texture(f, g);
				bufferBuilder.vertex(matrix4f, -1.0F, 0.0F, -1.0F).texture(h, g);
			}

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				var18 = RenderSystem.getDevice().createBuffer(() -> "Moon phases", GpuBuffer.USAGE_VERTEX, builtBuffer.getBuffer());
			}
		}

		return var18;
	}

	private GpuBuffer createStars() {
		Random random = Random.create(10842L);
		float f = 100.0F;

		GpuBuffer var19;
		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(VertexFormats.POSITION.getVertexSize() * 1500 * 4)) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);

			for (int i = 0; i < 1500; i++) {
				float g = random.nextFloat() * 2.0F - 1.0F;
				float h = random.nextFloat() * 2.0F - 1.0F;
				float j = random.nextFloat() * 2.0F - 1.0F;
				float k = 0.15F + random.nextFloat() * 0.1F;
				float l = MathHelper.magnitude(g, h, j);
				if (!(l <= 0.010000001F) && !(l >= 1.0F)) {
					Vector3f vector3f = new Vector3f(g, h, j).normalize(100.0F);
					float m = (float)(random.nextDouble() * (float) Math.PI * 2.0);
					Matrix3f matrix3f = new Matrix3f().rotateTowards(new Vector3f(vector3f).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-m);
					bufferBuilder.vertex(new Vector3f(k, -k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.vertex(new Vector3f(k, k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.vertex(new Vector3f(-k, k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.vertex(new Vector3f(-k, -k, 0.0F).mul(matrix3f).add(vector3f));
				}
			}

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				this.starIndexCount = builtBuffer.getDrawParameters().indexCount();
				var19 = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, builtBuffer.getBuffer());
			}
		}

		return var19;
	}

	private void createSky(VertexConsumer vertexConsumer, float height) {
		float f = Math.signum(height) * 512.0F;
		vertexConsumer.vertex(0.0F, height, 0.0F);

		for (int i = -180; i <= 180; i += 45) {
			vertexConsumer.vertex(f * MathHelper.cos(i * (float) (Math.PI / 180.0)), height, 512.0F * MathHelper.sin(i * (float) (Math.PI / 180.0)));
		}
	}

	private static GpuBuffer createEndSky() {
		GpuBuffer var10;
		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(24 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

			for (int i = 0; i < 6; i++) {
				Matrix4f matrix4f = new Matrix4f();
				switch (i) {
					case 1:
						matrix4f.rotationX((float) (Math.PI / 2));
						break;
					case 2:
						matrix4f.rotationX((float) (-Math.PI / 2));
						break;
					case 3:
						matrix4f.rotationX((float) Math.PI);
						break;
					case 4:
						matrix4f.rotationZ((float) (Math.PI / 2));
						break;
					case 5:
						matrix4f.rotationZ((float) (-Math.PI / 2));
				}

				bufferBuilder.vertex(matrix4f, -100.0F, -100.0F, -100.0F).texture(0.0F, 0.0F).color(-14145496);
				bufferBuilder.vertex(matrix4f, -100.0F, -100.0F, 100.0F).texture(0.0F, 16.0F).color(-14145496);
				bufferBuilder.vertex(matrix4f, 100.0F, -100.0F, 100.0F).texture(16.0F, 16.0F).color(-14145496);
				bufferBuilder.vertex(matrix4f, 100.0F, -100.0F, -100.0F).texture(16.0F, 0.0F).color(-14145496);
			}

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				var10 = RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, builtBuffer.getBuffer());
			}
		}

		return var10;
	}

	private GpuBuffer createEndFlash() {
		GpuBuffer var5;
		try (BufferAllocator bufferAllocator = BufferAllocator.fixedSized(4 * VertexFormats.POSITION_TEXTURE.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
			Matrix4f matrix4f = new Matrix4f();
			bufferBuilder.vertex(matrix4f, -1.0F, 0.0F, -1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(matrix4f, 1.0F, 0.0F, -1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(matrix4f, 1.0F, 0.0F, 1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(matrix4f, -1.0F, 0.0F, 1.0F).texture(0.0F, 1.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				var5 = RenderSystem.getDevice().createBuffer(() -> "End flash quad", GpuBuffer.USAGE_VERTEX, builtBuffer.getBuffer());
			}
		}

		return var5;
	}

	public void renderTopSky(float red, float green, float blue) {
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.write(RenderSystem.getModelViewMatrix(), new Vector4f(red, green, blue, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
		GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
		GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Sky disc", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.POSITION_SKY);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.setVertexBuffer(0, this.topSkyVertexBuffer);
			renderPass.draw(0, 10);
		}
	}

	public void updateRenderState(ClientWorld world, float f, Vec3d pos, SkyRenderState state) {
		DimensionEffects dimensionEffects = world.getDimensionEffects();
		state.skyType = dimensionEffects.getSkyType();
		if (state.skyType != DimensionEffects.SkyType.NONE) {
			if (state.skyType == DimensionEffects.SkyType.END) {
				EndLightFlashManager endLightFlashManager = world.getEndLightFlashManager();
				if (endLightFlashManager != null) {
					state.endFlashIntensity = endLightFlashManager.getSkyFactor(f);
					state.endFlashPitch = endLightFlashManager.getPitch();
					state.endFlashYaw = endLightFlashManager.getYaw();
				}
			} else {
				state.solarAngle = world.getSkyAngleRadians(f);
				state.time = world.getSkyAngle(f);
				state.rainGradient = 1.0F - world.getRainGradient(f);
				state.starBrightness = world.getStarBrightness(f) * state.rainGradient;
				state.sunriseAndSunsetColor = dimensionEffects.getSkyColor(state.time);
				state.moonPhase = world.getMoonPhase();
				state.skyColor = world.getSkyColor(pos, f);
				state.shouldRenderSkyDark = this.isSkyDark(f, world);
				state.isSunTransition = dimensionEffects.isSunRisingOrSetting(state.time);
			}
		}
	}

	private boolean isSkyDark(float f, ClientWorld clientWorld) {
		return MinecraftClient.getInstance().player.getCameraPosVec(f).y - clientWorld.getLevelProperties().getSkyDarknessHeight(clientWorld) < 0.0;
	}

	public void renderSkyDark() {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.translate(0.0F, 12.0F, 0.0F);
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.write(matrix4fStack, new Vector4f(0.0F, 0.0F, 0.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
		GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
		GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Sky dark", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.POSITION_SKY);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.setVertexBuffer(0, this.bottomSkyVertexBuffer);
			renderPass.draw(0, 10);
		}

		matrix4fStack.popMatrix();
	}

	public void renderCelestialBodies(MatrixStack matrices, float f, int i, float g, float h) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(f * 360.0F));
		this.renderSun(g, matrices);
		this.renderMoon(i, g, matrices);
		if (h > 0.0F) {
			this.renderStars(h, matrices);
		}

		matrices.pop();
	}

	private void renderSun(float alpha, MatrixStack matrices) {
		if (this.sunTexture != null) {
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.pushMatrix();
			matrix4fStack.mul(matrices.peek().getPositionMatrix());
			matrix4fStack.translate(0.0F, 100.0F, 0.0F);
			matrix4fStack.scale(30.0F, 1.0F, 30.0F);
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
				.write(matrix4fStack, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f(), 0.0F);
			GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
			GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
			GpuBuffer gpuBuffer = this.indexBuffer2.getIndexBuffer(6);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "Sky sun", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_CELESTIAL);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.bindSampler("Sampler0", this.sunTexture.getGlTextureView());
				renderPass.setVertexBuffer(0, this.sunVertexBuffer);
				renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer2.getIndexType());
				renderPass.drawIndexed(0, 0, 6, 1);
			}

			matrix4fStack.popMatrix();
		}
	}

	private void renderMoon(int phase, float alpha, MatrixStack matrixStack) {
		if (this.moonPhasesTexture != null) {
			int i = phase & 7;
			int j = i * 4;
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.pushMatrix();
			matrix4fStack.mul(matrixStack.peek().getPositionMatrix());
			matrix4fStack.translate(0.0F, -100.0F, 0.0F);
			matrix4fStack.scale(20.0F, 1.0F, 20.0F);
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
				.write(matrix4fStack, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), new Matrix4f(), 0.0F);
			GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
			GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
			GpuBuffer gpuBuffer = this.indexBuffer2.getIndexBuffer(6);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "Sky moon", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_CELESTIAL);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.bindSampler("Sampler0", this.moonPhasesTexture.getGlTextureView());
				renderPass.setVertexBuffer(0, this.moonPhaseVertexBuffer);
				renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer2.getIndexType());
				renderPass.drawIndexed(j, 0, 6, 1);
			}

			matrix4fStack.popMatrix();
		}
	}

	private void renderStars(float brightness, MatrixStack matrices) {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.mul(matrices.peek().getPositionMatrix());
		RenderPipeline renderPipeline = RenderPipelines.POSITION_STARS;
		GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
		GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
		GpuBuffer gpuBuffer = this.indexBuffer.getIndexBuffer(this.starIndexCount);
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.write(matrix4fStack, new Vector4f(brightness, brightness, brightness, brightness), new Vector3f(), new Matrix4f(), 0.0F);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Stars", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(renderPipeline);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.setVertexBuffer(0, this.starVertexBuffer);
			renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer.getIndexType());
			renderPass.drawIndexed(0, 0, this.starIndexCount, 1);
		}

		matrix4fStack.popMatrix();
	}

	public void renderGlowingSky(MatrixStack matrices, float f, int i) {
		float g = ColorHelper.getAlphaFloat(i);
		if (!(g <= 0.001F)) {
			float h = ColorHelper.getRedFloat(i);
			float j = ColorHelper.getGreenFloat(i);
			float k = ColorHelper.getBlueFloat(i);
			matrices.push();
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
			float l = MathHelper.sin(f) < 0.0F ? 180.0F : 0.0F;
			matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(l + 90.0F));
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.pushMatrix();
			matrix4fStack.mul(matrices.peek().getPositionMatrix());
			matrix4fStack.scale(1.0F, 1.0F, g);
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().write(matrix4fStack, new Vector4f(h, j, k, g), new Vector3f(), new Matrix4f(), 0.0F);
			GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
			GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "Sunrise sunset", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(RenderPipelines.POSITION_COLOR_SUNRISE_SUNSET);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.setVertexBuffer(0, this.sunRiseVertexBuffer);
				renderPass.draw(0, 18);
			}

			matrix4fStack.popMatrix();
			matrices.pop();
		}
	}

	public void renderEndSky() {
		if (this.endSkyTexture != null) {
			RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
			GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(36);
			GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
			GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
				.write(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "End sky", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_END_SKY);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.bindSampler("Sampler0", this.endSkyTexture.getGlTextureView());
				renderPass.setVertexBuffer(0, this.endSkyVertexBuffer);
				renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());
				renderPass.drawIndexed(0, 0, 36, 1);
			}
		}
	}

	public void drawEndLightFlash(MatrixStack matrixStack, float f, float skyFactor, float pitch) {
		if (this.endFlashTexture != null) {
			matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - pitch));
			matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F - skyFactor));
			Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
			matrix4fStack.pushMatrix();
			matrix4fStack.mul(matrixStack.peek().getPositionMatrix());
			matrix4fStack.translate(0.0F, 100.0F, 0.0F);
			matrix4fStack.scale(60.0F, 1.0F, 60.0F);
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms().write(matrix4fStack, new Vector4f(f, f, f, f), new Vector3f(), new Matrix4f(), 0.0F);
			GpuTextureView gpuTextureView = MinecraftClient.getInstance().getFramebuffer().getColorAttachmentView();
			GpuTextureView gpuTextureView2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
			GpuBuffer gpuBuffer = this.indexBuffer2.getIndexBuffer(6);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "End flash", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_CELESTIAL);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.bindSampler("Sampler0", this.endFlashTexture.getGlTextureView());
				renderPass.setVertexBuffer(0, this.endFlashVertexBuffer);
				renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer2.getIndexType());
				renderPass.drawIndexed(0, 0, 6, 1);
			}

			matrix4fStack.popMatrix();
		}
	}

	public void close() {
		this.sunVertexBuffer.close();
		this.moonPhaseVertexBuffer.close();
		this.starVertexBuffer.close();
		this.topSkyVertexBuffer.close();
		this.bottomSkyVertexBuffer.close();
		this.endSkyVertexBuffer.close();
		this.sunRiseVertexBuffer.close();
		this.endFlashVertexBuffer.close();
	}
}
