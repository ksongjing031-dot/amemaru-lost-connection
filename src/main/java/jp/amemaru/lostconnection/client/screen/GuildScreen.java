package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.client.ClientGuild;
import jp.amemaru.lostconnection.client.GuildHud;
import jp.amemaru.lostconnection.client.TitleText;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 冒険者ギルドの窓口（Phase 6 その3。Phase 0-A 7章「クエスト掲示板、パーティ募集、称号の授与、冒険日誌」）。
 * リリアとの会話か、ギルドの1階の依頼の掲示板（右クリック）から開く。
 *
 * <ul>
 *   <li>クエスト：今日のデイリー・ギルドの依頼・上位クエスト・昇格クエスト・受けている依頼。受ける・報告する・あきらめる・右上に出す。</li>
 *   <li>パーティ募集：一言と階・レベルの目安を書いて募集する。ほかの人の募集に申し込む。自分の募集の申し込みを受ける・断る。</li>
 *   <li>称号：表示している称号と、最近手に入れた称号。一覧の画面を開く。</li>
 *   <li>冒険日誌：サーバーで起きた出来事の年表（初撃破・限定称号・露店の区画が増えた など）。</li>
 *   <li>縁：主要なNPCとの親密度と、縁帳（組んだ人・取引した人・助け合った人）。</li>
 * </ul>
 */
public class GuildScreen extends Screen {
    private static final int W = 380;
    private static final int H = 222;
    private static final String[] TABS = {"quests", "party", "titles", "chronicle", "bonds"};
    private static final int TAB_W = 68;
    private static final int LIST_W = 152;
    private static final int QUEST_ROW = 22;
    private static final int QUEST_ROWS = 7;
    private static final int POST_ROW = 30;
    private static final int POST_ROWS = 4;
    private static final int CHRONICLE_ROW = 24;
    private static final int CHRONICLE_ROWS = 6;
    private static final int BOND_ROW = 26;

    private CompoundTag data;
    private String tab;
    private int left;
    private int top;
    private int selfVersion = -1;

    // クエスト
    private final List<CompoundTag> quests = new ArrayList<>();
    private int questScroll;
    private String selected = "";
    // パーティ募集
    private EditBox postText;
    private String postDraft = "";
    private int postFloor = 1;
    private int postMinLevel = 1;
    private int partyScroll;
    // 冒険日誌
    private int chronicleScroll;

    public GuildScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.guild.title"));
        this.data = data;
        this.tab = validTab(data.getString("tab"));
    }

    private static String validTab(String tab) {
        for (String t : TABS) {
            if (t.equals(tab)) {
                return t;
            }
        }
        return "quests";
    }

    /** サーバーから新しい中身が届いた（受けた・報告した・募集した など）。開いているタブはそのまま。 */
    public void update(CompoundTag data) {
        this.data = data;
        rebuildWidgets();
    }

    /** 自動の確認の写真で、タブを変える。 */
    public void ciTab(String next) {
        tab = validTab(next);
        rebuildWidgets();
    }

    private boolean inGuild() {
        return data.getBoolean("inGuild");
    }

    // ================================================================ 作る

    @Override
    protected void init() {
        selfVersion = ClientGuild.version();
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        switch (tab) {
            case "quests" -> initQuests();
            case "party" -> initParty();
            case "titles" -> addRenderableWidget(new CrystalButton(left + 14, top + H - 26, 120, 16,
                    Component.translatable("screen.amemaru.guild.titles.open"), b -> {
                        if (minecraft != null) {
                            minecraft.setScreen(new TitleListScreen(this));
                        }
                    }));
            case "bonds" -> addRenderableWidget(new CrystalButton(left + 14, top + H - 26, 120, 16,
                    Component.translatable("screen.amemaru.guild.bonds.connections"), b -> ClientGuild.send("connections_open")));
            default -> {
            }
        }
    }

    private void initQuests() {
        quests.clear();
        Set<String> seen = new HashSet<>();
        for (Tag tag : data.getList("board", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if (seen.add(one.getString("id"))) {
                quests.add(one);
            }
        }
        // 掲示板に出ないクエスト（NPCから直接受けた個人的な依頼など）も、受けていれば並べる
        for (Tag tag : ClientGuild.self().getList("quests", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) tag;
            if (seen.add(one.getString("id"))) {
                quests.add(one);
            }
        }
        quests.sort(Comparator.comparingInt(GuildScreen::questRank));
        if (quests.stream().noneMatch(q -> q.getString("id").equals(selected))) {
            selected = quests.isEmpty() ? "" : quests.get(0).getString("id");
        }
        questScroll = Mth.clamp(questScroll, 0, Math.max(0, quests.size() - QUEST_ROWS));
        CompoundTag quest = selectedQuest();
        if (quest == null) {
            return;
        }
        String id = quest.getString("id");
        String state = quest.getString("state");
        int bx = left + LIST_W + 22;
        int by = top + H - 24;
        if ("available".equals(state)) {
            Button accept = addRenderableWidget(new CrystalButton(bx, by, 64, 16, Component.translatable("screen.amemaru.guild.quest.accept"),
                    b -> questAction("quest_accept", id)));
            accept.active = inGuild();
        } else if ("active".equals(state)) {
            boolean done = quest.getBoolean("done");
            boolean atBoard = quest.getString("report").isEmpty();
            Button report = addRenderableWidget(new CrystalButton(bx, by, 64, 16, Component.translatable("screen.amemaru.guild.quest.report"),
                    b -> questAction("quest_report", id)));
            report.active = done && atBoard && inGuild();
            boolean tracked = quest.getBoolean("tracked");
            addRenderableWidget(new CrystalButton(bx + 68, by, 64, 16,
                    Component.translatable(tracked ? "screen.amemaru.guild.quest.untrack" : "screen.amemaru.guild.quest.track"), b -> {
                        CompoundTag args = new CompoundTag();
                        args.putString("id", id);
                        args.putBoolean("on", !tracked);
                        ClientGuild.send("quest_track", args);
                    }));
            if (!"town_tour".equals(quest.getString("kind"))) {
                addRenderableWidget(new CrystalButton(bx + 136, by, 64, 16, Component.translatable("screen.amemaru.guild.quest.abandon"),
                        b -> questAction("quest_abandon", id)));
            }
        }
    }

    private static int questRank(CompoundTag quest) {
        String state = quest.getString("state");
        return switch (state) {
            case "active" -> quest.getBoolean("done") ? 0 : 1;
            case "available" -> 2;
            case "locked" -> 3;
            default -> 4;
        };
    }

    private void questAction(String action, String id) {
        CompoundTag args = new CompoundTag();
        args.putString("id", id);
        ClientGuild.send(action, args);
    }

    private CompoundTag selectedQuest() {
        for (CompoundTag quest : quests) {
            if (quest.getString("id").equals(selected)) {
                return quest;
            }
        }
        return null;
    }

    private void initParty() {
        ListTag posts = data.getList("party", Tag.TAG_COMPOUND);
        CompoundTag mine = null;
        for (Tag tag : posts) {
            if (((CompoundTag) tag).getBoolean("mine")) {
                mine = (CompoundTag) tag;
            }
        }
        int y = top + 42;
        if (mine == null) {
            postText = new EditBox(font, left + 14, y, 168, 14, Component.translatable("screen.amemaru.guild.party.text"));
            postText.setMaxLength(40);
            postText.setValue(postDraft);
            postText.setHint(Component.translatable("screen.amemaru.guild.party.hint").withStyle(s -> s.withColor(0x6E7891)));
            postText.setResponder(text -> postDraft = text);
            addRenderableWidget(postText);
            addRenderableWidget(new CrystalButton(left + 188, y, 12, 14, Component.literal("-"), b -> {
                postFloor = Math.max(0, postFloor - 1);
            }));
            addRenderableWidget(new CrystalButton(left + 234, y, 12, 14, Component.literal("+"), b -> {
                postFloor = Math.min(20, postFloor + 1);
            }));
            addRenderableWidget(new CrystalButton(left + 252, y, 12, 14, Component.literal("-"), b -> {
                postMinLevel = Math.max(1, postMinLevel - 5 + (postMinLevel == 1 ? 4 : 0));
            }));
            addRenderableWidget(new CrystalButton(left + 300, y, 12, 14, Component.literal("+"), b -> {
                postMinLevel = Math.min(100, postMinLevel == 1 ? 5 : postMinLevel + 5);
            }));
            Button post = addRenderableWidget(new CrystalButton(left + 318, y, 48, 14, Component.translatable("screen.amemaru.guild.party.post"),
                    b -> {
                        CompoundTag args = new CompoundTag();
                        args.putString("text", postDraft);
                        args.putInt("floor", postFloor);
                        args.putInt("minLevel", postMinLevel);
                        ClientGuild.send("party_post", args);
                        postDraft = "";
                    }));
            post.active = inGuild();
        } else {
            postText = null;
            addRenderableWidget(new CrystalButton(left + 318, y, 48, 14, Component.translatable("screen.amemaru.guild.party.cancel"),
                    b -> ClientGuild.send("party_cancel")));
        }
        partyScroll = Mth.clamp(partyScroll, 0, Math.max(0, posts.size() - POST_ROWS));
        int rowY = top + 66;
        for (int i = partyScroll; i < Math.min(posts.size(), partyScroll + POST_ROWS); i++) {
            CompoundTag one = posts.getCompound(i);
            UUID owner = one.getUUID("owner");
            if (one.getBoolean("mine")) {
                // 自分の募集：申し込んだ人を、入れる・断る（2人まで並べる。残りは次の人を受けると出る）
                ListTag applicants = one.getList("applicants", Tag.TAG_COMPOUND);
                for (int a = 0; a < Math.min(2, applicants.size()); a++) {
                    CompoundTag applicant = applicants.getCompound(a);
                    UUID who = applicant.getUUID("uuid");
                    int ay = rowY + 1 + a * 13;
                    addRenderableWidget(new CrystalButton(left + W - 96, ay, 40, 12, Component.translatable("screen.amemaru.guild.party.take"),
                            b -> answer(who, true)));
                    addRenderableWidget(new CrystalButton(left + W - 52, ay, 40, 12, Component.translatable("screen.amemaru.guild.party.decline"),
                            b -> answer(who, false)));
                }
            } else {
                Button apply = addRenderableWidget(new CrystalButton(left + W - 72, rowY + 7, 58, 14,
                        Component.translatable(one.getBoolean("applied") ? "screen.amemaru.guild.party.applied" : "screen.amemaru.guild.party.apply"),
                        b -> {
                            CompoundTag args = new CompoundTag();
                            args.putUUID("owner", owner);
                            ClientGuild.send("party_apply", args);
                        }));
                apply.active = !one.getBoolean("applied") && inGuild();
            }
            rowY += POST_ROW;
        }
    }

    private void answer(UUID applicant, boolean accept) {
        CompoundTag args = new CompoundTag();
        args.putUUID("applicant", applicant);
        args.putBoolean("accept", accept);
        ClientGuild.send("party_answer", args);
    }

    // ================================================================ 動き

    @Override
    public void tick() {
        super.tick();
        if (postText != null) {
            postText.tick();
        }
        // 本人の様子（親密度・受けているクエスト・称号）が変わったら作り直す
        if (selfVersion != ClientGuild.version() && !"party".equals(tab)) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // タブ
        for (int i = 0; i < TABS.length; i++) {
            int tx = left + 12 + i * (TAB_W + 2);
            if (mouseX >= tx && mouseX < tx + TAB_W && mouseY >= top + 20 && mouseY < top + 34) {
                if (!TABS[i].equals(tab)) {
                    tab = TABS[i];
                    rebuildWidgets();
                }
                return true;
            }
        }
        // クエストの一覧
        if ("quests".equals(tab) && mouseX >= left + 10 && mouseX < left + 10 + LIST_W && mouseY >= top + 40) {
            int index = questScroll + (int) ((mouseY - (top + 40)) / QUEST_ROW);
            if (index >= 0 && index < quests.size() && mouseY < top + 40 + QUEST_ROWS * QUEST_ROW) {
                selected = quests.get(index).getString("id");
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = delta > 0 ? -1 : 1;
        switch (tab) {
            case "quests" -> {
                questScroll = Mth.clamp(questScroll + step, 0, Math.max(0, quests.size() - QUEST_ROWS));
                return true;
            }
            case "party" -> {
                int size = data.getList("party", Tag.TAG_COMPOUND).size();
                int next = Mth.clamp(partyScroll + step, 0, Math.max(0, size - POST_ROWS));
                if (next != partyScroll) {
                    partyScroll = next;
                    rebuildWidgets();
                }
                return true;
            }
            case "chronicle" -> {
                int size = data.getList("chronicle", Tag.TAG_COMPOUND).size();
                chronicleScroll = Mth.clamp(chronicleScroll + step, 0, Math.max(0, size - CHRONICLE_ROWS));
                return true;
            }
            default -> {
                return super.mouseScrolled(mouseX, mouseY, delta);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================================================================ 描く

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        for (int i = 0; i < TABS.length; i++) {
            int tx = left + 12 + i * (TAB_W + 2);
            boolean on = TABS[i].equals(tab);
            boolean hover = mouseX >= tx && mouseX < tx + TAB_W && mouseY >= top + 20 && mouseY < top + 34;
            UiStyle.card(graphics, tx, top + 20, tx + TAB_W, top + 34, on ? 0x8FD3FF : 0x3E5274, on);
            Component label = Component.translatable("screen.amemaru.guild.tab." + TABS[i]);
            graphics.drawCenteredString(font, label, tx + TAB_W / 2, top + 23, on ? UiStyle.TEXT_GOLD : hover ? UiStyle.TEXT : UiStyle.TEXT_DIM);
        }
        switch (tab) {
            case "quests" -> renderQuests(graphics, mouseX, mouseY);
            case "party" -> renderParty(graphics);
            case "titles" -> renderTitles(graphics);
            case "chronicle" -> renderChronicle(graphics);
            case "bonds" -> renderBonds(graphics);
            default -> {
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderQuests(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = left + 10;
        int y = top + 40;
        CompoundTag self = ClientGuild.self();
        if (quests.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.quest.none"), x + 4, y + 4, UiStyle.TEXT_DIM, false);
        }
        for (int i = questScroll; i < Math.min(quests.size(), questScroll + QUEST_ROWS); i++) {
            CompoundTag quest = quests.get(i);
            int ry = y + (i - questScroll) * QUEST_ROW;
            boolean on = quest.getString("id").equals(selected);
            boolean hover = mouseX >= x && mouseX < x + LIST_W && mouseY >= ry && mouseY < ry + QUEST_ROW - 2;
            int accent = kindColor(quest.getString("kind"));
            UiStyle.card(graphics, x, ry, x + LIST_W, ry + QUEST_ROW - 2, accent, on || hover);
            String state = quest.getString("state");
            int nameColor = "done".equals(state) || "locked".equals(state) ? UiStyle.TEXT_DIM : UiStyle.TEXT;
            graphics.drawString(font, UiStyle.clip(font, quest.getString("name"), LIST_W - 8), x + 4, ry + 2, nameColor, false);
            Component status = Component.translatable("screen.amemaru.guild.quest.kind." + quest.getString("kind"))
                    .append(" ・ ").append(stateText(quest));
            graphics.drawString(font, UiStyle.clip(font, status.getString(), LIST_W - 8), x + 4, ry + 11, stateColor(quest), false);
        }
        if (quests.size() > QUEST_ROWS) {
            String more = (questScroll + 1) + "-" + Math.min(quests.size(), questScroll + QUEST_ROWS) + "/" + quests.size();
            graphics.drawString(font, more, x + LIST_W - font.width(more), top + H - 12, UiStyle.TEXT_DIM, false);
        }
        // デイリーの数
        Component daily = Component.translatable("screen.amemaru.guild.quest.daily", self.getInt("dailyDone"), self.getInt("dailyMax"));
        graphics.drawString(font, daily, x, top + H - 12, UiStyle.TEXT_DIM, false);

        // 選んでいるクエスト
        CompoundTag quest = selectedQuest();
        int dx = left + LIST_W + 22;
        int dw = W - LIST_W - 34;
        int dy = top + 40;
        if (quest == null) {
            return;
        }
        graphics.drawString(font, UiStyle.clip(font, quest.getString("name"), dw), dx, dy, UiStyle.TEXT_GOLD, false);
        dy += 11;
        Component sub = Component.translatable("screen.amemaru.guild.quest.kind." + quest.getString("kind"));
        if (!quest.getString("giver").isEmpty()) {
            sub = Component.empty().append(sub).append(" ・ ")
                    .append(Component.translatable("screen.amemaru.guild.quest.giver", quest.getString("giver")));
        }
        graphics.drawString(font, UiStyle.clip(font, sub.getString(), dw), dx, dy, UiStyle.TEXT_DIM, false);
        dy += 12;
        dy += UiStyle.wrapped(graphics, font, quest.getString("description"), dx, dy, dw, UiStyle.TEXT, 4) * 10 + 3;
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.quest.objectives"), dx, dy, UiStyle.TEXT_HEADER, false);
        dy += 10;
        boolean showCounts = "active".equals(quest.getString("state"));
        for (Tag tag : quest.getList("objectives", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) tag;
            int count = o.getInt("count");
            int max = o.getInt("max");
            boolean complete = showCounts && count >= max;
            String progress = showCounts ? (max > 1 ? Math.min(count, max) + "/" + max : complete ? "✓" : "") : (max > 1 ? "×" + max : "");
            String text = UiStyle.clip(font, GuildHud.objectiveText(o).getString(), dw - 14 - font.width(progress));
            graphics.drawString(font, (complete ? "◆ " : "◇ ") + text, dx, dy, complete ? UiStyle.TEXT_GOOD : UiStyle.TEXT, false);
            graphics.drawString(font, progress, dx + dw - font.width(progress), dy, complete ? UiStyle.TEXT_GOOD : UiStyle.TEXT_DIM, false);
            dy += 10;
            if (dy > top + H - 60) {
                break;
            }
        }
        dy += 3;
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.quest.rewards"), dx, dy, UiStyle.TEXT_HEADER, false);
        dy += 10;
        UiStyle.wrapped(graphics, font, rewardText(quest), dx, dy, dw, UiStyle.TEXT_GOLD, 2);
        // 報告の場所・ギルドの外
        String note = "";
        if ("active".equals(quest.getString("state")) && quest.getBoolean("done")) {
            String report = quest.getString("report");
            note = report.isEmpty() ? Component.translatable("hud.amemaru.quest.report_board").getString()
                    : Component.translatable("hud.amemaru.quest.report_npc", report).getString();
        } else if ("locked".equals(quest.getString("state"))) {
            note = Component.translatable("screen.amemaru.guild.quest.locked_note").getString();
        }
        if (!inGuild()) {
            note = Component.translatable("screen.amemaru.guild.not_here").getString();
        }
        if (!note.isEmpty()) {
            graphics.drawString(font, UiStyle.clip(font, note, dw), dx, top + H - 36, inGuild() ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, false);
        }
    }

    private static Component stateText(CompoundTag quest) {
        String state = quest.getString("state");
        if ("active".equals(state)) {
            return Component.translatable(quest.getBoolean("done") ? "screen.amemaru.guild.quest.state.ready"
                    : "screen.amemaru.guild.quest.state.active");
        }
        return Component.translatable("screen.amemaru.guild.quest.state." + state);
    }

    private static int stateColor(CompoundTag quest) {
        return switch (quest.getString("state")) {
            case "active" -> quest.getBoolean("done") ? UiStyle.TEXT_GOOD : UiStyle.TEXT_GOLD;
            case "available" -> UiStyle.TEXT_HEADER;
            default -> UiStyle.TEXT_DIM;
        };
    }

    private static int kindColor(String kind) {
        return switch (kind) {
            case "daily" -> 0x9CFFB0;
            case "town_tour" -> 0xBFEFFF;
            case "upper" -> 0xFFB86B;
            case "promotion" -> 0xF2C94C;
            case "personal" -> 0xFF9ACD;
            case "story" -> 0xC285FF;
            default -> 0x8FD3FF;
        };
    }

    private String rewardText(CompoundTag quest) {
        List<String> parts = new ArrayList<>();
        if (quest.getLong("knots") > 0) {
            parts.add(Component.translatable("screen.amemaru.guild.reward.knots", quest.getLong("knots")).getString());
        }
        if (quest.getLong("exp") > 0) {
            parts.add(Component.translatable("screen.amemaru.guild.reward.exp", quest.getLong("exp")).getString());
        }
        for (Tag tag : quest.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) tag;
            Component name;
            try {
                name = Component.Serializer.fromJson(item.getString("name"));
            } catch (RuntimeException e) {
                name = null;
            }
            parts.add((name == null ? "?" : name.getString()) + "×" + item.getInt("count"));
        }
        for (Tag tag : quest.getList("bonds", Tag.TAG_COMPOUND)) {
            CompoundTag bond = (CompoundTag) tag;
            parts.add(Component.translatable("screen.amemaru.guild.reward.bond", bond.getString("npc"), bond.getInt("amount")).getString());
        }
        if (!quest.getString("title").isEmpty()) {
            parts.add(Component.translatable("screen.amemaru.guild.reward.title", quest.getString("title")).getString());
        }
        if (quest.getBoolean("promote")) {
            parts.add(Component.translatable("screen.amemaru.guild.reward.promote").getString());
        }
        return parts.isEmpty() ? Component.translatable("screen.amemaru.guild.reward.none").getString() : String.join("・", parts);
    }

    private void renderParty(GuiGraphics graphics) {
        ListTag posts = data.getList("party", Tag.TAG_COMPOUND);
        boolean mine = false;
        for (Tag tag : posts) {
            mine |= ((CompoundTag) tag).getBoolean("mine");
        }
        int y = top + 42;
        if (postText != null) {
            graphics.drawCenteredString(font, floorText(postFloor), left + 217, y + 3, UiStyle.TEXT);
            graphics.drawCenteredString(font, "Lv" + postMinLevel + "~", left + 282, y + 3, UiStyle.TEXT);
        } else if (mine) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.party.yours"), left + 14, y + 3, UiStyle.TEXT_GOLD, false);
        }
        UiStyle.divider(graphics, left + 12, left + W - 12, top + 61);
        if (posts.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.party.none"), left + 14, top + 70, UiStyle.TEXT_DIM, false);
        }
        int rowY = top + 66;
        for (int i = partyScroll; i < Math.min(posts.size(), partyScroll + POST_ROWS); i++) {
            CompoundTag one = posts.getCompound(i);
            boolean isMine = one.getBoolean("mine");
            UiStyle.card(graphics, left + 12, rowY, left + W - 12, rowY + POST_ROW - 3, isMine ? 0xFFF2A8 : 0x8FD3FF, isMine);
            Component head = Component.translatable("screen.amemaru.guild.party.row", one.getString("name"), one.getInt("size"), one.getInt("max"),
                    floorText(one.getInt("floor")), one.getInt("minLevel"), one.getInt("minutes"));
            int textW = W - 24 - (isMine ? 110 : 80);
            graphics.drawString(font, UiStyle.clip(font, head.getString(), textW), left + 16, rowY + 3, isMine ? UiStyle.TEXT_GOLD : UiStyle.TEXT, false);
            String text = one.getString("text");
            graphics.drawString(font, UiStyle.clip(font, text.isEmpty() ? "…" : text, textW), left + 16, rowY + 14, UiStyle.TEXT_DIM, false);
            if (isMine) {
                ListTag applicants = one.getList("applicants", Tag.TAG_COMPOUND);
                if (applicants.isEmpty()) {
                    String none = Component.translatable("screen.amemaru.guild.party.no_applicants").getString();
                    graphics.drawString(font, none, left + W - 16 - font.width(none), rowY + 9, UiStyle.TEXT_DIM, false);
                }
                for (int a = 0; a < Math.min(2, applicants.size()); a++) {
                    CompoundTag applicant = applicants.getCompound(a);
                    String label = UiStyle.clip(font, applicant.getString("name") + " Lv" + applicant.getInt("level"), 60);
                    graphics.drawString(font, label, left + W - 100 - font.width(label), rowY + 3 + a * 13, UiStyle.TEXT, false);
                }
            }
            rowY += POST_ROW;
        }
        if (!inGuild()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.not_here"), left + 14, top + H - 14, UiStyle.TEXT_BAD, false);
        } else {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.party.note"), left + 14, top + H - 14, UiStyle.TEXT_DIM, false);
        }
    }

    private static String floorText(int floor) {
        return floor <= 0 ? Component.translatable("screen.amemaru.guild.party.any_floor").getString()
                : Component.translatable("screen.amemaru.guild.party.floor", floor).getString();
    }

    private void renderTitles(GuiGraphics graphics) {
        int x = left + 16;
        int y = top + 42;
        long now = Util.getMillis();
        Map<String, CompoundTag> owned = ClientGuild.owned();
        CompoundTag shown = owned.get(ClientGuild.displayed());
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.titles.shown"), x, y, UiStyle.TEXT_HEADER, false);
        if (shown == null) {
            graphics.drawString(font, Component.translatable("screen.amemaru.titles.none_shown"), x + 90, y, UiStyle.TEXT_DIM, false);
        } else {
            graphics.drawString(font, TitleText.of(shown.getString("name"), shown.getInt("color"), shown.getString("kind"),
                    shown.getBoolean("rainbow"), now, true), x + 90, y, 0xFFFFFF, false);
        }
        y += 14;
        int total = ClientGuild.catalog().getList("titles", Tag.TAG_COMPOUND).size();
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.titles.count", owned.size(), total), x, y, UiStyle.TEXT, false);
        y += 16;
        UiStyle.divider(graphics, left + 12, left + W - 12, y - 4);
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.titles.recent"), x, y, UiStyle.TEXT_HEADER, false);
        y += 12;
        List<CompoundTag> recent = new ArrayList<>(owned.values());
        recent.sort(Comparator.comparingInt((CompoundTag t) -> t.getInt("day")).reversed());
        if (recent.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.titles.none"), x, y, UiStyle.TEXT_DIM, false);
        }
        for (int i = 0; i < Math.min(6, recent.size()); i++) {
            CompoundTag t = recent.get(i);
            graphics.drawString(font, TitleText.of(t.getString("name"), t.getInt("color"), t.getString("kind"), t.getBoolean("rainbow"), now, true),
                    x, y, 0xFFFFFF, false);
            String day = Component.translatable("screen.amemaru.guild.day", t.getInt("day")).getString();
            graphics.drawString(font, day, left + W - 20 - font.width(day), y, UiStyle.TEXT_DIM, false);
            y += 12;
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.titles.note"), left + 140, top + H - 22, UiStyle.TEXT_DIM, false);
    }

    private void renderChronicle(GuiGraphics graphics) {
        ListTag list = data.getList("chronicle", Tag.TAG_COMPOUND);
        int x = left + 16;
        int y = top + 42;
        if (list.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.chronicle.none"), x, y, UiStyle.TEXT_DIM, false);
            return;
        }
        for (int i = chronicleScroll; i < Math.min(list.size(), chronicleScroll + CHRONICLE_ROWS); i++) {
            CompoundTag entry = list.getCompound(i);
            ListTag args = entry.getList("args", Tag.TAG_STRING);
            Object[] values = new Object[args.size()];
            for (int a = 0; a < args.size(); a++) {
                values[a] = args.getString(a);
            }
            String day = Component.translatable("screen.amemaru.guild.day", entry.getInt("day")).getString();
            graphics.drawString(font, day, x, y, UiStyle.TEXT_GOLD, false);
            UiStyle.wrapped(graphics, font, Component.translatable(entry.getString("key"), values).getString(), x + 52, y, W - 84, UiStyle.TEXT, 2);
            y += CHRONICLE_ROW;
        }
        if (list.size() > CHRONICLE_ROWS) {
            String more = (chronicleScroll + 1) + "-" + Math.min(list.size(), chronicleScroll + CHRONICLE_ROWS) + "/" + list.size();
            graphics.drawString(font, more, left + W - 16 - font.width(more), top + H - 14, UiStyle.TEXT_DIM, false);
        }
    }

    private void renderBonds(GuiGraphics graphics) {
        ListTag bonds = ClientGuild.self().getList("bonds", Tag.TAG_COMPOUND);
        int x = left + 16;
        int y = top + 40;
        if (bonds.isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.amemaru.guild.bonds.none"), x, y + 2, UiStyle.TEXT_DIM, false);
        }
        int column = 0;
        for (Tag tag : bonds) {
            CompoundTag bond = (CompoundTag) tag;
            int cx = x + column * 176;
            UiStyle.card(graphics, cx - 2, y, cx + 170, y + BOND_ROW - 3, 0xFF9ACD, false);
            graphics.drawString(font, UiStyle.clip(font, bond.getString("name"), 70), cx + 2, y + 3, UiStyle.TEXT, false);
            int stage = bond.getInt("stage");
            Component stageName = Component.translatable("bond.amemaru.stage." + stage);
            graphics.drawString(font, stageName, cx + 168 - font.width(stageName), y + 3, stage >= 5 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_HEADER, false);
            // 段階の印（5つ）と、次の段階までのバー
            for (int s = 1; s <= 5; s++) {
                int sx = cx + 2 + (s - 1) * 9;
                graphics.fill(sx, y + 14, sx + 7, y + 20, s <= stage ? 0xFFFF9ACD : 0xFF26324D);
            }
            int toNext = bond.getInt("toNext");
            int barX = cx + 50;
            graphics.fill(barX, y + 15, barX + 70, y + 19, 0xFF1B2030);
            if (stage >= 5) {
                graphics.fill(barX, y + 15, barX + 70, y + 19, 0xFFF2C94C);
            } else {
                float ratio = toNext <= 0 ? 1.0F : 1.0F - Math.min(1.0F, toNext / (float) Math.max(1, toNext + pointsIntoStage(bond)));
                graphics.fill(barX, y + 15, barX + Math.round(70 * ratio), y + 19, 0xFFFF9ACD);
            }
            String note = bond.getBoolean("talked") ? "✓" : "";
            graphics.drawString(font, note, cx + 166 - font.width(note), y + 13, UiStyle.TEXT_GOOD, false);
            column++;
            if (column >= 2) {
                column = 0;
                y += BOND_ROW;
            }
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.bonds.note"), left + 140, top + H - 22, UiStyle.TEXT_DIM, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.guild.bonds.partners", ClientGuild.self().getInt("partners"),
                ClientGuild.self().getInt("connections")), left + 140, top + H - 12, UiStyle.TEXT_DIM, false);
    }

    /** 今の段階に入ってから貯めた点（段階の下の端からの点）。 */
    private static int pointsIntoStage(CompoundTag bond) {
        int[] stages = {0, 0, 30, 80, 150, 250};
        int stage = Mth.clamp(bond.getInt("stage"), 1, 5);
        return Math.max(0, bond.getInt("points") - stages[stage]);
    }
}
