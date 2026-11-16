package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * 基本のHUD：HPとMPのバー、経験値のバー、中央のレベルの結晶（Phase 0-B 13章 Phase 1）。
 *
 * <p>デザインは「結晶と糸」。濃い藍色の枠に水色の結晶のふちをつけ、中央の六角の結晶（冠つき）にレベルを出す。
 * 絵は {@code textures/gui/hud.png}（作り方は {@code tools/hudgen/make_hud.py}）。同じ名前・同じ配置で
 * 描き直せば、コードを変えずに差し替えられる。</p>
 *
 * <pre>
 *                 ▲ 冠
 *   ◀[ 1840/2160 ]⬡[ 410/590 ]▶   ← 左が緑のHP、右が青のMP。中央の結晶にレベル
 *   [========== 経験値 ==========]
 *   [         ホットバー         ]
 * </pre>
 *
 * <p>動き：HPが減ると、減った分が白く残ってから縮む（残像）。回復は滑らかに増える。HPが25%を切ると赤くなり、
 * 枠が点滅する。振り分けていないポイントがあると、結晶が光る。レベルが上がると結晶と経験値のバーが白く光る。</p>
 */
public final class AmemaruHud {
    /** {@code RegisterGuiOverlaysEvent} で登録するオーバーレイ。 */
    public static final IGuiOverlay STATUS = AmemaruHud::render;

    // 絵の位置は AmemaruHudArt にまとめてある
    private static final ResourceLocation TEXTURE = AmemaruHudArt.TEXTURE;
    private static final int TEX_SIZE = AmemaruHudArt.TEX_SIZE;
    private static final int BAR_W = AmemaruHudArt.BAR_W;
    private static final int BAR_H = AmemaruHudArt.BAR_H;
    private static final int BAR_INNER = AmemaruHudArt.BAR_INNER;
    private static final int EXP_W = AmemaruHudArt.EXP_W;
    private static final int EXP_H = AmemaruHudArt.EXP_H;
    private static final int GEM_W = AmemaruHudArt.GEM_W;
    private static final int GEM_H = AmemaruHudArt.GEM_H;

    // ---- 画面上の配置（ホットバーの上辺の中央が原点）
    private static final int HOTBAR_HEIGHT = 22;
    private static final int BAR_Y = -20;
    private static final int HP_X = -91;
    private static final int MP_X = 11;
    private static final int EXP_Y = -7;
    private static final int GEM_X = -12;
    private static final int GEM_Y = -31;

    private static final float LOW_HP = 0.25F;
    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int LEVEL_COLOR = 0xFFFFFF;
    private static final int LEVEL_POINTS_COLOR = 0xFFF2A8;

    private static final BarAnimation HP_ANIM = new BarAnimation();
    private static final BarAnimation MP_ANIM = new BarAnimation();
    private static final BarAnimation EXP_ANIM = new BarAnimation();
    private static int lastLevel = -1;
    private static long levelUpAt = Long.MIN_VALUE;

    private AmemaruHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPlayerState state = ClientPlayerState.get();
        if (player == null || minecraft.options.hideGui || player.isSpectator() || !state.hasData()) {
            return;
        }
        long now = Util.getMillis();
        float hpRatio = ratio(state.hp(), state.maxHp());
        float mpRatio = ratio(state.mp(), state.maxMp());
        updateAnimations(state, hpRatio, mpRatio, now);

        float scale = (float) ClientConfig.hudScale();
        boolean numbers = ClientConfig.showNumbers();
        Font font = minecraft.font;

        gui.setupOverlayRenderState(true, false);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenWidth / 2, screenHeight - HOTBAR_HEIGHT, 0.0D);
        pose.scale(scale, scale, 1.0F);

        // 馬などに乗っているときは、バニラのジャンプのゲージが同じ場所に出るため、経験値のバーを描かない
        if (player.jumpableVehicle() == null) {
            drawExpBar(graphics, state, now);
        }
        boolean low = hpRatio < LOW_HP && state.hp() > 0;
        drawBar(graphics, HP_X, AmemaruHudArt.V_HP_FRAME, low ? AmemaruHudArt.V_HP_FILL_LOW : AmemaruHudArt.V_HP_FILL, AmemaruHudArt.V_HP_GHOST, HP_ANIM);
        if (low) {
            // 低HP：枠を赤く点滅させる
            float pulse = 0.35F + 0.45F * (0.5F + 0.5F * Mth.sin(now / 1000.0F * TWO_PI * 1.2F));
            RenderSystem.setShaderColor(1.0F, 0.25F, 0.25F, pulse);
            graphics.blit(TEXTURE, HP_X, BAR_Y, AmemaruHudArt.U_HP_GLOW, AmemaruHudArt.V_GLOW, BAR_W, BAR_H, TEX_SIZE, TEX_SIZE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        drawBar(graphics, MP_X, AmemaruHudArt.V_MP_FRAME, AmemaruHudArt.V_MP_FILL, AmemaruHudArt.V_MP_GHOST, MP_ANIM);
        drawGem(graphics, state.statPoints() > 0, now);

        if (numbers) {
            drawBarText(graphics, font, I18n.get("hud.amemaru.hp", ceil(state.hp()), ceil(state.maxHp())), HP_X + 42);
            drawBarText(graphics, font, I18n.get("hud.amemaru.mp", ceil(state.mp()), ceil(state.maxMp())), MP_X + 38);
        }
        drawLevel(graphics, font, state);

        pose.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // 空気の泡・持ち物の名前などのバニラの表示が、このHUD（結晶の冠の上まで）より上に出るようにする
        int used = HOTBAR_HEIGHT + Mth.ceil(-GEM_Y * scale) + 9;
        gui.leftHeight = Math.max(gui.leftHeight, used);
        gui.rightHeight = Math.max(gui.rightHeight, used);
    }

    // ---------------------------------------------------------------- 描画

    /** バー1本：枠 → 残像 → 中身。中身はテクスチャの左から必要な幅だけ使う。 */
    private static void drawBar(GuiGraphics graphics, int x, int vFrame, int vFill, int vGhost, BarAnimation anim) {
        graphics.blit(TEXTURE, x, BAR_Y, 0, vFrame, BAR_W, BAR_H, TEX_SIZE, TEX_SIZE);
        int ghostWidth = fillWidth(anim.ghost, BAR_INNER);
        int fillWidth = fillWidth(anim.display, BAR_INNER);
        if (ghostWidth > fillWidth) {
            graphics.blit(TEXTURE, x, BAR_Y, 0, vGhost, 2 + ghostWidth, BAR_H, TEX_SIZE, TEX_SIZE);
        }
        if (fillWidth > 0) {
            graphics.blit(TEXTURE, x, BAR_Y, 0, vFill, 2 + fillWidth, BAR_H, TEX_SIZE, TEX_SIZE);
        }
    }

    private static void drawExpBar(GuiGraphics graphics, ClientPlayerState state, long now) {
        int x = -EXP_W / 2;
        graphics.blit(TEXTURE, x, EXP_Y, 0, AmemaruHudArt.V_EXP_FRAME, EXP_W, EXP_H, TEX_SIZE, TEX_SIZE);
        int width = fillWidth(EXP_ANIM.display, AmemaruHudArt.EXP_INNER);
        if (width > 0) {
            graphics.blit(TEXTURE, x, EXP_Y, 0, AmemaruHudArt.V_EXP_FILL, 1 + width, EXP_H, TEX_SIZE, TEX_SIZE);
        }
        float flash = levelUpFlash(now);
        if (flash > 0) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, flash * 0.8F);
            graphics.blit(TEXTURE, x, EXP_Y, 0, AmemaruHudArt.V_EXP_FLASH, EXP_W, EXP_H, TEX_SIZE, TEX_SIZE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** 中央の結晶。ポイントが残っていれば光り、レベルが上がった直後は白く光る。 */
    private static void drawGem(GuiGraphics graphics, boolean pointsLeft, long now) {
        if (pointsLeft) {
            float pulse = 0.45F + 0.55F * (0.5F + 0.5F * Mth.sin(now / 1000.0F * TWO_PI * 0.7F));
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, pulse);
            graphics.blit(TEXTURE, GEM_X - 2, GEM_Y - 2, AmemaruHudArt.U_GEM_GLOW, AmemaruHudArt.V_GEM_GLOW, AmemaruHudArt.GEM_GLOW_W, AmemaruHudArt.GEM_GLOW_H, TEX_SIZE, TEX_SIZE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        graphics.blit(TEXTURE, GEM_X, GEM_Y, pointsLeft ? AmemaruHudArt.U_GEM_LIT : AmemaruHudArt.U_GEM, 0, GEM_W, GEM_H, TEX_SIZE, TEX_SIZE);
        float flash = levelUpFlash(now);
        if (flash > 0) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, flash);
            graphics.blit(TEXTURE, GEM_X, GEM_Y, AmemaruHudArt.U_GEM_FLASH, 0, GEM_W, GEM_H, TEX_SIZE, TEX_SIZE);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawBarText(GuiGraphics graphics, Font font, String text, int centerX) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, BAR_Y + 2, 0xFFFFFF, true);
    }

    /** 結晶の中のレベルの数字。黒いふちをつけて読みやすくする。 */
    private static void drawLevel(GuiGraphics graphics, Font font, ClientPlayerState state) {
        String text = Integer.toString(state.level());
        int x = -font.width(text) / 2 + 1;
        int y = GEM_Y + AmemaruHudArt.GEM_HEX_TOP + 6;
        int color = state.statPoints() > 0 ? LEVEL_POINTS_COLOR : LEVEL_COLOR;
        graphics.drawString(font, text, x + 1, y, 0x0A0F1C, false);
        graphics.drawString(font, text, x - 1, y, 0x0A0F1C, false);
        graphics.drawString(font, text, x, y + 1, 0x0A0F1C, false);
        graphics.drawString(font, text, x, y - 1, 0x0A0F1C, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    // ---------------------------------------------------------------- 動き

    private static void updateAnimations(ClientPlayerState state, float hpRatio, float mpRatio, long now) {
        HP_ANIM.update(hpRatio, now);
        MP_ANIM.update(mpRatio, now);
        int level = state.level();
        if (lastLevel >= 0 && level > lastLevel) {
            levelUpAt = now;
            EXP_ANIM.reset(0.0F, now);
        }
        lastLevel = level;
        EXP_ANIM.update(state.expProgress(), now);
    }

    /** レベルが上がった直後の光の強さ（1 → 0、0.9秒）。 */
    private static float levelUpFlash(long now) {
        long elapsed = now - levelUpAt;
        if (elapsed < 0 || elapsed > 900) {
            return 0.0F;
        }
        return 1.0F - elapsed / 900.0F;
    }

    /** ログアウトしたときなどに、動きの状態を消す。 */
    public static void resetAnimations() {
        HP_ANIM.clear();
        MP_ANIM.clear();
        EXP_ANIM.clear();
        lastLevel = -1;
        levelUpAt = Long.MIN_VALUE;
    }

    private static float ratio(double value, double max) {
        if (max <= 0) {
            return 0.0F;
        }
        return (float) Mth.clamp(value / max, 0.0, 1.0);
    }

    private static int fillWidth(float ratio, int inner) {
        if (ratio <= 0.0F) {
            return 0;
        }
        return Math.max(1, Math.round(Mth.clamp(ratio, 0.0F, 1.0F) * inner));
    }

    private static long ceil(double value) {
        return (long) Math.ceil(Math.max(0.0, value));
    }

    /**
     * バーの表示の動き。減ったときは中身がすぐ減り、減った分（残像）が少し残ってから縮む。
     * 増えたときは中身が滑らかに増える。時間で動かすため、フレームレートに左右されない。
     */
    static final class BarAnimation {
        private static final long GHOST_HOLD_MS = 450;
        private static final float GHOST_SPEED = 0.6F;
        private static final float RISE_RATE = 10.0F;
        private static final float RISE_MIN_SPEED = 0.5F;

        float display = -1.0F;
        float ghost = -1.0F;
        private long holdUntil;
        private long last;

        void update(float target, long now) {
            if (display < 0.0F) {
                reset(target, now);
                return;
            }
            float dt = Math.min(0.25F, Math.max(0.0F, (now - last) / 1000.0F));
            last = now;
            if (target < display) {
                ghost = Math.max(ghost, display);
                display = target;
                holdUntil = now + GHOST_HOLD_MS;
            } else if (target > display) {
                float step = Math.max(RISE_MIN_SPEED * dt, (target - display) * Math.min(1.0F, RISE_RATE * dt));
                display = Math.min(target, display + step);
            }
            if (now >= holdUntil && ghost > display) {
                ghost = Math.max(display, ghost - GHOST_SPEED * dt);
            }
            if (ghost < display) {
                ghost = display;
            }
        }

        void reset(float value, long now) {
            display = value;
            ghost = value;
            holdUntil = now;
            last = now;
        }

        void clear() {
            display = -1.0F;
            ghost = -1.0F;
        }
    }
}
