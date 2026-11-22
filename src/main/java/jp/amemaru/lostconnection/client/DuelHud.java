package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.UiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;

/**
 * 決闘の間の表示（Phase 6 その4）：画面の上の真ん中に、残り時間と二人のHPの割合（西の人は青、東の人は赤）。
 * 戦う二人と、観戦している人に出す。
 */
public final class DuelHud {
    public static final IGuiOverlay OVERLAY = DuelHud::render;
    private static final int BAR = 90;

    private DuelHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || !ClientSocial.hudActive()) {
            return;
        }
        CompoundTag hud = ClientSocial.hud();
        ListTag fighters = hud.getList("fighters", Tag.TAG_COMPOUND);
        if (fighters.size() < 2) {
            return;
        }
        Font font = minecraft.font;
        int y = 28;
        int panel = BAR * 2 + 60;
        int x = (width - panel) / 2;
        graphics.fill(x, y, x + panel, y + 30, 0xA00B1022);
        graphics.fill(x, y, x + panel, y + 1, 0xFFE8C15A);
        String phase = hud.getString("phase");
        long left = hud.getLong("left");
        String clock = "FIGHT".equals(phase)
                ? String.format(Locale.ROOT, "%d:%02d", left / 1200, (left / 20) % 60)
                : Component.translatable("END".equals(phase) ? "hud.amemaru.duel.end" : "hud.amemaru.duel.ready").getString();
        graphics.drawString(font, clock, x + panel / 2 - font.width(clock) / 2, y + 4, "FIGHT".equals(phase) && left < 600
                ? 0xFFFF8A8A : 0xFFFFF2A8, true);
        String me = hud.getString("me");
        for (int i = 0; i < 2; i++) {
            CompoundTag one = fighters.getCompound(i);
            double ratio = Math.max(0.0, Math.min(1.0, one.getDouble("ratio")));
            int bx = i == 0 ? x + 6 : x + panel - 6 - BAR;
            int color = i == 0 ? 0xFF5DA9FF : 0xFFFF6A5A;
            String name = one.getString("name") + (one.getString("name").equals(me) ? " " + Component.translatable("hud.amemaru.duel.you").getString() : "");
            if (one.getBoolean("winner")) {
                name = "★ " + name;
            }
            name = UiStyle.clip(font, name, BAR);
            graphics.drawString(font, name, i == 0 ? bx : bx + BAR - font.width(name), y + 4, one.getBoolean("winner") ? 0xFFFFD866 : 0xFFFFFFFF, true);
            graphics.fill(bx, y + 16, bx + BAR, y + 24, 0xFF1B2030);
            int fill = (int) Math.round(BAR * ratio);
            if (i == 0) {
                graphics.fill(bx, y + 16, bx + fill, y + 24, color);
            } else {
                graphics.fill(bx + BAR - fill, y + 16, bx + BAR, y + 24, color);
            }
            String percent = Math.round(ratio * 100) + "%";
            graphics.drawString(font, percent, bx + BAR / 2 - font.width(percent) / 2, y + 16, 0xFFFFFFFF, true);
        }
        graphics.drawString(font, "VS", x + panel / 2 - font.width("VS") / 2, y + 17, 0xFFBFEFFF, true);
    }
}
