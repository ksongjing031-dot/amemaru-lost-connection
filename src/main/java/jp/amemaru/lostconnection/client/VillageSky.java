package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import jp.amemaru.lostconnection.AmemaruMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 6階「凍結した村」の空と雪（11月25日。Phase 0-A 9章の6階「空にオーロラ」・第2章の色「氷青・白銀・オーロラの緑紫」）。
 * サーバーが送る階の見え方（{@link ClientTowerEnv} の {@code sky}・{@code weather}）に合わせて、
 *
 * <ul>
 *   <li>{@code aurora}：空を夜の藍色に沈め（塔の階はいつも昼なので、上から薄い幕をかける）、緑から紫へ移るオーロラの帯を
 *       3本、ゆっくり揺らして描く。小さな星も少し</li>
 *   <li>{@code snow}：まわりに雪をちらつかせる（粒）。吹雪のとき（凍て鳥）は、ClientTowerEnv の吹雪の強さで多くする</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class VillageSky {
    private static final int STARS = 140;
    private static final float[][] STAR_DIRS = new float[STARS][];

    static {
        java.util.Random random = new java.util.Random(20261125L);
        for (int i = 0; i < STARS; i++) {
            float yaw = random.nextFloat() * Mth.TWO_PI;
            float pitch = 0.25F + random.nextFloat() * 1.25F;
            STAR_DIRS[i] = new float[]{Mth.cos(pitch) * Mth.cos(yaw), Mth.sin(pitch), Mth.cos(pitch) * Mth.sin(yaw), 0.4F + random.nextFloat() * 0.6F};
        }
    }

    private VillageSky() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !"aurora".equals(ClientTowerEnv.sky())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        float time = (minecraft.level.getGameTime() + event.getPartialTick()) / 20.0F;
        PoseStack pose = event.getPoseStack();
        Matrix4f m = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        // 夜の藍色の幕（上ほど濃い。地平線の近くは雪あかりで少し明るい）
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float r = 60.0F;
        int segments = 24;
        float[] rings = {-0.15F, 0.08F, 0.35F, 0.75F, 1.5708F};
        float[][] colors = {{0.30F, 0.38F, 0.52F, 0.70F}, {0.20F, 0.27F, 0.42F, 0.82F}, {0.09F, 0.14F, 0.28F, 0.90F},
                {0.05F, 0.08F, 0.19F, 0.93F}, {0.03F, 0.05F, 0.13F, 0.95F}};
        for (int ring = 0; ring < rings.length - 1; ring++) {
            float p0 = rings[ring];
            float p1 = rings[ring + 1];
            for (int i = 0; i < segments; i++) {
                float a0 = i * Mth.TWO_PI / segments;
                float a1 = (i + 1) * Mth.TWO_PI / segments;
                vertex(buffer, m, r, a0, p0, colors[ring]);
                vertex(buffer, m, r, a1, p0, colors[ring]);
                vertex(buffer, m, r, a1, p1, colors[ring + 1]);
                vertex(buffer, m, r, a0, p1, colors[ring + 1]);
            }
        }
        tesselator.end();
        // 星（小さな四角）
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] star : STAR_DIRS) {
            float twinkle = 0.6F + 0.4F * Mth.sin(time * 1.7F + star[3] * 40.0F);
            float a = star[3] * twinkle;
            float x = star[0] * 58.0F;
            float y = star[1] * 58.0F;
            float z = star[2] * 58.0F;
            float s = 0.12F + star[3] * 0.1F;
            buffer.vertex(m, x - s, y, z - s).color(0.9F, 0.95F, 1.0F, a).endVertex();
            buffer.vertex(m, x - s, y, z + s).color(0.9F, 0.95F, 1.0F, a).endVertex();
            buffer.vertex(m, x + s, y, z + s).color(0.9F, 0.95F, 1.0F, a).endVertex();
            buffer.vertex(m, x + s, y, z - s).color(0.9F, 0.95F, 1.0F, a).endVertex();
        }
        tesselator.end();
        // オーロラの帯（足すように光らせる）
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < 3; band++) {
            float center = 0.6F + band * 2.2F + Mth.sin(time * 0.02F + band) * 0.3F;
            float width = 1.6F + band * 0.3F;
            int strips = 64;
            for (int i = 0; i < strips; i++) {
                float t0 = (float) i / strips;
                float t1 = (float) (i + 1) / strips;
                float a0 = center - width / 2 + width * t0;
                float a1 = center - width / 2 + width * t1;
                float w0 = Mth.sin(t0 * Mth.PI);
                float w1 = Mth.sin(t1 * Mth.PI);
                float base0 = 0.42F + 0.12F * Mth.sin(a0 * 3.1F + time * 0.35F + band) + 0.05F * Mth.sin(a0 * 9.0F - time * 0.8F);
                float base1 = 0.42F + 0.12F * Mth.sin(a1 * 3.1F + time * 0.35F + band) + 0.05F * Mth.sin(a1 * 9.0F - time * 0.8F);
                float h0 = 0.22F + 0.08F * Mth.sin(a0 * 5.0F + time * 0.5F);
                float h1 = 0.22F + 0.08F * Mth.sin(a1 * 5.0F + time * 0.5F);
                float pulse0 = 0.55F + 0.45F * Mth.sin(a0 * 7.0F + time * 1.1F + band * 2.0F);
                float pulse1 = 0.55F + 0.45F * Mth.sin(a1 * 7.0F + time * 1.1F + band * 2.0F);
                float alpha0 = 0.32F * w0 * pulse0;
                float alpha1 = 0.32F * w1 * pulse1;
                float rad = 55.0F;
                // 下は緑、上は紫に移る（上の端は消える）
                vertexA(buffer, m, rad, a0, base0, 0.25F, 1.0F, 0.6F, alpha0);
                vertexA(buffer, m, rad, a1, base1, 0.25F, 1.0F, 0.6F, alpha1);
                vertexA(buffer, m, rad, a1, base1 + h1, 0.55F, 0.35F, 0.95F, 0.0F);
                vertexA(buffer, m, rad, a0, base0 + h0, 0.55F, 0.35F, 0.95F, 0.0F);
                // 帯の下のふち（細く明るい）
                vertexA(buffer, m, rad, a0, base0 - 0.03F, 0.4F, 1.0F, 0.75F, 0.0F);
                vertexA(buffer, m, rad, a1, base1 - 0.03F, 0.4F, 1.0F, 0.75F, 0.0F);
                vertexA(buffer, m, rad, a1, base1, 0.4F, 1.0F, 0.75F, alpha1 * 1.3F);
                vertexA(buffer, m, rad, a0, base0, 0.4F, 1.0F, 0.75F, alpha0 * 1.3F);
            }
        }
        tesselator.end();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f m, float r, float yaw, float pitch, float[] c) {
        buffer.vertex(m, r * Mth.cos(pitch) * Mth.cos(yaw), r * Mth.sin(pitch), r * Mth.cos(pitch) * Mth.sin(yaw)).color(c[0], c[1], c[2], c[3])
                .endVertex();
    }

    private static void vertexA(BufferBuilder buffer, Matrix4f m, float r, float yaw, float pitch, float red, float green, float blue, float a) {
        buffer.vertex(m, r * Mth.cos(pitch) * Mth.cos(yaw), r * Mth.sin(pitch), r * Mth.cos(pitch) * Mth.sin(yaw)).color(red, green, blue,
                Mth.clamp(a, 0.0F, 1.0F)).endVertex();
    }

    /** 雪：まわりに雪の粒（1 tick に数個。吹雪のときは多く、横に流れる）。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }
        // 11月25日：街にロストが及んでいる間（6日目から）、コネリアに白い雪（灰のような、音のない雪）が降る
        if (ClientTowerEnv.townLost() && minecraft.level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            var random = minecraft.level.random;
            for (int i = 0; i < 2; i++) {
                minecraft.level.addParticle(ParticleTypes.WHITE_ASH, minecraft.player.getX() + (random.nextDouble() - 0.5) * 28.0,
                        minecraft.player.getY() + 3.0 + random.nextDouble() * 10.0, minecraft.player.getZ() + (random.nextDouble() - 0.5) * 28.0,
                        0.0, -0.02, 0.0);
            }
        }
        if (!"snow".equals(ClientTowerEnv.weather())) {
            return;
        }
        float blizzard = ClientTowerEnv.blizzard();
        int count = 3 + Math.round(blizzard * 14);
        var random = minecraft.level.random;
        for (int i = 0; i < count; i++) {
            double x = minecraft.player.getX() + (random.nextDouble() - 0.5) * 32.0;
            double y = minecraft.player.getY() + 4.0 + random.nextDouble() * 12.0;
            double z = minecraft.player.getZ() + (random.nextDouble() - 0.5) * 32.0;
            minecraft.level.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, 0.02 + blizzard * 0.25, -0.05 - random.nextDouble() * 0.05,
                    blizzard * 0.12);
        }
    }
}
