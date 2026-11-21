package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.network.s2c.PartyStateS2C;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.UUID;

/**
 * パーティのHUD（左上。Phase 4。Phase 0-A 13章「仲間の糸が見えるようになる＝パーティHUD」）。
 *
 * <p>自分以外のメンバーを、入った順に並べる：名前（リーダーは★）・レベル・HPのバー。ダウンしている人は、赤いバーで
 * 教会へ戻るまでの残り。いない人は「いません」、べつのディメンションの人は「べつの場所」。届いている誘いも出す。
 * F3 の画面を出している間は隠す。</p>
 */
public final class PartyHud {
    public static final IGuiOverlay OVERLAY = PartyHud::render;

    private static final int X = 6;
    private static final int Y = 6;
    private static final int ROW = 23;
    private static final int WIDTH = 112;
    private static final int BAR_W = 104;
    private static final int BAR_H = 5;
    private static final int PANEL = 0x8C0B1022;
    private static final int PANEL_EDGE = 0xFF3E5274;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0xFF9AA6BF;
    private static final int LEADER = 0xFFFFE066;
    private static final int BAR_BG = 0xFF1B2030;
    private static final int HP_HIGH = 0xFF52DC6E;
    private static final int HP_MID = 0xFFE8C860;
    private static final int HP_LOW = 0xFFF06A4A;
    private static final int DOWN = 0xFFFF5A4A;

    private PartyHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.renderDebug || minecraft.options.hideGui) {
            return;
        }
        PartyStateS2C state = ClientParty.state();
        Font font = minecraft.font;
        UUID self = minecraft.player.getUUID();
        int y = Y;
        if (state.inParty()) {
            for (PartyStateS2C.Member member : state.members()) {
                if (member.id().equals(self)) {
                    continue;
                }
                drawMember(graphics, font, member, y);
                y += ROW;
            }
        }
        long age = ClientParty.age();
        for (PartyStateS2C.Invite invite : state.invites()) {
            if (invite.ticksLeft() - age <= 0) {
                continue;
            }
            Component text = Component.translatable("hud.amemaru.party.invite", invite.name());
            int w = font.width(text) + 8;
            graphics.fill(X - 2, y - 2, X - 2 + w, y + 11, PANEL);
            graphics.fill(X - 2, y - 2, X - 1, y + 11, 0xFF8FD3FF);
            graphics.drawString(font, text, X + 2, y, 0xFF8FD3FF, true);
            y += 15;
        }
    }

    private static void drawMember(GuiGraphics graphics, Font font, PartyStateS2C.Member member, int y) {
        graphics.fill(X - 2, y - 2, X - 2 + WIDTH, y + ROW - 4, PANEL);
        graphics.fill(X - 2, y - 2, X - 1, y + ROW - 4, member.down() ? DOWN : PANEL_EDGE);
        String name = (member.leader() ? "★" : "") + member.name();
        int nameColor = !member.online() ? TEXT_DIM : member.leader() ? LEADER : TEXT;
        graphics.drawString(font, font.plainSubstrByWidth(name, WIDTH - 34), X + 2, y, nameColor, true);
        // ダウンの間は、レベルの代わりに「ダウン 残りの秒」を同じ所に書く（重ならないように）
        if (member.online() && member.level() > 0 && !member.down()) {
            String level = "Lv" + member.level();
            graphics.drawString(font, level, X + WIDTH - 6 - font.width(level), y, TEXT_DIM, true);
        }
        int barY = y + 11;
        if (!member.online()) {
            graphics.drawString(font, Component.translatable("hud.amemaru.party.offline"), X + 2, barY - 1, TEXT_DIM, true);
            return;
        }
        if (!member.sameLevel() && !member.down()) {
            graphics.drawString(font, Component.translatable("hud.amemaru.party.elsewhere"), X + 2, barY - 1, TEXT_DIM, true);
            return;
        }
        graphics.fill(X + 2, barY, X + 2 + BAR_W, barY + BAR_H, BAR_BG);
        if (member.down()) {
            long left = Math.max(0L, member.downTicks() - ClientParty.age());
            float ratio = member.downLength() > 0 ? Mth.clamp(left / (float) member.downLength(), 0.0F, 1.0F) : 0.0F;
            float pulse = 0.65F + 0.35F * Mth.sin(ClientDown.now() * 0.45F);
            int alpha = (int) (255 * pulse) << 24;
            graphics.fill(X + 2, barY, X + 2 + Math.round(BAR_W * ratio), barY + BAR_H, alpha | (DOWN & 0xFFFFFF));
            Component text = Component.translatable("hud.amemaru.party.down", (left + 19) / 20).withStyle(ChatFormatting.RED);
            graphics.drawString(font, text, X + WIDTH - 6 - font.width(text), y, DOWN, true);
            return;
        }
        float hp = Mth.clamp(member.hp(), 0.0F, 1.0F);
        int color = hp > 0.5F ? HP_HIGH : hp > 0.25F ? HP_MID : HP_LOW;
        graphics.fill(X + 2, barY, X + 2 + Math.round(BAR_W * hp), barY + BAR_H, color);
    }
}
