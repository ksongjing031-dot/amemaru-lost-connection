package jp.amemaru.lostconnection.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Map;

/**
 * ダウンの画面（Phase 4）。
 *
 * <ul>
 *   <li>自分がダウンしている：画面のふちを暗い赤にし、真ん中に「ダウン」と、教会へ戻るまでの秒数と残りのバー、
 *       「仲間がそばでしゃがむと起き上がれます」。だれかが起こしていれば、その人の名前と、起こした量のバー。
 *       その下に「［H］を長押しでリタイア」と、押している間はたまる量のバー（11月22日）。</li>
 *   <li>自分が仲間を起こしている：照準の下に「〇〇を起こしています」と、起こした量のバー。</li>
 *   <li>そばにダウンしている人がいる：照準の下に「しゃがむと〇〇を起こせます」。</li>
 * </ul>
 */
public final class DownHud {
    public static final IGuiOverlay OVERLAY = DownHud::render;

    /** 手がかりを出す距離（起こせる距離 2.5 より少し広く） */
    private static final double HINT_RANGE = 3.5;
    private static final int BAR_W = 150;
    private static final int RED = 0xFFFF6A5A;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int DIM = 0xFFC8CEDC;
    private static final int AQUA = 0xFF8FD3FF;
    private static final int GOLD = 0xFFFFD27A;

    private DownHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }
        ClientDown.Info own = ClientDown.get(player.getId());
        if (own != null) {
            renderDown(graphics, minecraft, own, partialTick, width, height);
        } else {
            renderHelper(graphics, minecraft, player, partialTick, width, height);
        }
    }

    /** 自分がダウンしている。 */
    private static void renderDown(GuiGraphics graphics, Minecraft minecraft, ClientDown.Info info, float partialTick, int width,
                                   int height) {
        Font font = minecraft.font;
        // ふちを暗い赤に（上下は濃く、左右は帯を重ねる）
        graphics.fill(0, 0, width, height, 0x38200008);
        graphics.fillGradient(0, 0, width, height / 3, 0xA0180006, 0x00180006);
        graphics.fillGradient(0, height - height / 3, width, height, 0x00180006, 0xA0180006);
        for (int i = 0; i < 6; i++) {
            int alpha = 0x30 - i * 0x08;
            int band = width / 40;
            graphics.fill(i * band, 0, (i + 1) * band, height, alpha << 24 | 0x180006);
            graphics.fill(width - (i + 1) * band, 0, width - i * band, height, alpha << 24 | 0x180006);
        }
        int cx = width / 2;
        int y = height / 2 - 44;
        // 「ダウン」（2倍の大きさ）
        Component title = Component.translatable("hud.amemaru.down.title");
        graphics.pose().pushPose();
        graphics.pose().translate(cx, y, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawString(font, title, -font.width(title) / 2, 0, RED, true);
        graphics.pose().popPose();
        y += 24;
        int seconds = info.secondsLeftNow();
        Component left = Component.translatable("hud.amemaru.down.left", seconds);
        graphics.drawString(font, left, cx - font.width(left) / 2, y, WHITE, true);
        y += 12;
        drawBar(graphics, cx - BAR_W / 2, y, BAR_W, info.remainingNow(), RED);
        y += 10;
        Entity reviver = info.reviverId() >= 0 && minecraft.level != null ? minecraft.level.getEntity(info.reviverId()) : null;
        if (reviver != null) {
            Component text = Component.translatable("hud.amemaru.down.being_revived", reviver.getDisplayName());
            graphics.drawString(font, text, cx - font.width(text) / 2, y, AQUA, true);
            y += 12;
            drawBar(graphics, cx - BAR_W / 2, y, BAR_W, info.progressNow(partialTick), AQUA);
        } else {
            Component hint = Component.translatable("hud.amemaru.down.hint");
            graphics.drawString(font, hint, cx - font.width(hint) / 2, y, DIM, true);
        }
        // リタイア（11月22日のご指示）：キーを押し続けると、教会へ戻る
        y += reviver != null ? 12 : 16;
        if (DownClientEvents.retireSent()) {
            Component text = Component.translatable("hud.amemaru.down.retiring");
            graphics.drawString(font, text, cx - font.width(text) / 2, y, GOLD, true);
            return;
        }
        Component retire = Component.translatable("hud.amemaru.down.retire", KeyBindings.RETIRE.getTranslatedKeyMessage(),
                DownClientEvents.RETIRE_TICKS / 20);
        float held = DownClientEvents.retireProgress(partialTick);
        graphics.drawString(font, retire, cx - font.width(retire) / 2, y, held > 0.0F ? GOLD : DIM, true);
        if (held > 0.0F) {
            drawBar(graphics, cx - 50, y + 11, 100, held, GOLD);
        }
    }

    /** 自分が起こしている・そばにダウンしている人がいる。 */
    private static void renderHelper(GuiGraphics graphics, Minecraft minecraft, LocalPlayer player, float partialTick, int width,
                                     int height) {
        if (minecraft.level == null || ClientDown.all().isEmpty() || player.isSpectator()) {
            return;
        }
        Entity nearest = null;
        ClientDown.Info nearestInfo = null;
        double best = HINT_RANGE * HINT_RANGE;
        for (Map.Entry<Integer, ClientDown.Info> entry : ClientDown.all().entrySet()) {
            Entity entity = minecraft.level.getEntity(entry.getKey());
            if (entity == null || entity == player) {
                continue;
            }
            if (entry.getValue().reviverId() == player.getId()) {
                // 起こしている人を、いちばん先に出す
                nearest = entity;
                nearestInfo = entry.getValue();
                break;
            }
            double distance = entity.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                nearest = entity;
                nearestInfo = entry.getValue();
            }
        }
        if (nearest == null) {
            return;
        }
        Font font = minecraft.font;
        int cx = width / 2;
        int y = height / 2 + 14;
        if (nearestInfo.reviverId() == player.getId()) {
            Component text = Component.translatable("hud.amemaru.down.reviving", nearest.getDisplayName());
            graphics.drawString(font, text, cx - font.width(text) / 2, y, AQUA, true);
            drawBar(graphics, cx - 50, y + 11, 100, nearestInfo.progressNow(partialTick), AQUA);
        } else {
            float pulse = 0.6F + 0.4F * Mth.sin((ClientDown.now() + partialTick) * 0.3F);
            int alpha = Math.max(0x40, Math.min(0xFF, (int) (255 * pulse)));
            Component text = Component.translatable("hud.amemaru.down.can_revive", nearest.getDisplayName());
            graphics.drawString(font, text, cx - font.width(text) / 2, y, alpha << 24 | 0xFFE066, true);
        }
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int w, float ratio, int color) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + 5, 0xC0090C14);
        graphics.fill(x, y, x + w, y + 4, 0xFF1B2030);
        int filled = Math.round(w * Mth.clamp(ratio, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.fill(x, y, x + filled, y + 4, color);
            graphics.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
        }
    }
}
