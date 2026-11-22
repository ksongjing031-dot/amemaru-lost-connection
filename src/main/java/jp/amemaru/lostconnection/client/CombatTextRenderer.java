package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.AmemaruMod;
import jp.amemaru.lostconnection.client.fx.StatusVisuals;
import jp.amemaru.lostconnection.combat.StatusService;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.entity.TrainingDummy;
import jp.amemaru.lostconnection.network.s2c.CombatTextS2C;
import jp.amemaru.lostconnection.network.s2c.StatusFxS2C;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 頭の上の数字（ダメージ・回復・連携。Phase 0-B 5章 {@code CombatText}）と、連携が決まったときの大きな文字（クライアント専用）。
 *
 * <p>数字は相手の頭の上に出て、少し上がりながら消える。会心は大きく金色、受けたダメージは赤、回復は緑、MPは青。
 * 訓練人形には、HPのバーも出す。</p>
 */
@Mod.EventBusSubscriber(modid = AmemaruMod.MOD_ID, value = Dist.CLIENT)
public final class CombatTextRenderer {
    private static final long LIFE_MS = 1100;
    private static final long BANNER_MS = 1400;
    private static final int MAX_TEXTS = 64;
    /** この時間より前に出た文字とは、重ならないように段をずらす。 */
    private static final long STACK_MS = 450;
    /** 言葉（凍結！・砕けた！・連携！など）は、数字の段より上の段に出す。 */
    private static final double WORD_LANE = 0.42;
    private static final double WORD_STEP = 0.36;
    private static final double NUMBER_STEP = 0.26;

    /** 浮かぶ文字。{@code word} は言葉（数字ではない）。 */
    private record Floating(int entityId, boolean word, Vec3 position, String text, int color, int outline, float scale, long start) {
    }

    private record Banner(String text, int color, float scale, long start) {
    }

    private static final List<Floating> TEXTS = new ArrayList<>();
    @Nullable
    private static Banner banner;

    private CombatTextRenderer() {
    }

    // ---------------------------------------------------------------- 受け取り

    public static void handle(CombatTextS2C message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(message.entityId());
        boolean involved = false;
        if (minecraft.player != null) {
            for (int id : message.involved()) {
                if (id == minecraft.player.getId()) {
                    involved = true;
                    break;
                }
            }
        }
        long now = Util.getMillis();
        String amount = Long.toString(Math.round(message.amount()));
        switch (message.kind()) {
            case LINK -> {
                if (involved) {
                    banner = new Banner(Component.translatable("combat.amemaru.link").getString(), 0xFFC94D, 2.6F, now);
                }
                addWord(entity, Component.translatable("combat.amemaru.link").getString(), 0xFFC94D, 0x5A2A00, 1.5F, now);
                return;
            }
            case LINK3 -> {
                if (involved) {
                    banner = new Banner(Component.translatable("combat.amemaru.link3").getString(), 0xFF8A3D, 3.0F, now);
                }
                addWord(entity, Component.translatable("combat.amemaru.link3").getString(), 0xFF8A3D, 0x4A1600, 1.8F, now);
                return;
            }
            case SUPPORT -> {
                if (involved) {
                    banner = new Banner(Component.translatable("combat.amemaru.support").getString(), 0xFFE066, 2.0F, now);
                }
                addWord(entity, Component.translatable("combat.amemaru.support").getString(), 0xFFE066, 0x403000, 1.1F, now);
                return;
            }
            case PARRY -> {
                if (entity == minecraft.player) {
                    banner = new Banner(Component.translatable("combat.amemaru.parry").getString(), 0xD6F6FF, 2.2F, now);
                }
                addWord(entity, Component.translatable("combat.amemaru.parry").getString(), 0xD6F6FF, 0x0A2A3A, 1.4F, now);
                return;
            }
            case SHATTER -> {
                addWord(entity, Component.translatable("combat.amemaru.shatter").getString(), 0xBFF2FF, 0x06304A, 1.5F, now);
                return;
            }
            case FROZEN -> {
                addWord(entity, Component.translatable("combat.amemaru.frozen").getString(), 0x9FE6FF, 0x06304A, 1.2F, now);
                return;
            }
            case DODGE -> {
                addWord(entity, Component.translatable("combat.amemaru.dodge").getString(), 0xE8F4FF, 0x1A2A3A, 1.2F, now);
                return;
            }
            case MISS -> {
                addWord(entity, Component.translatable("combat.amemaru.miss").getString(), 0xC8C8D8, 0x1A1A2A, 1.1F, now);
                return;
            }
            case CHAIN -> {
                // 連撃（モンク）：本人の画面に大きく
                int count = (int) Math.round(message.amount());
                String word = Component.translatable("combat.amemaru.chain", count).getString();
                if (entity == minecraft.player) {
                    banner = new Banner(word, count >= 5 ? 0xFFE066 : 0xFFB46E, count >= 5 ? 2.8F : 1.8F, now);
                }
                addWord(entity, word, 0xFFB46E, 0x4A1600, 1.2F + 0.08F * count, now);
                return;
            }
            case REVIVE -> {
                addWord(entity, Component.translatable("combat.amemaru.revive").getString(), 0xB8FFD0, 0x003A1A, 1.5F, now);
                return;
            }
            case SAVED -> {
                if (entity == minecraft.player) {
                    banner = new Banner(Component.translatable("combat.amemaru.saved").getString(), 0xFFF0A0, 2.4F, now);
                }
                addWord(entity, Component.translatable("combat.amemaru.saved").getString(), 0xFFF0A0, 0x3A2A00, 1.5F, now);
                return;
            }
            default -> {
            }
        }
        if (!ClientConfig.showDamageNumbers()) {
            return;
        }
        switch (message.kind()) {
            case DAMAGE -> addAbove(entity, amount, 0xFFFFFF, 0x1A1A1A, 1.0F, now);
            case CRIT -> addAbove(entity, amount + "!", 0xFFD84A, 0x5A2A00, 1.45F, now);
            case TAKEN -> addAbove(entity, "-" + amount, 0xFF6060, 0x3A0000, 1.0F, now);
            case HEAL -> addAbove(entity, "+" + amount, 0x7CFF8A, 0x003A0A, 1.1F, now);
            case MP -> addAbove(entity, "+" + amount + " MP", 0x7CC4FF, 0x00203A, 1.0F, now);
            case SHARED -> addAbove(entity, "-" + amount + " " + Component.translatable("combat.amemaru.shared").getString(),
                    0x9FE6FF, 0x0A2A3A, 0.9F, now);
            case ABSORBED -> addAbove(entity, amount + " " + Component.translatable("combat.amemaru.absorbed").getString(),
                    0xFFF2A8, 0x3A2A00, 0.9F, now);
            case POISON -> addAbove(entity, amount, 0x9CF060, 0x143A00, 0.9F, now);
            default -> {
            }
        }
    }

    /** 数字（ダメージ・回復など）。少し横にばらけ、同時に出た数字とは段をずらす。 */
    private static void addAbove(@Nullable Entity entity, String text, int color, int outline, float scale, long now) {
        add(entity, false, text, color, outline, scale, now);
    }

    /** 言葉（凍結！・砕けた！・連携！など）。数字の上の段に、まっすぐ出す。 */
    private static void addWord(@Nullable Entity entity, String text, int color, int outline, float scale, long now) {
        add(entity, true, text, color, outline, scale, now);
    }

    private static void add(@Nullable Entity entity, boolean word, String text, int color, int outline, float scale, long now) {
        if (entity == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // 同じ相手の上に少し前に出た文字（同じ種類）があれば、重ならないよう上の段にずらす
        int recent = 0;
        for (Floating other : TEXTS) {
            if (other.entityId() == entity.getId() && other.word() == word && now - other.start() < STACK_MS) {
                recent++;
            }
        }
        double lift = word ? WORD_LANE + Math.min(recent, 3) * WORD_STEP : (recent % 3) * NUMBER_STEP;
        double spread = word ? 0.0 : 0.6;
        double jitterX = (minecraft.level.random.nextDouble() - 0.5) * spread;
        double jitterZ = (minecraft.level.random.nextDouble() - 0.5) * spread;
        // 頭の上に状態の印（凍え・星痕など）があれば、その上から出す
        double base = entity.getBbHeight() + 0.45 + StatusVisuals.markHeight(entity.getId());
        Vec3 at = new Vec3(entity.getX() + jitterX, entity.getY() + base + lift, entity.getZ() + jitterZ);
        if (TEXTS.size() >= MAX_TEXTS) {
            TEXTS.remove(0);
        }
        TEXTS.add(new Floating(entity.getId(), word, at, text, color, outline, scale, now));
    }

    public static void reset() {
        TEXTS.clear();
        banner = null;
    }

    // ---------------------------------------------------------------- 世界の中に描く

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.options.hideGui) {
            return;
        }
        long now = Util.getMillis();
        TEXTS.removeIf(text -> now - text.start() > LIFE_MS);
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        Font font = minecraft.font;
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();

        for (Floating text : TEXTS) {
            float age = (now - text.start()) / (float) LIFE_MS;
            float rise = 0.6F * (1.0F - (1.0F - age) * (1.0F - age));
            float pop = age < 0.12F ? 1.0F + (0.12F - age) * 3.0F : 1.0F;
            int alpha = (int) (255 * Mth.clamp((1.0F - age) * 2.0F, 0.0F, 1.0F));
            if (alpha < 8) {
                continue;
            }
            Vec3 at = text.position().add(0.0, rise, 0.0);
            drawBillboard(pose, camera, cameraPos, at, font, buffers, text.text(), (alpha << 24) | text.color(),
                    (alpha << 24) | text.outline(), 0.026F * text.scale() * pop);
        }

        // 訓練人形のHP
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof TrainingDummy dummy && dummy.distanceToSqr(cameraPos) < 24 * 24) {
                String label = (long) Math.ceil(dummy.getDummyHp()) + " / " + (long) dummy.getDummyMaxHp();
                if (dummy.getDummyDefense() > 0) {
                    label += "  " + Component.translatable("stat.amemaru.defense").getString() + " " + dummy.getDummyDefense();
                    // 防御崩しの間は、下がった後の防御も並べる（サーバーの計算と同じ −25%）
                    if (StatusVisuals.has(dummy.getId(), StatusFxS2C.Kind.ARMOR_BREAK)) {
                        label += "→" + Math.round(dummy.getDummyDefense() * (1.0 - StatusService.ARMOR_BREAK_DEFENSE));
                    }
                }
                Vec3 at = new Vec3(dummy.getX(), dummy.getY() + dummy.getBbHeight() + 0.25, dummy.getZ());
                drawBillboard(pose, camera, cameraPos, at, font, buffers, label, 0xFFE8F4FF, 0xFF0A0F1C, 0.02F);
            }
        }
        buffers.endBatch();
    }

    private static void drawBillboard(PoseStack pose, Camera camera, Vec3 cameraPos, Vec3 at, Font font,
                                      MultiBufferSource.BufferSource buffers, String text, int color, int outline, float scale) {
        pose.pushPose();
        pose.translate(at.x - cameraPos.x, at.y - cameraPos.y, at.z - cameraPos.z);
        pose.mulPose(camera.rotation());
        pose.scale(-scale, -scale, scale);
        FormattedCharSequence sequence = Component.literal(text).getVisualOrderText();
        float width = font.width(sequence);
        font.drawInBatch8xOutline(sequence, -width / 2.0F, 0.0F, color, outline, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    // ---------------------------------------------------------------- 画面の大きな文字

    /** 連携が決まったときの大きな文字（SkillHud から呼ぶ）。 */
    public static void renderBanner(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        Banner current = banner;
        if (current == null) {
            return;
        }
        long elapsed = Util.getMillis() - current.start();
        if (elapsed > BANNER_MS) {
            banner = null;
            return;
        }
        float t = elapsed / (float) BANNER_MS;
        float appear = Mth.clamp(t / 0.12F, 0.0F, 1.0F);
        float fade = Mth.clamp((1.0F - t) / 0.3F, 0.0F, 1.0F);
        float scale = current.scale() * (1.0F + (1.0F - appear) * 0.6F);
        int alpha = (int) (255 * Math.min(appear, fade));
        if (alpha < 8) {
            return;
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenWidth / 2.0F, screenHeight * 0.28F, 0.0F);
        pose.scale(scale, scale, 1.0F);
        int width = font.width(current.text());
        int color = (alpha << 24) | current.color();
        int shadow = (alpha << 24) | 0x1A0E00;
        graphics.drawString(font, current.text(), -width / 2 + 1, -4 + 1, shadow, false);
        graphics.drawString(font, current.text(), -width / 2, -4, color, false);
        pose.popPose();
    }
}
