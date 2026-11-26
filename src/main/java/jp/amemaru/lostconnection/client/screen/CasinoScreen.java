package jp.amemaru.lostconnection.client.screen;

import jp.amemaru.lostconnection.casino.CasinoGames;
import jp.amemaru.lostconnection.network.ModNetwork;
import jp.amemaru.lostconnection.network.c2s.CasinoC2S;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * カジノ「遊戯館ラッキーノット」の画面（Phase 7 その3。Phase 0-B 13章「右クリックでGUIを開いて使う。【11月22日に決定】」）。
 *
 * <p>タブ：ルーレット（運命の輪の帯と、賭けの盤）・ハイ＆ロー（共同事業②のあと）・両替（11月27日から、地下遊技場の持ち玉・
 * 持ちメダルもここでチップに戻せる）・景品。11月27日のご指示「ルーレットとハイ＆ロー以外を廃止」で、魚レースのタブはやめた。
 * 卓のタブでは、右に勝負の流れ（賭ける時間・回る・結果）と、卓を囲む人と、これまでの結果を出す。</p>
 */
public class CasinoScreen extends Screen {
    private static final int W = 400;
    private static final int H = 226;
    private static final String[] TABS = {"roulette", "highlow", "cashier", "prizes"};
    private static final long[] CHIPS = {1, 5, 10, 50, 100};
    private static final int CW = 16;
    private static final int CH = 14;
    private static final int PRIZE_ROW = 20;
    private static final int PRIZE_ROWS = 7;

    private CompoundTag data;
    private CompoundTag state = new CompoundTag();
    private long stateAt;
    private String tab;
    private String highLowTable = "highlow_a";
    private long chip = 10;
    private int left;
    private int top;
    private int prizeScroll;

    public CasinoScreen(CompoundTag data) {
        super(Component.translatable("screen.amemaru.casino.title"));
        this.data = data;
        this.tab = normalize(data.getString("tab"));
        if (data.contains("state")) {
            setState(data.getCompound("state"));
        }
    }

    private static String normalize(String name) {
        for (String t : TABS) {
            if (t.equals(name)) {
                return t;
            }
        }
        return "roulette";
    }

    /** 開いたまま、別の卓・タブで開き直す（サーバーから）。 */
    public void reopen(CompoundTag next) {
        data = next;
        tab = normalize(next.getString("tab"));
        prizeScroll = 0;
        if (next.contains("state")) {
            state = new CompoundTag();
            setState(next.getCompound("state"));
        } else {
            state = new CompoundTag();
        }
        rebuild();
    }

    /** チップ・景品などが変わった（サーバーから）。 */
    public void update(CompoundTag next) {
        data = next;
        rebuild();
    }

    /** 卓の様子が変わった（サーバーから）。 */
    public void setState(CompoundTag next) {
        String before = signature(state);
        state = next;
        if (next.contains("chips")) {
            // 払い戻しのあとのチップ（卓の知らせに入っている）
            data.putLong("chips", next.getLong("chips"));
        }
        stateAt = System.currentTimeMillis();
        if (next.getString("table").startsWith("highlow")) {
            highLowTable = next.getString("table");
        }
        if (minecraft != null && !before.equals(signature(next))) {
            rebuild();
        }
    }

    /** ボタンの並びと文字が変わる所（勝負の流れ・カード・走る魚・賭けた人・自分の賭け）。 */
    private static String signature(CompoundTag s) {
        return s.getString("table") + "|" + s.getString("phase") + "|" + s.getInt("card") + "|" + s.getInt("bettors") + "|"
                + s.getList("mine", Tag.TAG_COMPOUND).size();
    }

    /** 今「回っている」ところか。 */
    public boolean isRolling() {
        return "rolling".equals(state.getString("phase"));
    }

    /** 自動の確認の写真：タブを変える。 */
    public void ciTab(String name) {
        switchTab(normalize(name));
    }

    /** 自動の確認の写真：チップの額を選ぶ。 */
    public void ciChip(long value) {
        chip = value;
        rebuild();
    }

    /** 自動の確認の写真：賭ける（kind:value）。 */
    public void ciBet(String kind, int value) {
        bet(kind, value);
    }

    private void switchTab(String name) {
        tab = name;
        prizeScroll = 0;
        String table = tableOf(name);
        if (table.isEmpty()) {
            send("unwatch", new CompoundTag());
            state = new CompoundTag();
        } else if (!table.equals(state.getString("table"))) {
            CompoundTag args = new CompoundTag();
            args.putString("table", table);
            send("watch", args);
            state = new CompoundTag();
        }
        rebuild();
    }

    private String tableOf(String name) {
        return switch (name) {
            case "roulette" -> "roulette";
            case "highlow" -> data.getBoolean("highlow") ? highLowTable : "";
            default -> "";
        };
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2);
        rebuild();
    }

    private void rebuild() {
        if (minecraft == null) {
            return;
        }
        clearWidgets();
        addRenderableWidget(new CrystalButton(left + W - 20, top + 5, 14, 12, Component.literal("×"), b -> onClose()));
        for (int i = 0; i < TABS.length; i++) {
            String name = TABS[i];
            CrystalButton button = new CrystalButton(left + 8 + i * 77, top + 31, 74, 14,
                    Component.translatable("screen.amemaru.casino.tab." + name), b -> switchTab(name));
            button.active = !name.equals(tab);
            addRenderableWidget(button);
        }
        boolean betting = "betting".equals(state.getString("phase"));
        switch (tab) {
            case "roulette" -> {
                chipButtons(left + 8, top + 194, betting);
            }
            case "highlow" -> {
                if (data.getBoolean("highlow")) {
                    for (int i = 0; i < 2; i++) {
                        String kind = i == 0 ? "high" : "low";
                        double mult = state.getDouble(i == 0 ? "multHigh" : "multLow");
                        CrystalButton b = new CrystalButton(left + 20 + i * 112, top + 152, 104, 16,
                                Component.translatable("screen.amemaru.casino.hl." + kind, mult <= 0 ? "-" : fmt(mult)), x -> bet(kind, 0));
                        b.active = betting && mult > 0;
                        addRenderableWidget(b);
                    }
                    chipButtons(left + 8, top + 194, betting);
                }
            }
            case "cashier" -> {
                long[] amounts = {10, 50, 100, -1};
                for (int i = 0; i < 4; i++) {
                    long n = amounts[i];
                    addRenderableWidget(new CrystalButton(left + 20 + i * 92, top + 132, 86, 16, n < 0
                            ? Component.translatable("screen.amemaru.casino.buy_max") : Component.translatable("screen.amemaru.casino.buy", n), b -> {
                        CompoundTag args = new CompoundTag();
                        args.putLong("chips", n < 0 ? Long.MAX_VALUE : n);
                        send("buy", args);
                    }));
                    addRenderableWidget(new CrystalButton(left + 20 + i * 92, top + 176, 86, 16, n < 0
                            ? Component.translatable("screen.amemaru.casino.sell_all") : Component.translatable("screen.amemaru.casino.sell", n), b -> {
                        CompoundTag args = new CompoundTag();
                        args.putLong("chips", n < 0 ? Long.MAX_VALUE : n);
                        send("sell", args);
                    }));
                }
                // 11月27日：地下遊技場の持ち玉・持ちメダルをチップに戻す（等価）。右上の2行（左の文の短い行の横）
                CrystalButton balls = new CrystalButton(left + W - 14 - 48, top + 56, 48, 14,
                        Component.translatable("screen.amemaru.casino.cash_balls"), b -> send("cash_balls", new CompoundTag()));
                balls.active = data.getLong("balls") >= Math.max(1, data.getInt("ballsPerChip"));
                addRenderableWidget(balls);
                CrystalButton medals = new CrystalButton(left + W - 14 - 48, top + 71, 48, 14,
                        Component.translatable("screen.amemaru.casino.cash_medals"), b -> send("cash_medals", new CompoundTag()));
                medals.active = data.getLong("medals") >= Math.max(1, data.getInt("medalsPerChip"));
                addRenderableWidget(medals);
            }
            case "prizes" -> {
                ListTag prizes = data.getList("prizes", Tag.TAG_COMPOUND);
                for (int i = prizeScroll; i < Math.min(prizes.size(), prizeScroll + PRIZE_ROWS); i++) {
                    CompoundTag one = prizes.getCompound(i);
                    int y = top + 70 + (i - prizeScroll) * PRIZE_ROW;
                    CrystalButton b = new CrystalButton(left + W - 76, y + 2, 64, 14, Component.translatable(one.getBoolean("owned")
                            ? "screen.amemaru.casino.owned" : "screen.amemaru.casino.exchange"), x -> {
                        CompoundTag args = new CompoundTag();
                        args.putString("id", one.getString("id"));
                        send("prize", args);
                    });
                    b.active = !one.getBoolean("owned") && data.getLong("chips") >= one.getLong("chips");
                    addRenderableWidget(b);
                }
            }
            default -> {
            }
        }
    }

    private void chipButtons(int x, int y, boolean betting) {
        for (int i = 0; i < CHIPS.length; i++) {
            long value = CHIPS[i];
            CrystalButton b = new CrystalButton(x + i * 34, y, 32, 14, Component.literal(String.valueOf(value)), x2 -> {
                chip = value;
                rebuild();
            });
            b.active = chip != value;
            addRenderableWidget(b);
        }
        CrystalButton clear = new CrystalButton(x + 174, y, 60, 14, Component.translatable("screen.amemaru.casino.clear"), b -> send("clear", table()));
        clear.active = betting && !state.getList("mine", Tag.TAG_COMPOUND).isEmpty();
        addRenderableWidget(clear);
    }

    private CompoundTag table() {
        CompoundTag args = new CompoundTag();
        args.putString("table", state.getString("table"));
        return args;
    }

    private void bet(String kind, int value) {
        if (!"betting".equals(state.getString("phase"))) {
            return;
        }
        CompoundTag args = table();
        args.putString("kind", kind);
        args.putInt("value", value);
        args.putLong("chips", chip);
        send("bet", args);
    }

    private void send(String action, CompoundTag args) {
        ModNetwork.sendToServer(new CasinoC2S(action, args));
    }

    @Override
    public void removed() {
        send("unwatch", new CompoundTag());
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** 残りの秒（サーバーの timer から、届いてからの時間を引く）。 */
    private int secondsLeft() {
        long passed = (System.currentTimeMillis() - stateAt) / 50;
        return (int) Math.max(0, (state.getInt("timer") - passed + 19) / 20);
    }

    /** 回っている間の進み（0〜1）。 */
    private float rollProgress() {
        if (!"rolling".equals(state.getString("phase"))) {
            return "result".equals(state.getString("phase")) ? 1.0F : 0.0F;
        }
        long passed = (System.currentTimeMillis() - stateAt) / 50;
        int total = Math.max(1, state.getInt("timer"));
        return Mth.clamp(passed / (float) total, 0.0F, 1.0F);
    }

    // ================================================================ 描く

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        UiStyle.panel(graphics, left, top, left + W, top + H);
        graphics.drawCenteredString(font, title, left + W / 2, top + 7, UiStyle.TEXT_HEADER);
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.chips", data.getLong("chips")), left + 10, top + 19,
                UiStyle.TEXT_GOLD, false);
        if (data.getBoolean("night")) {
            Component night = Component.translatable("screen.amemaru.casino.night", data.getInt("tablePlayers"),
                    Math.round(data.getDouble("nightBonus") * 100));
            graphics.drawString(font, night, left + W - 10 - font.width(night), top + 19, 0xFF9CF5, false);
        }
        switch (tab) {
            case "roulette" -> renderRoulette(graphics, mouseX, mouseY);
            case "highlow" -> renderHighLow(graphics);
            case "cashier" -> renderCashier(graphics);
            default -> renderPrizes(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 右の列：勝負の流れ・卓を囲む人・これまでの結果。 */
    private void renderSide(GuiGraphics graphics, int x, int y, int w) {
        UiStyle.card(graphics, x - 4, y - 4, x + w, top + H - 8, 0xB07CE0, false);
        String phase = state.getString("phase");
        Component status = switch (phase) {
            case "betting" -> Component.translatable("screen.amemaru.casino.phase.betting", secondsLeft());
            case "rolling" -> Component.translatable("screen.amemaru.casino.phase.rolling." + state.getString("game"));
            case "result" -> Component.translatable("screen.amemaru.casino.phase.result");
            default -> Component.translatable("screen.amemaru.casino.phase.idle");
        };
        graphics.drawString(font, status, x, y, "betting".equals(phase) ? UiStyle.TEXT_GOOD : UiStyle.TEXT_GOLD, false);
        long stake = 0;
        for (Tag t : state.getList("mine", Tag.TAG_COMPOUND)) {
            stake += ((CompoundTag) t).getLong("chips");
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.my_stake", stake), x, y + 12, UiStyle.TEXT, false);
        if (state.contains("myBack")) {
            long back = state.getLong("myBack");
            graphics.drawString(font, Component.translatable(back > 0 ? "screen.amemaru.casino.my_win" : "screen.amemaru.casino.my_lose", back),
                    x, y + 24, back > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);
        }
        ListTag players = state.getList("players", Tag.TAG_COMPOUND);
        int bettors = state.getInt("bettors");
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.players", players.size(), bettors, data.getInt("tablePlayers")),
                x, y + 40, bettors >= data.getInt("tablePlayers") ? 0xFF9CF5 : UiStyle.TEXT_HEADER, false);
        for (int i = 0; i < Math.min(6, players.size()); i++) {
            CompoundTag p = players.getCompound(i);
            String line = UiStyle.clip(font, p.getString("name"), w - 52);
            graphics.drawString(font, line, x + 4, y + 52 + i * 10, UiStyle.TEXT, false);
            String amount = p.contains("back") ? "→" + p.getLong("back") : String.valueOf(p.getLong("stake"));
            graphics.drawString(font, amount, x + w - 10 - font.width(amount), y + 52 + i * 10,
                    p.contains("back") && p.getLong("back") > 0 ? UiStyle.TEXT_GOLD : UiStyle.TEXT_DIM, false);
        }
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.history"), x, y + 116, UiStyle.TEXT_HEADER, false);
        int hx = x;
        int hy = y + 128;
        for (Tag t : state.getList("history", Tag.TAG_INT)) {
            int v = ((net.minecraft.nbt.IntTag) t).getAsInt();
            String text;
            int color;
            switch (state.getString("game")) {
                case "highlow" -> {
                    text = jp.amemaru.lostconnection.casino.CasinoService.cardName(v);
                    color = 0xFFFFFF;
                }
                default -> {
                    text = String.valueOf(v);
                    color = v == 0 ? 0x3FA34D : CasinoGames.RED.contains(v) ? 0xD03030 : 0x202020;
                }
            }
            graphics.fill(hx, hy, hx + 18, hy + 11, 0xFF000000 | UiStyle.darken(color, 0.9F));
            graphics.drawCenteredString(font, text, hx + 9, hy + 2, "highlow".equals(state.getString("game")) ? 0x202020 : 0xFFFFFF);
            hx += 20;
            if (hx + 18 > x + w - 4) {
                hx = x;
                hy += 13;
            }
        }
    }

    private void lockedMessage(GuiGraphics graphics, String key) {
        UiStyle.card(graphics, left + 8, top + 52, left + W - 8, top + H - 8, 0xB07CE0, false);
        UiStyle.wrapped(graphics, font, Component.translatable(key).getString(), left + 24, top + 100, W - 48, UiStyle.TEXT_GOLD, 4);
    }

    // ---------------------------------------------------------------- ルーレット

    private int boardX() {
        return left + 10;
    }

    private int boardY() {
        return top + 100;
    }

    private void renderRoulette(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = left + 8;
        int y0 = top + 52;
        // 運命の輪（帯）：真ん中の印のところに止まる
        int cells = 13;
        int cw = 18;
        int stripW = cells * cw;
        UiStyle.card(graphics, x0, y0, x0 + stripW + 4, y0 + 34, 0xE0B040, false);
        int outcome = state.getInt("outcome");
        float center;
        int target = outcome >= 0 ? CasinoGames.WHEEL.indexOf(outcome) : 0;
        if (outcome >= 0) {
            float t = rollProgress();
            float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            center = target + 37 * 3 * (1.0F - eased);
        } else {
            center = (System.currentTimeMillis() % 30000L) / 30000.0F * 37;
        }
        int midX = x0 + 2 + stripW / 2;
        graphics.enableScissor(x0 + 2, y0 + 2, x0 + 2 + stripW, y0 + 32);
        int base = (int) Math.floor(center);
        float frac = center - base;
        for (int k = -cells / 2 - 1; k <= cells / 2 + 1; k++) {
            int idx = Math.floorMod(base + k, 37);
            int n = CasinoGames.WHEEL.get(idx);
            int cx = (int) (midX + (k - frac) * cw - cw / 2.0F);
            int color = n == 0 ? 0xFF2E8B3A : CasinoGames.RED.contains(n) ? 0xFFB02828 : 0xFF1C1C22;
            graphics.fill(cx + 1, y0 + 8, cx + cw - 1, y0 + 28, color);
            graphics.drawCenteredString(font, String.valueOf(n), cx + cw / 2, y0 + 14, 0xFFFFFF);
        }
        graphics.disableScissor();
        graphics.fill(midX - 1, y0 + 2, midX + 1, y0 + 8, 0xFFFFD866);
        graphics.fill(midX - 1, y0 + 28, midX + 1, y0 + 32, 0xFFFFD866);
        if ("result".equals(state.getString("phase")) && outcome >= 0) {
            Component res = Component.translatable(outcome == 0 ? "casino.amemaru.number.green" : CasinoGames.RED.contains(outcome)
                    ? "casino.amemaru.number.red" : "casino.amemaru.number.black", outcome);
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.casino.result", res), x0 + stripW / 2 + 2, y0 + 37,
                    UiStyle.TEXT_GOLD);
        } else {
            graphics.drawCenteredString(font, Component.translatable("screen.amemaru.casino.roulette_help"), x0 + stripW / 2 + 2, y0 + 37,
                    UiStyle.TEXT_DIM);
        }
        renderBoard(graphics, mouseX, mouseY);
        renderSide(graphics, left + 260, top + 56, W - 260 - 8);
    }

    /** 盤のマス（kind, value, x0, y0, x1, y1）。 */
    private List<int[]> cellsOf(List<String> kinds) {
        List<int[]> list = new ArrayList<>();
        int bx = boardX();
        int by = boardY();
        // 0（3段ぶん）
        list.add(new int[]{0, 0, bx, by, bx + CW, by + CH * 3});
        kinds.add("number");
        for (int col = 0; col < 12; col++) {
            for (int row = 0; row < 3; row++) {
                int n = col * 3 + (3 - row);
                int x = bx + CW + col * CW;
                int y = by + row * CH;
                list.add(new int[]{0, n, x, y, x + CW, y + CH});
                kinds.add("number");
            }
        }
        // 列（2:1）：上の段は3の列
        for (int row = 0; row < 3; row++) {
            int x = bx + CW + 12 * CW;
            list.add(new int[]{0, 3 - row, x, by + row * CH, x + 22, by + row * CH + CH});
            kinds.add("column");
        }
        // ダズン
        for (int d = 0; d < 3; d++) {
            int x = bx + CW + d * 4 * CW;
            list.add(new int[]{0, d + 1, x, by + CH * 3, x + 4 * CW, by + CH * 4});
            kinds.add("dozen");
        }
        // 外の賭け
        String[] outs = {"low", "even", "red", "black", "odd", "high"};
        for (int i = 0; i < 6; i++) {
            int x = bx + CW + i * 2 * CW;
            list.add(new int[]{0, 0, x, by + CH * 4, x + 2 * CW, by + CH * 5});
            kinds.add(outs[i]);
        }
        return list;
    }

    private void renderBoard(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> kinds = new ArrayList<>();
        List<int[]> cells = cellsOf(kinds);
        int outcome = "result".equals(state.getString("phase")) ? state.getInt("outcome") : -1;
        for (int i = 0; i < cells.size(); i++) {
            int[] c = cells.get(i);
            String kind = kinds.get(i);
            int value = c[1];
            int color = switch (kind) {
                case "number" -> value == 0 ? 0xFF2E8B3A : CasinoGames.RED.contains(value) ? 0xFFA82626 : 0xFF1C1C22;
                case "red" -> 0xFFA82626;
                case "black" -> 0xFF1C1C22;
                default -> 0xFF1E5A30;
            };
            boolean hover = mouseX >= c[2] && mouseX < c[4] && mouseY >= c[3] && mouseY < c[5];
            boolean won = outcome >= 0 && CasinoGames.rouletteWins(kind, value, outcome);
            graphics.fill(c[2], c[3], c[4], c[5], 0xFFE8D9A8);
            graphics.fill(c[2] + 1, c[3] + 1, c[4] - 1, c[5] - 1, hover ? brighten(color) : color);
            if (won) {
                graphics.fill(c[2] + 1, c[3] + 1, c[4] - 1, c[3] + 3, 0xFFFFD866);
            }
            String label = switch (kind) {
                case "number" -> String.valueOf(value);
                case "column" -> "2:1";
                case "dozen" -> Component.translatable("screen.amemaru.casino.dozen." + value).getString();
                default -> Component.translatable("screen.amemaru.casino.bet." + kind).getString();
            };
            graphics.drawCenteredString(font, label, (c[2] + c[4]) / 2, (c[3] + c[5]) / 2 - 4, 0xFFFFFF);
            long mine = myBet(kind, value);
            if (mine > 0) {
                // 賭けたマスは、紫の枠で囲む（隅の印は2けたの数に重なるので、枠にする）
                int mark = 0xFFC77DFF;
                graphics.fill(c[2], c[3], c[4], c[3] + 2, mark);
                graphics.fill(c[2], c[5] - 2, c[4], c[5], mark);
                graphics.fill(c[2], c[3], c[2] + 2, c[5], mark);
                graphics.fill(c[4] - 2, c[3], c[4], c[5], mark);
            }
        }
    }

    private long myBet(String kind, int value) {
        long sum = 0;
        for (Tag t : state.getList("mine", Tag.TAG_COMPOUND)) {
            CompoundTag b = (CompoundTag) t;
            if (b.getString("kind").equals(kind) && b.getInt("value") == value) {
                sum += b.getLong("chips");
            }
        }
        return sum;
    }

    // ---------------------------------------------------------------- ハイ＆ロー

    private void renderHighLow(GuiGraphics graphics) {
        if (!data.getBoolean("highlow")) {
            lockedMessage(graphics, "screen.amemaru.casino.locked.highlow");
            return;
        }
        int x0 = left + 8;
        int y0 = top + 52;
        UiStyle.card(graphics, x0, y0, x0 + 244, y0 + 136, 0x58C060, false);
        UiStyle.wrapped(graphics, font, Component.translatable("screen.amemaru.casino.hl.help").getString(), x0 + 6, y0 + 3, 232, UiStyle.TEXT_DIM, 2);
        int card = state.getInt("card");
        drawCard(graphics, x0 + 50, y0 + 25, card, true);
        String phase = state.getString("phase");
        int outcome = state.getInt("outcome");
        boolean shown = "result".equals(phase) || ("rolling".equals(phase) && rollProgress() > 0.6F);
        drawCard(graphics, x0 + 146, y0 + 25, outcome, shown && outcome > 0);
        graphics.drawCenteredString(font, Component.translatable("screen.amemaru.casino.hl.now"), x0 + 74, y0 + 88, UiStyle.TEXT_HEADER);
        graphics.drawCenteredString(font, Component.translatable("screen.amemaru.casino.hl.next"), x0 + 170, y0 + 88, UiStyle.TEXT_HEADER);
        if ("result".equals(phase) && outcome > 0) {
            Component res = Component.translatable(outcome > card ? "screen.amemaru.casino.hl.went_high"
                    : outcome < card ? "screen.amemaru.casino.hl.went_low" : "screen.amemaru.casino.hl.same");
            graphics.drawCenteredString(font, res, x0 + 122, y0 + 120, UiStyle.TEXT_GOLD);
        }
        renderSide(graphics, left + 260, top + 56, W - 260 - 8);
    }

    private void drawCard(GuiGraphics graphics, int x, int y, int card, boolean face) {
        graphics.fill(x - 1, y - 1, x + 49, y + 61, 0xFF0B0F18);
        if (!face || card <= 0) {
            graphics.fill(x, y, x + 48, y + 60, 0xFF5A2D82);
            for (int i = 4; i < 44; i += 6) {
                graphics.fill(x + i, y + 4, x + i + 2, y + 56, 0xFF7F3FB2);
            }
            graphics.drawCenteredString(font, "?", x + 24, y + 26, 0xFFD866);
            return;
        }
        graphics.fill(x, y, x + 48, y + 60, 0xFFF4EEDC);
        String name = jp.amemaru.lostconnection.casino.CasinoService.cardName(card);
        int color = card % 2 == 0 ? 0xB02828 : 0x1C1C22;
        graphics.drawString(font, name, x + 4, y + 4, color, false);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 24, y + 24, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(font, name, 0, 0, color);
        graphics.pose().popPose();
        graphics.drawString(font, name, x + 44 - font.width(name), y + 50, color, false);
    }

    // ---------------------------------------------------------------- 両替

    private void renderCashier(GuiGraphics graphics) {
        int x0 = left + 8;
        int y0 = top + 52;
        UiStyle.card(graphics, x0, y0, left + W - 8, top + H - 8, 0xE0B040, false);
        long rate = data.getLong("rate");
        long left2 = Math.max(0, data.getLong("limit") - data.getLong("bought"));
        int y = y0 + 8;
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.rate", rate), x0 + 12, y, UiStyle.TEXT_HEADER, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.knots", data.getLong("knots")), x0 + 12, y + 14, UiStyle.TEXT, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.today", left2, data.getLong("limit")), x0 + 12, y + 28,
                left2 > 0 ? UiStyle.TEXT_GOOD : UiStyle.TEXT_BAD, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.buy_head"), x0 + 12, top + 120, UiStyle.TEXT_GOLD, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.sell_head"), x0 + 12, top + 164, UiStyle.TEXT_GOLD, false);
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.cashier_note"), x0 + 12, top + 198, UiStyle.TEXT_DIM, false);
        // 地下遊技場の持ち玉・持ちメダル（ボタンの左に、右寄せで）
        int bx = left + W - 14 - 48 - 4;
        Component balls = Component.translatable("screen.amemaru.casino.balls_have", data.getLong("balls"),
                data.getLong("balls") / Math.max(1, data.getInt("ballsPerChip")));
        Component medals = Component.translatable("screen.amemaru.casino.medals_have", data.getLong("medals"),
                data.getLong("medals") / Math.max(1, data.getInt("medalsPerChip")));
        graphics.drawString(font, balls, bx - font.width(balls), top + 59, UiStyle.TEXT, false);
        graphics.drawString(font, medals, bx - font.width(medals), top + 74, UiStyle.TEXT, false);
    }

    private void renderPrizes(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = left + 8;
        int y0 = top + 52;
        graphics.drawString(font, Component.translatable("screen.amemaru.casino.prize_head"), x0 + 4, y0 + 2, UiStyle.TEXT_HEADER, false);
        ListTag prizes = data.getList("prizes", Tag.TAG_COMPOUND);
        for (int i = prizeScroll; i < Math.min(prizes.size(), prizeScroll + PRIZE_ROWS); i++) {
            CompoundTag one = prizes.getCompound(i);
            int y = top + 70 + (i - prizeScroll) * PRIZE_ROW;
            UiStyle.card(graphics, x0, y, left + W - 8, y + PRIZE_ROW - 2, 0xB07CE0, false);
            ItemStack stack = "title".equals(one.getString("kind")) ? new ItemStack(Items.NAME_TAG) : ItemStack.of(one.getCompound("stack"));
            graphics.renderItem(stack, x0 + 3, y + 1);
            String name = "title".equals(one.getString("kind"))
                    ? Component.translatable("screen.amemaru.casino.title_prize", one.getString("name")).getString()
                    : stack.getHoverName().getString() + (stack.getCount() > 1 ? " ×" + stack.getCount() : "");
            graphics.drawString(font, UiStyle.clip(font, name, 220), x0 + 24, y + 5, UiStyle.TEXT, false);
            String price = Component.translatable("screen.amemaru.casino.price", one.getLong("chips")).getString();
            graphics.drawString(font, price, left + W - 84 - font.width(price), y + 5, UiStyle.TEXT_GOLD, false);
            if (mouseX >= x0 + 3 && mouseX < x0 + 19 && mouseY >= y + 1 && mouseY < y + 17 && !"title".equals(one.getString("kind"))) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
        if (prizes.size() > PRIZE_ROWS) {
            graphics.drawString(font, (prizeScroll + 1) + "–" + Math.min(prizes.size(), prizeScroll + PRIZE_ROWS) + " / " + prizes.size(),
                    x0 + 4, top + H - 14, UiStyle.TEXT_DIM, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if ("prizes".equals(tab)) {
            int max = Math.max(0, data.getList("prizes", Tag.TAG_COMPOUND).size() - PRIZE_ROWS);
            int next = Mth.clamp(prizeScroll - (int) Math.signum(delta), 0, max);
            if (next != prizeScroll) {
                prizeScroll = next;
                rebuild();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if ("roulette".equals(tab) && button == 0) {
            List<String> kinds = new ArrayList<>();
            List<int[]> cells = cellsOf(kinds);
            for (int i = 0; i < cells.size(); i++) {
                int[] c = cells.get(i);
                if (mouseX >= c[2] && mouseX < c[4] && mouseY >= c[3] && mouseY < c[5]) {
                    bet(kinds.get(i), c[1]);
                    return true;
                }
            }
        }
        return false;
    }

    /** 色を少し明るくする（マウスが乗ったマス）。 */
    private static int brighten(int argb) {
        int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * 1.35F) + 24);
        int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * 1.35F) + 24);
        int b = Math.min(255, (int) ((argb & 0xFF) * 1.35F) + 24);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static String fmt(double mult) {
        return String.format(Locale.ROOT, mult == Math.floor(mult) ? "%.0f" : mult * 10 == Math.floor(mult * 10) ? "%.1f" : "%.2f", mult);
    }
}
