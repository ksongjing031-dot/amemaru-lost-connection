package jp.amemaru.lostconnection.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import jp.amemaru.lostconnection.client.ClientDefinitions;
import jp.amemaru.lostconnection.client.ClientPlayerState;
import jp.amemaru.lostconnection.client.KeyBindings;
import jp.amemaru.lostconnection.client.SkillHudArt;
import jp.amemaru.lostconnection.job.def.LinkType;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.LearnSkillC2S;
import jp.amemaru.lostconnection.network.c2s.SetLoadoutC2S;
import jp.amemaru.lostconnection.network.s2c.SyncDefinitionsS2C;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * スキルツリーの画面（初期値：Jキー。Phase 0-A 19章・S3「職業ごとのスキルツリー（GUI）」）。
 *
 * <p>左に、流派ごとの木（1列目がアクティブ、2列目がパッシブ、下が奥義）。右に、選んだスキルの説明・今の段階と次の段階の効果・
 * 習得のボタン・スキル枠（6つ）。スキル枠は、アクティブスキルを選んでから枠をクリックすると入る（右クリックで空にする）。
 * 習得と枠の入れかえはサーバーが確かめてから反映し、結果は同期で返ってくる。</p>
 */
public class SkillTreeScreen extends Screen {
    private static final int PANEL_WIDTH = 404;
    private static final int PANEL_HEIGHT = 228;
    private static final int NODE = SkillHudArt.NODE;
    private static final int COL_STEP = 44;
    private static final int ROW_STEP = 40;
    private static final int BLOCK_STEP = 98;
    private static final int TREE_X = 12;
    private static final int TREE_Y = 50;
    private static final int DETAIL_X = 214;
    private static final int SLOT_Y = 184;
    private static final int SLOT_STEP = 27;

    private int left;
    private int top;
    private int selected = -1;
    private CrystalButton learnButton;

    public SkillTreeScreen() {
        super(Component.translatable("screen.amemaru.skill_tree.title"));
    }

    @Override
    protected void init() {
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        learnButton = addRenderableWidget(new CrystalButton(left + DETAIL_X, top + 160, PANEL_WIDTH - DETAIL_X - 12, 16,
                Component.translatable("screen.amemaru.skill_tree.learn", 1), b -> learn()));
        addRenderableWidget(new CrystalButton(left + 12, top + PANEL_HEIGHT - 16, 60, 12,
                Component.translatable("screen.amemaru.status.close"), b -> onClose()));
        if (selected < 0) {
            List<SyncDefinitionsS2C.SkillInfo> skills = ClientDefinitions.get().skillsOf(ClientPlayerState.get().jobIndex());
            if (!skills.isEmpty()) {
                selected = skills.get(0).index();
            }
        }
        updateButton();
    }

    @Override
    public void tick() {
        super.tick();
        updateButton();
    }

    private void updateButton() {
        if (learnButton == null) {
            return;
        }
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(selected);
        ClientPlayerState state = ClientPlayerState.get();
        if (skill == null || !state.hasJob()) {
            learnButton.visible = false;
            return;
        }
        learnButton.visible = true;
        int rank = state.rank(skill.index());
        int next = rank + 1;
        if (next > skill.maxRank()) {
            learnButton.active = false;
            learnButton.setMessage(Component.translatable("screen.amemaru.skill_tree.max"));
            return;
        }
        int cost = skill.rankCosts()[next - 1];
        learnButton.setMessage(Component.translatable(rank == 0 ? "screen.amemaru.skill_tree.learn" : "screen.amemaru.skill_tree.rank_up", cost));
        learnButton.active = blocker(skill, state) == null;
    }

    private void learn() {
        if (selected >= 0) {
            ModNetwork.sendToServer(new LearnSkillC2S(selected));
        }
    }

    /** 次の段階を覚えられない理由（覚えられるなら null）。サーバーと同じ決まり。 */
    @Nullable
    private static Component blocker(SyncDefinitionsS2C.SkillInfo skill, ClientPlayerState state) {
        if (skill.jobIndex() != state.jobIndex()) {
            return Component.translatable("message.amemaru.skill.learn.other_job");
        }
        int next = state.rank(skill.index()) + 1;
        if (next > skill.maxRank()) {
            return Component.translatable("message.amemaru.skill.learn.max_rank");
        }
        if (state.level() < skill.minLevel()) {
            return Component.translatable("message.amemaru.skill.learn.level", skill.minLevel());
        }
        for (int i = 0; i < skill.requiredSkills().length; i++) {
            if (state.rank(skill.requiredSkills()[i]) < skill.requiredRanks()[i]) {
                return Component.translatable("message.amemaru.skill.learn.requirement", requirementText(skill));
            }
        }
        if ((skill.ultimate() || next >= skill.upperRank()) && !state.promoted()) {
            return Component.translatable("message.amemaru.skill.learn.upper");
        }
        if (state.skillPoints() < skill.rankCosts()[next - 1]) {
            return Component.translatable("message.amemaru.skill.learn.points", skill.rankCosts()[next - 1]);
        }
        return null;
    }

    private static String requirementText(SyncDefinitionsS2C.SkillInfo skill) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < skill.requiredSkills().length; i++) {
            SyncDefinitionsS2C.SkillInfo required = ClientDefinitions.get().skill(skill.requiredSkills()[i]);
            parts.add((required == null ? "?" : required.name()) + " " + skill.requiredRanks()[i] + "段");
        }
        return String.join("、", parts);
    }

    // ---------------------------------------------------------------- 位置

    /** 木の中の、節の左上（奥義は下の中央）。 */
    private int[] nodePosition(SyncDefinitionsS2C.SkillInfo skill) {
        if (skill.ultimate() || skill.schoolIndex() < 0) {
            return new int[]{left + TREE_X + BLOCK_STEP - NODE / 2 + 2, top + TREE_Y + 3 * ROW_STEP + 2};
        }
        int x = left + TREE_X + skill.schoolIndex() * BLOCK_STEP + Math.min(1, skill.column()) * COL_STEP;
        int y = top + TREE_Y + Math.min(2, skill.row()) * ROW_STEP;
        return new int[]{x, y};
    }

    private int slotX(int slot) {
        return left + DETAIL_X + slot * SLOT_STEP;
    }

    // ---------------------------------------------------------------- 描画

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT);
        ClientPlayerState state = ClientPlayerState.get();
        SyncDefinitionsS2C.JobInfo job = ClientDefinitions.get().job(state.jobIndex());
        if (job == null) {
            graphics.drawCenteredString(font, title, width / 2, top + 7, UiStyle.TEXT_HEADER);
            UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 19);
            UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.skill_tree.no_job").getString(), left + 40,
                    top + PANEL_HEIGHT / 2 - 10, PANEL_WIDTH - 80, UiStyle.TEXT_DIM, 4);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        renderHeader(graphics, state, job);
        List<SyncDefinitionsS2C.SkillInfo> skills = ClientDefinitions.get().skillsOf(job.index());
        renderSchools(graphics, job);
        renderConnectors(graphics, state, skills);
        SyncDefinitionsS2C.SkillInfo hovered = null;
        for (SyncDefinitionsS2C.SkillInfo skill : skills) {
            int[] at = nodePosition(skill);
            renderNode(graphics, state, skill, at[0], at[1]);
            if (mouseX >= at[0] && mouseX < at[0] + NODE && mouseY >= at[1] && mouseY < at[1] + NODE) {
                hovered = skill;
            }
        }
        renderDetails(graphics, state, job);
        int hoveredSlot = renderLoadout(graphics, state, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hovered != null) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.literal(hovered.name()).withStyle(ChatFormatting.AQUA).getVisualOrderText());
            lines.add(Component.translatable("screen.amemaru.skill_tree.rank", state.rank(hovered.index()), hovered.maxRank())
                    .withStyle(ChatFormatting.GRAY).getVisualOrderText());
            setTooltipForNextRenderPass(lines);
        } else if (hoveredSlot >= 0) {
            SyncDefinitionsS2C.SkillInfo inSlot = ClientDefinitions.get().skill(state.loadout(hoveredSlot));
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.amemaru.skill_tree.slot", hoveredSlot + 1,
                    KeyBindings.SKILLS[hoveredSlot].getTranslatedKeyMessage()).getVisualOrderText());
            lines.add(Component.literal(inSlot == null ? "―" : inSlot.name()).withStyle(ChatFormatting.AQUA).getVisualOrderText());
            lines.add(Component.translatable("screen.amemaru.skill_tree.slot_help").withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
            setTooltipForNextRenderPass(lines);
        }
    }

    private void renderHeader(GuiGraphics graphics, ClientPlayerState state, SyncDefinitionsS2C.JobInfo job) {
        RenderSystem.enableBlend();
        graphics.blit(job.icon(), left + 10, top + 5, 16, 16, 0, 0, 64, 64, 64, 64);
        graphics.drawString(font, Component.translatable("screen.amemaru.skill_tree.header", job.name()), left + 30, top + 9,
                0xFF000000 | job.color(), true);
        Component points = Component.translatable("screen.amemaru.status.skill_points", state.skillPoints());
        graphics.drawString(font, points, left + PANEL_WIDTH - 12 - font.width(points), top + 9,
                state.skillPoints() > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, true);
        Component upper;
        if (!job.hasUpper()) {
            upper = Component.empty();
        } else if (state.promoted()) {
            upper = Component.translatable("screen.amemaru.skill_tree.upper_done", job.upperName());
        } else {
            upper = Component.translatable("screen.amemaru.skill_tree.upper_todo", job.upperName(), job.upperMinDay(), job.upperMinLevel());
        }
        graphics.drawString(font, upper, left + 12, top + 22, state.promoted() ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);
        if (job.hasGauge()) {
            Component gauge = Component.translatable("screen.amemaru.skill_tree.gauge", job.gaugeName());
            graphics.drawString(font, gauge, left + PANEL_WIDTH - 12 - font.width(gauge), top + 22, 0xFF000000 | job.gaugeColor(), false);
        }
        UiStyle.divider(graphics, left + 12, left + PANEL_WIDTH - 12, top + 33);
    }

    private void renderSchools(GuiGraphics graphics, SyncDefinitionsS2C.JobInfo job) {
        for (int i = 0; i < job.schools().size() && i < 2; i++) {
            SyncDefinitionsS2C.SchoolInfo school = job.schools().get(i);
            int center = left + TREE_X + i * BLOCK_STEP + (COL_STEP + NODE) / 2;
            graphics.drawCenteredString(font, school.name(), center, top + TREE_Y - 11, 0xFF000000 | school.color());
        }
        // 木と説明の間の縦の線
        graphics.fill(left + DETAIL_X - 8, top + 40, left + DETAIL_X - 7, top + PANEL_HEIGHT - 20, 0x408FD3FF);
    }

    /** 前提のスキルとのつながり（糸）。覚えていれば明るく光る。 */
    private void renderConnectors(GuiGraphics graphics, ClientPlayerState state, List<SyncDefinitionsS2C.SkillInfo> skills) {
        for (SyncDefinitionsS2C.SkillInfo skill : skills) {
            int[] to = nodePosition(skill);
            for (int i = 0; i < skill.requiredSkills().length; i++) {
                SyncDefinitionsS2C.SkillInfo required = ClientDefinitions.get().skill(skill.requiredSkills()[i]);
                if (required == null) {
                    continue;
                }
                int[] from = nodePosition(required);
                boolean lit = state.rank(required.index()) >= skill.requiredRanks()[i];
                int color = lit ? 0xFF8FD3FF : 0xFF2C3A58;
                int fx = from[0] + NODE / 2;
                int fy = from[1] + NODE / 2;
                int tx = to[0] + NODE / 2;
                int ty = to[1] + NODE / 2;
                // 縦にのばしてから、横につなぐ
                graphics.fill(Math.min(fx, fx + 1), Math.min(fy, ty), Math.max(fx, fx + 1) + 1, Math.max(fy, ty), color);
                graphics.fill(Math.min(fx, tx), ty, Math.max(fx, tx), ty + 2, color);
            }
        }
    }

    private void renderNode(GuiGraphics graphics, ClientPlayerState state, SyncDefinitionsS2C.SkillInfo skill, int x, int y) {
        int rank = state.rank(skill.index());
        boolean available = blocker(skill, state) == null;
        int u;
        if (skill.ultimate()) {
            u = SkillHudArt.U_NODE_ULTIMATE;
        } else if (rank >= skill.maxRank()) {
            u = SkillHudArt.U_NODE_MAX;
        } else if (rank > 0) {
            u = SkillHudArt.U_NODE_LEARNED;
        } else if (available) {
            u = SkillHudArt.U_NODE_AVAILABLE;
        } else {
            u = SkillHudArt.U_NODE_LOCKED;
        }
        RenderSystem.enableBlend();
        if (skill.index() == selected) {
            graphics.blit(SkillHudArt.TEXTURE, x - 2, y - 2, SkillHudArt.U_NODE_SELECT, SkillHudArt.V_NODE,
                    SkillHudArt.NODE_SELECT, SkillHudArt.NODE_SELECT, 256, 256);
        }
        graphics.blit(SkillHudArt.TEXTURE, x, y, u, SkillHudArt.V_NODE, NODE, NODE, 256, 256);
        if (rank <= 0 && !available) {
            RenderSystem.setShaderColor(0.45F, 0.48F, 0.58F, 1.0F);
        }
        graphics.blit(skill.icon(), x + 2, y + 2, 32, 32, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // 段階の印
        int pipsWidth = skill.maxRank() * (SkillHudArt.PIP + 1) - 1;
        int px = x + (NODE - pipsWidth) / 2;
        for (int r = 1; r <= skill.maxRank(); r++) {
            boolean upper = skill.ultimate() || r >= skill.upperRank();
            int pipU = r <= rank ? (upper ? SkillHudArt.U_PIP_UPPER_FILLED : SkillHudArt.U_PIP_FILLED)
                    : (upper ? SkillHudArt.U_PIP_UPPER : SkillHudArt.U_PIP_EMPTY);
            graphics.blit(SkillHudArt.TEXTURE, px + (r - 1) * (SkillHudArt.PIP + 1), y + NODE - 4, pipU, SkillHudArt.V_PIP,
                    SkillHudArt.PIP, SkillHudArt.PIP, 256, 256);
        }
        if (state.slotOf(skill.index()) >= 0) {
            String key = KeyBindings.SKILLS[state.slotOf(skill.index())].getTranslatedKeyMessage().getString();
            graphics.drawString(font, key.length() > 2 ? key.substring(0, 2) : key, x + 2, y + 2, 0xFFFFF2A8, true);
        }
    }

    private void renderDetails(GuiGraphics graphics, ClientPlayerState state, SyncDefinitionsS2C.JobInfo job) {
        SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(selected);
        int x = left + DETAIL_X;
        int width = PANEL_WIDTH - DETAIL_X - 12;
        if (skill == null || skill.jobIndex() != job.index()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.skill_tree.select"), x, top + 44, UiStyle.TEXT_DIM, false);
            return;
        }
        int rank = state.rank(skill.index());
        RenderSystem.enableBlend();
        graphics.blit(skill.icon(), x, top + 40, 24, 24, 0, 0, 32, 32, 32, 32);
        graphics.drawString(font, skill.name(), x + 28, top + 41, UiStyle.TEXT, true);
        String school = skill.ultimate() ? Component.translatable("screen.amemaru.skill_tree.ultimate").getString()
                : skill.schoolIndex() >= 0 && skill.schoolIndex() < job.schools().size() ? job.schools().get(skill.schoolIndex()).name() : "";
        String kind = Component.translatable(skill.passive() ? "screen.amemaru.skill_tree.passive" : "screen.amemaru.skill_tree.active").getString();
        String link = LinkType.byIndex(skill.link()).label();
        String sub = school + "・" + kind + (link.isEmpty() ? "" : "・" + Component.translatable("screen.amemaru.skill_tree.link", link).getString());
        graphics.drawString(font, sub, x + 28, top + 52, UiStyle.TEXT_DIM, false);
        Component rankText = Component.translatable("screen.amemaru.skill_tree.rank", rank, skill.maxRank());
        graphics.drawString(font, rankText, left + PANEL_WIDTH - 12 - font.width(rankText), top + 41,
                rank > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);

        int y = top + 66;
        y += 10 * UiStyle.wrapped(graphics, font, skill.description(), x, y, width, UiStyle.TEXT, 2);
        y += 2;
        int shownRank = Math.max(1, Math.min(rank, skill.maxRank()));
        if (rank > 0) {
            y = renderRankBlock(graphics, skill, shownRank, x, y, width, "screen.amemaru.skill_tree.now", UiStyle.TEXT_HEADER, 3);
        }
        if (rank < skill.maxRank()) {
            y = renderRankBlock(graphics, skill, rank + 1, x, y, width,
                    rank == 0 ? "screen.amemaru.skill_tree.first" : "screen.amemaru.skill_tree.next", UiStyle.TEXT_GOLD, rank == 0 ? 4 : 3);
        }
        Component reason = blocker(skill, state);
        if (reason != null && rank < skill.maxRank()) {
            graphics.drawString(font, reason, x, top + 150, UiStyle.TEXT_BAD, false);
        }
    }

    /** 段階の見出し・数値・効果の行。 */
    private int renderRankBlock(GuiGraphics graphics, SyncDefinitionsS2C.SkillInfo skill, int rank, int x, int y, int width,
                                String headerKey, int headerColor, int maxLines) {
        if (y > top + 140) {
            return y;
        }
        String numbers = numbers(skill, rank);
        graphics.drawString(font, Component.translatable(headerKey, rank).getString() + (numbers.isEmpty() ? "" : "  " + numbers),
                x, y, headerColor, false);
        y += 10;
        List<String> lines = rank - 1 < skill.details().size() ? skill.details().get(rank - 1) : List.of();
        int shown = 0;
        for (String line : lines) {
            if (shown >= maxLines || y > top + 140) {
                break;
            }
            List<FormattedCharSequence> wrapped = font.split(FormattedText.of("・" + line), width);
            for (FormattedCharSequence part : wrapped) {
                if (shown >= maxLines || y > top + 140) {
                    break;
                }
                graphics.drawString(font, part, x, y, UiStyle.TEXT_DIM, false);
                y += 10;
                shown++;
            }
        }
        return y + 1;
    }

    /** 「MP 12・CD 12秒・詠唱 2秒」の形。 */
    private static String numbers(SyncDefinitionsS2C.SkillInfo skill, int rank) {
        if (skill.passive()) {
            return "";
        }
        int r = rank - 1;
        List<String> parts = new ArrayList<>();
        if (r < skill.mpCost().length && skill.mpCost()[r] > 0) {
            parts.add("MP " + skill.mpCost()[r]);
        }
        if (r < skill.gaugeCost().length && skill.gaugeCost()[r] > 0) {
            parts.add(Component.translatable("screen.amemaru.skill_tree.gauge_cost", skill.gaugeCost()[r]).getString());
        }
        if (r < skill.cooldown().length && skill.cooldown()[r] > 0) {
            parts.add("CD " + trim(skill.cooldown()[r]) + "秒");
        }
        if (r < skill.cast().length && skill.cast()[r] > 0) {
            parts.add("詠唱 " + trim(skill.cast()[r]) + "秒");
        }
        return String.join("・", parts);
    }

    private static String trim(float value) {
        return Math.abs(value - Math.round(value)) < 0.05F ? Integer.toString(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    /** スキル枠6つ。マウスが乗っている枠の番号を返す（なければ -1）。 */
    private int renderLoadout(GuiGraphics graphics, ClientPlayerState state, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("screen.amemaru.skill_tree.loadout"), left + DETAIL_X, top + SLOT_Y - 11,
                UiStyle.TEXT_HEADER, false);
        int hovered = -1;
        RenderSystem.enableBlend();
        for (int slot = 0; slot < 6; slot++) {
            int x = slotX(slot);
            int y = top + SLOT_Y;
            SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(state.loadout(slot));
            graphics.blit(SkillHudArt.TEXTURE, x, y, skill == null ? SkillHudArt.U_HEX_EMPTY : SkillHudArt.U_HEX, SkillHudArt.V_HEX,
                    SkillHudArt.HEX_W, SkillHudArt.HEX_H, 256, 256);
            if (skill != null) {
                graphics.blit(skill.icon(), x + 4, y + 5, 16, 16, 0, 0, 32, 32, 32, 32);
            }
            String key = KeyBindings.SKILLS[slot].getTranslatedKeyMessage().getString();
            graphics.drawCenteredString(font, key.length() > 3 ? key.substring(0, 3) : key, x + SkillHudArt.HEX_W / 2, y + SkillHudArt.HEX_H + 1,
                    0xFFE8F4FF);
            if (mouseX >= x && mouseX < x + SkillHudArt.HEX_W && mouseY >= y && mouseY < y + SkillHudArt.HEX_H) {
                hovered = slot;
            }
        }
        return hovered;
    }

    // ---------------------------------------------------------------- 操作

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClientPlayerState state = ClientPlayerState.get();
        // 木の節
        for (SyncDefinitionsS2C.SkillInfo skill : ClientDefinitions.get().skillsOf(state.jobIndex())) {
            int[] at = nodePosition(skill);
            if (mouseX >= at[0] && mouseX < at[0] + NODE && mouseY >= at[1] && mouseY < at[1] + NODE) {
                selected = skill.index();
                updateButton();
                return true;
            }
        }
        // スキル枠
        for (int slot = 0; slot < 6; slot++) {
            int x = slotX(slot);
            int y = top + SLOT_Y;
            if (mouseX >= x && mouseX < x + SkillHudArt.HEX_W && mouseY >= y && mouseY < y + SkillHudArt.HEX_H) {
                if (button == 1) {
                    ModNetwork.sendToServer(new SetLoadoutC2S(slot, -1));
                } else {
                    SyncDefinitionsS2C.SkillInfo skill = ClientDefinitions.get().skill(selected);
                    if (skill != null && !skill.passive() && state.rank(skill.index()) > 0) {
                        ModNetwork.sendToServer(new SetLoadoutC2S(slot, skill.index()));
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (KeyBindings.OPEN_SKILL_TREE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 選んでいるスキルを変える（自動の確認で使う）。 */
    public void select(int skillIndex) {
        selected = skillIndex;
        updateButton();
    }
}
