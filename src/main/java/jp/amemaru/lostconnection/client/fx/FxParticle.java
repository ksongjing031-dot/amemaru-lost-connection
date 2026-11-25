package jp.amemaru.lostconnection.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import jp.amemaru.lostconnection.particle.FxParticleOptions;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * このModの演出用パーティクル（クライアント専用）。どれも自分で光り（暗い所でも明るく見える）、光を足し合わせて描く。
 *
 * <p>動き方は種類（{@link Style}）で決まる。色・大きさ・続く時間は {@link FxParticleOptions} で決める。</p>
 */
public class FxParticle extends TextureSheetParticle {
    /** 種類ごとの動き方。 */
    public enum Style {
        /** やわらかな光：少しふくらみながら消える。 */
        GLOW,
        /** 火花：速さの向きに伸び、重さで落ちる。 */
        SPARK,
        /** 氷のかけら：回りながら落ちる。 */
        SHARD,
        /** 星：回りながら、またたいて消える。 */
        STAR,
        /** 紋字：ゆっくり昇り、ふわっと現れて消える。 */
        RUNE,
        /** 雪：ゆらゆら舞い落ちる。 */
        SNOW,
        /** 羽根：左右に揺れながら、ゆっくり落ちる（Phase 6 その2）。 */
        FEATHER,
        /** 花びら・光の葉：くるくる回りながら漂い、ゆっくり昇る。 */
        PETAL,
        /** 煙：ふくらみながら薄れる（光を足さずに描くので、暗い色も見える）。 */
        SMOKE,
        /** 泡：ゆらゆら昇り、最後にふくらんではじける。 */
        BUBBLE
    }

    /** カメラからこの距離（＋粒の大きさ）より近いと薄くなりはじめ、{@link #NEAR_FADE_END} で見えなくなる。 */
    private static final double NEAR_FADE_START = 1.0;
    private static final double NEAR_FADE_END = 0.2;

    /** 光を足し合わせて描く（明るい所ほど白く光る）。両面を描く。 */
    public static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "AMEMARU_ADDITIVE";
        }
    };

    private final Style style;
    private final SpriteSet sprites;
    private final float baseSize;
    private final float spin;
    private final float baseAlpha;

    protected FxParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, FxParticleOptions options,
                         SpriteSet sprites, Style style) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.style = style;
        this.sprites = sprites;
        this.rCol = options.red();
        this.gCol = options.green();
        this.bCol = options.blue();
        this.baseAlpha = 1.0F;
        this.alpha = 1.0F;
        this.lifetime = Math.max(1, options.lifetime());
        this.baseSize = Math.max(0.01F, options.size());
        this.quadSize = baseSize;
        this.spin = (random.nextFloat() - 0.5F) * 0.35F;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        switch (style) {
            case GLOW -> {
                this.gravity = 0.0F;
                this.friction = 0.86F;
                this.hasPhysics = false;
            }
            case SPARK -> {
                this.gravity = 0.55F;
                this.friction = 0.9F;
                this.hasPhysics = false;
                this.roll = 0.0F;
                this.oRoll = 0.0F;
            }
            case SHARD -> {
                this.gravity = 0.9F;
                this.friction = 0.97F;
                this.hasPhysics = true;
            }
            case STAR -> {
                this.gravity = 0.0F;
                this.friction = 0.88F;
                this.hasPhysics = false;
            }
            case RUNE -> {
                this.gravity = -0.02F;
                this.friction = 0.94F;
                this.hasPhysics = false;
                this.roll = 0.0F;
                this.oRoll = 0.0F;
            }
            case SNOW -> {
                this.gravity = 0.08F;
                this.friction = 0.96F;
                this.hasPhysics = true;
            }
            case FEATHER -> {
                this.gravity = 0.04F;
                this.friction = 0.93F;
                this.hasPhysics = true;
            }
            case PETAL -> {
                this.gravity = -0.015F;
                this.friction = 0.95F;
                this.hasPhysics = false;
            }
            case SMOKE -> {
                this.gravity = -0.01F;
                this.friction = 0.9F;
                this.hasPhysics = false;
            }
            case BUBBLE -> {
                this.gravity = -0.03F;
                this.friction = 0.92F;
                this.hasPhysics = false;
                this.roll = 0.0F;
                this.oRoll = 0.0F;
            }
        }
        pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return style == Style.SMOKE ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT : ADDITIVE;
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        float t = age / (float) lifetime;
        oRoll = roll;
        switch (style) {
            case GLOW -> {
                alpha = baseAlpha * (1.0F - t * t);
                quadSize = baseSize * (1.0F + 0.35F * t);
            }
            case SPARK -> {
                alpha = baseAlpha * (1.0F - t);
                quadSize = baseSize * (1.0F - 0.5F * t);
            }
            case SHARD -> {
                roll += spin * 1.6F;
                alpha = t > 0.7F ? baseAlpha * (1.0F - t) / 0.3F : baseAlpha;
            }
            case STAR -> {
                roll += spin;
                float twinkle = 0.75F + 0.25F * Mth.sin(age * 1.7F + baseSize * 10.0F);
                alpha = baseAlpha * twinkle * (1.0F - t * t);
                quadSize = baseSize * Mth.sin(Mth.PI * Math.min(1.0F, t * 1.15F + 0.12F));
            }
            case RUNE -> {
                alpha = baseAlpha * Mth.sin(Mth.PI * t);
                quadSize = baseSize * (0.85F + 0.3F * t);
            }
            case SNOW -> {
                roll += spin * 0.3F;
                xd += Mth.sin(age * 0.25F + baseSize * 7.0F) * 0.0025;
                alpha = baseAlpha * (1.0F - t * t);
            }
            case FEATHER -> {
                roll = Mth.sin(age * 0.22F + baseSize * 9.0F) * 0.6F;
                xd += Mth.sin(age * 0.18F + baseSize * 5.0F) * 0.004;
                zd += Mth.cos(age * 0.15F + baseSize * 3.0F) * 0.003;
                alpha = baseAlpha * (1.0F - t * t);
            }
            case PETAL -> {
                roll += spin * 0.9F;
                xd += Mth.sin(age * 0.3F + baseSize * 6.0F) * 0.003;
                alpha = baseAlpha * Mth.sin(Mth.PI * Math.min(1.0F, t * 1.1F + 0.05F));
            }
            case SMOKE -> {
                roll += spin * 0.2F;
                alpha = baseAlpha * 0.75F * (1.0F - t);
                quadSize = baseSize * (1.0F + 1.2F * t);
            }
            case BUBBLE -> {
                xd += Mth.sin(age * 0.4F + baseSize * 8.0F) * 0.002;
                alpha = baseAlpha * (t > 0.85F ? (1.0F - t) / 0.15F : 1.0F);
                quadSize = baseSize * (t > 0.85F ? 1.0F + (t - 0.85F) * 4.0F : 1.0F);
            }
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // カメラのすぐ前を通る粒は、画面をふさぐ大きな四角に見えるため、近いほど薄くする
        Vec3 cam = camera.getPosition();
        double dx = Mth.lerp(partialTicks, xo, x) - cam.x;
        double dy = Mth.lerp(partialTicks, yo, y) - cam.y;
        double dz = Mth.lerp(partialTicks, zo, z) - cam.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double fadeStart = NEAR_FADE_START + quadSize;
        float kept = alpha;
        if (distance < fadeStart) {
            alpha *= (float) Mth.clamp((distance - NEAR_FADE_END) / (fadeStart - NEAR_FADE_END), 0.0, 1.0);
            if (alpha < 0.004F) {
                alpha = kept;
                return;
            }
        }
        if (style == Style.SPARK) {
            renderStreak(buffer, camera, partialTicks);
        } else {
            super.render(buffer, camera, partialTicks);
        }
        alpha = kept;
    }

    /** 火花：速さの向きに伸びた四角（いつもカメラの方を向く）。 */
    private void renderStreak(VertexConsumer buffer, Camera camera, float partialTicks) {
        Vec3 cam = camera.getPosition();
        Vector3f head = new Vector3f((float) (Mth.lerp(partialTicks, xo, x) - cam.x), (float) (Mth.lerp(partialTicks, yo, y) - cam.y),
                (float) (Mth.lerp(partialTicks, zo, z) - cam.z));
        Vector3f direction = new Vector3f((float) xd, (float) yd, (float) zd);
        float speed = direction.length();
        if (speed < 1.0E-4F) {
            super.render(buffer, camera, partialTicks);
            return;
        }
        float length = Math.min(1.4F, speed * 2.6F) + quadSize;
        direction.mul(1.0F / speed);
        Vector3f tail = new Vector3f(direction).mul(-length).add(head);
        Vector3f side = new Vector3f(direction).cross(new Vector3f(head).negate());
        if (side.lengthSquared() < 1.0E-8F) {
            side.set(0.0F, 1.0F, 0.0F);
        }
        side.normalize().mul(quadSize * 0.5F);
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTicks);
        vertex(buffer, head.x + side.x, head.y + side.y, head.z + side.z, u1, v0, light);
        vertex(buffer, head.x - side.x, head.y - side.y, head.z - side.z, u1, v1, light);
        vertex(buffer, tail.x - side.x, tail.y - side.y, tail.z - side.z, u0, v1, light);
        vertex(buffer, tail.x + side.x, tail.y + side.y, tail.z + side.z, u0, v0, light);
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float u, float v, int light) {
        buffer.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
    }

    /** 種類ごとのパーティクルの作り方（{@code RegisterParticleProvidersEvent} で登録する）。 */
    public static final class Provider implements ParticleProvider<FxParticleOptions> {
        private final SpriteSet sprites;
        private final Style style;

        public Provider(SpriteSet sprites, Style style) {
            this.sprites = sprites;
            this.style = style;
        }

        @Override
        public Particle createParticle(FxParticleOptions options, ClientLevel level, double x, double y, double z, double vx, double vy,
                                       double vz) {
            return new FxParticle(level, x, y, z, vx, vy, vz, options, sprites, style);
        }
    }
}
