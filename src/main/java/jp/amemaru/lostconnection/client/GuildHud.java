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

import java.util.ArrayList;
import java.util.List;

/**
 * 受けているクエストの一覧（画面の右上。はじめの手引きが出ていれば、その下。Phase 6 その3）。
 * 掲示板で「追う」にしたクエストだけ、3つまで出す。目標を果たしたものは緑、報告できるものには印を付ける。
 */
public final class GuildHud {
    public static final IGuiOverlay OVERLAY = GuildHud::render;
    private static final int WIDTH = 150;
    private static final int MAX_QUESTS = 3;
    /** 1つのクエストに出す目標の行の数。これより多いときは、まだの目標を先に（元の順で）出して、残りを1行にまとめる。 */
    private static final int MAX_LINES = 5;

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
        if (quests.isEmpty() || StoryHud.bossBarShown()) {
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
            List<CompoundTag> objectives = new ArrayList<>();
            for (Tag tag : quest.getList("objectives", Tag.TAG_COMPOUND)) {
                objectives.add((CompoundTag) tag);
            }
            int moreLeft = 0;
            int moreDone = 0;
            if (objectives.size() > MAX_LINES) {
                List<CompoundTag> visible = new ArrayList<>();
                for (CompoundTag o : objectives) {
                    if (complete(o)) {
                        moreDone++;
                    } else if (visible.size() < MAX_LINES - 1) {
                        visible.add(o);
                    } else {
                        moreLeft++;
                    }
                }
                objectives = visible;
            }
            boolean summary = moreLeft + moreDone > 0;
            boolean done = quest.getBoolean("done");
            int h = 15 + (objectives.size() + (summary ? 1 : 0)) * 10 + (done ? 10 : 0);
            graphics.fill(x, y, x + WIDTH, y + h, 0x900B1022);
            graphics.fill(x, y, x + 1, y + h, done ? 0xFF9CFFB0 : 0xFFE8C15A);
            graphics.drawString(font, UiStyle.clip(font, quest.getString("name"), WIDTH - 10), x + 6, y + 4,
                    done ? 0xFF9CFFB0 : 0xFFFFF2A8, false);
            int line = y + 15;
            for (CompoundTag o : objectives) {
                int count = o.getInt("count");
                int max = o.getInt("max");
                boolean complete = complete(o);
                Component text = objectiveText(o);
                String progress = max > 1 ? " " + Math.min(count, max) + "/" + max : complete ? " ✓" : "";
                String body = UiStyle.clip(font, text.getString(), WIDTH - 12 - font.width(progress));
                graphics.drawString(font, (complete ? "◆ " : "◇ ") + body, x + 6, line, complete ? 0xFF9CFFB0 : 0xFFFFFFFF, false);
                graphics.drawString(font, progress, x + WIDTH - 4 - font.width(progress), line, complete ? 0xFF9CFFB0 : 0xFFBFEFFF, false);
                line += 10;
            }
            if (summary) {
                Component more = moreLeft > 0 && moreDone > 0 ? Component.translatable("hud.amemaru.quest.more_done", moreLeft, moreDone)
                        : moreLeft > 0 ? Component.translatable("hud.amemaru.quest.more", moreLeft)
                        : Component.translatable("hud.amemaru.quest.done_count", moreDone);
                graphics.drawString(font, UiStyle.clip(font, more.getString(), WIDTH - 10), x + 6, line, UiStyle.TEXT_DIM, false);
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

    private static boolean complete(CompoundTag objective) {
        return objective.getInt("count") >= objective.getInt("max");
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
