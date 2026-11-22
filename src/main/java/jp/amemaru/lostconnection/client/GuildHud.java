package jp.amemaru.lostconnection.client;

import jp.amemaru.lostconnection.client.screen.UiStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * 受けているクエストの一覧（画面の右上。はじめの手引きが出ていれば、その下。Phase 6 その3）。
 * 掲示板で「追う」にしたクエストだけ、3つまで出す。目標を果たしたものは緑、報告できるものには印を付ける。
 */
public final class GuildHud {
    public static final IGuiOverlay OVERLAY = GuildHud::render;
    private static final int WIDTH = 150;
    private static final int MAX_QUESTS = 3;

    private GuildHud() {
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        // 画面を開いているときは出さない（自動の確認の写真の、何も描かない画面のときだけは出す）
        if (minecraft.options.hideGui || minecraft.player == null
                || (minecraft.screen != null && !CiScreenshots.isHoldScreen(minecraft.screen))) {
            return;
        }
        List<CompoundTag> quests = ClientGuild.trackedQuests();
        if (quests.isEmpty()) {
            return;
        }
        Font font = minecraft.font;
        int x = width - WIDTH - 6;
        int y = Math.max(6, StoryHud.guideBottom() + 4);
        int shown = 0;
        for (CompoundTag quest : quests) {
            if (shown++ >= MAX_QUESTS) {
                break;
            }
            List<Tag> objectives = quest.getList("objectives", Tag.TAG_COMPOUND);
            boolean done = quest.getBoolean("done");
            int h = 15 + objectives.size() * 10 + (done ? 10 : 0);
            graphics.fill(x, y, x + WIDTH, y + h, 0x900B1022);
            graphics.fill(x, y, x + 1, y + h, done ? 0xFF9CFFB0 : 0xFFE8C15A);
            graphics.drawString(font, UiStyle.clip(font, quest.getString("name"), WIDTH - 10), x + 6, y + 4,
                    done ? 0xFF9CFFB0 : 0xFFFFF2A8, false);
            int line = y + 15;
            for (Tag tag : objectives) {
                CompoundTag o = (CompoundTag) tag;
                int count = o.getInt("count");
                int max = o.getInt("max");
                boolean complete = count >= max;
                Component text = objectiveText(o);
                String progress = max > 1 ? " " + Math.min(count, max) + "/" + max : complete ? " ✓" : "";
                String body = UiStyle.clip(font, text.getString(), WIDTH - 12 - font.width(progress));
                graphics.drawString(font, (complete ? "◆ " : "◇ ") + body, x + 6, line, complete ? 0xFF9CFFB0 : 0xFFFFFFFF, false);
                graphics.drawString(font, progress, x + WIDTH - 4 - font.width(progress), line, complete ? 0xFF9CFFB0 : 0xFFBFEFFF, false);
                line += 10;
            }
            if (done) {
                String report = quest.getString("report");
                Component where = report.isEmpty() ? Component.translatable("hud.amemaru.quest.report_board")
                        : Component.translatable("hud.amemaru.quest.report_npc", report);
                graphics.drawString(font, UiStyle.clip(font, where.getString(), WIDTH - 10), x + 6, line, 0xFF9CFFB0, false);
            }
            y += h + 3;
        }
    }

    /** 目標の文（サーバーで作った文を、JSON から戻す）。 */
    public static Component objectiveText(CompoundTag objective) {
        try {
            Component text = Component.Serializer.fromJson(objective.getString("text"));
            return text == null ? Component.empty() : text;
        } catch (RuntimeException e) {
            return Component.literal(objective.getString("text"));
        }
    }
}
