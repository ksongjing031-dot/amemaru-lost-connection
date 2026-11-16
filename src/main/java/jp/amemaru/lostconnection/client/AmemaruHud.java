package jp.amemaru.lostconnection.client;

import com.mojang.blaze3d.vertex.PoseStack;
import jp.amemaru.lostconnection.core.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import javax.annotation.Nullable;

/**
 * 基本のHUD：HPとMPのバー、経験値のバー、レベルの数字（Phase 0-B 13章 Phase 1）。
 *
 * <p>バニラのハート・防御・満腹度・経験値の表示は {@link ClientForgeEvents} で隠し、同じ場所にこのHUDを描く。
 * 位置はホットバーの上が基準で、configの {@code hud.scale} で大きさを変えられる。</p>
 *
 * <pre>
 *   [ HP 1840/2160 ]  12  [ MP 410/590 ]   ← バー（高さ10）。真ん中がレベル
 *   [=========== 経験値のバー ==========]   ← 高さ5（バニラの経験値バーと同じ位置）
 *   [            ホットバー             ]
 * </pre>
 */
public final class AmemaruHud {
    /** {@code RegisterGuiOverlaysEvent} で登録するオーバーレイ。 */
    public static final IGuiOverlay STATUS = AmemaruHud::render;

    private static final int HOTBAR_HEIGHT = 22;
    private static final int BAR_WIDTH = 80;
    private static final int BAR_HEIGHT = 10;
    private static final int BAR_Y = -18;
    private static final int EXP_Y = -7;
    private static final int EXP_WIDTH = 182;
    private static final int EXP_HEIGHT = 5;

    private static final int FRAME = 0xC0000000;
    private static final int EMPTY = 0xFF2A2A2A;
    private static final int HP_TOP = 0xFFE05050;
    private static final int HP_BOTTOM = 0xFFA02828;
    private static final int MP_TOP = 0xFF5A8CFF;
    private static final int MP_BOTTOM = 0xFF2A4CB0;
    private static final int EXP_TOP = 0xFF9CFF5A;
    private static final int EXP_BOTTOM = 0xFF4CA02A;
    private static final int EXP_MAX_TOP = 0xFFFFD35A;
    private static final int EXP_MAX_BOTTOM = 0xFFC08A1E;
    private static final int LEVEL_COLOR = 0x80FF20;
    private static final int LEVEL_POINTS_COLOR = 0xFFFF55;

    private AmemaruHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPlayerState state = ClientPlayerState.get();
        if (player == null || minecraft.options.hideGui || player.isSpectator() || !state.hasData()) {
            return;
        }

        float scale = (float) ClientConfig.hudScale();
        boolean numbers = ClientConfig.showNumbers();
        Font font = minecraft.font;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenWidth / 2, screenHeight - HOTBAR_HEIGHT, 0.0D);
        pose.scale(scale, scale, 1.0F);

        // 馬などに乗っているときは、バニラのジャンプのゲージが同じ場所に出るため、経験値のバーを描かない
        if (player.jumpableVehicle() == null) {
            drawExpBar(graphics, state);
        }

        double maxHp = Math.max(1.0, state.maxHp());
        double maxMp = Math.max(1.0, state.maxMp());
        drawBar(graphics, font, -91, BAR_Y, state.hp() / maxHp, HP_TOP, HP_BOTTOM,
                numbers ? I18n.get("hud.amemaru.hp", ceil(state.hp()), ceil(maxHp)) : null);
        drawBar(graphics, font, 91 - BAR_WIDTH, BAR_Y, state.mp() / maxMp, MP_TOP, MP_BOTTOM,
                numbers ? I18n.get("hud.amemaru.mp", ceil(state.mp()), ceil(maxMp)) : null);
        drawLevel(graphics, font, state);

        pose.popPose();

        // 空気の泡などのバニラの表示が、このHUDの上に重なるようにする
        int used = HOTBAR_HEIGHT + Mth.ceil(-BAR_Y * scale) + 10;
        gui.leftHeight = Math.max(gui.leftHeight, used);
        gui.rightHeight = Math.max(gui.rightHeight, used);
    }

    private static void drawBar(GuiGraphics graphics, Font font, int x, int y, double ratio,
                                int topColor, int bottomColor, @Nullable String text) {
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, FRAME);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, EMPTY);
        int filled = (int) Math.round(Mth.clamp(ratio, 0.0, 1.0) * BAR_WIDTH);
        if (filled > 0) {
            graphics.fillGradient(x, y, x + filled, y + BAR_HEIGHT, topColor, bottomColor);
        }
        if (text != null) {
            int textX = x + (BAR_WIDTH - font.width(text)) / 2;
            graphics.drawString(font, text, textX, y + 1, 0xFFFFFF, true);
        }
    }

    private static void drawExpBar(GuiGraphics graphics, ClientPlayerState state) {
        int x = -EXP_WIDTH / 2;
        graphics.fill(x - 1, EXP_Y - 1, x + EXP_WIDTH + 1, EXP_Y + EXP_HEIGHT + 1, FRAME);
        graphics.fill(x, EXP_Y, x + EXP_WIDTH, EXP_Y + EXP_HEIGHT, EMPTY);
        int filled = Math.round(state.expProgress() * EXP_WIDTH);
        if (filled > 0) {
            boolean max = state.isMaxLevel();
            graphics.fillGradient(x, EXP_Y, x + filled, EXP_Y + EXP_HEIGHT,
                    max ? EXP_MAX_TOP : EXP_TOP, max ? EXP_MAX_BOTTOM : EXP_BOTTOM);
        }
    }

    /** レベルの数字。振り分けていないポイントがあるときは黄色にする。 */
    private static void drawLevel(GuiGraphics graphics, Font font, ClientPlayerState state) {
        String text = Integer.toString(state.level());
        int x = -font.width(text) / 2;
        int y = BAR_Y + 1;
        int color = state.statPoints() > 0 ? LEVEL_POINTS_COLOR : LEVEL_COLOR;
        // バニラの経験値レベルと同じく、黒いふちをつける
        graphics.drawString(font, text, x + 1, y, 0x000000, false);
        graphics.drawString(font, text, x - 1, y, 0x000000, false);
        graphics.drawString(font, text, x, y + 1, 0x000000, false);
        graphics.drawString(font, text, x, y - 1, 0x000000, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static long ceil(double value) {
        return (long) Math.ceil(Math.max(0.0, value));
    }
}
