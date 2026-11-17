package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/**
 * スキルのHUD：ホットバーの左の六角のスキル枠6つ（蜂の巣の形）、専用ゲージ、詠唱のバー、連携の大きな文字。
 *
 * <pre>
 *   誓気 ━━━━━━━━ 45
 *   ⬡R ⬡G ⬡V
 *     ⬡Z ⬡X ⬡C        [ HP ]⬡[ MP ]
 *                     [ ホットバー ]
 * </pre>
 *
 * <p>枠には、アイコン・キー・クールダウン（影と残りの秒）を出す。MPやゲージが足りないと青く沈み、使えるようになった瞬間に光る。
 * 詠唱中・溜め中の枠は金のふちになり、画面の中央の少し下に詠唱のバー（溜めのときは溜めのバー）が出る。
 * 溜めきると、バーが白く脈を打ち「最大！」と出る。</p>
 */
public final class SkillHud {
    public static final IGuiOverlay OVERLAY = SkillHud::render;

    private static final ResourceLocation TEXTURE = SkillHudArt.TEXTURE;
    private static final int TEX = SkillHudArt.TEX_SIZE;
    private static final int STEP_X = 25;
    private static final int ROW_OFFSET = 12;
    private static final int ROW_Y = 22;
    private static final int BLOCK_W = ROW_OFFSET + 2 * STEP_X + SkillHudArt.HEX_W;
    private static final int BLOCK_H = ROW_Y + SkillHudArt.HEX_H;
    private static final int GAP_FROM_HOTBAR = 36;
    private static final long FLASH_MS = 350;

    private static final long[] READY_AT = new long[6];
    private static final boolean[] WAS_COOLING = new boolean[6];

    private SkillHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPlayerState state = ClientPlayerState.get();
        if (player == null || minecraft.options.hideGui || player.isSpectator() || !state.hasData()) {
            return;
        }
        Font font = minecraft.font;
        CombatTextRenderer.renderBanner(graphics, font, screenWidth, screenHeight);
        if (!state.hasJob()) {
            return;
        }
        gui.setupOverlayRenderState(true, false);
        RenderSystem.enableBlend();
        int baseX = blockX(screenWidth);
        int baseY = screenHeight - BLOCK_H - 2;
        long now = Util.getMillis();
        boolean altMode = ClientConfig.skillControl() == ClientConfig.SkillControl.HOTBAR_ALT;
        boolean altHeld = altMode && SkillInput.altDown(minecraft);

        for (int slot = 0; slot < 6; slot++) {
            int x = baseX + (slot < 3 ? slot * STEP_X : ROW_OFFSET + (slot - 3) * STEP_X);
            int y = baseY + (slot < 3 ? 0 : ROW_Y);
            drawSlot(graphics, font, state, slot, x, y, now, altMode, altHeld, partialTick);
        }
        drawGauge(graphics, font, state, baseX, baseY);
        drawCastBar(graphics, font, state, screenWidth, screenHeight, partialTick);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** スキル枠のかたまりの左端（狭い画面では左に寄せる）。 */
    private static int blockX(int screenWidth) {
        return Math.max(2, screenWidth / 2 - 91 - GAP_FROM_HOTBAR - BLOCK_W);
    }

    private static void drawSlot(GuiGraphics graphics, Font font, ClientPlayerState state, int slot, int x, int y, long now,
                                 boolean altMode, boolean altHeld, float partialTick) {
        // 文字（キーの名前・秒）を描くと、半透明の混ぜ合わせが切れる。枠ごとに、絵を描く前につけ直す
        blend();
        int skillIndex = state.loadout(slot);
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(skillIndex);
        if (altHeld) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.8F);
            graphics.blit(TEXTURE, x - 2, y - 2, SkillHudArt.U_HEX_GLOW, SkillHudArt.V_HEX, SkillHudArt.HEX_GLOW_W,
                    SkillHudArt.HEX_GLOW_H, TEX, TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (skill == null) {
            graphics.blit(TEXTURE, x, y, SkillHudArt.U_HEX_EMPTY, SkillHudArt.V_HEX, SkillHudArt.HEX_W, SkillHudArt.HEX_H, TEX, TEX);
            drawKey(graphics, font, slot, x, y, altMode);
            return;
        }
        graphics.blit(TEXTURE, x, y, SkillHudArt.U_HEX, SkillHudArt.V_HEX, SkillHudArt.HEX_W, SkillHudArt.HEX_H, TEX, TEX);

        int rank = Math.max(1, Math.min(state.rank(skillIndex), skill.maxRank()));
        int r = rank - 1;
        int mpCost = r < skill.mpCost().length ? skill.mpCost()[r] : 0;
        int gaugeCost = r < skill.gaugeCost().length ? skill.gaugeCost()[r] : 0;
        boolean affordable = state.mp() + 1.0E-6 >= mpCost && state.gauge() + 1.0E-6 >= gaugeCost;
        int remaining = state.cooldownRemaining(skillIndex);
        int total = state.cooldownTotal(skillIndex);
        boolean casting = state.castSkill() == skillIndex;

        // アイコン（32×32 の絵の、ふち2ピクセルを除いた中を 16×16 で描く）
        if (!affordable) {
            RenderSystem.setShaderColor(0.55F, 0.62F, 0.85F, 1.0F);
        }
        graphics.blit(skill.icon(), x + 4, y + 5, 16, 16, 2, 2, 28, 28, 32, 32);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (!affordable) {
            graphics.blit(TEXTURE, x, y, SkillHudArt.U_HEX_DIM, SkillHudArt.V_HEX, SkillHudArt.HEX_W, SkillHudArt.HEX_H, TEX, TEX);
        }

        // クールダウン：残りの割合だけ、上から影を落とす
        if (remaining > 0 && total > 0) {
            float ratio = Mth.clamp((remaining - partialTick) / total, 0.0F, 1.0F);
            int shade = Math.max(1, Math.round(SkillHudArt.HEX_H * ratio));
            graphics.blit(TEXTURE, x, y, SkillHudArt.U_HEX_SHADE, SkillHudArt.V_HEX, SkillHudArt.HEX_W, shade, TEX, TEX);
            String seconds = remaining >= 200 ? Integer.toString(Mth.ceil(remaining / 20.0F))
                    : String.format(Locale.ROOT, "%.1f", remaining / 20.0F);
            int width = font.width(seconds);
            graphics.drawString(font, seconds, x + (SkillHudArt.HEX_W - width) / 2, y + 9, 0xFFFFFF, true);
            WAS_COOLING[slot] = true;
        } else if (WAS_COOLING[slot]) {
            WAS_COOLING[slot] = false;
            READY_AT[slot] = now;
        }
        long sinceReady = now - READY_AT[slot];
        if (sinceReady >= 0 && sinceReady < FLASH_MS) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F - sinceReady / (float) FLASH_MS);
            graphics.blit(TEXTURE, x, y, SkillHudArt.U_HEX_FLASH, SkillHudArt.V_HEX, SkillHudArt.HEX_W, SkillHudArt.HEX_H, TEX, TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (casting) {
            float pulse = 0.6F + 0.4F * Mth.sin(now / 1000.0F * Mth.TWO_PI * 2.0F);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, pulse);
            graphics.blit(TEXTURE, x, y, SkillHudArt.U_HEX_CAST, SkillHudArt.V_HEX, SkillHudArt.HEX_W, SkillHudArt.HEX_H, TEX, TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        drawKey(graphics, font, slot, x, y, altMode);
    }

    /** 枠の右下に、キーの名前を小さく出す。 */
    private static void drawKey(GuiGraphics graphics, Font font, int slot, int x, int y, boolean altMode) {
        String key = altMode ? Integer.toString(slot + 1) : KeyBindings.SKILLS[slot].getTranslatedKeyMessage().getString();
        if (key.length() > 3) {
            key = key.substring(0, 3);
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + SkillHudArt.HEX_W - 2, y + SkillHudArt.HEX_H - 7, 200.0F);
        pose.scale(0.75F, 0.75F, 1.0F);
        int width = font.width(key);
        graphics.drawString(font, key, -width, 0, 0xFFE8F4FF, true);
        pose.popPose();
    }

    /** 専用ゲージ（誓気など）。職業にゲージがなければ描かない。 */
    private static void drawGauge(GuiGraphics graphics, Font font, ClientPlayerState state, int baseX, int baseY) {
        SyncDefinitionsS2C.JobInfo job = ClientDefinitions.get().job(state.jobIndex());
        if (job == null || !job.hasGauge() || state.gaugeMax() <= 0) {
            return;
        }
        int x = baseX;
        int y = baseY - 9;
        blend();
        graphics.blit(TEXTURE, x, y, 0, SkillHudArt.V_GAUGE_FRAME, SkillHudArt.GAUGE_W, SkillHudArt.GAUGE_H, TEX, TEX);
        float ratio = Mth.clamp(state.gauge() / state.gaugeMax(), 0.0F, 1.0F);
        int fill = Math.round(ratio * SkillHudArt.GAUGE_INNER);
        if (fill > 0) {
            int color = job.gaugeColor();
            RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
            graphics.blit(TEXTURE, x, y, 0, SkillHudArt.V_GAUGE_FILL, 1 + fill, SkillHudArt.GAUGE_H, TEX, TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        String label = job.gaugeName() + " " + (int) Math.floor(state.gauge());
        graphics.drawString(font, label, x, y - 9, 0xFFFFE6A8, true);
    }

    /** 詠唱のバー（画面の中央の少し下）。 */
    private static void drawCastBar(GuiGraphics graphics, Font font, ClientPlayerState state, int screenWidth, int screenHeight,
                                    float partialTick) {
        int skillIndex = state.castSkill();
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(skillIndex);
        if (skill == null) {
            return;
        }
        int x = (screenWidth - SkillHudArt.CAST_W) / 2;
        int y = screenHeight / 2 + 26;
        blend();
        graphics.blit(TEXTURE, x, y, 0, SkillHudArt.V_CAST_FRAME, SkillHudArt.CAST_W, SkillHudArt.CAST_H, TEX, TEX);
        float progress = state.castProgress(partialTick);
        int fill = Math.round(progress * SkillHudArt.CAST_INNER);
        boolean charging = state.charging();
        boolean full = charging && progress >= 0.999F;
        if (fill > 0) {
            if (charging) {
                // 溜め：橙から白へ。溜めきったら脈を打つ
                float pulse = full ? 0.75F + 0.25F * Mth.sin(Util.getMillis() / 90.0F) : 1.0F;
                float g = Mth.lerp(progress, 0.62F, 1.0F) * pulse;
                float b = Mth.lerp(progress, 0.3F, 1.0F) * pulse;
                RenderSystem.setShaderColor(pulse, g, b, 1.0F);
            }
            graphics.blit(TEXTURE, x, y, 0, SkillHudArt.V_CAST_FILL, 1 + fill, SkillHudArt.CAST_H, TEX, TEX);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        String name = skill.name();
        if (charging) {
            name += "  " + net.minecraft.network.chat.Component.translatable(full ? "hud.amemaru.charge_full" : "hud.amemaru.charge").getString();
        }
        graphics.drawString(font, name, (screenWidth - font.width(name)) / 2, y - 10, full ? 0xFFFFFFFF : 0xFFF2D7FF, true);
    }

    private static void blend() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }

    /** このHUDが画面の下から使う高さ（チャットなどを、これより上に出すため）。 */
    public static int reservedHeight() {
        ClientPlayerState state = ClientPlayerState.get();
        if (!state.hasJob()) {
            return 0;
        }
        return BLOCK_H + 2 + (state.gaugeMax() > 0 ? 20 : 0);
    }

    /** スキル枠のかたまりの右端（チャットを避けるかどうかの判断に使う）。 */
    public static int blockRight(int screenWidth) {
        return blockX(screenWidth) + BLOCK_W;
    }

    public static void resetAnimations() {
        java.util.Arrays.fill(READY_AT, 0L);
        java.util.Arrays.fill(WAS_COOLING, false);
    }
}
